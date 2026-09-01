package com.hilight.studio

import kotlin.math.roundToInt

/** Parses a typed duration in seconds into a value inside the slider's active millisecond range. */
internal fun parseDurationSeconds(
    text: String,
    minMs: Int,
    maxMs: Int,
): Int? {
    if (minMs > maxMs) return null
    val seconds = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (!seconds.isFinite()) return null

    // Clamp before narrowing so pasted exponents cannot overflow and the active safety gate remains
    // authoritative. Math.round gives predictable millisecond precision instead of truncating.
    val millis = (seconds * 1_000.0).coerceIn(minMs.toDouble(), maxMs.toDouble())
    return millis.roundToInt()
}
