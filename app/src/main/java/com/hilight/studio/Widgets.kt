package com.hilight.studio

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

val PRESET_COLORS = listOf(
    0xFFFF1744, 0xFFFF6D00, 0xFFFFD600, 0xFF00E676, 0xFF00E5FF,
    0xFF2979FF, 0xFF7C4DFF, 0xFFFF4081, 0xFFFFFFFF, 0xFFFF80AB,
).map { it.toInt() }

internal class PreviewLauncher(
    private val launch: (Pattern, Int, Int, Float, Int, Direction, Ambient?) -> Unit,
) {
    operator fun invoke(
        pattern: Pattern,
        color: Int,
        speedMs: Int,
        brightness: Float,
        durationMs: Int,
        direction: Direction = Direction.FORWARD,
        look: Ambient? = null,
    ) = launch(pattern, color, speedMs, brightness, durationMs, direction, look)
}

/** Launches every in-app preview through one truthful guard check without adding UI controls. */
@Composable
internal fun rememberPreviewLauncher(store: Store): PreviewLauncher {
    val context = LocalContext.current.applicationContext
    val resources = LocalResources.current
    return remember(store, context, resources) {
        PreviewLauncher { pattern, color, speedMs, brightness, durationMs, direction, look ->
            val reason = store.previewSuppressionReason()
            if (reason == null) {
                if (look != null) store.previewLook(look.copy(direction = direction), durationMs)
                else store.preview(pattern, color, speedMs, brightness, durationMs, direction)
            } else {
                Toast.makeText(
                    context,
                    resources.getString(
                        R.string.test_blocked_by_guard,
                        resources.getString(reason.shortRes),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}

private enum class InlineColorStudioMode {
    SPECTRUM,
    TEMPERATURE_K,
}

/**
 * Swatches plus hue / saturation / intensity, enhanced with TileDialog-style Dual Luminous
 * Controls (Color Wheel Stencil Filter button + White/Temperature Auto-Select circle),
 * inline Spectrum | Temperature (K) studio, and live Hex pill with Reset.
 */
@Composable
fun ColorPicker(
    color: Int,
    onColor: (Int) -> Unit,
    // Callers editing a named colour pass their own heading, so this stays a String. The default is
    // read from resources, which a composable default expression is allowed to do.
    label: String = stringResource(R.string.widget_colour),
) {
    val hsv = remember(color) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
    }
    var retainedHue by remember { androidx.compose.runtime.mutableFloatStateOf(hsv[0]) }
    if (hsv[1] > 0.001f) {
        retainedHue = hsv[0]
    }
    val view = LocalView.current
    var showStudio by remember { mutableStateOf(false) }
    var studioMode by remember { mutableStateOf(InlineColorStudioMode.SPECTRUM) }
    var selectedKelvin by remember { mutableIntStateOf(4500) }
    var whiteTempColor by remember { mutableIntStateOf(TileDialogActivity.kelvinToRgb(4500)) }

    val infiniteTransition = rememberInfiniteTransition(label = "colorPickerLuminous")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinAngle",
    )
    val shimmerPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerPulse",
    )

    val selectedColor = Color(color)
    val goldGlow = Color(0xFFFFDF73)
    val isWhiteSelected = (color == whiteTempColor) && !showStudio

    val rainbowBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color.Red, Color.Yellow, Color.Green,
                Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
            )
        )
    }
    val colorWheelSpinBrush = Brush.sweepGradient(
        0.0f to selectedColor.copy(alpha = 0.98f * shimmerPulse),
        0.18f to selectedColor.copy(alpha = 0.85f * shimmerPulse),
        0.40f to selectedColor.copy(alpha = 0.35f * shimmerPulse),
        0.65f to Color.Transparent,
        0.82f to selectedColor.copy(alpha = 0.50f * shimmerPulse),
        1.0f to selectedColor.copy(alpha = 0.98f * shimmerPulse),
    )
    val goldShimmerBrush = Brush.sweepGradient(
        0.0f to Color(0xFFFFF9E6).copy(alpha = 0.98f * shimmerPulse),
        0.18f to goldGlow.copy(alpha = 0.85f * shimmerPulse),
        0.40f to Color(0xFFFFB300).copy(alpha = 0.35f * shimmerPulse),
        0.65f to Color.Transparent,
        0.82f to goldGlow.copy(alpha = 0.50f * shimmerPulse),
        1.0f to Color(0xFFFFF9E6).copy(alpha = 0.98f * shimmerPulse),
    )

    val hueBrush = remember {
        Brush.horizontalGradient((0..6).map { Color(Renderer.hsv(it * 60f)) })
    }
    val cctBrush = remember {
        Brush.horizontalGradient(
            listOf(
                Color(TileDialogActivity.kelvinToRgb(2000)),
                Color(TileDialogActivity.kelvinToRgb(3000)),
                Color(TileDialogActivity.kelvinToRgb(4500)),
                Color(TileDialogActivity.kelvinToRgb(5500)),
                Color(TileDialogActivity.kelvinToRgb(7000)),
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. Color Wheel Stencil Filter Button (36.dp)
                val wheelScale by animateFloatAsState(
                    targetValue = if (showStudio) 1.08f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "wheelScale",
                )
                Box(
                    modifier = Modifier
                        .scale(wheelScale)
                        .size(36.dp)
                        .then(
                            if (showStudio) {
                                Modifier.drawBehind {
                                    val radius = size.minDimension / 2f
                                    val ringRadius = radius + 3.5.dp.toPx()
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                selectedColor.copy(alpha = 0.40f * shimmerPulse),
                                                selectedColor.copy(alpha = 0.12f * shimmerPulse),
                                                Color.Transparent,
                                            ),
                                            center = center,
                                            radius = ringRadius + 8.dp.toPx(),
                                        )
                                    )
                                    rotate(spinAngle) {
                                        drawCircle(
                                            brush = colorWheelSpinBrush,
                                            radius = ringRadius,
                                            center = center,
                                            style = Stroke(
                                                width = 2.5.dp.toPx(),
                                                cap = StrokeCap.Round,
                                            ),
                                        )
                                    }
                                }
                            } else Modifier
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .then(
                            if (!showStudio) {
                                Modifier.border(
                                    width = 1.5.dp,
                                    brush = rainbowBrush,
                                    shape = CircleShape,
                                )
                            } else {
                                Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    shape = CircleShape,
                                )
                            }
                        )
                        .clickable(role = Role.Button) {
                            PixelHaptics.click(view)
                            showStudio = !showStudio
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.ColorLens,
                        contentDescription = "Color Studio",
                        tint = Color.White,
                        modifier = Modifier
                            .size(19.dp)
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithCache {
                                val filterLightBrush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.98f),
                                        selectedColor.copy(alpha = 0.95f),
                                        selectedColor,
                                        selectedColor.copy(alpha = 0.65f),
                                    ),
                                    center = Offset(size.width * 0.45f, size.height * 0.40f),
                                    radius = size.minDimension * 0.85f,
                                )
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(brush = filterLightBrush, blendMode = BlendMode.SrcIn)
                                }
                            },
                    )
                }

                // 2. White / Temperature Auto-Select Circle (36.dp)
                val whiteScale by animateFloatAsState(
                    targetValue = if (isWhiteSelected) 1.08f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "whiteScale",
                )
                Box(
                    modifier = Modifier
                        .scale(whiteScale)
                        .size(36.dp)
                        .drawBehind {
                            val radius = size.minDimension / 2f
                            val ringRadius = radius + 3.5.dp.toPx()
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        goldGlow.copy(alpha = (if (isWhiteSelected) 0.42f else 0.24f) * shimmerPulse),
                                        goldGlow.copy(alpha = 0.10f * shimmerPulse),
                                        Color.Transparent,
                                    ),
                                    center = center,
                                    radius = ringRadius + 8.dp.toPx(),
                                )
                            )
                            if (isWhiteSelected) {
                                rotate(spinAngle) {
                                    drawCircle(
                                        brush = goldShimmerBrush,
                                        radius = ringRadius,
                                        center = center,
                                        style = Stroke(
                                            width = 2.5.dp.toPx(),
                                            cap = StrokeCap.Round,
                                        ),
                                    )
                                }
                            }
                        }
                        .clip(CircleShape)
                        .background(Color(whiteTempColor))
                        .border(
                            width = if (isWhiteSelected) 2.dp else 1.2.dp,
                            color = goldGlow.copy(alpha = if (isWhiteSelected) 0.90f * shimmerPulse else 0.55f),
                            shape = CircleShape,
                        )
                        .clickable(role = Role.Button) {
                            PixelHaptics.click(view)
                            onColor(whiteTempColor)
                            showStudio = false
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Clean pure illuminated circle — no dot inside
                }

                // 3. Compact Hex Pill + Reset Button
                HexColorPill(
                    color = color,
                    onColorChanged = onColor,
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            CircleShape,
                        )
                        .clickable(role = Role.Button) {
                            PixelHaptics.click(view)
                            selectedKelvin = 4500
                            whiteTempColor = TileDialogActivity.kelvinToRgb(4500)
                            onColor(0xFFFFFFFF.toInt())
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.RestartAlt,
                        contentDescription = "Reset Color",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Expandable Inline Spectrum | Temperature (K) Studio
        AnimatedVisibility(
            visible = showStudio,
            enter = fadeIn() + expandVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(22.dp),
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        RoundedCornerShape(22.dp),
                    )
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Mode Switcher: Spectrum | Temperature (K)
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val isSpectrum = studioMode == InlineColorStudioMode.SPECTRUM
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSpectrum) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable(role = Role.Button) {
                                PixelHaptics.click(view)
                                studioMode = InlineColorStudioMode.SPECTRUM
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Spectrum",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSpectrum) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val isTemp = studioMode == InlineColorStudioMode.TEMPERATURE_K
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isTemp) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable(role = Role.Button) {
                                PixelHaptics.click(view)
                                studioMode = InlineColorStudioMode.TEMPERATURE_K
                                val c = TileDialogActivity.kelvinToRgb(selectedKelvin)
                                whiteTempColor = c
                                onColor(c)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Temperature (K)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isTemp) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (studioMode == InlineColorStudioMode.SPECTRUM) {
                    ColorSpectrumWheel(
                        color = color,
                        onColorChanged = onColor,
                        sizeDp = 200,
                    )
                } else {
                    val kelvinColor = Color(TileDialogActivity.kelvinToRgb(selectedKelvin))
                    Text(
                        text = "$selectedKelvin K",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = kelvinColor,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .padding(horizontal = 6.dp)
                                .background(cctBrush, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                        )
                        Slider(
                            value = selectedKelvin.toFloat(),
                            valueRange = 2000f..7000f,
                            onValueChange = { k ->
                                selectedKelvin = k.roundToInt()
                                val c = TileDialogActivity.kelvinToRgb(selectedKelvin)
                                whiteTempColor = c
                                onColor(c)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = kelvinColor,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent,
                            ),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            2700 to "Warm 2700K",
                            4500 to "Neutral 4500K",
                            6500 to "Cool 6500K",
                        ).forEach { (k, kLabel) ->
                            val kColor = TileDialogActivity.kelvinToRgb(k)
                            val isSelected = selectedKelvin == k && color == kColor
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color(kColor).copy(alpha = 0.45f),
                                        CircleShape,
                                    )
                                    .clickable(role = Role.Button) {
                                        PixelHaptics.click(view)
                                        selectedKelvin = k
                                        whiteTempColor = kColor
                                        onColor(kColor)
                                    }
                                    .padding(vertical = 7.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = kLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(kColor),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PRESET_COLORS.forEach { c ->
                val selected = c == color
                val scale by animateFloatAsState(
                    if (selected) 1.16f else 1f,
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "swatch",
                )
                Box(
                    Modifier
                        .scale(scale)
                        .size(34.dp)
                        .background(Color(c), CircleShape)
                        .border(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            CircleShape,
                        )
                        .clickable(role = Role.Button) {
                            PixelHaptics.click(view)
                            onColor(c)
                        }
                )
            }
        }

        // hue: the track is the spectrum, the thumb rides on top
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .padding(horizontal = 6.dp)
                    .background(hueBrush, CircleShape)
            )
            Slider(
                value = retainedHue,
                valueRange = 0f..359f,
                onValueChange = { h ->
                    retainedHue = h
                    val s = if (hsv[1] < 0.01f) 1f else hsv[1]
                    val v = if (hsv[2] < 0.05f) 1f else hsv[2]
                    onColor(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }

        PixelSlider(stringResource(R.string.widget_saturation), hsv[1], 0f..1f, { s ->
            val v = if (hsv[2] < 0.05f) 1f else hsv[2]
            onColor(android.graphics.Color.HSVToColor(floatArrayOf(retainedHue, s, v)))
        }) { stringResource(R.string.widget_percent, (it * 100).toInt()) }
        PixelSlider(stringResource(R.string.widget_intensity), hsv[2], 0.05f..1f, { v ->
            onColor(android.graphics.Color.HSVToColor(floatArrayOf(retainedHue, hsv[1], v)))
        }) { stringResource(R.string.widget_percent, (it * 100).toInt()) }
    }
}

/**
 * Eight saturated LED colours derived from the app's current Material You scheme — which, with
 * wallpaper colours on, is derived from the wallpaper itself.
 *
 * The scheme's key hues are taken and their saturation and value pushed up, because container tones
 * are pale by design and pale is nearly invisible on an LED.
 */
@Composable
fun wallpaperLedColours(): List<Int> {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme.primary, scheme.tertiary, scheme.secondary, scheme.surfaceTint) {
        val seeds = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.surfaceTint)
            .map { android.graphics.Color.valueOf(it.red, it.green, it.blue).toArgb() }
        val hues = seeds.map { c ->
            FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }[0]
        }
        (0 until LED_COUNT).map { i ->
            val t = i.toFloat() / LED_COUNT * hues.size
            val a = hues[t.toInt().coerceAtMost(hues.lastIndex)]
            val b = hues[(t.toInt() + 1) % hues.size]
            val diff = ((b - a + 540) % 360) - 180
            val hue = (a + diff * (t - t.toInt()) + 360) % 360
            Renderer.hsv(hue, 1f, 1f)
        }
    }
}

/**
 * Material Design Color Spectrum Wheel:
 * 360-degree continuous HSV color wheel with touch/drag thumb selector.
 */
@Composable
fun ColorSpectrumWheel(
    color: Int,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 220,
) {
    val hsv = remember(color) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
    }
    val currentV by rememberUpdatedState(hsv[2])
    val currentOnColorChanged by rememberUpdatedState(onColorChanged)
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val view = LocalView.current
    val density = LocalDensity.current
    val outerInsetPx = with(density) { 10.dp.toPx() }
    val sweepColors = remember {
        (0..360 step 30).map { Color(Renderer.hsv(it.toFloat(), 1f, 1f)) }
    }

    Canvas(
        modifier = modifier
            .size(sizeDp.dp)
            .pointerInput(outerInsetPx) {
                detectDragGestures(
                    onDragStart = { offset: Offset ->
                        PixelHaptics.click(view)
                        updateColorFromOffset(
                            offset = offset,
                            viewSize = size.width.toFloat(),
                            outerInsetPx = outerInsetPx,
                            value = currentV,
                            onColorChanged = currentOnColorChanged,
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        updateColorFromOffset(
                            offset = change.position,
                            viewSize = size.width.toFloat(),
                            outerInsetPx = outerInsetPx,
                            value = currentV,
                            onColorChanged = currentOnColorChanged,
                        )
                    }
                )
            }
            .pointerInput(outerInsetPx) {
                detectTapGestures { offset: Offset ->
                    PixelHaptics.click(view)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val outerRadius = (minOf(size.width, size.height) / 2f - outerInsetPx).coerceAtLeast(1f)
                    val innerRadius = outerRadius * 0.48f
                    val dx = offset.x - cx
                    val dy = offset.y - cy
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist <= innerRadius * 0.85f) {
                        currentOnColorChanged(0xFFFFFFFF.toInt())
                    } else {
                        updateColorFromOffset(
                            offset = offset,
                            viewSize = size.width.toFloat(),
                            outerInsetPx = outerInsetPx,
                            value = currentV,
                            onColorChanged = currentOnColorChanged,
                        )
                    }
                }
            }
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerRadius = (minOf(size.width, size.height) / 2f - outerInsetPx).coerceAtLeast(1f)
        val innerRadius = (outerRadius * 0.48f).coerceAtLeast(1f)
        val centerDiscRadius = (innerRadius - 6.dp.toPx()).coerceAtLeast(1f)

        // 1. Hue sweep gradient on the outer spectrum ring
        drawCircle(
            brush = Brush.sweepGradient(sweepColors, center = Offset(cx, cy)),
            radius = outerRadius,
            center = Offset(cx, cy),
        )

        // 2. Saturation radial gradient (white blend)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = outerRadius,
            ),
            radius = outerRadius,
            center = Offset(cx, cy),
        )

        // 3. Material 3 Inner Center Well
        drawCircle(
            color = surfaceColor,
            radius = innerRadius,
            center = Offset(cx, cy),
        )

        // 4. Live Color Center Disc
        drawCircle(
            color = Color(color),
            radius = centerDiscRadius,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = outlineColor,
            radius = centerDiscRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        // 5. Outer subtle ring outline
        drawCircle(
            Color.White.copy(alpha = 0.25f),
            radius = outerRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        // 6. Material 3 Draggable Selector Thumb
        val angleRad = (hsv[0] * Math.PI / 180f).toFloat()
        val dist = (innerRadius + (outerRadius - innerRadius) * hsv[1]).coerceIn(innerRadius, outerRadius)
        val thumbX = cx + kotlin.math.cos(angleRad) * dist
        val thumbY = cy + kotlin.math.sin(angleRad) * dist
        val thumbOffset = Offset(thumbX, thumbY)

        // Elevated thumb with shadow + crisp white border + center color
        drawCircle(
            Color.Black.copy(alpha = 0.35f),
            radius = 13.dp.toPx(),
            center = thumbOffset + Offset(0f, 1.5.dp.toPx()),
        )
        drawCircle(
            Color.White,
            radius = 11.dp.toPx(),
            center = thumbOffset,
        )
        drawCircle(
            Color(color),
            radius = 8.dp.toPx(),
            center = thumbOffset,
        )
    }
}

private fun updateColorFromOffset(
    offset: Offset,
    viewSize: Float,
    outerInsetPx: Float,
    value: Float,
    onColorChanged: (Int) -> Unit,
) {
    val cx = viewSize / 2f
    val cy = viewSize / 2f
    val outerRadius = (viewSize / 2f - outerInsetPx).coerceAtLeast(1f)
    val innerRadius = outerRadius * 0.48f
    val dx = offset.x - cx
    val dy = offset.y - cy

    var angleDeg = (kotlin.math.atan2(dy, dx) * 180f / Math.PI).toFloat()
    if (angleDeg < 0) angleDeg += 360f

    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
    val sat = ((dist - innerRadius) / (outerRadius - innerRadius).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val v = if (value <= 0.05f) 1f else value

    val argb = android.graphics.Color.HSVToColor(floatArrayOf(angleDeg, sat, v))
    onColorChanged(argb)
}

/**
 * Material Design Hex Color Pill container:
 * Shows "#RRGGBB" with a copy/paste affordance and direct input capability.
 */
@Composable
fun HexColorPill(
    color: Int,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hexString = "%06X".format(color and 0xFFFFFF)
    var isEditing by remember { mutableStateOf(false) }
    var textInput by remember(hexString) { mutableStateOf(hexString) }
    val view = LocalView.current

    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                CircleShape,
            )
            .border(
                1.5.dp,
                Color(color).copy(alpha = 0.8f),
                CircleShape,
            )
            .clickable(role = Role.Button) {
                PixelHaptics.click(view)
                isEditing = true
            }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(Color(color), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
        Text(
            "#$hexString",
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    if (isEditing) {
        AlertDialog(
            onDismissRequest = { isEditing = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.style_colour)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { input: String ->
                            val filtered = input.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(6)
                            textInput = filtered.uppercase()
                        },
                        prefix = { Text("#") },
                        singleLine = true,
                        label = { Text("HEX Color") },
                    )
                    OutlinedButton(
                        onClick = {
                            textInput = "FFFFFF"
                            onColorChanged(0xFFFFFFFF.toInt())
                            isEditing = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reset to Default", style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = textInput.length == 6,
                    onClick = {
                        val parsed = runCatching {
                            android.graphics.Color.parseColor("#$textInput")
                        }.getOrNull()
                        if (parsed != null) {
                            onColorChanged(parsed)
                        }
                        isEditing = false
                    }
                ) {
                    ButtonLabel(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditing = false }) {
                    ButtonLabel(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * Full Material Design Color Spectrum Modal Dialog:
 * Features the 360° Color Spectrum Wheel, Hex Color Pill container,
 * Intensity slider, and quick color presets.
 */
@Composable
fun ColorSpectrumDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
    defaultColor: Int = 0xFFFFFFFF.toInt(),
) {
    var currentColor by remember { mutableIntStateOf(initialColor) }
    val hsv = remember(currentColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(currentColor, it) }
    }
    var retainedHue by remember { androidx.compose.runtime.mutableFloatStateOf(hsv[0]) }
    if (hsv[1] > 0.001f) {
        retainedHue = hsv[0]
    }

    val flashlightPresets = remember {
        listOf(
            0xFFFFFFFF.toInt(), // Pure White
            0xFFFFF4E5.toInt(), // Warm White
            0xFFFFB300.toInt(), // Amber Flash
            0xFFFF1744.toInt(), // Red Beacon
            0xFF00E676.toInt(), // Emerald Green
            0xFF00E5FF.toInt(), // Neon Cyan
            0xFF2979FF.toInt(), // Blue Signal
            0xFFD500F9.toInt(), // Purple Light
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.widget_colour), style = MaterialTheme.typography.titleLarge)
                HexColorPill(color = currentColor, onColorChanged = { currentColor = it })
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ColorSpectrumWheel(
                    color = currentColor,
                    onColorChanged = { currentColor = it },
                    sizeDp = 210,
                )

                // Quick Color Swatches
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    flashlightPresets.forEach { preset ->
                        val selected = preset == currentColor
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(Color(preset), CircleShape)
                                .border(
                                    if (selected) 2.5.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                )
                                .clickable { currentColor = preset }
                        )
                    }
                }

                // Brightness / Value Slider
                PixelSlider(
                    label = stringResource(R.string.widget_intensity),
                    value = hsv[2],
                    range = 0.05f..1f,
                    onChange = { v: Float ->
                        currentColor = android.graphics.Color.HSVToColor(floatArrayOf(retainedHue, hsv[1], v))
                    },
                ) { stringResource(R.string.widget_percent, (it * 100).toInt()) }

                // Outlined Material 3 Reset to Default Button
                OutlinedButton(
                    onClick = { currentColor = defaultColor },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to Default", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onColorSelected(currentColor)
                    onDismiss()
                }
            ) {
                ButtonLabel(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                ButtonLabel(stringResource(R.string.common_cancel))
            }
        },
    )
}
