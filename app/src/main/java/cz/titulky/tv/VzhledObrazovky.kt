package cz.titulky.tv

/**
 * Barvy obrazovky hraní v telefonu. Základní = tmavě šedá se zelenou,
 * GTA VI = noc nad Vice City: fialová, západ slunce (růžová → oranžová) a tyrkysová.
 * Jen paleta – žádné logo ani písmo ze hry.
 * Barvy rámečků (zelená/modrá/žlutá) se nemění: musí sedět s rámečky na televizi.
 */
internal data class VzhledObrazovky(
    val pozadi: Int,
    /** Horní panel – přechod shora dolů. */
    val panel: IntArray,
    val karta: Int,
    val segmentPozadi: Int,
    val segmentText: Int,
    /** Vybraný režim – přechod zleva doprava (dvě stejné barvy = plná). */
    val akcent: IntArray,
    val tlumena: Int,
    val tvVybrano: Int,
    val obrazovka: Int,
    val obrazovkaOkraj: Int,
    /** Úzké tučné písmo nadpisů (sans-serif-condensed). */
    val uzkePismo: Boolean,
    /** Tenký pruh pod horním panelem (null = žádný). */
    val pruh: IntArray?
) {
    companion object {
        val ZAKLAD = VzhledObrazovky(
            pozadi = 0xFF000000.toInt(),
            panel = intArrayOf(0xFF111114.toInt(), 0xFF111114.toInt()),
            karta = 0xFF1B1B21.toInt(),
            segmentPozadi = 0xFF2A2A31.toInt(),
            segmentText = 0xFFD4D4DC.toInt(),
            akcent = intArrayOf(0xFF3CFF6E.toInt(), 0xFF3CFF6E.toInt()),
            tlumena = 0xFFA3A3AF.toInt(),
            tvVybrano = 0xFF14A23C.toInt(),
            obrazovka = 0xFF1A1C26.toInt(),
            obrazovkaOkraj = 0xFF555A78.toInt(),
            uzkePismo = false,
            pruh = null
        )

        val GTA = VzhledObrazovky(
            pozadi = 0xFF0E0718.toInt(),
            panel = intArrayOf(0xFF1C0B2E.toInt(), 0xFF3A0F3C.toInt()),
            karta = 0xFF22112E.toInt(),
            segmentPozadi = 0xFF34173F.toInt(),
            segmentText = 0xFFF2D7EA.toInt(),
            akcent = intArrayOf(0xFFFF3D8B.toInt(), 0xFFFFA23C.toInt()),
            tlumena = 0xFFC7A6D3.toInt(),
            tvVybrano = 0xFF12B5A8.toInt(),
            obrazovka = 0xFF190D29.toInt(),
            obrazovkaOkraj = 0xFF8A3FA6.toInt(),
            uzkePismo = true,
            pruh = intArrayOf(0xFFFF3D8B.toInt(), 0xFFFFA23C.toInt(), 0xFF12B5A8.toInt())
        )

        fun pro(gta: Boolean) = if (gta) GTA else ZAKLAD
    }
}
