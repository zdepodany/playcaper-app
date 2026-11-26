# PlayCaper – backlog

Otevřená práce forku (autor: PS5 + Samsung TV + Mac, kamarád: PS5 + LG
43UK6300 + Android). Hotové položky jsou v `CHANGELOG.md` a v historii gitu,
ověřování LG ve `webos/TESTOVANI.md`.

Úklid 26. 11. 2026: odstraněny hotové PC-004, PC-005, PC-006, PC-010
(správa LG v Androidu) a PC-008 (paměť překladů, zbývající měření je v PC-012 A).
PC-003 sloučeno do PC-012. PC-007 (skládání vět pro Story) zaniká s PC-013.
Zrušen `ROADMAP.md` (plán P0–P6 z doby upstreamu): etapa P2 je příběhový
režim (PC-013), P4-02 je PC-012 B/C, nedokončené OCR body P0–P4 jsou v PC-015.
Odpadlo P5-02 (historie titulků, nápad zůstává u Macu) a P6 (OCR z HDMI přímo
na webOS a alternativní OCR; desktopová cesta je PC-009). Původní text je
v historii gitu (`git show 52baa41:ROADMAP.md`).

Navržené pořadí: PC-013 → PC-012 (A, B, C) → PC-009 → PC-001 → PC-015 → PC-011.

## PC-013 – Odstranění příběhového režimu

Stav: rozhodnuto 26. 11. 2026, nic neimplementováno.

Příběhový režim (Story, `docs/PRIBEHOVY-REZIM.md`) nepoužívá autor ani
kamarád a Mac verze ho mít nebude. Upstream se už nemerguje, takže mazání
nezpůsobí konflikty.

Rozsah (podle výskytů Story/`Pribeh`/`PCQ1`):

- Android: `Pribeh*.kt`, `PrekladPribehu.kt`, `PodporaPribehu.kt`,
  `CteciFronta.kt`, `SkladaniVet.kt`, `CasovaniPribehu.kt`, přepínač režimu
  v UI a nastavení, Story větev v `Propojeni.kt`; ~11 JVM testů,
  2 instrumentační testy.
- Přijímače: `PCQ1`/`PCS1`/`PCA1` ve `webos/service/protocol.js` a
  `webos/app/receiver-ui.js`, test `test-story`; Samsung přes `tizen/sync.py`.
- Dokumentace: `docs/PRIBEHOVY-REZIM.md`, README.

Rozhodnout při práci:

1. Story pakety v přijímačích odstranit (přepínač je u obou vypnutý, stačí
   vydat Android i přijímače společně).
2. Ověřit, že standardní režim nezávisí na `SkladaniVet` ani `CteciFronta`.
3. Uložený klíč „příběhový režim zapnutý“ po aktualizaci ignorovat.

První krok: `git grep -i "pribeh\|story\|PCQ1"` a rozdělit výskyty na
Story-only a sdílené se standardním režimem.

## PC-012 – Zrychlení cesty titulek → překlad → TV

Stav: rozbor kódu 25. 11. 2026, nic neimplementováno. Pořadí příčin je odhad
z kódu, ne měření.

### Kde se ztrácí čas

1. **Čekání na potvrzení textu** (`RemotePlayService.kt`): 200 ms na
   „vynořování“, druhé OCR pro zkrácený/dopisovaný text
   (`StabilitaTextu.prijmi`), až 2 čtení navíc při nejistém slově a až 4 při
   malém písmenu na začátku. Čtečka je jediná, čtení se sčítají.
2. **Online překlad postupně.** `Prekladac.preloz` běží na jednom vlákně a
   služby zkouší jednu po druhé; nedostupná služba stojí timeouty
   1,5 s + 2–3 s.
3. **Fronta nezahazuje staré věty.** `PlatnostPrekladu` zahodí jen výsledek;
   pomalý překlad staré věty zdrží novou.
4. **Více replik v titulku** (`prelozVse`) jde sériově, replika = požadavek.
5. UDP do TV a vykreslení v přijímači nejsou úzké hrdlo.

### Návrhy

- **A – měření fází.** Do záznamu pro každý titulek: první vidění →
  potvrzení → požadavek → odpověď → UDP, a podíl zásahů paměti překladů
  (`PametPrekladu`, PC-008) vs. DeepL/Google za jedno hraní. Udělat první.
- **B – fronta „poslední vyhrává“.** Držet jen nejnovější čekající větu.
- **C – souběh služeb.** Když první služba neodpoví do ~400–600 ms, spustit
  další a vzít první platnou odpověď. Riziko: kvóta DeepL, blokace Googlu.
- **D – spekulativní překlad** prvního OCR, jen u vět, které vypadají úplně.
- **E – dávka replik** v jednom požadavku (DeepL víc `text`, Google víc `q`).
- **F – druhá čtečka.** Pole `obsazeno[]` s víc čtečkami počítá. Riziko: CPU,
  teplo.
- **G – sady překladů** (PC-011).
- **H – udržovaná spojení** (keep-alive/HTTP/2 pro všechny služby).
- **I – volba výkonu** (přání kamaráda, Samsung S25 FE): Úsporný / Vyvážený /
  Maximální, ladí `INTERVAL_MS` (200/100/50 ms), F, D, C a čekání na
  potvrzení. Až po měření z A.
- Z dřívějšího PC-003: OCR spouštět jen při změně textové oblasti a brát
  nejnovější snímek místo fronty starých.

Rychlý náhled z ML Kitu (`RYCHLY_NAHLED`) už existuje.

První krok: A + B + C (jen Android, JVM testy, beze změny protokolu).
Odklady OCR měnit až podle měření a s ověřením při hraní (riziko useknutých
vět, PC-001).

## PC-009 – PlayCaper pro Mac

Stav: MVP v `macos/PlayCaper`, **ověřeno na zařízení** 26. 11. 2026
(GTA VI, PS Remote Play na M3, Samsung UE55CU7172 s přijímačem 1.3.0).
Popis, omezení okna a postup v `macos/PlayCaper/README.md`.

Rozhodnuto 26. 11. 2026: jen pro autora, ve stejném repu (`macos/`), nativně
ve Swiftu, výstup jen do přijímače na TV (na Macu jen menu bar a ladicí
panel), bez příběhového režimu. Pro pakety sdílené testovací vektory
(vstup → přesné bajty) pro JVM, Swift i `webos/test.cjs`.

### Další kroky

1. Volby odpovědí GTA VI (`PCV1`, port `Volby.kt` / `IkonyVoleb.kt`).
2. ~~Výběr oblasti tažením~~ (26. 11. 2026, okno Oblast…, rámeček se ukazuje
   na TV přes `PCO1`). Živý náhled okna Remote Play nefungoval, odebrán.
3. Úpravy OCR z Androidu (`Prekladac.upravSlang`), spojování rozdělených vět
   (`prelozAUkaz`), paměť překladů (PC-008).
4. Sdílené testovací vektory paketů (viz výše).

### Nápady

- Globální klávesové zkratky: pauza, znovu přeložit, ukázat originál.
- Historie titulků (EN + CZ) s hledáním.
- Glosář hry (jména postav a míst), zároveň `customWords` pro Vision.
- Ladicí panel s ukládáním vzorků do korpusu `tools/ocr`.
- Instalace a aktualizace přijímačů z Macu (`webos/build.py`, `tizen/build.py`).
- Apple Translation framework místo DeepL/Google, pokud podporuje češtinu
  (neověřeno).

## PC-001 – Úplný překlad víceřádkových textů

Stav: oprava v `main-fork` (CHANGELOG), čeká na herní přejímku.

Původní hlášení (upstream, 14. 11. 2026): z třířádkového textu zůstal
přeložený jen poslední řádek. Simulace ukázala ztrátu v `StabilitaTextu`
před překladem; stabilizace teď porovnává otisk pixelů výřezu a počet
řádků, opožděné OCR výsledky se zahazují. Limit: při měnícím se pozadí nebo
kompresním šumu ochrana nezabere a není prokázané, že šlo o jedinou příčinu.

Přejímka: při další ztrátě řádků porovnat v záznamu počty řádků a otisky a
rozlišit výpadek samotného OCR od výběru řádků, stabilizace a překladu.
Hotovo znamená: zůstane kompletní aktuální text, starší odpověď ho nepřepíše
a po změně scény nezůstane starý překlad.

## PC-015 – Kvalita OCR na Androidu podle měření

Stav: čeká na data (dříve ROADMAP P0-04, P1-02 až P1-04, P3, P4-01).
Výchozí audit a měření: `docs/P0-VYCHOZI-STAV.md`, nástroje `tools/ocr`.

Pravidlo: nejdřív stejné snímky a CER/WER před a po, pak jedna cílená změna.
Žádné pevné filtry ani opravy jednotlivých slov bez doloženého přínosu.

1. **Korpus.** Chybí problematické scény (špatná denní scéna, rychlé
   sekvence, malý text, tři řádky). Zachytit výřez před/po ořezu a ručně
   přepsat referenci. Bez toho ostatní body nejdou.
2. **Y vs. RGB a celý výřez vs. automatický pás** (`vyrezZI420`,
   `pasSTextem`): mohou vznikat zkomoleniny ještě před ML Kitem.
3. **720p vs. 1080p** (`ConnectVideoProfile`, `meritkoCteni`): stejná scéna,
   CER/WER, P50/P95, CPU, dosažené FPS. Nepřepínat naslepo; souvisí s volbou
   výkonu v PC-012 I.
4. **Horší čtení nahrazuje lepší** (`StabilitaTextu`, `Ctecka`) a případný
   druhý OCR průchod jen u nejistých výsledků.
5. **Slang a spojování vět** (`upravSlang`, `prelozAUkaz`, `opravJmena`)
   můžou měnit význam; porovnat originál, OCR, úpravu a český výsledek.

## PC-011 – Sady překladů pro hry

Stav: nápad 25. 11. 2026. Přínos závisí na měření z PC-012 A.

Paměť překladů (`PametPrekladu.kt`) je na každém zařízení zvlášť, dva hráči
stejné hry čerpají DeepL kvótu dvakrát. Sada = paměť jedné hry, kterou jde
exportovat a importovat, včetně ručních oprav a glosáře jmen.

- Pořadí hledání: ruční opravy → sada hry → osobní paměť → online.
- Hru vybírá uživatel ručně.
- Formát: hra, jazyky, verze klíče, položky `[originál, klíč, překlad,
  zdroj]`. Ukládat originál, aby šlo klíče po změně `klic()` přepočítat.
- Bez limitu 5000 vět; ručně opravený překlad se nepřepisuje.
- Glosář hry nahradí natvrdo zapsaný `CHYBNA_JMENA` v `RemotePlayService.kt`.
- Sdílení souborem (Android sdílení, git), bez serveru. Stejný formát pro Mac.

Rizika: rod mluvčího („Jsem připraven/a“, klíč mluvčího neobsahuje),
přibližná shoda je u krátkých vět nebezpečná („I can.“ × „I can't.“),
sada obsahuje velkou část scénáře hry (sdílet jen mezi sebou).

## PC-002 – Dlouhodobá přejímka LG přijímače

Stav: funkce LG jsou dorovnané s Androidem (`webos/SHODA-ANDROID.md`).
Zbývá několikahodinové hraní u kamaráda včetně uspání a návratu TV. Na
webOS 4.4 drží překryv otevřený stisk RED jednou za minutu (`keepalive.js`,
commit `30bf738`); ověřit, že při dlouhém hraní nic neruší.
