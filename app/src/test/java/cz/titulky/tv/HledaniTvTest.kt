package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HledaniTvTest {
    private val lg = Propojeni.Televize("lg-001122334455", "LG", "127.0.0.1", false)

    private fun odpovidac(reply: (String) -> String?, broadcast: Boolean = false, test: () -> Unit) {
        DatagramSocket(Propojeni.PORT_TITULKY, InetAddress.getByName("127.0.0.1")).use { socket ->
            socket.soTimeout = 2000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val task = executor.submit {
                    val p = DatagramPacket(ByteArray(512), 512)
                    socket.receive(p)
                    val query = String(p.data, 0, p.length, Charsets.UTF_8).split('\t')
                    assertEquals("PLAYCAPTION_FIND", query[0])
                    assertTrue(query[1].matches(Regex("[a-f0-9]{32}")))
                    reply(query[1])?.toByteArray(Charsets.UTF_8)?.let { socket.send(DatagramPacket(it, it.size, p.address, if (broadcast) Propojeni.PORT_HLEDANI else p.port)) }
                }
                test()
                task.get(3, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun storedLgCanBeFoundWithoutBroadcast() {
        odpovidac({ "PLAYCAPTION_TV\t${lg.id}\tLG test\t0\t$it" }) {
            val found = Propojeni.hledejVSiti(300, listOf(lg))
            assertEquals(listOf(lg.copy(jmeno = "LG test")), found.filter { it.ip == lg.ip })
        }
    }

    @Test fun directReplyMustMatchSavedIdentityAndCurrentNonce() {
        for (wrong in listOf("identity", "nonce")) {
            odpovidac({ nonce -> "PLAYCAPTION_TV\t${if (wrong == "identity") "lg-ffeeddccbbaa" else lg.id}\tLG\t0\t${if (wrong == "nonce") "b".repeat(32) else nonce}" }) {
                assertTrue(Propojeni.hledejVSiti(300, listOf(lg)).none { it.ip == lg.ip })
            }
        }
    }

    @Test fun broadcastAnnouncementStillWorks() {
        odpovidac({ "PLAYCAPTION_TV\tjina-tv\tJiná TV\t1" }, broadcast = true) {
            val found = Propojeni.hledejVSiti(300, listOf(lg))
            assertEquals(listOf(Propojeni.Televize("jina-tv", "Jiná TV", "127.0.0.1", true)), found.filter { it.ip == lg.ip })
        }
    }

    @Test fun samsungBroadcastWithVersionFieldStillWorks() {
        // Samsung 1.3.0 posílá v broadcastu 5. pole s verzí; to není nonce a nesmí se zahodit.
        odpovidac({ "PLAYCAPTION_TV\tsamsung-001122334455\tSamsung TV\t0\t1.3.0" }, broadcast = true) {
            val found = Propojeni.hledejVSiti(300, listOf(lg))
            assertEquals(listOf(Propojeni.Televize("samsung-001122334455", "Samsung TV", "127.0.0.1", false, "1.3.0")),
                found.filter { it.ip == lg.ip })
        }
    }

    @Test fun silentStoredTvIsNotReportedAsOnline() {
        odpovidac({ null }) { assertTrue(Propojeni.hledejVSiti(300, listOf(lg)).none { it.ip == lg.ip }) }
    }

    @Test fun directReplyWithoutNonceCannotConfirmStoredTv() {
        odpovidac({ "PLAYCAPTION_TV\t${lg.id}\tLG\t0" }) {
            assertTrue(Propojeni.hledejVSiti(300, listOf(lg)).none { it.ip == lg.ip })
        }
    }

    @Test fun concurrentSearchesReceiveTheirOwnDirectReply() {
        DatagramSocket(Propojeni.PORT_TITULKY, InetAddress.getByName(lg.ip)).use { socket ->
            socket.soTimeout = 2000
            val executor = Executors.newFixedThreadPool(3)
            try {
                val server = executor.submit {
                    val requests = (1..2).map {
                        DatagramPacket(ByteArray(512), 512).also { socket.receive(it) }
                    }
                    assertNotEquals(requests[0].port, requests[1].port)
                    requests.forEach { p ->
                        val nonce = String(p.data, 0, p.length, Charsets.UTF_8).substringAfter('\t')
                        val data = "PLAYCAPTION_TV\t${lg.id}\tLG\t0\t$nonce".toByteArray()
                        socket.send(DatagramPacket(data, data.size, p.address, p.port))
                    }
                }
                val searches = (1..2).map { executor.submit<List<Propojeni.Televize>> { Propojeni.hledejVSiti(500, listOf(lg)) } }
                searches.forEach { assertTrue(it.get(3, TimeUnit.SECONDS).any { tv -> tv.id == lg.id }) }
                server.get(3, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }
}
