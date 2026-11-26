// Prototyp PC-009: snímání okna PS Remote Play (ScreenCaptureKit) a OCR (Vision).
// Jen měření a ověření, ne základ aplikace. Viz macos/Prototyp/README.md.
import CoreImage
import Foundation
import ImageIO
import ScreenCaptureKit
import UniformTypeIdentifiers
import Vision

// MARK: - Parametry

struct Volby {
    var oknoId: CGWindowID?
    var oblast = CGRect(x: 0.10, y: 0.72, width: 0.80, height: 0.24) // zlomky okna, počátek vlevo nahoře
    var intervalMs = 100
    var sekund = 60
    var vystup = URL(fileURLWithPath: "vystup", isDirectory: true)
    var rychle = false

    init(_ args: [String]) throws {
        var i = 0
        func dalsi() throws -> String {
            i += 1
            guard i < args.count else { throw Chyba.argument("chybí hodnota u \(args[i - 1])") }
            return args[i]
        }
        while i < args.count {
            switch args[i] {
            case "--okno": oknoId = CGWindowID(try dalsi())
            case "--oblast":
                let c = try dalsi().split(separator: ",").compactMap { Double($0) }
                guard c.count == 4 else { throw Chyba.argument("--oblast x,y,š,v (zlomky 0–1)") }
                oblast = CGRect(x: c[0], y: c[1], width: c[2], height: c[3])
            case "--interval": intervalMs = Int(try dalsi()) ?? intervalMs
            case "--sekund": sekund = Int(try dalsi()) ?? sekund
            case "--vystup": vystup = URL(fileURLWithPath: try dalsi(), isDirectory: true)
            case "--rychle": rychle = true
            default: throw Chyba.argument("neznámý přepínač \(args[i])")
            }
            i += 1
        }
    }
}

enum Chyba: Error, CustomStringConvertible {
    case argument(String)
    case oknoNenalezeno
    var description: String {
        switch self {
        case .argument(let s): return s
        case .oknoNenalezeno: return "okno PS Remote Play nenalezeno (spusť `okna`, případně zadej --okno <id>)"
        }
    }
}

// MARK: - Okna

func vsechnaOkna() async throws -> [SCWindow] {
    let obsah = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: false)
    return obsah.windows
}

func jeRemotePlay(_ w: SCWindow) -> Bool {
    let app = w.owningApplication?.applicationName.lowercased() ?? ""
    let bundle = w.owningApplication?.bundleIdentifier.lowercased() ?? ""
    return app.contains("remote play") || bundle.contains("remoteplay") || app.contains("chiaki")
}

func najdiOkno(_ v: Volby) async throws -> SCWindow {
    let okna = try await vsechnaOkna()
    if let id = v.oknoId {
        guard let w = okna.first(where: { $0.windowID == id }) else { throw Chyba.oknoNenalezeno }
        return w
    }
    let kandidati = okna.filter { jeRemotePlay($0) && $0.frame.width > 300 && $0.frame.height > 200 }
    // Remote Play má víc (i skrytých) oken; přednost má viditelné, pak největší.
    func skore(_ w: SCWindow) -> (Int, CGFloat) { (w.isOnScreen ? 1 : 0, w.frame.width * w.frame.height) }
    guard let w = kandidati.max(by: { skore($0) < skore($1) }) else {
        throw Chyba.oknoNenalezeno
    }
    return w
}

func popis(_ w: SCWindow) -> String {
    let app = w.owningApplication?.applicationName ?? "?"
    let f = w.frame
    return "id \(w.windowID)  \(app) – „\(w.title ?? "")“  \(Int(f.width))×\(Int(f.height)) @ \(Int(f.minX)),\(Int(f.minY))  onScreen=\(w.isOnScreen) active=\(w.isActive) vrstva=\(w.windowLayer)"
}

func vypisOkna() async throws {
    let okna = try await vsechnaOkna()
        .filter { $0.frame.width > 100 && $0.frame.height > 100 }
        .sorted { (jeRemotePlay($0) ? 0 : 1, $0.windowID) < (jeRemotePlay($1) ? 0 : 1, $1.windowID) }
    for w in okna { print((jeRemotePlay(w) ? "▶ " : "  ") + popis(w)) }
}

// MARK: - Uložení obrázku

let ciKontext = CIContext()

func ulozPng(_ obraz: CGImage, _ url: URL) {
    guard let cil = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else { return }
    CGImageDestinationAddImage(cil, obraz, nil)
    CGImageDestinationFinalize(cil)
}

// MARK: - snimek: celé okno + vyznačená oblast

func snimek(_ v: Volby) async throws {
    let w = try await najdiOkno(v)
    print(popis(w))
    let filtr = SCContentFilter(desktopIndependentWindow: w)
    let k = SCStreamConfiguration()
    let meritko = CGFloat(filtr.pointPixelScale)
    k.width = Int(filtr.contentRect.width * meritko)
    k.height = Int(filtr.contentRect.height * meritko)
    k.showsCursor = false
    let t0 = Date()
    let obraz = try await SCScreenshotManager.captureImage(contentFilter: filtr, configuration: k)
    let ms = Int(Date().timeIntervalSince(t0) * 1000)
    try FileManager.default.createDirectory(at: v.vystup, withIntermediateDirectories: true)
    let url = v.vystup.appendingPathComponent("okno.png")
    ulozPng(obraz, url)
    print("Uloženo \(url.path) (\(obraz.width)×\(obraz.height) px, \(ms) ms), contentRect \(filtr.contentRect), měřítko \(meritko)")

    // náhled oblasti čtení
    let o = v.oblast
    let r = CGRect(x: o.minX * CGFloat(obraz.width), y: o.minY * CGFloat(obraz.height),
                   width: o.width * CGFloat(obraz.width), height: o.height * CGFloat(obraz.height)).integral
    if let vyrez = obraz.cropping(to: r) {
        let u = v.vystup.appendingPathComponent("oblast.png")
        ulozPng(vyrez, u)
        print("Oblast \(o) → \(u.path) (\(vyrez.width)×\(vyrez.height) px)")
    }
}

// MARK: - OCR

func rozpoznej(_ obraz: CGImage, rychle: Bool) -> ([String], Double, Error?) {
    let pozadavek = VNRecognizeTextRequest()
    pozadavek.recognitionLevel = rychle ? .fast : .accurate
    pozadavek.recognitionLanguages = ["en-US"]
    pozadavek.usesLanguageCorrection = true
    let t0 = Date()
    do {
        try VNImageRequestHandler(cgImage: obraz, options: [:]).perform([pozadavek])
    } catch {
        return ([], 0, error)
    }
    let ms = Date().timeIntervalSince(t0) * 1000
    let radky = (pozadavek.results ?? [])
        .sorted { $0.boundingBox.minY > $1.boundingBox.minY }
        .compactMap { $0.topCandidates(1).first?.string }
    return (radky, ms, nil)
}

func ocrSouboru(_ cesta: String, rychle: Bool) throws {
    guard let zdroj = CGImageSourceCreateWithURL(URL(fileURLWithPath: cesta) as CFURL, nil),
          let obraz = CGImageSourceCreateImageAtIndex(zdroj, 0, nil) else { throw Chyba.argument("nelze načíst \(cesta)") }
    for i in 1...3 {
        let (radky, ms, chyba) = rozpoznej(obraz, rychle: rychle)
        if let chyba { throw chyba }
        print("běh \(i): \(Int(ms)) ms  \(radky.joined(separator: " / "))")
    }
}

// MARK: - cti: průběžné snímání oblasti a OCR

final class Ctecka: NSObject, SCStreamOutput, SCStreamDelegate {
    let fronta = DispatchQueue(label: "snimky")
    let ocrFronta = DispatchQueue(label: "ocr")
    var zaneprazdneno = false
    let interval: TimeInterval
    let vystup: URL
    let rychle: Bool
    let start = Date()

    var posledniOcr = Date.distantPast
    var posledniText = ""
    var cisloVzorku = 0
    // statistika za posledních 5 s
    var uplne = 0, necinne = 0, jine = 0, ocrPocet = 0, ocrMsSoucet = 0.0, ocrMsMax = 0.0
    var bezSnimku = 0

    init(interval: TimeInterval, vystup: URL, rychle: Bool) {
        self.interval = interval
        self.vystup = vystup
        self.rychle = rychle
    }

    var cas: String { String(format: "%7.2f s", Date().timeIntervalSince(start)) }

    func stream(_ stream: SCStream, didOutputSampleBuffer sb: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .screen,
              let prilohy = CMSampleBufferGetSampleAttachmentsArray(sb, createIfNecessary: false) as? [[SCStreamFrameInfo: Any]],
              let raw = prilohy.first?[.status] as? Int,
              let stav = SCFrameStatus(rawValue: raw) else { return }
        switch stav {
        case .complete: uplne += 1
        case .idle: necinne += 1; return
        default: jine += 1; return
        }
        let ted = Date()
        guard !zaneprazdneno, ted.timeIntervalSince(posledniOcr) >= interval, let pb = sb.imageBuffer else { return }
        posledniOcr = ted
        // Kopie snímku: buffer ScreenCaptureKitu se hned vrací, OCR běží na vlastní frontě.
        let ci = CIImage(cvPixelBuffer: pb)
        guard let cg = ciKontext.createCGImage(ci, from: ci.extent) else { return }
        zaneprazdneno = true
        ocrFronta.async { [self] in
            let (radky, ms, chyba) = rozpoznej(cg, rychle: rychle)
            fronta.async { [self] in
                zaneprazdneno = false
                if let chyba { print("\(cas)  OCR chyba: \(chyba)"); return }
                ocrPocet += 1; ocrMsSoucet += ms; ocrMsMax = max(ocrMsMax, ms)
                let text = radky.joined(separator: " / ")
                guard text != posledniText else { return }
                posledniText = text
                cisloVzorku += 1
                print("\(cas)  OCR \(Int(ms)) ms  #\(cisloVzorku)  \(text.isEmpty ? "(prázdné)" : text)")
                if !text.isEmpty { ulozPng(cg, vystup.appendingPathComponent(String(format: "vzorek-%04d.png", cisloVzorku))) }
            }
        }
    }

    func vypisStatistiku() {
        fronta.async { [self] in
            let prumer = ocrPocet > 0 ? Int(ocrMsSoucet / Double(ocrPocet)) : 0
            print("\(cas)  ·· snímky: úplné \(uplne), beze změny \(necinne), jiné \(jine) | OCR \(ocrPocet)×, průměr \(prumer) ms, max \(Int(ocrMsMax)) ms")
            if uplne + necinne + jine == 0 {
                bezSnimku += 1
                print("\(cas)  !! za 5 s nepřišel žádný snímek (okno skryté/minimalizované/jiná plocha?)")
            }
            uplne = 0; necinne = 0; jine = 0; ocrPocet = 0; ocrMsSoucet = 0; ocrMsMax = 0
        }
    }

    func stream(_ stream: SCStream, didStopWithError error: Error) {
        print("\(cas)  !! stream skončil chybou: \(error)")
    }
}

func cti(_ v: Volby) async throws {
    let w = try await najdiOkno(v)
    print(popis(w))
    let filtr = SCContentFilter(desktopIndependentWindow: w)
    let meritko = CGFloat(filtr.pointPixelScale)
    let okno = filtr.contentRect
    let o = v.oblast
    let zdroj = CGRect(x: o.minX * okno.width, y: o.minY * okno.height,
                       width: o.width * okno.width, height: o.height * okno.height)
    let k = SCStreamConfiguration()
    k.sourceRect = zdroj
    k.width = Int(zdroj.width * meritko)
    k.height = Int(zdroj.height * meritko)
    k.minimumFrameInterval = CMTime(value: 1, timescale: 30)
    k.pixelFormat = kCVPixelFormatType_32BGRA
    k.showsCursor = false
    k.queueDepth = 3

    try FileManager.default.createDirectory(at: v.vystup, withIntermediateDirectories: true)
    let ctecka = Ctecka(interval: Double(v.intervalMs) / 1000, vystup: v.vystup, rychle: v.rychle)
    let stream = SCStream(filter: filtr, configuration: k, delegate: ctecka)
    try stream.addStreamOutput(ctecka, type: .screen, sampleHandlerQueue: ctecka.fronta)
    try await stream.startCapture()
    print("Čtu oblast \(o) = \(Int(zdroj.width))×\(Int(zdroj.height)) bodů (\(k.width)×\(k.height) px), OCR \(v.rychle ? "fast" : "accurate") nejvýš každých \(v.intervalMs) ms, \(v.sekund) s. Vzorky do \(v.vystup.path)")

    let konec = Date().addingTimeInterval(TimeInterval(v.sekund))
    while Date() < konec {
        try await Task.sleep(nanoseconds: 5_000_000_000)
        ctecka.vypisStatistiku()
    }
    try await stream.stopCapture()
}

// MARK: - main

setvbuf(stdout, nil, _IONBF, 0)
let args = Array(CommandLine.arguments.dropFirst())
do {
    switch args.first {
    case "okna": try await vypisOkna()
    case "snimek": try await snimek(Volby(Array(args.dropFirst())))
    case "cti": try await cti(Volby(Array(args.dropFirst())))
    case "ocr":
        guard args.count >= 2 else { throw Chyba.argument("ocr <soubor.png> [--rychle]") }
        try ocrSouboru(args[1], rychle: args.contains("--rychle"))
    default:
        print("""
        Použití: swift run Prototyp <příkaz> [volby]
          okna                     vypíše okna (▶ = Remote Play)
          snimek [volby]           uloží okno.png a oblast.png do výstupní složky
          cti [volby]              průběžně čte oblast a vypisuje text a časy
          ocr <soubor.png> [--rychle]  OCR jednoho obrázku (3 běhy, časy)
        Volby: --okno <id>  --oblast x,y,š,v (zlomky, výchozí 0.10,0.72,0.80,0.24)
               --interval <ms> (100)  --sekund <s> (60)  --vystup <složka> (vystup)  --rychle
        """)
    }
} catch {
    FileHandle.standardError.write("Chyba: \(error)\n".data(using: .utf8)!)
    exit(1)
}
