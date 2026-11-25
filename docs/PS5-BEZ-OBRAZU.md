# PS5 bez obrazu a příjem překladů z telefonu

## Doplnění – potlačení přehrávání Remote Play

Adamova přejímka aa60273 na TCL + POCO: 10/10 přechodů, 20/20 dokončených
TV relací, dvakrát Zastavit/opět připojit, bez překročení 5 s a bez pádů.
Ve starém logu navíc dohledal „Failed to get input audio buffer“ 4 s před
zaseknutím #b2f2cd3. To podporuje původní příčinu; úplný nativní stack chybí.
Test zavření telefonu před ConnectedEvent zůstal neověřený kvůli odmítnutému
ADB vstupu na Xiaomi (karta se neodsunula).

Nově nahlásil dvojitý zvuk: při „Jen televize“ bylo vedle HDMI slyšet i pomocný
Remote Play stream. Příčinou dřívějšího ticha mohl být zaseknutý audio dekodér,
ale report to neprokazuje. Nový patch proto výslovně vypíná místní přehrávání:
audio-output settings neotevírá zařízení a audio-output frame PCM zahazuje.
Platí pro obě ARM varianty telefonu i TV. Příjem, dekódování, odčerpávání bufferů,
EOS, oprava mutexu i stop → dispose → uvolnění výstupu se nemění.
Nevolá se systémové ztlumení, hlasitost ani změna HDMI zvukové cesty.

Před sloučením na novém commitu ověřit poslechem, že „Jen televize“ nezdvojuje
zvuk a hra přes HDMI dál hraje; totéž při „S telefonem“, pozastavení a obnovení.
Současně zopakovat předávání, obraz/titulky a dokončené úklidy bez přibývání
ps5-konec. Přejímka aa60273 nepokrývá tyto nově sestavené knihovny.
PR #15 zůstává draft, bez release, tagu a změny verze.

Ověření potlačení přehrávání: dva nové nativní testy nad skutečným audio-output.cpp
prošly spolu s původními šesti mutex/EOS scénáři, také pod ASan/UBSan. Prošlo
sestavení obou ARM variant, kontrola JNI/závislostí a SHA-256 knihoven v APK,
242 JVM testů, šest webOS sad i Samsung renderer testem z #21. Síťové sady
musí běžet odděleně: při jejich chybném souběhu vznikla kolize UDP portů;
následný izolovaný JVM běh prošel bez selhání.

Na připojeném Redmi Pad Pro (API 36, ARM64) navíc samostatný dočasný proces
načetl skutečnou novou knihovnu, zavolal nastavení 48/44,1 kHz, PCM callbacky
a uvolnění. Po nastavení a PCM nebyl jeho PID mezi AudioFlinger klienty;
proces skončil bez chyby. Instalovaná aplikace se tímto testem neměnila.
Nešlo o PS5 relaci ani poslech HDMI na TCL; tato přejímka stále zbývá.

## Doplnění – 17. 11. 2026: nativní audio zámek

Přejímka fa223f4 na TCL 65C805 + POCO F8 Pro nakonec zaznamenala 20/21
dokončených ukončení. Relace TV #b2f2cd3 v 01:28:58 dokončila stop za 11 ms,
ale zůstala v sessionFree; po video release chybělo audio EOS i dokončení JNI.
PS5 se uvolnila, ale ps5-konec a NDK MediaCodec_ zůstaly v procesu. Není správné
tento stav označit za úplnou opravu nebo za neškodný dlouhodobě.

Kontrola upstream kódu a původních ARM binárek našla konkrétní chybu:
audio frame při selhání dequeueInputBuffer vrací řízení bez odemčení
codec_mutex. sessionFree pak v jiném vlákně čeká ještě před logem audio EOS.
Video větev obdobnou chybu správně řeší přes společné odemčení. Jednořádkový
patch mění stejným způsobem audio návrat; bez změny časových limitů a kodeků.

Řízený nativní test na původním kódu chybu reprodukoval; po patchi prošlo šest
scénářů včetně částečného rámce a rekurzivní inicializace. Test používá upstream
audio-decoder.c i skutečná pthread vlákna, ale řízený MediaCodec, nikoli TCL.
Původní report neobsahoval celý nativní stack ani řádek předchozího selhání audio
bufferu; později dohledané řádky a přejímka aa60273 jsou popsané výše. Úplný
nativní stack stále chybí a jiné chyby systémového dekodéru nelze vyloučit.

V aa60273 vznikly ARM knihovny TV i telefonu ze stejné připnuté verze Chiaki-TV
pouze s opravou mutexu. Následné potlačení přehrávání je popsané výše.
Postup, testy a omezení: [native/README.md](../native/README.md).
Pořadí stop → dispose → uvolnění výstupu a ochrany fa223f4 zůstávají.
Nová přejímka musí zahrnout více opakování původního přechodu TCL z „Jen televize“
na „S telefonem“, celá ukončení i kontrolu vláken a paměti po přechodech. Test
zavření telefonu před ConnectedEvent zůstává podle dodaného hlášení neověřený.
PR #15 zůstává draft, bez release, tagu a změny verze.

Lokální ověření aa60273: šest nativních scénářů prošlo také s ASan/UBSan;
na původním kódu zámková regrese selže. Prošlo 242 JVM testů a sestavení debug
i instrumentačního APK. V APK byly porovnány SHA-256 všech čtyř nových ARM knihoven
s native/chiaki-build.json, JNI názvy a sdílené závislosti jsou shodné s původními.
Prošlo šest webOS sad a Samsung renderer (test z již sloučeného #21 nad Samsung
zdroji této větve, protože její původní test stále používá reálné čekání).
ADB nevidí zařízení; instrumentační testy ani nová fyzická přejímka se zde nespustily.

## Předchozí stav – 16. 11. 2026

### Dodatečná oprava životnosti plochy

Každá Session nyní vlastní svůj GlSnimac nebo ImageReader. Při vytvoření se
výstup převede z přípravných polí služby do registru konkrétní Session; další
spust() ani onDestroy() už její živou plochu nezavírají. Registr ji drží i při
zablokování nebo výjimce v nativním úklidu a opakované zastavení nezačne druhý
úklid stejné relace.

Pořadí je stop → úspěšné dispose(join/free) → uvolnění jejího výstupu.
GL zavření pak provede vlastní GL vlákno. Pokud dispose čeká nebo vyhodí chybu,
výstup se neuvolní násilím ani pomocí GC. V logu je samostatný krok
„uvolnění výstupu“, který nesmí předcházet konci dispose. Obslužné vlákno
ImageReader zůstává živé do dokončení všech výstupů této instance služby;
neplatné staré snímky pouze odčerpává a zavírá, nespouští nad nimi OCR.

Při přejímce zaznamenat pořadí pro odpovídající ID při předávání, opětovném
připojení i konci služby. Nativní pád se nemusí dostat do pad.txt; na TCL
proto zachytit také logcat. Telefonní Záznam obsahuje kroky a Java stacky
i bez připojení ADB, ale nenahradí celou nativní diagnostiku.

Po této opravě prošlo 242 JVM testů a sestavení aplikace i instrumentačního APK.
Šest nových regresí ověřuje pořadí zavření, čekající a chybný dispose, nezávislost
výstupů dvou relací a oba směry dokončení služby/dekodérů včetně prázdné služby.
Prošly i tři webOS Node sady, Samsung služba a git diff --check; Samsung vykreslení
se přeskočilo kvůli chybějícímu Playwrightu. Přejímka na telefonu a TCL stále zbývá.

Lokální rozšíření draftu zahrnuje také aktuální master s #16 a integraci #17;
před sloučením této větve nejprve sloučit #17 a zkontrolovat výsledný diff.
Story rozšíření #20 tato větev neobsahuje. Žádná nová fyzická přejímka neproběhla.

PR #15 zůstává draft. Adam předal přejímku na TCL 65C805, která potvrdila
zablokování `ps5-konec` v `setSurface(null)` ještě před zavoláním `stop()`.
Níže je úzká oprava. Její nové sestavení ještě nebylo ověřené na stejné TCL;
bez opakované přejímky PR neslučovat. Původní soubory `ZALOHA\tv_titulky.log`
a `ZALOHA\diag_vlakna\*` jsou u Adama, na tomto Macu nejsou k dispozici.

## Dodatečný report telefonu POCO F8 Pro

Předané záznamy z 0.22-test3 (075b9d6, tedy bez opravy #15) ukazují obraz
před převzetím PS5 v 22:27:09, následná odmítnutí obsazené konzole a později
úspěšné handshaky bez snímků. V 22:38:44 a 22:43:44 po hlídači chybí
událost Stopped; samotná její absence ale ještě neprokazuje konkrétní JNI deadlock.
Restart PS5 22:44:22 podle logu nepomohl. Obnovení obrazu až po vynuceném
ukončení telefonu v 22:48 je předané pozorování; přiložené textové logy končí
před ním. Ukazuje to na stav v procesu telefonu, nerozlišuje to síťový vstup,
dekodér, Surface ani opožděné události. Telefonní stack zatím chybí.
Čítač 1801 v tehdejším řádku „za 30 s“ je kumulativní od začátku relace,
nikoli počet snímků v tomto třicetisekundovém okně.

Kontrola kódu našla, že callbacky starých Session bez ověření zdroje volaly
onUdalost pro aktuální relaci. Nově se přijímají pouze callbacky aktuální Session;
ta je registrovaná ještě před startem. Starý ConnectedEvent tak nesmaže počítadla
a starý QuitEvent nezastaví nové spojení. Opožděný desetisekundový test je rovněž
svázaný s původní Session; staré GL/YUV callbacky kontrolují generaci čtení.

Po třech různých připojených relacích bez jediného dekódovaného snímku se automatické
pokusy zastaví a zůstane konkrétní důvod s návodem na Vynutit ukončení PlayCaption
na příslušném zařízení. Nevydává se to za automatickou opravu dekodéru. Pojistka
platí pro celý proces, přežije nový Service, Stop i Hotovo – hrát. První skutečný
snímek před aktivací pojistky řadu vynuluje; samotný handshake ji nemaže.
Odmítnutá nepřipojená relace se do této trojice nepočítá. Xbox tato pojistka neřídí.

Počítadlo po sobě jdoucích already-in-use odpovědí je oddělené od backoffu
neuspechu a zapisuje se s ID relace. Od šesté odpovědi hláška připouští jak jiné
zařízení, tak neukončenou vlastní relaci. Nelze z ní pravdivě určit vlastníka PS5.
Dodaná fotografie z 22:32 již obsahuje původní zpřísněnou hlášku; tvrzení, že se
nikdy neukázala, proto není tímto souborem důkazů potvrzené.

### Proč může chiaki.log ukazovat nulu

Referenční [JNI logger](https://github.com/505e06b2/Chiaki-TV/blob/f814cf69d636f149ad5a3cfffd19cb22da90beda/android/app/src/main/cpp/log.c#L68)
otevírá soubor s w+ při každé nové Session a zapisuje přes bufferované fwrite
bez průběžného fflush; uzavře jej až při úklidu. Není to nezávislý důkaz přesného
chování upravené telefonní binárky. V Kotlinu se sdílený chiaki.log nově nemaže,
nemění se verbose ani nativní knihovny. Přidává se měření délky před/po sessionCreate,
po ConnectedEvent a dokončeném dispose, spolu s volbou chiaki-pc/chiaki-jni.
Záznam u nulového souboru výslovně vysvětlí, že nula není důkazem absence paketů.

První dekódovaný snímek se zaznamená samostatně v titulky.log pro GL, YUV i
telefonní paměťovou cestu. Callback prvního síťového video paketu dodané Java API
nemá. V repozitáři je native/pcobraz.c (mezivrstva MediaCodec), ale ne úplné
zdroje upraveného libchiaki-pc.so ani reprodukovatelný postup sestavení dodaných
binárek; na tomto Macu není Android NDK. Mezivrstva může měřit vstup do dekodéru,
což ale není totéž jako první UDP video paket. Přímé měření prvního paketu zatím
není implementované. Pro odlišení síťového vstupu od dekodéru musí Adam zachytit
souběžný logcat (tag Chiaki, PlayCaptionObraz, MediaCodec/Codec2) a nový stack
telefonu. Další nativní změna má vycházet z tohoto měření, bez výměny binárek naslepo.
Do nové aplikační diagnostiky se nepřenáší obsah nativních zpráv ani údaje účtu.

### Přejímka doplnění na telefonu

1. Použít tento draft na telefonu i TCL. Na každém zařízení zachovat jeho vlastní
   dosavadní podpis (telefon může mít release, TV debug); podpisy mezi zařízeními
   stejné být nemusí. Aktualizace bez mazání dat.
   Před měřením jednou vynuceně ukončit původní zaseklé procesy, potom už ne.
2. Ověřit obraz na telefonu, převzetí PS5 televizí a následné převzetí telefonem.
   Nejméně třikrát; zachytit stejné časy a ID z obou zařízení.
3. Pro každé odpojení čekat stop začátek/konec a dispose začátek/konec. Po pěti
   sekundách se musí při čekání objevit stack ps5-konec. Žádné ukončovací setSurface(null).
4. První snímek musí být zaznamenaný s cestou a ID aktuální relace. Pokud stále
   chybí, uložit logcat i aplikační log před restartem procesu. Po třetí připojené
   relaci bez snímku už nesmí vznikat další Session; Stop/Hotovo pojistku neobejde.
5. Po šesté souvislé odpovědi already-in-use ověřit odpovídající počítadlo a hlášku.
   Samotné pokusy bez připojení nesmějí aktivovat třícyklovou obrazovou pojistku.

Bezpečný timeout nemůže zaručit návrat libovolného visícího JNI volání. Tato větev
nevolá sessionFree souběžně s běžícím nativním vláknem ani sama nezabíjí proces.
PR #15 zůstává draft do přejímky nového pořadí ukončení na TCL i telefonu.

### Automatické ověření telefonního doplnění

Prošlo `bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`:
236 JVM testů bez selhání, z toho šest nových scénářů HlidaniPs5Test. Pokrývají
staré události, přežití pojistky, reset až skutečným snímkem, nezapočítávání pouhého
odmítnutí a hranici šesté obsazené odpovědi. Prošly také tři webOS Node sady,
Samsung služba a git diff --check. Samsung vykreslení se přeskočilo kvůli chybějícímu
Playwrightu. Instrumentační APK bylo pouze sestavené; ADB nevidí žádné zařízení.
Soukromé předané logy nejsou součástí repozitáře. Nativní binárky a čísla verzí
zůstávají beze změny. Tyto testy nejsou hardwarovou přejímkou nativního ukončení.

## Rozhodující důkaz předaný Adamem

- Relace `#a226453`, GL: připojena 20:33:29, přechod do „S telefonem“ 20:34:05.
- `20:34:05 [PS5 konec #a226453] setSurface(null) začátek`.
- `20:34:10 5000 ms překročeno: setSurface(null)`; stack
  `ChiakiNative.sessionSetSurface(Native Method) ← Session.setSurface(Chiaki.kt:393)
  ← ukonciVPozadi(RemotePlayService.kt:2140)`. `stop` nezačal.
- Podle reportu ani po více než 90 s nenastal konec tohoto kroku; `ps5-konec`,
  MediaCodec a Takion dál běžely. Telefon se připojil až po vynuceném ukončení TV.
- Oddělená kontrola „S telefonem“: start 20:40:10, neprázdný PCT1 s 18 znaky
  20:42:22, `vstupTV=true`, oprávnění překryvu=true a Adamem potvrzené zobrazení.
  To potvrzuje funkční příjem/překryv v tomto pokusu, nikoli opravu ukončení.
- `debuggerd -j` bez rootu nefungoval, ale diagnostický Java stack zachytil
  vstup do konkrétního JNI volání. Pro tuto změnu není nutné získat root.

## Úzká oprava po zachycení závady

Ukončení nyní volá `stop()` → `dispose()` (`sessionJoin` → `sessionFree`).
Samostatné `setSurface(null)` se v této cestě nevolá. Pouhé přehození na
`stop()` → `setSurface(null)` → `dispose()` by stále mohlo zablokovat úklid
ve stejném Surface volání.

Jde o stejný postup jako
[shutdown v Chiaki-TV](https://github.com/505e06b2/Chiaki-TV/blob/f814cf69d636f149ad5a3cfffd19cb22da90beda/android/app/src/main/java/com/metallic/chiaki/session/StreamSession.kt#L41).
Lokální `Session.dispose()` čeká přes join a až potom volá free. Referenční
[JNI sessionFree](https://github.com/505e06b2/Chiaki-TV/blob/f814cf69d636f149ad5a3cfffd19cb22da90beda/android/app/src/main/cpp/chiaki-jni.c#L368)
zahrnuje ukončení video dekodéru. Je to podklad k použití API, nikoli nezávislé
ověření shody každé dodané binární knihovny s tímto upstream commitem.

Diagnostika měří nové kroky `stop` a `dispose(join/free)`. Pokud se zastaví až
join nebo úklid dekodéru, záznam to rozliší. Žádné paralelní free, přerušení JNI,
násilné zabíjení procesu nebo nový ukončovací timeout se nepřidává.

Pozorování staré GL volby a rozdílu ~1000 ms proti dřívějším ~45 ms u YUV je
samostatný výkonový nález. Tato úprava GL preference nemění: přejímka musí
nejdřív zopakovat původní GL scénář. Historicky uložené GL už může být prvním
snímkem označené jako ověřené; jednorázový návrat k YUV vyžaduje samostatný
návrh a přejímku, nelze ho vydávat za součást opravy ukončení.

Dodané podklady zachycují různé situace:

- Hlášení „Připojeno, ale nechodí žádný obraz“ znamená, že aktuální
  relace ještě nemá snímky. Bez stacku nelze určit, zda je příčinou stará relace,
  síť nebo cesta dekódování.
- Dodaný textový log obsahuje přečtené anglické repliky a 898 snímků od startu
  relace. Nejde o nulu ani o naměřených 898 snímků za posledních 30 sekund.
- Fotografie telefonu potvrzuje český překlad na telefonu. Nedokazuje odeslání,
  příjem ani zobrazení na TV. Režim „S telefonem“ musí mít spuštěný přijímač;
  TV při něm nemá držet vlastní Remote Play relaci.

## Co tato větev mění

- Každé ukončení má místní ID relace a začátek/konec kroků `stop`
  a `dispose(join/free)` včetně délky. Při překročení diagnostických
  5 sekund se jednou pro daný krok zachytí Java stack příslušného vlákna.
  Kontrola běží na samostatném vlákně, nezávisle na hlavním vlákně aplikace.
  Limit pouze zapisuje diagnostiku; nepřerušuje JNI, nevolá paralelně stop
  a neuvolňuje paměť relace násilím.
- Události připojení/konce označují zdrojovou i aktuální relaci. Hlídač zaznamená
  cestu YUV/GL, roli zařízení, počet snímků a stáří posledního snímku.
- První chyba `GlSnimac` se zapíše místo tichého spolknutí. Diagnostika obsahuje
  třídu chyby a stack metod, nikoli PSN klíče, tokeny, údaje účtu či argumenty JNI.
- Automatické GL se nově ukládá až po skutečném snímku. Po doloženém YUV snímku
  v této instalaci výpadek relace sám nevyvolá přepnutí na GL. Při nule snímků
  z neověřeného GL pokusu se návrat na YUV řídí stávajícím hlídačem.
- Stará uložená volba GL se bez důkazu nemaže: nejprve se použije jako dosud
  a její funkčnost se potvrdí snímkem. Předchozí verze nezaznamenávala historii
  úspěšného YUV, takže tuto historii nelze zpětně odhadnout. Pokud staré GL dává
  obraz, ale je pomalé, tato větev sama neprokazuje, že má místo něj použít YUV.
- Způsob dekódování telefonu, kodeky a stávající časy 10/15/30 sekund se nemění.
  Spárování, účty ani další nastavení se nemažou. Pro aktualizaci zachovat podpis APK.
- V režimu příjmu z telefonu se zaznamená start, první textový PCT1 paket,
  rozhodnutí o vstupu TV a oprávnění překryvu. Obsah textu se do nové diagnostiky
  nezapisuje. Výsledek je ve stejném `titulky.log` a v okně Záznam; nové značky
  jsou také v logcat pod tagem `PlayCaptionDiag`.

## Opakovaná přejímka na stejné TCL – Adam

1. Použít nové APK z aktuálního headu PR #15, na každém zařízení sestavené jeho
   dosavadním podpisem. Telefon a TV mohou mít odlišné podpisy. Aktualizovat bez
   mazání dat. Zapsat commit, model,
   firmware, cestu GL/YUV a čas. Před začátkem smí být nutné jednou vynuceně
   ukončit už visící starý proces; od začátku měřeného pokusu už nikoli.
2. „Jen televize“: připojit PS5 a potvrdit příchod snímků. Na stejné GL cestě
   přepnout do „S telefonem“. Pro stejné ID relace očekávat začátek/konec
   `stop`, jeho návratový kód a začátek/konec `dispose(join/free)`. Nový záznam
   nesmí ukazovat ukončovací volání `setSurface(null)`.
3. Telefon musí převzít PS5 bez vynuceného ukončení TV a bez opakovaného
   `Remote Play on Console is already in use`. Na TV ověřit neprázdný PCT1
   a skutečně zobrazené české titulky. Pouhé přijetí paketu není přejímka obrazu.
4. Opakovat nejméně třikrát: „Jen televize“ → „S telefonem“ → „Jen televize“.
   Ověřit i běžné ukončení aplikace. Tlačítko „Vypnout překlad“ jen pozastavuje
   čtení a není testem ukončení relace; jeho význam tento PR nemění.
5. Zopakovat „Jen televize“ s YUV, pokud je tato cesta na dané instalaci dostupná
   bez mazání dat. Nevyvozovat z pomalého GL závěr o rychlosti YUV bez porovnání
   stejného měření a scény.
6. Pokud některý krok zůstane viset, nejprve uložit stejnou relaci z TV logu,
   stack z diagnostického limitu a časově odpovídající log telefonu. Teprve
   potom lze jako zotavení použít Vynutit ukončení. PR zůstává draft, pokud
   původní scénář nelze zopakovat, chybí důkaz převzetí nebo oprava nefunguje.

Limit pouze zapisuje diagnostiku. Není oprávněním k souběžnému `sessionFree`,
dokud nativní vlákno paměť relace může používat. Bez releasu, tagu a změny verze.

## Původní automatická kontrola (před telefonním doplněním)

`bash gradlew :app:testDebugUnitTest :app:assembleDebug` a `git diff --check`.
Na této větvi prošlo 204 JVM testů bez selhání nebo přeskočení a sestavení APK.
`VolbaObrazuTest` ověřuje historický úspěch YUV, zachování staré/ověřené volby GL,
neukládání pouhého pokusu, návrat neověřeného GL a opakované snímky bez dalších
zápisů nastavení. `PrubehUkonceniTest` simuluje čas a skutečně čekající Java
vlákno; ověřuje pořadí, délky, jednorázový stack a pokračování po výjimce.
Nové testy ověřují stop před dispose a diagnostiku při blokujícím dispose.
Původní blokování potvrdil Adamův report, novou nativní cestu na TCL automatické
JVM testy neověřují; rozhodne až opakovaná přejímka výše.
