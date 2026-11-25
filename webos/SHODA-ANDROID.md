# Shoda funkcí televizního přijímače

LG 0.2.1 porovnáváme s Android režimem **titulky z telefonu/tabletu** a protokolem
v `Propojeni.kt`. Remote Play, OCR, překlad a účet konzole zajišťuje Android.
Matice odděluje implementované vykreslení od přejímky konkrétní herní scény.

| Funkce Android přijímače | LG 0.2.1 | Ověření |
| --- | --- | --- |
| `PCT1`: české titulky a více řádků | Ano | Parser/UI, skutečné UDP a dřívější hraní na C5. |
| Poloha, šířka a velikost podle zelené oblasti | Ano | Automatické testy a dosavadní běh. |
| Zelený náhled při nastavování polohy | Přerušovaný obrys textu | UDP → UI C5. |
| `B:`: barvy jmen mluvčích | Barví jméno včetně dvojtečky | Test signed ARGB, zelená/červená jména na C5. |
| Samostatná žlutá nápověda | Limit 20 s stejného textu | Automatické testy; závisí na obsahu vysílaném Androidem. |
| `PCV1`: odpovědi | Samostatný český seznam | Parser/UI, loopback UDP, testovací seznam na C5. |
| Zvýrazněná odpověď | Černý text na žlutém podkladu | Automatický test a C5. |
| Překlad vedle oblasti čtení | Strana s větším místem; při nedostatku místa uvnitř oblasti | Test výpočtu a souřadnice na C5. |
| Žlutý rámeček při přesouvání | Pojistka 8 s | Automatické testy a uživatel na C5. |
| Žlutý rámeček „Ukazovat pořád“ | Režim 2 | Test trvání, UDP a potvrzení uživatele. Výpadek jej skryje. |
| `PCV1`, vybraná `-2`: Text | Celý blok, limit 5 min stejného obsahu | Celý syntetický odstavec na C5 bez oříznutí. |
| `PCO1`: modrý rámeček | Souřadnice, skrytí, pojistka 8 s | UDP → C5 a potvrzení pohybu z tabletu. |
| Heartbeat neobnovuje prošlý text | Oddělené čítače titulků a voleb | Test včetně shodných ID obou kanálů. |
| Výpadek odesílajícího zařízení po 5 s | Skrytí textů i všech rámečků | Test celé UI cesty. |
| Nabídka, zavření a znovuspuštění | OK/Zpět otevře nabídku; Ukončit zavře; spuštění obnoví odběr | TV a regresní testy životního cyklu. |

Integrovaný Příběhový režim používá Android frontu a LG stránky s potvrzením
`PCA1`. Výchozí časování je 100 %, rozsah 50–200 %. Skrytí, nabídka a lokální test
pozastaví dočtení Story; Standardní časování se nemění. Automatické důkazy:
`test-story.cjs`, `test-controls.cjs`; nová herní přejímka integrované verze zbývá.

## Význam oblastí

- Zelená: kde se zobrazují české dialogové titulky.
- Modrá: odkud Android čte původní titulky. LG ji ukazuje při nastavování.
- Žlutá: odkud Android čte odpovědi nebo dokument. Český výsledek se podle
  Android přijímače umísťuje vedle, aby zůstal viditelný originál.

Viditelnost rámečku je samostatná volba, neslouží jako vypínač OCR. Prázdný seznam
skryje české odpovědi, trvale zapnutý rámeček zůstane. Styl a velikost písma nejsou
pixelovou kopií Androidu. LG dialogy stránkuje po dvou řádcích s limitem výšky
18 % obrazovky; Android přijímač tuto novou prezentační politiku zatím nemá.

## Odlišnosti zapojení a platformy

- Přihlašovací `PCS1` z původního protokolu přenáší přihlášení z TV do telefonu. LG údaje konzole neuchovává,
  proto přenos neposkytuje a discovery hlásí nespárovanou konzoli. Účet i párování
  se nastavují na Android zařízení; LG netvrdí, že má údaje, které může předat.
- Vlastní Remote Play a OCR přímo v televizi není součástí LG přijímače.
  Přímé čtení HDMI na LG bez rootu nemáme potvrzené.
- HDMI se vybírá uvnitř PlayCaption. Systémová změna aplikace/vstupu může překryv
  zavřít; sledování popředí přes `getForegroundAppInfo` testovaná TV odmítla.
- Potvrzená sestava: C5 + Redmi Pad Pro + PS5. Další LG modely, Xbox, dlouhý běh
  a všechny HDR/VRR kombinace vyžadují další přejímku.

## Zbývající herní přejímka

1. Skutečné dialogové volby v nastavené oblasti: správné OCR a český překlad.
2. Přepínání vybrané odpovědi a odpovídající zvýraznění na LG.
3. Celý skutečný dokument v režimu Text, včetně úplnosti překladu.
4. Oblasti u obou okrajů a dlouhý obsah při běžné velikosti písma.

Protokol neměníme. Nové funkce vysílače patří současně do této matice, parseru
a testů. Úspěšný syntetický paket není důkaz správnosti OCR herního obrazu.
