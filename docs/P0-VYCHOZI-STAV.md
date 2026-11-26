# P0: skutečný stav a výchozí měření

15. 11. 2026. Navazuje na sjednocený plán uživatele; původní roadmapa P0–P6 byla 26. 11. 2026 zrušena, otevřené body jsou v [BACKLOG.md](../BACKLOG.md) (PC-015). Příběhový režim je P2, nikoliv odměna za dokončení všech OCR optimalizací.

## Checkout a rozsah

Audit začal na `experiment/webos-hdmi-capture`, HEAD `8e70ced`. Pro P0 vznikla `chore/development-baseline` přímo z Android základu `8e70ced`, bez upstreamu. Nevznikl merge Android/LG větví, nová verze produktu ani instalace na zařízení.

| Větev / commit | Co skutečně obsahuje |
| --- | --- |
| `master`, lokálně pozorované `origin/master`: `95b3cc3` | Android 0.20.1; není v něm současná OCR oprava ani LG přijímač. Remote-tracking stav není nové ověření GitHubu. |
| `feature/ocr-text-blocks`: `80e7a41` | Seskupení bloků, stabilita, token platnosti a testy. |
| `bugfix/subtitle-cues`, `bugfix/ocr-image-quality`: `8e70ced` | Stejný commit; navíc ochrana proti spojování opakovaných OCR čtení. Ne dvě různé hotové opravy kvality obrazu. |
| `feature/lg-webos-receiver`: `eb9ee5c` | Základ samostatného LG přijímače. |
| `feature/lg-hdmi-lifecycle`: `2bffee2` | LG výběr HDMI a návrat overlay. |
| `feature/lg-choices-frames`: `1e968cf` | LG volby, Text, rámečky a barvy jmen. |
| `bugfix/lg-subtitle-pages`: `0b7a1df` | LG 0.0.7, omezení dialogu na dvouřádkové stránky. Vychází přímo z `95b3cc3` přes LG commity, neobsahuje Android OCR větev. |
| `experiment/webos-hdmi-capture` | Oddělený HDMI experiment; při auditu byly jeho zdroje/testy/výsledky stále lokální necommitované soubory. Výzkum zůstává odložený. |

Předchozí lokální změny zůstaly netknuté: 21 diagnostických řádků v `RemotePlayService.kt`, konzultační Markdowny, experiment a dřívější `webos/dist` artefakty. Diagnostický kód se nesmí omylem přibalit do P0 commitu. V aktuálním Android checkoutu není LG zdrojový strom; LG testy proto běžely nad odděleným `git archive` commitu `0b7a1df`, bez přepínání větve nebo změny TV.

## Co již existuje a co chybí

| Komponenta | Potvrzeno ve zdrojích | Hranice / další práce |
| --- | --- | --- |
| `Ctecka.kt`, `VyberTitulku.kt` | ML Kit, geometrie řádků/bloků, spojování více řádků, mluvčí, filtry HUD/jazyka | ID bloku platí uvnitř snímku; není to identita příběhové repliky napříč časem. Confidence ještě není použitá jako kalibrovaná kvalita. |
| `StabilitaTextu.kt` | Potvrzování zkrácení, prázdného čtení a dopisování; ochrana před některým opakovaným čtením | Chybí výběr nejlepší hypotézy s doloženou kvalitou; podobná nová replika není totéž co revize. |
| `PlatnostPrekladu` | Jediná globální verze, `novy()` zneplatní předchozí výsledek | Vhodná ochrana aktuálního Standardního režimu. Pro Story je nutné oddělit relaci, identitu repliky a revizi; globální zrušení by ztrácelo platné A po příchodu B. |
| `RemotePlayService.kt` | PS5/Xbox snímky, Y-only výřez, automatický/ruční ořez, stabilizace, překlad, náhled a aktuálnost výsledku | Neobsahuje čtecí frontu. `prelozAUkaz()` spojuje některá pokračování; `ukazJestli()` navíc kontroluje globální token a `posledniUkazany`. Samotná náhrada tokenu nestačí. |
| `Prekladac.kt` | Cache, jeden síťový executor, retry několika služeb, offline záloha a rychlý náhled | Zastaralá práce se tokenem neruší ve frontě. Pomalé retry mohou blokovat novější požadavky. Náhled a finální výsledek nejsou dvě samostatné příběhové repliky. |
| `TextVRamecku`, `Volby.kt` | Celý ručně vybraný odstavec a oddělený seznam voleb | Zachovat oddělené politiky; neřadit interaktivní volby za Story dialogy. |
| `Propojeni.kt` | PCT1/PCV1/PCO1; odeslání dvakrát a opakování aktuálního stavu každou sekundu | Heartbeat není nová replika. Přenos neobsahuje potvrzení dokončení čtení ani historii všech mezilehlých replik; není to garantovaná fronta událostí. |
| LG `receiver-ui.js` | Dvě řádky, stránky podle skutečné geometrie, neopakuje stránku při stejném textu/layoutu, samostatné volby/rámečky | Nový odlišný titulek resetuje stránkování. Čas stránky je nyní `max(2000, 1000 + UTF16Length × 50)` ms a omezuje ho platnost paketu; není to Story model 14 Unicode znaků/s s minimem 1,8 s. |
| Nastavení | Standardní ovládání titulků, oblastí a voleb | Příběhový přepínač, čtecí fronta a historie nejsou implementované. |

Tyto nálezy nejsou důvodem přepsat existující komponenty. P2 má nejprve využít existující příjem a překlad, přidat identitu/čtecí politiku a ponechat Standardní chování.

## P1: co už lze říct ze zdrojů

- `RemotePlayService` požaduje na Android telefonu/tabletu 1280 × 720, 30 FPS a 5000 kbit/s; H.265 při dostupnosti. To nejsou naměřené přijaté FPS/datový tok.
- Chiaki wrapper nabízí `RES_1080P` a `FPS_60`, `ConnectVideoProfile` přijímá rozměry/FPS. To dokládá možnost požadavek sestavit, nikoliv stabilitu konkrétního profilu na Redmi.
- `meritkoCteni = 1.4 × 960 / šířkaStreamu`: 720p dává 1,05×, 1080p 0,70×. Stejně relativně široký výřez proto vyjde na přibližně stejnou šířku OCR bitmapy. Vyšší zdroj nemusí automaticky přinést více detailů na vstupu ML Kit.
- `vyrezZI420()` respektuje stride rovin a do ARGB výřezu kopíruje pouze Y. U/V se uchovávají zvlášť pro barvu mluvčího. Z uložených šedých PNG nelze zpětně získat původní RGB a poctivě rozhodnout Y/RGB.
- `pasSTextem()` hledá světlé hrany pomocí pevných prahů a zúží oblast. Je to kandidát k porovnání s celým výřezem, ne potvrzená příčina konkrétního denního selhání.
- `PametObraz.kt` přebírá I420 přes JNI; `native/pcobraz.c` interpretuje stride/slice-height a převádí část formátů jako NV12. Správnost na všech vendor formátech není doložená. P1 má nejprve zaznamenat skutečný výstup dekodéru; plošný přepis není součást P0.

P0/P1 audit nepřepnul profil na 1080p ani nezměnil filtry, překlad, protokol nebo časování.

## Opakované deterministické kontroly při P0

| Kontrola | Aktuální výsledek | Co nedokládá |
| --- | --- | --- |
| Java 21, SDK 34, `bash gradlew :app:testDebugUnitTest` | **44 testů**, 0 chyb/selhání/přeskočení: geometrie 13, stabilita/platnost 16, Text 10, simulované OCR sekvence 5 | Neprovádí živé ML Kit, síťové překlady ani celý Service. |
| `node webos/test.cjs`, `test-udp.cjs`, `test-input.cjs`, `test-receiver.cjs` nad `0b7a1df` | **Všechny čtyři sady prošly** v odděleném snapshotu; UDP test používá skutečné loopback sockety | Neproběhla nová herní přejímka ani změna instalované TV. |
| `python3 -m unittest discover -s tools/ocr -v` | **8 testů metrik**: editace, Unicode, vážení, prázdné čtení, shodné pokrytí variant, duplicity, hash a neplatné časy | Nehodnotí kvalitu ML Kit modelu. |

Dřívější dva instrumentační testy jsou zdokumentované v `OCR-AUDIT.md`. Při tomto P0 `adb devices -l` vrátil prázdný seznam; nová Android inference tedy neproběhla. Sestavení nového benchmarkového APK je odlišné ověření od jeho běhu.

## Výchozí OCR skóre z již uložených dat

Sedm ručně přečtených PNG, **čtyři odlišné texty**, šest variant = 42 dřívějších ML Kit čtení na Redmi. Sada obsahuje jedno- a dvouřádkový text z dobře čitelné scény. Pásy byly už dříve zúžené kolem nalezeného textu; nejde o přehrání celého produkčního ořezu a filtrů.

Reference a parametry normalizace: `tools/ocr/reference-good-scene.json`, [návod](../tools/ocr/README.md). CER/WER jsou citlivé na velikost písmen a interpunkci, po NFC a sloučení whitespace.

| Varianta | CER | WER | Přesný obsah | Shodný počet řádků | OCR P50 / P95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1× původní velikost | 0,483 % | 2,273 % | 5/7 | 7/7 | 126 / 225 ms |
| 1,5× | 0 % | 0 % | 7/7 | 7/7 | 91 / 130 ms |
| 2× | 1,208 % | 4,545 % | 4/7 | 6/7 | 122 / 154 ms |
| 3× | 0,242 % | 2,273 % | 6/7 | 7/7 | 138 / 170 ms |
| 2× inverze | 0,242 % | 2,273 % | 6/7 | 7/7 | 117 / 156 ms |
| 2× pevný práh 160 | 3,140 % | 17,045 % | 2/7 | 7/7 | 105 / 132 ms |

**Z této tabulky nezavádíme plošně 1,5× ani žádný filtr.** Vzorek je malý, opakuje podobné vstupy, neobsahuje původní špatnou scénu a pořadí variant/cold start nebylo randomizované. P95 ze sedmi čtení je maximum této malé sady, ne stabilní populační odhad. Shodný počet řádků může skrývat chybný obsah.

Původní JSON neobsahoval hashe vstupů. Dnešní uložené PNG odpovídají manifestu SHA-256, ale historická vazba výsledků na přesné bajty není nezávisle potvrzená. Nový runner hash ukládá. Z toho důvodu staré výsledky nejsou zaměňované za čerstvě provedený hashově ověřený benchmark.

## Co potřebujeme ještě změřit

| Chybějící důkaz | Stav / dopad |
| --- | --- |
| Špatná denní GTA scéna před ořezem, po ořezu, původní YUV/RGB, ruční přepis | **Blokované měření**, data zatím nejsou. Brání závěru o příčině denních zkomolenin, ne P2 simulacím. |
| Tři řádky, malé písmo, více mluvčích, typewriter, HUD, statická instrukce a rychlé sekvence ze skutečné hry | Čeká; syntetické testy existují jen pro část případů. Rozšířit korpus před obecnými závěry P1/P3. |
| Nový ML Kit běh identických PNG s hashi | **Blokováno nepřipojeným Androidem**; runner/build lze připravit nezávisle. |
| Y-only vs skutečné RGB / širší oblast | Potřebuje původní barevný snímek, ne obarvení šedého PNG. |
| 720p vs 1080p, datový tok, skutečné FPS, CPU/RAM, latence fází | Čeká na řízený A/B běh na stejném zařízení/scéně. Nehodnotit pouze nastavené konstanty. |
| Kvalita překladu a `upravSlang()` | Samostatně posoudit význam; CER není metrika správné češtiny. |
| Ztracené/duplicitní/předčasně ukončené repliky, backlog, dočtení po pauze | P2 nejprve deterministicky s hotovými českými překlady a virtuálním časem, pak herní měření P5. |

## P2: návrh malé samostatné změny

P2 nesmí čekat na odstranění všech OCR chyb. První testy mohou přijmout správné české repliky A/B/C se zadanou identitou, revizí a časy; nevyžadují Android zařízení ani PS5/LG.

Preferovaný návrh pro ověření v P2: **jedna čtecí fronta v Androidu**, příjem/výroba dalších překladů pokračuje nezávisle. LG vlastní pouze stránky aktuálně zobrazované repliky, ne druhou frontu celého příběhu. Telefonní zobrazení musí používat tutéž autoritu.

Zásadní otevřený bod: počet a zalomení stránek na LG určuje skutečné měření DOM. Android nemůže z celkového počtu znaků bezpečně odhadnout dokončení poslední stránky. P2 má nejprve simulací ověřit malé oznámení dokončení aktuální repliky/stránky od přijímače s relací, identitou a revizí, idempotentním opakováním a detekcí podporované verze. Teprve pak navrhnout konkrétní kompatibilní rozšíření přenosu. **PCT1 se v P0 nemění.** Pokud se ukáže tento návrh zbytečně složitý, porovnat vlastnictví na aktivním přijímači; nezavést současně dvě čtecí fronty stejné relace.

Časování stránky: `max(1800 ms, počet Unicode code pointů české stránky × 1000 / 14)`, zaokrouhlit nahoru. Žádné čekání před první volnou replikou. Nová replika nekrátí čas předchozí, oprava čekající revize nevytváří novou položku. Aktuální stránka se nepřeklápí při každém heartbeat. Standardní režim si zachová současnou náhradu.

Rozhodnutí, která musí být před napojením UI otestovaná: pozdní překlad A před hotovým B, selhání překladu bez trvalého zablokování fronty, změna jazyka/oblasti/relace, odpojení přijímače a priorita interaktivních voleb. Bez tichého zahazování, bez neomezené paměti. Historie a propracované řešení hypoteticky velkého backlogu jsou až P5.
