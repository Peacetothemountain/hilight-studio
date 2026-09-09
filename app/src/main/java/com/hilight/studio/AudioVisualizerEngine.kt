package com.hilight.studio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Real-time hardware audio visualizer engine utilizing Android's [Visualizer] API.
 *
 * Captures global mixed audio stream (session 0), feeds low-latency FFT buffers to [AudioDsp],
 * and streams 8-LED color frames and energy telemetry to the app and hardware visor.
 */
class AudioVisualizerEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onFrame: (colors: IntArray, energies: FloatArray) -> Unit,
) {
    private val tag = "AudioVisualizerEngine"
    private var visualizer: Visualizer? = null
    private val follower = AudioDsp.EnvelopeFollower(attack = 0.82f, decay = 0.16f)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var mode: AudioDsp.VisualizerMode = AudioDsp.VisualizerMode.SPECTRUM_8_BAND

    @Volatile
    var sensitivity: Float = 1.0f

    private var simulationJob: Job? = null

    /**
     * Checks if RECORD_AUDIO permission has been granted by the user.
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts the audio visualizer. If audio recording permission is granted, attaches to session 0.
     * Otherwise, falls back gracefully to high-realism simulation mode so the user can preview effects.
     */
    @Synchronized
    fun start() {
        if (isRunning) return
        isRunning = true
        follower.reset()

        if (hasAudioPermission()) {
            try {
                initHardwareVisualizer()
                Log.i(tag, "Attached hardware Visualizer on session 0")
                return
            } catch (t: Throwable) {
                Log.w(tag, "Failed to initialize hardware Visualizer, falling back to simulation", t)
            }
        }

        // Fallback or demo simulation
        startSimulation()
    }

    private fun initHardwareVisualizer() {
        try {
            val v = Visualizer(0)
            val range = Visualizer.getCaptureSizeRange()
            val captureSize = range[1].coerceAtMost(512)
            v.captureSize = captureSize

            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        v: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) {
                        // Not used for FFT visualization
                    }

                    override fun onFftDataCapture(
                        v: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (!isRunning || fft == null) return
                        val magnitudes = AudioDsp.extractMagnitudes(fft)
                        val samplingRateHz = samplingRate / 1000
                        val rawEnergies = AudioDsp.computeBandEnergies(
                            magnitudes = magnitudes,
                            samplingRateHz = samplingRateHz,
                            sensitivity = sensitivity,
                        )
                        val smoothed = follower.update(rawEnergies)
                        val colors = AudioDsp.energiesToColors(smoothed, mode = mode)
                        mainHandler.post {
                            if (isRunning) onFrame(colors, smoothed)
                        }
                    }
                },
                Visualizer.getMaxCaptureRate() / 2, // ~30-40 Hz throttle for battery & thermals
                false,
                true,
            )

            v.enabled = true
            visualizer = v
        } catch (e: Exception) {
            visualizer = null
            throw e
        }
    }

    private fun startSimulation() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            var phase = 0.0
            while (isActive && isRunning) {
                phase += 0.08
                val mockEnergies = FloatArray(AudioDsp.BANDS_COUNT) { i ->
                    val wave = (sin(phase * 1.5 + i * 0.7) * 0.5 + 0.5).toFloat()
                    val pulse = if (i < 2 && (phase % 3.14) < 0.4) 0.95f else wave * 0.7f
                    (pulse * sensitivity).coerceIn(0f, 1f)
                }
                val smoothed = follower.update(mockEnergies)
                val colors = AudioDsp.energiesToColors(smoothed, mode = mode)
                mainHandler.post {
                    if (isRunning) onFrame(colors, smoothed)
                }
                delay(33) // ~30 FPS
            }
        }
    }

    /**
     * Stops the visualizer and releases hardware audio resources immediately.
     */
    @Synchronized
    fun stop() {
        isRunning = false
        simulationJob?.cancel()
        simulationJob = null
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (t: Throwable) {
            Log.w(tag, "Error releasing Visualizer", t)
        }
        visualizer = null
        follower.reset()
    }
}
