package cz.titulky.tv

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ikony tlačítek u voleb (GTA VI): bílý kruh s tmavým symbolem ✕ △ ○ □,
 * u jména NPC tmavý kruh s bílým okrajem a nápisem L2.
 *
 * Čtečka textu je nepozná (čte je jako "O", "(", "%"), proto se hledají v obraze:
 * 1) v pásu řádku najít kruh – obvod světlý, hned za ním tmavý obrys,
 * 2) tmavé pixely uvnitř porovnat se šablonami symbolů.
 * Čistý Kotlin bez Androidu, ať jde testovat na skutečných snímcích.
 */
enum class Ikona(val znak: String) {
    KRIZEK("✕"), TROJUHELNIK("△"), KROUZEK("○"), CTVERECEK("□"), RAMENNI("L2");

    companion object {
        fun zeZnaku(z: String) = values().firstOrNull { it.znak == z }
    }
}

object IkonyVoleb {
    /** Mřížka, na kterou se vnitřek kruhu převzorkuje (ikona má ve streamu jen ~18 px). */
    private const val N = 24
    /** Jak daleko od středu (podíl poloměru) se symbol vzorkuje – symboly sahají skoro k okraji kruhu. */
    private const val DOSAH = 0.86f

    private fun jas(c: Int) = (299 * ((c shr 16) and 255) + 587 * ((c shr 8) and 255) + 114 * (c and 255)) / 1000

    /** Jas všech pixelů – spočítá se jednou na snímek (hledání kruhu ho čte statisíckrát). */
    fun jasy(px: IntArray): IntArray = IntArray(px.size) { jas(px[it]) }

    /** Jas v neceločíselném bodě (bilineárně), mimo obraz = 0. [px] jsou už jasy. */
    private fun jasV(px: IntArray, w: Int, h: Int, x: Float, y: Float): Float {
        val x0 = x.toInt(); val y0 = y.toInt()
        if (x0 < 0 || y0 < 0 || x0 + 1 >= w || y0 + 1 >= h) return 0f
        val fx = x - x0; val fy = y - y0
        val i = y0 * w + x0
        val a = px[i]; val b = px[i + 1]; val c = px[i + w]; val d = px[i + w + 1]
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
    }

    private fun prumerNaKruhu(px: IntArray, w: Int, h: Int, cx: Float, cy: Float, r: Float): Float {
        var s = 0f
        val n = 24
        for (i in 0 until n) {
            val u = (2 * Math.PI * i / n).toFloat()
            s += jasV(px, w, h, cx + r * cos(u), cy + r * sin(u))
        }
        return s / n
    }

    /** Nalezený kruh ikony (pro testy a ladění). */
    data class Kruh(val cx: Float, val cy: Float, val r: Float, val skore: Float)

    /**
     * Najde kruh ikony v pásu řádku (souřadnice řádku z čtečky, v pixelech [px]).
     * Ikona je za textem vpravo; box čtečky ji někdy zahrnuje, jindy ne.
     */
    private fun najdiKruh(px: IntArray, w: Int, h: Int, left: Int, top: Int, right: Int, bottom: Int): Kruh? {
        val vyska = (bottom - top).coerceAtLeast(6)
        val stredY = (top + bottom) / 2f
        val odX = maxOf(left + vyska / 2, (right - vyska * 2.5f).toInt()).toFloat()
        val doX = minOf(w - 4, (right + vyska * 2.5f).toInt()).toFloat()
        val rMin = maxOf(5f, vyska * 0.35f); val rMax = vyska * 0.85f
        // 1) hrubě po 1 px, 2) jemně po 0,25 px kolem nejlepšího
        val hruby = hledej(px, w, h, odX, doX, stredY - vyska * 0.25f, stredY + vyska * 0.25f, rMin, rMax, 1f, 0.5f) ?: return null
        val jemny = hledej(px, w, h, hruby.cx - 1f, hruby.cx + 1f, hruby.cy - 1f, hruby.cy + 1f,
            hruby.r - 1f, hruby.r + 1f, 0.25f, 0.25f) ?: hruby
        return jemny.takeIf { it.skore > 60f }
    }

    private fun hledej(px: IntArray, w: Int, h: Int, odX: Float, doX: Float, odY: Float, doY: Float,
                       rOd: Float, rDo: Float, krok: Float, krokR: Float): Kruh? {
        var nej: Kruh? = null
        var cy = odY
        while (cy <= doY) {
            var cx = odX
            while (cx <= doX) {
                var r = rOd
                while (r <= rDo) {
                    val obvod = prumerNaKruhu(px, w, h, cx, cy, r - 1f)
                    val vne = prumerNaKruhu(px, w, h, cx, cy, r + 1.5f)
                    val skore = obvod - vne
                    if (obvod > 150f && (nej == null || skore > nej.skore)) nej = Kruh(cx, cy, r, skore)
                    r += krokR
                }
                cx += krok
            }
            cy += krok
        }
        return nej
    }

    /** Tmavé body uvnitř kruhu na mřížce N×N v souřadnicích −1..1 (vztaženo k poloměru). */
    private fun tmaveUvnitr(px: IntArray, w: Int, h: Int, k: Kruh): Pair<BooleanArray, BooleanArray> {
        val hodnoty = FloatArray(N * N)
        val uvnitr = BooleanArray(N * N)
        var max = 0f
        for (j in 0 until N) for (i in 0 until N) {
            val u = (i + 0.5f) / N * 2 - 1; val v = (j + 0.5f) / N * 2 - 1
            if (u * u + v * v > DOSAH * DOSAH) continue
            uvnitr[j * N + i] = true
            val l = jasV(px, w, h, k.cx + u * k.r, k.cy + v * k.r)
            hodnoty[j * N + i] = l
            if (l > max) max = l
        }
        val obvod = prumerNaKruhu(px, w, h, k.cx, k.cy, k.r - 1f)
        val prah = minOf(max, obvod) * 0.6f
        val tma = BooleanArray(N * N) { uvnitr[it] && hodnoty[it] < prah }
        return tma to uvnitr
    }

    // Šablony podle skutečných snímků ze streamu (symbol vyplní skoro celý bílý kruh, tahy ~0,25 poloměru).
    private fun sablona(ikona: Ikona, u: Float, v: Float): Boolean = when (ikona) {
        Ikona.KRIZEK -> abs(abs(u) - abs(v)) < 0.22f && maxOf(abs(u), abs(v)) < 0.64f
        Ikona.CTVERECEK -> maxOf(abs(u), abs(v)).let { it in 0.38f..0.68f }
        Ikona.KROUZEK -> sqrt(u * u + v * v).let { it in 0.40f..0.68f }
        Ikona.TROJUHELNIK -> {
            val vrchol = -0.72f; val zaklad = 0.55f
            val polovina = (v - vrchol) / (zaklad - vrchol) * 0.66f
            v in (vrchol - 0.05f)..(zaklad + 0.06f) &&
                (abs(abs(u) - polovina) < 0.16f || (v > zaklad - 0.2f && abs(u) <= polovina + 0.08f))
        }
        // L2/R2: nalezený kruh je vnější bílý okraj, uvnitř tmavé kolečko → tma jen v prstenci u okraje
        Ikona.RAMENNI -> sqrt(u * u + v * v) > 0.72f
    }

    /** Rozpozná symbol v nalezeném kruhu; null = nejisté. */
    private fun rozpoznej(px: IntArray, w: Int, h: Int, k: Kruh, vyskaRadku: Int): Ikona? {
        val (tma, uvnitr) = tmaveUvnitr(px, w, h, k)
        val pocetUvnitr = uvnitr.count { it }.coerceAtLeast(1)
        val podilTmy = tma.count { it }.toFloat() / pocetUvnitr
        if (podilTmy < 0.12f || podilTmy > 0.85f) return null
        val skore = Ikona.values().associateWith { ik ->
            var prunik = 0; var sjednoceni = 0
            for (j in 0 until N) for (i in 0 until N) {
                if (!uvnitr[j * N + i]) continue
                val u = (i + 0.5f) / N * 2 - 1; val v = (j + 0.5f) / N * 2 - 1
                val s = sablona(ik, u, v); val t = tma[j * N + i]
                if (s && t) prunik++
                if (s || t) sjednoceni++
            }
            if (sjednoceni == 0) 0f else prunik.toFloat() / sjednoceni
        }
        val serazene = skore.entries.sortedByDescending { it.value }
        val prvni = serazene[0]; val druhy = serazene[1]
        posledniSkore = serazene.joinToString(" ") { "%s=%.2f".format(it.key.znak, it.value) }
        // na snímcích ze hry: správný symbol 0,43–0,97, odstup od druhého ≥ 0,07 (□ vs ○ je nejtěsnější)
        return if (prvni.value >= 0.4f && prvni.value - druhy.value >= 0.06f) prvni.key else null
    }

    @Volatile internal var posledniSkore = ""

    /** Ikona u řádku voleb, nebo null (nenalezena / nejistá). [px] = ARGB pixely. */
    fun ikona(px: IntArray, w: Int, h: Int, left: Int, top: Int, right: Int, bottom: Int): Ikona? =
        ikonaZJasu(jasy(px), w, h, left, top, right, bottom)

    /** Ikony všech řádků jednoho snímku (řádek = left, top, right, bottom); jas se spočítá jen jednou. */
    fun ikony(px: IntArray, w: Int, h: Int, radky: List<IntArray>): List<Ikona?> {
        val j = jasy(px)
        return radky.map { r -> runCatching { ikonaZJasu(j, w, h, r[0], r[1], r[2], r[3]) }.getOrNull() }
    }

    private fun ikonaZJasu(j: IntArray, w: Int, h: Int, left: Int, top: Int, right: Int, bottom: Int): Ikona? {
        val k = najdiKruh(j, w, h, left, top, right, bottom) ?: return null
        return rozpoznej(j, w, h, k, bottom - top)
    }

    /** Ladění: skóre šablon (pro testy). */
    internal fun popis(argb: IntArray, w: Int, h: Int, left: Int, top: Int, right: Int, bottom: Int): String {
        val px = jasy(argb)
        val k = najdiKruh(px, w, h, left, top, right, bottom) ?: return "kruh nenalezen"
        val (tma, uvnitr) = tmaveUvnitr(px, w, h, k)
        val podil = tma.count { it }.toFloat() / uvnitr.count { it }.coerceAtLeast(1)
        return "kruh cx=%.1f cy=%.1f r=%.1f skore=%.0f tma=%.2f → %s".format(k.cx, k.cy, k.r, k.skore, podil, rozpoznej(px, w, h, k, bottom - top)) + "  [" + posledniSkore + "]"
    }
}
