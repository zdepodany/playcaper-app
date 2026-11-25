# Nativní audio opravy v Chiaki

Použitý zdroj je Chiaki-TV **2.1.1**, commit
[`f814cf69d636f149ad5a3cfffd19cb22da90beda`](https://github.com/505e06b2/Chiaki-TV/tree/f814cf69d636f149ad5a3cfffd19cb22da90beda).
Licence zůstává AGPLv3 / licence uvedené v upstream zdrojích. Verze síťové knihovny,
kodeky a verze jejích závislostí se v této opravě nezvyšují.

## Příčina a rozsah

`android_chiaki_audio_decoder_frame()` drží rekurzivní `codec_mutex`. Pokud
`AMediaCodec_dequeueInputBuffer()` nevrátí buffer, původní `return` přeskočí
odemčení. Stejné vlákno chybu nemusí poznat, ale po jeho ukončení čeká
`sessionFree` v jiném vlákně na tento zámek. Proto chybí i následující log
„Audio Decoder sending EOS buffer“.

`chiaki-audio-unlock.patch` mění pouze tento `return` na `goto beach`, tedy na
existující společné odemčení, stejně jako obdobná video větev. Časové limity,
EOS a pořadí ukončení dekodérů se nemění. Stejný chybový návrat byl nalezen také
v disassemblování původních ARM knihoven v repozitáři, ne jen v upstream zdroji.

`chiaki-silent-audio.patch` potlačuje pouze místní přehrávání pomocného Remote Play
streamu. Callback nastavení neotevírá Oboe/AAudio/OpenSL ES přehrávací stream
a callback PCM data zahazuje místo plnění přehrávací fronty. Příjem zvuku,
dekódování, uvolňování výstupních bufferů, EOS i oprava mutexu zůstávají zachované.
Žádný systémový mixer, hlasitost ani HDMI výstup
se nenastavuje. Nevzniká nový uživatelský přepínač a Xbox se nemění.

Původní TCL report zaznamenal 20/21 dokončených ukončení; poslední viselo
v `sessionFree` před audio EOS. Adam později dohledal chybu audio bufferu
4 s před tímto zaseknutím. Přejímka aa60273 pak dokončila 10/10 přechodů
a 20/20 TV úklidů. Následně nahlášený dvojitý zvuk řeší přehrávací patch výše;
tyto nově sestavené knihovny potřebují další přejímku TCL + telefon.
Úplný nativní stack původního visení chybí; jiné chyby MediaCodec nelze vyloučit.

## Test bez Android zařízení

Připrav čistý checkout uvedeného commitu mimo PlayCaption. Na něm příkaz
`python3 native/test/audio_shutdown.py /cesta/Chiaki-TV` záměrně selže po
nedostupném vstupním bufferu: další vlákno nemůže získat `codec_mutex`.

Po aplikaci patche projde šest scénářů: běžný zvuk rozdělený do bufferů,
chybějící dekodér, první nedostupný buffer, chyba po částečném zpracování,
další rámec po chybě a stejná chyba během inicializace pod rekurzivním zámkem.
Test kompiluje skutečný `audio-decoder.c` a upstream pthread kód. MediaCodec
nahrazuje řízenou testovací implementací; ověřuje také dokončení audio vlákna
přes EOS a uvolnění dekodéru. Desetisekundový limit procesu je pouze pojistka
testu, nikoli nový aplikační timeout.

Dva další scénáře kompilují skutečný `audio-output.cpp` s testovacím Oboe:
opakované nastavení nesmí otevřít ani spustit zařízení a neprázdné PCM rámce
nesmějí naplnit skutečnou upstream přehrávací frontu. Kontroluje se také bezpečné
uvolnění výstupu. Testovací Oboe není součástí produkčního sestavení; fyzický
poslech na TCL/telefonu a zachování HDMI zvuku musí ověřit přejímka zařízení.

## Sestavení ARM knihoven

Potřeba: Python 3.9+, C kompilátor pro host test, Git, CMake 3.x, Ninja,
NDK **r21e / 21.4.7075529**, hostitelský protoc **3.20.3** a Python protobuf
**3.20.3**. NDK ani host nástroje nejsou nové závislosti aplikace. Ověřená
kombinace host nástrojů: CMake 3.27.9 a Ninja 1.11.1.3.
Skript je určený pro macOS/Linux; na Windows lze použít WSL s Linux NDK.

- [Android NDK archiv](https://developer.android.com/ndk/downloads/older_releases)
- [protoc 3.20.3](https://github.com/protocolbuffers/protobuf/releases/tag/v3.20.3)

V odděleném checkoutu Chiaki-TV:

```sh
git checkout f814cf69d636f149ad5a3cfffd19cb22da90beda
git submodule update --init android/app/src/main/cpp/oboe third-party/gf-complete third-party/jerasure third-party/nanopb
```

V odděleném Python prostředí lze nainstalovat host nástroje:

```sh
python -m pip install cmake==3.27.9 ninja==1.11.1.3 protobuf==3.20.3
```

Přidej protoc 3.20.3 včetně jeho `include` adresáře. `protoc --version` musí
ukázat `libprotoc 3.20.3`. Z kořene PlayCaption spusť:

```sh
python native/build_chiaki.py /cesta/Chiaki-TV /cesta/android-ndk-r21e /cesta/mimo-repozitare/build
```

Skript ověří commit a čistotu upstream kódu, aplikuje oba audio patche a spustí
nativní regrese. Sestaví ARM64 i ARMv7 s Android API 21 a kontroluje stejné
exportované JNI názvy a systémové závislosti proti knihovnám aplikace.
Použije LLVM archiver z NDK; jeho starší GNU archiver na novém macOS padá.

Výsledek je v `build/jniLibs/<ABI>`; `build/manifest.json` obsahuje revize
a SHA-256 knihoven i obou patchů. Kopírování do `app/src/main/jniLibs` je záměrně
samostatný krok po kontrole. Pro telefon vzniká `libchiaki-pc.so` dosavadním
přejmenováním jediné závislosti `libmediandk.so` na `libpcobraz1.so`.
Mezivrstva `libpcobraz1.so` se nemění. x86 knihovny se nepřestavují;
aplikace aktuálně balí pouze obě ARM ABI.

Po výměně knihoven spustit `:app:testDebugUnitTest :app:assembleDebug
:app:assembleDebugAndroidTest` a přejímku TCL + telefonu. Při instalaci
zachovat podpis příslušného zařízení, data a spárování. PR #15 zůstává draft;
žádný release, tag ani změna veřejné verze není součástí této opravy.
