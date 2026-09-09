package com.hilight.studio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs

/**
 * Spatial Sensor Fusion Engine: Visor Tap Gesture Detector.
 *
 * Employs accelerometer impulse filtering to detect direct physical double-taps
 * on the Pixel 11 Pro XL rear camera visor glass ONLY while the phone is face-down and screen is OFF.
 */
class VisorTapDetector(
    private val context: Context,
    private val isFaceDownProvider: () -> Boolean,
    private val onDoubleTap: () -> Unit,
) : SensorEventListener {

    private val tag = "VisorTapDetector"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastTapTimeMs = 0L
    private var lastZ = 0f
    private var isListening = false

    companion object {
        private const val TAP_IMPULSE_THRESHOLD = 5.0f // High threshold to prevent false positives
        private const val MIN_TAP_INTERVAL_MS = 100L   // Debounce bounce
        private const val MAX_TAP_INTERVAL_MS = 450L   // Window for double tap
    }

    fun start() {
        if (isListening || accelSensor == null) return
        try {
            sensorManager?.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
            isListening = true
            Log.i(tag, "VisorTapDetector registered on accelerometer")
        } catch (t: Throwable) {
            Log.w(tag, "Failed to register accelerometer listener", t)
        }
    }

    fun stop() {
        if (!isListening) return
        try {
            sensorManager?.unregisterListener(this)
        } catch (ignored: Throwable) {
        }
        isListening = false
        lastTapTimeMs = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // Never trigger when user is actively using the screen or not strictly face-down
        if (event == null || powerManager?.isInteractive == true || !isFaceDownProvider()) return

        val z = event.values.getOrNull(2) ?: return
        val deltaZ = abs(z - lastZ)
        lastZ = z

        if (deltaZ > TAP_IMPULSE_THRESHOLD) {
            val now = SystemClock.elapsedRealtime()
            val timeSinceLast = now - lastTapTimeMs

            if (timeSinceLast in MIN_TAP_INTERVAL_MS..MAX_TAP_INTERVAL_MS) {
                lastTapTimeMs = 0L
                onDoubleTap()
            } else if (timeSinceLast > MAX_TAP_INTERVAL_MS) {
                lastTapTimeMs = now
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
