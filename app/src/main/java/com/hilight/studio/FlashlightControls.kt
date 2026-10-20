package com.hilight.studio

import androidx.annotation.StringRes
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

internal const val FLASHLIGHT_MIN_KELVIN = 2000
internal const val FLASHLIGHT_MAX_KELVIN = 7000
internal const val FLASHLIGHT_DEFAULT_KELVIN = 4000

/**
 * Planckian blackbody locus (Kelvin to opaque sRGB), after Tanner Helland's approximation.
 * Input is clamped to the 1000 K..40000 K range the fit is valid for.
 */
fun kelvinToRgb(kelvin: Int): Int {
    val temp = kelvin.coerceIn(1000, 40000) / 100.0
    val red = if (temp <= 66) 255.0 else 329.698727446 * (temp - 60.0).pow(-0.1332047592)
    val green = if (temp <= 66) {
        99.4708025861 * ln(temp) - 161.1195681661
    } else {
        288.1221695283 * (temp - 60.0).pow(-0.0755148492)
    }
    val blue = when {
        temp >= 66 -> 255.0
        temp <= 19 -> 0.0
        else -> 138.5177312231 * ln(temp - 10.0) - 305.0447927307
    }
    val r = red.coerceIn(0.0, 255.0).toInt()
    val g = green.coerceIn(0.0, 255.0).toInt()
    val b = blue.coerceIn(0.0, 255.0).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

@StringRes
private fun kelvinNameRes(kelvin: Int): Int = when (kelvin) {
    in 0..2500 -> R.string.flashlight_kelvin_candle
    in 2501..3200 -> R.string.flashlight_kelvin_warm
    in 3201..4200 -> R.string.flashlight_kelvin_neutral
    in 4201..5500 -> R.string.flashlight_kelvin_daylight
    else -> R.string.flashlight_kelvin_cool
}

private val KELVIN_PRESETS = listOf(2700, 4000, 5500, 6500)

private val GOLD_RIM = Color(0xFFFFE082)

/**
 * The flashlight's colour controls: the colour-wheel and white/temperature buttons with their
 * spinning luminous rings, and the expandable Spectrum / Temperature (K) drawer.
 *
 * Shared by the Quick Settings dialog and the Live tab so both stay identical. The ring rotation and
 * shimmer pulse are read only while drawing, so the running animations redraw the two 38 dp buttons
 * each frame instead of recomposing the surrounding screen.
 *
 * [turnOnWhenPicking] lights the flashlight when a colour is picked while it is off, which the Live
 * tab wants; the dialog, which opens from the tile, leaves the on/off state to its slider.
 */
@Composable
fun FlashlightColorControls(
    store: Store,
    modifier: Modifier = Modifier,
    wheelSize: Dp = 190.dp,
    turnOnWhenPicking: Boolean = false,
) {
    val color by store.flashlightColor.collectAsStateWithLifecycle()
    val active by store.flashlightActive.collectAsStateWithLifecycle()
    val kelvin by store.flashlightKelvin.collectAsStateWithLifecycle()
    val whiteColor by store.flashlightWhite.collectAsStateWithLifecycle()

    LuminousColorControls(
        color = color,
        whiteColor = whiteColor,
        kelvin = kelvin,
        onColor = { c ->
            store.setFlashlightColor(c)
            if (turnOnWhenPicking && !active) store.setFlashlight(true)
        },
        onKelvin = { k ->
            store.setFlashlightKelvin(k)
            if (turnOnWhenPicking && !active) store.setFlashlight(true)
        },
        modifier = modifier,
        wheelSize = wheelSize,
    )
}

/**
 * The colour-wheel and white/temperature buttons plus their expandable drawer, without any notion
 * of where the colour is stored. [FlashlightColorControls] binds it to the flashlight and
 * [ColorPicker] to whatever colour a screen is editing.
 *
 * [whiteColor] is what the white button applies: pure white, or the last temperature picked.
 * Picking a temperature reports through [onKelvin]; the caller is expected to make that tone both
 * the new [whiteColor] and the current [color].
 */
@Composable
fun LuminousColorControls(
    color: Int,
    whiteColor: Int,
    kelvin: Int,
    onColor: (Int) -> Unit,
    onKelvin: (Int) -> Unit,
    modifier: Modifier = Modifier,
    wheelSize: Dp = 190.dp,
    showHexInDrawer: Boolean = true,
) {
    val view = LocalView.current
    val pick by rememberUpdatedState(onColor)
    val pickKelvin by rememberUpdatedState(onKelvin)

    var showPalette by rememberSaveable { mutableStateOf(false) }
    var temperatureMode by rememberSaveable { mutableStateOf(false) }

    val transition = rememberInfiniteTransition(label = "luminousSpinner")
    // Kept as State and read inside draw lambdas only; see the KDoc above.
    val spinAngle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinAngle",
    )
    val shimmerPulse = transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerPulse",
    )

    val isWhiteActive = color == whiteColor && !showPalette
    val isWheelActive = color != whiteColor || showPalette
    val selectedColor = Color(color)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Colour-wheel expander: a spinning ring in the selected colour while it owns the colour.
            val wheelScale by animateFloatAsState(
                targetValue = if (isWheelActive) 1.08f else 1.0f,
                animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                label = "wheelScale",
            )
            val wheelLabel = stringResource(R.string.flashlight_color_wheel)
            Box(
                modifier = Modifier
                    .scale(wheelScale)
                    .size(38.dp)
                    .then(
                        if (isWheelActive) {
                            Modifier.drawBehind {
                                drawLuminousRing(
                                    aura = listOf(selectedColor, selectedColor),
                                    sweep = sweepStops(selectedColor, selectedColor, selectedColor, selectedColor, selectedColor),
                                    spin = spinAngle,
                                    pulse = shimmerPulse,
                                )
                            }
                        } else Modifier
                    )
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .then(
                        if (!isWheelActive) {
                            Modifier.border(1.5.dp, RAINBOW_BRUSH, CircleShape)
                        } else {
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                CircleShape,
                            )
                        }
                    )
                    .semantics { selected = showPalette }
                    .clickable(role = Role.Button, onClickLabel = wheelLabel) {
                        PixelHaptics.click(view)
                        showPalette = !showPalette
                    },
                contentAlignment = Alignment.Center,
            ) {
                // The palette glyph is a stencil: the selected colour shows through it.
                Icon(
                    Icons.Rounded.ColorLens,
                    contentDescription = wheelLabel,
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithCache {
                            val center = Offset(size.width * 0.45f, size.height * 0.40f)
                            val radius = size.minDimension * 0.85f
                            val fill = if (isWheelActive) {
                                Brush.radialGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.98f),
                                        selectedColor.copy(alpha = 0.95f),
                                        selectedColor,
                                        selectedColor.copy(alpha = 0.65f),
                                    ),
                                    center = center,
                                    radius = radius,
                                )
                            } else {
                                Brush.radialGradient(
                                    listOf(Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF616161)),
                                    center = center,
                                    radius = radius,
                                )
                            }
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush = fill, blendMode = BlendMode.SrcIn)
                            }
                        },
                )
            }

            // White / temperature button: a gold shimmering ring while white owns the colour.
            val whiteScale by animateFloatAsState(
                targetValue = if (isWhiteActive) 1.08f else 1.0f,
                animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                label = "whiteScale",
            )
            val restingRim = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            Box(
                modifier = Modifier
                    .scale(whiteScale)
                    .size(38.dp)
                    .then(
                        if (isWhiteActive) {
                            Modifier.drawBehind {
                                drawLuminousRing(
                                    aura = listOf(Color(0xFFFFD54F), Color(0xFFFFB300)),
                                    auraAlpha = 0.38f,
                                    sweep = sweepStops(
                                        Color(0xFFFFF9E6), Color(0xFFFFD54F), Color(0xFFFFB300),
                                        Color(0xFFFFE082), Color(0xFFFFF9E6),
                                    ),
                                    spin = spinAngle,
                                    pulse = shimmerPulse,
                                )
                            }
                        } else Modifier
                    )
                    .clip(CircleShape)
                    .background(Color(whiteColor))
                    .drawWithContent {
                        drawContent()
                        // Drawn rather than Modifier.border so the shimmer is a draw-phase read.
                        val width = if (isWhiteActive) 2.dp.toPx() else 1.dp.toPx()
                        drawCircle(
                            color = if (isWhiteActive) {
                                GOLD_RIM.copy(alpha = 0.85f * shimmerPulse.value)
                            } else {
                                restingRim
                            },
                            radius = size.minDimension / 2f - width / 2f,
                            style = Stroke(width),
                        )
                    }
                    .semantics { selected = isWhiteActive }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.flashlight_white),
                    ) {
                        PixelHaptics.click(view)
                        pick(whiteColor)
                        showPalette = false
                    },
            )
        }

        AnimatedVisibility(
            visible = showPalette,
            enter = fadeIn() + expandVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ModePill(R.string.flashlight_mode_spectrum, selected = !temperatureMode) {
                        PixelHaptics.click(view)
                        temperatureMode = false
                    }
                    ModePill(R.string.flashlight_mode_temperature, selected = temperatureMode) {
                        PixelHaptics.click(view)
                        temperatureMode = true
                        pickKelvin(kelvin)
                    }
                }

                if (!temperatureMode) {
                    ColorSpectrumWheel(
                        color = color,
                        onColorChanged = { pick(it) },
                        sizeDp = wheelSize.value.roundToInt(),
                    )
                    if (showHexInDrawer) HexColorPill(color = color, onColorChanged = { pick(it) })
                } else {
                    TemperaturePicker(
                        kelvin = kelvin,
                        onKelvin = { pickKelvin(it) },
                        onPreset = { k ->
                            PixelHaptics.click(view)
                            pickKelvin(k)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModePill(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TemperaturePicker(kelvin: Int, onKelvin: (Int) -> Unit, onPreset: (Int) -> Unit) {
    val kelvinColor = Color(kelvinToRgb(kelvin))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.flashlight_kelvin, kelvin, stringResource(kelvinNameRes(kelvin))),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = kelvinColor,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(CircleShape)
                .background(BLACKBODY_BRUSH)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape),
        )
        Slider(
            value = kelvin.toFloat(),
            onValueChange = { onKelvin(it.roundToInt()) },
            valueRange = FLASHLIGHT_MIN_KELVIN.toFloat()..FLASHLIGHT_MAX_KELVIN.toFloat(),
            steps = 25,
            colors = SliderDefaults.colors(
                thumbColor = kelvinColor,
                activeTrackColor = kelvinColor.copy(alpha = 0.85f),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            KELVIN_PRESETS.forEach { k ->
                val current = kelvin == k
                Text(
                    text = stringResource(R.string.flashlight_kelvin_preset, k),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (current) Color(kelvinToRgb(k)) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .semantics { selected = current }
                        .clickable(role = Role.Button) { onPreset(k) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private val RAINBOW_BRUSH = Brush.sweepGradient(
    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
)

private val BLACKBODY_BRUSH = Brush.horizontalGradient(
    listOf(2000, 3000, 4000, 5500, 7000).map { Color(kelvinToRgb(it)) },
)

/** Colour stops of a luminous ring: bright head, fading tail, a gap, then a softer glint. */
private class SweepStops(val head: Color, val near: Color, val tail: Color, val glint: Color, val end: Color)

private fun sweepStops(head: Color, near: Color, tail: Color, glint: Color, end: Color) =
    SweepStops(head, near, tail, glint, end)

/** Soft radial aura plus a rotating sweep-gradient ring, both scaled by the shimmer pulse. */
private fun DrawScope.drawLuminousRing(
    aura: List<Color>,
    auraAlpha: Float = 0.40f,
    sweep: SweepStops,
    spin: State<Float>,
    pulse: State<Float>,
) {
    val p = pulse.value
    val ringRadius = size.minDimension / 2f + 4.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                aura[0].copy(alpha = auraAlpha * p),
                aura[1].copy(alpha = 0.12f * p),
                Color.Transparent,
            ),
            center = center,
            radius = ringRadius + 9.dp.toPx(),
        ),
    )
    rotate(spin.value) {
        drawCircle(
            brush = Brush.sweepGradient(
                0.0f to sweep.head.copy(alpha = 0.98f * p),
                0.18f to sweep.near.copy(alpha = 0.85f * p),
                0.40f to sweep.tail.copy(alpha = 0.35f * p),
                0.65f to Color.Transparent,
                0.82f to sweep.glint.copy(alpha = 0.50f * p),
                1.0f to sweep.end.copy(alpha = 0.98f * p),
                center = center,
            ),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
