package cz.titulky.tv

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import cz.titulky.tv.Prekladac.DeeplVyuziti

/**
 * Stav měsíčních limitů DeepL pro zobrazení (hlavní obrazovka, obrazovka hraní, okno s klíči).
 * Paměť stavu je společná pro celou aplikaci – DeepL se neptáme častěji než jednou za minutu.
 */
internal object DeeplLimit {

    enum class Uroven { NACITA, OK, POZOR, KRITICKE }

    /** Co ukázat: text pod proužkem, zaplnění 0–1 a barva. */
    data class Zobrazeni(val text: String, val podil: Float, val uroven: Uroven)

    private val ui = Handler(Looper.getMainLooper())
    private var stavy: List<DeeplVyuziti>? = null
    private var stavyKlice: List<String> = emptyList()
    private var cas = 0L
    private var nacita = false
    /** Kdo chce dostat nový stav (právě zobrazené proužky). */
    private val odberatele = mutableSetOf<(List<DeeplVyuziti>) -> Unit>()

    /** Uložený stav pro tyto klíče, nebo null. */
    fun ulozene(klice: List<String>): List<DeeplVyuziti>? = stavy.takeIf { stavyKlice == klice }

    /** Uloží stav načtený jinde (okno s klíči) a pošle ho zobrazeným proužkům. */
    fun uloz(klice: List<String>, nove: List<DeeplVyuziti>) {
        stavy = nove; stavyKlice = klice; cas = System.currentTimeMillis()
        odberatele.toList().forEach { it(nove) }
    }

    /**
     * Načte stav na pozadí, když je uložený starší než [maxStari] nebo pro jiné klíče.
     * Výsledek přijde odběratelům na hlavním vlákně.
     */
    fun obnov(klice: List<String>, maxStari: Long = 60_000L) {
        if (klice.isEmpty() || nacita) return
        if (ulozene(klice) != null && System.currentTimeMillis() - cas <= maxStari) return
        nacita = true
        Thread {
            // hned po startu aplikace síť občas ještě není → jeden opakovaný pokus
            val nove = klice.map { klic ->
                Prekladac.nactiDeeplVyuziti(klic).let {
                    if (it == DeeplVyuziti.Chyba) { Thread.sleep(2000); Prekladac.nactiDeeplVyuziti(klic) } else it
                }
            }
            ui.post { nacita = false; uloz(klice, nove) }
        }.apply { isDaemon = true }.start()
    }

    private fun cislo(n: Long, jazyk: String) = java.text.NumberFormat.getIntegerInstance(java.util.Locale(jazyk)).format(n)

    /** Popis jednoho klíče (okno s klíči). */
    fun popisKlice(stav: DeeplVyuziti, jazyk: String): String = when (stav) {
        is DeeplVyuziti.Ok -> Texty.t(jazyk, if (stav.vycerpano) "deeplLimitVycerpan" else "deeplLimit")
            .replace("{pouzito}", cislo(stav.pouzito, jazyk))
            .replace("{limit}", cislo(stav.limit, jazyk))
            .replace("{procenta}", stav.procenta.toString())
        DeeplVyuziti.SpatnyKlic -> Texty.t(jazyk, "deeplLimitSpatnyKlic")
        DeeplVyuziti.Chyba -> Texty.t(jazyk, "deeplLimitChyba")
    }

    /** Souhrn přes všechny klíče pro proužek. */
    fun zobrazeni(stavy: List<DeeplVyuziti>?, pocetKlicu: Int, jazyk: String): Zobrazeni {
        if (stavy.isNullOrEmpty()) return Zobrazeni(Texty.t(jazyk, "deeplLimitNacitam"), 0f, Uroven.NACITA)
        val ok = Prekladac.soucetDeepl(stavy)
            ?: return Zobrazeni(popisKlice(stavy.first(), jazyk), 0f, Uroven.KRITICKE)
        val podil = if (ok.limit > 0) (ok.pouzito.toFloat() / ok.limit).coerceIn(0f, 1f) else 0f
        val nefunguje = stavy.count { it !is DeeplVyuziti.Ok }
        val text = if (ok.vycerpano) Texty.t(jazyk, "deeplVseVycerpano")
            .replace("{pouzito}", cislo(ok.pouzito, jazyk)).replace("{limit}", cislo(ok.limit, jazyk))
        else Texty.t(jazyk, "deeplLimitKratce")
            .replace("{pouzito}", cislo(ok.pouzito, jazyk)).replace("{limit}", cislo(ok.limit, jazyk))
            .replace("{procenta}", ok.procenta.toString()) +
            (if (pocetKlicu > 1) " · " + Texty.t(jazyk, if (pocetKlicu <= 4) "deeplPocetKlicu" else "deeplPocetKlicu5")
                .replace("{n}", pocetKlicu.toString()) else "") +
            (if (nefunguje > 0) " · " + Texty.t(jazyk, "deeplNefunguje").replace("{n}", nefunguje.toString()) else "")
        val uroven = when {
            ok.procenta >= 90 || nefunguje > 0 -> Uroven.KRITICKE
            ok.procenta >= 75 -> Uroven.POZOR
            else -> Uroven.OK
        }
        return Zobrazeni(text, podil, uroven)
    }

    /**
     * Proužek + text, jen pro čtení. Sám se přihlásí k novým stavům, dokud je na obrazovce,
     * a po zobrazení si řekne o čerstvý stav. [obnovovat] = načítat znovu každou minutu (obrazovka hraní).
     * null = žádné klíče.
     */
    fun pruh(context: Context, textSize: Float, barvaTextu: Int, obnovovat: Boolean = false): View? {
        val nast = Nastaveni(context)
        val klice = nast.deeplKlice.ifEmpty { return null }
        val d = context.resources.displayMetrics.density
        fun dp(x: Int) = (x * d).toInt()
        fun zaobleni(barva: Int) = GradientDrawable().apply { setColor(barva); cornerRadius = 999f }

        val radek = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val pruh = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; background = zaobleni(SEDA) }
        val vypln = View(context)
        val zbytek = View(context)
        pruh.addView(vypln, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f))
        pruh.addView(zbytek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        val popis = TextView(context).apply { this.textSize = textSize; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        radek.addView(pruh, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)))
        radek.addView(popis, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })

        fun vykresli(stavy: List<DeeplVyuziti>?) {
            val z = zobrazeni(stavy, klice.size, nast.jazykNeboCs)
            (vypln.layoutParams as LinearLayout.LayoutParams).weight = z.podil
            (zbytek.layoutParams as LinearLayout.LayoutParams).weight = 1f - z.podil
            vypln.background = zaobleni(when (z.uroven) {
                Uroven.NACITA -> SEDA
                Uroven.OK -> ZELENA
                Uroven.POZOR -> ZLUTA
                Uroven.KRITICKE -> CERVENA
            })
            pruh.requestLayout()
            popis.text = z.text
            popis.setTextColor(if (z.uroven == Uroven.KRITICKE) CERVENA else barvaTextu)
        }
        vykresli(ulozene(klice))

        val odber: (List<DeeplVyuziti>) -> Unit = { if (Nastaveni(context).deeplKlice == klice) vykresli(it) }
        val kazdouMinutu = object : Runnable {
            override fun run() { obnov(klice); radek.postDelayed(this, 60_000L) }
        }
        radek.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                odberatele += odber
                ulozene(klice)?.let(::vykresli)
                if (obnovovat) radek.post(kazdouMinutu) else obnov(klice)
            }
            override fun onViewDetachedFromWindow(v: View) {
                odberatele -= odber
                radek.removeCallbacks(kazdouMinutu)
            }
        })
        return radek
    }

    private val SEDA = Color.parseColor("#3A3A44")
    private val ZELENA = Color.parseColor("#22C55E")
    private val ZLUTA = Color.parseColor("#FFC24B")
    private val CERVENA = Color.parseColor("#FF6B70")
}
