package cz.titulky.tv

/**
 * Režim titulků na obrazovce hraní: Standardní | Příběhový | GTA VI.
 *
 * Uloženo je to dál jako dvě nezávislé volby (Nastaveni.pribehovy, Volby.gta), aby
 * služba ani přijímače nic nového znát nemusely. GTA VI = standardní časování
 * + volby odpovědí v GTA stylu (ikony tlačítek, jméno NPC) + vzhled titulků na TV.
 */
enum class RezimTitulku(val klic: String, val napovedaKlic: String) {
    STANDARD("rezimStandard", "rezimStandardNap"),
    PRIBEH("rezimPribeh", "rezimPribehNap"),
    GTA("rezimGta", "rezimGtaNap");

    /** Jak se má režim promítnout do uložených voleb. */
    data class Volby(val pribehovy: Boolean, val gta: Boolean, val volbyZapnout: Boolean)

    fun volby() = when (this) {
        STANDARD -> Volby(pribehovy = false, gta = false, volbyZapnout = false)
        PRIBEH -> Volby(pribehovy = true, gta = false, volbyZapnout = false)
        // GTA VI bez voleb odpovědí nedává smysl → zapnout (vypnout jdou pak ručně)
        GTA -> Volby(pribehovy = false, gta = true, volbyZapnout = true)
    }

    companion object {
        /** GTA má přednost: starší build mohl mít zapnuté obojí. */
        fun z(pribehovy: Boolean, gta: Boolean) = when {
            gta -> GTA
            pribehovy -> PRIBEH
            else -> STANDARD
        }
    }
}
