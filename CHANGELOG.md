# Changelog – co je v které verzi

Číslo verze se ukazuje v aplikaci vpravo nahoře (např. BETA 0.10) a v Záznamu (logu).
Od verze 0.10 se vyvíjí jen jedna aplikace (dřívější NextGen, větev `master`) – umí „Jen televize“ i „S telefonem“.
Při každé nové verzi se zvýší číslo v `app/build.gradle.kts` (versionCode + versionName) a sem se dopíše, co je nového.

## Nevydané změny

- **Nastavení → Překlad → Služby překladu:** pořadí online služeb (DeepL, Google, Microsoft, MyMemory) si uživatel mění přetažením za úchyt (knihovna `sh.calvin.reorderable` 2.2.0, poslední pro Kotlin 1.9 / Compose 1.6; pro čtečku obrazovky akce „Posunout nahoru/dolů“) a každou může vypnout (`SluzbyPrekladu.kt`, `TelefonSluzby.kt`, `Nastaveni.poradiSluzeb` / `vypnuteSluzby`). DeepL se otevírá z tohoto seznamu (podstránka s klíči); bez klíče má místo přepínače tlačítko „Vložit klíč“. Před názvy jsou loga služeb (`ic_sluzba_*.xml`; DeepL má v tmavém režimu světlou variantu v `drawable-night`, MyMemory obecnou ikonu překladu). Offline záloha „Google ML Kit“ je pevně poslední a vypnout nejde. Řádek v Nastavení ukazuje souhrn pořadí, samostatný řádek DeepL zmizel. `Prekladac` čte pořadí při každé větě; příběhový režim zkouší první dvě zapnuté služby (dřív pevně DeepL → Google) a nově respektuje pauzu Googlu po selhání. Výchozí pořadí je stejné jako dřív. JVM testy a sestavení prošly, vzhled podstránky ověřen na emulátoru (světlý i tmavý režim); přetahování a fyzické zařízení neověřeny.
- Nastavení: odebrána sekce „Jazyk“ s řádkem Čeština, protože jiný jazyk už není na výběr. Kompilace prošla, na zařízení neověřeno.
- Nastavení: nová sekce „Překlad“ s řádkem DeepL (stav limitu, nebo „Nepoužívá se – překládá Google“). Řádek otevře podstránku (`DeeplObrazovka` v `TelefonDeepl.kt`) se stručným popisem fungování DeepL, odkazem na získání klíče zdarma a poli pro až 5 klíčů. Pod každým uloženým klíčem je jeho limit nebo chyba. Podstránka nahrazuje dřívější dialog `AlertDialog`. Pole pro další klíč přibude, až se vyplní poslední. Kompilace a JVM testy prošly, na zařízení neověřeno.
- **Fork PlayCaper: odebrán překlad do slovenštiny.** Jediný jazyk překladu i prostředí je čeština (`Nastaveni.AKTIVNI_JAZYKY`). Obrazovka výběru jazyka (`JazykObrazovka`) a tlačítko „Změnit“ u jazyka v Nastavení odstraněny; první spuštění vede rovnou na Domů. Dříve uložené „sk“ se čte jako čeština (`Nastaveni.jazykNeboCs`). Slovenské texty v `Texty.kt` zůstávají kvůli snadnému slučování s upstreamem, jen se už nepoužijí. Samsung přijímač (`tizen/`) má dál slovenské prostředí, to s jazykem překladu nesouvisí.
- Připojení PS5 je teď průvodce ve spodním panelu (`ModalBottomSheet`) nad Domů místo samostatné obrazovky (`PripojeniSheet` v `TelefonPripojeni.kt`). Další krok se ukáže, až je předchozí hotový (účet → hledání PS5 → PIN). Hotové kroky se sbalí do řádku s ✔ a akcí „Změnit účet“ / „Hledat znovu“. Nahoře je „Krok X ze 3“ s ukazatelem průběhu. Úvod o druhém uživateli se ukazuje jen v kroku 1 a Spárovat jde zmáčknout až po zadání 8 číslic. Po přihlášení v prohlížeči se aplikace vrátí na Domů s otevřeným panelem. Kompilace prošla; krok 1 a zavření panelu tlačítkem Zpět ověřeny na emulátoru, kroky 2–3 a spárování s PS5 neověřeny.
- Domů: bez spárované konzole se už neukazuje karta Stav „Nejdřív připoj konzoli“; tlačítko Hrát je do spárování neaktivní (připojení zůstává v řádku PlayStation). Kompilace prošla, na zařízení neověřeno.
- **Fork PlayCaper: odebrána podpora Xboxu a Android TV / Google TV.** Aplikace je jen pro telefon/tablet; titulky ukazuje v telefonu nebo je posílá přijímači na LG / Samsung. Odstraněno: spojení s Xboxem (`Xbox*.kt`), obrazovky pro televizi v `MainActivity`, překryv přes obraz (`OverlayService`, `Oznameni`), příjem titulků z telefonu na Android TV (`PrijemService`, přijímač v `Propojeni`, `PribehPrijem`, `PribehPrehravac`), převzetí spárování z TV, automatické spuštění po zapnutí TV (`AutoStart`), zjišťování aplikace v popředí (`Popredi`), vývojářská obrazovka (`VyvojActivity`, `SnimekService`), cesta obrazu YUV a úsporný režim slabých televizí v `RemotePlayService`, platforma `ANDROID_TV` (uložený profil se načte jako neznámá platforma), leanback v manifestu a oprávnění pro překryv, start po zapnutí, statistiky využití a snímání obrazovky. Knihovna Chiaki se vždy nejdřív zkusí načíst v upravené variantě s obrazem přímo do paměti. Místní přihlašovací stránka PSN poslouchá jen na 127.0.0.1 (dřív v celé domácí síti kvůli QR kódu na TV); QR kód a knihovna ZXing odstraněny. WebRTC zůstává (třídy pro snímky z dekodéru). JVM testy prošly (310), debug APK i instrumentační APK se sestaví; na zařízení zatím neověřeno.
- Telefon/tablet: první část přechodu na Jetpack Compose s Material 3 a dynamickými barvami (Material You). Hlavní obrazovka (Domů), nová stránka Nastavení (jazyk + klíče DeepL, výstup titulků, Co překládat), výběr jazyka a spodní `NavigationBar` jsou v Compose (`TelefonUi.kt`); `MainActivity` je teď `ComponentActivity`. Obrazovky „Připojit ke konzoli“ pro PS5 (účet, hledání PS5, PIN) a přihlášení přes QR/prohlížeč jsou také v Compose (`TelefonPripojeni.kt`); PIN a stav párování se při změně přihlášení už nemažou. Texty na stránce přepsány srozumitelněji (úvod, kroky s nadpisem a krátkým popisem; nové klíče `pp*` v `Texty.kt`, jen čeština a slovenština). Ostatní telefonní obrazovky (Xbox, `TelefonActivity`, dialogy) zatím zůstávají ve Views. Televize se nemění. Nové závislosti: Compose BOM 2024.06.00, `material3`, `activity-compose`. Testy prošly, vzhled ověřen na emulátoru, fyzické zařízení zatím ne.
- Stránka „Připojit ke konzoli“ (PS5) zkrácena: odstraněn blok „Máš PlayCaption na Android TV / Google TV?“ s tlačítkem převzetí (zůstává na hlavní obrazovce), jeden stručný úvod s druhým uživatelem, kratší popisy kroků 1–3 a logo PlayStation (`ic_playstation.xml`) v kroku 1. Kompilace prošla, vzhled na zařízení neověřen.
- Odstraněno logo PlayCaption z úvodu a z nadpisů obrazovek; tam, kde logo sloužilo jako tlačítko diagnostického záznamu, je teď text „PlayCaper“.
- Odstraněna uvítací obrazovka „Ahoj a vítej v PlayCaption!“ (po výběru jazyka se rovnou ukáže hlavní obrazovka).
- Android při aktivním výstupu do LG nepoužije minutové ukončení po zhasnutí displeje. Služba pro tento stream drží CPU zámek a uvolní jej při změně výstupu, zastavení a zániku; původní Android TV a ostatní režimy zachovávají dosavadní limit. Chování na zamčeném fyzickém zařízení ještě vyžaduje přejímku.
- README upřesňuje společný překladač a proměnlivé zpoždění Story i rozdíl HDMI signálu a interního vykreslování hry.

## 0.22 (versionCode 35)

Co je nového pro hráče:
- **Nový, jednodušší vzhled.** Televize má všechno na jedné obrazovce: Jazyk, Stav, Konzole,
  Co překládat a velkou volbu „Jak chceš hrát?“. Telefon vede krok za krokem (1–4) až k tlačítku
  „▶ Hrát“. Stav spojení se při připojování plní zeleně a po připojení ukáže
  „PlayStation připravena“ / „Xbox připraven“.
- **Povolení zobrazení na TV** je velké pulzující tlačítko hned pod logem – stačí zmáčknout OK.
- **Diagnostika:** najeď ovladačem na logo PlayCaption a zmáčkni OK – záznam se ukáže přes celou
  obrazovku a posouvá se šipkami. Při potížích ho vyfoť a pošli.
- **PS5 se už nezasekne** při opakovaném připojování (dřív pomohl jen restart konzole)
  a na TV zmizel **dvojitý zvuk / ozvěna**.
- **Příběhový režim i na Android TV** v režimu „S telefonem“.
- **Víceřádkové titulky** se už neztrácejí a stav konzole ukazuje, co se opravdu děje
  (připojuji / čekám na obraz / obraz chodí).
- **Spolehlivější přihlášení PlayStation** přes telefon.
- **LG televize:** přijímač 0.2.3 (lepší vyhledání TV, instalace i na webOS 5).
  Aktualizace: v telefonu otevři průvodce LG, **krok 4** – ukáže, jestli máš aktuální verzi,
  nebo nabídne instalaci novější.
- Díky Rudolfu Nohalovi (LG, opravy chyb a vývoj celé aplikace) a Michaelu Šubákovi (Samsung).

Technické podrobnosti:
- Vzhled 0.22 (jen popisky a rozložení, funkce stejné): televize má hlavní obrazovku
  v mřížce (Jazyk | Stav, Konzole | Co překládat, Podpora | Jak chceš hrát?).
  Pruh Stav se při připojování plní průhledně zeleně a po připojení zezelená
  („PlayStation připravena“ / „Xbox připraven“); rozsvítí se i řádek konzole.
  Povolení zobrazení je velké tlačítko hned pod logem, po povolení zezelená.
  „Překrývat původní titulky“ → „Zakrýt anglické titulky“ (Ano / Ne).
  Telefon: očíslované kroky 1–4, nahoře jeden pruh stavu, „▶ Hrát“.
- Diagnostika: najetí na logo ukáže „DIAGNOSTIKA – Zmáčkni OK“, záznam je přes celou
  obrazovku a posouvá se šipkami ▲▼ (podržení OK dál přidá tlačítko Vývoj).
- LG průvodce: odstraněna zastaralá věta, že na LG je potřeba Standardní režim.
- Poděkování v aplikaci: Rudolf Nohal (LG, opravy chyb a vývoj celé aplikace) a Michael Šubák (Samsung).

- PC-001: Android zachová kompletní víceřádkový text, když OCR na nezměněném
  obrazu opakovaně vynechá již přečtené řádky. Při skutečné změně snímku zůstává
  původní potvrzování kratšího textu a zmizení; opravy a doplňování jsou zachované.
- Žlutý rámeček nepřijímá opožděné OCR po zahájení novějšího čtení. Záznam navíc
  rozlišuje počty řádků před/po výběru a stabilizaci. Původní scénu Alan Wake 2
  je ještě potřeba ověřit při hraní; LG protokol, přijímač ani FPS se nemění.
- PS5: pomocný Remote Play stream na telefonu ani Android TV neotevírá zvukové
  přehrávání a nepředává PCM do přehrávací fronty. Odstranění dvojitého zvuku
  nemění HDMI, systémovou hlasitost ani opravu sessionFree; dekodér dál odčerpává
  buffery a končí přes EOS. Nové nativní regrese ověřují obě přehrávací cesty.
- PS5: nativní oprava zámku audio dekodéru. Po nedostupném vstupním bufferu
  chybová větev nově odemkne mutex, na který jinak při ukončení čekal sessionFree.
  Stejná oprava v ARM knihovnách TV i telefonu; šest regresí se skutečnými vlákny.
  Časové limity, kodeky a síťové chování se nemění. Přejímka nových knihoven na TCL zbývá.
- GL plocha i YUV ImageReader patří konkrétní PS5 Session a zavírají se až po
  úspěšném dispose. Nový pokus ani konec služby výstup živého dekodéru nezavře;
  při blokujícím či chybném úklidu zůstává držený. Obsluha YUV pokračuje do
  dokončení posledního výstupu, aby starý dekodér mohl dokončit práci.
- Telefon i TV ignorují události již ukončené PS5 relace. Po třech připojených
  pokusech bez jediného snímku se opakování v procesu zastaví s návodem na
  Vynutit ukončení, bez mazání dat. První skutečný snímek průběžnou řadu selhání
  vyčistí; samotný handshake nikoli. Obsazení PS5 má samostatné počítadlo a log.
- PS5 diagnostika zaznamená první dekódovaný snímek z GL/YUV/paměti i na telefonu
  a velikost sdíleného nativního logu před/po vytvoření, po připojení a po úklidu.
  Prázdný chiaki.log se neinterpretuje jako důkaz chybějících video paketů.
- Diagnostika PS5 bez obrazu: ID relací, délky jednotlivých kroků ukončování,
  upozornění se stackem při překročení pěti sekund a záznam hlídače/YUV/GL.
  Adamův report z TCL potvrdil blokování `setSurface(null)` před `stop()`.
  Ukončení nově používá `stop()` → `dispose(join/free)` bez samostatného
  odpojování Surface. Nová oprava čeká na opakovanou přejímku stejné TCL.
  První selhání GL a stav příjmu titulků z telefonu jsou viditelné
  v Záznamu. Postup přejímky: `docs/PS5-BEZ-OBRAZU.md`.
- Automatická volba obrazu TV se potvrzuje skutečným snímkem, ne časovačem.
  Po doloženém YUV výpadek sám nepřepíná na GL; neověřený GL pokus bez snímků
  vrací stávající hlídač na YUV. Stará volba GL se nejprve zachovává a ověřuje,
  telefon, kodeky, časy připojení a spárování zůstávají. Přejímka TCL ještě zbývá.
- PSN přihlášení: staré karty a formuláře prohlížeče už nemohou odeslat použitý
  kód do nového pokusu. Otevřená stránka, formulář i QR patří jednomu pokusu;
  skutečné chyby jeho ověření zůstávají viditelné.
- PSN přihlášení: prohlížeč potvrzuje uložený účet až po ověření u Sony, kontrole
  Account ID a úspěšném zápisu na zařízení. Během ověřování ukazuje průběh;
  konkrétní chyba zůstává viditelná v aplikaci i na webové stránce.
- Opakované odeslání stejného kódu nespouští další výměnu. Zrušený pokus nesmí
  později přepsat účet; PIN párování čeká na dokončené přihlášení.
- Instrukce rozlišují telefon/tablet a Android TV. Telefon u LG/Samsungu zadává
  PIN z PS5 přímo, bez návratu přes Home na TV. Android TV zachovává přihlášení
  telefonem přes QR a místní síť. Verze APK ani TV přijímačů se tímto krokem nemění.

- Samsung: oprava nestabilního testu potvrzení dočtené Story repliky. Simulovaný
  čas ověřuje i případ, kdy 1 400 ms ještě nestačí kvůli fázi vykreslovacího tiku.
  Časování přijímače ani doba zobrazení titulků se nemění.
- Android TV v režimu „S telefonem“ nově umí Story: měří stránky podle boxu,
  zachovává čtecí čas při pauze, skrývá text mimo vstup a při volbách a potvrzuje
  dočtení poslední stránky. Opakované pakety čtení nerestartují; staré generace
  neobnoví zrušený text. PCS1 Story je oddělené od stávajícího předání spárování.
- U všech TV včetně Androidu se Story aktivuje až po platném PCA1 od
  zvoleného cíle. Do té doby pokračuje Standardní výstup; přenos se přepne až
  s první Story stránkou. Staré titulky se po návratu na Standardní neopakují,
  volby zůstávají funkční. Starší Android TV bez podpory zůstane na Standardním
  výstupu; je nutné aktualizovat telefon i TV. Místní Story, LG/Samsung protokol
  a režim „Jen televize“ se nemění. Přejímka na TCL zatím neproběhla.
- Stav konzole už nezelená pouhým spuštěním služby: rozlišuje čekání na spojení,
  čekání na první snímek, příjem a výpadek obrazu. V běžném UI jsou krátké hlášky;
  úplné důvody a technické podrobnosti zůstávají v Záznamu.
- Fatální důvod (např. požadavek PS5 na přihlašovací PIN) zůstává viditelný
  i po zastavení služby; nový pokus nebo ruční vypnutí jej vyčistí.
- Výstup do TV ukazuje chyby UDP odesílání a otevřeně uvádí, že přijetí a zobrazení
  nejsou potvrzené. Android přijímač hlásí chybu soketu, oprávnění či okna překryvu
  a skrytí mimo TV vstup. Aktuální stavy jsou i v Záznamu.
- Beze změny formátů paketů pro LG, Samsung a Android TV, kodeků a nastavení.

- LG průvodce odděluje úspěšnou instalaci a přijetí požadavku na spuštění od
  samostatné kontroly běžícího přijímače. Potvrdí jen LG se shodným ID i IP,
  ukáže čas kontroly a zapíše výsledek do Záznamu. Opožděné/zrušené hledání se ignoruje.
- Kontrola využívá stávající discovery včetně staršího ohlášení bez nového ACK.
  Nemění výstup titulků, přenosové protokoly ani instalační balíček.

- Samsung (Tizen): přijímač titulků sloučen z bestranger/samsungappka do `tizen/`. Přijímač 1.3.0 podporuje Příběhový režim a hlásí svou verzi. Android: platforma Samsung, průvodce s Developer Mode, uložení přibaleného nepodepsaného WGT a ověření běžícího přijímače včetně verze. Přímá instalace z Androidu zatím není; důvody a další kroky jsou v `tizen/INSTALACE-Z-ANDROIDU.md`. LG ani Android TV cesta se nemění. Po sloučení s LG discovery: broadcast s verzí Samsungu se dál přijímá (nonce se kontroluje jen u přímé odpovědi) a Samsung, stejně jako LG, nevstupuje do převzetí přihlášení ke konzoli.
- LG přijímač 0.2.3: discovery obnovuje síťové adresy při běhu, reaguje na přímý dotaz uložené TV a ignoruje opožděné události starého socketu. Android zachovává broadcast pro Android TV/starší LG a navíc ověřuje známé LG přes IP, ID a náhodný identifikátor dotazu. Převzetí přihlášení z Android TV nepouští LG do nepodporovaného postupu. LG UI hlásí chybějící síť a nepotvrzený start příjmu do 10 s. Fyzické ověření problémové B4 ještě čeká.
- Android LG instalátor: průvodce ukazuje kód, důvod a stav chyby vrácené TV, s možností text zkopírovat. Ukončené spojení a neplatná odpověď mají vlastní hlášky. Diagnostika vybírá jen omezená chybová pole, bez úplných odpovědí a přihlašovacích údajů.
- LG přijímač 0.2.2: balení IPK používá pevné datum 1. 1. 2000 místo roku 1970, které může webOS 5.x odmítat chybou `-5: ipk verified failed`. Build kontroluje data v obou TAR archivech; regresní testy hlídají obsah, oprávnění a opakovatelnost. Chování přijímače a protokoly se nemění; ověření na webOS 5.6.2 ještě čeká na uživatele.

## 0.21 (versionCode 33)

Co je nového pro hráče:
- **LG televize (webOS):** titulky z Android telefonu nebo tabletu se zobrazí i na LG. Telefon do LG sám nainstaluje přijímač titulků (TV musí mít zapnutý Developer Mode). Ověřeno na LG C5. Autor: Rudolf Nohal.
- **Příběhový režim (telefon/tablet, volitelný):** repliky se ukazují postupně za sebou, žádná se nepřeskočí a každá svítí tak dlouho, aby se dala přečíst. Rychlost čtení 50–200 %, při volbách ve hře se čtení zastaví. Když se fronta nahromadí, aplikace sama zrychlí a pak se vrátí k nastavené rychlosti. Výchozí je dál Standardní režim. Autor: Rudolf Nohal.
- **Více televizí v telefonu:** profily televizí s vlastním nastavením polohy a oblastí titulků.
- **Méně blikání a chyb ve Standardním režimu:** zkracující se nebo dopisovaný titulek se ukáže až po potvrzení dalším čtením, starý pomalý překlad už nepřepíše nový titulek.

Technické podrobnosti:
- Story experiment: konzervativní skládání nedokončených anglických vět před překladem, ID/revize zdrojů, omezené čekání a oddělení následující věty. Volby a přetížená fronta čekání ukončí; Standardní režim a LG protokol zůstávají. Bez oprav jednotlivých slov a bez AI služby. Herní přejímka ještě probíhá.

- Story: od tří čekajících replik se časování automaticky exponenciálně zkracuje, nejvýše na polovinu uživatelské doby a ne pod podporovaných 50 %. Platí od další stránky, bez zahazování replik; po odbavení se vrací nastavená rychlost. Stav ukazuje aktivní dohánění. LG aktualizace není potřeba.

- Story: podle herní přejímky GTA V je základní čtecí doba o 20 % kratší; výchozích 100 % nyní odpovídá předchozím 80 %. Shodný výpočet Android/LG 0.2.1, Standardní režim beze změny.

- Integrační větev Story spojuje správu TV profilů, instalátor a Developer Mode s Android čtecí frontou. LG 0.2.0 podporuje Standardní i Příběhový režim; jeho nabídka, skrytí a lokální test pozastaví Story bez falešného dočtení. Master ani veřejné vydání se tím nemění.

- Příběhový režim: kratší základní zobrazení při výchozích 100 %, posuvník délky 50–200 % po 5 % a výrazná tlačítka Standardní/Příběhový. Vyžaduje LG přijímač 0.2.0; rozčtená stránka se při změně času nerestartuje.

- Volitelný Příběhový režim pro Android tablet + LG 0.0.8: čtecí fronta a potvrzené dočtení všech stránek; Standardní režim zůstává výchozí. Herní přejímka zatím probíhá.

- Opakované OCR čtení stejné repliky se nespojí jako pokračování věty; regrese zachycená při telefonátu v GTA V.
- OCR zachovává příslušnost řádků k blokům; větší řádkování uvnitř jednoho bloku nemusí vyřadit začátek repliky.
- Jednorázově neúplný OCR snímek nepotvrdí zkrácení textu. Doplněný text lze znovu přeložit i bez závěrečné tečky.
- Opožděné překlady kontrolují platnost textu, nastavení oblasti a spojení. Ruční režim Text je chráněn proti jednorázově neúplnému čtení celého odstavce.
- Přidané JVM regresní testy a samostatný test skutečného ML Kit na Androidu; rozsah a omezení popisuje `docs/OCR-AUDIT.md`.
- Android LG: společné šifrované spárování pro správu přijímače a platnost Developer Mode. Skutečná verze se kontroluje před instalací; stejný přijímač se nepřeinstaluje a novější se nevrací na starší.

- Android LG: skutečný zbývající čas Developer Mode pro konkrétní TV, datum ověření, ruční kontrola a upozornění pod tři dny. Šifrované uložení relace, označení staršího/chybového údaje; bez prodlužování Developer Mode a bez změny TV přijímače.

- Android LG průvodce: oddělená tlačítka, upozornění na platnost Developer Mode u přípravy i instalace, záložní správce Aphanes pro Android před počítačem a uložení přibaleného IPK do zvolené složky.

- Android: průvodce připojením LG po jednotlivých krocích, odkazy na LG Developer a instalační nástroje, návrat k rozpracovanému kroku a vyhledání přijímače na konci. Přímý instalátor přibaleného IPK přes Developer Mode/Key Server ověřuje přenos i nainstalovanou verzi; ruční postup zůstává zálohou.
- Android telefon/tablet: oddělená volba TV platformy a konkrétního přijímače, vlastní nastavení polohy a oblastí pro každou TV, zachování původního profilu při změně IP. Přepnutí výstupu vyčistí starý přijímač; jazyk a účet konzole zůstávají společné.
- LG dokumentace: odstraněn interní pracovní plán z repozitáře i distribučního ZIPu, upřesněno předávání instalačního IPK přes Releases.
- LG 0.1.0 (kandidát P0): nabídka přes OK/Zpět, lokální test vykreslení, dočasné skrytí a diagnostika známých stavů včetně upozornění na nepodporovaný Story režim. Jeden aktivní zdroj do 5 s bez jeho dat. Reprodukovatelný IPK a manifest verze, schopností, velikosti a SHA256; bez změny Android kódu nebo Story protokolu.

- LG přijímač: jednotný název PlayCaption LG, Android ikona v obou velikostech launcheru a hlášení pro obecné odesílající zařízení. HDMI a discovery ověřené i s jinými vstupy/podsítěmi; aktualizovaný návod včetně možnosti instalace IPK z Android správce bez PC.

- LG přijímač 0.0.7: dialogy se střídají po nejvýše dvou řádcích, dlouhé repliky po stránkách. Nová replika ruší zbytek staré; text má nejvýše 18 % výšky obrazu.

- LG přijímač 0.0.6: dialogové volby a zvýraznění, dlouhé texty, žlutý/modrý rámeček oblasti čtení, zelený náhled polohy a barvy jmen. Matice funkcí rozlišuje shodu TV přijímačů a OCR/párování konzole na tabletu.

- LG přijímač nabízí výběr HDMI přímo na TV a po přepnutí obnoví překryv; počítač je potřeba pouze k instalaci.
- LG webOS: návod instalace a každodenního používání, lokální sestavení distribučního ZIPu a automatické testy; trvalá chybová signalizace prodlužování překryvu a ignorování opožděných odpovědí ze zrušených volání.
- LG webOS: přijímač obnovuje systémový časovač překryvu, aby jej TV při hraní automaticky nezavírala po 120 sekundách.
- LG webOS přijímač `webos/`: ohlašování do sítě pro stávající Android aplikaci, příjem titulků/nápovědy přes UDP, poloha a velikost podle tabletu, skrytí při výpadku spojení. Bez nutnosti upravovat Android APK.

- Příběhový režim má kratší výchozí časování při 100 %, nastavitelnou délku zobrazení 50–200 % a skutečná tlačítka Standardní/Příběhový. Procenta se ukládají na Androidu a řídí i LG přijímač 0.2.0; změna se použije od další stránky. Standardní režim zachovává dosavadní časování.
- Samostatné jádro čtecí fronty Příběhového režimu s pořadím/revizemi replik, časováním českých stránek, pauzou a simulovanými regresními testy.
- Experimentální napojení Příběhového režimu na Android překladač a LG 0.0.8: volitelný přepínač, jeden finální překlad, pořadí replik, potvrzení dočtení poslední stránky, pauza pro volby a návrat do Standardního režimu.

## 0.20 (versionCode 31)
- PS5 na telefonu: obraz jde z dekodéru přímo do paměti (jako u Xboxu) – čtení titulků 20–40 ms místo ~80 ms. Televize beze změny.
- PS5 na telefonu: kodek H.265 (čistší obraz, ostřejší písmo) – automaticky, jen když ho telefon umí; jinak H.264.
- PS5: poškozený obraz po výpadku Wi-Fi se nečte (méně nesmyslů).
- Spojování rozdělených vět: jen když pokračování přijde hned; tři tečky zůstávají; useknutý titulek se po dočtení přeloží znovu; useknutý začátek („et“ místo „Let“) se přečte znovu.
- Titulky už nepřeblikávají (rychlý offline překlad jen když Google neodpoví do 0,15 s).
- Telefon: po „Připojit“ rámeček „Máš PlayCaption už přihlášenou v televizi?“ (Vyhledat televizi / Nemám televizi).
- Televize: nový návod „Titulky z telefonu“ pro obě cesty spárování telefonu.
- Telefon: Zpět aplikaci nezavře (spojení drží), displej nezhasne, když běží spojení; srozumitelné hlášení, když PS5 drží jiné zařízení.
- Záznam: přehled připojení a odpojení, delší výpis titulků.

## Stará beta (jen televize) – větev `beta-0.9` (už se nevyvíjí)

### 0.9.2 (versionCode 12)
- Lepší čtení: zkomolené slovo z rozmazaného snímku se nepřekládá hned, počká se na ostřejší snímek; úplný nesmysl se na televizi neukáže.
- Slabší televize: čtení titulků běží „v pozadí“ (spojení s PS5 má přednost), úsporný režim se zapne hned po prvním pomalém čtení nebo výpadku.
- Po výpadku obrazu delší pauza (8 s) před novým připojením – méně chyb „already in use“.
- Srozumitelné české zprávy při odpojení („připojím se znovu sám, nic nedělej“).

### 0.9.1 (versionCode 11)
- Televize, které nedávají obraz (Philips, Xiaomi): po 15 s se aplikace sama přepne na jiný způsob čtení obrazu (OpenGL).
- Bez povolení „Zobrazovat přes jiné aplikace“ už aplikace nespadne – ukáže návod.
- Pojistka proti opakovanému pádu po zapnutí televize.
- Poděkování projektu Chiaki / chiaki-ng v aplikaci.

### 0.9 (versionCode 10)
- První veřejná beta.

## Hlavní verze (televize + telefon) – větev `master`

### Beta 0.18 (versionCode 29)
- Oprava: televize už nespadne při předání PS5 telefonu („Translator has been closed“ – např. po přepnutí telefonu na foťák a zpět).
- Telefon se po 1 minutě odpojí od PS5 jen když je zamčený (zhasnutý displej). S otevřenou jinou aplikací (foťák – natáčení videa) překlad běží dál.
- Televize: v kartě „Připojit ke konzoli“ nové tlačítko „+ Přidat Android zařízení“ – okno s QR kódem ke stažení aplikace, 4místným kódem (platí 180 s, odpočet, „Nový kód“) a adresou televize. Po spárování okno samo zmizí.
- Dlaždice „Jen televize“: místo „Funguje každému“ teď „Slabší televize nemusí stíhat“.
- Slabá televize = do 2 GB paměti (dřív pod 1,8 GB) – ale jakmile televize při hraní 10× po sobě přečte titulek rychle, upozornění se už neukazuje. Upozornění má žlutočerné výstražné proužky, tlačítka „Zpět“ / „Pokračovat“.
- Upozornění „Televize má slabý výkon“ (jen na opravdu slabé televizi při volbě „Jen televize“) říká i to, že aplikace může přestat reagovat nebo se ukončit, a doporučuje Android telefon / tablet.
- „Menu a ovládací nápověda“ už nemá Ano/Ne (televize ani telefon) – menu, úkoly a dopisy si každý přeloží žlutým rámečkem v mobilní aplikaci.
- Když aplikace v režimu „Jen televize“ spadne, po dalším otevření se nepřipojí sama dokola: ukáže „⚠️ Aplikace minule spadla“ (výstražné proužky, Zpět / Pokračovat) a televizi si zapamatuje jako slabou.
- Uvítání je kratší a přátelštější („Ahoj a vítej v PlayCaption!“ – beta, zpětná vazba ve Facebook skupině) a při prvním spuštění se ukáže až po výběru jazyka, rovnou v tom jazyce. Nemizí samo – pokračuje se tlačítkem „OK, jdeme na to“.
- Televize: po kliknutí na „Připojit“ nejdřív čitelné upozornění, že s mobilní aplikací (Android telefon / tablet) se v televizi přihlašovat nemusí. Tlačítka „Zpět (budu používat mobilní aplikaci)“ → druhá tabulka „vyber dole dlaždici S telefonem“ (potvrdit OK), a „OK (přihlásit televizi)“.
- Telefon: tlačítko „📺 Vyhledat televizi“ – seznam nalezených televizí, klepnout na svoji a opsat kód. Nenajde-li televizi: „Zkusit znovu“ nebo „Zadat adresu televize“.
- Nespárovaný telefon, který v síti najde SPÁROVANOU televizi s PlayCaption, schová přihlašování („Připojit“) – stačí opsat kód z televize. Nespárovaná televize nevadí: telefon se spáruje s PS5 sám a televize jen ukazuje titulky.

### Beta 0.17 (versionCode 28)
- Režim s telefonem: jméno postavy má na televizi (i v telefonu) barvu jako ve hře – stejně jako v režimu Jen televize („Vivian:“ zeleně). Se starší verzí na druhém zařízení je jméno prostě bílé.

### Beta 0.16 (versionCode 27)
- Navazuje na 0.14 (zase jedna apka pro televize i telefony).
- Titulky jen v telefonu (📱 Telefon): telefon jde otočit na šířku → černá obrazovka jen s velkými titulky přes celou šířku a malou zelenou tečkou „Titulky aktivní“. Obrazovka s posuvníky (📺 Televize) zůstává jen na výšku.

### Beta 0.15 (versionCode 26) – jen pro test na Philipsu, odložená ve složce `NextUpdate_015`
- Dvě apky: TV (navíc „Zapnout přes Usnadnění“ pro televize se schovaným povolením) a Telefon (bez Usnadnění).

### Beta 0.14 (versionCode 25)
- Odebráno „Zapnout přes Usnadnění“ – telefony Xiaomi (HyperOS) kvůli němu verzi 0.13 odmítly nainstalovat („Aplikaci nelze nainstalovat“). Jinak stejné jako 0.13.

### Beta 0.13 (versionCode 24)
- Tlačítko „Povolit zobrazení přes jiné aplikace“ otevře nastavení vždy, když to jde (stránka povolení → seznam aplikací → nastavení PlayCaption); návod až když televize nastavení nemá.
- Zapnutí přes Usnadnění (pro televize se schovaným povolením) – v 0.14 zase odebráno.

### Beta 0.12 (versionCode 23)
- Už by nemělo být potřeba „Vynutit ukončení“: ukončování starého spojení běží zvlášť a nezablokuje hledání PS5; když PS5 v síti není vidět, aplikace se zkusí připojit přímo.
- PS5 se hledá i přímo na známé adrese – na sítích, kde hromadné hledání neprojde (oddělená 2,4/5 GHz Wi-Fi), už není potřeba pořád znovu párovat.
- Žlutý rámeček má nový režim „📄 Text“: přeloží celý text v rámečku najednou (dopis, SMS, popis úkolu) a ukáže ho na televizi jedním oknem.
- Volby odpovědí (žlutá tabulka) se ukážou jen, když hra opravdu ukazuje zvýrazněnou volbu – loga a nápisy (např. na načítací obrazovce) už na televizi nezůstanou viset.
- Při tažení rámečků v mini televizi se už nehýbe celá stránka.
- Visící staré spojení s PS5 (po výpadku Wi-Fi, zhasnutí telefonu…) se před novým připojením vždy ukončí – méně chyb „already in use“.

### Beta 0.11 (versionCode 22)
- Telefon může převzít spárování s PS5 od televize: na televizi „S telefonem“, v telefonu „📺 Převzít spárování z televize“ a opsat 4místný kód z televize. Bez přihlašování účtu a PINu z PS5.

### Beta 0.10 (versionCode 21) – dřívější NextGen
- Nově jedna aplikace pro všechny: „Jen televize“ i „S telefonem“.
- Vše z bety 0.9.2 (lepší čtení, slabší televize, české zprávy).
- Telefon dostává z PS5 ostřejší obraz (720p, víc dat) – přesnější čtení v akčních scénách.
- Přepínač v telefonu [📺 Televize | 📱 Telefon] – kde se ukazují titulky, jedním klepnutím.
- Modrý rámeček „Čtení titulků: RUČNĚ“ – čte se jen tam, kde jsou titulky; posuvníky šířky a výšky.
- Zámečky u zeleného, modrého i žlutého rámečku (zamčený rámeček nejde posunout, posuvníky se schovají).
- Šedá nápověda pod tlačítky; v režimu televize jen malý zelený řádek nahoře, nastavení jde posouvat.
- Nic nezamrzne na televizi: po uspání PS5 telefon smaže nápovědu i volby; pojistky 20 s / 45 s / 5 s.
- Otočení telefonu na bok už nepřeruší posílání do televize (aplikace jen na výšku).
- Slabá televize: aplikace doporučí režim s telefonem a upozorní, když člověk zvolí jen televizi.

### NextGen 0.1 (versionCode 20, jen testovací)
- Režim „S telefonem“, volby odpovědí (žlutý rámeček), úpravy polohy titulků z telefonu.

