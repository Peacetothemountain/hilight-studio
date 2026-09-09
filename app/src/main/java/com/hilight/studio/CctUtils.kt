package com.hilight.studio

import androidx.compose.ui.graphics.Color
import kotlin.math.ln

/**
 * Optical Correlated Color Temperature (CCT) calculator based on the Planckian Locus
 * and Tanner Helland blackbody chromaticity algorithms.
 *
 * Translates thermodynamic Kelvin temperatures into calibrated sRGB and Android ARGB colors
 * for high-CRI video fill-light, camera flash, and cinematic practical lighting effects.
 */
object CctUtils {

    data class CctPreset(
        val kelvin: Int,
        val label: String,
        val description: String,
    )

    val PRESETS = listOf(
        CctPreset(1900, "Candlelight", "1900K - Intimate soft flame"),
        CctPreset(2700, "Warm Tungsten", "2700K - Cozy residential incandescent"),
        CctPreset(3200, "Studio Halogen", "3200K - Warm cinematography key"),
        CctPreset(4000, "Horizon Daylight", "4000K - Neutral early morning sun"),
        CctPreset(5000, "Studio Flash", "5000K - Pure neutral white"),
        CctPreset(5600, "Daylight Noon", "5600K - Standard cinematography daylight"),
        CctPreset(6500, "Overcast Sky", "6500K - Crisp cool daylight"),
        CctPreset(8000, "Blue Hour", "8000K - Deep twilight cool"),
    )

    /**
     * Converts a thermodynamic temperature in Kelvin (1000K - 12000K) to an sRGB [Color].
     */
    fun kelvinToColor(kelvin: Int): Color {
        val temp = (kelvin.coerceIn(1000, 12000)) / 100.0

        // Red
        val red = if (temp <= 66.0) {
            255.0
        } else {
            val r = temp - 60.0
            329.698727446 * Math.pow(r, -0.1332047592)
        }

        // Green
        val green = if (temp <= 66.0) {
            val g = temp
            99.4708025861 * ln(g) - 161.1195681661
        } else {
            val g = temp - 60.0
            288.1221695283 * Math.pow(g, -0.0755148492)
        }

        // Blue
        val blue = if (temp >= 66.0) {
            255.0
        } else if (temp <= 19.0) {
            0.0
        } else {
            val b = temp - 10.0
            138.5177312231 * ln(b) - 305.0447927307
        }

        val rClamped = red.coerceIn(0.0, 255.0).toInt()
        val gClamped = green.coerceIn(0.0, 255.0).toInt()
        val bClamped = blue.coerceIn(0.0, 255.0).toInt()

        return Color(rClamped, gClamped, bClamped)
    }

    /**
     * Converts Kelvin to an integer ARGB color compatible with Android's Lights HAL.
     */
    fun kelvinToArgb(kelvin: Int): Int {
        val c = kelvinToColor(kelvin)
        return (0xFF shl 24) or
            ((c.red * 255f).toInt() shl 16) or
            ((c.green * 255f).toInt() shl 8) or
            (c.blue * 255f).toInt()
    }
}
