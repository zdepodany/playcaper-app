import CoreGraphics
import XCTest
@testable import PlayCaperCore

final class OblastCteniTests: XCTestCase {
    private let gta = CGRect(x: 0.10, y: 0.70, width: 0.80, height: 0.26)

    private func shoda(_ a: CGRect, _ b: CGRect, file: StaticString = #filePath, line: UInt = #line) {
        for (x, y) in [(a.minX, b.minX), (a.minY, b.minY), (a.width, b.width), (a.height, b.height)] {
            XCTAssertEqual(x, y, accuracy: 1e-9, "\(a) ≠ \(b)", file: file, line: line)
        }
    }

    func testPosunDrziVelikost() {
        shoda(OblastCteni.uprav(gta, uchyt: .cele, dx: -0.05, dy: -0.2),
              CGRect(x: 0.05, y: 0.50, width: 0.80, height: 0.26))
    }

    func testPosunSeZastaviNaOkraji() {
        shoda(OblastCteni.uprav(gta, uchyt: .cele, dx: 0.5, dy: 0.5),
              CGRect(x: 0.20, y: 0.74, width: 0.80, height: 0.26))
        shoda(OblastCteni.uprav(gta, uchyt: .cele, dx: -1, dy: -1),
              CGRect(x: 0, y: 0, width: 0.80, height: 0.26))
    }

    func testRohMeniDveHrany() {
        // levý horní roh: pravý a dolní okraj zůstanou
        shoda(OblastCteni.uprav(gta, uchyt: .vlevoNahore, dx: 0.1, dy: -0.1),
              CGRect(x: 0.20, y: 0.60, width: 0.70, height: 0.36))
        // pravý dolní roh nepřeteče z obrazu
        shoda(OblastCteni.uprav(gta, uchyt: .vpravoDole, dx: 0.5, dy: 0.5),
              CGRect(x: 0.10, y: 0.70, width: 0.90, height: 0.30))
    }

    func testHranaNeprejdePresProtejsi() {
        let r = OblastCteni.uprav(gta, uchyt: .vlevo, dx: 2, dy: 0)
        XCTAssertEqual(r.maxX, 0.90, accuracy: 1e-9)
        XCTAssertEqual(r.width, OblastCteni.minVelikost, accuracy: 1e-9)
        let h = OblastCteni.uprav(gta, uchyt: .dole, dx: 0, dy: -1)
        XCTAssertEqual(h.minY, 0.70, accuracy: 1e-9)
        XCTAssertEqual(h.height, OblastCteni.minVelikost, accuracy: 1e-9)
    }

    func testHranaMeniJenSvujSmer() {
        shoda(OblastCteni.uprav(gta, uchyt: .nahore, dx: 0.3, dy: -0.1),
              CGRect(x: 0.10, y: 0.60, width: 0.80, height: 0.36))
    }

    func testOmez() {
        shoda(OblastCteni.omez(CGRect(x: -0.2, y: 0.9, width: 0.5, height: 0.3)),
              CGRect(x: 0, y: 0.7, width: 0.5, height: 0.3))
        shoda(OblastCteni.omez(CGRect(x: 0.5, y: 0.5, width: 0, height: 2)),
              CGRect(x: 0.5, y: 0, width: 0.05, height: 1))
    }

    func testRadekVObraze() {
        let radek = OcrRadek(text: "Hi", x: 0.25, y: 0.5, sirka: 0.5, vyska: 0.25)
        shoda(OblastCteni.radekVObraze(radek, oblast: gta),
              CGRect(x: 0.30, y: 0.83, width: 0.40, height: 0.065))
    }

    func testPolohaUchytu() {
        XCTAssertEqual(OblastCteni.Uchyt.vlevoNahore.poloha, CGPoint(x: 0, y: 0))
        XCTAssertEqual(OblastCteni.Uchyt.vpravo.poloha, CGPoint(x: 1, y: 0.5))
        XCTAssertEqual(OblastCteni.Uchyt.dole.poloha, CGPoint(x: 0.5, y: 1))
    }
}
