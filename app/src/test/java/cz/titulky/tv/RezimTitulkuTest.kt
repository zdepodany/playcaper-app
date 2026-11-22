package cz.titulky.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RezimTitulkuTest {
    @Test
    fun rezimZUlozenychVoleb() {
        assertEquals(RezimTitulku.STANDARD, RezimTitulku.z(pribehovy = false, gta = false))
        assertEquals(RezimTitulku.PRIBEH, RezimTitulku.z(pribehovy = true, gta = false))
        assertEquals(RezimTitulku.GTA, RezimTitulku.z(pribehovy = false, gta = true))
        // starší build: obojí zapnuté → GTA (příběhová fronta by GTA volby nezobrazila)
        assertEquals(RezimTitulku.GTA, RezimTitulku.z(pribehovy = true, gta = true))
    }

    @Test
    fun gtaVypinaPribehAZapinaVolby() {
        val v = RezimTitulku.GTA.volby()
        assertFalse(v.pribehovy)
        assertTrue(v.gta)
        assertTrue(v.volbyZapnout)
    }

    @Test
    fun ostatniRezimyGtaVypnou() {
        for (r in listOf(RezimTitulku.STANDARD, RezimTitulku.PRIBEH)) {
            assertFalse(r.volby().gta)
            assertFalse(r.volby().volbyZapnout)
        }
        assertTrue(RezimTitulku.PRIBEH.volby().pribehovy)
    }

    @Test
    fun zpatkyNaStejnyRezim() {
        for (r in RezimTitulku.values()) assertEquals(r, RezimTitulku.z(r.volby().pribehovy, r.volby().gta))
    }
}
