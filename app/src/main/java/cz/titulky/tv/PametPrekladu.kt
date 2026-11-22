package cz.titulky.tv

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Paměť přeložených vět (PC-008): přežije restart aplikace, šetří online překlady (hlavně limit DeepL).
 *
 * - Jeden soubor na cílový jazyk (`preklady/preklady-cs.json`), nejvýš [max] vět, vyhazují se nejdéle nepoužité.
 * - Klíč je sjednocený anglický text ([klic]) – stejná věta přečtená OCR trochu jinak
 *   (velké písmeno, čárka místo tečky, diakritika navíc) se najde. Otazník se zachovává.
 * - Načítání a ukládání běží na vlastním vlákně; ukládá se nejvýš jednou za [ZPOZDENI_ULOZENI_MS]
 *   a při zavření. Bez [slozka] (vývojové obrazovky) jen v paměti.
 */
internal class PametPrekladu(private val slozka: File?, private val max: Int = 5000) {

    private val mapa = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > max
    }
    private var jazyk: String? = null
    private var ulozeniNaplanovano = false
    private val io = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "PametPrekladu").apply { isDaemon = true } }

    @Synchronized fun get(text: String): String? = mapa[klic(text)]

    @Synchronized fun put(text: String, preklad: String) {
        val k = klic(text)
        if (k.isEmpty() || preklad.isBlank()) return
        mapa[k] = preklad
        naplanujUlozeni()
    }

    @Synchronized fun velikost() = mapa.size

    /** Přepne cílový jazyk: uloží dosavadní paměť a na pozadí načte paměť nového jazyka. */
    @Synchronized fun prepniJazyk(kod: String) {
        if (kod == jazyk) return
        jazyk?.let { stary -> val data = snimek(); spust { zapis(stary, data) } }
        mapa.clear()
        jazyk = kod
        spust {
            val nactene = nacti(kod)
            synchronized(this) {
                if (jazyk != kod || nactene.isEmpty()) return@synchronized
                // starší (ze souboru) dopředu, mezitím přeložené za ně – zůstanou nejčerstvější
                val nove = LinkedHashMap(mapa)
                mapa.clear()
                nactene.forEach { (k, v) -> mapa[k] = v }
                mapa.putAll(nove)
            }
        }
    }

    /** Uloží hned (při zavření služby) a ukončí vlákno, až doběhne. */
    @Synchronized fun zavri() {
        jazyk?.let { j -> val data = snimek(); spust { zapis(j, data) } }
        io.shutdown()
    }

    private fun spust(prace: () -> Unit) { runCatching { io.execute { runCatching(prace) } } }

    private fun naplanujUlozeni() {
        if (slozka == null || ulozeniNaplanovano) return
        ulozeniNaplanovano = true
        runCatching {
            io.schedule(Runnable {
                val (j, data) = synchronized(this) { ulozeniNaplanovano = false; jazyk to snimek() }
                if (j != null) runCatching { zapis(j, data) }
            }, ZPOZDENI_ULOZENI_MS, TimeUnit.MILLISECONDS)
        }.onFailure { ulozeniNaplanovano = false }
    }

    /** Kopie v pořadí od nejdéle nepoužité (tak se i načte zpět). */
    private fun snimek(): List<Pair<String, String>> = mapa.entries.map { it.key to it.value }

    private fun soubor(kod: String) = slozka?.let { File(it, "preklady-${kod.filter(Char::isLetterOrDigit)}.json") }

    private fun zapis(kod: String, data: List<Pair<String, String>>) {
        val cil = soubor(kod) ?: return
        cil.parentFile?.mkdirs()
        val vety = JSONArray()
        data.forEach { (k, v) -> vety.put(JSONArray().put(k).put(v)) }
        val json = JSONObject().put("verze", VERZE).put("vety", vety).toString()
        // nejdřív dočasný soubor, pak přejmenovat – při pádu uprostřed zápisu zůstane starý celý
        val docasny = File(cil.parentFile, cil.name + ".tmp")
        docasny.writeText(json, Charsets.UTF_8)
        if (!docasny.renameTo(cil)) { cil.delete(); docasny.renameTo(cil) }
    }

    private fun nacti(kod: String): List<Pair<String, String>> {
        val f = soubor(kod)?.takeIf { it.isFile } ?: return emptyList()
        return runCatching {
            val o = JSONObject(f.readText(Charsets.UTF_8))
            if (o.optInt("verze") != VERZE) return emptyList()
            val vety = o.getJSONArray("vety")
            (0 until vety.length()).mapNotNull { i ->
                vety.optJSONArray(i)?.let { p -> p.optString(0).takeIf { it.isNotEmpty() }?.let { it to p.optString(1) } }
            }.filter { it.second.isNotBlank() }.takeLast(max)
        }.getOrDefault(emptyList())
    }

    companion object {
        /** Změna [klic] = jiná verze → staré soubory se nepoužijí. */
        private const val VERZE = 1
        private const val ZPOZDENI_ULOZENI_MS = 20_000L

        private val DIAKRITIKA = Regex("\\p{Mn}+")
        private val UVOZOVKY = Regex("[\"“”„«»]")
        private val INTERPUNKCE = Regex("[.,;:!…·•–—-]+")
        private val MEZERY = Regex("\\s+")

        /**
         * Sjednocený klíč anglické věty: malá písmena, bez diakritiky z OCR, jednotné apostrofy,
         * bez uvozovek a běžné interpunkce (tečka ≈ čárka ≈ vykřičník). Otazník zůstává –
         * „You're leaving?“ a „You're leaving.“ se mají překládat různě.
         */
        fun klic(text: String): String {
            var t = Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIAKRITIKA, "")
            t = t.lowercase().replace('’', '\'').replace('‘', '\'').replace('`', '\'')
            t = t.replace(UVOZOVKY, " ").replace(INTERPUNKCE, " ")
            t = t.replace(Regex("\\s*\\?+"), "?")
            return t.replace(MEZERY, " ").trim()
        }
    }
}
