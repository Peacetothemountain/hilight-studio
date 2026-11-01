package com.hilight.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthEffectsTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val black = 0xFF000000.toInt()

    private fun sample(frame: IntArray, dx: Float, dy: Float, reach: Float = 100f) =
        FloatArray(4).also { ledFieldSample(frame, dx, dy, reach, it) }

    @Test fun eachLedLeansTowardsItsOwnDirection() {
        // LED 0 points up and the rest run clockwise, as on the drawn disc: with red on top and blue
        // on the bottom half, light above the disc is red and light below it is blue.
        val frame = IntArray(LED_COUNT) { if (it == 0 || it == 1 || it == 7) red else blue }
        val above = sample(frame, 0f, -60f)
        val below = sample(frame, 0f, 60f)
        assertTrue("above should be mostly red: ${above.toList()}", above[0] > above[2])
        assertTrue("below should be mostly blue: ${below.toList()}", below[2] > below[0])
    }

    @Test fun nearTheDiscEveryLedContributesEqually() {
        val frame = IntArray(LED_COUNT) { if (it % 2 == 0) red else blue }
        val centre = sample(frame, 0.5f, 0f)
        assertEquals(1f, centre[0], 0.02f)
        assertEquals(1f, centre[2], 0.02f)
    }

    @Test fun intensityFallsToZeroAtReachAndColourStaysSaturated() {
        val frame = IntArray(LED_COUNT) { 0xFF402000.toInt() } // a dim orange
        val near = sample(frame, 10f, 0f)
        val mid = sample(frame, 50f, 0f)
        val far = sample(frame, 100f, 0f)
        assertTrue(near[3] > mid[3])
        assertEquals(0f, far[3], 0f)
        // Normalised to full brightness: dimness lives in alpha, not in the RGB that gets interpolated.
        assertEquals(1f, mid[0], 1e-4f)
        assertEquals(0.5f, mid[1], 0.01f)
        assertTrue(mid[3] < 0.3f)
    }

    @Test fun darkOrEmptyFramesCastNoLight() {
        assertEquals(0f, sample(IntArray(LED_COUNT) { black }, 20f, 20f)[3], 0f)
        assertEquals(0f, sample(IntArray(0), 20f, 20f)[3], 0f)
        assertEquals(0f, sample(IntArray(LED_COUNT) { red }, 20f, 20f, reach = 0f)[3], 0f)
    }

    @Test fun edgeBlurAmountSnapsToTenthsAndClamps() {
        assertEquals(0f, quantizeEdge(0f), 0f)
        assertEquals(0f, quantizeEdge(-3f), 0f)
        assertEquals(0f, quantizeEdge(Float.NaN), 0f)
        assertEquals(0.1f, quantizeEdge(0.01f), 1e-6f)
        assertEquals(0.5f, quantizeEdge(0.42f), 1e-6f)
        assertEquals(1f, quantizeEdge(7f), 0f)
    }

    private companion object {
        const val LED_COUNT = 8
    }
}
