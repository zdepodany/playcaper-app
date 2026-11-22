package cz.titulky.tv

internal enum class UrovenSpojeni { VYPNUTO, CEKANI, OK, CHYBA }
internal data class PrehledSpojeni(val klic: String, val uroven: UrovenSpojeni, val detail: String = "", val stariSnimkuMs: Long? = null)

/** Stav služby není důkaz spojení ani příjmu obrazu. Čas dodává volající, v testech simulovaný. */
internal class StavObrazu {
    private var pripojenoOd: Long? = null
    private var posledniSnimek: Long? = null
    private var chyba: String? = null
    private var chybaUkonceni: String? = null

    @Synchronized fun zacni() { pripojenoOd = null; posledniSnimek = null; chyba = null; chybaUkonceni = null }
    /** Konec relace bez nového pokusu (např. opožděná QuitEvent po fatální chybě): konkrétní důvod zůstává. */
    @Synchronized fun zastaveno() { pripojenoOd = null; posledniSnimek = null; chyba = null }
    @Synchronized fun ukonciChybou(duvod: String) { chybaUkonceni = duvod }
    @Synchronized fun pripojeno(ted: Long) { pripojenoOd = ted }
    @Synchronized fun snimek(ted: Long) { posledniSnimek = ted; chyba = null }
    @Synchronized fun chyba(duvod: String) { chyba = duvod }

    @Synchronized fun prehled(ted: Long, aktivni: Boolean, pozastaveno: Boolean, duvod: String): PrehledSpojeni = when {
        chybaUkonceni != null -> PrehledSpojeni("stavUkoncenoChybou", UrovenSpojeni.CHYBA, chybaUkonceni!!)
        !aktivni -> PrehledSpojeni("stavVypnuto", UrovenSpojeni.VYPNUTO)
        pozastaveno -> PrehledSpojeni("stavPozastaveno", UrovenSpojeni.VYPNUTO)
        chyba != null -> PrehledSpojeni("stavChybaObrazu", UrovenSpojeni.CHYBA, chyba!!)
        pripojenoOd == null -> PrehledSpojeni("stavNavazuji", UrovenSpojeni.CEKANI, duvod)
        posledniSnimek == null && ted - pripojenoOd!! >= 10_000 -> PrehledSpojeni("stavBezObrazu", UrovenSpojeni.CHYBA)
        posledniSnimek == null -> PrehledSpojeni("stavCekaObraz", UrovenSpojeni.CEKANI)
        ted - posledniSnimek!! > 30_000 -> PrehledSpojeni("stavObrazUstal", UrovenSpojeni.CHYBA, stariSnimkuMs = ted - posledniSnimek!!)
        else -> PrehledSpojeni("stavObrazChodi", UrovenSpojeni.OK, stariSnimkuMs = (ted - posledniSnimek!!).coerceAtLeast(0))
    }
}

internal data class PrehledPrenosu(val odeslano: Boolean = false, val chyba: String? = null, val duvodKlic: String = "") {
    val klic get() = when {
        chyba != null -> "tvChybaOdesilani"
        odeslano -> "tvOdesilanoNeovereno"
        else -> "tvVybranaNeoverena"
    }
}

/** UDP send potvrzuje jen předání systému. Starý pracovník nesmí přepsat stav nového cíle. */
internal class StavPrenosu {
    private var generace = 0L
    @Volatile var stav = PrehledPrenosu()
        private set
    @Synchronized fun reset(nova: Long) { generace = nova; stav = PrehledPrenosu() }
    @Synchronized fun vysledek(relace: Long, chyba: Throwable?) {
        if (relace != generace) return
        stav = if (chyba == null) PrehledPrenosu(odeslano = true)
            else PrehledPrenosu(chyba = chyba.javaClass.simpleName, duvodKlic = when (chyba) {
                is java.net.UnknownHostException -> "sitAdresa"
                is SecurityException -> "sitOpravneni"
                else -> "sitOdeslani"
            })
    }
}
