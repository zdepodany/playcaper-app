package cz.titulky.tv

import cz.titulky.tv.SluzbyPrekladu.DEEPL
import cz.titulky.tv.SluzbyPrekladu.GOOGLE
import cz.titulky.tv.SluzbyPrekladu.MICROSOFT
import cz.titulky.tv.SluzbyPrekladu.MYMEMORY
import org.junit.Assert.assertEquals
import org.junit.Test

class SluzbyPrekladuTest {

    @Test
    fun prazdneUlozeniDavaVychoziPoradi() {
        assertEquals(listOf(DEEPL, GOOGLE, MICROSOFT, MYMEMORY), SluzbyPrekladu.normalizuj(emptyList()))
    }

    @Test
    fun normalizaceVyhodiNeznameADoplniChybejici() {
        assertEquals(
            listOf(MICROSOFT, GOOGLE, DEEPL, MYMEMORY),
            SluzbyPrekladu.normalizuj(listOf("xbox", MICROSOFT, GOOGLE, MICROSOFT)),
        )
    }

    @Test
    fun aktivniRespektujePoradiAVypnute() {
        assertEquals(
            listOf(MICROSOFT, DEEPL, MYMEMORY),
            SluzbyPrekladu.aktivni(listOf(MICROSOFT, DEEPL, GOOGLE, MYMEMORY), setOf(GOOGLE), maDeeplKlic = true),
        )
    }

    @Test
    fun deeplBezKliceNeboPresVolbySeVynecha() {
        assertEquals(listOf(GOOGLE, MICROSOFT, MYMEMORY), SluzbyPrekladu.aktivni(emptyList(), emptySet(), maDeeplKlic = false))
        assertEquals(
            listOf(GOOGLE, MICROSOFT, MYMEMORY),
            SluzbyPrekladu.aktivni(emptyList(), emptySet(), maDeeplKlic = true, bezDeepl = true),
        )
    }

    @Test
    fun presunPretazenimANeplatneIndexyBezeZmeny() {
        assertEquals(listOf(MYMEMORY, DEEPL, GOOGLE, MICROSOFT), SluzbyPrekladu.presun(emptyList(), 3, 0))
        assertEquals(listOf(GOOGLE, MICROSOFT, DEEPL, MYMEMORY), SluzbyPrekladu.presun(emptyList(), 0, 2))
        assertEquals(listOf(GOOGLE, DEEPL, MICROSOFT, MYMEMORY), SluzbyPrekladu.presun(emptyList(), 1, 0))
        assertEquals(SluzbyPrekladu.VYCHOZI, SluzbyPrekladu.presun(emptyList(), 0, 4))
        assertEquals(SluzbyPrekladu.VYCHOZI, SluzbyPrekladu.presun(emptyList(), -1, 0))
    }
}
