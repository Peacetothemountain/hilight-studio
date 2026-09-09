package com.hilight.studio

/**
 * Cinema Practical Lighting Effects Engine for Filmmakers and Photographers.
 *
 * Provides calibrated hardware lighting modes executed by the native Lights HAL daemon:
 * Candlelight, Lightning Storm, Paparazzi Flashes, Police Beacon, Studio Tally, and Catchlight.
 */
class CinemaFxEngine {
    enum class Mode(
        val id: String,
        val label: String,
        val description: String,
        val baseColor: Int,
    ) {
        OFF("off", "Off", "No practical effects active", 0),
        CANDLE("candle", "Candlelight", "1900K organic warm flame flicker with spatial micro-drift", 0xFFFF7A00.toInt()),
        LIGHTNING("lightning", "Lightning Storm", "Sudden 6500K multi-pulse strikes with realistic thunderstorm delays", 0xFFDDEEFF.toInt()),
        PAPARAZZI("paparazzi", "Paparazzi Crowd", "Chaotic press camera flash bursts across individual visor LEDs", 0xFFFFFFFF.toInt()),
        POLICE("police", "Police Beacon", "High-visibility dual-color emergency double-pulse strobe", 0xFFFF0000.toInt()),
        TALLY("tally", "Studio Tally", "Steady 100% red recording tally light for on-camera subjects", 0xFFFF0000.toInt()),
        CATCHLIGHT("catchlight", "Portrait Catchlight", "Smooth 5600K orbital sweep providing flattering eye reflection", 0xFFFFFAEE.toInt()),
    }

    @Volatile
    var currentMode: Mode = Mode.OFF
        private set

    fun setMode(mode: Mode) {
        currentMode = mode
    }

    fun stop() {
        currentMode = Mode.OFF
    }
}
