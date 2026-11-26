import AppKit
import PlayCaperCore
import SwiftUI

/// Okno „Oblast čtení“: plocha 16:9 představuje obraz hry, rámeček se táhne myší, za rohy a hrany
/// se mění velikost. Kde přesně leží, ukazuje stejný rámeček na TV nad hrou (`PCO1`);
/// po puštění se oblast hned použije.
struct OblastOkno: View {
    @ObservedObject var rizeni: Rizeni
    @ObservedObject private var ladeni: LadeniOcr
    @AppStorage("oblastNaTv") private var naTv = true
    /// Rámeček během tažení (nil = netáhne se, platí `rizeni.oblast`).
    @State private var navrh: CGRect?
    @State private var zacatek: CGRect?

    init(rizeni: Rizeni) {
        self.rizeni = rizeni
        _ladeni = ObservedObject(wrappedValue: rizeni.ladeni)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            platno
                .aspectRatio(16.0 / 9.0, contentMode: .fit)
                .frame(minWidth: 560, minHeight: 315)

            HStack {
                Text(popisOblasti).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                Spacer()
                Toggle("Ukazovat rámeček na TV", isOn: $naTv)
                Button("Výchozí GTA VI") {
                    rizeni.oblast = Rizeni.oblastGta
                    if naTv { rizeni.ukazOblastNaTv(Rizeni.oblastGta, vynutit: true) }
                }
            }
            Text("Plocha odpovídá obrazu hry. Rámeček táhni myší, za rohy a hrany měníš velikost; na TV se při tom ukazuje nad hrou. Zelené jsou řádky, které se berou jako titulek, oranžové se zahazují (nápisy ve scéně).")
                .font(.caption).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .frame(minWidth: 600)
    }

    private var popisOblasti: String {
        let o = navrh ?? rizeni.oblast
        return String(format: "x %.2f  y %.2f  šířka %.2f  výška %.2f", o.minX, o.minY, o.width, o.height)
    }

    private var platno: some View {
        GeometryReader { g in
            let vel = g.size
            let o = navrh ?? rizeni.oblast
            let r = CGRect(x: o.minX * vel.width, y: o.minY * vel.height,
                           width: o.width * vel.width, height: o.height * vel.height)
            ZStack(alignment: .topLeading) {
                pozadi

                // ztmavení mimo oblast
                Path { p in
                    p.addRect(CGRect(origin: .zero, size: vel))
                    p.addRect(r)
                }
                .fill(Color.black.opacity(0.45), style: FillStyle(eoFill: true))
                .allowsHitTesting(false)

                // řádky z posledního OCR (při tažení neplatí, patří ke staré oblasti)
                if navrh == nil {
                    ForEach(Array(ladeni.radky.enumerated()), id: \.offset) { _, radek in
                        Rectangle()
                            .stroke(radek.vybrany ? Color.green : Color.orange, lineWidth: 1.5)
                            .frame(width: radek.ramec.width * vel.width, height: radek.ramec.height * vel.height)
                            .position(x: radek.ramec.midX * vel.width, y: radek.ramec.midY * vel.height)
                            .allowsHitTesting(false)
                    }
                }

                Rectangle()
                    .fill(Color.white.opacity(0.001))
                    .overlay(Rectangle().strokeBorder(Color.blue, lineWidth: 2))
                    .frame(width: r.width, height: r.height)
                    .contentShape(Rectangle())
                    .pointerStyle(.grabIdle)
                    .gesture(tazeni(.cele, vel))
                    .position(x: r.midX, y: r.midY)

                ForEach(OblastCteni.Uchyt.allCases.filter { $0 != .cele }, id: \.self) { u in
                    Rectangle()
                        .fill(Color.white)
                        .overlay(Rectangle().strokeBorder(Color.blue, lineWidth: 1.5))
                        .frame(width: 11, height: 11)
                        .contentShape(Rectangle().inset(by: -5))
                        .pointerStyle(.frameResize(position: Self.pozice(u)))
                        .gesture(tazeni(u, vel))
                        .position(x: r.minX + u.poloha.x * r.width, y: r.minY + u.poloha.y * r.height)
                }
            }
            .frame(width: vel.width, height: vel.height)
        }
        .clipShape(Rectangle())
    }

    private var pozadi: some View {
        Rectangle().fill(Color.gray.opacity(0.25))
    }

    private func tazeni(_ u: OblastCteni.Uchyt, _ vel: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 1)
            .onChanged { h in
                let start = zacatek ?? rizeni.oblast
                if zacatek == nil { zacatek = start }
                let n = OblastCteni.uprav(start, uchyt: u, dx: h.translation.width / vel.width,
                                          dy: h.translation.height / vel.height)
                navrh = n
                if naTv { rizeni.ukazOblastNaTv(n) }
            }
            .onEnded { _ in
                if let n = navrh {
                    rizeni.oblast = n
                    if naTv { rizeni.ukazOblastNaTv(n, vynutit: true) }
                }
                navrh = nil
                zacatek = nil
            }
    }

    private static func pozice(_ u: OblastCteni.Uchyt) -> FrameResizePosition {
        switch u {
        case .vlevoNahore: return .topLeading
        case .nahore: return .top
        case .vpravoNahore: return .topTrailing
        case .vpravo: return .trailing
        case .vpravoDole: return .bottomTrailing
        case .dole: return .bottom
        case .vlevoDole: return .bottomLeading
        case .vlevo, .cele: return .leading
        }
    }
}
