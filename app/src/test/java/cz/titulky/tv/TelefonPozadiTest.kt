package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class TelefonPozadiTest {
    @Test fun selectedLgKeepsActiveSenderAfterScreenOff() {
        assertTrue(TelefonPozadi.pokracovatNaLg(true, "192.168.1.20", TvPlatforma.WEBOS))
    }

    @Test fun localSamsungAndUnknownKeepOriginalStopPolicy() {
        for (platforma in listOf(TvPlatforma.SAMSUNG, TvPlatforma.NEZNAMA, null)) {
            assertFalse(TelefonPozadi.pokracovatNaLg(true, "192.168.1.20", platforma))
        }
    }

    @Test fun stoppingOrRemovingLgOutputReleasesBackgroundExemption() {
        assertTrue(TelefonPozadi.pokracovatNaLg(true, "192.168.1.20", TvPlatforma.WEBOS))
        assertFalse(TelefonPozadi.pokracovatNaLg(false, "192.168.1.20", TvPlatforma.WEBOS))
        assertFalse(TelefonPozadi.pokracovatNaLg(true, null, TvPlatforma.WEBOS))
        assertFalse(TelefonPozadi.pokracovatNaLg(true, "", TvPlatforma.WEBOS))
    }
}
