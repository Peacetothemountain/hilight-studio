package com.hilight.studio

import kotlin.math.sqrt

/** What the background position tracker can currently prove. Only [FACE_DOWN] permits output. */
enum class FaceDownState {
    INACTIVE,
    STARTING,
    CHECKING,
    FACE_DOWN,
    NOT_FACE_DOWN,
    UNAVAILABLE,
    STALE,
    START_FAILED,
}

internal const val FACE_DOWN_STALE_AFTER_MS = 5_000L

/** Fail-closed freshness check shared by notification and renderer-output gates. */
internal fun isFreshFaceDown(
    state: FaceDownState,
    lastSampleElapsedMs: Long,
    nowElapsedMs: Long,
    staleAfterMs: Long = FACE_DOWN_STALE_AFTER_MS,
): Boolean = state == FaceDownState.FACE_DOWN &&
    lastSampleElapsedMs > 0L &&
    nowElapsedMs >= lastSampleElapsedMs &&
    nowElapsedMs - lastSampleElapsedMs <= staleAfterMs

/** Also times out a registration that succeeded but never delivered its first sample. */
internal fun isFaceDownWatchdogStale(
    watchStartedElapsedMs: Long,
    lastSampleElapsedMs: Long,
    nowElapsedMs: Long,
    staleAfterMs: Long = FACE_DOWN_STALE_AFTER_MS,
): Boolean {
    val referenceMs = maxOf(watchStartedElapsedMs, lastSampleElapsedMs)
    return referenceMs > 0L &&
        nowElapsedMs >= referenceMs &&
        nowElapsedMs - referenceMs > staleAfterMs
}

/**
 * Converts gravity samples into a stable face-down state.
 *
 * Android's positive Z axis points out through the screen, so a negative normalized Z means the
 * screen faces the surface. Separate enter/exit cones prevent edge-angle chatter, and every sample
 * in the settling window must look like plausible gravity so motion cannot win on one lucky frame.
 */
internal class FaceDownDetector(
    private val stableMs: Long = 400L,
    private val enterRatio: Float = -0.82f,
    private val exitRatio: Float = -0.65f,
    private val minGravity: Float = 7.0f,
    private val maxGravity: Float = 13.0f,
) {
    private var state = FaceDownState.CHECKING
    private var candidate: FaceDownState? = null
    private var candidateSinceMs = 0L

    fun reset(): FaceDownState {
        state = FaceDownState.CHECKING
        candidate = null
        candidateSinceMs = 0L
        return state
    }

    fun update(x: Float, y: Float, z: Float, nowElapsedMs: Long): FaceDownState {
        val magnitude = sqrt(x * x + y * y + z * z)
        val plausible = magnitude.isFinite() && magnitude in minGravity..maxGravity
        val normalizedZ = if (plausible && magnitude > 0f) z / magnitude else 1f
        val target = when (state) {
            FaceDownState.FACE_DOWN -> {
                if (plausible && normalizedZ <= exitRatio) {
                    FaceDownState.FACE_DOWN
                } else {
                    FaceDownState.NOT_FACE_DOWN
                }
            }
            else -> {
                if (plausible && normalizedZ <= enterRatio) {
                    FaceDownState.FACE_DOWN
                } else {
                    FaceDownState.NOT_FACE_DOWN
                }
            }
        }

        if (target == state) {
            candidate = null
            return state
        }
        if (candidate != target || nowElapsedMs < candidateSinceMs) {
            candidate = target
            candidateSinceMs = nowElapsedMs
            return state
        }
        if (nowElapsedMs - candidateSinceMs >= stableMs) {
            state = target
            candidate = null
        }
        return state
    }
}
