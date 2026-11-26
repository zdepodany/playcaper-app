import Foundation

/// Protokol telefon/Mac → přijímač na TV. Musí odpovídat `Propojeni.kt` (Android)
/// a `webos/service/protocol.js` (LG; Samsung má kopii přes `tizen/sync.py`).
///
/// - TV se každé 2 s hlásí broadcastem `PLAYCAPTION_TV\t<id>\t<jméno>\t<0/1>[\t<verze>]` na port 41988.
/// - Mac posílá titulky přímo na IP televize, port 41989:
///   `PCT1\t<číslo>\t<doba ms>\t<text>\t<nápověda>` (text escapovaný, prázdný = smazat).
public enum Protokol {
    public static let portHledani: UInt16 = 41988
    public static let portTitulky: UInt16 = 41989
    public static let ohlaseni = "PLAYCAPTION_TV"
    /// U+2063 na začátku textu = vzhled GTA VI na přijímači (starší přijímače ho jen nezobrazí).
    public static let znackaGta = "\u{2063}"

    public static func escapuj(_ s: String) -> String {
        s.replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\t", with: " ")
            .replacingOccurrences(of: "\n", with: "\\n")
    }

    public static func titulek(cislo: Int64, doba: Int64, text: String, napoveda: String = "", gta: Bool) -> String {
        let t = gta && !text.isEmpty ? znackaGta + text : text
        return "PCT1\t\(cislo)\t\(doba)\t\(escapuj(t))\t\(escapuj(napoveda))"
    }

    /// Smaže titulek, volby i rámeček na televizi, od které se odpojujeme (jako `vycistiCil` na Androidu).
    public static func vycisteni(titulekId: Int64, volbyId: Int64) -> [String] {
        [
            "PCT1\t\(titulekId)\t0\t\t",
            "PCV1\t\(volbyId)\t0\t0.1\t0.1\t0.9\t0.9\t-1\t",
            "PCO1\t0\t0.1\t0.1\t0.9\t0.9",
        ]
    }

    public struct Televize: Hashable, Identifiable, Sendable {
        public let id: String
        public let jmeno: String
        public let ip: String
        public let verze: String?
        public init(id: String, jmeno: String, ip: String, verze: String? = nil) {
            self.id = id; self.jmeno = jmeno; self.ip = ip; self.verze = verze
        }
    }

    /// Broadcast ohlášení televize; nonce přímých odpovědí (5. pole) Mac nepoužívá.
    public static func prectiOhlaseni(_ zprava: String, ip: String) -> Televize? {
        let c = zprava.components(separatedBy: "\t")
        guard c.count >= 3, c[0] == ohlaseni, !c[1].isEmpty else { return nil }
        let verze = c.count >= 5 && !c[4].isEmpty ? c[4] : nil
        return Televize(id: c[1], jmeno: c[2], ip: ip, verze: verze)
    }
}
