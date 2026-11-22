package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class CisteniVolebTest {
    @Test fun zbytkyIkonZeZaznamuSeUseknou() {
        val ze_zaznamu = mapOf(
            "TAUNT O" to "TAUNT", "LOCAL(" to "LOCAL", "LOCAL()" to "LOCAL", "LOCALS(O)" to "LOCALS",
            "LOCAL )" to "LOCAL", "TAUNT O)" to "TAUNT", "DEFUSE %" to "DEFUSE", "DEFUSE <" to "DEFUSE",
            "GOODBYE." to "GOODBYE", "▶ Rob the bank" to "Rob the bank")
        for ((vstup, cekam) in ze_zaznamu) assertEquals(vstup, cekam, CisteniVoleb.ocisti(vstup))
    }

    @Test fun beznaVolbaZustaneCela() {
        assertEquals("Ask about Lucia", CisteniVoleb.ocisti("Ask about Lucia"))
        assertEquals("Go", CisteniVoleb.ocisti("Go"))
    }

    @Test fun preklepCteckyNajdeZnamouVolbu() {
        val zname = listOf("GREET", "TAUNT", "LOCALS")
        assertEquals("GREET", CisteniVoleb.stabilizuj("CREET", zname))
        assertEquals("LOCALS", CisteniVoleb.stabilizuj("LOCALSO", zname))
        assertEquals("LOCALS", CisteniVoleb.stabilizuj("LOCA1S", zname))
        assertEquals("DEFUSE", CisteniVoleb.stabilizuj("DEFUSE", zname))
        // krátká slova se nepárují (Yes/Yea jsou různé volby)
        assertEquals("Yea", CisteniVoleb.stabilizuj("Yea", listOf("Yes")))
    }

    @Test fun velkaPismenaProPrekladac() {
        assertEquals("Provoke", CisteniVoleb.proPreklad("PROVOKE"))
        assertEquals("Ask about Lucia", CisteniVoleb.proPreklad("Ask about Lucia"))
    }

    @Test fun slovnicekJenProCestinu() {
        assertEquals("Posmívat se", CisteniVoleb.zeSlovniku("TAUNT", "cs"))
        assertEquals("Pozdravit", CisteniVoleb.zeSlovniku("Greet.", "cs"))
        assertNull(CisteniVoleb.zeSlovniku("TAUNT", "sk"))
        assertNull(CisteniVoleb.zeSlovniku("Rob the bank", "cs"))
    }

    @Test fun jmenoNpcBezIkonyAMistni() {
        assertEquals("Andrew", CisteniVoleb.jmenoNpc("F ANDREW", "cs"))
        assertEquals("Andrew", CisteniVoleb.jmenoNpc("ANDREW", "cs"))
        assertEquals("Místní", CisteniVoleb.jmenoNpc("LOCAL", "cs"))
        assertEquals("Local", CisteniVoleb.jmenoNpc("LOCAL", "sk"))
    }

    @Test fun slovnicekSnesePreklepyCtecky() {
        assertEquals("Místní", CisteniVoleb.jmenoNpc("CoCALS", "cs"))
        assertEquals("Místní", CisteniVoleb.jmenoNpc("L0CALS", "cs"))
        assertEquals("Pozdravit", CisteniVoleb.zeSlovniku("CREET", "cs"))
        // krátká slova a jména se neohýbají na slovníček
        assertNull(CisteniVoleb.zeSlovniku("Lucas", "cs"))
        assertEquals("Andrew", CisteniVoleb.jmenoNpc("ANDREW", "cs"))
    }

    @Test fun zamestnanec() {
        assertEquals("Zaměstnanec", CisteniVoleb.jmenoNpc("STAFF MEMBER", "cs"))
        assertEquals("Zaměstnanec", CisteniVoleb.jmenoNpc("Staff meimber", "cs"))
        assertEquals("Zaměstnanec", CisteniVoleb.jmenoNpc("F STAFF MEMBER", "cs"))
    }

    /** Záznam 22. 11. 2026: ikona tlačítka přečtená jako písmeno → slovník volbu nepoznal a šla na DeepL. */
    @Test fun slovnikPoznaVolbuSeZbytkemIkony() {
        val ze_zaznamu = mapOf(
            "GREET (X" to "Pozdravit", "GREET X" to "Pozdravit", "TAUNT C" to "Posmívat se",
            "TAUNT D" to "Posmívat se", "PROVOKE O E" to "Vyprovokovat", "CDEFUSE X" to "Uklidnit",
            "GOODBYE" to "Rozloučit se", "LOCAL" to "Místní")
        for ((vstup, cekam) in ze_zaznamu) {
            val v = CisteniVoleb.ocisti(vstup)
            assertEquals(vstup, cekam, CisteniVoleb.zeSlovniku(v, "cs"))
        }
    }

    @Test fun jednoPismenoNaKonciSkutecneVolbyNeprekazi() {
        // klíč se zkracuje jen pro hledání – volba mimo slovník se dál překládá celá
        assertNull(CisteniVoleb.zeSlovniku("Plan B", "cs"))
        assertEquals("plan", CisteniVoleb.klicSlovniku("Plan B"))
        assertEquals("staff member", CisteniVoleb.klicSlovniku("STAFF MEMBER X"))
    }

    @Test fun samaPismenaJsouSum() {
        assertTrue(CisteniVoleb.jeSum("T T S"))
        assertTrue(CisteniVoleb.jeSum("X O"))
        assertTrue(CisteniVoleb.jeSum("( % )"))
        assertFalse(CisteniVoleb.jeSum("GREET X"))
        assertFalse(CisteniVoleb.jeSum("Go"))
        assertFalse(CisteniVoleb.jeSum("Ask about Lucia"))
    }

    @Test fun turisti() {
        assertEquals("Turisti", CisteniVoleb.jmenoNpc("TOURIST", "cs"))
        assertEquals("Turisti", CisteniVoleb.jmenoNpc("TOURISTS", "cs"))
    }
}
