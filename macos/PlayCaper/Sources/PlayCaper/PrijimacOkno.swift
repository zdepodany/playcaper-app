import AppKit
import PlayCaperCore
import SwiftUI

/// Instalace a aktualizace Samsung přijímače z Macu přes Tizen CLI (`tizen/NAVOD-SAMSUNG.md`, krok 3b):
/// podepsat přibalený WGT aktivním profilem certifikátu → `sdb connect` → `tizen install` → `tizen run`
/// → počkat, až se přijímač ohlásí s novou verzí. Hotovo je teprve to poslední.
@MainActor
final class InstalatorSamsung: ObservableObject {
    enum StavKroku { case ceka, bezi, hotovo, chyba }

    struct Krok: Identifiable {
        let id: Int
        let nazev: String
        var stav: StavKroku = .ceka
        var poznamka = ""
    }

    static let nazvyKroku = ["Podepsat certifikátem", "Připojit k TV", "Nainstalovat",
                             "Spustit přijímač na TV", "Ověřit, že se ohlásí nová verze"]

    @Published private(set) var kroky: [Krok] = nazvyKroku.enumerated().map { Krok(id: $0.offset, nazev: $0.element) }
    @Published private(set) var bezi = false
    @Published private(set) var vystup = ""
    @Published private(set) var rada: String?
    @Published private(set) var hotovo = false
    /// Čas posledního selhání (okno pak samo rozbalí výstup příkazů).
    @Published private(set) var selhaloKdy: Date?

    static let tizenStudio = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("tizen-studio")
    static var sdb: String { tizenStudio.appendingPathComponent("tools/sdb").path }
    static var tizen: String { tizenStudio.appendingPathComponent("tools/ide/bin/tizen").path }

    /// Přijímač přibalený v appce (`nastroje/sestav-app.sh` ho kopíruje z `tizen/dist`).
    static let balicek: (SamsungPrijimac.Balicek, URL)? = {
        guard let slozka = Bundle.main.resourceURL?.appendingPathComponent("samsung"),
              let data = try? Data(contentsOf: slozka.appendingPathComponent("receiver-manifest.json")),
              let b = SamsungPrijimac.prectiManifest(data) else { return nil }
        let wgt = slozka.appendingPathComponent(b.soubor)
        return FileManager.default.fileExists(atPath: wgt.path) ? (b, wgt) : nil
    }()

    /// Co chybí k instalaci (nil = vše připravené).
    static var coChybi: String? {
        if balicek == nil { return "Přijímač není v appce přibalený. Sestav ji přes nastroje/sestav-app.sh." }
        if !FileManager.default.isExecutableFile(atPath: tizen) || !FileManager.default.isExecutableFile(atPath: sdb) {
            return "Chybí Tizen Studio v ~/tizen-studio (postup v tizen/NAVOD-SAMSUNG.md, krok 3b)."
        }
        if profil == nil { return "V Certificate Manageru není aktivní profil certifikátu Samsung." }
        return nil
    }

    static var profil: String? {
        let url = FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent("tizen-studio-data/profile/profiles.xml")
        return (try? String(contentsOf: url, encoding: .utf8)).flatMap(SamsungPrijimac.aktivniProfil)
    }

    /// `verzeNaTv` vrací verzi, kterou TV s danou IP právě ohlašuje (nil = neohlašuje).
    func instaluj(ip: String, verzeNaTv: @escaping () -> String?) {
        guard !bezi, let balicek = Self.balicek, let profil = Self.profil else { return }
        let (b, zdroj) = balicek
        bezi = true
        hotovo = false
        rada = nil
        vystup = ""
        kroky = Self.nazvyKroku.enumerated().map { Krok(id: $0.offset, nazev: $0.element) }
        Zaznam.zapis("Instalace Samsung přijímače \(b.verze) na \(ip) (profil \(profil))")

        Task {
            defer { bezi = false }
            // 1. Podpis kopie v dočasné složce (tizen package podepisuje soubor na místě).
            krok(0, .bezi)
            let slozka = FileManager.default.temporaryDirectory.appendingPathComponent("playcaper-wgt-\(UUID().uuidString)")
            try? FileManager.default.createDirectory(at: slozka, withIntermediateDirectories: true)
            defer { try? FileManager.default.removeItem(at: slozka) }
            let wgt = slozka.appendingPathComponent(b.soubor)
            do { try FileManager.default.copyItem(at: zdroj, to: wgt) } catch {
                return selhalo(0, error.localizedDescription)
            }
            let podpis = await Self.spust(Self.tizen, ["package", "-t", "wgt", "-s", profil, "--", wgt.path], limit: 120)
            let obsah = await Self.spust("/usr/bin/unzip", ["-l", wgt.path], limit: 20)
            guard podpis.kod == 0, obsah.text.contains("author-signature.xml") else {
                return selhalo(0, "Balíček se nepodepsal.")
            }
            krok(0, .hotovo, "profil \(profil)")

            // 2. Připojení: server sdb zvlášť (jinak by jeho démon držel výstup), pak connect a název TV.
            krok(1, .bezi)
            _ = await Self.spust(Self.sdb, ["start-server"], limit: 20)
            _ = await Self.spust(Self.sdb, ["connect", ip], limit: 30)
            let zarizeni = await Self.spust(Self.sdb, ["devices"], limit: 20)
            guard let nazev = SamsungPrijimac.nazevZarizeni(zarizeni.text, ip: ip) else {
                rada = SamsungPrijimac.rada("failed to connect")
                return selhalo(1, "TV \(ip) není připojená.")
            }
            krok(1, .hotovo, nazev)

            // 3. Instalace (přepíše starší verzi, nastavení zůstane).
            krok(2, .bezi)
            _ = await Self.spust(Self.sdb, ["connect", ip], limit: 30)
            var instalace = await Self.spust(Self.tizen, ["install", "-n", wgt.path, "-t", nazev], limit: 180)
            if instalace.text.lowercased().contains("no connected target") {
                _ = await Self.spust(Self.sdb, ["connect", ip], limit: 30)
                instalace = await Self.spust(Self.tizen, ["install", "-n", wgt.path, "-t", nazev], limit: 180)
            }
            let t = instalace.text.lowercased()
            guard instalace.kod == 0, t.contains("successfully") || t.contains("installed the package"),
                  !t.contains("failed") else {
                rada = SamsungPrijimac.rada(instalace.text)
                return selhalo(2, "Instalace selhala, podrobnosti níže.")
            }
            krok(2, .hotovo, "verze \(b.verze)")

            // 4. Spuštění; když se nepovede, stačí spustit ručně.
            krok(3, .bezi)
            let spusteni = await Self.spust(Self.tizen, ["run", "-p", SamsungPrijimac.idAplikace, "-t", nazev], limit: 60)
            if spusteni.kod == 0, spusteni.text.lowercased().contains("launched") {
                krok(3, .hotovo)
            } else {
                krok(3, .chyba, "spusť PlayCaption Samsung ručně v Apps na TV")
            }

            // 5. Ověření: přijímač se musí sám ohlásit s novou verzí (hlásí se každé 2 s).
            krok(4, .bezi, "čekám až 60 s")
            for _ in 0..<60 {
                if verzeNaTv() == b.verze {
                    krok(4, .hotovo, "TV hlásí verzi \(b.verze)")
                    hotovo = true
                    Zaznam.zapis("Samsung přijímač \(b.verze) nainstalován a ohlášen")
                    return
                }
                try? await Task.sleep(for: .seconds(1))
            }
            selhalo(4, "Nainstalováno, ale TV se s verzí \(b.verze) neohlásila. Spusť přijímač na TV a zkontroluj verzi v ladění (OK).")
        }
    }

    private func krok(_ i: Int, _ s: StavKroku, _ poznamka: String = "") {
        kroky[i].stav = s
        kroky[i].poznamka = poznamka
    }

    private func selhalo(_ i: Int, _ proc: String) {
        krok(i, .chyba, proc)
        Zaznam.zapis("Instalace Samsung přijímače selhala (\(kroky[i].nazev)): \(proc)")
        selhaloKdy = Date()
    }

    private func pridejVystup(_ prikaz: String, _ text: String) {
        let zaznam = "$ \(prikaz)\n\(text)\(text.hasSuffix("\n") || text.isEmpty ? "" : "\n")"
        vystup += zaznam
        // Celý výstup i do záznamu (~/Library/Logs/PlayCaper), ať jde dohledat i po zavření okna.
        Zaznam.zapis("Instalace: \(zaznam.trimmingCharacters(in: .newlines))")
    }

    /// Spustí příkaz a vrátí kód a výstup. Výstup jde do dočasného souboru, ne do roury:
    /// démon `sdb`, který příkaz může spustit, by rouru držel otevřenou navždy.
    private static func spust(_ program: String, _ argumenty: [String], limit: TimeInterval) async -> (kod: Int32, text: String) {
        let vysledek: (Int32, String) = await withCheckedContinuation { c in
            DispatchQueue.global().async {
                let soubor = FileManager.default.temporaryDirectory.appendingPathComponent("playcaper-\(UUID().uuidString).log")
                FileManager.default.createFile(atPath: soubor.path, contents: nil)
                defer { try? FileManager.default.removeItem(at: soubor) }
                guard let h = try? FileHandle(forWritingTo: soubor) else { return c.resume(returning: (-1, "nejde vytvořit záznam")) }
                let p = Process()
                p.executableURL = URL(fileURLWithPath: program)
                p.arguments = argumenty
                p.standardOutput = h
                p.standardError = h
                p.standardInput = FileHandle.nullDevice
                do { try p.run() } catch {
                    try? h.close()
                    return c.resume(returning: (-1, error.localizedDescription))
                }
                let hlidac = DispatchWorkItem { if p.isRunning { p.terminate() } }
                DispatchQueue.global().asyncAfter(deadline: .now() + limit, execute: hlidac)
                p.waitUntilExit()
                hlidac.cancel()
                try? h.close()
                let text = (try? String(contentsOf: soubor, encoding: .utf8)) ?? ""
                c.resume(returning: (p.terminationStatus, text))
            }
        }
        let jmeno = (program as NSString).lastPathComponent
        await MainActor.run { aktualni?.pridejVystup(([jmeno] + argumenty).joined(separator: " "), vysledek.1) }
        return (vysledek.0, vysledek.1)
    }

    /// Instance, do které se zapisuje výstup příkazů (je jen jedna, okno Přijímač na TV).
    private static weak var aktualni: InstalatorSamsung?
    init() { Self.aktualni = self }
}

/// Okno „Přijímač na TV“: verze v appce vs. na TV a tlačítko Nainstalovat / Aktualizovat.
struct PrijimacOkno: View {
    @ObservedObject var rizeni: Rizeni
    @StateObject private var instalator = InstalatorSamsung()
    @AppStorage("samsungIp") private var ip = ""
    @State private var podrobnosti = false

    var body: some View {
        Form {
            Section {
                HStack(spacing: 12) {
                    Image(systemName: "tv")
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 40, height: 40)
                        .background(Color.blue.gradient, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Samsung přijímač").font(.headline)
                        Text(popisVerzi).font(.subheadline).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(.vertical, 4)
                if let chybi = InstalatorSamsung.coChybi {
                    Label(chybi, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                }
            }

            Section {
                if !samsungy.isEmpty {
                    Picker("Televize", selection: $ip) {
                        ForEach(samsungy) { tv in Text("\(tv.jmeno) (\(tv.ip))").tag(tv.ip) }
                        if !ip.isEmpty, !samsungy.contains(where: { $0.ip == ip }) { Text(ip).tag(ip) }
                    }
                }
                TextField("IP adresa TV", text: $ip, prompt: Text("např. 192.168.1.50"))
            } header: {
                Text("Televize")
            } footer: {
                Text("Když přijímač na TV neběží, TV se neohlašuje; pak zadej její IP ručně.")
                    .font(.caption).foregroundStyle(.secondary)
            }

            Section("Postup") {
                ForEach(instalator.kroky) { k in
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        ikona(k.stav).frame(width: 16)
                        Text(k.nazev).foregroundStyle(k.stav == .ceka ? .secondary : .primary)
                        Spacer()
                        if !k.poznamka.isEmpty {
                            Text(k.poznamka).font(.caption).foregroundStyle(k.stav == .chyba ? .red : .secondary)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                }
                if instalator.hotovo {
                    Label("Hotovo, na TV běží nový přijímač.", systemImage: "checkmark.seal.fill").foregroundStyle(.green)
                }
                if let rada = instalator.rada {
                    Label(rada, systemImage: "lightbulb.fill").foregroundStyle(.orange)
                        .fixedSize(horizontal: false, vertical: true)
                }
                HStack {
                    if instalator.bezi { ProgressView().controlSize(.small) }
                    Spacer()
                    Button(nazevTlacitka) {
                        let cil = ip.trimmingCharacters(in: .whitespaces)
                        let r = rizeni // žije po celou dobu běhu appky
                        instalator.instaluj(ip: cil) { r.viditelneTelevize.first(where: { $0.ip == cil })?.verze }
                    }
                    .keyboardShortcut(.defaultAction)
                    .disabled(instalator.bezi || ip.trimmingCharacters(in: .whitespaces).isEmpty || InstalatorSamsung.coChybi != nil)
                }
            }

            Section {
                DisclosureGroup("Výstup příkazů", isExpanded: $podrobnosti) {
                    ScrollView {
                        Text(instalator.vystup.isEmpty ? "Zatím nic." : instalator.vystup)
                            .font(.system(.caption, design: .monospaced))
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(6)
                    }
                    .frame(height: 200)
                    .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 6))
                    HStack {
                        Button("Zkopírovat výstup") {
                            NSPasteboard.general.clearContents()
                            NSPasteboard.general.setString(instalator.vystup, forType: .string)
                        }
                        .disabled(instalator.vystup.isEmpty)
                        Button("Otevřít záznam") { NSWorkspace.shared.open(Zaznam.soubor) }
                    }
                }
            }
        }
        .formStyle(.grouped)
        .frame(minWidth: 480, idealWidth: 520, minHeight: 520)
        .onAppear {
            if ip.isEmpty, let tv = samsungy.first { ip = tv.ip }
        }
        .onChange(of: instalator.selhaloKdy) { podrobnosti = true }
    }

    private var samsungy: [Protokol.Televize] {
        rizeni.viditelneTelevize.filter { $0.id.hasPrefix("samsung-") }
    }

    private var stavVerze: SamsungPrijimac.StavVerze? {
        guard let b = InstalatorSamsung.balicek?.0 else { return nil }
        let naTv = rizeni.viditelneTelevize.first(where: { $0.ip == ip })?.verze
        return SamsungPrijimac.porovnej(naTv: naTv, pribalena: b.verze)
    }

    private var popisVerzi: String {
        guard let b = InstalatorSamsung.balicek?.0 else { return "" }
        let naTv = rizeni.viditelneTelevize.first(where: { $0.ip == ip })?.verze
        switch stavVerze {
        case .starsi: return "V appce je verze \(b.verze), na TV starší \(naTv ?? "") – aktualizuj."
        case .stejna: return "Na TV je aktuální verze \(b.verze)."
        case .novejsi: return "Na TV je novější verze \(naTv ?? "") než v appce (\(b.verze)). Přeinstalace by ji vrátila zpět."
        default: return "V appce je verze \(b.verze). Verzi na TV nevím (přijímač neběží nebo ji nehlásí)."
        }
    }

    private var nazevTlacitka: String {
        switch stavVerze {
        case .starsi: return "Aktualizovat"
        case .stejna, .novejsi: return "Přeinstalovat"
        default: return "Nainstalovat"
        }
    }

    @ViewBuilder private func ikona(_ s: InstalatorSamsung.StavKroku) -> some View {
        switch s {
        case .ceka: Image(systemName: "circle").foregroundStyle(.tertiary)
        case .bezi: ProgressView().controlSize(.mini)
        case .hotovo: Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
        case .chyba: Image(systemName: "xmark.circle.fill").foregroundStyle(.red)
        }
    }
}
