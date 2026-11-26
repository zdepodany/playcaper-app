import AppKit
import PlayCaperCore
import SwiftUI

/// Obsah okénka z menu baru.
struct MenuView: View {
    @ObservedObject var rizeni: Rizeni
    @Environment(\.openSettings) private var otevriNastaveni

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("PlayCaper").font(.headline)
                Spacer()
                Toggle("Číst titulky", isOn: $rizeni.zapnuto).toggleStyle(.switch).labelsHidden()
            }
            StavRadek(rizeni: rizeni)

            Divider()

            Picker("Televize", selection: $rizeni.vybranaTvId) {
                Text("Žádná").tag("")
                ForEach(rizeni.viditelneTelevize) { tv in
                    Text("\(tv.jmeno) (\(tv.ip))").tag(tv.id)
                }
            }
            if rizeni.viditelneTelevize.isEmpty {
                Text("Hledám přijímač na síti… (na TV musí běžet přijímač PlayCaption)")
                    .font(.caption).foregroundStyle(.secondary)
            }
            if let chyba = rizeni.chybaSite {
                Text("Odesílání na TV: \(chyba)").font(.caption).foregroundStyle(.red)
            }
            Toggle("Vzhled titulků GTA VI", isOn: $rizeni.gta)

            Divider()

            VStack(alignment: .leading, spacing: 4) {
                Text(rizeni.posledniEn.isEmpty ? "—" : rizeni.posledniEn)
                    .font(.caption).foregroundStyle(.secondary).lineLimit(3)
                Text(rizeni.posledniCz.isEmpty ? "Zatím žádný titulek" : rizeni.posledniCz)
                    .font(.body.weight(.medium)).lineLimit(4)
                if !rizeni.posledniInfo.isEmpty {
                    Text(rizeni.posledniInfo).font(.caption2).foregroundStyle(.tertiary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .textSelection(.enabled)

            Divider()

            HStack {
                Button("Nastavení…") {
                    NSApp.activate(ignoringOtherApps: true)
                    otevriNastaveni()
                }
                Button("Záznam") { NSWorkspace.shared.open(Zaznam.soubor) }
                Spacer()
                Button("Ukončit") { NSApp.terminate(nil) }
            }
        }
        .padding(14)
        .frame(width: 340)
    }
}

struct StavRadek: View {
    @ObservedObject var rizeni: Rizeni

    var body: some View {
        let (text, barva) = popis
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Circle().fill(barva).frame(width: 8, height: 8)
                Text(text).font(.callout)
            }
            if rizeni.stav == .chybiOpravneni {
                Button("Otevřít Soukromí → Nahrávání obrazovky") {
                    NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture")!)
                }
                Text("Po povolení PlayCaper ukonči a spusť znovu.").font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private var popis: (String, Color) {
        switch rizeni.stav {
        case .vypnuto: return ("Vypnuto", .gray)
        case .chybiOpravneni: return ("Chybí oprávnění k nahrávání obrazovky", .red)
        case .hledamOkno: return ("Hledám okno PS Remote Play…", .orange)
        case .cte: return ("Čtu titulky (OCR \(Int(rizeni.ocrMs)) ms)", .green)
        case .bezSnimku: return ("Okno Remote Play nedodává obraz – není skryté nebo minimalizované?", .orange)
        case .chyba(let s): return ("Chyba snímání: \(s)", .red)
        }
    }
}

struct NastaveniView: View {
    @ObservedObject var rizeni: Rizeni
    @State private var klice = Klicenka.nactiKlice().joined(separator: "\n")
    @State private var ulozeno = false
    @State private var x = 0.0
    @State private var y = 0.0
    @State private var s = 0.0
    @State private var v = 0.0

    var body: some View {
        Form {
            Section("DeepL") {
                TextEditor(text: $klice)
                    .font(.system(.body, design: .monospaced))
                    .frame(height: 70)
                Text("Jeden klíč na řádek (Free klíče končí „:fx“). Ukládají se do Klíčenky. Bez klíče se překládá přes Google.")
                    .font(.caption).foregroundStyle(.secondary)
                HStack {
                    Button("Uložit klíče") {
                        rizeni.nastavKlice(Klicenka.rozdel(klice))
                        ulozeno = true
                    }
                    if ulozeno { Text("Uloženo (\(rizeni.deeplKlicu))").font(.caption).foregroundStyle(.green) }
                }
            }
            Section("Oblast čtení (zlomky obrazu hry, počátek vlevo nahoře)") {
                HStack {
                    pole("x", $x); pole("y", $y); pole("šířka", $s); pole("výška", $v)
                }
                HStack {
                    Button("Použít") { rizeni.oblast = CGRect(x: x, y: y, width: s, height: v) }
                    Button("Výchozí GTA VI") { rizeni.oblast = Rizeni.oblastGta; nactiOblast() }
                    Spacer()
                    Button("Ukázat poslední výřez") { rizeni.ulozVyrez() }
                }
            }
        }
        .formStyle(.grouped)
        .frame(width: 480)
        .onAppear(perform: nactiOblast)
    }

    private func pole(_ popisek: String, _ hodnota: Binding<Double>) -> some View {
        TextField(popisek, value: hodnota, format: .number.precision(.fractionLength(2)))
            .frame(width: 70)
    }

    private func nactiOblast() {
        let o = rizeni.oblast
        x = o.minX; y = o.minY; s = o.width; v = o.height
    }
}
