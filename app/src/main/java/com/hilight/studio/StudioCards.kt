package com.hilight.studio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Material 3 Expressive Studio Fill-Light & Planckian CCT Controller.
 * Stepless Kelvin tuning from 1900K (Candle) to 8000K (Blue Hour) with calibrated sRGB output.
 */
@Composable
fun StudioFillLightCard(
    store: Store,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentKelvin by store.cctKelvin.collectAsStateWithLifecycle()
    val isFlashActive by store.flashlightActive.collectAsStateWithLifecycle()
    val brightness by store.flashlightBrightness.collectAsStateWithLifecycle()

    val currentColor = CctUtils.kelvinToColor(currentKelvin)

    PixelCard(modifier = modifier, tone = if (isFlashActive) 3 else 2) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionTitle(stringResource(R.string.live_studio_title))
                Caption(stringResource(R.string.live_studio_caption))
            }
            // Swatch displaying Planckian blackbody chromaticity
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Stepless Kelvin Slider
        PixelSlider(
            label = stringResource(R.string.live_cct_kelvin_label),
            value = currentKelvin.toFloat(),
            range = 1900f..8000f,
            onChange = { k ->
                PixelHaptics.tick(context)
                store.setCctKelvin(k.toInt())
            },
            format = { "%.0fK".format(it) },
        )

        Spacer(Modifier.height(8.dp))

        // Quick Preset Chips (Tungsten, Halogen, Daylight, etc.)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val chips = listOf(
                Pair(1900, "1900K"),
                Pair(2700, "2700K"),
                Pair(3200, "3200K"),
                Pair(5600, "5600K"),
                Pair(6500, "6500K"),
            )
            chips.forEach { (kelvin, label) ->
                val selected = currentKelvin in (kelvin - 150)..(kelvin + 150)
                val chipBg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "chipBg",
                )
                val chipColor by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "chipColor",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(chipBg)
                        .clickable {
                            PixelHaptics.click(context)
                            store.setCctKelvin(kelvin)
                            if (!isFlashActive) store.setFlashlight(true)
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = chipColor,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Brightness Control for the Studio Light
        PixelSlider(
            label = stringResource(R.string.widget_intensity),
            value = brightness,
            range = 0.05f..1.0f,
            onChange = { b ->
                PixelHaptics.tick(context)
                store.setFlashlightBrightness(b)
            },
            format = { "%.0f%%".format(it * 100f) },
        )
    }
}

/**
 * Cinema Practical FX Card: Pre-programmed lighting scenarios for filmmakers and video creators.
 */
@Composable
fun CinemaPracticalFxCard(
    store: Store,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentMode by store.cinemaFxMode.collectAsStateWithLifecycle()

    val effects = listOf(
        Triple(CinemaFxEngine.Mode.CANDLE, Icons.Rounded.WbSunny, Color(0xFFFF9800)),
        Triple(CinemaFxEngine.Mode.LIGHTNING, Icons.Rounded.Bolt, Color(0xFF00E5FF)),
        Triple(CinemaFxEngine.Mode.PAPARAZZI, Icons.Rounded.CameraAlt, Color(0xFFE0E0E0)),
        Triple(CinemaFxEngine.Mode.POLICE, Icons.Rounded.NotificationsActive, Color(0xFFFF1744)),
        Triple(CinemaFxEngine.Mode.TALLY, Icons.Rounded.CameraAlt, Color(0xFFFF0033)),
        Triple(CinemaFxEngine.Mode.CATCHLIGHT, Icons.Rounded.AutoAwesome, Color(0xFF69F0AE)),
    )

    PixelCard(modifier = modifier, tone = if (currentMode != CinemaFxEngine.Mode.OFF) 3 else 2) {
        SectionTitle(stringResource(R.string.live_fx_title))
        Caption(stringResource(R.string.live_fx_caption))

        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            effects.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { (mode, icon, accent) ->
                        val active = currentMode == mode
                        val tileBg by animateColorAsState(
                            if (active) accent.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            label = "fxBg",
                        )
                        val textColor by animateColorAsState(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            label = "fxText",
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.medium)
                                .background(tileBg)
                                .clickable {
                                    PixelHaptics.click(context)
                                    store.setCinemaFxMode(
                                        if (active) CinemaFxEngine.Mode.OFF else mode
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (active) accent else accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = icon,
                                    contentDescription = mode.label,
                                    tint = if (active) Color.White else accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor,
                                )
                                Text(
                                    text = if (active) "Active" else "Tap to run",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Real-Time Music & Sound Visualizer Card with dynamic 8-band FFT equalizer Canvas.
 */
@Composable
fun AudioVisualizerCard(
    store: Store,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val active by store.audioVisualizerActive.collectAsStateWithLifecycle()
    val energies by store.audioEnergies.collectAsStateWithLifecycle()
    val sensitivity by store.audioSensitivity.collectAsStateWithLifecycle()
    val currentVisualizerMode by store.audioVisualizerMode.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            PixelHaptics.doubleClick(context)
            store.setAudioVisualizerActive(true)
        }
    }

    PixelCard(modifier = modifier, tone = if (active) 3 else 2) {
        SectionTitle(stringResource(R.string.live_audio_title))
        Caption(stringResource(R.string.live_audio_caption))

        Spacer(Modifier.height(12.dp))

        // Real-Time 8-LED Graphic Equalizer Display Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                val totalWidth = size.width
                val totalHeight = size.height
                val barCount = AudioDsp.BANDS_COUNT
                val spacing = 8.dp.toPx()
                val barWidth = (totalWidth - (spacing * (barCount - 1))) / barCount

                for (i in 0 until barCount) {
                    val energy = if (i < energies.size) energies[i] else 0.05f
                    val barHeight = (energy * totalHeight).coerceAtLeast(4.dp.toPx())
                    val x = i * (barWidth + spacing)
                    val y = totalHeight - barHeight

                    val bandHue = AudioDsp.ACOUSTIC_BANDS[i].defaultHue
                    val barColor = Color(AudioDsp.hsvToArgb(bandHue, 0.9f, 0.95f))

                    // Draw rounded bar
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )

                    // Draw glowing highlight disc on top of bar when active
                    if (energy > 0.35f) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f * energy),
                            radius = barWidth * 0.35f,
                            center = Offset(x + barWidth / 2f, y + barWidth / 2f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Toggle Switch & Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable {
                    PixelHaptics.click(context)
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        store.toggleAudioVisualizer()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Column {
                    Text(
                        text = if (active) stringResource(R.string.live_audio_toggle_on) else stringResource(R.string.live_audio_toggle_off),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Caption(
                        if (!hasPermission) stringResource(R.string.live_audio_permission_required)
                        else if (active) "Real-time FFT active" else "Ready to listen"
                    )
                }
            }
            Switch(
                checked = active,
                onCheckedChange = { checked ->
                    PixelHaptics.click(context)
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        store.setAudioVisualizerActive(checked)
                    }
                },
            )
        }

        // Mode selector and Sensitivity slider when active
        AnimatedVisibility(
            visible = active,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                // Sensitivity Slider
                PixelSlider(
                    label = stringResource(R.string.live_audio_sensitivity),
                    value = sensitivity,
                    range = 0.5f..2.5f,
                    onChange = { s ->
                        PixelHaptics.tick(context)
                        store.setAudioSensitivity(s)
                    },
                    format = { "%.1fx".format(it) },
                )
            }
        }
    }
}

/**
 * Visor Tap Gesture Control Card: Detects physical double-taps on the camera glass.
 */
@Composable
fun VisorTapCard(
    store: Store,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val enabled by store.visorTapEnabled.collectAsStateWithLifecycle()

    PixelCard(modifier = modifier, tone = if (enabled) 3 else 2) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        stringResource(R.string.live_tap_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Caption(
                        stringResource(
                            if (enabled) R.string.live_tap_subtitle_on
                            else R.string.live_tap_subtitle_off
                        )
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = { chk ->
                    PixelHaptics.click(context)
                    store.setVisorTapEnabled(chk)
                },
            )
        }
    }
}
