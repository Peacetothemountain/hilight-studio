package com.hilight.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class BrandColorsTest {

    @Test
    fun `detectColor returns signature brand colors for popular apps`() {
        assertEquals(0xFF25D366.toInt(), BrandColors.detectColor(null, "com.whatsapp"))
        assertEquals(0xFF5865F2.toInt(), BrandColors.detectColor(null, "com.discord"))
        assertEquals(0xFF1A73E8.toInt(), BrandColors.detectColor(null, "com.google.android.apps.messaging"))
        assertEquals(0xFFEA4335.toInt(), BrandColors.detectColor(null, "com.google.android.gm"))
        assertEquals(0xFF1DB954.toInt(), BrandColors.detectColor(null, "com.spotify.music"))
    }

    @Test
    fun `detectColor falls back to default green when app is unknown and context is null`() {
        assertEquals(BrandColors.DEFAULT_FALLBACK, BrandColors.detectColor(null, "com.unknown.app"))
    }
}
