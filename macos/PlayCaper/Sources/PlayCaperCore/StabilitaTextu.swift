import Foundation

// Přepis `StabilitaTextu.kt` (Android). Chování má zůstat stejné; změny dělat na obou stranách.

/// Zkrácení, zmizení a rozepsané pokračování potvrzuje druhé shodné čtení.
public final class StabilitaTextu {
    private var posledni: [String] = []
    private var podezrely: [String]?
    private var posledniOtisk: String?
    private var posledniRadky = 0

    public init() {}

    public func prijmi(_ text: String, otiskObrazu: String? = nil, radku: Int = 1) -> Bool {
        let slova = Self.slova(text.lowercased())
        if slova == posledni {
            podezrely = nil
            posledniRadky = (otiskObrazu != nil && otiskObrazu == posledniOtisk) ? max(radku, posledniRadky) : radku
            posledniOtisk = otiskObrazu
            return true
        }
        var i = 0
        for slovo in posledni where i < slova.count && slova[i] == slovo { i += 1 }
        let zkraceny = slova.count < posledni.count && i == slova.count
        if zkraceny && radku < posledniRadky && stejnyViceRadkovyObraz(otiskObrazu) {
            podezrely = nil
            return false
        }
        let konciVetou = Self.posledniZnak(text).map { ".!?…\"'".contains($0) } ?? false
        let dopisujeSe = !posledni.isEmpty && Self.doplneno(text, posledni.joined(separator: " ")) && !konciVetou
        if (zkraceny || dopisujeSe) && podezrely != slova {
            podezrely = slova
            return false
        }
        posledni = slova
        posledniOtisk = otiskObrazu
        posledniRadky = radku
        podezrely = nil
        return true
    }

    public func stejnyViceRadkovyObraz(_ otisk: String?) -> Bool {
        otisk != nil && otisk == posledniOtisk && posledniRadky >= 2
    }

    public func reset() {
        posledni = []; podezrely = nil; posledniOtisk = nil; posledniRadky = 0
    }

    private static let vzorSlov = try! NSRegularExpression(pattern: "[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?")

    static func slova(_ text: String) -> [String] {
        let ns = text as NSString
        return vzorSlov.matches(in: text, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range) }
    }

    static func posledniZnak(_ text: String) -> Character? {
        text.reversed().first { !$0.isWhitespace }
    }

    public static func doplneno(_ novy: String, _ puvodni: String) -> Bool {
        let pred = puvodni.lowercased().filter { $0.isLetter || $0.isNumber }
        let ted = novy.lowercased().filter { $0.isLetter || $0.isNumber }
        return !pred.isEmpty && ted.count > pred.count && ted.hasPrefix(pred)
    }
}

/// Celý text v oblasti titulků (přepis `TextVRamecku`): `nil` = beze změny, `""` = skrýt,
/// jinak nový potvrzený text k překladu.
public final class TextVRamecku {
    private let stabilita = StabilitaTextu()
    public private(set) var text = ""
    private var prazdnyOd: TimeInterval?
    /// Jak dlouho smí text chybět, než se titulek schová (OCR mezi snímky občas nic nepřečte).
    public static let tolerancePrazdna: TimeInterval = 1.5

    public init() {}

    public func zpracuj(_ radky: [String], ted: TimeInterval, otiskObrazu: String? = nil) -> String? {
        let novy = Self.spoj(radky)
        if novy.filter(\.isLetter).count < 3 {
            if text.isEmpty { return nil }
            if stabilita.stejnyViceRadkovyObraz(otiskObrazu) { prazdnyOd = nil; return nil }
            let od = prazdnyOd ?? ted
            prazdnyOd = od
            return ted - od > Self.tolerancePrazdna ? reset() : nil
        }
        prazdnyOd = nil
        let radku = radky.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.count
        if !stabilita.prijmi(novy, otiskObrazu: otiskObrazu, radku: radku) || novy == text { return nil }
        // Odchylka od Androidu: Vision mezi snímky střídá „Uh, yeah“ / „Uh. yeah“ (prototyp PC-009).
        // Stejná slova = stejný titulek, jinak by se věta pořád překládala znovu a na TV blikala.
        if StabilitaTextu.slova(novy.lowercased()) == StabilitaTextu.slova(text.lowercased()) { return nil }
        text = novy
        return novy
    }

    @discardableResult
    public func reset() -> String? {
        let bylText = !text.isEmpty
        text = ""
        prazdnyOd = nil
        stabilita.reset()
        return bylText ? "" : nil
    }

    public static func spoj(_ radky: [String]) -> String {
        var acc = ""
        for r in radky.map({ $0.trimmingCharacters(in: .whitespacesAndNewlines) }) where !r.isEmpty {
            if acc.isEmpty { acc = r }
            else if acc.hasSuffix("-") { acc = String(acc.dropLast()) + r }
            else { acc += " " + r }
        }
        return acc.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
    }
}
