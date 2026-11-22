package cz.titulky.tv

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class TvProfilyTest {
    @Test fun lgASamsungSePoznajiPodleId() {
        val p = store()
        val lg = tv("lg-001122334455")
        val samsung = tv("samsung-001122334455")
        assertEquals(TvPlatforma.WEBOS, p.platformaPrijimace(lg.id))
        assertEquals(TvPlatforma.SAMSUNG, p.platformaPrijimace(samsung.id))
        p.zapamatuj(lg.id, lg.jmeno, lg.ip)
        p.zapamatuj(samsung.id, samsung.jmeno, samsung.ip)
        assertEquals(TvPlatforma.WEBOS, p.najdi(lg.id)!!.platforma)
        assertEquals(TvPlatforma.SAMSUNG, p.najdi(samsung.id)!!.platforma)
    }
    private val files = mutableMapOf<String, SharedPreferences>()
    private val legacy = memory()
    private val metadata = memory()
    private fun store() = TvProfily(metadata, legacy) { files.getOrPut(it) { memory() } }
    private fun tv(id: String, ip: String = "127.0.0.1") = Propojeni.Televize(id, "TV $id", ip)

    @Test fun twoTvsKeepSeparateLayoutAndFramesAfterRestart() {
        val p = store()
        p.zapamatuj("A", "Obývák", "10.0.0.1", TvPlatforma.WEBOS)
        p.vyber("A")
        p.nastaveni("A").edit().putFloat("tvX", .3f).putBoolean("titRucne", true).apply()
        p.zapamatuj("B", "Ložnice", "10.0.0.2", TvPlatforma.WEBOS)
        p.vyber("B")
        assertFalse(p.nastaveni("B").contains("tvX"))
        p.nastaveni("B").edit().putFloat("tvX", .8f).apply()
        val restarted = store()
        restarted.vyber("A")
        assertEquals(.3f, restarted.nastaveni("A").getFloat("tvX", 0f))
        assertTrue(restarted.nastaveni("A").getBoolean("titRucne", false))
        assertEquals(.8f, restarted.nastaveni("B").getFloat("tvX", 0f))
        assertEquals(2, restarted.seznam().size)
        TvProfily.lokalne()
        assertNull(TvProfily.aktivniId)
        assertEquals("A", restarted.posledniId)
    }

    @Test fun legacySettingsMigrateOnlyToPreviousIdAndDoNotGuessPlatform() {
        legacy.edit().putString("tvId", "A").putFloat("tvX", .4f).putString("jazyk", "cs").putString("psnToken", "private").apply()
        val p = store()
        p.zapamatuj("B", "LG jiná", "10.0.0.2")
        assertFalse(p.nastaveni("B").contains("tvX"))
        p.zapamatuj("A", "LG PlayCaption", "10.0.0.1")
        assertEquals(TvPlatforma.NEZNAMA, p.najdi("A")!!.platforma)
        assertEquals(.4f, p.nastaveni("A").getFloat("tvX", 0f))
        assertFalse(p.nastaveni("A").contains("jazyk"))
        assertFalse(p.nastaveni("A").contains("psnToken"))
        p.nastaveni("A").edit().putFloat("tvX", .7f).apply()
        p.zapamatuj("A", "Obývák", "10.0.0.8", TvPlatforma.WEBOS)
        assertEquals(.7f, p.nastaveni("A").getFloat("tvX", 0f))
        assertEquals("cs", legacy.getString("jazyk", null))
        assertEquals(.4f, legacy.getFloat("tvX", 0f))
    }

    @Test fun changedAddressKeepsIdPlatformAndSettings() {
        val p = store()
        p.zapamatuj("A", "Obývák", "10.0.0.1", TvPlatforma.WEBOS)
        p.nastaveni("A").edit().putFloat("tvSirka", .9f).apply()
        p.zapamatuj("A", "Nové jméno", "10.0.0.9")
        assertEquals(1, p.seznam().size)
        assertEquals(TvPlatforma.WEBOS, p.najdi("A")!!.platforma)
        assertEquals("10.0.0.9", p.najdi("A")!!.ip)
        assertEquals(.9f, p.nastaveni("A").getFloat("tvSirka", 0f))
    }

    @Test fun noAutomaticRedirectToDifferentTvOrUnknownPlatform() {
        val p = store()
        assertEquals("A", p.automaticky(listOf(tv("A")))?.id)
        assertNull(p.automaticky(listOf(tv("A"), tv("B"))))
        p.zapamatuj("A", "A", "10.0.0.1", TvPlatforma.WEBOS)
        p.vyber("A")
        assertNull(p.automaticky(listOf(tv("B"))))
        p.platforma = TvPlatforma.SAMSUNG
        assertNull(p.automaticky(listOf(tv("A"))))
        p.platforma = TvPlatforma.WEBOS
        assertEquals("10.0.0.8", p.automaticky(listOf(tv("A", "10.0.0.8")))?.ip)
        p.pouzeTelefon = true
        assertNull(store().automaticky(listOf(tv("A"))))
        p.zapamatujVyber("A")
        assertFalse(p.pouzeTelefon)
        TvProfily.lokalne()
    }

    @Test fun legacyIdDoesNotFallBackToOnlyOtherTv() {
        legacy.edit().putString("tvId", "A").apply()
        assertNull(store().automaticky(listOf(tv("B"))))
        assertEquals("A", store().automaticky(listOf(tv("A")))?.id)
    }

    /** Malý paměťový SharedPreferences, bez Android runtime a nové testovací knihovny. */
    private fun memory(): SharedPreferences {
        val data = mutableMapOf<String, Any?>()
        lateinit var prefs: SharedPreferences
        lateinit var editor: SharedPreferences.Editor
        val pending = mutableMapOf<String, Any?>()
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, m, a ->
            when (m.name) {
                "apply", "commit" -> { pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }; pending.clear(); if (m.name == "commit") true else null }
                "remove" -> { pending[a!![0] as String] = null; editor }
                else -> { pending[a!![0] as String] = a[1]; editor }
            }
        } as SharedPreferences.Editor
        prefs = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, a ->
            when (m.name) {
                "edit" -> editor
                "getAll" -> data.toMap()
                "contains" -> data.containsKey(a!![0])
                else -> data[a!![0]] ?: a[1]
            }
        } as SharedPreferences
        return prefs
    }
}
