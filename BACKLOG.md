# PlayCaption – backlog

## PC-001 – Úplný překlad víceřádkových a postupně se objevujících textů

Stav: opraven reprodukovaný výpadek řádků při nezměněném obrazu; původní herní
scéna ještě čeká na přejímku. Větev `codex/pc-001-multiline-text`, bez vydání.

### Pozorované chování

Uživatel 14. 11. 2026 na PS5 + Redmi Pad Pro + LG C5 hlásí, že první dva řádky
víceřádkového textu krátce probliknou a zůstane přeložený pouze poslední řádek.
Na dodané fotografii je anglický text:

> The Shoebox can store your items. Alan can access stored items from any Shoebox in the Dark Place.

Výsledný český text na LG je pouze „Botník na temném místě.“. Fotografie ukazuje
výsledek, ne průběh čtení; zatím neprokazuje, ve které části zpracování se text ztrácí.
Osobní fotografii místnosti nevkládáme do veřejného repozitáře.

Samostatný test LG přijímače 0.0.4 na stejné C5 zobrazil celý syntetický český paket
se třemi řádky bez oříznutí (porovnání paketu s DOM a rozměry boxu). To ověřuje
pouze zobrazení již kompletního textu; nereprodukuje ani neopravuje chybu ze hry.

### Kontext a hypotézy

- Autor popisuje zpoždění obrazu Remote Play a rozdíl PS5 30 FPS / Xbox 60 FPS.
  To je jeho vysvětlení, nikoli změřená příčina této chyby.
- V aktuálním kódu `RemotePlayService.kt` je pro PS5 vytvořen `ConnectVideoProfile`
  s hodnotou `30` FPS. Vyšší podporovaná frekvence se má posoudit samostatně.
- Prověřit ořez oblasti OCR, seskupení řádků, stabilizaci postupně psaného textu,
  nahrazování částečných výsledků a pořadí asynchronních překladů.
- Oddělit skutečně chybějící text od oříznutí při zobrazení na tabletu nebo LG.
  LG v této verzi pouze přijímá hotový text; samo OCR ani překlad neprovádí.

### Audit a oprava – 16. 11. 2026

- Seskupení OCR řádků i čekání na kompletní výsledky jednotlivých překladů už
  existují. LG přijímá hotový text; v tomto kroku se jeho kód ani protokol nemění.
- Regresní scénář před opravou selhal: celý třířádkový odstavec následovaný dvěma
  stejnými čteními posledního řádku přijal `StabilitaTextu` jako nové úplné čtení.
  `TextVRamecku` pak nahradil celý odstavec textem „Shoebox in the Dark Place.“.
  Ztráta v této simulaci nastává na Androidu před překladem, nikoli při zobrazení LG.
- Stabilizace nyní porovnává otisk všech pixelů zdrojového výřezu a počet řádků.
  Výpadek dříve přečtených řádků na stejném obrazu nepotvrdí ani opakované OCR.
  Doplnění a opravy zůstávají možné; při změně obrazu platí původní potvrzování
  kratšího textu a jeho zmizení. Bez otisku se chování nemění.
- Žlutý rámeček také zahazuje opožděné výsledky OCR, pokud mezitím začalo novější
  čtení. Dosavadní ochrana opožděných překladů zůstává zachovaná.
- Záznam uvádí počty surových, seskupených, filtrovaných a vybraných řádků,
  číslo snímku a zkrácený otisk; neukládá zdrojový obrázek ani přihlašovací údaje.
- Prošlo 202 JVM testů včetně výpadku horních/prostředních řádků, prázdného OCR,
  skutečné změny scény, růstu textu, resetu a skutečného UDP přenosu celého
  odstavce. Síťový překladač je v tomto testu nahrazen pevnou odpovědí.
- Limit: shoda otisku je konzervativní důkaz stejného obrazu. Měnící se pozadí
  nebo kompresní šum tuto ochranu neaktivují. Bez původních snímků a herního
  ověření není prokázáno, že jde o jedinou příčinu hlášeného PC-001.

### Zbývající herní přejímka

1. Na stejné scéně porovnat snímek z Remote Play, kompletní výsledek OCR,
   vstup/výstup překladu, odeslaný paket a zobrazení na tabletu i LG.
2. Změřit stáří snímku, interval OCR a čas překladu; identifikovat, kde vzniká
   zpoždění nebo ztráta řádků. Záznamy nesmějí obsahovat PSN klíče/tokeny.
3. Při další ztrátě porovnat nové počty řádků a otisky. Rozlišit výpadek již
   samotného OCR od výběru řádků, stabilizace a překladu. Nepřepínat FPS naslepo.

Hotovo znamená: zůstává kompletní aktuální text, starší odpověď ho nepřepíše,
neproblikávají zbytečně částečné řádky a po změně scény nezůstává starý překlad.

## PC-002 – Dorovnání funkcí LG přijímače

Stav: v LG 0.0.6 doplněny níže uvedené vizuální funkce; herní přejímka pokračuje.

- Dialogové volby a delší herní texty přenášené zprávou `PCV1`.
- Modrý OCR rámeček `PCO1`, rámeček pro nastavení a barvy jmen postav.
- Matice implementace a testů: `webos/SHODA-ANDROID.md` a `webos/TESTOVANI.md`.
- Zbývají další LG modely/firmware a několika hodinové hraní, včetně uspání a návratu TV.

## PC-003 – Měřením podložená optimalizace přenosu a čtení

Stav: návrh pro pozdější fázi, ne důvod k přepisu aplikace nyní.

- Zpracovávat nejnovější použitelný snímek místo rostoucí fronty starých snímků.
- OCR spouštět podle změny textové oblasti, překladem nezatěžovat opakovaný text.
- Sledovat identitu textového bloku a úplnost řádků, ne pouze poslední přečtený řádek.
- U budoucí verze protokolu vyhodnotit session ID a pořadí zpráv, aby se daly
  odlišit opožděné UDP pakety od restartu aplikace na stejném zařízení.
- Návrhy posoudit až po PC-001; mohou již částečně existovat v Android kódu.

## PC-010 – Instalace LG přijímače přímo z Android PlayCaption

Implementováno v `feature/tv-platform-management`: místní instalace přibaleného
IPK, společné šifrované spárování, kontrola skutečné verze před přenosem, aktualizace
bez odinstalování a odmítnutí downgradu. Stejná verze se jen spustí. Prvotní
spárování připraví také ověřování času Developer Mode. Záloha je externí Android
správce, export IPK a volitelně počítač. Detaily a limity v `webos/TESTOVANI.md`.
Internetové stahování novějšího IPK a vlastní backend nejsou součástí MVP.

## PC-004 – Profily přijímačů v Android odesílači

Implementováno: platforma oddělená od konkrétní TV a od aktivního výstupu.
Každá TV má stabilní ID, poslední adresu a vlastní polohu/velikost titulků,
zámky a oblasti čtení. Staré společné nastavení přebírá pouze původní TV;
jazyk a účty konzole se nekopírují. Neznámou platformu potvrzuje uživatel,
verze a schopnosti starého přijímače se neodhadují. Nalezená adresa má přednost
před uloženou; vypnutá poslední TV se automaticky nenahradí jinou.

Kontroly: `bash gradlew :app:testDebugUnitTest :app:assembleDebug` – pět testů
profilů a test skutečného UDP přenosu na loopbacku. Ruční přejímka: dvě fyzické
TV s odlišným nastavením, návrat na telefon a zpět, restart aplikace.
Uživatel na Redmi Pad Pro potvrdil nalezení a výběr C5.
Karta výstupu byla po opravě výšky/odsazení vizuálně zkontrolovaná na tabletu:
rozlišuje vybranou TV od aktivního odesílání. Nepředstírá potvrzení příjmu UDP.
Vlastní LG instalátor a přibalený IPK jsou implementované; aktuální přejímka je v `webos/TESTOVANI.md`.

## PC-005 – Průvodce přípravou LG

Implementováno: pět kroků v Android aplikaci, odkazy na oficiální LG Developer,
veřejná vydání a komunitní instalační správce. Přístup přes zvolenou platformu
LG nebo nabídku Jak připojit televizi. Návrat k rozpracovanému kroku, přeskočení
přípravy u již nainstalovaného přijímače a skutečné vyhledání na konci.
Průvodce neukládá hesla, neprodlužuje Developer Mode a nepovažuje proklikání
instrukcí za dokončenou instalaci či spojení. Přímý instalátor IPK je také implementovaný.

Kontroly: čtyři testy navigace průvodce a šest dosavadních testů profilů/přenosu;
`bash gradlew :app:testDebugUnitTest :app:assembleDebug`. Postup LG ověřen podle
oficiální dokumentace Developer Mode 15. 11. 2026. Ruční přejímka první instalace
novým uživatelem a mobilní externí správce zůstávají otevřené.

Přejímka na Redmi Pad Pro (test03): automatizované UI prošlo všech pět obrazovek,
systémové Zpět, zavření/obnovení uloženého kroku, přeskočení přípravy, návrat
z externího odkazu a navazující discovery se skutečným seznamem LG přijímačů.
Screenshot posledního kroku potvrdil viditelnost celého textu i navigace.
Tento test nemění Developer Mode na TV a nenahrazuje první instalaci na čisté LG.


## PC-006 – Informace o platnosti Developer Mode

Implementováno v Androidu: čas z kontrolní služby LG na kartě konkrétní TV,
datum ověření, ruční kontrola a zvýraznění pod tři dny. První propojení vyžaduje
jednorázově Key Server a Passphrase; pak je relace uložená šifrovaně pro stabilní
ID TV. Kontrola nejvýše hodinově při viditelné kartě, starší/chybný údaj označený.
Žádné automatické prodlužování ani změna přijímače LG. Testy a důkazy v `webos/TESTOVANI.md`.
Prvotní propojení času je součástí ověření TV v instalačním průvodci; stejný šifrovaně uložený klíč slouží k dalším aktualizacím.

## PC-007 – Skládání nedokončených vět před Story překladem

Stav: konzervativní MVP na `codex/story-sentence-buffer`, čeká na herní přejímku.
Souvisí s PC-001; nezastavuje další OCR práci.

- `SkladaniVet` drží pouze nepředaný konec anglické věty. Silně nedokončené
  konstrukce mohou navázat při shodě známého mluvčího, u neznámého pouze
  omezené spojení typu „We are such“ → „a nuisance.“.
- Běžné repliky bez tečky se nezdržují. Chybějící interpunkce ani samotné malé
  písmeno nejsou důkaz pokračování. Standardní režim se nemění.
- Limity experimentu: 2500 ms celkem, po potvrzeném zmizení nejvýše 600 ms
  v rámci stejného limitu, tři zdrojové repliky a 800 znaků čekajícího textu.
  Interaktivní volby a tři již zařazené Story položky čekání ukončí.
- ID zdroje a revize vznikají nad událostmi nové/opravované repliky; OCR pořadí
  není samo identita dialogu. Nepřepisuje se klasifikace OCR. Vstupní chybnou
  klasifikaci tato vrstva neumí spolehlivě opravit.
- Regrese pokrývají všech 16 zadaných scénářů, negativní korpus, zdrojové
  úseky, prodlevy a napojení překladače/čtecí fronty. Detaily v
  `docs/PRIBEHOVY-REZIM.md`.
- Pro rozšíření: nejdříve změřit správná a chybná spojení na skutečných
  dialozích. Nezavádět opravy jednotlivých slov, LLM ani LG změny bez důkazu.

## PC-008 – Trvalá paměť přeložených vět

Stav: implementováno 22. 11. 2026 (necommitnuto), čeká na herní ověření.
Cíl je méně online překladů, hlavně šetřit měsíční limit DeepL.

- Původně: `LruCache<String, String>(500)` v `Prekladac.kt`, jen v RAM, smazaná
  při restartu `RemotePlayService` a při změně jazyka.
- Záznam z 22. 11. 2026 ukázal obě ztráty: stejná věta přes DeepL dvakrát během
  4 s („a Proper job.“ / „a proper job.“) a znovu po reinstalaci aplikace.
- Hotovo – `PametPrekladu.kt`:
  - soubor na jazyk `files/preklady/preklady-<jazyk>.json`, nejvýš 5000 vět (LRU),
    načítání na pozadí, ukládání nejvýš jednou za 20 s a při zavření služby,
    zápis přes dočasný soubor; poškozený soubor se ignoruje;
  - sjednocený klíč: malá písmena, bez diakritiky z OCR, jednotné apostrofy,
    bez uvozovek a běžné interpunkce; otazník se zachovává (otázka ≠ oznámení);
  - překlad dokončený po přepnutí jazyka se do paměti neukládá.
- Zbývá: po pár hodinách hraní spočítat ze záznamu podíl `paměť` vs. `deepl`
  a zkontrolovat, že sjednocení neslučuje věty s jiným významem.

## PC-009 – PlayCaper pro Mac (snímání okna Remote Play)

Stav: MVP v `macos/PlayCaper` (26. 11. 2026). **Ověřeno na zařízení** se
skutečnou TV: GTA VI na PS5 → PS Remote Play na Macu (M3) → PlayCaper →
Samsung UE55CU7172 (přijímač 1.3.0), české titulky na TV. Rozhodnutí
26. 11. 2026 viz „Rozhodnuto“ níže.
Autor forku má iPhone a Mac, kamarád Android. Verze pro iPhone by znamenala
přepsat Chiaki/Remote Play a narazit na omezení iOS (broadcast `PLAYCAPTION_TV`
jen s entitlementem pro multicast, uspávání na pozadí, podpis na 7 dní, AGPL
vs. App Store). Mac tato omezení nemá.

### Princip

- Neimplementovat vlastního klienta Remote Play. Snímat okno oficiálního
  PS Remote Play nebo chiaki-ng přes ScreenCaptureKit (jen výřez titulků).
- OCR přes Apple Vision, překlad DeepL/Google jako na Androidu, výstup do okna
  na Macu nebo po UDP do stávajících přijímačů LG/Samsung. Protokol ani
  přijímače se nemění.
- Odpadá `RemotePlayService.kt`, JNI, nativní knihovny i přihlašování k PSN.
- Výkon: Android jede pevně v 720p/30 fps s OCR každých 100 ms
  (`INTERVAL_MS`) bez přizpůsobení zařízení. Apple Silicon by unesl vyšší
  rozlišení i frekvenci; to je odhad, ne měření.

### Rozhodnuto (26. 11. 2026)

- **Jen pro autora forku.** Kamarád Mac verzi nechce, Windows verze se nedělá.
  Distribuce = vlastní build, bez podpisu a notarizace pro další lidi.
- **Ve stejném repu**, adresář `macos/` na `main-fork`, ne samostatné repo.
  Protokol a přijímače LG/Samsung jsou tady, změny protokolu jdou jedním
  commitem a jedním během testů. Licence je stejně AGPL (odvozené dílo).
- **Nativně ve Swiftu** (SwiftUI, ScreenCaptureKit, Vision, Network.framework,
  URLSession), žádný Kotlin Multiplatform. Bez příběhového režimu zbývá
  k přepisu jen `StabilitaTextu` (~110 ř.), `RezimTitulku`, kódování paketů
  z `Propojeni` a případně `Volby` (~250 ř.); KMP by stálo víc než ušetří.
- **Výstup jen do přijímače na TV.** Autor hraje na TV, do které je PS5
  zapojená přes HDMI; Mac čte jen druhou kopii obrazu z okna Remote Play.
  Na Macu žádné okno titulků, jen menu bar (zapnout/vypnout, výběr TV, stav)
  a ladicí panel. Plovoucí okno titulků z nápadů níže odpadá.
- **Bez příběhového režimu.** Mac posílá jen `PCT1` a `PCV1`/`PCO1`;
  `PCQ1`/`PCS1`/`PCA1`, `CteciFronta`, `SkladaniVet` a `CasovaniPribehu`
  se nepřepisují. Přijímače se nemění. Viz PC-013.
- **Shoda se stávající implementací:** pro pakety sdílené testovací vektory
  (vstup → přesné bajty), které čtou JVM testy, Swift testy i `webos/test.cjs`.

### Otevřené otázky

1. **Hlavní riziko.** Jde zachytit okno Remote Play, když je schované za jiným
   oknem, minimalizované nebo na jiné ploše? Na okno se při hraní nikdo nedívá,
   takže má být odklizené.

   **Ověřeno na zařízení 26. 11. 2026** (`macos/Prototyp`, M3, macOS 27.2,
   oficiální PS Remote Play přihlášený druhým účtem, GTA VI, okno 960×568 bodů):

   | stav okna | snímky | výsledek |
   |---|---|---|
   | viditelné | ~29/s | čte |
   | překryté jiným oknem přes celou obrazovku | ~29/s | **čte** (dialogy živě) |
   | aplikace skrytá (Cmd+H) | 0 | nečte |
   | minimalizované do Docku | 0 | nečte |
   | zhasnutý displej Macu | jen „beze změny“ | nečte (obraz zamrzne) |
   | jiná plocha | – | neověřeno |

   Závěr: okno může zůstat **za ostatními okny**, ale nesmí být skryté ani
   minimalizované a displej nesmí zhasnout (aplikace bude držet
   `IOPMAssertion` proti uspání displeje). Se zavřeným víkem bez externího
   monitoru to fungovat nebude.

   Další měření z prototypu: OCR Vision `accurate` na výřezu 1536×272 px
   průměrně 42–58 ms, max ~100 ms; první volání v systému jednorázově 31 s
   (příprava modelu). Dialogy chodců i víceřádkové titulky přečtené správně,
   kolísá jen čárka/tečka (řeší stabilizace). Hlavní problém je šum z nápisů
   ve scéně („JASON'S TATTOOS“, „CRASH OUT CARTEL“) – v MVP filtrovat
   (barva/pozice titulků, užší oblast).
2. Apple Translation framework: podporuje češtinu? Neověřeno, do té doby
   DeepL/Google jako na Androidu.

### Nápady na vylepšení (QOL), které Mac umožní

- Výběr okna Remote Play ze seznamu a automatické nalezení po spuštění.
- Oblast čtení tažením myší nad živým náhledem okna, víc oblastí najednou.
- ~~Plovoucí průhledné okno titulků vždy nahoře~~ (odpadá, výstup jde na TV).
  Stav a ovládání v menu baru.
- Globální klávesové zkratky: pauza, znovu přeložit, ukázat anglický originál,
  kopírovat poslední titulek.
- Historie titulků (EN + CZ vedle sebe) s hledáním a exportem; možnost vrátit
  se k přeskočenému dialogu.
- Úprava paměti překladů (PC-008) v UI: opravit špatný překlad, uloží se.
- Glosář pro hru: jména postav a míst se nepřekládají (nebo pevný překlad);
  stejná slova jako vlastní slovník pro Vision (`customWords`).
- Ladicí panel: živý výřez, výsledek OCR, časy čtení/překladu, zdroj
  překladu. Ukládání vzorků do korpusu pro `tools/ocr` (OCR benchmark).
- Předčítání českých titulků (AVSpeechSynthesizer, český hlas).
- DeepL klíče v Klíčence.
- Instalace a aktualizace přijímačů LG/Samsung z Macu (SSH, stávající
  `webos/build.py` a `tizen/build.py`).

### MVP (hotovo 26. 11. 2026)

Appka v menu baru (`macos/PlayCaper`, popis v jeho README): najde okno Remote
Play, čte výřez titulků každých 100 ms, vybere vystředěný blok řádků (nápisy
ve scéně a český text zahodí), stabilizuje (přepis `StabilitaTextu` /
`TextVRamecku`), přeloží (DeepL z Klíčenky, záložně Google) a pošle `PCT1`
vybrané TV. Časování schovávání jako na Androidu (1,8 s + 60 ms/znak).
Drží zámek proti zhasnutí displeje. Podepisuje se certifikátem Apple
Development, oprávnění k nahrávání obrazovky tak přežije nový build.

Naměřeno při hraní: čtení 50–110 ms, překlad Google 50–260 ms.
Odchylka od Androidu: titulek se stejnými slovy a jinou interpunkcí se
nepřekládá znovu (Vision střídá „Uh, yeah“ / „Uh. yeah“).

### Další kroky

1. ~~Ověřit se skutečnou TV~~ (hotovo 26. 11. 2026, Samsung).
2. Volby odpovědí GTA VI (`PCV1`, port `Volby.kt` / `IkonyVoleb.kt`).
3. Výběr oblasti tažením nad náhledem okna místo čísel v Nastavení.
4. Úpravy OCR z Androidu (`Prekladac.upravSlang`), spojování rozdělených vět
   (`prelozAUkaz`), paměť překladů (PC-008).

## PC-011 – Sady překladů pro hry

Stav: nápad 25. 11. 2026, nic neimplementováno. Navazuje na PC-008.

### Cíl

Paměť překladů (`PametPrekladu.kt`) je dnes uložená zvlášť na každém zařízení.
Dva hráči stejné hry tak za stejné věty čerpají DeepL kvótu dvakrát. Sada
překladů je paměť jedné hry, kterou jde exportovat, sdílet a importovat,
včetně ručních oprav a glosáře jmen.

### Jak se věta najde (stávající mechanismus)

- Klíč je sjednocený anglický text (`PametPrekladu.klic`): malá písmena, bez
  diakritiky, uvozovek a běžné interpunkce, otazník zůstává. Hledá se přesná
  shoda klíče.
- Jméno mluvčího („Arthur: …“) se před překladem odřízne
  (`RemotePlayService.prelozVse`), klíčem je jen replika.
- Překlep OCR v jediném písmenu dává jiný klíč, takže věta se nenajde a
  přeloží se online. Chyba nevznikne, jen to stojí kvótu.

### Návrh

- Pořadí hledání: ruční opravy → sada zvolené hry → osobní paměť → online.
- Hru vybírá uživatel ručně. Automatické rozpoznání hry z PS5 zatím neřešit.
- Formát souboru: hra, zdrojový a cílový jazyk, verze klíče a položky
  `[anglický originál, klíč, překlad, zdroj (deepl/google/mlkit/ručně)]`.
  Ukládat i původní anglický text, aby šlo klíče po změně `klic()` přepočítat
  (dnešní soubor PC-008 ukládá jen klíč).
- Sada nemá limit 5000 vět (LRU). Celá hra může mít desítky tisíc replik.
- Ručně opravený překlad se nikdy automaticky nepřepíše.
- Glosář hry (jména postav a míst a jejich pevný překlad nebo zákaz překladu)
  nahradí natvrdo zapsaný `CHYBNA_JMENA` v `RemotePlayService.kt`.
- Při importu se položky slučují: ruční vyhrává, jinak zůstane stávající.
- Sdílení: export/import souboru (Android sdílení, sdílená složka, git).
  Server ani další hardware nejsou potřeba. Stejný formát pro budoucí
  desktopovou verzi (PC-009).

### Rizika a otevřené otázky

- **Rod mluvčího.** „I'm ready.“ je podle postavy „Jsem připraven/a“. Klíč
  mluvčího neobsahuje, takže první uložený překlad platí pro všechny postavy.
  Zvážit volitelný klíč „mluvčí + věta“ s náhradou na samotnou větu.
- **Bloky vs. věty.** Příběhový režim (`SkladaniVet`) překládá spojené repliky.
  Klíč celého bloku se při jiném rozdělení OCR nenajde. Změřit, jak často
  se v sadě trefí blok a jak často jednotlivá věta.
- **Přibližná shoda** (odchylka 1–2 znaky u dlouhých vět) by pokryla chyby
  OCR. U krátkých vět je nebezpečná („I can.“ × „I can't.“). Pokud vůbec, tak
  až po změření, kolik zásahů se kvůli OCR ztrácí.
- **Autorská práva.** Sada obsahuje velkou část scénáře hry. Sdílení mezi
  pár hráči je něco jiného než veřejné repo se scénáři celých her.
- **Přeložit dopředu celý scénář** (z cizího zdroje) by vyčerpalo DeepL
  kvótu (scénář má miliony znaků). Sada vzniká hraním, ne hromadným překladem.

### První krok

Do záznamu doplnit počty zásahů paměti vs. online překladů na jedno hraní
(PC-008 to má jako zbývající bod). Bez čísel nejde odhadnout přínos sdílení.

## PC-012 – Zrychlení cesty titulek → překlad → TV

Stav: rozbor kódu 25. 11. 2026, nic neimplementováno. Souvisí s PC-003
(snímky a OCR) a PC-011 (sady překladů). Pořadí příčin je odhad z kódu,
ne měření.

### Kde se ztrácí čas

1. **Čekání na potvrzení textu před překladem** (`RemotePlayService.kt`):
   200 ms na „vynořování“, druhé OCR pro zkrácený/dopisovaný text
   (`StabilitaTextu.prijmi`), až 2 čtení navíc při nejistém slově a až 4 při
   malém písmenu na začátku. Čtečka je jediná (`ctecky = listOf(Ctecka())`),
   takže se každé čtení navíc sčítá.
2. **Online překlad postupně.** `Prekladac.preloz` běží na jednom vlákně
   (`newSingleThreadExecutor`) a služby zkouší jednu po druhé. Nedostupná
   služba stojí timeouty 1,5 s + 2–3 s (Google 2 pokusy), než přijde další.
3. **Fronta nezahazuje staré věty.** `PlatnostPrekladu` zahodí jen výsledek;
   pomalý překlad staré věty zdrží novou.
4. **Více replik v titulku** (`prelozVse`) jde sériově, jedna replika = jeden
   požadavek.
5. Přenos UDP do TV (jednotky ms, 2× po 60 ms) a vykreslení v přijímači
   (bez animací) nejsou úzké hrdlo. Zpoždění samotného Remote Play
   (720p/30 fps, dekodér) aplikace prakticky neovlivní.

### Návrhy (podle poměru přínos/práce)

- **A – měření fází.** Do záznamu pro každý titulek: první vidění →
  potvrzení → odeslání požadavku → odpověď → UDP. Dnešní řádek „čtení X ms,
  překlad Y ms“ nezahrnuje čekání na potvrzení. Udělat jako první.
- **B – fronta „poslední vyhrává“.** Držet jen nejnovější čekající větu,
  zastaralé zahodit před odesláním.
- **C – souběh služeb (hedging).** Když první služba neodpoví do
  ~400–600 ms, spustit paralelně další a vzít první platnou odpověď.
  Riziko: víc volání, čerpání kvóty DeepL, dřívější blokace Googlu.
- **D – spekulativní překlad.** Přeložit už první OCR a souběžně čekat na
  potvrzení; při shodě je výsledek hotový. Omezit na věty, které vypadají
  úplně (velké písmeno, koncová interpunkce), kvůli kvótě.
- **E – dávka replik.** DeepL umí víc `text`, Google víc `q` v jednom
  požadavku.
- **F – druhá čtečka (pipelining OCR).** Pole `obsazeno[]` už počítá s víc
  čtečkami. Riziko: CPU a teplo telefonu.
- **G – sady překladů** (PC-011): zásah v paměti = ~0 ms.
- **H – udržovaná spojení** (keep-alive/HTTP/2 pro všechny služby, ne jen
  ping na Google). Ušetří nové TLS spojení.
- **I – volba výkonu v nastavení** (přání kamaráda, 26. 11. 2026, telefon
  Samsung S25 FE). Přepínač Úsporný / Vyvážený / Maximální, který jen
  zapíná a ladí ostatní návrhy: interval OCR (`INTERVAL_MS`, dnes 100 ms;
  např. 200 / 100 / 50 ms), počet čteček (F), spekulativní překlad (D),
  souběh služeb (C) a délku čekání na potvrzení textu. Výkon telefonu
  zrychlí jen OCR; čekání na potvrzení je záměrné a online překlad závisí
  na síti. Rizika „Maximální“: baterie, přehřátí a throttling (může
  zhoršit i dekódování Remote Play), čerpání kvóty DeepL, blokace Googlu,
  víc useknutých vět (PC-001). Dělat až po měření z A, jinak hrozí, že se
  telefon jen víc hřeje bez zrychlení.

Rychlý náhled z ML Kitu (`RYCHLY_NAHLED = true`) už existuje; okamžitou
odezvu řeší za cenu přepnutí z horšího překladu na lepší.

### První krok

A + B + C: malé, jen Android, testovatelné JVM testy, beze změny protokolu
a přijímačů. Podle měření z A rozhodnout o D a ladění odkladů OCR. Odklady
jsou nejcitlivější místo (riziko překladu useknutých vět, viz PC-001) a
vyžadují ověření při hraní.

## PC-013 – Odstranění příběhového režimu

Stav: rozhodnuto 26. 11. 2026, nic neimplementováno.

Příběhový režim (Story, `docs/PRIBEHOVY-REZIM.md`) nepoužívá autor forku ani
kamarád a Mac verze (PC-009) ho mít nebude. Pochází z upstreamu; dokud se
upstream mergoval, mazání by znamenalo konflikty při každém merge. Od
26. 11. 2026 se upstream nemerguje (fork se vyvíjí samostatně), takže to
překážkou není.

### Rozsah (přibližně podle výskytů Story/`Pribeh`/`PCQ1`)

- Android: `Pribeh*.kt`, `CteciFronta.kt`, `SkladaniVet.kt`,
  `CasovaniPribehu.kt`, přepínač režimu v UI a nastavení, Story větev
  v `Propojeni.kt` (zjišťování podpory přijímačem); ~15 souborů v `main`,
  ~11 JVM testů, 2 instrumentační testy.
- Přijímače: Story pakety `PCQ1`/`PCS1`/`PCA1` v `webos/service/protocol.js`
  a `webos/app/receiver-ui.js`, testy `test-story`; Samsung přes `tizen/sync.py`.
- Dokumentace: `docs/PRIBEHOVY-REZIM.md`, README, ROADMAP, PC-007.

### Otevřené otázky

1. Přijímače: Story pakety odstranit, nebo je dál tiše ignorovat? Starší
   verze aplikace u kamaráda by je jinak posílaly do prázdna; přepínač je ale
   stejně vypnutý, takže stačí odstranit a vydat Android i přijímače společně.
2. Zůstane z příběhového režimu něco užitečného pro standardní režim?
   `SkladaniVet` (skládání nedokončených vět) souvisí s PC-001; před smazáním
   ověřit, že standardní režim na ní nezávisí.
3. Klíče v uložených nastaveních (zapnutý příběhový režim) po aktualizaci
   ignorovat, ať se aplikace nerozbije.

### První krok

Projít výskyty (`git grep -i "pribeh\|story\|PCQ1"`) a rozdělit je na
Story-only a sdílené se standardním režimem.
