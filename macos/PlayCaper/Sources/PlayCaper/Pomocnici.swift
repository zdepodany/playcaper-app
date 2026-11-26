import Foundation
import IOKit.pwr_mgt
import Security

/// DeepL klíče v Klíčence (jeden klíč na řádek).
enum Klicenka {
    private static let sluzba = "cz.playcaper.mac.deepl"

    static func nactiKlice() -> [String] {
        let dotaz: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: sluzba,
            kSecAttrAccount as String: "klice", kSecReturnData as String: true,
        ]
        var vysledek: AnyObject?
        guard SecItemCopyMatching(dotaz as CFDictionary, &vysledek) == errSecSuccess,
              let data = vysledek as? Data, let text = String(data: data, encoding: .utf8) else { return [] }
        return rozdel(text)
    }

    static func ulozKlice(_ klice: [String]) {
        let zaklad: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: sluzba,
            kSecAttrAccount as String: "klice",
        ]
        SecItemDelete(zaklad as CFDictionary)
        guard !klice.isEmpty else { return }
        var novy = zaklad
        novy[kSecValueData as String] = Data(klice.joined(separator: "\n").utf8)
        SecItemAdd(novy as CFDictionary, nil)
    }

    static func rozdel(_ text: String) -> [String] {
        text.split(whereSeparator: { $0.isNewline || $0 == "," || $0 == " " })
            .map { String($0) }.filter { !$0.isEmpty }
    }
}

/// Záznam do ~/Library/Logs/PlayCaper/playcaper.log (jako `zapis` na Androidu).
enum Zaznam {
    static let soubor: URL = {
        let slozka = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/Logs/PlayCaper")
        try? FileManager.default.createDirectory(at: slozka, withIntermediateDirectories: true)
        return slozka.appendingPathComponent("playcaper.log")
    }()
    private static let fronta = DispatchQueue(label: "cz.playcaper.zaznam")
    private static let format: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss.SSS"
        return f
    }()

    static func zapis(_ radek: String) {
        let cas = Date()
        fronta.async {
            let text = "\(format.string(from: cas))  \(radek)\n"
            print(text, terminator: "")
            if let h = try? FileHandle(forWritingTo: soubor) {
                h.seekToEndOfFile()
                h.write(Data(text.utf8))
                try? h.close()
            } else {
                try? Data(text.utf8).write(to: soubor)
            }
        }
    }
}

/// Brání zhasnutí displeje, dokud se čte (se zhasnutým displejem obraz okna zamrzne, PC-009).
final class ZamekDispleje {
    private var id: IOPMAssertionID = 0
    private var drzi = false

    func drz(_ ano: Bool) {
        if ano && !drzi {
            drzi = IOPMAssertionCreateWithName(kIOPMAssertionTypePreventUserIdleDisplaySleep as CFString,
                                                IOPMAssertionLevel(kIOPMAssertionLevelOn),
                                                "PlayCaper čte titulky z okna Remote Play" as CFString, &id) == kIOReturnSuccess
        } else if !ano && drzi {
            IOPMAssertionRelease(id)
            drzi = false
        }
    }
}
