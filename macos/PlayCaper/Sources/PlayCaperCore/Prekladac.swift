import Foundation

/// Překlad EN → čeština: DeepL (vlastní klíče, jako na Androidu), záložně neoficiální Google.
/// Pauzy klíčů odpovídají `Prekladac.kt`: neplatný klíč / vyčerpaná kvóta 1 h, jiná chyba 30 s.
public actor Prekladac {
    public struct Vysledek: Sendable {
        public let text: String
        public let zdroj: String
    }

    private var deeplKlice: [String]
    private var pauzy: [String: Date] = [:]
    private let session: URLSession
    private static let pauzaKlic: TimeInterval = 60 * 60
    private static let pauzaKratka: TimeInterval = 30

    public init(deeplKlice: [String] = []) {
        self.deeplKlice = deeplKlice
        let k = URLSessionConfiguration.default
        k.timeoutIntervalForRequest = 3
        k.httpMaximumConnectionsPerHost = 4
        session = URLSession(configuration: k)
    }

    public func nastavKlice(_ klice: [String]) {
        deeplKlice = klice
        pauzy = [:]
    }

    public func preloz(_ text: String, cil: String = "cs") async -> Vysledek? {
        if let d = await zkusDeepl(text, cil) { return Vysledek(text: d, zdroj: "deepl") }
        for _ in 0..<2 {
            if let g = try? await google(text, cil), !g.isEmpty { return Vysledek(text: g, zdroj: "google") }
        }
        return nil
    }

    /// Naváže spojení předem, ať první titulek nečeká na TLS.
    public func zahrej() async {
        _ = try? await google("Hello.", "cs")
    }

    private func zkusDeepl(_ text: String, _ cil: String) async -> String? {
        for klic in deeplKlice where Date() >= (pauzy[klic] ?? .distantPast) {
            let (vysledek, pauza): (String?, TimeInterval)
            do { (vysledek, pauza) = try await deepl(text, cil, klic) } catch { (vysledek, pauza) = (nil, Self.pauzaKratka) }
            if let vysledek, !vysledek.isEmpty { return vysledek }
            pauzy[klic] = Date().addingTimeInterval(pauza)
            if pauza != Self.pauzaKlic { return nil }
        }
        return nil
    }

    private func deepl(_ text: String, _ cil: String, _ klic: String) async throws -> (String?, TimeInterval) {
        let hostitel = klic.hasSuffix(":fx") ? "api-free.deepl.com" : "api.deepl.com"
        var p = URLRequest(url: URL(string: "https://\(hostitel)/v2/translate")!)
        p.httpMethod = "POST"
        p.timeoutInterval = 2.5
        p.setValue("DeepL-Auth-Key \(klic)", forHTTPHeaderField: "Authorization")
        p.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        p.httpBody = try JSONSerialization.data(withJSONObject: [
            "text": [text], "source_lang": "EN", "target_lang": cil.uppercased(),
        ])
        let (data, odpoved) = try await session.data(for: p)
        switch (odpoved as? HTTPURLResponse)?.statusCode ?? 0 {
        case 200: break
        case 403, 456: return (nil, Self.pauzaKlic)
        default: return (nil, Self.pauzaKratka)
        }
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let preklad = ((json?["translations"] as? [[String: Any]])?.first?["text"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return (preklad, Self.pauzaKratka)
    }

    /// Neoficiální adresa Google Překladače (stejná jako na Androidu).
    private func google(_ text: String, _ cil: String) async throws -> String? {
        var c = URLComponents(string: "https://translate.googleapis.com/translate_a/single")!
        c.queryItems = [
            .init(name: "client", value: "gtx"), .init(name: "sl", value: "en"),
            .init(name: "tl", value: cil), .init(name: "dt", value: "t"), .init(name: "q", value: text),
        ]
        var p = URLRequest(url: c.url!)
        p.timeoutInterval = 2
        p.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
        let (data, odpoved) = try await session.data(for: p)
        guard (odpoved as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return Self.prectiGoogle(data)
    }

    /// Odpověď: `[[["přeložená věta","původní",…],["další věta",…]],…]`
    static func prectiGoogle(_ data: Data) -> String? {
        guard let koren = try? JSONSerialization.jsonObject(with: data) as? [Any],
              let kusy = koren.first as? [Any] else { return nil }
        let text = kusy.compactMap { ($0 as? [Any])?.first as? String }.joined()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return text.isEmpty ? nil : text
    }
}
