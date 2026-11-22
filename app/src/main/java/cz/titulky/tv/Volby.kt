package cz.titulky.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View

/**
 * Volby odpovědí ve hře (dialogové volby – "Vykrást banku / Nevykrást").
 *
 * Uživatel si v telefonu (mini televize) posune žlutý šrafovaný rámeček na místo, kde hra
 * ukazuje volby, a zapne ho. Telefon pak tu oblast čte, pozná, na které volbě je hráč
 * (hra ji zvýrazní – jiná barva / podbarvení), přeloží je a pošle do televize.
 * Televize ukáže český seznam VEDLE anglických voleb (nezakrývá je).
 */
object Volby {
    private val zmeny = java.util.concurrent.atomic.AtomicLong()
    val revize get() = zmeny.get()
    /** Čte se oblast voleb? (vypínač pod mini televizí) */
    @Volatile var zapnuto = false
    /** Kde ve hře jsou volby: vlevo, nahoře, vpravo, dole – podíl šířky/výšky obrazu (0..1). */
    @Volatile var oblast = VYCHOZI.copyOf()
    /** Ukazovat žlutý rámeček na televizi pořád (jinak jen při posouvání). */
    @Volatile var ukazatNaTv = false
    /** Zámeček: žlutý rámeček nejde posunout ani změnit. */
    @Volatile var zamceno = false
    /** Režim Text: celý text v rámečku (dopis, SMS, popis úkolu) se přeloží jako celek, ne jako seznam voleb. */
    @Volatile var rezimText = false
    /**
     * GTA VI: volby se vybírají tlačítkem (△, ✕), žádná není zvýrazněná → nic nezvýrazňovat
     * a nečekat na zvýraznění. Spodní řádek je jméno NPC (u L2) – nepřekládat strojově.
     */
    @Volatile var gta = false

    val VYCHOZI get() = floatArrayOf(0.55f, 0.45f, 0.95f, 0.80f)

    private fun prefs(c: Context) = TvProfily.nastaveni(c)

    fun nacti(c: Context) {
        zmeny.incrementAndGet()
        val p = prefs(c)
        zapnuto = p.getBoolean("volbyZap", false)
        ukazatNaTv = p.getBoolean("volbyUkazat", false)
        zamceno = p.getBoolean("volbyZamek", false)
        rezimText = p.getBoolean("volbyRezimText", false)
        gta = p.getBoolean("volbyGta", false)
        val v = VYCHOZI
        oblast = floatArrayOf(
            p.getFloat("volbyL", v[0]), p.getFloat("volbyT", v[1]),
            p.getFloat("volbyR", v[2]), p.getFloat("volbyB", v[3]))
    }

    fun uloz(c: Context) {
        zmeny.incrementAndGet()
        val o = oblast
        prefs(c).edit().putBoolean("volbyZap", zapnuto).putBoolean("volbyUkazat", ukazatNaTv).putBoolean("volbyZamek", zamceno).putBoolean("volbyRezimText", rezimText).putBoolean("volbyGta", gta)
            .putFloat("volbyL", o[0]).putFloat("volbyT", o[1])
            .putFloat("volbyR", o[2]).putFloat("volbyB", o[3]).apply()
    }

    /** „Vybraná volba“ = -2 → televize ví, že jde o text (dopis…), a nechá ho na obrazovce déle. */
    const val VYBRANA_TEXT = -2

    /** Barva rámečku voleb (stejná žlutá jako nápověda). */
    val ZLUTA = 0xFFFFD54A.toInt()
}

/**
 * Překlad všech voleb jedním dotazem: volby se spojí po řádcích, přeloží najednou
 * a odpověď se zase rozdělí. Místo N dotazů za sebou (každý ~0,3–0,8 s) jen jeden.
 */
object DavkaVoleb {
    fun spoj(volby: List<String>): String = volby.joinToString("\n")

    /** Rozdělí překlad zpět na volby; null = počet řádků nesedí (pak se překládá po jedné). */
    fun rozdel(preklad: String, pocet: Int): List<String>? {
        val casti = preklad.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        return if (casti.size == pocet) casti else null
    }
}

/**
 * Očištění přečtených voleb před překladem.
 * - Ikony tlačítek vedle voleb čtečka čte jako "O", "(O)", ")", "%" → useknout.
 * - Stejná volba přečtená trochu jinak ("CREET"/"GREET", "LOCALSO"/"LOCALS") → vzít tu známou,
 *   ať se nepřekládá a neposílá pořád znovu.
 * - VELKÁ PÍSMENA Google bere jako zkratku a nepřeloží ("DEFUSE") → překládat "Defuse".
 * - Krátké volby z interakcí s NPC (GTA VI) mají pevný překlad – jedno slovo bez kontextu
 *   strojový překlad nedá ("Taunt" → "Posměch").
 */
object CisteniVoleb {
    /** U+2063 (neviditelný oddělovač) před jménem NPC – nový přijímač podle něj zapne vzhled GTA VI, starý ho nezobrazí. */
    const val ZNACKA_NPC = "\u2063"
    /** U+2064 (neviditelné plus) před symbolem ikony tlačítka na konci řádku ("Pozdravit ⁤✕"). */
    const val ZNACKA_IKONY = "\u2064"
    private val ZBYTEK_IKONY = Regex("""(\s+[O0o]|\s*[(\[<][O0o]|\s*[()\[\]<>%|.,:;_~*•-]+)$""")

    fun ocisti(v: String): String {
        var t = v.trim().trimStart { ch -> !ch.isLetterOrDigit() && ch != '"' && ch != '„' }.trim()
        while (true) {
            val dalsi = ZBYTEK_IKONY.replace(t, "").trim()
            if (dalsi == t || dalsi.count { it.isLetter() } < 2) return t
            t = dalsi
        }
    }

    /** Najde mezi známými volbami tu, která se liší jen o překlep čtečky; jinak vrátí vstup. */
    fun stabilizuj(v: String, zname: Collection<String>): String {
        if (v in zname) return v
        val a = v.lowercase()
        val povoleno = when { a.length >= 8 -> 2; a.length >= 5 -> 1; else -> 0 }
        if (povoleno == 0) return v
        return zname.firstOrNull { z -> vzdalenost(a, z.lowercase(), povoleno) <= povoleno } ?: v
    }

    /** GTA VI: jméno NPC u spodního řádku – bez ikony vlevo ("F ANDREW" → "Andrew", "LOCAL" → "Místní"). */
    fun jmenoNpc(v: String, jazyk: String): String {
        val bezIkony = v.replace(Regex("""^\S\s+(?=\p{L}{2})"""), "")
        return zeSlovniku(bezIkony, jazyk) ?: proPreklad(bezIkony)
    }

    /** Text pro překladač: CELÉ VELKÝMI → "Jen první velké". */
    fun proPreklad(v: String): String =
        if (v.any { it.isLetter() } && v.none { it.isLowerCase() }) v.lowercase().replaceFirstChar { it.uppercaseChar() } else v

    /** Pevný překlad krátkých voleb (klíč malými písmeny, bez interpunkce). */
    fun zeSlovniku(v: String, jazyk: String): String? {
        if (jazyk != "cs") return null
        val k = v.lowercase().trim().trimEnd('.', '!', '?')
        SLOVNIK_CS[k]?.let { return it }
        // překlepy čtečky: číslice místo písmen ("L0CALS"), jedno špatné písmeno ("CoCALS", "CREET")
        val opraveny = k.replace('0', 'o').replace('1', 'l')
        SLOVNIK_CS[opraveny]?.let { return it }
        if (opraveny.length < 5) return null
        return SLOVNIK_CS.entries.firstOrNull { vzdalenost(opraveny, it.key, 1) <= 1 }?.value
    }

    private val SLOVNIK_CS = mapOf(
        "greet" to "Pozdravit",
        "taunt" to "Posmívat se",
        "defuse" to "Uklidnit",
        "provoke" to "Vyprovokovat",
        "goodbye" to "Rozloučit se",
        "local" to "Místní",
        "locals" to "Místní",
        "staff member" to "Zaměstnanec",
        "tourist" to "Turisti",
        "tourists" to "Turisti",
    )

    /** Levenshteinova vzdálenost s předčasným koncem nad limitem. */
    private fun vzdalenost(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var pred = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val ted = IntArray(b.length + 1)
            ted[0] = i
            for (j in 1..b.length)
                ted[j] = minOf(pred[j] + 1, ted[j - 1] + 1, pred[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            pred = ted
        }
        return pred[b.length]
    }
}

/**
 * Ruční oblast titulků (modrý šrafovaný rámeček). Vypnuto = automatické hledání titulků
 * (spodní část obrazu, aplikace se sama učí, kde titulky jsou). Zapnuto = čte se JEN
 * v rámečku – hodí se, když hra má titulky jinde nebo aplikace bere i něco okolo.
 */
object OblastTitulku {
    @Volatile var rucne = false
    /** Zámeček: rámeček nejde posunout ani změnit (ať se omylem neposune). */
    @Volatile var zamceno = false
    /** vlevo, nahoře, vpravo, dole – podíl obrazu (0..1) */
    @Volatile var oblast = VYCHOZI.copyOf()

    val VYCHOZI get() = floatArrayOf(0.10f, 0.72f, 0.90f, 0.97f)
    val MODRA = 0xFF4FB3FF.toInt()

    private fun prefs(c: Context) = TvProfily.nastaveni(c)

    fun nacti(c: Context) {
        val p = prefs(c)
        rucne = p.getBoolean("titRucne", false)
        zamceno = p.getBoolean("titZamek", false)
        val v = VYCHOZI
        oblast = floatArrayOf(
            p.getFloat("titL", v[0]), p.getFloat("titT", v[1]),
            p.getFloat("titR", v[2]), p.getFloat("titB", v[3]))
    }

    fun uloz(c: Context) {
        val o = oblast
        prefs(c).edit().putBoolean("titRucne", rucne).putBoolean("titZamek", zamceno)
            .putFloat("titL", o[0]).putFloat("titT", o[1])
            .putFloat("titR", o[2]).putFloat("titB", o[3]).apply()
    }
}

/**
 * Žlutý šrafovaný, lehce průhledný rámeček (oblast čtení voleb).
 * Používá se v mini televizi v telefonu i na skutečné televizi při nastavování.
 */
class SrafovanyRamecek(c: Context, private val popisek: String? = null, barva: Int = Volby.ZLUTA) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val bezAlfy = barva and 0x00FFFFFF
    private val vypln = Paint().apply { color = 0x33000000 or bezAlfy; style = Paint.Style.FILL }
    private val cara = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x77000000 or bezAlfy; strokeWidth = 2f * d }
    private val okraj = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = barva; style = Paint.Style.STROKE; strokeWidth = 3f * d
        pathEffect = DashPathEffect(floatArrayOf(10f * d, 6f * d), 0f)
    }
    private val pismo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = barva; textSize = 12f * d; isFakeBoldText = true
        setShadowLayer(3f, 0f, 0f, 0xFF000000.toInt())
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        cv.drawRect(0f, 0f, w, h, vypln)
        // šikmé šrafování
        cv.save()
        cv.clipRect(0f, 0f, w, h)
        val krok = 14f * d
        var x = -h
        while (x < w) { cv.drawLine(x, h, x + h, 0f, cara); x += krok }
        cv.restore()
        val o = okraj.strokeWidth / 2
        cv.drawRect(o, o, w - o, h - o, okraj)
        if (popisek != null && h > pismo.textSize * 1.6f) cv.drawText(popisek, 6f * d, pismo.textSize + 4f * d, pismo)
    }
}
