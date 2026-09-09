package com.hilight.studio

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Curated brand palettes and adaptive color extraction for installed apps.
 *
 * Automatically detects signature brand colors for popular apps or extracts
 * dominant vibrant tones from app icons so users don't have to manually tune colors.
 */
object BrandColors {

    private val SIGNATURES = mapOf(
        "com.whatsapp" to 0xFF25D366.toInt(),
        "com.whatsapp.w4b" to 0xFF25D366.toInt(),
        "org.telegram.messenger" to 0xFF0088CC.toInt(),
        "org.telegram.messenger.web" to 0xFF0088CC.toInt(),
        "org.thunderdog.challegram" to 0xFF0088CC.toInt(),
        "org.thoughtcrime.securesms" to 0xFF3A76F0.toInt(),
        "com.google.android.apps.messaging" to 0xFF1A73E8.toInt(),
        "com.google.android.gm" to 0xFFEA4335.toInt(),
        "com.google.android.dialer" to 0xFF34A853.toInt(),
        "com.google.android.apps.maps" to 0xFF34A853.toInt(),
        "com.google.android.youtube" to 0xFFFF0000.toInt(),
        "com.facebook.orca" to 0xFF0084FF.toInt(),
        "com.facebook.katana" to 0xFF1877F2.toInt(),
        "com.instagram.android" to 0xFFE1306C.toInt(),
        "com.discord" to 0xFF5865F2.toInt(),
        "com.Slack" to 0xFF4A154B.toInt(),
        "com.microsoft.teams" to 0xFF6264A7.toInt(),
        "com.snapchat.android" to 0xFFFFFC00.toInt(),
        "com.spotify.music" to 0xFF1DB954.toInt(),
        "com.reddit.frontpage" to 0xFFFF4500.toInt(),
        "com.twitter.android" to 0xFF1D9BF0.toInt(),
        "com.zhiliaoapp.musically" to 0xFFFE2C55.toInt(),
        "com.netflix.mediaclient" to 0xFFE50914.toInt(),
        "tv.twitch.android.app" to 0xFF9146FF.toInt(),
        "im.vector.app" to 0xFF0DBD8B.toInt(),
        "jp.naver.line.android" to 0xFF06C755.toInt(),
        "com.tencent.mm" to 0xFF07C160.toInt(),
        "com.skype.raider" to 0xFF00AFF0.toInt(),
        "com.viber.voip" to 0xFF7360F2.toInt(),
        "com.hilight.studio" to 0xFF7C4DFF.toInt(),
    )

    const val DEFAULT_FALLBACK = 0xFF00E676.toInt()

    fun detectColor(ctx: Context?, pkg: String): Int {
        SIGNATURES[pkg]?.let { return it }
        if (ctx != null) {
            runCatching {
                val icon = ctx.packageManager.getApplicationIcon(pkg)
                val bmp = icon.toBitmap(48, 48)
                val extracted = extractProminentColor(bmp)
                if (extracted != null) return extracted
            }
        }
        return DEFAULT_FALLBACK
    }

    /**
     * Extracts the most vibrant, saturated color from a bitmap icon.
     */
    fun extractProminentColor(bmp: Bitmap): Int? {
        val w = bmp.width
        val h = bmp.height
        val hsv = FloatArray(3)
        var bestColor: Int? = null
        var maxScore = 0f

        val stepX = (w / 16).coerceAtLeast(1)
        val stepY = (h / 16).coerceAtLeast(1)

        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val pixel = bmp.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 160) continue // Skip transparent or semi-transparent

                android.graphics.Color.colorToHSV(pixel, hsv)
                val sat = hsv[1]
                val value = hsv[2]

                // Ignore near-greyscale, near-black, and washed out white
                if (sat < 0.25f || value < 0.2f || (sat < 0.2f && value > 0.85f)) continue

                // Score by vibrancy and saturation
                val score = sat * 1.5f + value
                if (score > maxScore) {
                    maxScore = score
                    bestColor = 0xFF000000.toInt() or (pixel and 0x00FFFFFF)
                }
            }
        }
        return bestColor
    }
}
