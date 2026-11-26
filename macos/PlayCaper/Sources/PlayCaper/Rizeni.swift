import AppKit
import PlayCaperCore
import SwiftUI

/// Spojuje snímání, OCR, překlad a odesílání na TV. Standardní režim z Androidu
/// (`RemotePlayService.prelozAUkaz` / `ukazCesky` / `schovejCesky`), bez příběhového režimu.
@MainActor
final class Rizeni: ObservableObject {
    /// Výchozí oblast čtení pro GTA VI: zlomky obrazu hry (bez titulku okna). Změřeno v prototypu PC-009.
    static let oblastGta = CGRect(x: 0.10, y: 0.70, width: 0.80, height: 0.26)
    static let dobaTitulku: Int64 = 15_000

    private let d = UserDefaults.standard

    @Published var zapnuto: Bool { didSet { d.set(zapnuto, forKey: "zapnuto"); prepni() } }
    @Published var gta: Bool { didSet { d.set(gta, forKey: "gta"); vysilac.nastavGta(gta) } }
    /// "" = žádná televize (jen čtení a záznam).
    @Published var vybranaTvId: String { didSet { d.set(vybranaTvId, forKey: "tvId"); aplikujCil() } }
    @Published var oblast: CGRect { didSet { ulozOblast(); snimac.oblast = oblast } }

    @Published private(set) var stav: Snimac.Stav = .vypnuto
    @Published private(set) var televize: [Protokol.Televize] = []
    @Published private(set) var chybaSite: String?
    @Published private(set) var posledniEn = ""
    @Published private(set) var posledniCz = ""
    @Published private(set) var posledniInfo = ""
    @Published private(set) var ocrMs = 0.0
    /// Řádky posledního OCR pro náhled v okně Oblast čtení (samostatný objekt, ať se 10× za sekundu
    /// nepřekresluje menu).
    let ladeni = LadeniOcr()

    private let snimac: Snimac
    private let vysilac: Vysilac
    private let hledac = HledacTelevizi()
    private let prekladac: Prekladac
    private let zamekDispleje = ZamekDispleje()
    private let textVRamecku = TextVRamecku()
    private var videno: [String: Date] = [:]
    private var tokenPrekladu = 0
    private var ukazanoKdy: Date?
    private var ukazanaDelka = 0
    private var schovani: DispatchWorkItem?
    private var schovaniOblasti: DispatchWorkItem?
    private var oblastNaTvKdy = Date.distantPast

    init() {
        d.register(defaults: ["zapnuto": true, "gta": true, "tvId": ""])
        let ulozenaOblast = (d.array(forKey: "oblast") as? [Double]).flatMap { a in
            a.count == 4 ? CGRect(x: a[0], y: a[1], width: a[2], height: a[3]) : nil
        }.map(OblastCteni.omez) ?? Self.oblastGta
        zapnuto = d.bool(forKey: "zapnuto")
        gta = d.bool(forKey: "gta")
        vybranaTvId = d.string(forKey: "tvId") ?? ""
        oblast = ulozenaOblast
        snimac = Snimac(oblast: ulozenaOblast)
        vysilac = Vysilac(gta: d.bool(forKey: "gta"))
        prekladac = Prekladac(deeplKlice: Klicenka.nactiKlice())

        // Uložená televize platí hned (ještě před jejím ohlášením), IP se pak aktualizuje.
        if let id = d.string(forKey: "tvId"), !id.isEmpty, let ip = d.string(forKey: "tvIp") {
            televize = [Protokol.Televize(id: id, jmeno: d.string(forKey: "tvJmeno") ?? id, ip: ip)]
        }

        snimac.naStav = { [weak self] s in self?.stav = s }
        snimac.naRadky = { [weak self] r, ms in self?.zpracujOcr(r, ms: ms) }
        vysilac.naChybu = { [weak self] ch in self?.chybaSite = ch }
        hledac.naTelevizi = { [weak self] tv in self?.prijmiTelevizi(tv) }
        hledac.spust()
        aplikujCil()
        Task { await prekladac.zahrej() }
        Zaznam.zapis("PlayCaper spuštěn (DeepL klíčů: \(Klicenka.nactiKlice().count))")
        prepni()
    }

    /// Počet uložených DeepL klíčů (drží se v paměti, hlavní okno se překresluje po každém OCR).
    @Published private(set) var deeplKlicu = Klicenka.nactiKlice().count

    func nastavKlice(_ klice: [String]) {
        Klicenka.ulozKlice(klice)
        Task { await prekladac.nastavKlice(klice) }
        deeplKlicu = klice.count
    }

    func ulozVyrez() {
        let url = Zaznam.soubor.deletingLastPathComponent().appendingPathComponent("vyrez.png")
        if snimac.ulozPosledniVyrez(do: url) { NSWorkspace.shared.open(url) }
    }

    /// Modrý rámeček oblasti na TV nad skutečnou hrou (jako tažení rámečku na Androidu).
    /// Při tažení nejvýš ~12× za sekundu, `vynutit` pošle vždy (konečná poloha).
    /// Schová se 2,5 s po posledním volání.
    func ukazOblastNaTv(_ o: CGRect, vynutit: Bool = false) {
        let ted = Date()
        if vynutit || ted.timeIntervalSince(oblastNaTvKdy) >= 0.08 {
            oblastNaTvKdy = ted
            vysilac.posliJednou(Protokol.oblast(o, ukazat: true))
        }
        schovaniOblasti?.cancel()
        let ukol = DispatchWorkItem { [weak self] in
            self?.vysilac.posliJednou(Protokol.oblast(o, ukazat: false))
        }
        schovaniOblasti = ukol
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5, execute: ukol)
    }

    /// Vybraná TV je Samsung se starším přijímačem, než jaký je přibalený v appce.
    var samsungKAktualizaci: Bool {
        guard let tv = viditelneTelevize.first(where: { $0.id == vybranaTvId }), tv.id.hasPrefix("samsung-"),
              let b = InstalatorSamsung.balicek?.0 else { return false }
        return SamsungPrijimac.porovnej(naTv: tv.verze, pribalena: b.verze) == .starsi
    }

    /// Televize ohlášené za posledních 10 s (+ vybraná, i když se zrovna neohlásila).
    var viditelneTelevize: [Protokol.Televize] {
        let ted = Date()
        return televize.filter { $0.id == vybranaTvId || ted.timeIntervalSince(videno[$0.id] ?? .distantPast) < 10 }
    }

    // MARK: - Řízení

    private func prepni() {
        zamekDispleje.drz(zapnuto)
        if zapnuto {
            snimac.spust()
        } else {
            snimac.zastav()
            textVRamecku.reset()
            tokenPrekladu += 1
            schovani?.cancel()
            vysilac.zmena("", doba: 0)
            posledniEn = ""; posledniCz = ""
            ladeni.nastav([], vybrane: [], oblast: oblast)
        }
    }

    private func prijmiTelevizi(_ tv: Protokol.Televize) {
        videno[tv.id] = Date()
        if let i = televize.firstIndex(where: { $0.id == tv.id }) {
            if televize[i] != tv { televize[i] = tv }
        } else {
            televize.append(tv)
            Zaznam.zapis("Nalezena TV \(tv.jmeno) (\(tv.id), \(tv.ip)\(tv.verze.map { ", verze \($0)" } ?? ""))")
        }
        if tv.id == vybranaTvId { aplikujCil() }
        objectWillChange.send() // viditelneTelevize závisí na čase posledního ohlášení
    }

    private func aplikujCil() {
        guard let tv = televize.first(where: { $0.id == vybranaTvId }) else {
            vysilac.nastavCil(nil)
            return
        }
        d.set(tv.ip, forKey: "tvIp")
        d.set(tv.jmeno, forKey: "tvJmeno")
        vysilac.nastavCil(tv.ip)
    }

    private func ulozOblast() {
        d.set([oblast.minX, oblast.minY, oblast.width, oblast.height], forKey: "oblast")
    }

    // MARK: - OCR → překlad → TV

    private func zpracujOcr(_ radky: [OcrRadek], ms: Double) {
        guard zapnuto else { return }
        ocrMs = ms
        let vybrane = VyberTitulku.vyber(radky)
        ladeni.nastav(radky, vybrane: vybrane, oblast: oblast)
        guard let novy = textVRamecku.zpracuj(vybrane.map(\.text), ted: Date().timeIntervalSince1970) else { return }
        if novy.isEmpty {
            schovejCesky()
            return
        }
        posledniEn = novy
        prelozAUkaz(novy, casCteni: ms)
    }

    private func prelozAUkaz(_ anglicky: String, casCteni: Double) {
        tokenPrekladu += 1
        let token = tokenPrekladu
        let t0 = Date()
        Task {
            let vysledek = await prekladac.preloz(anglicky)
            guard token == tokenPrekladu, zapnuto else { return }
            let msPrekladu = Int(Date().timeIntervalSince(t0) * 1000)
            let cesky = vysledek.map { Self.velkePismeno($0.text, podle: anglicky) } ?? anglicky
            let zdroj = vysledek?.zdroj ?? "bez překladu"
            posledniCz = cesky
            posledniInfo = "\(zdroj) · čtení \(Int(casCteni)) ms · překlad \(msPrekladu) ms"
            Zaznam.zapis("EN: \(anglicky)\n             → CZ (\(posledniInfo)): \(cesky)")
            ukazCesky(cesky)
            // Anglický titulek mezitím zmizel → i tak ukázat, ale jen na minimální dobu.
            if textVRamecku.text.isEmpty { schovejCesky() }
        }
    }

    /// Anglická věta začíná velkým písmenem → česká taky (jako na Androidu).
    static func velkePismeno(_ cesky: String, podle anglicky: String) -> String {
        guard anglicky.first(where: { !$0.isWhitespace })?.isUppercase == true, let prvni = cesky.first else { return cesky }
        return prvni.uppercased() + cesky.dropFirst()
    }

    private func ukazCesky(_ cesky: String) {
        schovani?.cancel()
        if ukazanoKdy == nil || Date().timeIntervalSince(ukazanoKdy!) > 0.4 { ukazanoKdy = Date() }
        ukazanaDelka = cesky.count
        vysilac.zmena(cesky, doba: Self.dobaTitulku)
    }

    /// Anglický titulek zmizel → český schovat, až si ho stihneš přečíst
    /// (aspoň 1,8 s + 60 ms na písmeno od chvíle, kdy se ukázal).
    private func schovejCesky() {
        let minDoba = 1.8 + 0.06 * Double(ukazanaDelka)
        let zbyva = (ukazanoKdy ?? .distantPast).addingTimeInterval(minDoba).timeIntervalSinceNow
        ukazanoKdy = nil
        schovani?.cancel()
        let ukol = DispatchWorkItem { [weak self] in
            self?.vysilac.zmena("", doba: 0)
            self?.posledniEn = ""
        }
        schovani = ukol
        DispatchQueue.main.asyncAfter(deadline: .now() + max(0, zbyva), execute: ukol)
    }
}

/// Řádky posledního OCR ve zlomcích obrazu hry, pro náhled v okně Oblast čtení.
@MainActor
final class LadeniOcr: ObservableObject {
    struct Radek: Equatable {
        let ramec: CGRect
        /// true = výběr titulku ho vzal, false = zahozený (nápis ve scéně, HUD…)
        let vybrany: Bool
    }

    @Published private(set) var radky: [Radek] = []

    func nastav(_ vse: [OcrRadek], vybrane: [OcrRadek], oblast: CGRect) {
        let nove = vse.map { Radek(ramec: OblastCteni.radekVObraze($0, oblast: oblast), vybrany: vybrane.contains($0)) }
        if nove != radky { radky = nove }
    }
}
