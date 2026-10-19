package com.hilight.studio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock

/** Sensor-framework adapter owned by [ForegroundWatcher]; classification itself stays JVM-testable. */
internal class FaceDownSensorTracker(
    context: Context,
    private val handler: Handler,
    private val onReading: (FaceDownState, Long) -> Unit,
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = findSensor(manager)
    private val detector = FaceDownDetector()
    @Volatile private var running = false
    private var watchStartedElapsedMs = 0L
    private var lastSampleElapsedMs = 0L
    private var lastReportedState: FaceDownState? = null
    private var lastReportedElapsedMs = 0L

    private val staleWatch = object : Runnable {
        @Synchronized
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            if (isFaceDownWatchdogStale(watchStartedElapsedMs, lastSampleElapsedMs, now)) {
                watchStartedElapsedMs = now
                lastSampleElapsedMs = 0L
                detector.reset()
                lastReportedState = FaceDownState.STALE
                lastReportedElapsedMs = now
                onReading(FaceDownState.STALE, now)
                val selected = sensor
                if (manager != null && selected != null) {
                    manager.unregisterListener(this@FaceDownSensorTracker)
                    manager.registerListener(
                        this@FaceDownSensorTracker,
                        selected,
                        SensorManager.SENSOR_DELAY_NORMAL,
                        0,
                        handler,
                    )
                }
            }
            if (running) handler.postDelayed(this, STALE_CHECK_MS)
        }
    }

    @Synchronized
    fun start() {
        if (running) return
        detector.reset()
        watchStartedElapsedMs = 0L
        lastSampleElapsedMs = 0L
        lastReportedState = null
        lastReportedElapsedMs = 0L
        val selected = sensor
        if (manager == null || selected == null) {
            onReading(FaceDownState.UNAVAILABLE, SystemClock.elapsedRealtime())
            return
        }
        running = manager.registerListener(
            this,
            selected,
            SensorManager.SENSOR_DELAY_NORMAL,
            0,
            handler,
        )
        if (!running) {
            onReading(FaceDownState.UNAVAILABLE, SystemClock.elapsedRealtime())
            return
        }
        val now = SystemClock.elapsedRealtime()
        watchStartedElapsedMs = now
        lastReportedState = FaceDownState.CHECKING
        lastReportedElapsedMs = now
        onReading(FaceDownState.CHECKING, now)
        handler.postDelayed(staleWatch, STALE_CHECK_MS)
    }

    /** Unregister before the owner quits [handler]'s looper. */
    @Synchronized
    fun stop() {
        running = false
        manager?.unregisterListener(this)
        handler.removeCallbacks(staleWatch)
        detector.reset()
        watchStartedElapsedMs = 0L
        lastSampleElapsedMs = 0L
        lastReportedState = null
        lastReportedElapsedMs = 0L
    }

    @Synchronized
    override fun onSensorChanged(event: SensorEvent) {
        if (!running || event.values.size < 3) return
        // SensorEvent timestamp bases have varied; use the same monotonic clock as Store's fences.
        val now = SystemClock.elapsedRealtime()
        lastSampleElapsedMs = now
        val nextState = detector.update(event.values[0], event.values[1], event.values[2], now)
        if (nextState != lastReportedState || now - lastReportedElapsedMs >= HEARTBEAT_REPORT_MS) {
            lastReportedState = nextState
            lastReportedElapsedMs = now
            onReading(nextState, now)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val STALE_CHECK_MS = 1_000L
        private const val HEARTBEAT_REPORT_MS = 1_500L

        fun hasSensor(context: Context): Boolean =
            findSensor(context.getSystemService(SensorManager::class.java)) != null

        /** Prefer non-wake-up sensors so continuous gravity/accelerometer sampling does not prevent CPU sleep. */
        private fun findSensor(manager: SensorManager?): Sensor? = manager?.let {
            it.getDefaultSensor(Sensor.TYPE_GRAVITY, false)
                ?: it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false)
                ?: it.getDefaultSensor(Sensor.TYPE_GRAVITY, true)
                ?: it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        }
    }
}
