package com.hilight.studio

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.MeshGradientPainter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The graphics Google previewed alongside Android 18 at Android Dev Summit 2026, applied to HiLight.
 *
 * - **Mesh gradients** ([MeshGradientPainter]): the light HiLight throws around the phone in the Live
 *   hero is a coarse mesh whose vertex colours come from the eight live LED colours, so a chase or a
 *   rainbow visibly travels around the halo instead of collapsing into one averaged blob.
 * - **Progressive blur** ([BlurRadiusSpec]): a tilt-shift depth of field keeps the camera bar and the
 *   HiLight disc sharp while the rest of the phone falls out of focus, and scrolling content softens
 *   into the edges of each tab instead of being cut off.
 *
 * Both run on the GPU (the mesh through android.graphics.Mesh on API 34+, the blur through runtime
 * shaders on API 33+), and HiLight's floor is API 37, so neither needs a fallback path here. Both can
 * still be switched off under Setup → Appearance, which restores the previous flat rendering.
 */
val LocalDepthEffects = compositionLocalOf { true }

// ------------------------------------------------------------------ hero geometry

/**
 * Where the phone, its camera island and the HiLight disc sit in the Live hero.
 *
 * Shared by the drawing code and the depth-of-field blur, so the focus band always lands on the
 * island whatever the card's width or the device profile.
 */
internal class HeroGeometry(
    val left: Float,
    val top: Float,
    val phoneW: Float,
    val phoneH: Float,
    val corner: Float,
    /** Centre of the HiLight diffuser disc. */
    val source: Offset,
    /** Vertical extent of the camera bar (or the Fold's camera block). */
    val islandTop: Float,
    val islandBottom: Float,
)

internal fun heroGeometry(size: Size, profile: DeviceProfile): HeroGeometry {
    val phoneW = size.width * (if (profile.foldStyle) 0.94f else 0.84f)
    val phoneH = size.height * 1.5f
    val left = (size.width - phoneW) / 2f
    val top = size.height * 0.10f
    val corner = phoneW * (if (profile.foldStyle) 0.08f else 0.14f)
    return if (profile.foldStyle) {
        // Mirrors drawFoldCameraBlock.
        val blockW = phoneW * 0.44f
        val blockH = phoneW * 0.20f
        val blockLeft = left + phoneW * 0.05f
        val blockTop = top + phoneH * 0.06f
        HeroGeometry(
            left, top, phoneW, phoneH, corner,
            source = Offset(blockLeft + blockW * 0.83f, blockTop + blockH / 2f),
            islandTop = blockTop,
            islandBottom = blockTop + blockH,
        )
    } else {
        // Mirrors drawCenteredVisorBar and drawPixel11ProCameraIsland.
        val barW = phoneW * (if (profile.lensCount == 2) 0.86f else 0.94f)
        val barH = phoneW * 0.25f
        val barLeft = left + (phoneW - barW) / 2f
        val barTop = top + phoneW * 0.095f
        val inset = barH * 0.052f
        val innerW = barW - inset * 2f
        HeroGeometry(
            left, top, phoneW, phoneH, corner,
            source = Offset(barLeft + inset + innerW * 0.86f, barTop + barH / 2f),
            islandTop = barTop,
            islandBottom = barTop + barH,
        )
    }
}

// ------------------------------------------------------------------ mesh light field

/**
 * Samples the colour HiLight casts at an offset ([dx], [dy]) from the disc centre.
 *
 * Each LED contributes most in its own direction (LED 0 points up, then clockwise, matching the disc
 * drawing), with a von Mises-style angular kernel; close to the disc every LED contributes equally,
 * so the centre never shows a seam. The intensity falls off smoothly to zero at [reach].
 *
 * Writes `[r, g, b, a]` into [out], all in 0..1. The colour is normalised to full brightness and the
 * intensity carried in alpha, so interpolating towards a faint vertex fades the light instead of
 * darkening it to grey. Pure maths, so it is unit-tested on the JVM.
 */
internal fun ledFieldSample(frame: IntArray, dx: Float, dy: Float, reach: Float, out: FloatArray) {
    out[0] = 0f; out[1] = 0f; out[2] = 0f; out[3] = 0f
    val n = frame.size
    if (n == 0 || reach <= 0f) return
    val dist = sqrt(dx * dx + dy * dy)
    if (dist >= reach) return

    val directional = smoothstep(0f, reach * NEAR_FIELD, dist)
    val phi = atan2(dy, dx)
    var r = 0f
    var g = 0f
    var b = 0f
    var wSum = 0f
    for (i in 0 until n) {
        val theta = (i.toFloat() / n) * TWO_PI - HALF_PI
        val angular = exp(ANGULAR_SHARPNESS * (cos(phi - theta) - 1f))
        val w = 1f + (angular - 1f) * directional
        val c = frame[i]
        r += w * ((c shr 16) and 0xFF) / 255f
        g += w * ((c shr 8) and 0xFF) / 255f
        b += w * (c and 0xFF) / 255f
        wSum += w
    }
    if (wSum <= 0f) return
    r /= wSum; g /= wSum; b /= wSum
    val lum = max(r, max(g, b))
    if (lum <= 0.004f) return

    val t = 1f - dist / reach
    val falloff = t * t * (3f - 2f * t)
    out[0] = r / lum
    out[1] = g / lum
    out[2] = b / lum
    out[3] = (falloff * lum).coerceIn(0f, 1f)
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private const val TWO_PI = (2.0 * PI).toFloat()
private const val HALF_PI = (PI / 2.0).toFloat()
private const val ANGULAR_SHARPNESS = 2.4f
private const val NEAR_FIELD = 0.18f

/**
 * The halo HiLight throws onto the stage, plus a faint wash of the same light across the matte back.
 *
 * One instance per hero, created in `remember`. The mesh is only 7×5 vertices, so recolouring it every
 * LED frame is a few hundred multiplies; the GPU does the smooth bicubic fill in between.
 */
internal class LedLightField {
    private var frame: IntArray = IntArray(0)
    private var sizePx = Size.Zero
    private var source = Offset.Zero
    private var reach = 1f
    private var strength = 0f
    private val sample = FloatArray(4)
    private val bodyClip = Path()

    private val painter = MeshGradientPainter(ROWS, COLUMNS, hasBicubicColor = true) {
        for (row in 0..rows) {
            val v = row.toFloat() / rows
            for (col in 0..columns) {
                val u = col.toFloat() / columns
                ledFieldSample(
                    frame,
                    u * sizePx.width - source.x,
                    v * sizePx.height - source.y,
                    reach,
                    sample,
                )
                setVertex(
                    row = row,
                    column = col,
                    position = Offset(u, v),
                    color = Color(
                        red = sample[0],
                        green = sample[1],
                        blue = sample[2],
                        alpha = (sample[3] * strength).coerceIn(0f, 1f),
                    ),
                )
            }
        }
    }

    /** Halo behind the phone. Draw before the body. */
    fun DrawScope.drawHalo(frame: IntArray, geometry: HeroGeometry, bloom: Float) {
        if (!prepare(frame, geometry, bloom * HALO_ALPHA)) return
        with(painter) { draw(size) }
    }

    /** Light catching the back of the phone around the island. Draw after the body. */
    fun DrawScope.drawBodyWash(frame: IntArray, geometry: HeroGeometry, bloom: Float) {
        if (!prepare(frame, geometry, bloom * WASH_ALPHA)) return
        bodyClip.reset()
        bodyClip.addRoundRect(
            RoundRect(
                left = geometry.left,
                top = geometry.top,
                right = geometry.left + geometry.phoneW,
                bottom = geometry.top + geometry.phoneH,
                cornerRadius = CornerRadius(geometry.corner),
            )
        )
        clipPath(bodyClip) {
            with(painter) { draw(this@drawBodyWash.size) }
        }
    }

    private fun DrawScope.prepare(frame: IntArray, geometry: HeroGeometry, strength: Float): Boolean {
        if (strength <= 0.004f || frame.isEmpty()) return false
        this@LedLightField.frame = frame
        this@LedLightField.sizePx = size
        this@LedLightField.source = geometry.source
        this@LedLightField.reach = geometry.phoneW * REACH
        this@LedLightField.strength = strength
        return true
    }

    private companion object {
        const val ROWS = 4
        const val COLUMNS = 6
        const val REACH = 1.3f
        const val HALO_ALPHA = 0.62f
        const val WASH_ALPHA = 0.16f
    }
}

// ------------------------------------------------------------------ progressive blur

/**
 * Tilt-shift depth of field for the Live hero: the camera island stays sharp, the stage above it
 * softens a little and the body below falls away like a macro photo of the real phone.
 */
internal fun Modifier.heroDepthOfField(profile: DeviceProfile, enabled: Boolean): Modifier =
    if (!enabled) this else blur {
        val w = size.width.value
        val h = size.height.value
        if (w <= 0f || h <= 0f) return@blur
        val g = heroGeometry(Size(w, h), profile)
        val focusTop = (g.islandTop / h - 0.06f).coerceIn(0.02f, 0.90f)
        val focusBottom = (g.islandBottom / h + 0.10f).coerceIn(focusTop + 0.02f, 0.98f)
        blurRadiusSpec = BlurRadiusSpec.verticalGradient(
            listOf(
                BlurStop(0f, HERO_BLUR_TOP),
                BlurStop(focusTop, 0.dp),
                BlurStop(focusBottom, 0.dp),
                BlurStop(1f, HERO_BLUR_BOTTOM),
            )
        )
    }

private val HERO_BLUR_TOP = 2.dp
private val HERO_BLUR_BOTTOM = 8.dp

/**
 * Softens content into the top and bottom of a scrolling tab, the way Android 18's system surfaces do.
 *
 * Each edge only blurs when there is content beyond it, ramping in over the first [edge] of travel,
 * so a tab at rest at the top is untouched. Scroll position is read inside the blur's layer block,
 * which re-runs only the layer — scrolling never recomposes the tab.
 */
internal fun Modifier.scrollEdgeBlur(
    state: ScrollState,
    enabled: Boolean,
    edge: Dp = 32.dp,
    maxRadius: Dp = 6.dp,
): Modifier = if (!enabled) this else blur {
    val h = size.height
    if (h <= edge * 2) return@blur
    val edgePx = edge.toPx()
    val top = quantizeEdge(state.value / edgePx)
    val bottom = quantizeEdge((state.maxValue - state.value) / edgePx)
    if (top == 0f && bottom == 0f) return@blur
    val f = (edge / h).coerceIn(0.01f, 0.49f)
    blurRadiusSpec = BlurRadiusSpec.verticalGradient(
        listOf(
            BlurStop(0f, maxRadius * top),
            BlurStop(f, 0.dp),
            BlurStop(1f - f, 0.dp),
            BlurStop(1f, maxRadius * bottom),
        )
    )
}

/**
 * Snaps an edge's 0..1 blur amount to tenths. Mid-scroll every frame then produces an equal spec,
 * which lets the blur node reuse its RenderEffect instead of rebuilding it per pixel of travel.
 */
internal fun quantizeEdge(raw: Float): Float {
    if (raw.isNaN() || raw <= 0f) return 0f
    return min(1f, kotlin.math.ceil(raw * 10f) / 10f)
}
