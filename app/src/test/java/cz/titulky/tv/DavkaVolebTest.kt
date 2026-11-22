package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class DavkaVolebTest {
    @Test fun spojeniARozdeleniZachovaPoradi() {
        val volby = listOf("Rob the bank", "Walk away", "Ask about Lucia")
        assertEquals("Rob the bank\nWalk away\nAsk about Lucia", DavkaVoleb.spoj(volby))
        assertEquals(listOf("Vykrást banku", "Odejít", "Zeptat se na Lucii"),
            DavkaVoleb.rozdel("Vykrást banku\n Odejít \nZeptat se na Lucii\n", 3))
    }

    @Test fun prazdneRadkyVOdpovediSeIgnoruji() {
        assertEquals(listOf("Ano", "Ne"), DavkaVoleb.rozdel("Ano\n\nNe", 2))
    }

    @Test fun nesouhlasiciPocetRadkuVratiNull() {
        assertNull(DavkaVoleb.rozdel("Vykrást banku a odejít", 2))
        assertNull(DavkaVoleb.rozdel("A\nB\nC", 2))
    }
}
