package com.hilight.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CinemaFxEngineTest {

    @Test
    fun modeProperties_areConfigured() {
        for (mode in CinemaFxEngine.Mode.entries) {
            assertNotNull(mode.label)
            assertNotNull(mode.description)
        }
        assertEquals(7, CinemaFxEngine.Mode.entries.size)
    }

    @Test
    fun cctKelvin_mapsToCorrectColors() {
        // Candlelight 1900K has heavy red component, very low blue
        val candle = CctUtils.kelvinToArgb(1900)
        val r = (candle ushr 16) and 0xFF
        val b = candle and 0xFF
        assertEquals(255, r)
        assert(b < 50) { "Candlelight blue should be < 50, got $b" }

        // Daylight 6500K has balanced high RGB
        val daylight = CctUtils.kelvinToArgb(6500)
        val dr = (daylight ushr 16) and 0xFF
        val dg = (daylight ushr 8) and 0xFF
        val db = daylight and 0xFF
        assert(dr > 240 && dg > 240 && db > 240) { "Daylight RGB should be near 255" }
    }
}
