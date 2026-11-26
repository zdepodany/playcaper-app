import XCTest
@testable import PlayCaperCore

final class VyberTitulkuTests: XCTestCase {
    func r(_ t: String, _ x: Double, _ y: Double, _ s: Double, _ v: Double = 0.12, jistota: Float = 1) -> OcrRadek {
        OcrRadek(text: t, x: x, y: y, sirka: s, vyska: v, jistota: jistota)
    }

    func testVystredenyDvouradkovyTitulek() {
        let radky = [
            r("Whatever, I can't waste my time on this,", 0.25, 0.55, 0.50),
            r("I have important police business to do.", 0.25, 0.70, 0.50),
        ]
        XCTAssertEqual(VyberTitulku.vyber(radky).map(\.text), radky.map(\.text))
    }

    func testNapisMimoStredSeZahodi() {
        let radky = [
            r("JASON'S TATTOOS &", 0.02, 0.20, 0.25),
            r("Hey, how are you?", 0.40, 0.70, 0.20),
        ]
        XCTAssertEqual(VyberTitulku.vyber(radky).map(\.text), ["Hey, how are you?"])
    }

    func testVzdalenyVystredenyNapisNadTitulkemSeNepripoji() {
        let radky = [
            r("CRASH OUT CARTEL", 0.35, 0.05, 0.30, 0.20),
            r("Yo, over here, guys!", 0.38, 0.75, 0.24),
        ]
        XCTAssertEqual(VyberTitulku.vyber(radky).map(\.text), ["Yo, over here, guys!"])
    }

    func testCeskyTextSeNeprekladá() {
        // Obrazovka připojování PS Remote Play (zachyceno při prvním spuštění MVP).
        XCTAssertTrue(VyberTitulku.vyber([r("Ostatní připojení", 0.4, 0.7, 0.2)]).isEmpty)
        XCTAssertTrue(VyberTitulku.vyber([r("Zrušit", 0.45, 0.7, 0.1)]).isEmpty)
    }

    func testSmetiNaZacatkuRadku() {
        XCTAssertEqual(VyberTitulku.vycisti("• Another day, another dollar, right?"), "Another day, another dollar, right?")
        XCTAssertEqual(VyberTitulku.vycisti("‹Well, okay then..."), "Well, okay then...")
        XCTAssertEqual(VyberTitulku.vycisti("*I'm just gonna"), "I'm just gonna")
        XCTAssertEqual(VyberTitulku.vycisti("'Scuse me, hey!"), "'Scuse me, hey!")
        XCTAssertEqual(VyberTitulku.vycisti("...and then"), "...and then")
    }

    func testNizkaJistotaAZnakySeZahodi() {
        let radky = [r("•", 0.49, 0.5, 0.02), r("Go away.", 0.45, 0.7, 0.1, jistota: 0.1)]
        XCTAssertTrue(VyberTitulku.vyber(radky).isEmpty)
    }
}
