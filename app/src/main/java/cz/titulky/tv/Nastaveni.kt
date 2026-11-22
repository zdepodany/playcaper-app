package cz.titulky.tv

import android.content.Context

/** Nastavení PlayCaption (jazyk, co překládat, jak zobrazovat). */
class Nastaveni(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gamelingo", Context.MODE_PRIVATE)

    /** Uložený jazyk překladu i prostředí aplikace; null = nic neuloženo. */
    var jazyk: String?
        get() = prefs.getString("jazyk", null)
        set(v) = prefs.edit().putString("jazyk", v).apply()

    /** Jazyk, který se opravdu použije: uložený, pokud jde vybrat (starší „sk“ apod. → čeština). */
    val jazykNeboCs get() = jazyk?.takeIf { it in AKTIVNI_JAZYKY } ?: "cs"

    /**
     * Vlastní klíče k DeepL API (nepovinné, nejvýš [MAX_DEEPL_KLICU]) v pořadí použití.
     * Po vyčerpání jednoho překladač přejde na další. Prázdný seznam = DeepL se nepoužívá.
     * Starší verze ukládala jediný klíč pod "deeplKlic" – ten se bere jako první.
     */
    var deeplKlice: List<String>
        get() = (prefs.getString("deeplKlice", null) ?: prefs.getString("deeplKlic", null) ?: "")
            .let(::vycistiDeeplKlice)
        set(v) = prefs.edit().remove("deeplKlic")
            .putString("deeplKlice", vycistiDeeplKlice(v.joinToString("\n")).joinToString("\n")).apply()

    /** Pořadí online překladových služeb (id z [SluzbyPrekladu]); vždy obsahuje všechny. */
    var poradiSluzeb: List<String>
        get() = SluzbyPrekladu.normalizuj(prefs.getString("poradiSluzeb", null).orEmpty().split(",").filter { it.isNotBlank() })
        set(v) = prefs.edit().putString("poradiSluzeb", SluzbyPrekladu.normalizuj(v).joinToString(",")).apply()

    /** Služby, které uživatel vypnul (výchozí: žádná). */
    var vypnuteSluzby: Set<String>
        get() = prefs.getString("vypnuteSluzby", null).orEmpty().split(",").filter { it in SluzbyPrekladu.VYCHOZI }.toSet()
        set(v) = prefs.edit().putString("vypnuteSluzby", v.filter { it in SluzbyPrekladu.VYCHOZI }.joinToString(",")).apply()

    companion object {
        const val MAX_DEEPL_KLICU = 5

        /** Jazyky překladu, které fork podporuje (slovenština odebrána). */
        val AKTIVNI_JAZYKY = setOf("cs")

        /** Řádky → klíče: oříznout, bez prázdných a duplicit, nejvýš [MAX_DEEPL_KLICU]. */
        fun vycistiDeeplKlice(text: String): List<String> =
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_DEEPL_KLICU)
    }

    var pribehovy: Boolean
        get() = prefs.getBoolean("pribehovy", false)
        set(v) = prefs.edit().putBoolean("pribehovy", v).apply()

    var casPribehuProcenta: Int
        get() = prefs.getInt("casPribehuProcenta", CasovaniPribehu.VYCHOZI_PROCENTA)
            .coerceIn(CasovaniPribehu.MIN_PROCENT, CasovaniPribehu.MAX_PROCENT)
        set(v) = prefs.edit().putInt("casPribehuProcenta", v.coerceIn(CasovaniPribehu.MIN_PROCENT, CasovaniPribehu.MAX_PROCENT)).apply()

    /** Box s překladem přes původní titulek (jinak nad ním). */
    var prekryvat: Boolean
        get() = prefs.getBoolean("prekryvat", true)
        set(v) = prefs.edit().putBoolean("prekryvat", v).apply()

    /** Překládat mluvené dialogy. */
    var dialogy: Boolean
        get() = prefs.getBoolean("dialogy", true)
        set(v) = prefs.edit().putBoolean("dialogy", v).apply()

    /** Překládat nápovědu k ovládání a úkoly (žlutě). */
    var napovedy: Boolean
        get() = prefs.getBoolean("napovedy", false)
        set(v) = prefs.edit().putBoolean("napovedy", v).apply()
}
