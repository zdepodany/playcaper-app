import Darwin
import Foundation
import PlayCaperCore

/// Posílá titulky přijímači na TV (UDP, port 41989), jako `Propojeni.Vysilac` na Androidu:
/// při změně hned (dvakrát, kdyby se paket po Wi-Fi ztratil) a pak každou sekundu znovu.
final class Vysilac {
    private let fronta = DispatchQueue(label: "cz.playcaper.vysilac")
    private let sock: Int32
    private var cil: String?
    // Čísla začínají časem, ať po restartu aplikace přijímač nepovažuje nové zprávy za staré.
    private var cislo = Int64(Date().timeIntervalSince1970 * 1000)
    private var cisloVoleb = Int64(Date().timeIntervalSince1970 * 1000)
    private var text = ""
    private var doba: Int64 = 0
    private var gta: Bool
    private var casovac: DispatchSourceTimer?
    /// Poslední chyba odesílání (nil = v pořádku); volá se na hlavním vlákně.
    var naChybu: ((String?) -> Void)?
    private var posledniChyba: String?

    init(gta: Bool) {
        self.gta = gta
        sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        let t = DispatchSource.makeTimerSource(queue: fronta)
        t.schedule(deadline: .now() + 1, repeating: 1)
        t.setEventHandler { [weak self] in self?.posli() }
        t.resume()
        casovac = t
    }

    func nastavCil(_ ip: String?) {
        fronta.async { [self] in
            guard ip != cil else { return }
            if let stary = cil {
                cislo += 1; cisloVoleb += 1
                for z in Protokol.vycisteni(titulekId: cislo, volbyId: cisloVoleb) { odesli(z, stary) }
            }
            cil = ip
            text = ""; doba = 0
            cislo += 1
            posli()
        }
    }

    func zmena(_ t: String, doba d: Int64) {
        fronta.async { [self] in
            guard t != text || d != doba else { return }
            text = t; doba = d
            cislo += 1
            posli()
            fronta.asyncAfter(deadline: .now() + 0.06) { self.posli() }
        }
    }

    func nastavGta(_ g: Bool) {
        fronta.async { [self] in
            guard g != gta else { return }
            gta = g
            cislo += 1
            posli()
        }
    }

    private func posli() {
        guard let cil else { return }
        odesli(Protokol.titulek(cislo: cislo, doba: doba, text: text, gta: gta), cil)
    }

    private func odesli(_ zprava: String, _ ip: String) {
        var adresa = sockaddr_in()
        adresa.sin_family = sa_family_t(AF_INET)
        adresa.sin_port = Protokol.portTitulky.bigEndian
        guard inet_pton(AF_INET, ip, &adresa.sin_addr) == 1 else { hlasChybu("neplatná IP \(ip)"); return }
        let data = Array(zprava.utf8)
        let r = withUnsafePointer(to: &adresa) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                sendto(sock, data, data.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        hlasChybu(r < 0 ? String(cString: strerror(errno)) : nil)
    }

    private func hlasChybu(_ chyba: String?) {
        guard chyba != posledniChyba else { return }
        posledniChyba = chyba
        if let chyba { Zaznam.zapis("Odesílání na TV selhalo: \(chyba)") }
        DispatchQueue.main.async { self.naChybu?(chyba) }
    }
}

/// Poslouchá ohlášení přijímačů `PLAYCAPTION_TV` (broadcast každé 2 s na port 41988).
final class HledacTelevizi {
    /// Volá se na hlavním vlákně při každém ohlášení.
    var naTelevizi: ((Protokol.Televize) -> Void)?
    private var vlakno: Thread?

    func spust() {
        guard vlakno == nil else { return }
        let v = Thread { [weak self] in self?.poslouchej() }
        v.name = "cz.playcaper.hledani"
        vlakno = v
        v.start()
    }

    private func poslouchej() {
        let s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        guard s >= 0 else { Zaznam.zapis("Hledání TV: socket selhal"); return }
        var ano: Int32 = 1
        setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &ano, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(s, SOL_SOCKET, SO_REUSEPORT, &ano, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(s, SOL_SOCKET, SO_BROADCAST, &ano, socklen_t(MemoryLayout<Int32>.size))
        var adresa = sockaddr_in()
        adresa.sin_family = sa_family_t(AF_INET)
        adresa.sin_port = Protokol.portHledani.bigEndian
        adresa.sin_addr.s_addr = INADDR_ANY
        let navazano = withUnsafePointer(to: &adresa) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(s, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
        }
        guard navazano == 0 else {
            Zaznam.zapis("Hledání TV: port \(Protokol.portHledani) obsazený (\(String(cString: strerror(errno))))")
            close(s)
            return
        }
        var buf = [UInt8](repeating: 0, count: 512)
        while true {
            var od = sockaddr_in()
            var delka = socklen_t(MemoryLayout<sockaddr_in>.size)
            let n = withUnsafeMutablePointer(to: &od) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { recvfrom(s, &buf, buf.count, 0, $0, &delka) }
            }
            guard n > 0 else { continue }
            var ipBuf = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            inet_ntop(AF_INET, &od.sin_addr, &ipBuf, socklen_t(INET_ADDRSTRLEN))
            let ip = String(cString: ipBuf)
            guard let zprava = String(bytes: buf[0..<n], encoding: .utf8),
                  let tv = Protokol.prectiOhlaseni(zprava, ip: ip) else { continue }
            DispatchQueue.main.async { self.naTelevizi?(tv) }
        }
    }
}
