package com.hilight.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDspTest {

    @Test
    fun extractMagnitudes_calculatesCorrectHypot() {
        // DC = 10, Nyquist = 5, Bin 1: Real = 3, Imag = 4 -> Hypot = 5
        val fft = byteArrayOf(10, 5, 3, 4)
        val mags = AudioDsp.extractMagnitudes(fft)

        assertEquals(2, mags.size)
        assertEquals(10f, mags[0], 0.001f)
        assertEquals(5f, mags[1], 0.001f)
    }

    @Test
    fun computeBandEnergies_returns8NormalizedBands() {
        val magnitudes = FloatArray(256) { 20f }
        val energies = AudioDsp.computeBandEnergies(magnitudes, samplingRateHz = 44100, sensitivity = 1.0f)

        assertEquals(8, energies.size)
        for (i in 0 until 8) {
            assertTrue("Band $i should be >= 0.0", energies[i] >= 0f)
            assertTrue("Band $i should be <= 1.0", energies[i] <= 1.0f)
        }
    }

    @Test
    fun envelopeFollower_attacksFastAndDecaysSmoothly() {
        val follower = AudioDsp.EnvelopeFollower(attack = 0.8f, decay = 0.2f)

        // Initial zero
        val targetHigh = FloatArray(8) { 1.0f }
        val attacked = follower.update(targetHigh)
        assertEquals(0.8f, attacked[0], 0.01f)

        // Drop to zero -> decay
        val targetZero = FloatArray(8) { 0.0f }
        val decayed = follower.update(targetZero)
        assertEquals(0.64f, decayed[0], 0.01f)
    }

    @Test
    fun energiesToColors_generatesValidArgbColorsForAllModes() {
        val energies = FloatArray(8) { 0.75f }

        for (mode in AudioDsp.VisualizerMode.entries) {
            val colors = AudioDsp.energiesToColors(energies, mode = mode)
            assertEquals(8, colors.size)
            for (color in colors) {
                // Must have full alpha 0xFF
                val alpha = (color ushr 24) and 0xFF
                assertEquals(0xFF, alpha)
            }
        }
    }

    @Test
    fun hsvToArgb_convertsBoundaryHuesAccurately() {
        // Red (0 deg)
        val red = AudioDsp.hsvToArgb(0f, 1f, 1f)
        assertEquals(0xFFFF0000.toInt(), red)

        // Green (120 deg)
        val green = AudioDsp.hsvToArgb(120f, 1f, 1f)
        assertEquals(0xFF00FF00.toInt(), green)

        // Blue (240 deg)
        val blue = AudioDsp.hsvToArgb(240f, 1f, 1f)
        assertEquals(0xFF0000FF.toInt(), blue)
    }
}
