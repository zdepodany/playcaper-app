package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class PrihlaseniTelefonemTest {
    private val ulohy = LinkedBlockingQueue<Runnable>()
    private val id = "AQIDBAUGBwg="
    private val spoustec = Executor { ulohy.add(it) }
    private fun dokonci() = requireNotNull(ulohy.poll(2, TimeUnit.SECONDS)).run()
    private fun cesta(kod: String, server: PrihlaseniTelefonem? = null) =
        "/kod?" + (server?.let { it.cesta.substringAfter('?') + "&" } ?: "") + "u=" + URLEncoder.encode("${PsnPrihlaseni.PRESMEROVANI}?code=$kod", "UTF-8")

    private fun nacti(cesta: String, host: String = "127.0.0.1",
        presmeruje: Boolean = cesta.contains("code%3D") && cesta.contains("p=")): String {
        val doKdy = System.nanoTime() + 2_000_000_000L
        while (true) {
            try {
                val c = URL("http://$host:${PrihlaseniTelefonem.PORT}$cesta").openConnection() as HttpURLConnection
                c.connectTimeout = 200; c.readTimeout = 2000
                try {
                    val telo = c.inputStream.bufferedReader().use { it.readText() }
                    assertEquals("no-store", c.getHeaderField("Cache-Control"))
                    if (presmeruje) {
                        assertEquals("/stav", c.url.path)
                        assertFalse(c.url.query.contains("code"))
                    }
                    return telo
                }
                finally { c.disconnect() }
            } catch (e: java.net.ConnectException) {
                if (System.nanoTime() > doKdy) throw e
                Thread.sleep(10)
            }
        }
    }

    @Test fun prijetiKoduSamoNepotvrzujeUlozeniUctu() {
        var ulozeno = false
        val pokus = PsnPokus({ id }, { ulozeno = true; true }, spoustec)
        val server = PrihlaseniTelefonem(true, pokus)
        try {
            server.spust()
            val html = nacti(cesta("test-code", server))
            assertFalse("Před uložením nesmí být potvrzený účet", html.contains("Účet je uložený"))
            assertTrue(html.contains("Ověřuji přihlášení"))
            assertTrue(html.contains("http-equiv=refresh"))
            assertFalse(ulozeno)
            dokonci()
            assertTrue(ulozeno)
            assertTrue(nacti("/stav").contains("Účet je uložený"))
            assertFalse(nacti("/stav").contains("http-equiv=refresh"))
            nacti(cesta("test-code", server))
            assertTrue(ulohy.isEmpty())
        } finally { server.zastav() }
    }

    @Test fun obnovenaAdresaSeStarymKodemNezahajiNovePrihlaseni() {
        val pokus = PsnPokus({ throw PsnHttpChyba("token", 400) }, { true }, spoustec)
        val server = PrihlaseniTelefonem(true, pokus)
        try {
            server.spust()
            nacti(cesta("stary-kod-z-historie"))
            assertEquals(PsnPokus.Stav.CEKA, pokus.stav)
            assertTrue(ulohy.isEmpty())
            val html = nacti("/stav")
            assertFalse(html.contains("Sony odmítlo"))
            assertFalse(html.contains("HTTP 400"))
            assertTrue(html.contains("<form"))
        } finally { server.zastav() }
    }

    @Test fun formularJinehoPokusuNeodesleKodANeovlivniAktualniPrihlaseni() {
        val stary = PrihlaseniTelefonem(true, PsnPokus({ id }, { true }, spoustec))
        val pokus = PsnPokus({ id }, { true }, spoustec)
        val server = PrihlaseniTelefonem(true, pokus)
        assertNotEquals(stary.cesta, server.cesta)
        try {
            server.spust()
            val html = nacti(server.cesta)
            assertTrue(html.contains("name=\"p\" value=\"${server.cesta.substringAfter("p=")}\""))
            nacti(cesta("stary-kod", stary), presmeruje = false)
            assertEquals(PsnPokus.Stav.CEKA, pokus.stav)
            assertTrue(ulohy.isEmpty())
            nacti(cesta("novy-kod", server))
            assertEquals(PsnPokus.Stav.OVERUJE, pokus.stav)
            dokonci()
            assertEquals(PsnPokus.Stav.HOTOVO, pokus.stav)
            nacti(cesta("stary-kod", stary), presmeruje = false)
            assertEquals(PsnPokus.Stav.HOTOVO, pokus.stav)
            assertTrue(ulohy.isEmpty())
        } finally { server.zastav() }
    }

    @Test fun novaStrankaNeprevezmeChybuPredchozihoPokusu() {
        val staryPokus = PsnPokus({ throw PsnHttpChyba("token", 400) }, { true }, spoustec)
        val stary = PrihlaseniTelefonem(true, staryPokus)
        staryPokus.prihlas("prosly-kod"); dokonci()
        assertTrue(stary.stranka().contains("HTTP 400"))
        val pokus = PsnPokus({ id }, { true }, spoustec)
        val server = PrihlaseniTelefonem(true, pokus)
        try {
            server.spust()
            nacti(cesta("prosly-kod", stary), presmeruje = false)
            for (cesta in listOf(server.cesta, "/stav?${stary.cesta.substringAfter('?')}")) {
                val html = nacti(cesta)
                assertTrue(html.contains("<form"))
                assertFalse(html.contains("HTTP 400"))
                assertFalse(html.contains("Sony odmítlo"))
            }
            assertEquals(PsnPokus.Stav.CEKA, pokus.stav)
            assertTrue(ulohy.isEmpty())
        } finally { server.zastav() }
    }

    @Test fun chybaZustaneViditelnaAZnovuLzeZadatNovyKod() {
        val pokus = PsnPokus({ if (it == "stary") throw PsnHttpChyba("token", 400) else id }, { true }, spoustec)
        val server = PrihlaseniTelefonem(true, pokus)
        try {
            server.spust()
            nacti(cesta("stary", server)); dokonci()
            repeat(2) {
                val html = nacti("/stav")
                assertTrue(html.contains("Sony odmítlo přihlašovací kód"))
                assertTrue(html.contains("HTTP 400"))
                assertTrue(html.contains("<form"))
                assertFalse(html.contains("Hotovo!"))
            }
            assertTrue(nacti(cesta("novy", server)).contains("Ověřuji"))
            dokonci()
            assertTrue(nacti("/stav").contains("Účet je uložený"))
        } finally { server.zastav() }
    }

    @Test fun neplatnyVstupNespustiOverovani() {
        val server = PrihlaseniTelefonem(true, PsnPokus({ id }, { true }, spoustec))
        try {
            server.spust()
            val html = nacti("/kod?${server.cesta.substringAfter('?')}&u=neplatny-vstup")
            assertTrue(html.contains("V adrese jsem nenašel kód"))
            assertTrue(nacti("/kod?${server.cesta.substringAfter('?')}&u=%zz").contains("V adrese jsem nenašel kód"))
            assertTrue(ulohy.isEmpty())
        } finally { server.zastav() }
    }

    @Test fun strankaJeJenProTelefon() {
        val pokus = PsnPokus({ id }, { true }, spoustec)
        val server = PrihlaseniTelefonem(true, pokus)
        val html = server.stranka()
        assertTrue(html.contains(PsnPrihlaseni.ADRESA_PRIHLASENI))
        assertFalse(html.contains("televiz"))
        pokus.prihlas("z-telefonu"); dokonci()
        assertTrue(server.stranka().contains("Účet je uložený"))
    }

    @Test fun strankaNeniVidetZDomaciSite() {
        val ip = java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isPointToPoint && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.hostAddress
        org.junit.Assume.assumeNotNull(ip)
        val server = PrihlaseniTelefonem(true, PsnPokus({ id }, { true }, spoustec))
        try {
            server.spust()
            assertTrue(nacti("/stav").contains("PlayCaption"))
            val pripojeno = runCatching {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress(ip, PrihlaseniTelefonem.PORT), 300) }
            }.isSuccess
            assertFalse(pripojeno)
        } finally { server.zastav() }
    }

    @Test fun anglickaStrankaTakeCekaAZobraziDuvodChyby() {
        val pokus = PsnPokus({ throw java.net.SocketTimeoutException("secret-code") }, { true }, spoustec)
        val server = PrihlaseniTelefonem(false, pokus)
        pokus.prihlas("secret-code")
        assertTrue(server.stranka().contains("Verifying your PlayStation"))
        dokonci()
        val html = server.stranka()
        assertTrue(html.contains("Sony did not respond in time"))
        assertFalse(html.contains("secret-code"))
        assertFalse(html.contains("Done!"))
    }

    @Test fun obsazenyPortNeoznaciPrihlasovaciStrankuZaPripravenou() {
        val chyba = CountDownLatch(1)
        val pokus = PsnPokus({ id }, { true }, spoustec) { chyba.countDown() }
        ServerSocket(PrihlaseniTelefonem.PORT, 50, java.net.InetAddress.getLoopbackAddress()).use {
            val server = PrihlaseniTelefonem(true, pokus)
            try {
                server.spust()
                assertTrue(chyba.await(2, TimeUnit.SECONDS))
                assertEquals(PsnPokus.Stav.CHYBA, pokus.stav)
                assertEquals("psnStranka", pokus.chyba)
            } finally { server.zastav() }
        }
    }
}
