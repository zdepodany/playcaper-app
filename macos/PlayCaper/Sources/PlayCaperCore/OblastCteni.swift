import CoreGraphics

/// Úprava oblasti čtení tažením nad náhledem okna. Oblast je ve zlomcích obrazu hry
/// (0…1, počátek vlevo nahoře), stejně jako `Snimac.oblast` a rámeček `PCO1` na TV.
public enum OblastCteni {
    /// Za co se táhne: celý rámeček (posun), hrana nebo roh (změna velikosti).
    public enum Uchyt: CaseIterable, Sendable {
        case cele, vlevoNahore, nahore, vpravoNahore, vpravo, vpravoDole, dole, vlevoDole, vlevo

        var levy: Bool { self == .vlevo || self == .vlevoNahore || self == .vlevoDole }
        var pravy: Bool { self == .vpravo || self == .vpravoNahore || self == .vpravoDole }
        var horni: Bool { self == .nahore || self == .vlevoNahore || self == .vpravoNahore }
        var dolni: Bool { self == .dole || self == .vlevoDole || self == .vpravoDole }

        /// Poloha úchytu ve zlomcích rámečku (0 = vlevo/nahoře, 1 = vpravo/dole).
        public var poloha: CGPoint {
            CGPoint(x: levy ? 0 : pravy ? 1 : 0.5, y: horni ? 0 : dolni ? 1 : 0.5)
        }
    }

    /// Nejmenší šířka a výška oblasti (5 % obrazu), ať se rámeček nedá „ztratit“.
    public static let minVelikost: CGFloat = 0.05

    /// Rámeček `puvodni` po tažení úchytem o `dx`, `dy` (zlomky obrazu).
    /// Posun drží velikost a zastaví se na okraji obrazu; hrany nepřejdou přes protější.
    public static func uprav(_ puvodni: CGRect, uchyt: Uchyt, dx: CGFloat, dy: CGFloat) -> CGRect {
        let r = omez(puvodni)
        if uchyt == .cele {
            let x = min(max(0, r.minX + dx), 1 - r.width)
            let y = min(max(0, r.minY + dy), 1 - r.height)
            return CGRect(x: x, y: y, width: r.width, height: r.height)
        }
        var l = r.minX, t = r.minY, p = r.maxX, b = r.maxY
        if uchyt.levy { l = min(max(0, l + dx), p - minVelikost) }
        if uchyt.pravy { p = max(min(1, p + dx), l + minVelikost) }
        if uchyt.horni { t = min(max(0, t + dy), b - minVelikost) }
        if uchyt.dolni { b = max(min(1, b + dy), t + minVelikost) }
        return CGRect(x: l, y: t, width: p - l, height: b - t)
    }

    /// Oblast uvnitř obrazu a aspoň `minVelikost` velká (ruční zadání čísel, uložená hodnota).
    public static func omez(_ r: CGRect) -> CGRect {
        let r = r.standardized
        let w = min(max(r.width, minVelikost), 1)
        let h = min(max(r.height, minVelikost), 1)
        let x = min(max(0, r.minX), 1 - w)
        let y = min(max(0, r.minY), 1 - h)
        return CGRect(x: x, y: y, width: w, height: h)
    }

    /// Řádek OCR (souřadnice ve výřezu oblasti) převedený do zlomků celého obrazu hry.
    public static func radekVObraze(_ radek: OcrRadek, oblast: CGRect) -> CGRect {
        CGRect(x: oblast.minX + radek.x * oblast.width, y: oblast.minY + radek.y * oblast.height,
               width: radek.sirka * oblast.width, height: radek.vyska * oblast.height)
    }
}
