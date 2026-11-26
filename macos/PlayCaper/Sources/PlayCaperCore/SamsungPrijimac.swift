import Foundation

/// Pomocná logika instalace Samsung přijímače z Macu (Tizen CLI), bez spouštění procesů.
/// Postup odpovídá `tizen/NAVOD-SAMSUNG.md`, krok 3b.
public enum SamsungPrijimac {
    /// Aplikace přijímače na TV (`tizen/app/config.xml`); nesmí se měnit.
    public static let idAplikace = "PCaption01.PlayCaptionTest"

    /// Přibalený přijímač podle `receiver-manifest.json` z `tizen/build.py`.
    public struct Balicek: Equatable, Sendable {
        public let verze: String
        public let soubor: String
        public init(verze: String, soubor: String) { self.verze = verze; self.soubor = soubor }
    }

    public static func prectiManifest(_ data: Data) -> Balicek? {
        guard let j = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              (j["platform"] as? String) == "tizen",
              let verze = j["version"] as? String, let soubor = j["file"] as? String,
              soubor.hasSuffix(".wgt"), !soubor.contains("/") else { return nil }
        return Balicek(verze: verze, soubor: soubor)
    }

    public enum StavVerze: Equatable, Sendable {
        /// Přijímač neběží nebo verzi nehlásí (starší než 1.3.0).
        case neznama
        case starsi, stejna, novejsi
    }

    /// Verze na TV vůči přibalené (číselně po částech, „1.10.0“ > „1.9.0“).
    public static func porovnej(naTv: String?, pribalena: String) -> StavVerze {
        guard let naTv, !naTv.isEmpty else { return .neznama }
        let a = naTv.split(separator: ".").map { Int($0) ?? 0 }
        let b = pribalena.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(a.count, b.count) {
            let x = i < a.count ? a[i] : 0, y = i < b.count ? b[i] : 0
            if x != y { return x < y ? .starsi : .novejsi }
        }
        return .stejna
    }

    /// Název připojené TV pro `tizen install -t` z výpisu `sdb devices`
    /// (`192.168.1.119:26101 \tdevice \tUE55CU7172UXXH`). Nil = TV s touto IP není připojená.
    public static func nazevZarizeni(_ vypis: String, ip: String) -> String? {
        for radek in vypis.split(whereSeparator: \.isNewline) {
            let c = radek.split(whereSeparator: { $0 == " " || $0 == "\t" }).map(String.init)
            guard c.count >= 3, c[1] == "device" else { continue }
            if c[0] == ip || c[0].hasPrefix(ip + ":") { return c[2] }
        }
        return nil
    }

    /// Aktivní profil certifikátu z `~/tizen-studio-data/profile/profiles.xml`.
    public static func aktivniProfil(_ xml: String) -> String? {
        guard let r = xml.range(of: #"<profiles[^>]*\sactive="([^"]+)""#, options: .regularExpression) else { return nil }
        let shoda = String(xml[r])
        guard let z = shoda.range(of: #"active=""#) else { return nil }
        let zbytek = shoda[z.upperBound...]
        return zbytek.firstIndex(of: "\"").map { String(zbytek[..<$0]) }
    }

    /// Srozumitelná rada podle výstupu Tizen CLI (časté chyby z návodu).
    public static func rada(_ vystup: String) -> String? {
        let v = vystup.lowercased()
        if v.contains("author certificate not match") || v.contains("author certificate not matched") {
            return "Na TV je přijímač podepsaný jiným certifikátem. Odinstaluj PlayCaption Samsung v Apps na TV a zkus to znovu."
        }
        if v.contains("118") || v.contains("invalid certificate chain") || v.contains("certificate") && v.contains("invalid") {
            return "TV odmítla certifikát. Profil v Certificate Manageru musí obsahovat DUID této TV."
        }
        if v.contains("failed to connect") || v.contains("unable to connect") || v.contains("connection refused") {
            return "K TV se nejde připojit. TV musí být zapnutá, v Developer Mode musí být jako Host PC IP adresa tohoto Macu; případně TV na chvíli vypoj ze zásuvky."
        }
        return nil
    }
}
