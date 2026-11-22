package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class StavSpojeniTest {
    @Test fun fatalReasonSurvivesStopSelfAndLateFrameUntilNewAttemptOrManualStop() {
        val stav = StavObrazu()
        val reason = "PS5 chce PIN pro přihlášení – zatím nepodporováno ✘"
        stav.zacni(); stav.pripojeno(0); stav.snimek(1)
        // zastav() uvolní relaci; stopSelf/onDestroy pak vypne aktivni.
        stav.zacni(); stav.ukonciChybou(reason)
        val failed = stav.prehled(2, false, false, reason)
        assertEquals(UrovenSpojeni.CHYBA, failed.uroven)
        assertEquals(reason, failed.detail)
        assertTrue(Texty.spojeni("cs", failed).contains("PS5 chce PIN"))
        stav.snimek(3)
        assertEquals(UrovenSpojeni.CHYBA, stav.prehled(4, false, false, reason).uroven)
        stav.zacni()
        assertEquals(UrovenSpojeni.CEKANI, stav.prehled(5, true, false, "Připojuji").uroven)
        assertEquals(UrovenSpojeni.VYPNUTO, stav.prehled(6, false, false, "Remote Play zastaveno.").uroven)
    }

    @Test fun lateQuitOfOldSessionKeepsFatalReasonButManualStopClearsIt() {
        val stav = StavObrazu()
        val reason = "PS5 chce PIN pro přihlášení – zatím nepodporováno ✘"
        stav.zacni(); stav.pripojeno(0)
        stav.zastaveno(); stav.ukonciChybou(reason)
        // QuitEvent ukončované relace dorazí až po stopSelf a vede na zastav() → zastaveno().
        stav.zastaveno(); stav.snimek(1)
        val po = stav.prehled(2, false, false, "Remote Play ukončeno: stopped")
        assertEquals(UrovenSpojeni.CHYBA, po.uroven)
        assertEquals(reason, po.detail)
        // Ruční zastavení (ACTION_STOP) volá zacni() a chybu uklidí.
        stav.zacni(); stav.zastaveno()
        assertEquals(UrovenSpojeni.VYPNUTO, stav.prehled(3, false, false, "Remote Play zastaveno.").uroven)
    }

    @Test fun runningServiceAndHandshakeDoNotProveVideo() {
        val stav = StavObrazu()
        stav.zacni()
        assertEquals(UrovenSpojeni.CEKANI, stav.prehled(0, true, false, "Připojuji").uroven)
        stav.pripojeno(100)
        assertEquals("stavCekaObraz", stav.prehled(101, true, false, "").klic)
        assertEquals(UrovenSpojeni.CHYBA, stav.prehled(10_100, true, false, "").uroven)
    }

    @Test fun framesCanStopAndRecoverWithoutChangingServiceLifetime() {
        val stav = StavObrazu()
        stav.pripojeno(0)
        stav.snimek(100)
        assertEquals(UrovenSpojeni.OK, stav.prehled(200, true, false, "").uroven)
        val waiting = stav.prehled(29_100, true, false, "")
        assertEquals(29_000L, waiting.stariSnimkuMs)
        assertTrue(Texty.spojeni("cs", waiting).contains("před 29 s"))
        assertEquals("stavObrazUstal", stav.prehled(30_101, true, false, "").klic)
        stav.snimek(30_102)
        assertEquals(UrovenSpojeni.OK, stav.prehled(30_103, true, false, "").uroven)
    }

    @Test fun retryDiscardsPreviousFramesAndPreservesConcreteReason() {
        val stav = StavObrazu()
        stav.pripojeno(0); stav.snimek(10); stav.zacni()
        val s = stav.prehled(11, true, false, "PS5 používá jiné zařízení")
        assertEquals(UrovenSpojeni.CEKANI, s.uroven)
        assertEquals("PS5 používá jiné zařízení", s.detail)
        stav.pripojeno(20)
        assertEquals("stavCekaObraz", stav.prehled(21, true, false, "").klic)
    }

    @Test fun decoderFailureIsVisibleWhileServiceRunsAndNextFrameClearsIt() {
        val stav = StavObrazu()
        stav.pripojeno(0); stav.snimek(1); stav.chyba("Chyba obrazu: IllegalStateException")
        assertEquals(UrovenSpojeni.CHYBA, stav.prehled(2, true, false, "").uroven)
        assertTrue(stav.prehled(2, true, false, "").detail.contains("IllegalStateException"))
        stav.snimek(3)
        assertEquals(UrovenSpojeni.OK, stav.prehled(4, true, false, "").uroven)
    }

    @Test fun pausedAndStoppedAreNeverGreen() {
        val stav = StavObrazu()
        stav.pripojeno(0); stav.snimek(1)
        assertEquals(UrovenSpojeni.VYPNUTO, stav.prehled(2, false, false, "").uroven)
        assertEquals("stavPozastaveno", stav.prehled(2, true, true, "").klic)
    }

    @Test fun udpSendIsNotReceiptAndObsoleteWorkerCannotChangeNewTarget() {
        val stav = StavPrenosu()
        stav.reset(1)
        assertFalse(stav.stav.odeslano)
        stav.vysledek(1, null)
        assertTrue(stav.stav.odeslano)
        assertEquals("tvOdesilanoNeovereno", stav.stav.klic)
        stav.reset(2)
        stav.vysledek(1, java.net.SocketException("secret token"))
        assertNull(stav.stav.chyba)
        assertFalse(stav.stav.odeslano)
    }

    @Test fun concreteNetworkFailureRecoversAndNeverIncludesExceptionPayload() {
        val stav = StavPrenosu()
        stav.reset(1)
        stav.vysledek(1, java.net.UnknownHostException("secret token/account"))
        assertEquals("UnknownHostException", stav.stav.chyba)
        assertEquals("sitAdresa", stav.stav.duvodKlic)
        assertEquals("tvChybaOdesilani", stav.stav.klic)
        stav.vysledek(1, null)
        assertNull(stav.stav.chyba)
        assertEquals("tvOdesilanoNeovereno", stav.stav.klic)
    }

    @Test fun statusKeepsWholeReasonAndDistinguishesDataFromDeliveryInBothLanguages() {
        val reason = "PS5 používá jiné zařízení. " + "Konkrétní důvod. ".repeat(10)
        assertTrue(Texty.spojeni("cs", PrehledSpojeni("stavNavazuji", UrovenSpojeni.CEKANI, reason), podrobne = true).endsWith(reason))
        for (language in listOf("cs", "sk")) {
            val text = Texty.prenos(language, PrehledPrenosu(odeslano = true))
            assertTrue(text.contains(if (language == "cs") "nejsou potvrzené" else "nie sú potvrdené"))
        }
    }

    @Test fun normalUiCannotExpandNativeErrorIntoStackTraceButRecordKeepsDetails() {
        val trace = "Remote Play ukončeno: " + "dlouhá chyba ".repeat(100) + "\n" +
            "at native.function(file:123)\n".repeat(1000)
        val state = PrehledSpojeni("stavNavazuji", UrovenSpojeni.CEKANI, trace)
        val ui = Texty.spojeni("cs", state)
        assertTrue(ui.length < 400)
        assertTrue(ui.lines().size <= 3)
        assertFalse(ui.contains("native.function"))
        assertTrue(ui.contains("Diagnostice"))
        assertTrue(Texty.spojeni("cs", state, podrobne = true).endsWith(trace))
    }

    @Test fun normalUiUsesHumanMessageAndTechnicalTypeStaysInRecord() {
        val error = PrehledPrenosu(chyba = "UnknownHostException", duvodKlic = "sitAdresa")
        val ui = Texty.prenos("cs", error)
        assertTrue(ui.contains("Zkontroluj vybranou TV a její IP"))
        assertFalse(ui.contains("Exception"))
        assertTrue(Texty.prenos("cs", error, podrobne = true).contains("UnknownHostException"))
    }
}
