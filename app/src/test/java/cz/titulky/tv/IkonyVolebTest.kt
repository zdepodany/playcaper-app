package cz.titulky.tv

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Snímek voleb z testovacích dat: 32bit BMP (shora dolů) → ARGB pixely. */
internal class SnimekBmp(val w: Int, val h: Int, val px: IntArray) {
    companion object {
        fun nacti(f: File): SnimekBmp {
            val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val data = b.getInt(10); val w = b.getInt(18); val vyska = b.getInt(22)
            require(b.getShort(28).toInt() == 32) { "čekám 32bit BMP" }
            val h = abs(vyska)
            val px = IntArray(w * h)
            for (y in 0 until h) {
                val radek = if (vyska < 0) y else h - 1 - y
                for (x in 0 until w) px[radek * w + x] = b.getInt(data + (y * w + x) * 4) or (0xFF shl 24)
            }
            return SnimekBmp(w, h, px)
        }
        private fun abs(i: Int) = if (i < 0) -i else i
    }
}

/** Řádek popisu snímku: box čtečky + přečtený text. */
internal data class RadekSnimku(val l: Int, val t: Int, val r: Int, val b: Int, val text: String)

internal fun testovaciSnimky(): List<Triple<String, SnimekBmp, List<RadekSnimku>>> {
    val dir = File(IkonyVolebTest::class.java.classLoader!!.getResource("volby-ikony")!!.toURI())
    return dir.listFiles { f -> f.name.endsWith(".txt") }!!.sortedBy { it.name }.map { txt ->
        val radky = txt.readLines().drop(1).filter { it.isNotBlank() }.map { r ->
            val (box, text) = r.split("\t", limit = 2)
            val (l, t, rr, b) = box.split(",").map { it.toInt() }
            RadekSnimku(l, t, rr, b, text)
        }
        Triple(txt.name.removeSuffix(".txt"), SnimekBmp.nacti(File(dir, txt.name.removeSuffix(".txt") + ".bmp")), radky)
    }
}

class IkonyVolebTest {
    /** Skutečné výřezy žlutého rámečku z GTA VI (stream PS5, 192×180 px) – co je na nich vidět. */
    private val ocekavane = mapOf(
        "001559-784" to listOf(null, null), // přechodový snímek, kruh jen napůl → raději nic
        "001601-893" to listOf(Ikona.TROJUHELNIK, Ikona.KRIZEK, Ikona.RAMENNI),
        "001603-472" to listOf(Ikona.TROJUHELNIK, Ikona.KRIZEK, Ikona.RAMENNI),
        "001605-361" to listOf(Ikona.TROJUHELNIK, Ikona.KRIZEK, Ikona.RAMENNI),
        "001606-972" to listOf(Ikona.TROJUHELNIK, Ikona.KRIZEK, Ikona.RAMENNI),
        "001612-038" to listOf(Ikona.KRIZEK, Ikona.RAMENNI),
        "001613-649" to listOf(Ikona.KRIZEK, Ikona.RAMENNI),
        "001615-248" to listOf(null, null), // nápis ve scéně, žádné volby
        "001617-643" to listOf(Ikona.KRIZEK, Ikona.CTVERECEK, null), // L2 tu čtečka uřízla
        "001619-246" to listOf(Ikona.KRIZEK, Ikona.CTVERECEK, Ikona.RAMENNI),
        "001621-380" to listOf(Ikona.KRIZEK, Ikona.CTVERECEK, Ikona.RAMENNI),
    )

    @Test fun ikonyNaSnimcichZeHry() {
        val snimky = testovaciSnimky()
        assertEquals(ocekavane.keys, snimky.map { it.first }.toSet())
        for ((jmeno, s, radky) in snimky) {
            val zjistene = radky.map { IkonyVoleb.ikona(s.px, s.w, s.h, it.l, it.t, it.r, it.b) }
            assertEquals("$jmeno ${radky.map { it.text }} – " + radky.joinToString(" | ") { IkonyVoleb.popis(s.px, s.w, s.h, it.l, it.t, it.r, it.b) },
                ocekavane[jmeno], zjistene)
        }
    }

    @Test fun celySnimekNajednouDaStejneIkonyAJeRychly() {
        val (_, s, radky) = testovaciSnimky().first { it.first == "001619-246" }
        val ramecky = radky.map { intArrayOf(it.l, it.t, it.r, it.b) }
        repeat(3) { IkonyVoleb.ikony(s.px, s.w, s.h, ramecky) } // zahřátí JIT
        val od = System.nanoTime()
        val ikony = IkonyVoleb.ikony(s.px, s.w, s.h, ramecky)
        val ms = (System.nanoTime() - od) / 1_000_000
        println("RYCHLOST ikony 3 řádků: $ms ms")
        assertEquals(listOf(Ikona.KRIZEK, Ikona.CTVERECEK, Ikona.RAMENNI), ikony)
    }

    @Test fun ikonaZeZnaku() {
        assertEquals(Ikona.KRIZEK, Ikona.zeZnaku("✕"))
        assertEquals(null, Ikona.zeZnaku("X"))
    }
}
