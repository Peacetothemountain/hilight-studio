package com.hilight.studio

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Cinema Practical Lighting Effects Engine for Filmmakers and Photographers.
 *
 * Generates photorealistic lighting simulations across the 8-LED Pixel visor:
 * Candlelight, Lightning Storm, Paparazzi Flashes, Police Beacon, Studio Tally, and Catchlight.
 */
class CinemaFxEngine(
    private val onFrame: (colors: IntArray, brightness: Float) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    enum class Mode(val label: String, val description: String) {
        OFF("Off", "No practical effects active"),
        CANDLE("Candlelight", "1900K organic warm flame flicker with spatial micro-drift"),
        LIGHTNING("Lightning Storm", "Sudden 6500K multi-pulse strikes with realistic thunderstorm delays"),
        PAPARAZZI("Paparazzi Crowd", "Chaotic press camera flash bursts across individual visor LEDs"),
        POLICE("Police Beacon", "High-visibility dual-color emergency double-pulse strobe"),
        TALLY("Studio Tally", "Steady 100% red recording tally light for on-camera subjects"),
        CATCHLIGHT("Portrait Catchlight", "Smooth 5600K orbital sweep providing flattering eye reflection"),
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var fxJob: Job? = null
    private val rnd = Random.Default

    @Volatile
    var currentMode: Mode = Mode.OFF
        private set

    fun setMode(mode: Mode) {
        fxJob?.cancel()
        currentMode = mode

        if (mode == Mode.OFF) {
            mainHandler.post { onFrame(IntArray(8) { 0xFF000000.toInt() }, 0f) }
            return
        }

        fxJob = scope.launch {
            when (mode) {
                Mode.OFF -> Unit
                Mode.CANDLE -> runCandle()
                Mode.LIGHTNING -> runLightning()
                Mode.PAPARAZZI -> runPaparazzi()
                Mode.POLICE -> runPolice()
                Mode.TALLY -> runTally()
                Mode.CATCHLIGHT -> runCatchlight()
            }
        }
    }

    private suspend fun runCandle() {
        val frame = IntArray(8)
        while (scope.isActive) {
            // Per-LED organic flame flicker
            val masterBrightness = 0.55f + (rnd.nextFloat() * 0.45f)
            for (i in 0 until 8) {
                // Subtle kelvin drift around 1900K (1750K to 2150K)
                val kelvin = 1900 + rnd.nextInt(-150, 250)
                val baseColor = CctUtils.kelvinToArgb(kelvin)
                val ledDim = 0.7f + (rnd.nextFloat() * 0.3f)
                frame[i] = applyBrightness(baseColor, ledDim)
            }
            emitFrame(frame, masterBrightness)
            delay(rnd.nextLong(40, 90))
        }
    }

    private suspend fun runLightning() {
        val darkFrame = IntArray(8) { 0xFF000000.toInt() }
        val strikeColor = CctUtils.kelvinToArgb(6500)
        val flashFrame = IntArray(8) { strikeColor }

        while (scope.isActive) {
            // Darkness / distant atmosphere between strikes
            emitFrame(darkFrame, 0f)
            delay(rnd.nextLong(1500, 4500))

            // Multi-strike lightning bolt (2 to 4 bursts)
            val strikes = rnd.nextInt(2, 5)
            for (s in 0 until strikes) {
                val strikeBrightness = if (s == 0) 1.0f else (0.4f + rnd.nextFloat() * 0.6f)
                emitFrame(flashFrame, strikeBrightness)
                delay(rnd.nextLong(30, 70))
                emitFrame(darkFrame, 0f)
                delay(rnd.nextLong(40, 110))
            }
        }
    }

    private suspend fun runPaparazzi() {
        val frame = IntArray(8)
        val flashColor = CctUtils.kelvinToArgb(5800)

        while (scope.isActive) {
            frame.fill(0xFF000000.toInt())
            // 1 or 2 LEDs flash simultaneously
            val flashes = rnd.nextInt(1, 3)
            for (f in 0 until flashes) {
                val led = rnd.nextInt(8)
                frame[led] = flashColor
            }
            emitFrame(frame, 1.0f)
            delay(rnd.nextLong(35, 65))

            // Dark interval
            frame.fill(0xFF000000.toInt())
            emitFrame(frame, 0f)
            delay(rnd.nextLong(50, 180))
        }
    }

    private suspend fun runPolice() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0044FF.toInt()
        val frame = IntArray(8)

        while (scope.isActive) {
            // Red double flash (Left 4 LEDs)
            frame.fill(0xFF000000.toInt())
            for (i in 0 until 4) frame[i] = red
            emitFrame(frame, 1f); delay(50)
            frame.fill(0xFF000000.toInt()); emitFrame(frame, 0f); delay(50)
            for (i in 0 until 4) frame[i] = red
            emitFrame(frame, 1f); delay(50)
            frame.fill(0xFF000000.toInt()); emitFrame(frame, 0f); delay(140)

            // Blue double flash (Right 4 LEDs)
            for (i in 4 until 8) frame[i] = blue
            emitFrame(frame, 1f); delay(50)
            frame.fill(0xFF000000.toInt()); emitFrame(frame, 0f); delay(50)
            for (i in 4 until 8) frame[i] = blue
            emitFrame(frame, 1f); delay(50)
            frame.fill(0xFF000000.toInt()); emitFrame(frame, 0f); delay(140)
        }
    }

    private suspend fun runTally() {
        val tallyRed = 0xFFFF0011.toInt()
        val frame = IntArray(8) { tallyRed }
        emitFrame(frame, 1.0f)
        while (scope.isActive) {
            delay(500)
        }
    }

    private suspend fun runCatchlight() {
        val frame = IntArray(8)
        val catchColor = CctUtils.kelvinToArgb(5600)
        var pos = 0
        var direction = 1

        while (scope.isActive) {
            frame.fill(0xFF000000.toInt())
            frame[pos] = catchColor
            val neighbor = (pos - direction).coerceIn(0, 7)
            frame[neighbor] = applyBrightness(catchColor, 0.4f)

            emitFrame(frame, 0.85f)

            pos += direction
            if (pos >= 7) {
                pos = 7
                direction = -1
            } else if (pos <= 0) {
                pos = 0
                direction = 1
            }
            delay(120)
        }
    }

    private fun emitFrame(colors: IntArray, brightness: Float) {
        val copy = colors.clone()
        mainHandler.post {
            if (currentMode != Mode.OFF) {
                onFrame(copy, brightness)
            }
        }
    }

    private fun applyBrightness(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        val a = (color ushr 24) and 0xFF
        val r = (((color ushr 16) and 0xFF) * f).toInt()
        val g = (((color ushr 8) and 0xFF) * f).toInt()
        val b = ((color and 0xFF) * f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun stop() {
        setMode(Mode.OFF)
    }
}
