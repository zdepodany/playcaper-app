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
  na GTA VI; změnit jde v Nastavení, „Ukázat poslední výřez“ ukáže, co se čte.
- Podpis certifikátem Apple Development je nutný kvůli oprávnění: s ad-hoc
  podpisem se macOS po každém buildu ptá znovu.
