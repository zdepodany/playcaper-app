/* SPDX-License-Identifier: AGPL-3.0-or-later */
"use strict";
// webOS 3.x/4.x zavře průhledný překryv 120 s po posledním stisku ovladače a
// restartUserActivityTimer tam aplikacím nepovolí. Síťové ovládání TV (SSAP, stejné
// jako LG ThinQ) umí poslat stisk tlačítka, který se počítá jako aktivita ovladače.
// Barevné tlačítko na HDMI vstupu nic neotevírá a přijímač ho ignoruje.
// Ověřeno na LG 43UK6300 (webOS 4.4.3); kód je ES5 kvůli Node.js 0.12.
var net = require("net");
var crypto = require("crypto");
var toBuffer = require("./protocol").toBuffer;

var MANIFEST = { manifestVersion: 1, appVersion: "1.0",
    permissions: ["CONTROL_INPUT_JOYSTICK", "CONTROL_MOUSE_AND_KEYBOARD"] };

function zeros(length) {
    if (typeof Buffer.alloc === "function") return Buffer.alloc(length);
    var buffer = new Buffer(length);
    buffer.fill(0);
    return buffer;
}

// Minimální WebSocket klient (RFC 6455): textové rámce bez fragmentace, ping/pong, close.
function connect(url, handlers) {
    var match = /^wss?:\/\/([^\/:]+):(\d+)(\/.*)?$/.exec(url);
    if (!match) { setImmediate(function () { handlers.close("Neplatná adresa " + url); }); return { send: function () {}, close: function () {} }; }
    var socket = net.connect(Number(match[2]), match[1]);
    var open = false, closed = false, pending = zeros(0);
    function finish(reason) {
        if (closed) return;
        closed = true;
        try { socket.destroy(); } catch (error) {}
        handlers.close(reason);
    }
    function frame(opcode, payload) {
        var mask = crypto.randomBytes(4), length = payload.length, header;
        if (length < 126) { header = zeros(2); header[1] = 0x80 | length; }
        else { header = zeros(4); header[1] = 0x80 | 126; header.writeUInt16BE(length, 2); }
        header[0] = 0x80 | opcode;
        var masked = zeros(length);
        for (var i = 0; i < length; i++) masked[i] = payload[i] ^ mask[i % 4];
        return Buffer.concat([header, mask, masked]);
    }
    socket.setTimeout(30000, function () { if (!open) finish("Síťové ovládání TV neodpovědělo."); });
    socket.on("connect", function () {
        socket.write("GET " + (match[3] || "/") + " HTTP/1.1\r\nHost: " + match[1] + ":" + match[2] +
            "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + crypto.randomBytes(16).toString("base64") +
            "\r\nSec-WebSocket-Version: 13\r\n\r\n");
    });
    socket.on("data", function (data) {
        pending = Buffer.concat([pending, data]);
        if (!open) {
            var end = pending.toString("binary").indexOf("\r\n\r\n");
            if (end < 0) return;
            if (!/^HTTP\/1\.1 101/.test(pending.toString("utf8", 0, end))) { finish("TV odmítla WebSocket spojení."); return; }
            open = true;
            socket.setTimeout(0);
            pending = pending.slice(end + 4);
            handlers.open();
        }
        while (pending.length >= 2) {
            var opcode = pending[0] & 15, length = pending[1] & 127, offset = 2;
            if (length === 126) { if (pending.length < 4) return; length = pending.readUInt16BE(2); offset = 4; }
            else if (length === 127) { if (pending.length < 10) return; length = pending.readUInt32BE(6); offset = 10; }
            if (pending[1] & 128) offset += 4; // server rámce maskovat nesmí, ale ať se nerozpadne parser
            if (pending.length < offset + length) return;
            var payload = pending.slice(offset, offset + length);
            pending = pending.slice(offset + length);
            if (opcode === 1) handlers.message(payload.toString("utf8"));
            else if (opcode === 9) socket.write(frame(10, payload));
            else if (opcode === 8) { finish("TV ukončila spojení."); return; }
        }
    });
    socket.on("error", function (error) { finish("Síťové ovládání TV není dostupné (" + error.code + ")."); });
    socket.on("close", function () { finish("Spojení se síťovým ovládáním TV skončilo."); });
    return {
        send: function (text) { if (open && !closed) socket.write(frame(1, toBuffer(text))); },
        close: function () { if (!closed) { closed = true; try { socket.end(frame(8, zeros(0))); } catch (error) {} } }
    };
}

/**
 * Udržuje překryv tím, že každou minutu pošle TV stisk tlačítka.
 * notify(event): {state: "pairing"} čeká na potvrzení na TV, {state: "active", clientKey},
 * {state: "failed", errorText}.
 */
exports.create = function (options) {
    var host = options.host || "127.0.0.1", port = options.port || 3000;
    var key = options.key || "RED", interval = options.interval || 60000, retry = options.retry || 10000;
    var notify = options.notify;
    var running = false, main = null, pointer = null, timer = null, restart = null, clientKey = null, generation = 0;

    function cleanup() {
        clearInterval(timer); timer = null;
        clearTimeout(restart); restart = null;
        if (pointer) pointer.close();
        if (main) main.close();
        pointer = main = null;
    }
    function fail(text, permanent) {
        if (!running) return;
        cleanup();
        notify({ state: "failed", errorText: text });
        // Odmítnuté spárování se neopakuje, aby TV neukazovala dotaz pořád dokola.
        if (permanent) { running = false; return; }
        var current = generation;
        restart = setTimeout(function () { if (running && current === generation) begin(); }, retry);
    }
    function press() { if (pointer) pointer.send("type:button\nname:" + key + "\n\n"); }
    function begin() {
        var current = ++generation;
        main = connect("ws://" + host + ":" + port + "/", {
            open: function () {
                var payload = { pairingType: "PROMPT", manifest: MANIFEST };
                if (clientKey) payload["client-key"] = clientKey;
                main.send(JSON.stringify({ type: "register", id: "register", payload: payload }));
            },
            message: function (text) {
                if (current !== generation) return;
                var message;
                try { message = JSON.parse(text); } catch (error) { return; }
                var payload = message.payload || {};
                if (message.type === "response" && message.id === "register" && payload.pairingType === "PROMPT") {
                    notify({ state: "pairing" });
                } else if (message.type === "registered") {
                    if (typeof payload["client-key"] === "string") clientKey = payload["client-key"];
                    main.send(JSON.stringify({ type: "request", id: "pointer",
                        uri: "ssap://com.webos.service.networkinput/getPointerInputSocket" }));
                } else if (message.id === "pointer" && typeof payload.socketPath === "string") {
                    pointer = connect(payload.socketPath, {
                        open: function () {
                            if (current !== generation) return;
                            press();
                            clearInterval(timer);
                            timer = setInterval(press, interval);
                            notify({ state: "active", clientKey: clientKey });
                        },
                        message: function () {},
                        close: function (reason) { if (current === generation) fail(reason, false); }
                    });
                } else if (message.type === "error") {
                    var denied = message.id === "register";
                    fail(denied ? "Připojení PlayCaption k TV bylo odmítnuto." : "TV odmítla síťové ovládání.", denied);
                }
            },
            close: function (reason) { if (current === generation) fail(reason, false); }
        });
    }
    return {
        start: function (savedKey) {
            if (typeof savedKey === "string" && /^[A-Za-z0-9]{8,128}$/.test(savedKey)) clientKey = savedKey;
            if (running) return;
            running = true;
            begin();
        },
        stop: function () { running = false; generation++; cleanup(); }
    };
};
