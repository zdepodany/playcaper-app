# Prototyp Mac verze (PC-009)

Jen měření: ScreenCaptureKit snímá okno PS Remote Play, Vision čte titulky.
Není to základ aplikace. Výsledky jsou zapsané v `BACKLOG.md` u PC-009.

```sh
cd macos/Prototyp
swift build
.build/debug/Prototyp okna                 # okna, ▶ = Remote Play
.build/debug/Prototyp snimek               # vystup/okno.png + vystup/oblast.png
.build/debug/Prototyp cti --sekund 30      # průběžné čtení, časy, vzorky do vystup/
.build/debug/Prototyp ocr vystup/oblast.png
```

Oblast čtení: `--oblast x,y,š,v` jako zlomky okna (počátek vlevo nahoře, okno
včetně titulku okna). Výchozí `0.10,0.72,0.80,0.24` sedí na titulky GTA VI.

Potřebuje oprávnění Nahrávání obrazovky pro terminál, ze kterého se spouští.
