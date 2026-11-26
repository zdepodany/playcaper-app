/* SPDX-License-Identifier: AGPL-3.0-or-later */
/* Samsung prijímač PlayCaption. Rozmiestnenie titulkov vychádza z LG prijímača
   (webos/app/app.js v PlayCaption, © Adam Růžička). */
var NAZOV_PORTU = 'PLAYCAPTION_UI';
var NAZOV_PORTU_SLUZBY = 'PLAYCAPTION_SLUZBA';

var hdmi = [];
var idx = 0;
var hdmiText = 'Spouštím HDMI…';
var prijemText = 'Spouštím příjem titulků…';
var prijemChyba = false;
var nazovTv = '';
var verziaTv = '';
var sluzbaVidena = 0;
var sluzbaPokus = 0;
var dataVidene = 0;
var posledneData = 0;
var poslednyZdroj = null;
var posledneId = null;
var titulokDo = 0;
var hintDo = 0;
var stavDo = 0;
var stavNatrvalo = false;
var ladenie = true;
var zaznamy = [];
var stavSluzby = null;
var zobrazene = 0;
var start = Date.now();

var elStav = document.getElementById('stav');
var elNapoveda = document.getElementById('napoveda');
var elTitulok = document.getElementById('subtitle');
var elHint = document.getElementById('hint');
var elDebug = document.getElementById('debug');
var ui = new window.PlayCaptionReceiverUI(document);

function dve(n) {
    return (n < 10 ? '0' : '') + n;
}

function zaznam(text) {
    var d = new Date();
    zaznamy.push(dve(d.getHours()) + ':' + dve(d.getMinutes()) + ':' + dve(d.getSeconds()) + ' ' + text);
    if (zaznamy.length > 14) {
        zaznamy.shift();
    }
}

function pred(cas, teraz) {
    return cas ? Math.max(0, Math.round((teraz - cas) / 1000)) + ' s' : 'nikdy';
}

function kresliLadenie(teraz) {
    elDebug.hidden = !ladenie;
    if (!ladenie) {
        return;
    }
    var p = stavSluzby && stavSluzby.pocty;
    var riadky = [
        'LADĚNÍ (OK = schovat)   běží ' + Math.round((teraz - start) / 1000) + ' s' + (verziaTv ? '   verze ' + verziaTv : ''),
        'Služba: ' + (stavSluzby ? stavSluzby.node + ', ozvala se před ' + pred(sluzbaVidena, teraz) : 'zatím se neozvala'),
        'TV v síti: ' + (stavSluzby && stavSluzby.adresy && stavSluzby.adresy.length ? stavSluzby.adresy.join(', ') : 'neznámé'),
        'Ohlášení: ' + (p ? p.ohlasenia : 0) + '   Odesílatel: ' + (poslednyZdroj || 'žádný') + ', data před ' + pred(dataVidene || posledneData, teraz),
        'Přijato: titulky ' + (p ? p.titulky : 0) + ', volby ' + (p ? p.volby : 0) + ', oblasti ' + (p ? p.oblasti : 0) +
            ', příběh ' + (p && p.pribeh ? p.pribeh : 0) + ', neznámé ' + (p ? p.neplatne : 0) + ', převzetí ' + (p ? p.prevzatia : 0) + ', cizí ' + (p ? p.odmietnute : 0),
        'Zobrazené titulky: ' + zobrazene,
        '----'
    ];
    elDebug.textContent = riadky.concat(zaznamy).join('\n');
}

function kresliStav() {
    var riadky = [hdmiText, prijemText];
    if (nazovTv && !prijemChyba) {
        riadky.push('V telefonu nebo na Macu vyber: ' + nazovTv);
    }
    elStav.textContent = riadky.join('\n');
}

function ukazStav(ms) {
    kresliStav();
    elStav.hidden = false;
    elNapoveda.hidden = false;
    stavDo = Date.now() + (ms || 8000);
}

function chybaHdmi(co) {
    return function (e) {
        hdmiText = co + ': ' + (e && e.message ? e.message : e);
        zaznam(hdmiText);
        ukazStav(60000);
    };
}

function ukazHdmi() {
    if (hdmi.length === 0) {
        hdmiText = 'Nenašel jsem žádný HDMI vstup.';
        ukazStav(60000);
        return;
    }
    var zdroj = hdmi[idx];
    tizen.tvwindow.setSource(zdroj, function () {
        tizen.tvwindow.show(
            function () {
                hdmiText = 'HDMI ' + zdroj.number + ' běží (' + (idx + 1) + ' z ' + hdmi.length + ')';
                zaznam(hdmiText);
                try { localStorage.setItem('playcaption-hdmi', String(zdroj.number)); } catch (e) {}
                ukazStav();
            },
            chybaHdmi('show selhalo'),
            ['0px', '0px', '1920px', '1080px'],
            'MAIN',
            'BEHIND'
        );
    }, chybaHdmi('setSource selhalo'));
}

function filtrujHdmi(zoznam) {
    var vysledok = [];
    for (var i = 0; i < zoznam.length; i++) {
        if (zoznam[i].type === 'HDMI') {
            vysledok.push(zoznam[i]);
        }
    }
    return vysledok;
}

/* Stav čítania repliky v Príbehovom režime ide cez službu späť telefónu. */
function pribehStav(id, stav) {
    try {
        var ja = tizen.application.getCurrentApplication();
        tizen.messageport.requestRemoteMessagePort(ja.appInfo.packageId + '.Prijimac', NAZOV_PORTU_SLUZBY)
            .sendMessage([{ key: 'd', value: JSON.stringify({ t: 'pribehStav', id: id, stav: stav }) }]);
    } catch (e) {}
}

function vycistiText(nechajPribeh) {
    if (nechajPribeh && ui.storyActive()) {
        ui.pauseStory(Date.now());
    } else {
        ui.clear();
    }
    elTitulok.hidden = true;
    elHint.hidden = true;
    elHint.textContent = '';
    posledneId = null;
}

function spracujPaket(paket, zdroj) {
    var teraz = Date.now();
    var zmenaSpojenia = poslednyZdroj !== zdroj || (dataVidene && teraz - dataVidene > 5000);
    if (zmenaSpojenia) {
        vycistiText(paket.kind === 'story' && (!poslednyZdroj || poslednyZdroj === zdroj));
        prijemText = 'Titulky přicházejí (' + zdroj + ').';
        prijemChyba = false;
        ukazStav(4000);
    }
    dataVidene = teraz;
    posledneData = teraz;
    poslednyZdroj = zdroj;
    if (ui.handle(paket, teraz)) {
        return;
    }
    if (paket.kind !== 'story' && ui.storyActive()) {
        return;
    }
    if (paket.kind === 'story' && ui.oldStory(paket.id)) {
        return;
    }
    var novy = paket.id !== posledneId || zmenaSpojenia;
    // Opakované pakety sú len udržiavanie spojenia; v Príbehu ale nesú pauzu a čakajú na stav.
    if (!novy && paket.kind !== 'story') {
        return;
    }
    posledneId = paket.id;
    if (novy) {
        var druh = paket.kind === 'story' ? 'Příběh' : 'Titulek #' + paket.id + ' (' + paket.duration + ' ms)';
        if (paket.text) {
            zobrazene++;
            zaznam(druh + ': ' + paket.text.replace(/\n/g, ' / ').slice(0, 70));
        } else {
            zaznam(druh + ': prázdný (schovat)');
        }
        if (paket.hint) {
            zaznam('Nápověda: ' + paket.hint.replace(/\n/g, ' / ').slice(0, 70));
        }
    }
    elTitulok.hidden = !paket.text;
    titulokDo = teraz + paket.duration;
    var r = paket.layout;
    var sirka = r ? r[2] : 0.8;
    var vlavo = r ? Math.max(sirka / 2, Math.min(1 - sirka / 2, r[0])) : 0.5;
    elTitulok.style.left = (vlavo * 100) + '%';
    elTitulok.style.bottom = ((1 - (r ? r[1] : 0.91)) * 100) + '%';
    elTitulok.style.width = (sirka * 100) + '%';
    var vyska = document.documentElement.clientHeight || 1080;
    elTitulok.style.fontSize = Math.min(38 * (r ? r[3] : 1), (vyska * 0.18 - 32) / 2.7) + 'px';
    if (paket.kind === 'story') {
        titulokDo = Infinity;
        var stav = ui.story(paket, teraz);
        if (stav) {
            pribehStav(paket.id, stav);
        }
    } else {
        ui.caption(paket, teraz);
    }
    if (paket.hint !== elHint.textContent) {
        elHint.textContent = paket.hint;
        elHint.hidden = !paket.hint;
        hintDo = teraz + 20000;
    }
}

function odSluzby(data) {
    var sprava = null;
    for (var i = 0; i < data.length; i++) {
        if (data[i].key === 'd') {
            try { sprava = JSON.parse(data[i].value); } catch (e) {}
        }
    }
    if (!sprava) {
        return;
    }
    sluzbaVidena = Date.now();
    if (sprava.t === 'paket') {
        spracujPaket(sprava.paket, sprava.zdroj);
    } else if (sprava.t === 'pripravene') {
        nazovTv = sprava.nazov;
        verziaTv = sprava.verzia || '';
        if (!stavSluzby) {
            zaznam('Služba běží: ' + sprava.nazov);
        }
        stavSluzby = sprava;
        if (prijemChyba || !dataVidene || Date.now() - dataVidene > 5000) {
            var bolaChyba = prijemChyba || prijemText.indexOf('Spouštím') === 0;
            prijemChyba = false;
            prijemText = 'Příjem připraven, čekám na telefon nebo Mac. (' + sprava.node + ')';
            if (bolaChyba) {
                ukazStav(15000);
            } else {
                kresliStav();
            }
        }
    } else if (sprava.t === 'log') {
        zaznam(sprava.text);
    } else if (sprava.t === 'chyba') {
        prijemChyba = true;
        prijemText = sprava.text;
        zaznam('CHYBA služby: ' + sprava.text);
        ukazStav(60000);
    } else if (sprava.t === 'iny') {
        prijemText = 'Titulky zkouší posílat i jiné zařízení, ignoruji ho.';
        ukazStav(6000);
    }
}

function spustiSluzbu() {
    sluzbaPokus = Date.now();
    var ja = tizen.application.getCurrentApplication();
    var idSluzby = ja.appInfo.packageId + '.Prijimac';
    function zlyhalo(e) {
        prijemChyba = true;
        prijemText = 'Službu příjmu se nepodařilo spustit: ' + (e && e.message ? e.message : e);
        zaznam(prijemText);
        ukazStav(60000);
    }
    zaznam('Spouštím službu ' + idSluzby);
    try {
        var ovladanie = new tizen.ApplicationControl(
            'http://tizen.org/appcontrol/operation/service', null, null, null,
            [new tizen.ApplicationControlData('apka', [ja.appInfo.id])]
        );
        tizen.application.launchAppControl(ovladanie, idSluzby, function () {}, zlyhalo);
    } catch (e) {
        zlyhalo(e);
    }
}

function tik() {
    var teraz = Date.now();
    ui.tick(teraz);
    if (teraz >= titulokDo) {
        elTitulok.hidden = true;
    }
    if (teraz >= hintDo) {
        elHint.hidden = true;
    }
    if (dataVidene && teraz - dataVidene > 5000) {
        dataVidene = 0;
        vycistiText(true);
        prijemText = 'Spojení s odesílatelem přerušeno, čekám.';
        zaznam('5 s bez dat od odesílatele, schovávám titulky');
        ukazStav(10000);
    }
    if (typeof tizen !== 'undefined' && teraz - sluzbaVidena > 7000 && teraz - sluzbaPokus > 7000) {
        if (sluzbaPokus && !prijemChyba) {
            prijemChyba = true;
            prijemText = 'Služba příjmu neodpovídá, zkouším znovu.';
            zaznam(prijemText);
            ukazStav(60000);
        }
        spustiSluzbu();
    }
    kresliLadenie(teraz);
    if (!stavNatrvalo && teraz >= stavDo) {
        elStav.hidden = true;
        elNapoveda.hidden = true;
    }
}

function koniec() {
    try {
        tizen.tvwindow.hide(function () {}, function () {});
    } catch (e) {}
    try {
        tizen.application.getCurrentApplication().exit();
    } catch (e) {}
}

document.addEventListener('keydown', function (e) {
    if (e.keyCode === 10009) {
        koniec();
    } else if (e.keyCode === 13) {
        ladenie = !ladenie;
        stavNatrvalo = ladenie;
        try { localStorage.setItem('playcaption-ladenie', ladenie ? '1' : '0'); } catch (e2) {}
        ukazStav(ladenie ? 0 : 1);
    } else if (e.keyCode === 39 && hdmi.length > 1) {
        idx = (idx + 1) % hdmi.length;
        ukazHdmi();
    } else if (e.keyCode === 37 && hdmi.length > 1) {
        idx = (idx - 1 + hdmi.length) % hdmi.length;
        ukazHdmi();
    }
});

window.onload = function () {
    elNapoveda.textContent = 'Šipky vlevo a vpravo: jiný HDMI vstup. OK: ladění a stav. Zpět: konec.';
    try { ladenie = localStorage.getItem('playcaption-ladenie') !== '0'; } catch (e) {}
    stavNatrvalo = ladenie;
    zaznam('Aplikace spuštěna');
    ukazStav(15000);
    setInterval(tik, 250);

    if (typeof tizen === 'undefined') {
        hdmiText = 'Tohle neběží na Tizen TV (chybí objekt tizen).';
        prijemText = '';
        ukazStav(600000);
        return;
    }

    try {
        tizen.messageport.requestLocalMessagePort(NAZOV_PORTU).addMessagePortListener(odSluzby);
    } catch (e) {
        prijemChyba = true;
        prijemText = 'Spojení se službou selhalo: ' + (e && e.message ? e.message : e);
        zaznam(prijemText);
    }
    spustiSluzbu();

    tizen.systeminfo.getPropertyValue('VIDEOSOURCE', function (info) {
        var spojene = filtrujHdmi(info.connected || []);
        hdmi = spojene.length > 0 ? spojene : filtrujHdmi(info.supported || []);
        zaznam('HDMI vstupy: připojené ' + spojene.length + ', podporované ' + filtrujHdmi(info.supported || []).length);
        var ulozene = null;
        try { ulozene = localStorage.getItem('playcaption-hdmi'); } catch (e) {}
        for (var i = 0; i < hdmi.length; i++) {
            if (String(hdmi[i].number) === ulozene) {
                idx = i;
            }
        }
        ukazHdmi();
    }, chybaHdmi('Čtení zdrojů selhalo'));
};
