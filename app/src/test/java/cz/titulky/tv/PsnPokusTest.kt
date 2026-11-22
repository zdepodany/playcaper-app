package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executor

class PsnPokusTest {
    private val id = "AQIDBAUGBwg="
    private val ulohy = mutableListOf<Runnable>()
    private val spoustec = Executor { ulohy.add(it) }
    private fun dokonci() = ulohy.removeAt(0).run()

    @Test fun uspechAzPoOvereniPlatnehoIdAZapisu() {
        var ulozeno: String? = null
        lateinit var p: PsnPokus
        p = PsnPokus({ id }, {
            assertEquals(PsnPokus.Stav.OVERUJE, p.stav)
            ulozeno = it; true
        }, spoustec)
        assertFalse(p.lzeParovat(false))
        p.prihlas("kod")
        assertEquals(PsnPokus.Stav.OVERUJE, p.stav)
        assertNull(ulozeno)
        assertFalse(p.lzeParovat(true))
        dokonci()
        assertEquals(id, ulozeno)
        assertEquals(PsnPokus.Stav.HOTOVO, p.stav)
        assertTrue(p.lzeParovat(true))
    }

    @Test fun neplatneAccountIdSeNeulozi() {
        for (neplatne in listOf("", "neplatne!", "AQIDBA==", "AQIDBAUGBwgJCg==")) {
            val p = PsnPokus({ neplatne }, { fail("Neplatné ID nesmí do úložiště"); true }, spoustec)
            p.prihlas("kod")
            dokonci()
            assertEquals(PsnPokus.Stav.CHYBA, p.stav)
            assertEquals("psnOdpoved", p.chyba)
            assertFalse(p.lzeParovat(true))
        }
    }

    @Test fun selhaniZapisuNeniUspech() {
        val p = PsnPokus({ id }, { false }, spoustec)
        p.prihlas("kod"); dokonci()
        assertEquals(PsnPokus.Stav.CHYBA, p.stav)
        assertEquals("psnUlozeni", p.chyba)
    }

    @Test fun duvodyChybNeztratiFaziAniNevyzradiKod() {
        val chyby = listOf(
            PsnHttpChyba("token", 400) to "psnKod",
            PsnHttpChyba("info", 400) to "psnSony",
            PsnHttpChyba("token", 401) to "psnSony",
            PsnHttpChyba("info", 503) to "psnSony",
            PsnHttpChyba("token", 429) to "psnLimit",
            SocketTimeoutException("sensitive-token") to "psnCas",
            UnknownHostException("sensitive-token") to "psnSit",
            IllegalArgumentException("sensitive-token") to "psnOdpoved"
        )
        for ((e, klic) in chyby) {
            val p = PsnPokus({ throw e }, { fail("Při chybě se nesmí ukládat"); true }, spoustec)
            p.prihlas("sensitive-code"); dokonci()
            assertEquals(klic, p.chyba)
            assertEquals((e as? PsnHttpChyba)?.status, p.http)
            val popis = p.popisChyby("cs")
            assertFalse(popis.contains("sensitive"))
            assertFalse(popis.contains("http://"))
            assertFalse(p.lzeParovat(true))
        }
    }

    @Test fun opakovaneOdeslaniAniObnovaStrankyNevymeniKodZnovu() {
        var vymen = 0
        val p = PsnPokus({ vymen++; id }, { true }, spoustec)
        p.prihlas("kod"); p.prihlas("kod"); p.prihlas("jiny-behem-overovani")
        assertEquals(1, ulohy.size)
        dokonci()
        p.prihlas("kod")
        assertEquals(0, ulohy.size)
        assertEquals(1, vymen)
        assertEquals(PsnPokus.Stav.HOTOVO, p.stav)
    }

    @Test fun poOdmitnutiZustaneStaryUcetANovyKodLzeZkusit() {
        var ulozeno = "puvodni-ucet"
        val p = PsnPokus({ if (it == "spatny") throw PsnHttpChyba("token", 400) else id },
            { ulozeno = it; true }, spoustec)
        p.prihlas("spatny"); dokonci()
        val chybaPredOpakovanim = p.vysledek
        p.prihlas("spatny")
        assertTrue(ulohy.isEmpty())
        assertEquals("puvodni-ucet", ulozeno)
        p.prihlas("novy")
        assertEquals(PsnPokus.Stav.OVERUJE, p.stav)
        assertEquals("psnKod", chybaPredOpakovanim.chyba)
        assertEquals(400, chybaPredOpakovanim.http)
        dokonci()
        assertEquals(id, ulozeno)
        assertEquals(PsnPokus.Stav.HOTOVO, p.stav)
        assertEquals("", p.chyba)
        assertNull(p.http)
    }

    @Test fun zrusenyPokusAniJehoPozdniOdpovedNeprepisiNovyUcet() {
        var ulozeno = "puvodni-ucet"
        val stary = PsnPokus({ "CAcGBQQDAgE=" }, { ulozeno = it; true }, spoustec)
        val novy = PsnPokus({ id }, { ulozeno = it; true }, spoustec)
        stary.prihlas("stary")
        stary.zrus()
        novy.prihlas("novy")
        ulohy.removeAt(1).run()
        dokonci()
        stary.prihlas("jeste-jeden")
        assertTrue(ulohy.isEmpty())
        assertEquals(id, ulozeno)
        assertEquals(PsnPokus.Stav.HOTOVO, novy.stav)
    }

    @Test fun nezSpustiSitMuzeSePrihlaseniZrusit() {
        var ulozeno = false
        val p = PsnPokus({ fail("Zrušená stránka nesmí spotřebovat kód"); id }, { ulozeno = true; true }, spoustec)
        p.prihlas("kod"); p.chybaStranky(); dokonci()
        assertFalse(ulozeno)
        assertEquals(PsnPokus.Stav.CHYBA, p.stav)
        assertEquals("psnStranka", p.chyba)
    }

    @Test fun zruseniBehemSiteZabraniZapisuPozdniOdpovedi() {
        lateinit var p: PsnPokus
        p = PsnPokus({ p.zrus(); id }, { fail("Pozdní odpověď zrušeného přihlášení"); true }, spoustec)
        p.prihlas("kod"); dokonci()
        assertNotEquals(PsnPokus.Stav.HOTOVO, p.stav)
    }

    @Test fun noveTextyJsouVeVsechJazycich() {
        val klice = listOf("psnOveruji", "psnKod", "psnSony", "psnLimit", "psnCas", "psnSit", "psnOdpoved",
            "psnUlozeni", "psnStranka", "qrVProhlizeci")
        for (jazyk in Texty.JAZYKY) for (klic in klice) {
            assertNotEquals(klic, Texty.t(jazyk.kod, klic))
        }
    }
}
