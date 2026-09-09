package com.hilight.studio

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * High-definition haptic resonance orchestrator for Google Pixel haptic actuators (Cirrus Logic).
 *
 * Provides tactile parity with Google's Material 3 Expressive motion curves, translating
 * optical animations into synchronized physical vibrations.
 */
object PixelHaptics {

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Ultra-crisp tick for slider increments and micro-adjustments. */
    fun tick(context: Context) {
        val v = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(10)
        }
    }

    /** Satisfying tactile click for button presses, tile toggles, and state switches. */
    fun click(context: Context) {
        val v = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(25)
        }
    }

    /** Heavy mechanical thud for alarms, emergency alerts, and mode locks. */
    fun heavyClick(context: Context) {
        val v = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(40)
        }
    }

    /** Double click for special triggers, presets, and mode confirmations. */
    fun doubleClick(context: Context) {
        val v = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 20, 50, 20), -1)
        }
    }

    /**
     * Tactile velocity sweep simulating an optical wave travelling across the 8-LED camera visor.
     * Uses Pixel's primitive composition API for fluid amplitude ramping.
     */
    fun lightWaveSweep(context: Context) {
        val v = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.3f, 0)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 30)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 30)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 30)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.3f, 30)
                v.vibrate(composition.compose())
                return
            } catch (ignored: Throwable) {
                // Fallback to standard click if composition is unsupported on target emulator
            }
        }
        click(context)
    }

    /** Heartbeat pulse for breathing / pulse lighting patterns. */
    fun heartbeat(context: Context) {
        val v = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 30, 80, 50),
                    intArrayOf(0, 140, 0, 255),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 30, 80, 50), -1)
        }
    }
}
