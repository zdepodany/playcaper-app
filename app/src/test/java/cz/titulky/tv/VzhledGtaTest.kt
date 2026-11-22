package cz.titulky.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class VzhledGtaTest {
    @Test
    fun znackaJenPriZapnuteGta() {
        assertEquals("⁣Lucia: Jedeme", Propojeni.sVzhledem("Lucia: Jedeme", gta = true))
        assertEquals("Lucia: Jedeme", Propojeni.sVzhledem("Lucia: Jedeme", gta = false))
        // prázdný titulek = smazat; nesmí se z něj stát neviditelný „neprázdný“ text
        assertEquals("", Propojeni.sVzhledem("", gta = true))
    }

    @Test
    fun androidTvPrijimacZnackuOdstrani() {
        assertEquals("Jedeme", Propojeni.bezVzhledu("⁣Jedeme"))
        val token = "a".repeat(32) + ".1.0.0"
        val paket = PribehProtokol.titulek(token, Propojeni.sVzhledem("Jason: Pojď", gta = true), false,
            floatArrayOf(.5f, .9f, .8f, 1f), intArrayOf())
        assertEquals("Jason: Pojď", PribehProtokol.prijmi(paket)?.titulek?.text)
    }
}
