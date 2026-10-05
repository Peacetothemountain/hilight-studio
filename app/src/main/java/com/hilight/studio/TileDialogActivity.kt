package com.hilight.studio

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/**
 * Floating preference dialog for Quick Settings tile long-press.
 * Pixel 11 1-for-1 shell reproduction with Google Material 3 Expressive design,
 * authentic blurred background, centered luminous flashlight strength slider,
 * and a high-precision professional color & Planckian CCT temperature selector.
 */
class TileDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.68f)
        if (Build.VERSION.SDK_INT >= 31) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val params = window.attributes
            params.blurBehindRadius = 75
            window.attributes = params
        }

        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        val store = Store.get(this)

        setContent {
            val active by store.flashlightActive.collectAsStateWithLifecycle()
            val color by store.flashlightColor.collectAsStateWithLifecycle()
            val intensity by store.flashlightBrightness.collectAsStateWithLifecycle()
            val view = LocalView.current

            val currentPercentage = if (active) (intensity * 100).roundToInt() else 0
            var showColorPalette by remember { mutableStateOf(false) }
            var colorMode by remember { mutableStateOf(ColorPickerMode.SPECTRUM) } // SPECTRUM or CCT
            var selectedKelvin by remember { mutableIntStateOf(4000) }

            HiLightTheme {
                // Tapping outside the card dismisses
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { finishAndRemoveTask() }
                        )
                        .systemBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Google Pixel 11 Dialog Card Container Shell
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
                        ),
                        modifier = Modifier
                            .widthIn(max = 380.dp)
                            .wrapContentHeight()
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {} // swallow clicks inside card
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 22.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Title Header (Pixel 11 Authentic Typography)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Flashlight Strength",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Live Percentage Pill Badge
                                val badgeColor by animateColorAsState(
                                    targetValue = if (active && currentPercentage > 0) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    label = "badgeColor"
                                )
                                Text(
                                    text = if (active && currentPercentage > 0) "$currentPercentage%" else "Off",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = badgeColor
                                )
                            }

                            // 2. Centered Pixel Trapezoid Flashlight Slider
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TrapezoidFlashlightSlider(
                                    value = if (active) intensity else 0f,
                                    onValueChange = { v ->
                                        if (v > 0.01f) {
                                            store.setFlashlightBrightness(v)
                                            if (!active) store.setFlashlight(true)
                                        } else {
                                            store.setFlashlight(false)
                                        }
                                    },
                                    color = color
                                )
                            }

                            // 3. Quick Color Swatches & Expandable Color Wheel Strip
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Fast Preset Palette Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Color Wheel Expander Toggle Chip
                                    val rainbowBrush = Brush.sweepGradient(
                                        listOf(
                                            Color.Red, Color.Yellow, Color.Green,
                                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                        )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (showColorPalette) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                                            .border(
                                                width = if (showColorPalette) 2.5.dp else 1.5.dp,
                                                brush = rainbowBrush,
                                                shape = CircleShape
                                            )
                                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                                PixelHaptics.click(view)
                                                showColorPalette = !showColorPalette
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.ColorLens,
                                            contentDescription = "Color Wheel",
                                            tint = if (showColorPalette) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Wallpaper Dynamic Color Chip
                                    val dynamicThemeColor = MaterialTheme.colorScheme.primary
                                    val isDynamicSelected = color == android.graphics.Color.valueOf(
                                        dynamicThemeColor.red,
                                        dynamicThemeColor.green,
                                        dynamicThemeColor.blue
                                    ).toArgb()

                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(dynamicThemeColor)
                                            .border(
                                                width = if (isDynamicSelected) 2.5.dp else 1.dp,
                                                color = if (isDynamicSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                                PixelHaptics.click(view)
                                                val argb = android.graphics.Color.valueOf(
                                                    dynamicThemeColor.red,
                                                    dynamicThemeColor.green,
                                                    dynamicThemeColor.blue
                                                ).toArgb()
                                                store.setFlashlightColor(argb)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_flashlight),
                                            contentDescription = "Dynamic Theme",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Curated Lighting & Studio Swatches
                                    val presets = listOf(
                                        0xFFFFF3E0.toInt(), // 2700K Candlelight
                                        0xFFFFF8E7.toInt(), // 3500K Warm White
                                        0xFFFFFFFF.toInt(), // Pure White
                                        0xFFE1F5FE.toInt(), // 6500K Daylight
                                        0xFFFF5252.toInt(), // Coral Red
                                        0xFFFFAB00.toInt(), // Amber
                                        0xFF00E676.toInt(), // Emerald
                                        0xFF00E5FF.toInt(), // Neon Cyan
                                        0xFF7C4DFF.toInt(), // Purple
                                    )

                                    presets.forEach { preset ->
                                        val selected = preset == color
                                        val scale by androidx.compose.animation.core.animateFloatAsState(
                                            targetValue = if (selected) 1.15f else 1.0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            label = "swatchScale"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .scale(scale)
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(preset))
                                                .border(
                                                    width = if (selected) 2.5.dp else 1.dp,
                                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                    shape = CircleShape
                                                )
                                                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                                    PixelHaptics.click(view)
                                                    store.setFlashlightColor(preset)
                                                }
                                        )
                                    }
                                }

                                // 4. Expandable High-Precision Pro Color Wheel & CCT Temperature Section
                                AnimatedVisibility(
                                    visible = showColorPalette,
                                    enter = fadeIn() + expandVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)),
                                    exit = fadeOut() + shrinkVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Mode Switcher: RGB Spectrum vs Kelvin CCT
                                        Row(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                                .padding(3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Spectrum Mode Pill
                                            val isSpectrum = colorMode == ColorPickerMode.SPECTRUM
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(if (isSpectrum) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable {
                                                        PixelHaptics.click(view)
                                                        colorMode = ColorPickerMode.SPECTRUM
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Spectrum",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = if (isSpectrum) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // Kelvin CCT Mode Pill
                                            val isCct = colorMode == ColorPickerMode.KELVIN_CCT
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(if (isCct) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable {
                                                        PixelHaptics.click(view)
                                                        colorMode = ColorPickerMode.KELVIN_CCT
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Temperature (K)",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = if (isCct) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (colorMode == ColorPickerMode.SPECTRUM) {
                                            // High precision 360° Color Spectrum Wheel
                                            ColorSpectrumWheel(
                                                color = color,
                                                onColorChanged = { store.setFlashlightColor(it) },
                                                sizeDp = 190
                                            )

                                            // Live Hex Pill Display
                                            HexColorPill(
                                                color = color,
                                                onColorChanged = { store.setFlashlightColor(it) }
                                            )
                                        } else {
                                            // Planckian CCT Temperature Selector
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = getKelvinDescription(selectedKelvin),
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = Color(color)
                                                )

                                                // Realistic Blackbody Radiation Gradient Bar
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(28.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            Brush.horizontalGradient(
                                                                listOf(
                                                                    Color(kelvinToRgb(2000)),
                                                                    Color(kelvinToRgb(3000)),
                                                                    Color(kelvinToRgb(4000)),
                                                                    Color(kelvinToRgb(5500)),
                                                                    Color(kelvinToRgb(7000))
                                                                )
                                                            )
                                                        )
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                                )

                                                // Temperature Slider (2000K to 7000K)
                                                Slider(
                                                    value = selectedKelvin.toFloat(),
                                                    onValueChange = { k ->
                                                        selectedKelvin = k.roundToInt()
                                                        val c = kelvinToRgb(selectedKelvin)
                                                        store.setFlashlightColor(c)
                                                    },
                                                    valueRange = 2000f..7000f,
                                                    steps = 25,
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Color(color),
                                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Bottom Action Buttons (1-for-1 Pixel 11 Reproduction)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // "Turn off" Outlined Pill Button
                                OutlinedButton(
                                    onClick = {
                                        PixelHaptics.toggle(view, false)
                                        store.setFlashlight(false)
                                        finishAndRemoveTask()
                                    },
                                    shape = CircleShape,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.50f)
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "Turn off",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }

                                // "Done" Filled Primary Pill Button
                                Button(
                                    onClick = {
                                        PixelHaptics.confirm(view)
                                        finishAndRemoveTask()
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 26.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "Done",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private enum class ColorPickerMode {
        SPECTRUM,
        KELVIN_CCT
    }

    private fun getKelvinDescription(kelvin: Int): String {
        val label = when (kelvin) {
            in 2000..2500 -> "Candlelight"
            in 2501..3200 -> "Warm Incandescent"
            in 3201..4200 -> "Neutral White"
            in 4201..5500 -> "Crisp Daylight"
            else -> "Cool Sky"
        }
        return "$kelvin K · $label"
    }

    companion object {
        /**
         * Mathematical approximation of Planckian blackbody locus (Kelvin -> sRGB)
         * according to Tanner Helland's algorithm.
         */
        fun kelvinToRgb(kelvin: Int): Int {
            val temp = kelvin / 100.0
            val red: Double
            val green: Double
            val blue: Double

            // Red calculation
            red = if (temp <= 66) 255.0 else {
                329.698727446 * Math.pow(temp - 60.0, -0.1332047592)
            }

            // Green calculation
            green = if (temp <= 66) {
                99.4708025861 * Math.log(temp) - 161.1195681661
            } else {
                288.1221695283 * Math.pow(temp - 60.0, -0.0755148492)
            }

            // Blue calculation
            blue = if (temp >= 66) 255.0 else if (temp <= 19) 0.0 else {
                138.5177312231 * Math.log(temp - 10.0) - 305.0447927307
            }

            val r = red.coerceIn(0.0, 255.0).toInt()
            val g = green.coerceIn(0.0, 255.0).toInt()
            val b = blue.coerceIn(0.0, 255.0).toInt()
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
