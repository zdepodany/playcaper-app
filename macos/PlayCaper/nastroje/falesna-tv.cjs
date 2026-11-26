// Falešná TV pro ladění PlayCaper na Macu bez televize:
// ohlašuje se jako přijímač (PLAYCAPTION_TV) a vypisuje titulky rozparsované
// skutečným parserem přijímače (webos/service/protocol.js).
//
//   node macos/PlayCaper/nastroje/falesna-tv.cjs
"use strict";
const dgram = require("dgram");
const path = require("path");
const protocol = require(path.join(__dirname, "../../../webos/service/protocol.js"));

const PORT_HLEDANI = 41988;
const PORT_TITULKY = 41989;
const ohlaseni = Buffer.from("PLAYCAPTION_TV\tfalesna-tv\tFalešná TV (Mac)\t0", "utf8");

const prijem = dgram.createSocket({ type: "udp4", reuseAddr: true });
let posledniId = null;
prijem.on("message", (zprava, od) => {
    const p = protocol.parse(zprava);
    if (!p) { console.log(`?? nerozpoznáno od ${od.address}: ${JSON.stringify(zprava.toString("utf8"))}`); return; }
    if (p.kind === "subtitle") {
        if (p.id === posledniId) return; // každou sekundu se opakuje
        posledniId = p.id;
        const cas = new Date().toISOString().slice(11, 23);
        console.log(`${cas}  #${p.id}  ${p.text ? JSON.stringify(p.text) : "(smazat)"}  doba=${p.duration} gta=${p.gta}`);
    } else {
        console.log(`${p.kind}: ${JSON.stringify(p)}`);
    }
});
prijem.bind(PORT_TITULKY, () => console.log(`Poslouchám titulky na ${PORT_TITULKY}`));

const hlas = dgram.createSocket({ type: "udp4", reuseAddr: true });
hlas.bind(() => {
    hlas.setBroadcast(true);
    // Jen broadcast, jako skutečná TV (dvě různé zdrojové IP by PlayCaper přepínaly mezi cíli).
    const posli = () => hlas.send(ohlaseni, PORT_HLEDANI, "255.255.255.255");
    posli();
    setInterval(posli, 2000);
    console.log("Ohlašuji se jako „Falešná TV (Mac)“ každé 2 s");
});
