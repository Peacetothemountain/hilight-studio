package com.hilight.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceDownDetectorTest {

    @Test
    fun `face down must remain stable before it is accepted`() {
        val detector = FaceDownDetector()

        assertEquals(FaceDownState.CHECKING, detector.update(0f, 0f, -9.81f, 1_000))
        assertEquals(FaceDownState.CHECKING, detector.update(0f, 0f, -9.81f, 1_399))
        assertEquals(FaceDownState.FACE_DOWN, detector.update(0f, 0f, -9.81f, 1_400))
    }

    @Test
    fun `face up and upright settle as not face down`() {
        val faceUp = FaceDownDetector()
        val upright = FaceDownDetector()

        faceUp.update(0f, 0f, 9.81f, 10)
        upright.update(0f, 9.81f, 0f, 10)

        assertEquals(FaceDownState.NOT_FACE_DOWN, faceUp.update(0f, 0f, 9.81f, 410))
        assertEquals(FaceDownState.NOT_FACE_DOWN, upright.update(0f, 9.81f, 0f, 410))
    }

    @Test
    fun `hysteresis keeps an accepted phone face down near the enter edge`() {
        val detector = FaceDownDetector()
        detector.update(0f, 0f, -9.81f, 1_000)
        detector.update(0f, 0f, -9.81f, 1_400)

        assertEquals(FaceDownState.FACE_DOWN, detector.update(6.6f, 0f, -8.0f, 1_500))
    }

    @Test
    fun `leaving face down must also remain stable before output is revoked`() {
        val detector = FaceDownDetector()
        detector.update(0f, 0f, -9.81f, 1_000)
        detector.update(0f, 0f, -9.81f, 1_400)

        assertEquals(FaceDownState.FACE_DOWN, detector.update(0f, 0f, 9.81f, 2_000))
        assertEquals(FaceDownState.FACE_DOWN, detector.update(0f, 0f, 9.81f, 2_399))
        assertEquals(FaceDownState.NOT_FACE_DOWN, detector.update(0f, 0f, 9.81f, 2_400))
    }

    @Test
    fun `motion resets the entire face down settling window`() {
        val detector = FaceDownDetector()
        detector.update(0f, 0f, -9.81f, 1_000)
        detector.update(0f, 0f, -30f, 1_300)

        assertEquals(FaceDownState.CHECKING, detector.update(0f, 0f, -9.81f, 1_400))
        assertEquals(FaceDownState.CHECKING, detector.update(0f, 0f, -9.81f, 1_799))
        assertEquals(FaceDownState.FACE_DOWN, detector.update(0f, 0f, -9.81f, 1_800))
    }

    @Test
    fun `freshness is fail closed for unknown future and stale samples`() {
        assertTrue(isFreshFaceDown(FaceDownState.FACE_DOWN, 10_000, 14_999))
        assertFalse(isFreshFaceDown(FaceDownState.FACE_DOWN, 10_000, 15_001))
        assertFalse(isFreshFaceDown(FaceDownState.CHECKING, 10_000, 10_100))
        assertFalse(isFreshFaceDown(FaceDownState.FACE_DOWN, 20_000, 19_999))
        assertFalse(isFreshFaceDown(FaceDownState.FACE_DOWN, 0, 100))
    }

    @Test
    fun `watchdog times out first-sample silence and later sample silence`() {
        assertFalse(isFaceDownWatchdogStale(10_000, 0, 15_000))
        assertTrue(isFaceDownWatchdogStale(10_000, 0, 15_001))
        assertFalse(isFaceDownWatchdogStale(10_000, 14_000, 19_000))
        assertTrue(isFaceDownWatchdogStale(10_000, 14_000, 19_001))
        assertFalse(isFaceDownWatchdogStale(0, 0, 20_000))
        assertFalse(isFaceDownWatchdogStale(20_000, 0, 19_999))
    }
}
