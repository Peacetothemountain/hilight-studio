package com.hilight.studio

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * The device drawn in the Live hero.
 *
 * Google's press renders are copyrighted, so this is a vector reconstruction rather than a bundled
 * image — which also lets the glow be driven by the live pattern maths. The layouts follow the launch
 * hardware: on the Pro and Pro XL, HiLight sits at the right-hand end of the full-width camera bar;
 * on the Pro Fold the bar is a compact block in the top-left corner; the non-Pro Pixel 11 has no
 * HiLight at all.
 */
enum class DeviceProfile(
    /**
     * Marketing name, which is not translated — a Pixel 11 Pro is called that in every language.
     * [labelRes] is set only for the fallback profile, whose label is a phrase rather than a name.
     */
    val label: String,
    @StringRes val labelRes: Int? = null,
    /** width / height of the body */
    val aspect: Float,
    val lensCount: Int,
    val hasHiLight: Boolean,
    val foldStyle: Boolean = false,
    /** body width as a fraction of the canvas — over 1 means the device runs off the sides */
    val zoom: Float = 0.93f,
    /** where the body's left edge sits, as a fraction of the canvas */
    val originX: Float = -1f,
) {
    PRO_XL("Pixel 11 Pro XL", aspect = 0.470f, lensCount = 3, hasHiLight = true),
    PRO("Pixel 11 Pro", aspect = 0.455f, lensCount = 3, hasHiLight = true),
    FOLD(
        "Pixel 11 Pro Fold", aspect = 0.950f, lensCount = 3, hasHiLight = true, foldStyle = true,
        zoom = 1.15f, originX = 0.035f,
    ),
    BASE("Pixel 11", aspect = 0.462f, lensCount = 2, hasHiLight = false),
    GENERIC("this device", labelRes = R.string.device_generic, aspect = 0.465f, lensCount = 3, hasHiLight = true),
    ;

    companion object {
        /** Best-effort match on the marketing name, which is what Build.MODEL carries on Pixels. */
        fun detect(model: String = Build.MODEL): DeviceProfile {
            val m = model.lowercase()
            return when {
                !m.contains("pixel") -> GENERIC
                m.contains("fold") -> FOLD
                m.contains("pro xl") -> PRO_XL
                m.contains("pro") -> PRO
                m.contains("pixel 11") -> BASE
                else -> GENERIC
            }
        }
    }
}

@Composable
fun rememberDeviceProfile(): DeviceProfile = remember { DeviceProfile.detect() }

/**
 * The back of the phone with HiLight lit by the real pattern maths, plus the light it pools onto the
 * surface underneath — which is how the feature is actually seen, face-down.
 *
 * The array reads as one diffused disc rather than eight pinpoints, because the eight LEDs sit behind
 * the flash window; the individual colours still drive the disc, so a chase or a rainbow visibly
 * travels around it.
 */
@Composable
fun DeviceHero(
    pattern: Pattern,
    cfg: Ambient,
    active: Boolean,
    modifier: Modifier = Modifier,
    profile: DeviceProfile = rememberDeviceProfile(),
    heightDp: Int = 210,
) {
    val frame = rememberLedFrame(pattern, cfg, active && profile.hasHiLight)
    val bloom by animateFloatAsState(
        targetValue = if (active && profile.hasHiLight) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "bloom",
    )

    // Assembled before the modifier chain because a semantics block is not a composable scope
    val modelName = profile.labelRes?.let { stringResource(it) } ?: profile.label
    val doing = when {
        !profile.hasHiLight -> stringResource(R.string.hero_no_array)
        !active -> stringResource(R.string.hero_array_off)
        else -> stringResource(R.string.hero_showing, stringResource(pattern.labelRes))
    }
    val description = stringResource(R.string.hero_description, modelName, doing)

    Box(
        modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(28.dp))
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxWidth().height(heightDp.dp)) {
            // Stage studio backdrop with radial lighting
            drawRect(Stage)
            drawRect(
                brush = Brush.radialGradient(
                    listOf(StageHigh, Stage),
                    center = Offset(size.width / 2f, size.height * 0.40f),
                    radius = size.width * 0.75f,
                ),
            )

            // Centered phone shell with visible outer body and chassis
            val phoneW = size.width * (if (profile.foldStyle) 0.94f else 0.84f)
            val phoneH = size.height * 1.5f
            val left = (size.width - phoneW) / 2f
            val top = size.height * 0.10f
            val corner = phoneW * (if (profile.foldStyle) 0.08f else 0.14f)

            if (bloom > 0.01f) drawSpill(frame, left, top, phoneW, bloom)
            drawBody(left, top, phoneW, phoneH, corner)

            if (profile.foldStyle) {
                drawFoldCameraBlock(frame, left, top, phoneW, phoneH, bloom)
            } else {
                drawCenteredVisorBar(frame, left, top, phoneW, phoneH, profile, bloom)
            }
        }
    }
}

// The device keeps its own graphite palette whatever the wallpaper does: a real Pixel is dark, and the
// LEDs only read as light against a dark body.
// Realistic Google Pixel 11 Pro Industrial Palette (Obsidian / Hazel Matte & Satin Titanium)
private val Stage = Color(0xFF090B0E)
private val StageHigh = Color(0xFF14171D)
private val Body = Color(0xFF383B3A)
private val BodyEdgeHighlight = Color(0xFF5E6360)
private val BodyEdgeShadow = Color(0xFF262827)
private val AntennaLine = Color(0xFF222423)
private val VisorBody = Color(0xFF181B21)
private val VisorEdge = Color(0xFF636C7A)
private val SapphirePill = Color(0xFF040608)
private val Lens = Color(0xFF040608)

private fun DrawScope.drawBody(left: Float, top: Float, w: Float, h: Float, corner: Float) {
    // 1. Drop shadow / contact shadow
    drawRoundRect(
        Color.Black.copy(alpha = 0.65f),
        Offset(left, top + h * 0.015f),
        Size(w, h),
        CornerRadius(corner),
    )

    // 2. Satin frosted Hazel / Obsidian matte glass back
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0xFF454947), Color(0xFF383B39), Color(0xFF2D302E)),
            start = Offset(left, top),
            end = Offset(left + w, top + h * 0.8f),
        ),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(corner),
    )

    // 3. Precision CNC-machined titanium perimeter frame
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(BodyEdgeHighlight, BodyEdgeShadow, BodyEdgeHighlight.copy(alpha = 0.6f)),
            start = Offset(left, top),
            end = Offset(left + w, top + h),
        ),
        Offset(left, top),
        Size(w, h),
        CornerRadius(corner),
        style = Stroke(width = size.height * 0.007f),
    )

    // 4. Antenna line notches as on official device
    val antH = h * 0.022f
    val antW = w * 0.007f
    // Top center antenna line
    drawRect(
        color = AntennaLine,
        topLeft = Offset(left + w * 0.50f - antW / 2f, top),
        size = Size(antW, antH),
    )
    // Left side antenna line
    drawRect(
        color = AntennaLine,
        topLeft = Offset(left, top + h * 0.32f),
        size = Size(antH, antW),
    )

    // 5. Subtle studio softbox specular light reflection
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
            start = Offset(left, top),
            end = Offset(left + w * 0.6f, top + h * 0.4f),
        ),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(corner),
    )
}

/**
 * Centered camera visor bar across the phone back with authentic Pixel 11 Pro triple lens
 * pill cutout and prominent HiLight frosted optical diffuser flash module.
 */
private fun DrawScope.drawCenteredVisorBar(
    frame: IntArray,
    left: Float,
    top: Float,
    phoneW: Float,
    phoneH: Float,
    profile: DeviceProfile,
    bloom: Float,
) {
    val barW = phoneW * 0.94f
    val barH = phoneW * 0.25f
    val barLeft = left + (phoneW - barW) / 2f
    val barTop = top + phoneW * 0.095f

    drawPixel11ProCameraIsland(
        colors = frame,
        barLeft = barLeft,
        barTop = barTop,
        barW = barW,
        barH = barH,
        bloom = bloom,
    )
}

/**
 * Direct 1:1 photorealistic rendering of the official Pixel 11 Pro XL camera island
 * matching the hardware specifications and official photography.
 */
private fun DrawScope.drawPixel11ProCameraIsland(
    colors: IntArray,
    barLeft: Float,
    barTop: Float,
    barW: Float,
    barH: Float,
    bloom: Float,
    isCroppedMacro: Boolean = false,
) {
    val cy = barTop + barH / 2f
    val cornerRadius = CornerRadius(barH / 2f)

    // 1. Deep Ambient Occlusion Drop Shadow casting downwards onto matte phone back
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.70f),
        topLeft = Offset(barLeft, barTop + barH * 0.12f),
        size = Size(barW, barH),
        cornerRadius = cornerRadius,
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.38f),
        topLeft = Offset(barLeft, barTop + barH * 0.22f),
        size = Size(barW, barH),
        cornerRadius = cornerRadius,
    )

    // 2. Precision-machined Satin Titanium Island Perimeter Frame
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color(0xFF6B706E), // Top metallic specular sheen
                Color(0xFF484C4A), // Mid-tone titanium
                Color(0xFF2B2E2C), // Bottom shadow
            ),
            startY = barTop,
            endY = barTop + barH,
        ),
        topLeft = Offset(barLeft, barTop),
        size = Size(barW, barH),
        cornerRadius = cornerRadius,
    )

    // Outer titanium edge chamfer stroke
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0xFF7E8481), Color(0xFF383C3A), Color(0xFF686D6B)),
            start = Offset(barLeft, barTop),
            end = Offset(barLeft + barW, barTop + barH),
        ),
        topLeft = Offset(barLeft, barTop),
        size = Size(barW, barH),
        cornerRadius = cornerRadius,
        style = Stroke(width = barH * 0.032f),
    )

    // Top specular light hairline
    drawLine(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.40f), Color.Transparent),
            startX = barLeft + barW * 0.15f,
            endX = barLeft + barW * 0.85f,
        ),
        start = Offset(barLeft + barW * 0.15f, barTop + 1f),
        end = Offset(barLeft + barW * 0.85f, barTop + 1f),
        strokeWidth = barH * 0.016f,
    )

    // 3. Jet-Black Sapphire Crystal Pill Window (Continuous Inset Capsule)
    val innerInset = barH * 0.052f
    val innerW = barW - innerInset * 2
    val innerH = barH - innerInset * 2
    val innerLeft = barLeft + innerInset
    val innerTop = barTop + innerInset
    val innerCornerRadius = CornerRadius(innerH / 2f)

    drawRoundRect(
        color = Color(0xFF030405), // Ultra-deep obsidian sapphire black
        topLeft = Offset(innerLeft, innerTop),
        size = Size(innerW, innerH),
        cornerRadius = innerCornerRadius,
    )
    // Dark metallic glass retention bezel
    drawRoundRect(
        color = Color(0xFF1B1E1D),
        topLeft = Offset(innerLeft, innerTop),
        size = Size(innerW, innerH),
        cornerRadius = innerCornerRadius,
        style = Stroke(width = barH * 0.018f),
    )

    // Glass specular top-reflection arc
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
            start = Offset(innerLeft, innerTop),
            end = Offset(innerLeft + innerW * 0.5f, innerTop + innerH * 0.5f),
        ),
        topLeft = Offset(innerLeft, innerTop),
        size = Size(innerW, innerH),
        cornerRadius = innerCornerRadius,
    )

    // 4. Triple Optical Lenses (Wide 50MP, Center Ultrawide 48MP, 5x Telephoto 48MP)
    val lensR = innerH * 0.36f
    // As seen on real device: Left Lens (0.19w), Center Lens (0.42w), Right Lens (0.64w), Flash (0.86w)
    val lx1 = innerLeft + innerW * 0.19f
    val lx2 = innerLeft + innerW * 0.42f
    val lx3 = innerLeft + innerW * 0.64f
    val flashX = innerLeft + innerW * 0.86f

    // Lens 1: Wide 50MP
    drawOfficialCameraLens(lx1, cy, lensR, 1.0f)
    // Lens 2: Center Ultrawide 48MP (slightly larger aperture ring)
    drawOfficialCameraLens(lx2, cy, lensR * 1.06f, 1.06f)
    // Lens 3: 5x Telephoto 48MP
    drawOfficialCameraLens(lx3, cy, lensR, 1.0f)

    // Tiny sensor dot between lens 3 and flash
    val sensorX = innerLeft + innerW * 0.76f
    drawCircle(
        color = Color(0xFF0D0F0E),
        radius = barH * 0.026f,
        center = Offset(sensorX, cy),
    )

    // 5. Official HiLight Diffuser Flash Disc (Far Right - Inset in Sapphire Glass)
    val flashR = innerH * 0.28f
    drawHiLightDisc(colors, Offset(flashX, cy), flashR, bloom)
}

/**
 * Official Google Pixel 11 Pro triple camera optics rendering:
 * Concentric knurled iris rings, deep sensor well, dark sapphire multi-coatings,
 * and realistic glass glints.
 */
private fun DrawScope.drawOfficialCameraLens(cx: Float, cy: Float, r: Float, scale: Float) {
    // Outer titanium lens retention ring
    drawCircle(Color(0xFF1E2120), radius = r, center = Offset(cx, cy))
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(Color(0xFF3C403E), Color(0xFF1A1C1B), Color(0xFF484C4A), Color(0xFF1A1C1B), Color(0xFF3C403E)),
            center = Offset(cx, cy),
        ),
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.10f),
    )

    // Deep black optical cavity
    drawCircle(Color(0xFF040506), radius = r * 0.86f, center = Offset(cx, cy))
    drawCircle(Color(0xFF010203), radius = r * 0.58f, center = Offset(cx, cy))

    // Anti-reflective multi-coating reflection (Dark sapphire blue with subtle emerald tint)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF1E3A6E).copy(alpha = 0.75f), Color.Transparent),
            center = Offset(cx - r * 0.20f, cy - r * 0.22f),
            radius = r * 0.65f,
        ),
        radius = r * 0.65f,
        center = Offset(cx - r * 0.20f, cy - r * 0.22f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF1B4E43).copy(alpha = 0.45f), Color.Transparent),
            center = Offset(cx + r * 0.18f, cy + r * 0.20f),
            radius = r * 0.50f,
        ),
        radius = r * 0.50f,
        center = Offset(cx + r * 0.18f, cy + r * 0.20f),
    )

    // Specular glass pinpoint reflections
    drawCircle(
        Color.White.copy(alpha = 0.85f),
        radius = r * 0.08f,
        center = Offset(cx - r * 0.22f, cy - r * 0.25f),
    )
    drawCircle(
        Color.White.copy(alpha = 0.45f),
        radius = r * 0.04f,
        center = Offset(cx + r * 0.20f, cy + r * 0.22f),
    )
}

/** Pro Fold: compact camera block in the top-left corner, HiLight inside it. */
private fun DrawScope.drawFoldCameraBlock(
    frame: IntArray,
    left: Float,
    top: Float,
    phoneW: Float,
    phoneH: Float,
    bloom: Float,
) {
    val blockW = phoneW * 0.44f
    val blockH = phoneW * 0.20f
    val blockLeft = left + phoneW * 0.05f
    val blockTop = top + phoneH * 0.06f
    val cy = blockTop + blockH / 2f

    // Shadow
    drawRoundRect(
        Color.Black.copy(alpha = 0.55f),
        Offset(blockLeft, blockTop + blockH * 0.08f),
        Size(blockW, blockH),
        CornerRadius(blockH * 0.35f),
    )
    // Titanium block
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF262B34), VisorBody, Color(0xFF121419)),
            startY = blockTop,
            endY = blockTop + blockH,
        ),
        Offset(blockLeft, blockTop),
        Size(blockW, blockH),
        CornerRadius(blockH * 0.35f),
    )
    drawRoundRect(
        VisorEdge.copy(alpha = 0.7f),
        Offset(blockLeft, blockTop),
        Size(blockW, blockH),
        CornerRadius(blockH * 0.35f),
        style = Stroke(width = size.height * 0.003f),
    )

    val lensR = blockH * 0.26f
    drawLens(blockLeft + blockW * 0.16f, cy, lensR)
    drawLens(blockLeft + blockW * 0.38f, cy, lensR)
    drawPeriscopeLens(blockLeft + blockW * 0.60f, cy, lensR)
    drawHiLightDisc(frame, Offset(blockLeft + blockW * 0.83f, cy), blockH * 0.28f, bloom)

    // Stainless steel hinge seam
    drawRoundRect(
        BodyEdgeHighlight.copy(alpha = 0.65f),
        Offset(left + phoneW * 0.495f, top + phoneH * 0.02f),
        Size(phoneW * 0.012f, phoneH * 0.96f),
        CornerRadius(phoneW * 0.006f),
    )
}

/** 50MP Wide & 48MP Ultrawide Camera Lens */
private fun DrawScope.drawLens(cx: Float, cy: Float, r: Float) {
    // Outer lens housing with beveled metallic ring
    drawCircle(Color(0xFF2C313A), radius = r * 1.08f, center = Offset(cx, cy))
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(Color(0xFF4F5663), Color(0xFF23272F), Color(0xFF606877), Color(0xFF23272F), Color(0xFF4F5663)),
            center = Offset(cx, cy),
        ),
        radius = r * 1.05f,
        center = Offset(cx, cy),
        style = Stroke(width = size.height * 0.0028f),
    )
    drawCircle(Lens, radius = r, center = Offset(cx, cy))

    // Deep optical sensor cavity
    drawCircle(Color(0xFF090B0E), radius = r * 0.76f, center = Offset(cx, cy))
    drawCircle(Color(0xFF030406), radius = r * 0.50f, center = Offset(cx, cy))

    // Multi-coated anti-reflective glass reflections (sapphire blue & emerald green)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF38558A).copy(alpha = 0.60f), Color.Transparent),
            center = Offset(cx - r * 0.22f, cy - r * 0.25f),
            radius = r * 0.60f,
        ),
        radius = r * 0.60f,
        center = Offset(cx - r * 0.22f, cy - r * 0.25f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF1E6B52).copy(alpha = 0.40f), Color.Transparent),
            center = Offset(cx + r * 0.18f, cy + r * 0.20f),
            radius = r * 0.45f,
        ),
        radius = r * 0.45f,
        center = Offset(cx + r * 0.18f, cy + r * 0.20f),
    )
    // Specular glass point highlight
    drawCircle(
        Color.White.copy(alpha = 0.75f),
        radius = r * 0.08f,
        center = Offset(cx - r * 0.25f, cy - r * 0.28f),
    )
}

/** 5x Telephoto Periscope Rectangular Prism Lens */
private fun DrawScope.drawPeriscopeLens(cx: Float, cy: Float, r: Float) {
    val boxSize = r * 1.6f
    val boxLeft = cx - boxSize / 2f
    val boxTop = cy - boxSize / 2f
    val corner = boxSize * 0.25f

    // Outer dark titanium casing
    drawRoundRect(Color(0xFF2C313A), Offset(boxLeft, boxTop), Size(boxSize, boxSize), CornerRadius(corner))
    drawRoundRect(
        Color(0xFF4F5663),
        Offset(boxLeft, boxTop),
        Size(boxSize, boxSize),
        CornerRadius(corner),
        style = Stroke(width = size.height * 0.0025f),
    )
    // Deep dark periscope aperture
    val innerSize = boxSize * 0.76f
    val innerLeft = cx - innerSize / 2f
    val innerTop = cy - innerSize / 2f
    drawRoundRect(Color(0xFF030406), Offset(innerLeft, innerTop), Size(innerSize, innerSize), CornerRadius(corner * 0.7f))

    // Rectangular prism anti-reflective reflection
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0xFF38558A).copy(alpha = 0.45f), Color(0xFF1E6B52).copy(alpha = 0.25f), Color.Transparent),
            start = Offset(innerLeft, innerTop),
            end = Offset(innerLeft + innerSize, innerTop + innerSize),
        ),
        topLeft = Offset(innerLeft, innerTop),
        size = Size(innerSize, innerSize),
        cornerRadius = CornerRadius(corner * 0.7f),
    )
    // Specular corner highlight
    drawCircle(
        Color.White.copy(alpha = 0.65f),
        radius = r * 0.07f,
        center = Offset(innerLeft + innerSize * 0.25f, innerTop + innerSize * 0.25f),
    )
}

/**
 * 1:1 Official Pixel 11 Pro HiLight Flash Optical Diffuser Module:
 * Precision-machined titanium circular bezel ring with a frosted white optical diffuser disc
 * housing 8 addressable micro-LED emitters with true volumetric light scattering.
 */
private fun DrawScope.drawHiLightDisc(colors: IntArray, center: Offset, radius: Float, bloom: Float) {
    val bezelR = radius * 1.16f

    // 1. Brushed aerospace titanium bezel housing
    drawCircle(Color(0xFF15181F), radius = bezelR, center = center)
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(
                Color(0xFF6B7280), Color(0xFF2E333C), Color(0xFF868E9C),
                Color(0xFF2E333C), Color(0xFF6B7280)
            ),
            center = center,
        ),
        radius = bezelR,
        center = center,
        style = Stroke(width = size.height * 0.0035f),
    )
    // Inner bezel recession shadow
    drawCircle(Color(0xFF080A0E), radius = radius * 1.02f, center = center)

    val n = colors.size.coerceAtLeast(1)
    var lumSum = 0f
    var r = 0f
    var g = 0f
    var b = 0f
    for (i in 0 until colors.size) {
        val c = Color(colors[i])
        lumSum += (c.red + c.green + c.blue) / 3f
        r += c.red; g += c.green; b += c.blue
    }
    val avg = Color(r / n, g / n, b / n)
    val lum = (lumSum / n).coerceIn(0f, 1f)
    val isLit = bloom > 0.01f && lum > 0.01f

    // 2. Frosted Milky-White Diffuser Disc
    clipPath(Path().apply { addOval(Rect(center = center, radius = radius)) }) {
        // Base milky diffuser background
        val milkyBase = if (isLit) Color(0xFFF4F7FB) else Color(0xFFE2E7EF)
        val milkyEdge = if (isLit) Color(0xFFD0D8E4) else Color(0xFFB8C0CC)

        drawCircle(
            brush = Brush.radialGradient(
                listOf(milkyBase, milkyEdge),
                center = Offset(center.x - radius * 0.12f, center.y - radius * 0.12f),
                radius = radius,
            ),
            radius = radius,
            center = center,
        )

        // Micro-Fresnel Circular Texture Etching Rings
        drawCircle(
            Color.White.copy(alpha = if (isLit) 0.35f else 0.45f),
            radius = radius * 0.80f,
            center = center,
            style = Stroke(width = size.height * 0.0018f),
        )
        drawCircle(
            Color.White.copy(alpha = if (isLit) 0.30f else 0.40f),
            radius = radius * 0.56f,
            center = center,
            style = Stroke(width = size.height * 0.0018f),
        )
        drawCircle(
            Color.White.copy(alpha = if (isLit) 0.25f else 0.35f),
            radius = radius * 0.34f,
            center = center,
            style = Stroke(width = size.height * 0.0015f),
        )

        // 3. The 8 Micro-LED Emitters
        val emitterDistance = radius * 0.58f
        val emitterRadius = radius * 0.19f
        val ledCount = if (colors.isNotEmpty()) colors.size else 8

        for (i in 0 until ledCount) {
            val angle = (i.toFloat() / ledCount) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
            val pos = Offset(
                center.x + cos(angle) * emitterDistance,
                center.y + sin(angle) * emitterDistance,
            )

            if (!isLit || colors.isEmpty()) {
                // OFF state: subtle translucent micro-diode cavities visible through white frosted glass
                drawCircle(
                    Color(0xFF9EA6B2).copy(alpha = 0.55f),
                    radius = emitterRadius * 0.80f,
                    center = pos,
                )
                drawCircle(
                    Color(0xFF788190).copy(alpha = 0.45f),
                    radius = emitterRadius * 0.48f,
                    center = pos,
                )
                drawCircle(
                    Color.White.copy(alpha = 0.50f),
                    radius = emitterRadius * 0.22f,
                    center = Offset(pos.x - emitterRadius * 0.15f, pos.y - emitterRadius * 0.15f),
                )
            } else {
                val ledColor = Color(colors[i % colors.size])
                val ledLum = (ledColor.red + ledColor.green + ledColor.blue) / 3f
                if (ledLum > 0.01f) {
                    // Volumetric diffusion cone inside frosted silica glass
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                ledColor.copy(alpha = 0.90f * bloom),
                                ledColor.copy(alpha = 0.40f * bloom),
                                Color.Transparent,
                            ),
                            center = pos,
                            radius = radius * 0.75f,
                        ),
                        radius = radius * 0.75f,
                        center = pos,
                    )
                    // High-intensity diode emitter core
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                Color.White.copy(alpha = 0.98f * bloom),
                                ledColor.copy(alpha = 0.92f * bloom),
                            ),
                            center = pos,
                            radius = emitterRadius * 0.90f,
                        ),
                        radius = emitterRadius * 0.90f,
                        center = pos,
                    )
                    // Brilliant diode spark
                    drawCircle(
                        Color.White.copy(alpha = 0.95f * bloom),
                        radius = emitterRadius * 0.38f,
                        center = pos,
                    )
                }
            }
        }

        if (isLit) {
            // Overall luminous milky glow blending across the diffuser disc
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        avg.copy(alpha = 0.48f * bloom * lum),
                        avg.copy(alpha = 0.16f * bloom * lum),
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            // Hot optical center flare
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.70f * bloom * lum),
                        avg.copy(alpha = 0.22f * bloom * lum),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius * 0.55f,
                ),
                radius = radius * 0.55f,
                center = center,
            )
        }

        // Frosted glass top specular surface reflection arc
        drawCircle(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                start = Offset(center.x - radius * 0.6f, center.y - radius * 0.7f),
                end = Offset(center.x + radius * 0.4f, center.y + radius * 0.3f),
            ),
            radius = radius,
            center = center,
        )
    }

    // 4. External luminous radiance bloom onto visor and back glass
    if (isLit) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    avg.copy(alpha = 0.55f * bloom * lum),
                    avg.copy(alpha = 0.20f * bloom * lum),
                    Color.Transparent,
                ),
                center = center,
                radius = radius * 2.8f,
            ),
            radius = radius * 2.8f,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.35f * bloom * lum),
                    avg.copy(alpha = 0.10f * bloom * lum),
                    Color.Transparent,
                ),
                center = center,
                radius = radius * 1.5f,
            ),
            radius = radius * 1.5f,
            center = center,
        )
    }
}

/** Soft pool of colour under the phone reflecting off the desk surface. */
private fun DrawScope.drawSpill(colors: IntArray, left: Float, top: Float, phoneW: Float, bloom: Float) {
    var r = 0f
    var g = 0f
    var b = 0f
    colors.forEach {
        val c = Color(it)
        r += c.red; g += c.green; b += c.blue
    }
    val n = colors.size.coerceAtLeast(1)
    val avg = Color(r / n, g / n, b / n)
    val lum = (avg.red + avg.green + avg.blue) / 3f
    if (lum < 0.02f) return
    val center = Offset(left + phoneW * 0.75f, top + phoneW * 0.22f)
    val radius = phoneW * 1.35f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                avg.copy(alpha = 0.38f * bloom * lum),
                avg.copy(alpha = 0.12f * bloom * lum),
                Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * Drives previews at the hardware's own frame rate using the shared pattern maths, so what is on
 * screen matches what the LEDs are doing.
 */
@Composable
fun rememberLedFrame(pattern: Pattern, cfg: Ambient, active: Boolean = true): IntArray {
    val dark = IntArray(LED_COUNT) { 0xFF000000.toInt() }
    val frame by produceState(dark, pattern, cfg, active) {
        if (!active) {
            value = dark
            return@produceState
        }
        val start = System.currentTimeMillis()
        while (true) {
            value = Renderer.frame(pattern, System.currentTimeMillis() - start, cfg)
            delay(33)                       // Light.getMinUpdatePeriodMillis()
        }
    }
    return frame
}

/** Compact strip used on rule cards: an abstraction of the array, one dot per addressable LED. */
@Composable
fun LedStrip(
    pattern: Pattern,
    cfg: Ambient,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    heightDp: Int = 40,
) {
    val frame = rememberLedFrame(pattern, cfg, active)
    val patternName = stringResource(pattern.labelRes)
    val label = if (active) stringResource(R.string.hero_strip_preview, patternName)
    else stringResource(R.string.hero_strip_off, patternName)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .semantics { contentDescription = label },
    ) {
        val n = frame.size
        val gap = size.width / (n * 3.2f)
        val d = (size.width - gap * (n - 1)) / n
        val r = minOf(d, size.height) / 2.6f
        for (i in 0 until n) {
            val c = Color(frame[i])
            val cx = i * (d + gap) + d / 2f
            val cyc = size.height / 2f
            val lum = (c.red + c.green + c.blue) / 3f
            if (lum > 0.02f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(c.copy(alpha = 0.45f * lum), Color.Transparent),
                        center = Offset(cx, cyc),
                        radius = r * 3.4f,
                    ),
                    radius = r * 3.4f,
                    center = Offset(cx, cyc),
                )
            }
            drawCircle(Color.Black.copy(alpha = 0.14f), radius = r * 1.2f, center = Offset(cx, cyc))
            drawCircle(c, radius = r, center = Offset(cx, cyc))
        }
    }
}

/**
 * Direct cropped close-up of the official Pixel 11 Pro XL camera visor island:
 * Used on the Style page to display the authentic titanium capsule bar
 * with the 3 sapphire lenses and the active HiLight 8-LED diffuser module.
 */
@Composable
fun HiLightDiffuserPreview(
    pattern: Pattern,
    cfg: Ambient,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val frame = rememberLedFrame(pattern, cfg, active)
    val bloom by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "diffuserBloom",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(108.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val barW = (w * 0.94f).coerceAtMost(360.dp.toPx())
            val barH = barW * 0.26f
            val barLeft = (w - barW) / 2f
            val barTop = (h - barH) / 2f

            drawPixel11ProCameraIsland(
                colors = frame,
                barLeft = barLeft,
                barTop = barTop,
                barW = barW,
                barH = barH,
                bloom = bloom,
                isCroppedMacro = true,
            )
        }
    }
}

