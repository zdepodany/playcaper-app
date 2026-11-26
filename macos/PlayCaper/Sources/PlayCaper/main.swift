import AppKit
import CoreGraphics
import ImageIO
import PlayCaperCore
import SwiftUI

struct PlayCaperApp: App {
    @StateObject private var rizeni = Rizeni()

    var body: some Scene {
        // Hlavní okno: ikona v menu baru se na MacBooku s výřezem často nevejde (schová se pod něj),
        // proto je stejný obsah i v okně, které se otevře při spuštění a po kliknutí na ikonu v Docku.
        Window("PlayCaper", id: "hlavni") {
            MenuView(rizeni: rizeni)
        }
        .windowResizability(.contentSize)

        MenuBarExtra {
            MenuView(rizeni: rizeni)
        } label: {
            Image(systemName: rizeni.zapnuto ? "captions.bubble.fill" : "captions.bubble")
        }
        .menuBarExtraStyle(.window)

        Settings {
            NastaveniView(rizeni: rizeni)
        }
    }
}

/// Ladění bez UI: `PlayCaper --vzorky <složka>` přečte PNG výřezy (např. z prototypu)
/// a vypíše, co OCR vidí a co z toho výběr titulku nechá.
func zkusVzorky(_ slozka: String) {
    let soubory = (try? FileManager.default.contentsOfDirectory(atPath: slozka))?
        .filter { $0.hasSuffix(".png") }.sorted() ?? []
    for nazev in soubory {
        let url = URL(fileURLWithPath: slozka).appendingPathComponent(nazev)
        guard let zdroj = CGImageSourceCreateWithURL(url as CFURL, nil),
              let obraz = CGImageSourceCreateImageAtIndex(zdroj, 0, nil) else { continue }
        let (radky, ms) = Snimac.rozpoznej(obraz)
        let vybrane = VyberTitulku.vyber(radky).map(\.text)
        let zahozene = radky.map(\.text).filter { !vybrane.contains($0) }
        print("\(nazev) (\(Int(ms)) ms)  ✔ \(vybrane.joined(separator: " / "))" +
              (zahozene.isEmpty ? "" : "   ✘ \(zahozene.joined(separator: " / "))"))
    }
}

if let i = CommandLine.arguments.firstIndex(of: "--vzorky"), i + 1 < CommandLine.arguments.count {
    zkusVzorky(CommandLine.arguments[i + 1])
} else {
    PlayCaperApp.main()
}
