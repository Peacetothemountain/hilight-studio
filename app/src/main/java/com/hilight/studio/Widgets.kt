package com.hilight.studio

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput

val PRESET_COLORS = listOf(
    0xFFFF1744, 0xFFFF6D00, 0xFFFFD600, 0xFF00E676, 0xFF00E5FF,
    0xFF2979FF, 0xFF7C4DFF, 0xFFFF4081, 0xFFFFFFFF, 0xFFFF80AB,
).map { it.toInt() }

/**
 * Swatches plus hue / saturation / intensity.
 *
 * The selected swatch grows on a spring and keeps a ring, the way Pixel's wallpaper and theme
 * pickers behave, and the hue track is the gradient itself rather than a tinted bar.
 */
@Composable
fun ColorPicker(
    color: Int,
    onColor: (Int) -> Unit,
    // Callers editing a named colour pass their own heading, so this stays a String. The default is
    // read from resources, which a composable default expression is allowed to do.
    label: String = stringResource(R.string.widget_colour),
) {
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
    val haptics = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Box(
                Modifier
                    .size(30.dp)
                    .background(Color(color), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PRESET_COLORS.forEach { c ->
                val selected = c == color
                val scale by animateFloatAsState(
                    if (selected) 1.16f else 1f,
                    spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
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
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    .background(
                        Brush.horizontalGradient((0..6).map { Color(Renderer.hsv(it * 60f)) }),
                        CircleShape,
                    )
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
    val seeds = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.surfaceTint)
        .map { android.graphics.Color.valueOf(it.red, it.green, it.blue).toArgb() }
    val hues = seeds.map { c ->
        FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }[0]
    }
    return (0 until LED_COUNT).map { i ->
        val t = i.toFloat() / LED_COUNT * hues.size
        val a = hues[t.toInt().coerceAtMost(hues.lastIndex)]
        val b = hues[(t.toInt() + 1) % hues.size]
        val diff = ((b - a + 540) % 360) - 180
        val hue = (a + diff * (t - t.toInt()) + 360) % 360
        Renderer.hsv(hue, 1f, 1f)
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
    val haptics = LocalHapticFeedback.current
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .size(sizeDp.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset: Offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        updateColorFromOffset(offset, size.width.toFloat(), hsv[2], onColorChanged)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        updateColorFromOffset(change.position, size.width.toFloat(), hsv[2], onColorChanged)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset: Offset ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    updateColorFromOffset(offset, size.width.toFloat(), hsv[2], onColorChanged)
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerRadius = size.width / 2f - 10.dp.toPx()
        val innerRadius = outerRadius * 0.48f

        // 1. Hue sweep gradient on the outer spectrum ring
        val sweepColors = (0..360 step 30).map { Color(Renderer.hsv(it.toFloat(), 1f, 1f)) }
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
    value: Float,
    onColorChanged: (Int) -> Unit,
) {
    val cx = viewSize / 2f
    val cy = viewSize / 2f
    val radius = viewSize / 2f - 20f
    val dx = offset.x - cx
    val dy = offset.y - cy

    var angleDeg = (kotlin.math.atan2(dy, dx) * 180f / Math.PI).toFloat()
    if (angleDeg < 0) angleDeg += 360f

    val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(radius)
    val sat = (dist / radius).coerceIn(0f, 1f)
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
    val haptics = LocalHapticFeedback.current

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
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                isEditing = true
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .background(Color(color), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
        Text(
            "#$hexString",
            style = MaterialTheme.typography.titleMedium.copy(
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

    val flashlightPresets = listOf(
        0xFFFFFFFF.toInt(), // Pure White
        0xFFFFF4E5.toInt(), // Warm White
        0xFFFFB300.toInt(), // Amber Flash
        0xFFFF1744.toInt(), // Red Beacon
        0xFF00E676.toInt(), // Emerald Green
        0xFF00E5FF.toInt(), // Neon Cyan
        0xFF2979FF.toInt(), // Blue Signal
        0xFFD500F9.toInt(), // Purple Light
    )

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




@Composable
fun VerticalFlashlightSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Int,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    
    val accent = Color(color)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    
    val animatedValue by androidx.compose.animation.core.animateFloatAsState(
        targetValue = value,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.7f, 
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "sliderValue"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(130.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(trackColor)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                ) { change, dragAmount ->
                    change.consume()
                    val height = size.height.toFloat()
                    val newValue = (value - dragAmount / height).coerceIn(0f, 1f)
                    if (newValue != value) {
                        onValueChange(newValue)
                    }
                }
            }
            .clickable {
                onValueChange(if (value > 0f) 0f else 1f)
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Base fill layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(animatedValue)
                .background(accent.copy(alpha = 0.6f))
        )
        
        // Volumetric Light Beam Canvas
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val fillTop = h * (1f - animatedValue)
            
            // Flashlight icon is positioned at the bottom, about 48dp size + 32dp padding
            // Center of the light emission from the icon:
            val emitterX = w / 2f
            val emitterY = h - 64.dp.toPx() 
            
            if (animatedValue > 0.02f) {
                // Draw expanding light beam (Trapezoid)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(emitterX - 12.dp.toPx(), emitterY)
                    lineTo(emitterX + 12.dp.toPx(), emitterY)
                    // Expand outwards as it goes up
                    val spread = 24.dp.toPx() + (w * 0.45f * animatedValue)
                    lineTo(emitterX + spread, fillTop)
                    lineTo(emitterX - spread, fillTop)
                    close()
                }
                
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.75f * animatedValue),
                            Color.White.copy(alpha = 0.1f * animatedValue),
                            Color.Transparent
                        ),
                        startY = fillTop,
                        endY = emitterY
                    )
                )
                
                // Hotspot glow at the top of the fill
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.8f * animatedValue), Color.Transparent),
                        center = Offset(emitterX, fillTop),
                        radius = w * 0.8f * animatedValue
                    ),
                    center = Offset(emitterX, fillTop),
                    radius = w * 0.8f * animatedValue
                )
            }
        }
        
        // Icon at bottom
        Box(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .size(64.dp)
                .background(if (value > 0f) Color.White.copy(alpha = 0.2f) else Color.Transparent, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_hilight_foreground),
                contentDescription = null,
                tint = if (value > 0f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
