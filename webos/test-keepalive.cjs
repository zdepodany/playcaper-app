// Udržení překryvu na webOS 3.x/4.x simulovaným tlačítkem přes síťové ovládání LG (SSAP).
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const crypto = require('node:crypto');
const vm = require('node:vm');
const { once } = require('node:events');
const keepalive = require('./service/keepalive');

// Falešný SSAP server: WebSocket na /, pointer socket na /pointer.
function frame(text) {
    const data = Buffer.from(text);
    const header = data.length < 126 ? Buffer.from([0x81, data.length]) : Buffer.from([0x81, 126, data.length >> 8, data.length & 255]);
    return Buffer.concat([header, data]);
}
function parseFrames(state, chunk, onText) {
    state.buf = Buffer.concat([state.buf, chunk]);
    while (state.buf.length >= 2) {
        let len = state.buf[1] & 127, off = 2;
        if (len === 126) { if (state.buf.length < 4) return; len = state.buf.readUInt16BE(2); off = 4; }
        assert.equal(state.buf[1] & 128, 128, 'Klient musí maskovat rámce.');
        if (state.buf.length < off + 4 + len) return;
        const mask = state.buf.subarray(off, off + 4), data = Buffer.from(state.buf.subarray(off + 4, off + 4 + len));
        for (let i = 0; i < len; i++) data[i] ^= mask[i % 4];
        const opcode = state.buf[0] & 15;
        state.buf = state.buf.subarray(off + 4 + len);
        if (opcode === 1) onText(data.toString());
    }
}
function fakeTv(mode) {
    const tv = { registrations: [], presses: [], sockets: [], mode };
    tv.server = http.createServer();
    tv.server.on('upgrade', (req, socket) => {
        tv.sockets.push(socket);
        const accept = crypto.createHash('sha1').update(req.headers['sec-websocket-key'] + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
        socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' + accept + '\r\n\r\n');
        const state = { buf: Buffer.alloc(0) };
        socket.on('error', () => {});
        socket.on('data', chunk => parseFrames(state, chunk, text => {
            if (req.url === '/pointer') { tv.presses.push(text); return; }
            const msg = JSON.parse(text);
            if (msg.type === 'register') {
                tv.registrations.push(msg.payload);
                if (tv.mode === 'deny') { socket.write(frame(JSON.stringify({ type: 'error', id: msg.id, error: '403 User denied access' }))); return; }
                if (msg.payload['client-key'] !== 'abc123def456') socket.write(frame(JSON.stringify({ type: 'response', id: msg.id, payload: { pairingType: 'PROMPT', returnValue: true } })));
                setTimeout(() => socket.write(frame(JSON.stringify({ type: 'registered', id: msg.id, payload: { 'client-key': 'abc123def456' } }))), 20);
            } else if (msg.uri === 'ssap://com.webos.service.networkinput/getPointerInputSocket') {
                socket.write(frame(JSON.stringify({ type: 'response', id: msg.id, payload: { returnValue: true,
                    socketPath: 'ws://127.0.0.1:' + tv.server.address().port + '/pointer' } })));
            }
        }));
    });
    return tv;
}
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(check, what) {
    for (let i = 0; i < 200 && !check(); i++) await wait(10);
    assert.ok(check(), what);
}

(async () => {
    // 1) První spárování: dotaz na TV, pak stisky RED v intervalu a předání klíče.
    const tv = fakeTv('prompt');
    tv.server.listen(0, '127.0.0.1'); await once(tv.server, 'listening');
    const events = [];
    const ka = keepalive.create({ port: tv.server.address().port, interval: 40, retry: 40, notify: e => events.push(e) });
    ka.start('');
    await until(() => tv.presses.length >= 3, 'Tlačítko se musí posílat opakovaně.');
    assert.deepEqual(events.slice(0, 2).map(e => e.state), ['pairing', 'active']);
    assert.equal(events[1].clientKey, 'abc123def456');
    assert.equal(tv.presses[0], 'type:button\nname:RED\n\n');
    assert.deepEqual(tv.registrations[0].manifest.permissions, ['CONTROL_INPUT_JOYSTICK', 'CONTROL_MOUSE_AND_KEYBOARD']);
    ka.start('abc123def456'); // opakovaný start nesmí otevřít druhé spojení
    assert.equal(tv.registrations.length, 1);

    // 2) Výpadek spojení: obnoví se s uloženým klíčem, bez nového dotazu na TV.
    tv.sockets.forEach(s => s.destroy());
    await until(() => tv.registrations.length === 2 && events.filter(e => e.state === 'active').length === 2, 'Po výpadku se musí znovu připojit.');
    assert.equal(tv.registrations[1]['client-key'], 'abc123def456');
    assert.equal(events.filter(e => e.state === 'pairing').length, 1, 'Uložený klíč nesmí znovu ukázat dotaz.');

    // 3) Zastavení: žádné další stisky ani pokusy o spojení.
    ka.stop();
    await wait(60);
    const pressed = tv.presses.length;
    await wait(150);
    assert.equal(tv.presses.length, pressed, 'Po stop() se nesmí nic posílat.');
    assert.equal(tv.registrations.length, 2);

    // 4) Odmítnutí na TV: trvalá chyba, žádné opakování dotazu.
    const denied = fakeTv('deny');
    denied.server.listen(0, '127.0.0.1'); await once(denied.server, 'listening');
    const deniedEvents = [];
    const kd = keepalive.create({ port: denied.server.address().port, interval: 40, retry: 40, notify: e => deniedEvents.push(e) });
    kd.start();
    await until(() => deniedEvents.length > 0, 'Odmítnutí musí přijít jako událost.');
    await wait(150);
    assert.equal(denied.registrations.length, 1, 'Odmítnutý dotaz se nesmí opakovat.');
    assert.match(deniedEvents[0].errorText, /odmítnuto/);

    // 5) Nedostupné ovládání: dočasná chyba a další pokus.
    const offline = [];
    const ko = keepalive.create({ port: 1, retry: 30, notify: e => offline.push(e) });
    ko.start();
    await until(() => offline.length >= 2, 'Nedostupné ovládání se musí zkoušet znovu.');
    ko.stop();
    assert.ok(offline.every(e => e.state === 'failed'));
    for (const t of [tv, denied]) { t.sockets.forEach(s => s.destroy()); t.server.close(); }

    // 6) UI: po odmítnutí restartUserActivityTimer požádá službu, uloží klíč a upraví hlášku.
    const elements = Object.fromEntries(['status', 'subtitle', 'hint'].map(id => [id, { style: {}, textContent: '' }]));
    const listeners = {}, timers = new Map(), bridges = [], storage = { 'playcaption-remote-key': 'savedkey123' };
    let now = 1000, nextId = 0;
    function Bridge() { bridges.push(this); this.call = (uri, payload) => { this.uri = uri; this.payload = payload; }; this.cancel = () => { this.cancelled = true; }; }
    vm.runInNewContext(fs.readFileSync(__dirname + '/app/app.js', 'utf8'), {
        document: { getElementById: id => elements[id], addEventListener: (name, fn) => { listeners[name] = fn; } },
        window: { close() {}, addEventListener: (name, fn) => { listeners[name] = fn; } },
        localStorage: { getItem: k => storage[k] ?? null, setItem: (k, v) => { storage[k] = String(v); } },
        PalmServiceBridge: Bridge, Date: { now: () => now }, JSON,
        setInterval: fn => { timers.set(++nextId, fn); return nextId; }, clearInterval: id => timers.delete(id)
    });
    const byUri = end => bridges.filter(b => b.uri && b.uri.endsWith(end));
    const listen = () => byUri('/listen').at(-1);
    listen().onservicecallback(JSON.stringify({ returnValue: true, ready: true, name: 'LG PlayCaption · TEST' }));
    byUri('/restartUserActivityTimer').at(-1).onservicecallback('{"returnValue":true}');
    assert.equal(byUri('/remoteKeepAlive').length, 0, 'Na novějších LG se síťové ovládání nepoužívá.');
    now += 45000; [...timers.values()].forEach(fn => fn());
    byUri('/restartUserActivityTimer').at(-1).onservicecallback('{"returnValue":false}');
    assert.equal(byUri('/remoteKeepAlive').length, 1);
    assert.equal(JSON.parse(byUri('/remoteKeepAlive')[0].payload).clientKey, 'savedkey123');
    assert.match(elements.status.textContent, /LG odmítá/);
    listen().onservicecallback(JSON.stringify({ returnValue: true, keepAlive: 'pairing' }));
    assert.match(elements.status.textContent, /potvrď ovladačem/);
    listen().onservicecallback(JSON.stringify({ returnValue: true, keepAlive: 'active', clientKey: 'newkey456' }));
    assert.equal(storage['playcaption-remote-key'], 'newkey456');
    assert.equal(elements.status.textContent.includes('LG odmítá'), false);
    now += 45000; [...timers.values()].forEach(fn => fn());
    byUri('/restartUserActivityTimer').at(-1).onservicecallback('{"returnValue":false}');
    assert.equal(byUri('/remoteKeepAlive').length, 1, 'Běžící udržování se nesmí znovu žádat.');
    assert.equal(elements.status.textContent.includes('LG odmítá'), false);
    listen().onservicecallback(JSON.stringify({ returnValue: true, keepAlive: 'failed', errorText: 'Připojení PlayCaption k TV bylo odmítnuto.' }));
    assert.match(elements.status.textContent, /odmítnuto/);
    listeners.webOSRelaunch();
    byUri('/restartUserActivityTimer').at(-1).onservicecallback('{"returnValue":false}');
    assert.equal(byUri('/remoteKeepAlive').length, 2, 'Po novém spuštění se může zeptat znovu.');
    console.log('OK: udržení překryvu přes síťové ovládání – párování, RED v intervalu, obnova s klíčem, stop, odmítnutí, nedostupnost a UI.');
})().catch(error => { console.error(error); process.exit(1); });
