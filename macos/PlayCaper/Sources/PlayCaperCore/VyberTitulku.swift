import Foundation

/// Jeden řádek z OCR. Souřadnice jsou zlomky oblasti čtení (0–1), počátek vlevo nahoře.
public struct OcrRadek: Equatable, Sendable {
    public var text: String
    public var x: Double, y: Double, sirka: Double, vyska: Double
    public var jistota: Float

    public init(text: String, x: Double, y: Double, sirka: Double, vyska: Double, jistota: Float = 1) {
        self.text = text; self.x = x; self.y = y; self.sirka = sirka; self.vyska = vyska; self.jistota = jistota
    }
    public var stredX: Double { x + sirka / 2 }
    public var spodek: Double { y + vyska }
}

/// Výběr řádků titulku z oblasti čtení.
///
/// Titulky (GTA VI a většina her) jsou vodorovně vystředěné a tvoří souvislý blok řádků stejné
/// výšky u spodního okraje. Nápisy ve scéně („JASON'S TATTOOS“, „NO REFUNDS“) bývají mimo střed,
/// jinak velké nebo oddělené mezerou. Prototyp (PC-009) ukázal, že právě ony jsou hlavní šum.
public enum VyberTitulku {
    /// Největší odchylka středu řádku od středu oblasti (zlomek šířky oblasti).
    public static let toleranceStredu = 0.08
    public static let minJistota: Float = 0.3

    public static func vyber(_ radky: [OcrRadek]) -> [OcrRadek] {
        let kandidati = radky.filter { r in
            r.jistota >= minJistota &&
                abs(r.stredX - 0.5) <= toleranceStredu &&
                r.text.filter(\.isLetter).count >= 2 &&
                // Písmena s diakritikou = český text (okno Remote Play, menu), ne anglický titulek.
                !r.text.contains { $0.isLetter && !$0.isASCII }
        }.sorted { $0.y < $1.y }
        guard let spodni = kandidati.last else { return [] }
        var blok = [spodni]
        // Nahoru přidávat řádky, které navazují (malá mezera) a mají podobnou výšku písma.
        for r in kandidati.dropLast().reversed() {
            let horni = blok[0]
            let mezera = horni.y - r.spodek
            let pomer = r.vyska / horni.vyska
            guard mezera <= horni.vyska * 1.0, pomer > 0.7, pomer < 1.4 else { break }
            blok.insert(r, at: 0)
        }
        return blok.map { var r = $0; r.text = vycisti(r.text); return r }
    }

    /// Smetí na začátku řádku z OCR („• Another day…“, „‹Well…“, „*I'm…“).
    public static func vycisti(_ radek: String) -> String {
        let povolene = "'\"‘’“”.…¡¿(-"
        let t = radek.drop { !($0.isLetter || $0.isNumber || povolene.contains($0)) }
        return String(t).trimmingCharacters(in: .whitespaces)
    }
}
