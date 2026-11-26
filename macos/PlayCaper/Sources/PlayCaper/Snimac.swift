import AppKit
import CoreImage
import PlayCaperCore
import ScreenCaptureKit
import Vision

/// Snímá výřez titulků z okna PS Remote Play (ScreenCaptureKit) a čte ho (Vision).
///
/// Ověřeno prototypem (PC-009): okno může být za jinými okny, ale ne skryté (Cmd+H),
/// minimalizované, ani při zhasnutém displeji.
final class Snimac: NSObject, SCStreamOutput, SCStreamDelegate {
    enum Stav: Equatable {
        case vypnuto, chybiOpravneni, hledamOkno, cte, bezSnimku
        case chyba(String)
    }

    /// Volá se na hlavním vlákně.
    var naStav: ((Stav) -> Void)?
    /// Volá se na hlavním vlákně: řádky (souřadnice v oblasti čtení) a čas OCR v ms.
    var naRadky: (([OcrRadek], Double) -> Void)?

    /// Oblast čtení jako zlomky obrazu hry (bez titulku okna), počátek vlevo nahoře.
    var oblast: CGRect {
        didSet { if oblast != oldValue { Task { await restartuj() } } }
    }

    static let interval: TimeInterval = 0.1 // jako INTERVAL_MS na Androidu

    private var aktivni = false
    private var stream: SCStream?
    private var oknoId: CGWindowID?
    private var velikostOkna: CGSize = .zero
    private var hlidac: Timer?
    private var stav: Stav = .vypnuto
    private var zadanoOpravneni = false

    // Jen na `snimkyFronta`:
    private let snimkyFronta = DispatchQueue(label: "cz.playcaper.snimky")
    private let ocrFronta = DispatchQueue(label: "cz.playcaper.ocr")
    private var zaneprazdneno = false
    private var posledniOcr = Date.distantPast
    private var posledniSnimek = Date.distantPast
    private var posledniObraz: CGImage?
    private let ci = CIContext()

    init(oblast: CGRect) {
        self.oblast = oblast
    }

    func spust() {
        guard !aktivni else { return }
        aktivni = true
        hlas(.hledamOkno)
        hlidac = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            Task { await self?.zkontroluj() }
        }
        Task { await zkontroluj() }
    }

    func zastav() {
        aktivni = false
        hlidac?.invalidate()
        hlidac = nil
        Task {
            await zastavStream()
            hlas(.vypnuto)
        }
    }

    /// Poslední přečtený výřez (pro ladění oblasti).
    func ulozPosledniVyrez(do url: URL) -> Bool {
        guard let obraz = snimkyFronta.sync(execute: { posledniObraz }),
              let cil = CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil) else { return false }
        CGImageDestinationAddImage(cil, obraz, nil)
        return CGImageDestinationFinalize(cil)
    }

    private func hlas(_ s: Stav) {
        DispatchQueue.main.async { [self] in
            guard s != stav else { return }
            stav = s
            naStav?(s)
        }
    }

    private func restartuj() async {
        guard aktivni else { return }
        await zastavStream()
        await zkontroluj()
    }

    private func zkontroluj() async {
        guard aktivni else { return }
        guard CGPreflightScreenCaptureAccess() else {
            if !zadanoOpravneni { zadanoOpravneni = true; CGRequestScreenCaptureAccess() }
            hlas(.chybiOpravneni)
            return
        }
        let okna: [SCWindow]
        do {
            okna = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: false).windows
        } catch {
            hlas(.chybiOpravneni)
            return
        }
        guard let w = Self.vyberOkno(okna) else {
            await zastavStream()
            hlas(.hledamOkno)
            return
        }
        if stream == nil || w.windowID != oknoId || w.frame.size != velikostOkna {
            await zastavStream()
            do { try await spustStream(w) } catch {
                hlas(.chyba(error.localizedDescription))
                return
            }
        }
        let posledni = snimkyFronta.sync { posledniSnimek }
        hlas(Date().timeIntervalSince(posledni) > 3 ? .bezSnimku : .cte)
    }

    /// Remote Play má víc (i skrytých) oken; přednost má viditelné, pak největší.
    static func vyberOkno(_ okna: [SCWindow]) -> SCWindow? {
        func jeRemotePlay(_ w: SCWindow) -> Bool {
            let app = w.owningApplication?.applicationName.lowercased() ?? ""
            let bundle = w.owningApplication?.bundleIdentifier.lowercased() ?? ""
            return app.contains("remote play") || bundle.contains("remoteplay") || app.contains("chiaki")
        }
        func skore(_ w: SCWindow) -> (Int, CGFloat) { (w.isOnScreen ? 1 : 0, w.frame.width * w.frame.height) }
        return okna.filter { jeRemotePlay($0) && $0.frame.width > 300 && $0.frame.height > 200 }
            .max { skore($0) < skore($1) }
    }

    /// Obraz hry v okně: pod titulkem okna (28 bodů, kromě celé obrazovky), 16:9 vystředěné.
    static func oblastVidea(_ okno: CGSize, celaObrazovka: Bool) -> CGRect {
        let titulek: CGFloat = celaObrazovka ? 0 : 28
        let k = CGRect(x: 0, y: titulek, width: okno.width, height: max(1, okno.height - titulek))
        if k.width / k.height > 16.0 / 9.0 {
            let w = k.height * 16 / 9
            return CGRect(x: (k.width - w) / 2, y: k.minY, width: w, height: k.height)
        }
        let h = k.width * 9 / 16
        return CGRect(x: 0, y: k.minY + (k.height - h) / 2, width: k.width, height: h)
    }

    private func spustStream(_ w: SCWindow) async throws {
        let filtr = SCContentFilter(desktopIndependentWindow: w)
        let meritko = CGFloat(filtr.pointPixelScale)
        let celaObrazovka = NSScreen.screens.contains { $0.frame.size == w.frame.size }
        let video = Self.oblastVidea(filtr.contentRect.size, celaObrazovka: celaObrazovka)
        let o = oblast
        let zdroj = CGRect(x: video.minX + o.minX * video.width, y: video.minY + o.minY * video.height,
                           width: o.width * video.width, height: o.height * video.height)
        let k = SCStreamConfiguration()
        k.sourceRect = zdroj
        k.width = max(32, Int(zdroj.width * meritko))
        k.height = max(32, Int(zdroj.height * meritko))
        k.minimumFrameInterval = CMTime(value: 1, timescale: 30)
        k.pixelFormat = kCVPixelFormatType_32BGRA
        k.showsCursor = false
        k.queueDepth = 3

        let s = SCStream(filter: filtr, configuration: k, delegate: self)
        try s.addStreamOutput(self, type: .screen, sampleHandlerQueue: snimkyFronta)
        snimkyFronta.sync { posledniSnimek = Date() } // čas na první snímek
        try await s.startCapture()
        stream = s
        oknoId = w.windowID
        velikostOkna = w.frame.size
        Zaznam.zapis("Snímám okno \(w.windowID) \(Int(w.frame.width))×\(Int(w.frame.height)), výřez \(Int(zdroj.width))×\(Int(zdroj.height)) bodů")
    }

    private func zastavStream() async {
        guard let s = stream else { return }
        stream = nil
        oknoId = nil
        try? await s.stopCapture()
    }

    // MARK: SCStreamOutput (na snimkyFronta)

    func stream(_ stream: SCStream, didOutputSampleBuffer sb: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .screen,
              let prilohy = CMSampleBufferGetSampleAttachmentsArray(sb, createIfNecessary: false) as? [[SCStreamFrameInfo: Any]],
              let raw = prilohy.first?[.status] as? Int,
              let stavSnimku = SCFrameStatus(rawValue: raw) else { return }
        guard stavSnimku == .complete || stavSnimku == .idle else { return }
        let ted = Date()
        posledniSnimek = ted
        guard stavSnimku == .complete, !zaneprazdneno, ted.timeIntervalSince(posledniOcr) >= Self.interval,
              let pb = sb.imageBuffer else { return }
        // Kopie snímku: buffer ScreenCaptureKitu se hned vrací, OCR běží na vlastní frontě.
        let obraz = CIImage(cvPixelBuffer: pb)
        guard let cg = ci.createCGImage(obraz, from: obraz.extent) else { return }
        posledniOcr = ted
        posledniObraz = cg
        zaneprazdneno = true
        ocrFronta.async { [self] in
            let (radky, ms) = Self.rozpoznej(cg)
            snimkyFronta.async { self.zaneprazdneno = false }
            DispatchQueue.main.async { self.naRadky?(radky, ms) }
        }
    }

    func stream(_ stream: SCStream, didStopWithError error: Error) {
        Zaznam.zapis("Snímání skončilo chybou: \(error.localizedDescription)")
        DispatchQueue.main.async { [self] in
            if self.stream === stream { self.stream = nil; oknoId = nil }
        }
    }

    // MARK: OCR

    static func rozpoznej(_ obraz: CGImage) -> ([OcrRadek], Double) {
        let pozadavek = VNRecognizeTextRequest()
        pozadavek.recognitionLevel = .accurate
        pozadavek.recognitionLanguages = ["en-US"]
        pozadavek.usesLanguageCorrection = true
        let t0 = Date()
        try? VNImageRequestHandler(cgImage: obraz, options: [:]).perform([pozadavek])
        let ms = Date().timeIntervalSince(t0) * 1000
        let radky: [OcrRadek] = (pozadavek.results ?? []).compactMap { o in
            guard let kandidat = o.topCandidates(1).first else { return nil }
            let b = o.boundingBox // normalizované, počátek vlevo dole
            return OcrRadek(text: kandidat.string, x: b.minX, y: 1 - b.maxY, sirka: b.width, vyska: b.height,
                            jistota: kandidat.confidence)
        }
        return (radky, ms)
    }
}
