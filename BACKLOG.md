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
TV s odlišným nastavením, návrat na telefon a zpět, restart aplikace, původní
Android TV režim. Uživatel na Redmi Pad Pro potvrdil nalezení a výběr C5.
Karta výstupu byla po opravě výšky/odsazení vizuálně zkontrolovaná na tabletu:
rozlišuje vybranou TV od aktivního odesílání. Nepředstírá potvrzení příjmu UDP.
Původní Android TV hardware dosud touto etapou nepřetestován.
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

Stav: nápad k diskusi 25. 11. 2026, nic neimplementováno.
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

### Otevřené otázky

1. Jde zachytit okno Remote Play, když je schované za jiným oknem,
   minimalizované nebo na jiné ploše? Ověřit prototypem jako první.
2. Sdílená logika (`StabilitaTextu`, `SkladaniVet`, `CteciFronta`,
   `CasovaniPribehu`, `Volby`, kódování paketů z `Propojeni`): Kotlin
   Multiplatform, nebo přepis do Swiftu a údržba dvakrát?
3. Distribuce pro sebe: vlastní build stačí. Pro další lidi podpis/notarizace.

### Nápady na vylepšení (QOL), které Mac umožní

- Výběr okna Remote Play ze seznamu a automatické nalezení po spuštění.
- Oblast čtení tažením myší nad živým náhledem okna, víc oblastí najednou.
- Plovoucí průhledné okno titulků vždy nahoře, propouštějící kliknutí: přes
  okno Remote Play nebo na druhém monitoru. Stav a ovládání v menu baru.
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

### První krok

Prototyp na jeden večer: ScreenCaptureKit + Vision nad oknem PS Remote Play
s titulky ze hry. Změřit čas čtení a kvalitu OCR, ověřit otázku 1.
