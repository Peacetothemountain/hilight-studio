package com.hilight.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CctUtilsTest {

    @Test
    fun testCandlelight1900K() {
        val color = CctUtils.kelvinToColor(1900)
        assertTrue(color.red > color.green)
        assertTrue(color.green > color.blue)
        assertEquals(1f, color.red, 0.01f)
    }

    @Test
    fun testDaylight5600K() {
        val color = CctUtils.kelvinToColor(5600)
        assertEquals(1f, color.red, 0.05f)
        assertTrue(color.green > 0.85f)
        assertTrue(color.blue > 0.75f)
    }

    @Test
    fun testOvercast8000K() {
        val color = CctUtils.kelvinToColor(8000)
        assertEquals(1f, color.blue, 0.01f)
        assertTrue(color.blue >= color.red)
    }

    @Test
    fun testKelvinToArgb() {
        val argb = CctUtils.kelvinToArgb(3200)
        val alpha = (argb ushr 24) and 0xFF
        assertEquals(255, alpha)
    }
}
