# PlayCaper pro Mac

Appka v menu baru: čte anglické titulky z okna **PS Remote Play**, překládá je
do češtiny a posílá je přijímači PlayCaption na TV (LG/Samsung, stejný protokol
jako Android). Rozhodnutí a měření jsou v `BACKLOG.md` u PC-009.

## Použití

1. PS Remote Play přihlásit **druhým účtem PSN** a připojit k PS5 (hlavním účtem
   se hraje na TV; stejný účet by odpojil ovladač).
2. Spustit PlayCaper, povolit **Nahrávání obrazovky** (Nastavení systému →
   Soukromí a zabezpečení) a appku restartovat.
3. V menu baru (ikona bubliny s titulky) vybrat televizi. Na TV musí běžet přijímač.

Okno Remote Play může ležet **za ostatními okny**, ale nesmí být skryté (Cmd+H),
minimalizované ani na zhasnutém displeji (PlayCaper zhasnutí sám brání).
Se zavřeným víkem bez externího monitoru to nefunguje.

**Samsung přijímač** jde nainstalovat nebo aktualizovat přímo z appky: Nastavení →
„Nainstalovat nebo aktualizovat Samsung přijímač…“ (v menu se nabídne samo, když
vybraná TV hlásí starší verzi). Appka podepíše přibalený WGT aktivním profilem
z Certificate Manageru, připojí se `sdb`, nainstaluje, spustí přijímač a počká,
až se TV ohlásí s novou verzí. Potřebuje jednorázově připravené Tizen Studio
v `~/tizen-studio`, certifikát a Developer Mode na TV (`tizen/NAVOD-SAMSUNG.md`,
krok 3b). `nastroje/sestav-app.sh` přijímač sestaví (`tizen/build.py`) a přibalí.

DeepL klíče se zadávají v Nastavení (ukládají se do Klíčenky), bez nich se
překládá přes Google. Záznam: `~/Library/Logs/PlayCaper/playcaper.log`.

## Vývoj

```sh
cd macos/PlayCaper
swift test                          # testy jádra (PlayCaperCore)
nastroje/sestav-app.sh              # dist/PlayCaper.app, podepsaná Apple Development
open dist/PlayCaper.app

node nastroje/falesna-tv.cjs        # falešná TV: ohlásí se a vypisuje přijaté titulky
                                    # (parsuje je skutečný webos/service/protocol.js)
.build/debug/PlayCaper --vzorky <složka s PNG>   # OCR + výběr titulku nad uloženými výřezy
```

- `Sources/PlayCaperCore`: logika bez UI. `Protokol` (musí sedět s `Propojeni.kt`
  a `webos/service/protocol.js`), `StabilitaTextu` / `TextVRamecku` (přepis
  z Androidu, změny dělat na obou stranách), `VyberTitulku`, `Prekladac`.
- `Sources/PlayCaper`: `Snimac` (ScreenCaptureKit + Vision), `Sit` (UDP),
  `Rizeni` (OCR → překlad → TV, časování jako `RemotePlayService`), UI.
- Oblast čtení je ve zlomcích obrazu hry (bez titulku okna, 16:9). Výchozí sedí
  na GTA VI. Mění se v okně **Oblast…** (z menu nebo z Nastavení): rámeček se
  táhne po ploše 16:9, která představuje obraz hry, a při tažení se stejný modrý
  rámeček ukazuje na TV nad hrou (`PCO1`, jako na Androidu). Zelené rámečky jsou
  řádky z OCR vzaté jako titulek, oranžové zahozené. Živý náhled okna Remote
  Play byl zkusmo přidaný a zase odebraný (nefungoval spolehlivě). Geometrie tažení je v `PlayCaperCore/OblastCteni.swift`.
- Podpis certifikátem Apple Development je nutný kvůli oprávnění: s ad-hoc
  podpisem se macOS po každém buildu ptá znovu.
