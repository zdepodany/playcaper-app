package cz.titulky.tv

/**
 * Služba si sem uloží, co se má ukázat, a obrazovka TelefonActivity si to každou chvíli přečte
 * (a když je vybraná televize, pošle se to i do ní).
 */
object TelefonTitulky {
    @Volatile var text: String = ""
        private set
    @Volatile var napoveda: String = ""
        set(v) {
            field = v
            // režim "telefon + televize": nápovědu poslat i do televize
            Propojeni.Vysilac.zmena(text, posledniDoba, v, barvy)
        }
    @Volatile private var posledniDoba = 0L
    /** PS5 si vzalo jiné zařízení → obrazovku zavřít. */
    @Volatile var odpojeno = false
    /** Do kdy text platí (0 = dokud ho nic nepřepíše). */
    @Volatile private var doKdy = 0L

    /** Barva jména postavy u každého řádku (0 = bez barvy) – jako ve hře ("Vivian:" zeleně). */
    @Volatile var barvy: IntArray? = null
        private set

    fun nastav(t: String, doba: Long, barvyJmen: IntArray? = null) {
        barvy = barvyJmen
        text = t
        posledniDoba = doba
        doKdy = if (t.isNotEmpty() && doba > 0) System.currentTimeMillis() + doba else 0L
        // režim "telefon + televize": titulek poslat i do televize
        Propojeni.Vysilac.zmena(t, doba, napoveda, barvyJmen)
    }

    /** Text, který se má teď ukazovat (prošlý = prázdný). */
    fun aktualni(): String =
        if (doKdy != 0L && System.currentTimeMillis() > doKdy) "" else text
}
