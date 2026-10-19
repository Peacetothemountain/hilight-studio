package com.hilight.studio

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

/**
 * Swatches plus hue / saturation / intensity, enhanced with Material 3 Expressive
 * Color Spectrum Wheel launcher, Planckian Kelvin CCT quick presets, and live Hex pill.
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
    val view = LocalView.current
    var showWheelDialog by remember { mutableStateOf(false) }
    var showCctRow by remember { mutableStateOf(false) }
    var selectedKelvin by remember { mutableIntStateOf(4000) }
    val hueBrush = remember {
        Brush.horizontalGradient((0..6).map { Color(Renderer.hsv(it * 60f)) })
    }
    val cctBrush = remember {
        Brush.horizontalGradient(
            listOf(
                Color(TileDialogActivity.kelvinToRgb(2000)),
                Color(TileDialogActivity.kelvinToRgb(3000)),
                Color(TileDialogActivity.kelvinToRgb(4000)),
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. Color Wheel Dialog Chip
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            CircleShape,
                        )
                        .clickable(role = Role.Button) {
                            PixelHaptics.click(view)
                            showWheelDialog = true
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Rounded.ColorLens,
                        contentDescription = null,
                        tint = Color(color),
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        "Wheel",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // 2. Kelvin CCT Temperature Toggle Chip
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (showCctRow) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            CircleShape,
                        )
                        .clickable(role = Role.Button) {
                            PixelHaptics.click(view)
                            showCctRow = !showCctRow
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Rounded.WbSunny,
                        contentDescription = null,
                        tint = if (showCctRow) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFFFFD54F),
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        "CCT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (showCctRow) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }

                // 3. Compact Hex / Swatch Badge
                HexColorPill(
                    color = color,
                    onColorChanged = onColor,
                )
            }
        }

        // Expandable Kelvin CCT Quick Presets & Blackbody Strip
        AnimatedVisibility(
            visible = showCctRow,
            enter = fadeIn() + expandVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.shapes.medium,
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(2700 to "2700K", 4000 to "4000K", 5500 to "5500K", 6500 to "6500K").forEach { (k, kLabel) ->
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
                                .clickable(role = Role.Button) {
                                    PixelHaptics.click(view)
                                    selectedKelvin = k
                                    onColor(kColor)
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = kLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(kColor),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .padding(horizontal = 6.dp)
                            .background(cctBrush, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CircleShape)
                    )
                    Slider(
                        value = selectedKelvin.toFloat(),
                        valueRange = 2000f..7000f,
                        onValueChange = { k ->
                            selectedKelvin = k.roundToInt()
                            onColor(TileDialogActivity.kelvinToRgb(selectedKelvin))
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(TileDialogActivity.kelvinToRgb(selectedKelvin)),
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                        ),
                    )
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
                value = hsv[0],
                valueRange = 0f..359f,
                onValueChange = { h ->
                    onColor(android.graphics.Color.HSVToColor(floatArrayOf(h, hsv[1], hsv[2])))
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
            onColor(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], s, hsv[2])))
        }) { stringResource(R.string.widget_percent, (it * 100).toInt()) }
        PixelSlider(stringResource(R.string.widget_intensity), hsv[2], 0.05f..1f, { v ->
            onColor(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], v)))
        }) { stringResource(R.string.widget_percent, (it * 100).toInt()) }
    }

    if (showWheelDialog) {
        ColorSpectrumDialog(
            initialColor = color,
            onDismiss = { showWheelDialog = false },
            onColorSelected = onColor,
        )
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
                    updateColorFromOffset(
                        offset = offset,
                        viewSize = size.width.toFloat(),
                        outerInsetPx = outerInsetPx,
                        value = currentV,
                        onColorChanged = currentOnColorChanged,
                    )
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerRadius = size.width / 2f - outerInsetPx
        val innerRadius = outerRadius * 0.48f

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
            radius = innerRadius - 6.dp.toPx(),
            center = Offset(cx, cy),
        )
        drawCircle(
            color = outlineColor,
            radius = innerRadius - 6.dp.toPx(),
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
                        currentColor = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], v))
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
