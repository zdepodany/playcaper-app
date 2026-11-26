import XCTest
@testable import PlayCaperCore

final class SamsungPrijimacTests: XCTestCase {
    func testManifest() {
        let json = #"{"platform":"tizen","version":"1.3.1","file":"PlayCaption-Samsung-1.3.1-nepodpisany.wgt","id":"PCaption01.PlayCaptionTest"}"#
        XCTAssertEqual(SamsungPrijimac.prectiManifest(Data(json.utf8)),
                       .init(verze: "1.3.1", soubor: "PlayCaption-Samsung-1.3.1-nepodpisany.wgt"))
        XCTAssertNil(SamsungPrijimac.prectiManifest(Data(#"{"platform":"webos","version":"1","file":"a.ipk"}"#.utf8)))
        XCTAssertNil(SamsungPrijimac.prectiManifest(Data(#"{"platform":"tizen","version":"1","file":"../a.wgt"}"#.utf8)))
    }

    func testPorovnaniVerzi() {
        XCTAssertEqual(SamsungPrijimac.porovnej(naTv: "1.3.0", pribalena: "1.3.1"), .starsi)
        XCTAssertEqual(SamsungPrijimac.porovnej(naTv: "1.3.1", pribalena: "1.3.1"), .stejna)
        XCTAssertEqual(SamsungPrijimac.porovnej(naTv: "1.10.0", pribalena: "1.9.0"), .novejsi)
        XCTAssertEqual(SamsungPrijimac.porovnej(naTv: "1.3", pribalena: "1.3.0"), .stejna)
        XCTAssertEqual(SamsungPrijimac.porovnej(naTv: nil, pribalena: "1.3.1"), .neznama)
    }

    func testNazevZarizeni() {
        // skutečný výpis `sdb devices` (26. 11. 2026)
        let vypis = "List of devices attached \n192.168.1.119:26101 \tdevice    \tUE55CU7172UXXH\n"
        XCTAssertEqual(SamsungPrijimac.nazevZarizeni(vypis, ip: "192.168.1.119"), "UE55CU7172UXXH")
        XCTAssertNil(SamsungPrijimac.nazevZarizeni(vypis, ip: "192.168.1.11"))
        XCTAssertNil(SamsungPrijimac.nazevZarizeni("192.168.1.119:26101\toffline\tUE55\n", ip: "192.168.1.119"))
    }

    func testAktivniProfil() {
        let xml = """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <profiles active="PlayCaper" version="3.1">
        <profile name="PlayCaper">
        """
        XCTAssertEqual(SamsungPrijimac.aktivniProfil(xml), "PlayCaper")
        XCTAssertNil(SamsungPrijimac.aktivniProfil("<profiles version=\"3.1\">"))
    }

    func testRada() {
        XCTAssertNotNil(SamsungPrijimac.rada("error: failed to connect to remote target '192.168.1.119'"))
        XCTAssertNotNil(SamsungPrijimac.rada("Author certificate not match"))
        XCTAssertNil(SamsungPrijimac.rada("Tizen application is successfully installed."))
    }
}
