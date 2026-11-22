package cz.titulky.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PametPrekladuTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Záznam 22. 11. 2026: stejné věty přečtené OCR jinak šly přes DeepL znovu. */
    @Test fun variantyZeZaznamuMajiStejnyKlic() {
        val k = PametPrekladu.Companion::klic
        assertEquals(
            k("Get a nice suit and go door to door until someone gives you a Proper job."),
            k("Get a nice suit and go door to door until someone gives you a proper job."))
        val zaklad = k("You got less time than you think to turn your life around, kid.")
        assertEquals(zaklad, k("You got less time than you think to turn your life around. kid."))
        assertEquals(zaklad, k("You got less time than you think to turn your life aroùnd, kid."))
        assertEquals(zaklad, k("You got less time than you think to turn your life around, kid"))
        assertEquals(k("I don't give out cash"), k("I don’t give out cash!"))
        assertEquals(k("\"Fine,\" he said."), k("Fine he said"))
    }

    @Test fun otaznikSeNeslucujeSOznamenim() {
        assertNotEquals(PametPrekladu.klic("You're leaving?"), PametPrekladu.klic("You're leaving."))
        assertEquals(PametPrekladu.klic("What's that tell you?"), PametPrekladu.klic("What's that tell you ?"))
    }

    @Test fun preziteRestartuAJazykyOddelene() {
        val slozka = tmp.newFolder("preklady")
        PametPrekladu(slozka).apply {
            prepniJazyk("cs")
            put("Hello there.", "Ahoj.")
            prepniJazyk("sk")
            put("Hello there.", "Ahoj tam.")
            zavri()
        }
        Thread.sleep(300)
        val znovu = PametPrekladu(slozka).apply { prepniJazyk("cs") }
        assertEquals("Ahoj.", cekej { znovu.get("hello there") })
        znovu.prepniJazyk("sk")
        assertEquals("Ahoj tam.", cekej { znovu.get("Hello there!") })
        znovu.zavri()
    }

    @Test fun nejvysMaxVetNejstarsiVypadnou() {
        val p = PametPrekladu(null, max = 3).apply { prepniJazyk("cs") }
        p.put("one", "jedna"); p.put("two", "dva"); p.put("three", "tři")
        p.get("one") // použitá → zůstane
        p.put("four", "čtyři")
        assertEquals(3, p.velikost())
        assertNull(p.get("two"))
        assertEquals("jedna", p.get("one"))
        p.zavri()
    }

    @Test fun poskozenySouborNevadi() {
        val slozka = tmp.newFolder("preklady")
        java.io.File(slozka, "preklady-cs.json").writeText("{nesmysl")
        val p = PametPrekladu(slozka).apply { prepniJazyk("cs") }
        Thread.sleep(200)
        assertNull(p.get("Hello"))
        p.put("Hello", "Ahoj")
        assertEquals("Ahoj", p.get("hello"))
        p.zavri()
    }

    /** Načtení ze souboru běží na pozadí → chvíli počkat. */
    private fun <T> cekej(hodnota: () -> T?): T? {
        repeat(50) { hodnota()?.let { return it }; Thread.sleep(20) }
        return hodnota()
    }
}
