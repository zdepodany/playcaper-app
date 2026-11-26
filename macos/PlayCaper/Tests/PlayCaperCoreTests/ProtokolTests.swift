import XCTest
@testable import PlayCaperCore

/// Formát musí sedět s `Propojeni.kt` a `webos/service/protocol.js`.
/// Bajty jsou ověřené i proti skutečnému parseru přijímače: `node macos/PlayCaper/nastroje/falesna-tv.cjs`.
final class ProtokolTests: XCTestCase {
    func testTitulek() {
        XCTAssertEqual(Protokol.titulek(cislo: 7, doba: 15000, text: "Ahoj\ntam", gta: false),
                       "PCT1\t7\t15000\tAhoj\\ntam\t")
    }

    func testTitulekGta() {
        XCTAssertEqual(Protokol.titulek(cislo: 1, doba: 15000, text: "Jdi pryč.", gta: true),
                       "PCT1\t1\t15000\t\u{2063}Jdi pryč.\t")
        // prázdný text = smazat, bez značky
        XCTAssertEqual(Protokol.titulek(cislo: 2, doba: 0, text: "", gta: true), "PCT1\t2\t0\t\t")
    }

    func testEscapovani() {
        XCTAssertEqual(Protokol.escapuj("a\\b\tc\nd"), "a\\\\b c\\nd")
    }

    func testOhlaseniLg() {
        let tv = Protokol.prectiOhlaseni("PLAYCAPTION_TV\tlg-abc\tLG obývák\t0", ip: "192.168.1.50")
        XCTAssertEqual(tv, Protokol.Televize(id: "lg-abc", jmeno: "LG obývák", ip: "192.168.1.50"))
    }

    func testOhlaseniSamsungSVerzi() {
        let tv = Protokol.prectiOhlaseni("PLAYCAPTION_TV\tsamsung-1\tSamsung\t0\t1.3.0", ip: "192.168.1.119")
        XCTAssertEqual(tv?.verze, "1.3.0")
    }

    func testCiziZpravaNeniTelevize() {
        XCTAssertNil(Protokol.prectiOhlaseni("PLAYCAPTION_FIND\tabc", ip: "1.2.3.4"))
        XCTAssertNil(Protokol.prectiOhlaseni("PLAYCAPTION_TV\t\tX", ip: "1.2.3.4"))
    }
}
