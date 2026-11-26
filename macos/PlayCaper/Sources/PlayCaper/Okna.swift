import AppKit
import PlayCaperCore
import SwiftUI

// MARK: - Hlavní okno

/// Hlavní okno ve stylu Nastavení systému: stav čtení s přepínačem, poslední titulek, televize
/// a čtení/překlad. Otevře se při spuštění a po kliknutí na ikonu v Docku.
struct HlavniOkno: View {
    @ObservedObject var rizeni: Rizeni
    @Environment(\.openSettings) private var otevriNastaveni
    @Environment(\.openWindow) private var otevriOkno

    var body: some View {
        Form {
            Section {
                StavKarta(rizeni: rizeni)
            }

            Section("Poslední titulek") {
                PosledniTitulek(rizeni: rizeni, velky: true)
            }

            Section("Televize") {
                VyberTelevize(rizeni: rizeni)
            }

            Section("Čtení a překlad") {
                Toggle(isOn: $rizeni.gta) {
                    Text("Vzhled titulků GTA VI")
                    Text("Na TV ukáže titulky stylem hry, u voleb i ikony tlačítek.")
                }
                LabeledContent {
                    Button("Upravit…") { otevri("oblast") }
                } label: {
                    Text("Oblast čtení")
                    Text(rizeni.oblast == Rizeni.oblastGta ? "Výchozí pro GTA VI" : "Vlastní")
                }
                LabeledContent {
                    Button("Nastavit…") {
                        NSApp.activate(ignoringOtherApps: true)
                        otevriNastaveni()
                    }
                } label: {
                    Text("Překladač")
                    Text(rizeni.deeplKlicu > 0 ? "DeepL (klíčů: \(rizeni.deeplKlicu))" : "Google (bez klíče DeepL)")
                }
            }
        }
        .formStyle(.grouped)
        .frame(minWidth: 440, idealWidth: 480, minHeight: 650)
    }

    private func otevri(_ id: String) {
        NSApp.activate(ignoringOtherApps: true)
        otevriOkno(id: id)
    }
}

/// Velká karta nahoře: ikona stavu, popis a hlavní přepínač čtení.
struct StavKarta: View {
    @ObservedObject var rizeni: Rizeni

    var body: some View {
        let p = PopisStavu(rizeni.stav, ocrMs: rizeni.ocrMs)
        HStack(spacing: 12) {
            Image(systemName: p.ikona)
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .background(p.barva.gradient, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text("Čtení titulků").font(.headline)
                Text(p.text).font(.subheadline).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            Toggle("Číst titulky", isOn: $rizeni.zapnuto)
                .toggleStyle(.switch)
                .labelsHidden()
                .controlSize(.large)
        }
        .padding(.vertical, 4)

        if rizeni.stav == .chybiOpravneni {
            OpravneniRada()
        }
    }
}

struct OpravneniRada: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("PlayCaper potřebuje povolené Nahrávání obrazovky. Po povolení ho ukonči a spusť znovu.")
                .font(.callout).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            Button("Otevřít Soukromí a zabezpečení…") {
                NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture")!)
            }
        }
    }
}

/// Text, ikona a barva pro stav snímání (sdílí hlavní okno i menu bar).
struct PopisStavu {
    let text: String
    let ikona: String
    let barva: Color

    init(_ stav: Snimac.Stav, ocrMs: Double) {
        switch stav {
        case .vypnuto:
            (text, ikona, barva) = ("Vypnuto", "captions.bubble", .gray)
        case .chybiOpravneni:
            (text, ikona, barva) = ("Chybí oprávnění k nahrávání obrazovky", "lock.fill", .red)
        case .hledamOkno:
            (text, ikona, barva) = ("Hledám okno PS Remote Play…", "magnifyingglass", .orange)
        case .cte:
            (text, ikona, barva) = ("Čtu titulky · OCR \(Int(ocrMs)) ms", "captions.bubble.fill", .green)
        case .bezSnimku:
            (text, ikona, barva) = ("Okno Remote Play nedodává obraz. Není skryté nebo minimalizované?", "eye.slash.fill", .orange)
        case .chyba(let s):
            (text, ikona, barva) = ("Chyba snímání: \(s)", "exclamationmark.triangle.fill", .red)
        }
    }
}

struct PosledniTitulek: View {
    @ObservedObject var rizeni: Rizeni
    var velky = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if rizeni.posledniCz.isEmpty {
                Text("Zatím žádný titulek")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: velky ? .center : .leading)
                    .padding(.vertical, velky ? 14 : 0)
            } else {
                Text(rizeni.posledniCz)
                    .font(velky ? .title3.weight(.medium) : .body.weight(.medium))
                    .lineLimit(velky ? 5 : 4)
                if !rizeni.posledniEn.isEmpty {
                    Text(rizeni.posledniEn)
                        .font(velky ? .callout : .caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                }
                if !rizeni.posledniInfo.isEmpty {
                    Text(rizeni.posledniInfo).font(.caption).foregroundStyle(.tertiary)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .textSelection(.enabled)
    }
}

/// Výběr televize s jejím stavem (hledání, starší přijímač, chyba odesílání).
struct VyberTelevize: View {
    @ObservedObject var rizeni: Rizeni
    @Environment(\.openWindow) private var otevriOkno

    var body: some View {
        Picker("Televize", selection: $rizeni.vybranaTvId) {
            Text("Žádná").tag("")
            ForEach(rizeni.viditelneTelevize) { tv in
                Text(tv.jmeno).tag(tv.id)
            }
        }

        if let tv = rizeni.viditelneTelevize.first(where: { $0.id == rizeni.vybranaTvId }) {
            LabeledContent("Adresa", value: tv.ip)
            if let v = tv.verze { LabeledContent("Verze přijímače", value: v) }
        }

        if rizeni.viditelneTelevize.isEmpty {
            HStack(spacing: 8) {
                ProgressView().controlSize(.small)
                Text("Hledám přijímač na síti… Na TV musí běžet přijímač PlayCaption.")
                    .font(.callout).foregroundStyle(.secondary)
            }
        }

        if rizeni.samsungKAktualizaci {
            LabeledContent {
                Button("Aktualizovat…") { otevri() }
            } label: {
                Label("Na TV je starší přijímač", systemImage: "arrow.down.circle.fill")
                    .foregroundStyle(.orange)
            }
        }

        if let chyba = rizeni.chybaSite {
            Label("Odesílání na TV: \(chyba)", systemImage: "wifi.exclamationmark")
                .foregroundStyle(.red)
        }
    }

    private func otevri() {
        NSApp.activate(ignoringOtherApps: true)
        otevriOkno(id: "prijimac")
    }
}

// MARK: - Menu bar

/// Kompaktní okénko z menu baru: přepínač, televize, poslední titulek.
struct MenuView: View {
    @ObservedObject var rizeni: Rizeni
    @Environment(\.openWindow) private var otevriOkno

    var body: some View {
        let p = PopisStavu(rizeni.stav, ocrMs: rizeni.ocrMs)
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: p.ikona)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 26, height: 26)
                    .background(p.barva.gradient, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text("PlayCaper").font(.headline)
                    Text(p.text).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
                Spacer()
                Toggle("Číst titulky", isOn: $rizeni.zapnuto).toggleStyle(.switch).labelsHidden()
            }
            if rizeni.stav == .chybiOpravneni { OpravneniRada() }

            Divider()

            Picker("Televize", selection: $rizeni.vybranaTvId) {
                Text("Žádná").tag("")
                ForEach(rizeni.viditelneTelevize) { tv in Text(tv.jmeno).tag(tv.id) }
            }
            if let chyba = rizeni.chybaSite {
                Text("Odesílání na TV: \(chyba)").font(.caption).foregroundStyle(.red)
            }

            Divider()

            PosledniTitulek(rizeni: rizeni)

            Divider()

            HStack {
                Button("Otevřít PlayCaper") {
                    NSApp.activate(ignoringOtherApps: true)
                    otevriOkno(id: "hlavni")
                }
                Spacer()
                Button("Ukončit") { NSApp.terminate(nil) }
            }
        }
        .padding(14)
        .frame(width: 320)
    }
}

// MARK: - Menu Titulky

/// Položky menu „Titulky“ v liště aplikace (se zkratkami).
struct PrikazyTitulku: Commands {
    @ObservedObject var rizeni: Rizeni

    var body: some Commands {
        CommandGroup(replacing: .newItem) {}
        CommandMenu("Titulky") {
            Toggle("Číst titulky", isOn: $rizeni.zapnuto)
                .keyboardShortcut("r")
            Toggle("Vzhled titulků GTA VI", isOn: $rizeni.gta)
            Divider()
            OtevriOknoTlacitko(nazev: "Oblast čtení…", id: "oblast")
                .keyboardShortcut("o", modifiers: [.command, .shift])
            OtevriOknoTlacitko(nazev: "Přijímač na TV…", id: "prijimac")
                .keyboardShortcut("t", modifiers: [.command, .shift])
            Divider()
            Button("Ukázat poslední výřez") { rizeni.ulozVyrez() }
            Button("Otevřít záznam") { NSWorkspace.shared.open(Zaznam.soubor) }
                .keyboardShortcut("l", modifiers: [.command, .shift])
        }
    }
}

private struct OtevriOknoTlacitko: View {
    let nazev: String
    let id: String
    @Environment(\.openWindow) private var otevriOkno

    var body: some View {
        Button(nazev) {
            NSApp.activate(ignoringOtherApps: true)
            otevriOkno(id: id)
        }
    }
}

// MARK: - Nastavení

/// Nastavení (Cmd+,) rozdělené do panelů jako v systémových appkách.
struct NastaveniView: View {
    @ObservedObject var rizeni: Rizeni

    var body: some View {
        TabView {
            Tab("Překlad", systemImage: "character.bubble") {
                NastaveniPrekladu(rizeni: rizeni)
            }
            Tab("Oblast čtení", systemImage: "viewfinder") {
                NastaveniOblasti(rizeni: rizeni)
            }
            Tab("Přijímač", systemImage: "tv") {
                NastaveniPrijimace()
            }
            Tab("Pokročilé", systemImage: "gearshape.2") {
                NastaveniPokrocile(rizeni: rizeni)
            }
        }
        .frame(width: 500)
    }
}

private struct NastaveniPrekladu: View {
    @ObservedObject var rizeni: Rizeni
    @State private var klice = Klicenka.nactiKlice().joined(separator: "\n")
    @State private var ulozeno = false

    var body: some View {
        Form {
            Section {
                TextEditor(text: $klice)
                    .font(.system(.body, design: .monospaced))
                    .frame(height: 80)
                    .onChange(of: klice) { ulozeno = false }
                HStack {
                    if ulozeno {
                        Label("Uloženo (klíčů: \(rizeni.deeplKlicu))", systemImage: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                    }
                    Spacer()
                    Button("Uložit klíče") {
                        rizeni.nastavKlice(Klicenka.rozdel(klice))
                        ulozeno = true
                    }
                    .keyboardShortcut(.defaultAction)
                }
            } header: {
                Text("Klíče DeepL")
            } footer: {
                Text("Jeden klíč na řádek, klíče Free končí „:fx“. Ukládají se do Klíčenky. Bez klíče se překládá přes Google.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .scrollDisabled(true)
        .fixedSize(horizontal: false, vertical: true)
    }
}

private struct NastaveniOblasti: View {
    @ObservedObject var rizeni: Rizeni
    @Environment(\.openWindow) private var otevriOkno
    @State private var x = 0.0
    @State private var y = 0.0
    @State private var s = 0.0
    @State private var v = 0.0

    var body: some View {
        Form {
            Section {
                LabeledContent("Nastavit tažením") {
                    Button("Otevřít editor oblasti…") {
                        NSApp.activate(ignoringOtherApps: true)
                        otevriOkno(id: "oblast")
                    }
                }
            } footer: {
                Text("V editoru se rámeček táhne myší a zároveň se ukazuje na TV nad hrou.")
                    .font(.caption).foregroundStyle(.secondary)
            }

            Section {
                pole("Vlevo (x)", $x)
                pole("Nahoře (y)", $y)
                pole("Šířka", $s)
                pole("Výška", $v)
                HStack {
                    Button("Výchozí GTA VI") { rizeni.oblast = Rizeni.oblastGta; nactiOblast() }
                    Spacer()
                    Button("Použít") { rizeni.oblast = OblastCteni.omez(CGRect(x: x, y: y, width: s, height: v)) }
                }
            } header: {
                Text("Přesné hodnoty")
            } footer: {
                Text("Zlomky obrazu hry (0 až 1), počátek vlevo nahoře.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .scrollDisabled(true)
        .fixedSize(horizontal: false, vertical: true)
        .onAppear(perform: nactiOblast)
        .onChange(of: rizeni.oblast) { nactiOblast() }
    }

    private func pole(_ popisek: String, _ hodnota: Binding<Double>) -> some View {
        TextField(popisek, value: hodnota, format: .number.precision(.fractionLength(2)))
            .multilineTextAlignment(.trailing)
    }

    private func nactiOblast() {
        let o = rizeni.oblast
        x = o.minX; y = o.minY; s = o.width; v = o.height
    }
}

private struct NastaveniPrijimace: View {
    @Environment(\.openWindow) private var otevriOkno

    var body: some View {
        Form {
            Section {
                LabeledContent {
                    Button("Otevřít…") {
                        NSApp.activate(ignoringOtherApps: true)
                        otevriOkno(id: "prijimac")
                    }
                } label: {
                    Text("Samsung přijímač")
                    Text(InstalatorSamsung.balicek.map { "V appce je verze \($0.0.verze)" } ?? "Není přibalený")
                }
            } footer: {
                Text("Instalace a aktualizace přes Tizen Studio v ~/tizen-studio (tizen/NAVOD-SAMSUNG.md, krok 3b). LG přijímač se instaluje z Androidu.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .scrollDisabled(true)
        .fixedSize(horizontal: false, vertical: true)
    }
}

private struct NastaveniPokrocile: View {
    @ObservedObject var rizeni: Rizeni

    var body: some View {
        Form {
            Section {
                LabeledContent {
                    Button("Ukázat") { rizeni.ulozVyrez() }
                } label: {
                    Text("Poslední výřez")
                    Text("Obrázek, který právě prošel OCR.")
                }
                LabeledContent {
                    Button("Otevřít") { NSWorkspace.shared.open(Zaznam.soubor) }
                } label: {
                    Text("Záznam")
                    Text("~/Library/Logs/PlayCaper/playcaper.log")
                }
            } header: {
                Text("Ladění")
            }
        }
        .formStyle(.grouped)
        .scrollDisabled(true)
        .fixedSize(horizontal: false, vertical: true)
    }
}
