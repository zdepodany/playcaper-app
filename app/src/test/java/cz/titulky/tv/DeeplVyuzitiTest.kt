package cz.titulky.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeeplVyuzitiTest {

    @Test
    fun prectePouzitiALimit() {
        val stav = Prekladac.prectiDeeplVyuziti("""{"character_count":123456,"character_limit":500000}""")
        assertEquals(Prekladac.DeeplVyuziti.Ok(123456, 500000), stav)
        stav as Prekladac.DeeplVyuziti.Ok
        assertEquals(24, stav.procenta)
        assertFalse(stav.vycerpano)
    }

    @Test
    fun vycerpanyLimit() {
        val stav = Prekladac.prectiDeeplVyuziti("""{"character_count":500000,"character_limit":500000}""")
        stav as Prekladac.DeeplVyuziti.Ok
        assertEquals(100, stav.procenta)
        assertTrue(stav.vycerpano)
    }

    @Test
    fun nulovyLimitNedeliNulou() {
        val stav = Prekladac.prectiDeeplVyuziti("""{"character_count":5,"character_limit":0}""")
        stav as Prekladac.DeeplVyuziti.Ok
        assertEquals(0, stav.procenta)
        assertFalse(stav.vycerpano)
    }

    @Test
    fun soucetPresKliceBezNefunkcnich() {
        val soucet = Prekladac.soucetDeepl(listOf(
            Prekladac.DeeplVyuziti.Ok(500_000, 500_000),
            Prekladac.DeeplVyuziti.SpatnyKlic,
            Prekladac.DeeplVyuziti.Ok(100_000, 500_000),
        ))
        assertEquals(Prekladac.DeeplVyuziti.Ok(600_000, 1_000_000), soucet)
    }

    @Test
    fun soucetPreteceniKliceNepocitaNadLimit() {
        val soucet = Prekladac.soucetDeepl(listOf(Prekladac.DeeplVyuziti.Ok(510_000, 500_000), Prekladac.DeeplVyuziti.Ok(0, 500_000)))
        assertEquals(Prekladac.DeeplVyuziti.Ok(500_000, 1_000_000), soucet)
    }

    @Test
    fun soucetBezFunkcnihoKliceJeNull() {
        assertEquals(null, Prekladac.soucetDeepl(listOf(Prekladac.DeeplVyuziti.SpatnyKlic, Prekladac.DeeplVyuziti.Chyba)))
        assertEquals(null, Prekladac.soucetDeepl(emptyList()))
    }

    @Test
    fun cisteniKlicu() {
        assertEquals(listOf("a:fx", "b:fx"), Nastaveni.vycistiDeeplKlice("  a:fx \n\n b:fx\na:fx\n"))
        assertEquals(5, Nastaveni.vycistiDeeplKlice("1\n2\n3\n4\n5\n6").size)
        assertEquals(emptyList<String>(), Nastaveni.vycistiDeeplKlice(""))
    }

    @Test
    fun chybejiciPoleNeboNesmyslJeChyba() {
        assertEquals(Prekladac.DeeplVyuziti.Chyba, Prekladac.prectiDeeplVyuziti("""{"character_count":1}"""))
        assertEquals(Prekladac.DeeplVyuziti.Chyba, Prekladac.prectiDeeplVyuziti("<html>"))
    }
}
