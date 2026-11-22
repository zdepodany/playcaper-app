package cz.titulky.tv

/**
 * Online překladové služby, jejichž pořadí a zapnutí si volí uživatel (Nastavení → Překlad → Služby).
 * Offline překlad v telefonu (ML Kit) v seznamu není: je to vždy poslední záloha a nejde vypnout.
 */
object SluzbyPrekladu {
    const val DEEPL = "deepl"
    const val GOOGLE = "google"
    const val MICROSOFT = "microsoft"
    const val MYMEMORY = "mymemory"

    /** Výchozí pořadí (stejné jako před zavedením nastavení). */
    val VYCHOZI = listOf(DEEPL, GOOGLE, MICROSOFT, MYMEMORY)

    /** Příběhový režim má časový rozpočet: zkouší jen tolik prvních zapnutých služeb. */
    const val PRIBEH_MAX = 2

    /** Uložené pořadí → platné: neznámá id a duplicity pryč, chybějící (nové služby) na konec ve výchozím pořadí. */
    fun normalizuj(ulozene: List<String>): List<String> {
        val platne = ulozene.filter { it in VYCHOZI }.distinct()
        return platne + VYCHOZI.filter { it !in platne }
    }

    /** Služby, které překladač opravdu zkusí, v pořadí. DeepL jen s klíčem a když se nemá přeskočit. */
    fun aktivni(poradi: List<String>, vypnute: Set<String>, maDeeplKlic: Boolean, bezDeepl: Boolean = false): List<String> =
        normalizuj(poradi).filter { id ->
            id !in vypnute && (id != DEEPL || (maDeeplKlic && !bezDeepl))
        }

    /** Přetažení služby z pozice [z] na [na] (indexy v normalizovaném pořadí); mimo rozsah beze změny. */
    fun presun(poradi: List<String>, z: Int, na: Int): List<String> {
        val seznam = normalizuj(poradi).toMutableList()
        if (z !in seznam.indices || na !in seznam.indices) return seznam
        seznam.add(na, seznam.removeAt(z))
        return seznam
    }

    fun nazev(id: String) = when (id) {
        DEEPL -> "DeepL"
        GOOGLE -> "Google"
        MICROSOFT -> "Microsoft"
        MYMEMORY -> "MyMemory"
        else -> id
    }
}
