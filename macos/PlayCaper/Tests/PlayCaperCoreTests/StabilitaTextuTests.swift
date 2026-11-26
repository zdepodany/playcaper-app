import XCTest
@testable import PlayCaperCore

/// Případy převzaté z `app/src/test/.../StabilitaTextuTest.kt`, aby se Mac a Android chovaly stejně.
final class StabilitaTextuTests: XCTestCase {
    let cely = "We have to find the way back to our home."

    func testStejnePixelyNepotvrdiOpakovaneZkraceneOcr() {
        let s = StabilitaTextu()
        XCTAssertTrue(s.prijmi(cely, otiskObrazu: "snimek42", radku: 3))
        for _ in 0..<4 { XCTAssertFalse(s.prijmi("to our home.", otiskObrazu: "snimek42", radku: 1)) }
        XCTAssertTrue(s.prijmi(cely, otiskObrazu: "snimek42", radku: 3))
        XCTAssertFalse(s.prijmi("to our home.", otiskObrazu: "snimek43", radku: 1))
        XCTAssertTrue(s.prijmi("to our home.", otiskObrazu: "snimek43", radku: 1))
    }

    func testJednoradkoveCteniZustavaSPuvodnimPotvrzovanim() {
        let s = StabilitaTextu()
        XCTAssertTrue(s.prijmi(cely, otiskObrazu: "stejny", radku: 1))
        XCTAssertFalse(s.prijmi("to our home.", otiskObrazu: "stejny", radku: 1))
        XCTAssertTrue(s.prijmi("to our home.", otiskObrazu: "stejny", radku: 1))
    }

    func testChybejiciProstredniRadekJenJednouNahraduNepotvrdi() {
        let s = StabilitaTextu()
        XCTAssertTrue(s.prijmi(cely))
        XCTAssertFalse(s.prijmi("We have to find to our home."))
        XCTAssertTrue(s.prijmi(cely))
        XCTAssertFalse(s.prijmi("to our home."))
    }

    func testSkutecnaKratsiReplikaSePotvrdiDruhymCtenim() {
        let s = StabilitaTextu()
        XCTAssertTrue(s.prijmi(cely))
        XCTAssertFalse(s.prijmi("We have to find"))
        XCTAssertTrue(s.prijmi("We have to find"))
    }

    func testJinaReplikaNecekaNaPotvrzeni() {
        let s = StabilitaTextu()
        XCTAssertTrue(s.prijmi(cely))
        XCTAssertTrue(s.prijmi("Arthur: Get out of here!"))
    }

    func testPostupneDopsanyTextSeNezasekneNaPrvniCast() {
        let s = StabilitaTextu()
        XCTAssertTrue(s.prijmi("We have to"))
        XCTAssertFalse(s.prijmi("We have to find"))
        XCTAssertTrue(s.prijmi("We have to find"))
        XCTAssertTrue(s.prijmi(cely))
    }

    func testZalomeniNeniZmenaObsahu() {
        let s = StabilitaTextu()
        XCTAssertTrue(s.prijmi(cely))
        XCTAssertTrue(s.prijmi("We have to find\nthe way back\nto our home."))
    }

    func testPrazdnySnimekSeMusiPotvrdit() {
        let s = StabilitaTextu()
        XCTAssertTrue(s.prijmi(cely))
        XCTAssertFalse(s.prijmi(""))
        XCTAssertTrue(s.prijmi(""))
    }

    func testKompletniTextNeprobliknePriJednomNeuplnemSnimku() {
        let s = StabilitaTextu()
        var preklady: [String] = []
        var posledni = ""
        for text in [cely, "to our home.", cely] where s.prijmi(text) && text != posledni {
            preklady.append(text); posledni = text
        }
        XCTAssertEqual(preklady, [cely])
    }

    func testDoplneni() {
        XCTAssertTrue(StabilitaTextu.doplneno("We have to find the key", "We have to find"))
        XCTAssertTrue(StabilitaTextu.doplneno("They get here...", "They get her"))
        XCTAssertFalse(StabilitaTextu.doplneno("A different sentence.", "We have to find"))
        XCTAssertFalse(StabilitaTextu.doplneno("We have to find", "We have to find"))
    }

    func testSlovaSApostrofem() {
        XCTAssertEqual(StabilitaTextu.slova("i don't know, you're it"), ["i", "don't", "know", "you're", "it"])
    }

    // TextVRamecku

    func testCarkaVsTeckaJeStejnyText() {
        // Vision mezi snímky střídá „Uh, yeah“ a „Uh. yeah“ (prototyp PC-009) → překládat jen jednou.
        let t = TextVRamecku()
        XCTAssertEqual(t.zpracuj(["Uh, yeah, obviously..."], ted: 0), "Uh, yeah, obviously...")
        XCTAssertNil(t.zpracuj(["Uh. yeah, obviously..."], ted: 0.1))
    }

    func testKratkyVypadekCteniTitulekNeschova() {
        let t = TextVRamecku()
        XCTAssertNotNil(t.zpracuj(["Hey, how are you?"], ted: 0))
        XCTAssertNil(t.zpracuj([], ted: 0.1))
        XCTAssertNil(t.zpracuj(["Hey, how are you?"], ted: 0.5))
        XCTAssertNil(t.zpracuj([], ted: 1.0))
        XCTAssertNil(t.zpracuj([], ted: 2.4))
        XCTAssertEqual(t.zpracuj([], ted: 2.6), "")
    }

    func testSpojeniRadku() {
        XCTAssertEqual(TextVRamecku.spoj(["Whatever, I can't waste my time on this,", " I have important police business to do."]),
                       "Whatever, I can't waste my time on this, I have important police business to do.")
        XCTAssertEqual(TextVRamecku.spoj(["some-", "thing  odd"]), "something odd")
    }
}
