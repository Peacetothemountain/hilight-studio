package com.hilight.studio

import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Pure Kotlin Audio Digital Signal Processing (DSP) engine.
 *
 * Processes raw Android audio FFT buffers, computes frequency-domain magnitudes,
 * groups frequencies into 8 calibrated acoustic bands matching the physical Pixel visor LEDs,
 * applies psychoacoustic logarithmic weighting, and executes attack/decay smoothing.
 */
object AudioDsp {

    const val BANDS_COUNT = 8

    data class BandDefinition(
        val name: String,
        val minHz: Float,
        val maxHz: Float,
        val defaultHue: Float, // Hue in degrees [0..360]
    )

    /**
     * 8 standard acoustic bands tuned for music visualization across the rear 8-LED visor:
     * - LED 1: Sub-Bass (20 - 60 Hz) - Kick drum, 808 subs (Deep Red)
     * - LED 2: Bass (60 - 250 Hz) - Bass guitar, fundamental kick (Orange)
     * - LED 3: Low-Mid (250 - 500 Hz) - Warmth, low vocals, snare body (Amber/Yellow)
     * - LED 4: Mid (500 - 1000 Hz) - Horns, synth leads, vocals (Green)
     * - LED 5: Upper-Mid (1000 - 2000 Hz) - Vocal articulation, snare snap (Cyan)
     * - LED 6: Presence (2000 - 4000 Hz) - Vocal clarity, guitars (Electric Blue)
     * - LED 7: Brilliance (4000 - 8000 Hz) - Cymbals, high hats (Violet)
     * - LED 8: Air (8000 - 16000 Hz) - Sparkle, room acoustic air (Magenta)
     */
    val ACOUSTIC_BANDS = listOf(
        BandDefinition("Sub-Bass", 20f, 60f, 0f),
        BandDefinition("Bass", 60f, 250f, 28f),
        BandDefinition("Low-Mid", 250f, 500f, 50f),
        BandDefinition("Mid", 500f, 1000f, 120f),
        BandDefinition("Upper-Mid", 1000f, 2000f, 185f),
        BandDefinition("Presence", 2000f, 4000f, 220f),
        BandDefinition("Brilliance", 4000f, 8000f, 275f),
        BandDefinition("Air", 8000f, 16000f, 315f),
    )

    enum class VisualizerMode(val label: String, val description: String) {
        SPECTRUM_8_BAND("8-Band Spectrum", "Individual frequency band energy mapped to each of the 8 LEDs"),
        CENTER_OUT_PULSE("Stereo Center Pulse", "Bass energy originates from center LEDs 4 & 5 and pulses outward"),
        CHROMATIC_HARMONICS("Harmonic Hue Flow", "Visor color dynamically shifts based on dominant pitch and tonality"),
    }

    /**
     * Extracts linear frequency magnitudes from Android's Visualizer FFT byte array format.
     * Android FFT format:
     * - fft[0] = DC real
     * - fft[1] = Nyquist real
     * - For k in 1 until n/2:
     *     fft[2*k] = Real, fft[2*k + 1] = Imaginary
     */
    fun extractMagnitudes(fft: ByteArray): FloatArray {
        val n = fft.size
        if (n < 2) return FloatArray(0)
        val numBins = n / 2
        val magnitudes = FloatArray(numBins)

        magnitudes[0] = Math.abs(fft[0].toInt()).toFloat()

        for (k in 1 until numBins) {
            val r = fft[2 * k].toFloat()
            val i = fft[2 * k + 1].toFloat()
            magnitudes[k] = hypot(r, i)
        }
        return magnitudes
    }

    /**
     * Maps FFT bin magnitudes into 8 normalized frequency bands [0.0f .. 1.0f].
     *
     * @param magnitudes FFT magnitude array from [extractMagnitudes]
     * @param samplingRateHz Audio sampling rate in Hz (e.g. 44100 or 48000)
     * @param sensitivity Gain multiplier (0.5 to 3.0)
     */
    fun computeBandEnergies(
        magnitudes: FloatArray,
        samplingRateHz: Int,
        sensitivity: Float = 1.0f,
    ): FloatArray {
        val energies = FloatArray(BANDS_COUNT)
        if (magnitudes.isEmpty() || samplingRateHz <= 0) return energies

        val numBins = magnitudes.size
        val binWidthHz = (samplingRateHz / 2f) / numBins.toFloat()

        for (b in 0 until BANDS_COUNT) {
            val band = ACOUSTIC_BANDS[b]
            val startBin = (band.minHz / binWidthHz).toInt().coerceIn(0, numBins - 1)
            val endBin = (band.maxHz / binWidthHz).toInt().coerceIn(startBin, numBins - 1)

            var sum = 0f
            var count = 0
            for (i in startBin..endBin) {
                sum += magnitudes[i]
                count++
            }

            val avg = if (count > 0) sum / count else 0f
            // Psychoacoustic logarithmic dB scaling:
            // 20 * log10(avg + 1) with floor suppression
            val db = if (avg > 0.5f) 20f * log10(avg + 1f) else 0f
            // Dynamic gain normalization: higher bands naturally have lower energy, so compensate with gentle slope
            val bandGain = 1.0f + (b * 0.18f)
            val normalized = ((db / 65f) * sensitivity * bandGain).coerceIn(0f, 1f)
            energies[b] = normalized
        }
        return energies
    }

    /**
     * Attack-Decay envelope filter that simulates analog VU meter needle physics:
     * Instant attack on transients (beats, snares), smooth exponential decay for eye comfort.
     */
    class EnvelopeFollower(
        private val attack: Float = 0.75f,
        private val decay: Float = 0.18f,
    ) {
        private val smoothed = FloatArray(BANDS_COUNT)

        fun update(targetEnergies: FloatArray): FloatArray {
            for (i in 0 until BANDS_COUNT) {
                val target = if (i < targetEnergies.size) targetEnergies[i] else 0f
                if (target > smoothed[i]) {
                    // Fast attack
                    smoothed[i] += (target - smoothed[i]) * attack
                } else {
                    // Smooth decay
                    smoothed[i] -= (smoothed[i] - target) * decay
                }
                smoothed[i] = smoothed[i].coerceIn(0f, 1f)
            }
            return smoothed.clone()
        }

        fun reset() {
            smoothed.fill(0f)
        }
    }

    /**
     * Converts normalized band energies into 8 ARGB LED colors according to the chosen [VisualizerMode].
     */
    fun energiesToColors(
        energies: FloatArray,
        mode: VisualizerMode = VisualizerMode.SPECTRUM_8_BAND,
        customBaseColor: Int = 0xFF7C4DFF.toInt(),
    ): IntArray {
        val outColors = IntArray(BANDS_COUNT)

        when (mode) {
            VisualizerMode.SPECTRUM_8_BAND -> {
                for (i in 0 until BANDS_COUNT) {
                    val energy = if (i < energies.size) energies[i] else 0f
                    val hue = ACOUSTIC_BANDS[i].defaultHue
                    // Interpolate brightness and saturation based on energy
                    val sat = 0.85f + (0.15f * energy)
                    val value = energy.coerceIn(0f, 1f)
                    outColors[i] = hsvToArgb(hue, sat, value)
                }
            }

            VisualizerMode.CENTER_OUT_PULSE -> {
                // Symmetrical mapping: LED indices 3 & 4 (center) are bass, spreading to 0 & 7 (edges)
                val bassEnergy = if (energies.isNotEmpty()) (energies[0] + energies[1]) / 2f else 0f
                val midEnergy = if (energies.size > 3) (energies[2] + energies[3]) / 2f else 0f
                val highEnergy = if (energies.size > 6) (energies[4] + energies[5] + energies[6]) / 3f else 0f

                val centerPairs = listOf(
                    Pair(3, 4) to bassEnergy,
                    Pair(2, 5) to (bassEnergy * 0.5f + midEnergy * 0.5f),
                    Pair(1, 6) to midEnergy,
                    Pair(0, 7) to highEnergy,
                )

                for ((pair, energy) in centerPairs) {
                    val (l, r) = pair
                    val hue = (330f + (pair.first * 45f)) % 360f
                    val color = hsvToArgb(hue, 0.95f, energy)
                    outColors[l] = color
                    outColors[r] = color
                }
            }

            VisualizerMode.CHROMATIC_HARMONICS -> {
                // Find dominant frequency band
                var maxEnergy = 0f
                var dominantBand = 0
                for (i in 0 until BANDS_COUNT) {
                    val e = if (i < energies.size) energies[i] else 0f
                    if (e > maxEnergy) {
                        maxEnergy = e
                        dominantBand = i
                    }
                }
                val dominantHue = ACOUSTIC_BANDS[dominantBand].defaultHue

                for (i in 0 until BANDS_COUNT) {
                    val e = if (i < energies.size) energies[i] else 0f
                    // Slight spatial gradient around dominant hue
                    val h = (dominantHue + (i - 3.5f) * 12f + 360f) % 360f
                    outColors[i] = hsvToArgb(h, 0.9f, e)
                }
            }
        }

        return outColors
    }

    /**
     * Converts HSV to an Android 32-bit ARGB integer.
     */
    fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
        val h = (hue % 360f + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val c = v * s
        val x = c * (1f - Math.abs((h / 60f) % 2f - 1f))
        val m = v - c

        val (r1, g1, b1) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val red = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val green = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val blue = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)

        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
