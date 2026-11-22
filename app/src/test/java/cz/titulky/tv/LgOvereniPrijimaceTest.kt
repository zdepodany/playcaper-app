package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LgOvereniPrijimaceTest {
    private val tv = Propojeni.Televize("lg-001122334455", "LG", "127.0.0.1", false)

    @Test fun targetSelectionUsesOnlyKnownLgAndDoesNotGuessBetweenSeveralTvs() {
        val lg = TvProfil(tv.id, tv.jmeno, tv.ip, TvPlatforma.WEBOS)
        val other = lg.copy(id = "lg-ffeeddccbbaa", ip = "127.0.0.2")
        val neznama = lg.copy(id = "neznama-tv", platforma = TvPlatforma.NEZNAMA)
        val samsung = lg.copy(id = "samsung-001122334455", platforma = TvPlatforma.SAMSUNG)
        assertNull(LgOvereniPrijimace.cil(null, "", "", listOf(neznama, samsung), neznama.id))
        assertNull(LgOvereniPrijimace.cil(null, "", "", listOf(lg, other), null))
        assertEquals(tv, LgOvereniPrijimace.cil(null, "", "", listOf(lg, neznama), neznama.id))
        assertEquals(other.ip, LgOvereniPrijimace.cil(null, "", other.ip, listOf(lg, other), lg.id)?.ip)
        assertNull(LgOvereniPrijimace.cil(null, "", "127.0.0.3", listOf(lg), lg.id))
    }

    @Test fun verifiedMissingPackageDoesNotFallBackToStaleSavedReceiver() {
        val saved = TvProfil(tv.id, tv.jmeno, tv.ip, TvPlatforma.WEBOS)
        val missing = LgKontrola(tv.id, null, "0.2.3", LgStavVerze.CHYBI)
        assertNull(LgOvereniPrijimace.cil(missing, tv.ip, tv.ip, listOf(saved), tv.id))
        val installed = missing.copy(verze = "0.2.3", stav = LgStavVerze.AKTUALNI)
        assertEquals(tv.ip, LgOvereniPrijimace.cil(installed, tv.ip, tv.ip, emptyList(), null)?.ip)
    }

    @Test fun installationAndLaunchAloneDoNotConfirmRunningReceiver() {
        val state = LgOvereniPrijimace()
        assertTrue(LgInstalaceVysledek("0.2.3", true).spusteno)
        assertNull(state.vysledek)
        val attempt = state.zacni(tv)
        assertNull(state.vysledek)
        assertTrue(state.bezi)
        assertTrue(state.dokonci(attempt, emptyList(), 100))
        assertFalse(state.vysledek!!.nalezen)
        assertFalse(state.bezi)
    }

    @Test fun onlySameIdentityAndIpConfirmTheIntendedLg() {
        val state = LgOvereniPrijimace()
        val wrong = listOf(tv.copy(id = "android-tv"), tv.copy(id = "samsung-001122334455"),
            tv.copy(ip = "127.0.0.2"))
        state.dokonci(state.zacni(tv), wrong, 100)
        assertFalse(state.vysledek!!.nalezen)
        state.dokonci(state.zacni(tv), wrong + tv.copy(jmeno = "Aktuální název"), 200)
        assertTrue(state.vysledek!!.nalezen)
        assertEquals(200L, state.vysledek!!.overenoKdy)
    }

    @Test fun cancelledOrOlderSearchCannotChangeNewTarget() {
        val state = LgOvereniPrijimace()
        val old = state.zacni(tv)
        val target = tv.copy(id = "lg-ffeeddccbbaa", ip = "127.0.0.2")
        val current = state.zacni(target)
        assertFalse(state.dokonci(old, listOf(tv), 100))
        assertTrue(state.bezi)
        assertNull(state.vysledek)
        state.zrus()
        assertFalse(state.dokonci(current, listOf(target), 200))
        assertFalse(state.bezi)
        assertNull(state.vysledek)
    }

    @Test fun realDirectDiscoveryConfirmsReceiverWithoutChangingOutputOrSubtitleCache() {
        DatagramSocket(Propojeni.PORT_TITULKY, InetAddress.getByName(tv.ip)).use { socket ->
            socket.soTimeout = 2000
            val worker = Executors.newSingleThreadExecutor()
            try {
                val response = worker.submit {
                    val packet = DatagramPacket(ByteArray(512), 512)
                    socket.receive(packet)
                    val query = String(packet.data, 0, packet.length, Charsets.UTF_8).split('\t')
                    assertEquals("PLAYCAPTION_FIND", query[0])
                    val data = "PLAYCAPTION_TV\t${tv.id}\tLG test\t0\t${query[1]}".toByteArray()
                    socket.send(DatagramPacket(data, data.size, packet.address, packet.port))
                }
                val output = Propojeni.Vysilac.cilIp
                val subtitle = TelefonTitulky.text
                val state = LgOvereniPrijimace()
                val attempt = state.zacni(tv)
                assertTrue(state.dokonci(attempt, Propojeni.hledejVSiti(350, listOf(tv)), 100))
                response.get(3, TimeUnit.SECONDS)
                assertTrue(state.vysledek!!.nalezen)
                assertEquals(output, Propojeni.Vysilac.cilIp)
                assertEquals(subtitle, TelefonTitulky.text)
            } finally { worker.shutdownNow() }
        }
    }

    @Test fun olderLgAnnouncementStillWorksWithoutDirectReplyOrNewAck() {
        DatagramSocket(Propojeni.PORT_TITULKY, InetAddress.getByName(tv.ip)).use { socket ->
            socket.soTimeout = 2000
            val worker = Executors.newSingleThreadExecutor()
            try {
                val response = worker.submit {
                    val request = DatagramPacket(ByteArray(512), 512)
                    socket.receive(request)
                    val data = "PLAYCAPTION_TV\t${tv.id}\tStarší LG\t0".toByteArray()
                    socket.send(DatagramPacket(data, data.size, request.address, Propojeni.PORT_HLEDANI))
                }
                val state = LgOvereniPrijimace()
                state.dokonci(state.zacni(tv), Propojeni.hledejVSiti(350, listOf(tv)), 100)
                response.get(3, TimeUnit.SECONDS)
                assertTrue(state.vysledek!!.nalezen)
            } finally { worker.shutdownNow() }
        }
    }
}
