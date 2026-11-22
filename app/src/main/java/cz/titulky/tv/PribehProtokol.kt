package cz.titulky.tv

internal object PribehProtokol {
    data class Odpoved(val id: String, val stav: String)
    data class Titulek(val id: String, val text: String, val pauza: Boolean, val rozvrh: FloatArray,
        val barvy: IntArray, val procenta: Int?)
    data class Zprava(val id: String, val titulek: Titulek? = null)
    private val id = Regex("[a-f0-9]{32}\\.\\d{1,10}\\.\\d{1,10}\\.\\d{1,10}")
    /** PCS1 má i párování; Story přijímá pouze platný token a přesný tvar paketu. */
    fun prijmi(text: String): Zprava? {
        if (text.toByteArray(Charsets.UTF_8).size > 8192) return null
        val p = text.split('\t')
        if (p.size < 2 || !id.matches(p[1])) return null
        if (p[0] == "PCQ1") return if (p.size == 2) Zprava(p[1]) else null
        if (p[0] != "PCS1" || p.size !in 9..10 || p[2] !in listOf("0", "1") || p[3].length > 4096) return null
        val rozvrh = p.subList(4, 8).map { it.toFloatOrNull()?.takeIf { n -> n.isFinite() } ?: return null }.toFloatArray()
        rozvrh[0] = rozvrh[0].coerceIn(0f, 1f); rozvrh[1] = rozvrh[1].coerceIn(.05f, 1f)
        rozvrh[2] = rozvrh[2].coerceIn(.2f, 1f); rozvrh[3] = rozvrh[3].coerceIn(.5f, 2.5f)
        val barvy = if (p[8].isEmpty()) intArrayOf() else {
            p[8].split(',').take(64).map {
                val n = it.toLongOrNull()?.takeIf { n -> n in -2147483648L..4294967295L } ?: return null
                n.toInt()
            }.toIntArray()
        }
        val procenta = if (p.size == 10) {
            if (!Regex("D:\\d{2,3}").matches(p[9])) return null
            p[9].substring(2).toInt().takeIf { it in CasovaniPribehu.MIN_PROCENT..CasovaniPribehu.MAX_PROCENT } ?: return null
        } else null
        return Zprava(p[1], Titulek(p[1], Propojeni.bezVzhledu(Propojeni.odEscapuj(p[3])), p[2] == "1", rozvrh, barvy, procenta))
    }
    fun odpoved(text: String): Odpoved? {
        val p = text.split('\t')
        if (p.size != 3 || p[0] != "PCA1" || !id.matches(p[1]) || p[2] !in listOf("ready", "reading", "done")) return null
        return Odpoved(p[1], p[2])
    }
    fun dotaz(token: String) = "PCQ1\t$token"
    fun titulek(token: String, text: String, pauza: Boolean, rozvrh: FloatArray, barvy: IntArray,
        procenta: Int = CasovaniPribehu.VYCHOZI_PROCENTA): String {
        require(id.matches(token) && rozvrh.size == 4 && rozvrh.all { it.isFinite() })
        require(procenta in CasovaniPribehu.MIN_PROCENT..CasovaniPribehu.MAX_PROCENT)
        val escaped = text.replace("\\", "\\\\").replace("\t", " ").replace("\n", "\\n")
        require(escaped.length <= 4096) { "Příběhový titulek přesahuje limit přijímače" }
        val packet = "PCS1\t$token\t${if (pauza) 1 else 0}\t$escaped\t${rozvrh.joinToString("\t")}\t${barvy.joinToString(",")}\tD:$procenta"
        require(packet.toByteArray(Charsets.UTF_8).size <= 8192) { "Příběhový titulek přesahuje limit UDP" }
        return packet
    }
}
