# PlayCaption: roadmapa a prioritizovaný technický plán (P0–P6)

Aktualizace 15. 11. 2026 podle sjednoceného plánu uživatele. Podklady a výchozí měření: [P0-VYCHOZI-STAV.md](docs/P0-VYCHOZI-STAV.md). Před změnou vždy znovu ověř checkout a špinavý strom.

**Cíl:** úplné a srozumitelné české titulky, zdarma, bez povinného PC/rootu. Standardní režim zachovat; volitelný Příběhový režim má přednost správnost → úplnost → čitelnost → pořadí → synchronizace.

P0 → první měřitelná P1 změna, pokud jsou data → **P2 samostatně, bez závislosti na dokončení P3/P4**. Blokované získání problematických snímků neblokuje testy P2 nad správnými českými dialogy. HDMI experiment zůstává odložený v P6.

Stavy: čeká / probíhá / dokončeno / blokováno. Dokončení úkolu není důkaz hotové celé etapy ani nasazeného produktu. Kritickou překladovou/protokolovou překážku P2 řešit v P2, neposouvat ji formálně až do P4.

## P0 — audit a měřicí základ

### P0-01 · Mapa větví a existující implementace — dokončeno

- **Problém/části:** Android OCR a LG jsou v různých větvích; pracovní strom obsahuje diagnostiku. `Ctecka`, `VyberTitulku`, `StabilitaTextu`, `RemotePlayService`, `Prekladac`, `PametObraz`, `native/pcobraz.c`, `Volby`, `Propojeni`, Android/LG prezentace.
- **Závislosti:** žádné. **Řešení:** inventura commitů, cílené čtení skutečných callsite, nic z existujícího neimplementovat znovu.
- **Testy/hotovo:** zdokumentované skutečné větve, hotové/rozpracované/chybějící funkce a zachované dirty soubory v P0 auditu. **Riziko:** lokální `origin/*` není aktuální GitHub ani důkaz runtime.

### P0-02 · Zopakovat dostupné regrese — dokončeno

- **Problém/části:** potřeba baseline před změnou; JVM testy a čtyři `webos/test*.cjs` sady.
- **Závislosti:** P0-01. **Řešení:** JVM z Android checkoutu, LG z odděleného snapshotu `0b7a1df`.
- **Testy/hotovo:** 44 JVM testů a všechny čtyři LG sady prošly; hranice důkazů jsou zapsané. **Riziko:** nejsou to testy živého ML Kit ani herní přejímka.

### P0-03 · Reprodukovatelný OCR runner a CER/WER — dokončeno

- **Problém/části:** šestivariantový runner a 42 výsledků byly jen v dočasných datech. `OcrKvalityBenchmark.kt`, `tools/ocr/*`.
- **Závislosti:** P0-01. **Řešení:** přenést existující diagnostický runner do androidTest, doplnit SHA vstupů, standardní metriky a oddělené APK ID; žádné produkční filtry.
- **Testy/hotovo:** 8 testů skórování, ruční reference 7 PNG / 4 textů, baseline všech 6 variant, sestavení obou diagnostických APK. **Riziko:** nový runner na zařízení ještě neběžel; historická měření nemají hashe. PNG/JSON zůstávají lokálně ignorované, přenos na jiný počítač vyžaduje dodání dat.

### P0-04 · Chybějící referenční korpus a nový běh — blokováno

- **Problém/části:** chybí původní špatná denní scéna a připojený Android. Lokální diagnostické PNG/JSON, nový benchmark.
- **Závislosti:** P0-03 a dostupnost scén/zařízení. **Řešení:** zachytit před/po ořezu, původní YUV/RGB a ručně přepsat stejné vstupy; štítky den/noc/pohyb/font/HUD/mluvčí/počet řádků/typewriter.
- **Testy/hotovo:** reprodukce chyby + nová inference s hashi, samostatně metadata/CPU/RAM/FPS. Rozšířit o rychlé sekvence, malý text, instrukci a tři řádky. **Riziko/bloker:** správné noční výřezy nenahrazují špatný vstup; tento úkol neblokuje P2.

## P1 — kvalita vstupu podle měření

### P1-01 · Audit rozlišení, škálování a barevné cesty — dokončeno

- **Problém/části:** vyšší rozlišení může být před OCR opět zmenšeno. `RemotePlayService`, `PametObraz`, Chiaki wrapper, native převod.
- **Závislosti:** P0-01. **Řešení:** doložit požadovaný profil, vzorec měřítka, Y-only bitmapu a hranice decoder formátů.
- **Testy/hotovo:** zdrojový audit v P0 dokumentu; není označený za provozní benchmark. **Riziko:** konfigurace není dosažená kvalita; bez dat žádná změna konstant.

### P1-02 · Y/RGB a celý výřez proti automatickému pásu — čeká

- **Problém/části:** zkomoleniny mohou vzniknout před ML Kit. `vyrezZI420`, `pasSTextem`, benchmarkové varianty.
- **Závislosti:** P0-04. **Řešení:** stejné snímky, stejná geometrie; Y-only/RGB, původní oblast/ořez, rozumné měřítko; podle potřeby lokální kontrast.
- **Testy/hotovo:** CER/WER + obsahová úplnost řádků na dobré i špatné sadě, časy fází; přínos bez regrese doložený. **Riziko:** šedé PNG nelze zpětně rekonstruovat na původní RGB; nepřidávat pevný filtr bez přínosu.

### P1-03 · Řízené 720p/1080p A/B — čeká

- **Problém/části:** vyšší profil může pomoct i přetížit dekodér. `ConnectVideoProfile`, JNI rozměry/stride, `meritkoCteni`.
- **Závislosti:** P0-04, P1-01. **Řešení:** dočasný diagnostický profil a zachování skutečných detailů na vstupu OCR, jedna proměnná na srovnání.
- **Testy/hotovo:** stejná scéna/zařízení, CER/WER, P50/P95, CPU/RAM, dosažené rozměry/FPS, stabilita. **Riziko:** nepřepnout všem 1080p/60 jen podle existence presetů.

### P1-04 · Jedna doložená produkční změna — čeká

- **Problém/části:** měření má vést k cílené opravě, ne sadě heuristik. Konkrétní obrazový callsite určí P1-02/03.
- **Závislosti:** prokázaný přínos v P1-02 nebo P1-03. **Řešení:** nejmenší změna se samostatnou regresí a porovnáním baseline.
- **Testy/hotovo:** stejná sada před/po, žádná regrese dobrých scén, build/JVM/ML Kit a herní přejímka podle dopadu. **Riziko:** pokud přínos není, úkol uzavřít bez změny; P2 může pokračovat nezávisle.

## P2 — Příběhový režim MVP

### P2-01 · Samostatná větev a rozhodnutí o vlastnictví — dokončeno

- **Problém/části:** čtecí fronta není síťová fronta překladače; LG zná skutečné stránky. Android/LG prezentace, `Propojeni`, `PlatnostPrekladu`.
- **Závislosti:** P0-01/02, **ne dokončení P1/P3/P4**. **Řešení:** připravit `feature/story-mode` z P0 základu bez upstreamu; před integrací doplnit LG větev bez duplicit. Prověřit jednu čtecí frontu v Androidu a dokončení aktuálního cue přijímačem podle P0 návrhu.
- **Testy/hotovo:** explicitní vlastník fronty, virtuální vstupy/výstupy, vyřešená vazba na poslední stránku a minimální kompatibilní přenos. **Riziko:** dvě nezávislé fronty nebo odhad času podle celého textu mohou znovu ztrácet stránky. P0 byl pouze návrh; navazující MVP už používá PCQ1/PCS1/PCA1 při zachovaném Standardním PCT1.
- **Postup:** větev `feature/story-mode` aktivní. Android je jediný vlastník čtecí fronty; LG drží jedinou repliku, měří její stránky a potvrzuje dočtení. Přenos a dočtení prošly na C5. Viz [kontrakt fronty](docs/PRIBEHOVY-REZIM.md).

### P2-02 · Identita repliky a její revize — dokončeno

- **Problém/části:** globální token zahazuje platné A po příchodu B. `StabilitaTextu`, `RemotePlayService`, deterministické testy.
- **Závislosti:** P2-01. **Řešení:** rozlišit relaci/pořadí repliky/revizi; existující událost nové repliky či `oprava` využít, ne znovu přepisovat OCR. Zachovat jméno a barvu mluvčího.
- **Testy/hotovo:** pozdní platné A zachované, stará revize A odmítnutá, opakované čtení neduplikuje, nová podobná replika se neztratí; Standardní regrese zůstávají. **Riziko:** identita OCR bloku není identita cue; pokročilý odhad hypotéz patří do P3.
- **Postup:** token relace/pořadí/revize a ochrana rozečteného obsahu hotové v `CteciFronta`; simulace ověřují starý callback i dvě samostatná „Ano.“. Stávající OCR události nové/opravované repliky a barvy jmen jsou napojené přes `PribehovaRelace`; nová OCR klasifikace se nezaváděla.

### P2-03 · Čtecí politika s virtuálním časem — dokončeno (MVP Android + LG)

- **Problém/části:** nové výsledky nesmí přerušit čtení A. Malá samostatně testovatelná politika + skutečný zobrazovací callsite.
- **Závislosti:** kontrakt P2-01/02; správné simulované překlady stačí. **Řešení:** pořadí A/B/C nezávislé na dokončení překladu; čas české stránky `ceil(max(1500 ms, ceil(Unicode code points × 1000 / 18)) × procenta × 80 / 10000)` při výchozích 100 %, poslední stránku dočíst, první volnou ukázat ihned.
- **Testy/hotovo:** krátký/jednoslovný/dlouhý/vícestránkový text, emoji/diakritika, rychlé A/B/C, pauza vyprázdní frontu, přesné časy a počet zobrazení. **Riziko:** definovat bounded paměť a viditelné přetížení, bez tichého zahazování a preventivního agresivního backlog managementu.
- **Postup:** samostatné jádro, virtuální čas, dočtení poslední stránky, pauza se zbývajícím časem a explicitní kapacitní limity implementované; 33 nových testů a 44 původních JVM testů prošlo, APK se sestavila. Zobrazovací callsite je napojený přes potvrzení LG; uživatel přijal hraní a nové časování na C5. Podrobnosti v aktuálním závěru níže.

### P2-04 · Napojení překladače bez ztráty pořadí — dokončeno

- **Problém/části:** online náhled/final, retry a pozdní callback. `Prekladac`, `prelozAUkaz`, `ukazJestli`.
- **Závislosti:** P2-02/03. **Řešení:** work queue oddělit od read queue, aktualizace jedné revize nevkládá další cue; konečný výsledek/fallback v omezeném rozpočtu. Kritickou blokaci odstranit zde, ne čekat na P4.
- **Testy/hotovo:** B hotovo před A, chyba/timeout A neblokuje věčně B, offline náhled později než final, změna relace během retry. **Riziko:** nesmí se začít ukazovat původní anglický text jako úspěšný český překlad bez rozlišení chyby.

### P2-05 · LG/Android zobrazení a kompatibilní doručení — dokončeno (MVP Android + LG)

- **Problém/části:** LG nyní ruší nedočtené stránky a heartbeat přenáší jen poslední stav. `Propojeni`, `TelefonTitulky`, `OverlayService`, LG parser/služba/receiver UI.
- **Závislosti:** P2-01/03/04. **Řešení:** zvolená jediná autorita čtení, receiver pouze aktuální cue/stránky, idempotentní ID/revize/relace a případné minimální potvrzení; capability fallback ponechá PCT1 Standardní režim funkční.
- **Testy/hotovo:** stejné heartbeat nezrestartují stránku/frontu; ztráta/duplicitní/opožděný UDP, restart a obnova, poslední stránka, omezený box. **Riziko:** starý přijímač nesmí nepozorovaně dostat nepodporovaný Story režim; síťová garance přesně jednou není splněná pouhým dvojím odesláním UDP.

### P2-06 · Přepínač, změna relace a interaktivní obsah — dokončeno (MVP Android + LG)

- **Problém/části:** hráč potřebuje volitelný režim a včasné volby. `Nastaveni`, `TelefonActivity`/nastavení, `Volby`, oba výstupy.
- **Závislosti:** P2-03/05. **Řešení:** Standardní výchozí; změna jazyka/oblasti/konzole/relace resetuje příslušnou frontu a tokeny. Volby/text mají vlastní politiku; aktivní interaktivní volby mohou pozastavit Story čtení a potom ho obnovit.
- **Testy/hotovo:** přepínání za běhu, zpožděné odpovědi ze starého režimu, reset, volby během A, pokračování s nezkráceným zbývajícím časem. **Riziko:** nevázat život české fronty na zmizení anglického OCR textu; změna nastavení musí být oběma výstupům srozumitelná.

### P2-07 · MVP přejímka a metriky — dokončeno (MVP Android + LG)

- **Problém/části:** funkce potřebuje prokazatelné čtení, ne jen správný finální string. JVM/JS simulace a přejímka obou režimů.
- **Závislosti:** P2-02 až 06. **Řešení:** 16 scénářů ze zadání, řízené hodiny; počítat zobrazené/ztracené/duplicitní/přerušené cues, maximum fronty a čekání P50/P95, čas vyprázdnění po pauze.
- **Testy/hotovo:** všechny přijaté platné samostatné cues v běžných scénářích právě jednou, správné pořadí, celá doba každé stránky; samostatná herní přejímka pak P5. **Riziko:** syntetické správné překlady dokazují zobrazovací politiku, nikoliv kvalitu OCR/překladu.

### Integrační stav P2 před sjednocením TV větví (historický záznam)

- Implementované napojení překladů přes `PribehovaRelace` a `PrekladPribehu`: jeden finální výsledek, rozpočet 8 s, online práce bez další fronty, offline záloha a odmítnutí starých revizí.
- `TelefonActivity` má skutečná tlačítka Standardní/Příběhový, Standardní výchozí; délka zobrazení 50–200 % po 5 %, výchozí 100 % kratšího základu. Reset nastavení/relace a pauza pro volby jsou zapojené. PCQ1/PCS1/PCA1 je samostatná potvrzovaná cesta; Standardní PCT1/PCV1/PCO1 zůstaly.
- 101 JVM testů bez selhání; 5 LG sad a 3 instrumentační testy na tabletu. Přímý C5 test: celé tři stránky, žádné přetečení, ACK po dočtení, následně Standardní titulek a ignorování starého Story paketu.
- Opravené první Android runtime selhání `NetworkOnMainThreadException`: otevření, obsluha i zrušení UDP běží na pracovním vlákně. Dva instrumentační testy na Redmi prošly se StrictMode a skutečným potvrzením od C5.
- Na tabletu `PlayCaption Test 0.20.1-story-test03`, LG 0.0.9. Uživatel u předchozího story-test02 potvrdil, že po opětovném připojení PS5 chyba zmizela a titulky běží; komfort nového časování story-test03 ještě nepotvrdil. Zbývá podrobná herní přejímka úplnosti rychlých replik, návratu do Standardního režimu, skutečných dialogových voleb a dlouhodobých výpadků; telefonní dvouřádkový stránkovač není součást tohoto MVP.

## P3 — pokročilé OCR pouze podle výsledků P1

### P3-01 · Volba hypotézy a zachování správného čtení — čeká

- **Problém/části:** horší revize někdy nahradí lepší. `StabilitaTextu`, `Ctecka`, cue identita.
- **Závislosti:** P1 data + P2-02 kontrakt. **Řešení:** porovnat čas/text/geometrii/mluvčího a kvalitu obrazu; confidence jako pomocný nekalibrovaný signál, ne slepé dvě ze tří.
- **Testy/hotovo:** revize vs nová podobná věta, změna mluvčího, typewriter, chybějící řádek, návrat správné hypotézy; lepší CER/úplnost bez ztrát nových cues. **Riziko:** žádné ruční opravy konkrétních chybných slov.

### P3-02 · Adaptivní druhý OCR průchod — čeká

- **Problém/části:** nejistý snímek může potřebovat alternativu; část fallbacků již existuje v `cti()`. Benchmark, produkční OCR callsite.
- **Závislosti:** P1-02, P3-01. **Řešení:** jen nejisté výsledky a omezený čas; nový snímek/širší oblast/měřítko pouze s prokázaným přínosem.
- **Testy/hotovo:** nezdržuje dobré první čtení a nové dialogy, doložený přínos vs režie. **Riziko:** nesmí trvale násobit všechna OCR; bez přínosu neimplementovat.

## P4 — překlad a práce

### P4-01 · Slang a kontext bez přidávání starých vět do výstupu — čeká

- **Problém/části:** `upravSlang()` a spojování mohou měnit význam. `Prekladac`, `prelozAUkaz`, `opravJmena`.
- **Závislosti:** P0-01; nezávislé na dokončení P3. **Řešení:** srovnat originál/OCR/postprocessing a význam české věty, kontext odlišit od zobrazované aktuální repliky.
- **Testy/hotovo:** krátké navazující věty, jména, slang i běžná slova; žádné zdvojování/narůstání titulku. **Riziko:** bez kontextového API je spojování vědomý kompromis, ne obecná oprava.

### P4-02 · Omezená fronta práce a strategie finálního překladu — čeká

- **Problém/části:** `newSingleThreadExecutor`, retry a fallback mohou zdržovat novou práci. `Prekladac` a odběratelé callbacků.
- **Závislosti:** P2 kontrakty pro revize/identitu. **Řešení:** rušit jen nepotřebné revize, zachovat samostatná platná cues; časový rozpočet a offline fallback, zdarma výchozí.
- **Testy/hotovo:** timeouty/výpadky několika služeb, změna jazyka, uzavřený translator, pořadí a žádné dvojí vložení. **Riziko:** neoficiální bezplatné služby nemají garantovanou dostupnost; blokující chyby P2 se opravují již v P2-04.

## P5 — UX a dlouhodobá přejímka

### P5-01 · Hraní a skutečný backlog — čeká

- **Problém/části:** neznáme běžnou čtecí prodlevu a komfort. Oba režimy na Androidu/LG C5.
- **Závislosti:** P2 MVP. **Řešení:** dlouhá cutscéna, rychlý telefonát, pauza, mise/akce a odpovědi; nejprve měřit skutečné zpoždění.
- **Testy/hotovo:** ztráty/duplicity/přerušení, čtení poslední stránky, návrat po výpadku, backlog a dočtení po pauze; známé regresní scény. **Riziko:** neoptimalizovat hypotetickou nekonečnou frontu proti běžnému hráči.

### P5-02 · Historie, přeskočení a rychlost čtení — částečně hotovo

- **Problém/části:** hráč může chtít dočíst minulý text nebo dohnat děj. Omezená historie a nastavení/UI.
- **Závislosti:** P5-01 a zvolený vlastník čtení. **Řešení:** jednoduchá historie se jmény/pořadím, případný návrat k aktuálnímu a procentní délka zobrazení už implementovaná podle herní zpětné vazby; historie a přeskočení zůstávají odložené.
- **Testy/hotovo:** srozumitelné ovládání, limit paměti, žádné duplikování při návratu. **Riziko:** historie není podmínka prvního P2 MVP.

## P6 — odložený výzkum

### P6-01 · HDMI capture / OCR přímo na webOS — čeká

- **Problém/části:** možnost provozu bez Androidu, ale zatím jen capture důkaz. `experiments/webos-hdmi-capture-probe`.
- **Závislosti:** nové odůvodnění návratu k výzkumu, žádná závislost hlavního vývoje. **Řešení při návratu:** persistentní SSAP bez opakované registrace/spouštění child, rozdělit režii a capture; případně ručně vyvolaná statická obrazovka.
- **Testy/hotovo:** stabilita/latence/kvalita/HDR/VRR a vhodný OCR engine doložené; zachovat experiment, testy, dokumentaci. **Riziko:** 540p a tři snímky nejsou důkaz celého překladače; bez rootu, bez změny funkčního přijímače nyní.

### P6-02 · Alternativní OCR/server/API — čeká

- **Problém/části:** možné budoucí vyšší kvalitativní požadavky. Samostatný výzkum OCR/PC/macOS/služeb.
- **Závislosti:** prokázaná nedostatečnost současné cesty. **Řešení:** srovnat měřením; placené API jen volitelně s vlastním klíčem, nikdy povinná cesta.
- **Testy/hotovo:** přínos/náklady/latence a ochrana klíčů doložené. **Riziko:** nový engine/server nesmí blokovat současný bezplatný provoz ani P2.

### Navazující integrace správy LG – 15. 11. 2026

`feature/story-mode` v commitu `3733f39` zahrnuje správu TV z `484ed6e`.
132 JVM testů a šest LG sad prošlo. Společná testovací APK s uloženým
spárováním je na Redmi, LG 0.2.0 na C5. Tři Android instrumentační testy
a řízené zobrazení čtyř skutečných LG stránek včetně skrytí a následného
Standardu prošly. Podrobnosti a limity: `PRIBEHOVY-REZIM.md`.
Nová herní přejímka zůstává otevřená; dokončování vět/OCR se zde neměnilo.

### Stav před uzavřením Story etapy

Uživatel potvrdil hraní s `story-platforms-test02` a LG 0.2.1 jako vyhovující.
Základní Story funkce a nové časování jsou tím uživatelsky přijaté v testované
konfiguraci. Zbývá závěrečný simulační přehled metrik P2-07, kontrola změn
a předání větve. Aktuální `origin/master` již obsahuje PR #3 a opravu
Windows cest v LG distribučním ZIPu (`aba0735`); tu je potřeba přibrat do Story.
Dokončování vět, další OCR optimalizace a veřejný release zůstávají mimo toto
uzavření.

### Uzavření Story MVP pro kontrolu a merge

Větev `codex/story-mode-integration` obsahuje aktuální master `aba0735` včetně
Windows `as_posix()` opravy LG ZIPu. Story i dřívější OCR opravy zůstávají
zachované. Uživatel přijal hraní s LG 0.2.1 a zkráceným výchozím časováním.

P2-07 uzavírá test `metrikyRychlehoDialoguNaVychozimCasovani`: šestnáct
správně přeložených replik po 100 ms, časování 100 %. Zobrazeno/dočteno 16,
ztráty/duplicity/předčasná přerušení 0, maximum fronty včetně čtené repliky 15,
čekání P50 7700 ms / P95 16500 ms (nearest rank), vyprázdnění 17700 ms po
posledním příchodu. Jde o záměrně přetíženou simulaci, nikoliv latenci GTA V.
Další regresní scénáře pokrývají pauzu, revize, pořadí callbacků a limity.

Finální kontroly: 133 JVM testů, šest Node sad přijímače, osm Python testů
OCR skórování, sestavení Android APK / instrumentačního APK a LG IPK.
Nový přímý Windows build nebyl proveden. Telefonní dvouřádkový stránkovač,
dokončování vět a další OCR výzkum
zůstávají mimo přijatý rozsah MVP. PR slouží ke kontrole a sloučení, ne k vydání.
