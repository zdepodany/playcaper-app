# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Co to je

PlayCaption: Android aplikace (Kotlin) pro telefon/tablet, která přes Chiaki Remote Play (PS5) čte anglické titulky ze hry (OCR), překládá je do češtiny a zobrazuje je v telefonu, nebo je po UDP pošle „přijímači“ titulků (HTML/JS) na LG webOS či Samsung Tizen, který je ukáže přes HDMI obraz na TV. Fork podporu Xboxu a Android TV / Google TV odebral (oba uživatelé mají PS5, jeden Samsung a druhý LG TV). Dokumentace, komentáře i commity jsou česky.

## Fork a git workflow

Toto je fork `zdepodany/playcaper-app` původního `AdamRuzick4/PlayCaption-app` (AGPLv3), používaný pro autora forku a kamaráda.

- `origin` = fork, `upstream` = původní repo. `master` je jen zrcadlo `upstream/master` (pouze `--ff-only`), vlastní práce je ve výchozí větvi `main-fork` a v odbočkách z ní.
- `applicationId` (`cz.titulky.tv`) se **nesmí měnit**: aktualizace u uživatelů forku by se rozbily.
- Fork se jmenuje **PlayCaper** (repo `playcaper-app`, `app_name` v `strings.xml`). Název a logo „PlayCaption“ jsou podle README vyhrazeny původnímu autorovi a upravené verze mají mít jiný název. Zbylé výskyty „PlayCaption“ v kódu, textech UI a dokumentaci zůstávají záměrně, aby se snadno mergoval upstream; protokolové identifikátory (`PLAYCAPTION_TV`, `cz.playcaption.overlaytest`) se nesmí měnit.
- Aktualizace z upstreamu: `git fetch upstream && git checkout master && git merge --ff-only upstream/master && git checkout main-fork && git merge master`.

## Příkazy

Potřeba: JDK 17+ (Gradle s Java 21/25 funguje), Android SDK, Python 3.9+, Node.js 20+ s npx.

```sh
bash gradlew :app:assembleDebug                      # APK (před tím automaticky sestaví LG IPK a Samsung WGT a přibalí do assets)
bash gradlew :app:testDebugUnitTest                  # všechny JVM testy (app/src/test)
bash gradlew :app:testDebugUnitTest --tests cz.titulky.tv.StabilitaTextuTest   # jedna třída
bash gradlew :app:assembleDebugAndroidTest           # jen sestaví instrumentační APK (běh vyžaduje zařízení)

python3 webos/build.py        # LG: testy, syntaxe, IPK + ZIP do webos/dist/ (+ receiver-manifest.json)
node webos/test.cjs           # jednotlivé LG testy: test-udp, test-input, test-receiver, test-controls, test-story, test-keepalive
python3 tizen/sync.py         # po změně webos/service/protocol.js nebo webos/app/receiver-ui.js
python3 tizen/build.py        # Samsung: nepodepsaný WGT do tizen/dist/
node tizen/test/test.cjs
python3 -m unittest discover -s tools/ocr -v         # OCR skórování (CER/WER)
```

Neexistuje lint ani formátovací krok (`lint` je v Gradle záměrně vypnutý). Kořenový projekt se jmenuje `TitulkyTV`, jediný modul je `:app`.

## Architektura

**Tok dat.** Konzole → HDMI → TV (uživatel hraje v plné kvalitě). Android aplikace v telefonu paralelně získá druhou kopii obrazu přes Remote Play (`RemotePlay*.kt`, Chiaki), snímky bere přímo z dekodéru (`PametObraz.kt` + `libpcobraz1.so`, třídy `VideoFrame` z WebRTC) nebo přes OpenGL (`GlSnimac.kt`), vyřízne oblast titulků a provede OCR (`Ctecka.kt`, ML Kit; `StabilitaTextu.kt` stabilizuje postupně se objevující text), přeloží (`Prekladac.kt`: DeepL s vlastními klíči, Google, offline ML Kit; `PametPrekladu.kt` je trvalá paměť vět) a výsledek zobrazí v telefonu (`TelefonTitulky` v `Rezim.kt`, `TelefonActivity.kt`) nebo ho pošle přijímači na TV (`Propojeni.Vysilac`). UI telefonu je v Compose (`MainActivity.kt`, `TelefonUi.kt`, `TelefonPripojeni.kt`), obrazovka hraní `TelefonActivity.kt` zatím ve Views.

**Režimy.** Standardní režim překládá okamžitě. Příběhový režim (`Pribeh*.kt`, `CteciFronta.kt`, `SkladaniVet.kt`, `CasovaniPribehu.kt`, popis v `docs/PRIBEHOVY-REZIM.md`) přidává čtecí frontu a skládání nedokončených vět. Režim titulků GTA VI s ikonami tlačítek u voleb: `RezimTitulku.kt`, `Volby.kt`, `IkonyVoleb.kt`. Příběhový výstup se používá jen po potvrzení podpory přijímačem.

**Přijímače a protokol.** Telefon ↔ TV po UDP: TV se hlásí `PLAYCAPTION_TV` na port 41988 každé 2 s, telefon posílá na 41989 pakety `PCT1` (titulek), `PCV1` (volby), `PCO1` a Story `PCQ1`/`PCS1` s potvrzením `PCA1`. Definice na straně Androidu je `Propojeni.kt` (+ `Pribeh*.kt`; Android je jen odesílatel), na straně TV `webos/service/protocol.js`; změna protokolu se musí udělat na obou stranách a v obou přijímačích.
- `webos/`: LG přijímač. `app/` je průhledné UI, `service/` Node.js služba přes `webos-service`. Aplikace nemá žádné npm závislosti.
- `tizen/`: Samsung přijímač. `app/js/prijimac-ui.js` a `app/service/protocol.js` **vznikají kopírováním z `webos/` přes `tizen/sync.py`, neupravuj je ručně**. WGT je nepodepsaný (podpis je vázaný na konkrétní TV).
- Android `Lg*.kt` / `Samsung*.kt` (průvodce, SSH instalace přes JSch, Developer Mode) instalují přibalený přijímač z `assets/lg/` a `assets/samsung/`, které generují Gradle úlohy `prepareLgReceiver` / `prepareSamsungReceiver` v `app/build.gradle.kts` (kontrolují manifest, SHA-256 a název souboru).

**Kompatibilita starších TV (důležité pro LG).** Na webOS 3.x/4.x běží služby v Node.js 0.12 a UI v Chromiu 53: nepoužívej `Buffer.from`, `Array.prototype.find`, `String.prototype.trimEnd` a podobné novější API v `webos/service/` a `webos/app/`. Na webOS 4.4 zavře TV překryv po 120 s bez stisku ovladače (nevyřešeno). Podrobnosti zjištěné na LG 43UK6300 jsou ve `webos/TESTOVANI.md` a `webos/SHODA-ANDROID.md`.

**Nativní vrstva.** `app/src/main/jniLibs/*/` obsahuje předsestavené `libchiaki-jni.so` (Chiaki 2.1.1 se dvěma patchi: `native/chiaki-audio-unlock.patch`, `chiaki-silent-audio.patch`), `libchiaki-pc.so` (kopie s přejmenovanou závislostí, načítá se přednostně, `libchiaki-jni.so` je záloha) a vlastní `libpcobraz1.so` (zdroj `native/pcobraz.c`). Normální vývoj je nepřestavuje; postup s NDK r21e je v `native/README.md`. `com/metallic/chiaki/lib/Chiaki.kt` je převzatý a upravený kód Chiaki (úpravy v hlavičce souboru).

## Konvence

- Při každé nové verzi zvyš `versionCode` + `versionName` v `app/build.gradle.kts` a dopiš změny do `CHANGELOG.md` (sekce „Nevydané změny“ → nová verze).
- Otevřená práce a rozhodnutí: `BACKLOG.md` (položky PC-xxx) a `ROADMAP.md` (P0–P6). Jak se věci ověřovaly na skutečných TV: `webos/TESTOVANI.md`, `docs/P0-VYCHOZI-STAV.md`.
- Texty v UI jsou česky; síťové texty se v přijímačích vykreslují přes `textContent`, nikdy jako HTML.
- Většina chování vyžaduje fyzickou přejímku na TV/konzoli; JVM a Node testy ji nenahrazují (v commitech a dokumentaci se rozlišuje „testy prošly“ a „ověřeno na zařízení“).
- OCR benchmark používá oddělené `applicationId` přes `tools/ocr/isolated.gradle`, aby nepřepsal běžnou instalaci (viz `tools/ocr/README.md`).
