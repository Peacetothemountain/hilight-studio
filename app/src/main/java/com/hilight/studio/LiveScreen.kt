package com.hilight.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Explains what the safety limits are doing, so a dark array never looks like a fault.
 *
 * The countdown ticks locally between status polls, which arrive about every 1.5 s.
 */
@Composable
private fun SafetyState(status: HelperStatus) {
    var elapsedMs by remember(status.ambientRemainingMs, status.ambientHeld, status.resting) {
        mutableLongStateOf(0L)
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(status.ambientRemainingMs, status.ambientHeld, status.resting, owner) {
        if (!status.resting && !status.ambientHeld && status.ambientRemainingMs > 0L) {
            owner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (elapsedMs < status.ambientRemainingMs) {
                    delay(500)
                    elapsedMs += 500
                }
            }
        }
    }
    val remaining = (status.ambientRemainingMs - elapsedMs).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when {
            status.resting ->
                Caption(stringResource(R.string.live_safety_resting))
            status.ambientHeld || remaining == 0L ->
                Caption(stringResource(R.string.live_safety_timed_out))
            else ->
                Caption(
                    stringResource(R.string.live_safety_countdown, remaining / 1000, status.dutyPct)
                )
        }
        Caption(
            stringResource(
                R.string.live_renderer_pid,
                status.pid,
                stringResource(
                    if (status.sessionOpen) R.string.live_session_open
                    else R.string.live_session_closed
                ),
            )
        )
    }
}

/**
 * White-light effects have no colour of their own, so give their tiles a readable accent.
 *
 * Keyed on the pattern rather than on the tile's label, which is translated now: comparing a label
 * against the English word "Rainbow" would have picked the wrong accent in every other language.
 */
@Composable
private fun tileAccent(pattern: Pattern, color: Int): Color = when {
    color != 0xFFFFFFFF.toInt() -> Color(color)
    pattern == Pattern.RAINBOW -> Color(0xFF7C4DFF)
    else -> Color(0xFFFFB300)
}

/** Home surface: the phone itself, the master switch, and one-tap effects. */
@Composable
fun LiveScreen(store: Store) {
    val launchPreview = rememberPreviewLauncher(store)
    val enabled by store.enabled.collectAsStateWithLifecycle()
    val ambient by store.ambient.collectAsStateWithLifecycle()
    val status by store.status.collectAsStateWithLifecycle()
    val rules by store.rules.collectAsStateWithLifecycle()
    val suppression by store.suppression.collectAsStateWithLifecycle()
    val previewLook by store.previewLook.collectAsStateWithLifecycle()

    val profile = rememberDeviceProfile()
    // A real model name is not translated; only the fallback profile has a resource to read.
    val modelName = profile.labelRes?.let { stringResource(it) } ?: profile.label

    PixelCard(tone = 0) {
        // while a test is running the hero shows the test, not the ambient look
        val shown = previewLook ?: ambient
        val showing = enabled || previewLook != null
        DeviceHero(
            pattern = if (showing) shown.pattern else Pattern.OFF,
            cfg = shown,
            active = showing && status.alive,
            profile = profile,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    !profile.hasHiLight -> stringResource(R.string.live_status_unavailable)
                    previewLook != null -> stringResource(
                        R.string.live_status_testing,
                        stringResource(shown.pattern.labelRes),
                    )
                    enabled -> stringResource(
                        R.string.live_status_on,
                        stringResource(ambient.pattern.labelRes),
                    )
                    else -> stringResource(R.string.live_status_system)
                },
                style = MaterialTheme.typography.titleMedium,
                // Weighted so the status line is the thing that wraps. Unweighted, both children
                // competed for the row and Japanese — where this line runs half again as long as the
                // English — squeezed the model name into a two-line sliver against the edge.
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Caption(modelName)
        }
        Caption(
            when {
                !profile.hasHiLight ->
                    stringResource(R.string.live_hint_no_array, modelName)
                !status.alive -> stringResource(R.string.live_hint_no_renderer)
                enabled -> stringResource(R.string.live_hint_look)
                else -> stringResource(R.string.live_hint_take_over)
            }
        )
    }

    PixelCard(tone = 2) {
        PixelToggleRow(
            title = stringResource(
                if (enabled) R.string.live_toggle_driving else R.string.live_toggle_system
            ),
            subtitle = null,
            checked = enabled,
            onChange = { store.setEnabled(it) },
        )
        suppression?.let {
            // The full explanation, not the tile's two-word form in Suppression.shortRes.
            Caption(
                stringResource(
                    when (it) {
                        Suppression.QUIET_HOURS -> R.string.live_suppressed_quiet_hours
                        Suppression.LOW_BATTERY -> R.string.live_suppressed_low_battery
                        Suppression.POWER_SAVER -> R.string.live_suppressed_power_saver
                        Suppression.SCREEN_ON -> R.string.live_suppressed_screen_on
                        Suppression.NOT_FACE_DOWN -> R.string.live_suppressed_not_face_down
                    }
                )
            )
        }
        AnimatedVisibility(
            visible = status.alive && enabled && suppression == null,
            enter = fadeIn(tween(200)) + expandVertically(tween(240)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(200)),
        ) {
            SafetyState(status)
        }
    }

    // Android 17 8-LED Flashlight with animated beam & Pro Studio Color / CCT controls
    Android17FlashlightCard(store = store)

    PixelCard {
        SectionTitle(stringResource(R.string.live_tests_title))
        Caption(stringResource(R.string.live_tests_caption))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QUICK_TESTS.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { (labelRes, icon, spec) ->
                        PixelTile(
                            label = stringResource(labelRes),
                            icon = icon,
                            accent = tileAccent(spec.first, spec.second),
                            enabled = enabled && status.alive,
                            modifier = Modifier.weight(1f),
                        ) { launchPreview(spec.first, spec.second, 1200, 1f, 4_000) }
                    }
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    PixelCard {
        SectionTitle(
            stringResource(R.string.live_rules_title),
            trailing = {
                Caption(stringResource(R.string.live_rules_on_count, rules.count { it.enabled }))
            },
        )
        if (rules.isEmpty()) {
            Caption(stringResource(R.string.live_rules_empty))
        } else {
            rules.forEach { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(ruleLabel(r), style = MaterialTheme.typography.bodyLarge)
                    // One format string rather than three pieces joined with a dot, so a translator
                    // can put the trigger first if that is the natural order.
                    Text(
                        stringResource(
                            R.string.live_rule_summary,
                            if (r.randomColor) stringResource(R.string.live_rule_random)
                            else stringResource(r.pattern.labelRes),
                            stringResource(
                                if (r.trigger == Trigger.NOTIFICATION) R.string.live_rule_notify
                                else R.string.live_rule_in_app
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (r.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// Each tile borrows its pattern's own name, so the label is a string resource id. Random is the
// exception: a tile is too narrow for "Random colours".
private val QUICK_TESTS: List<Triple<Int, ImageVector, Pair<Pattern, Int>>> = listOf(
    Triple(Pattern.RAINBOW.shortLabelRes, Icons.Rounded.AutoAwesome, Pattern.RAINBOW to 0xFFFFFFFF.toInt()),
    Triple(R.string.live_test_random, Icons.Rounded.Casino, Pattern.RANDOM to 0xFFFFFFFF.toInt()),
    Triple(Pattern.STROBE.shortLabelRes, Icons.Rounded.FlashOn, Pattern.STROBE to 0xFFFFFFFF.toInt()),
    Triple(Pattern.COMET.shortLabelRes, Icons.Rounded.Flare, Pattern.COMET to 0xFF00E5FF.toInt()),
    Triple(Pattern.PULSE.shortLabelRes, Icons.Rounded.Bolt, Pattern.PULSE to 0xFFFF1744.toInt()),
    Triple(Pattern.HEARTBEAT.shortLabelRes, Icons.Rounded.Favorite, Pattern.HEARTBEAT to 0xFFFF1744.toInt()),
    Triple(Pattern.BREATHE.shortLabelRes, Icons.Rounded.Nightlight, Pattern.BREATHE to 0xFF7C4DFF.toInt()),
    Triple(Pattern.WAVE.shortLabelRes, Icons.Rounded.Waves, Pattern.WAVE to 0xFF00E676.toInt()),
    Triple(Pattern.RADAR.shortLabelRes, Icons.Rounded.Radar, Pattern.RADAR to 0xFF00E5FF.toInt()),
)

/**
 * Android 17 style 8-LED Flashlight Quick Action Card:
 * Features Google's Pixel Android 17 style flashlight controls,
 * one-tap toggle with spring physics, inline Pro Studio Color & Temperature (K) drawer,
 * and long-press opening the Color Spectrum Wheel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Android17FlashlightCard(
    store: Store,
) {
    val active by store.flashlightActive.collectAsStateWithLifecycle()
    val color by store.flashlightColor.collectAsStateWithLifecycle()
    val brightness by store.flashlightBrightness.collectAsStateWithLifecycle()
    val view = androidx.compose.ui.platform.LocalView.current
    var showColorPicker by remember { mutableStateOf(false) }
    var showStudioDrawer by remember { mutableStateOf(false) }
    var cctKelvin by remember { mutableStateOf(4500f) }

    val currentColor = Color(color)
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val animatedBg by animateColorAsState(
        targetValue = if (active) currentColor.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "flashlightBg",
    )

    PixelCard(tone = if (active) 3 else 2) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(animatedBg)
                .combinedClickable(
                    onClick = {
                        PixelHaptics.toggle(view, !active)
                        store.toggleFlashlight()
                    },
                    onLongClick = {
                        PixelHaptics.heavy(view)
                        showColorPicker = true
                    },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                // Android 17 Animated Flashlight Emitter Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (active) currentColor.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (active) R.drawable.ic_flashlight else R.drawable.ic_flashlight_off),
                        contentDescription = null,
                        tint = if (active) currentColor else inactiveColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column {
                    Text(
                        stringResource(R.string.live_flashlight_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Caption(
                        stringResource(
                            if (active) R.string.live_flashlight_subtitle_on
                            else R.string.live_flashlight_subtitle_off
                        )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Color Swatch toggles the inline Pro Studio Color & Temperature drawer
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        )
                        .clickable(
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClickLabel = stringResource(R.string.widget_colour),
                        ) {
                            PixelHaptics.click(view)
                            showStudioDrawer = !showStudioDrawer
                        }
                )
                androidx.compose.material3.Switch(
                    checked = active,
                    onCheckedChange = {
                        PixelHaptics.toggle(view, it)
                        store.setFlashlight(it)
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = active || showStudioDrawer,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PixelSlider(
                    label = stringResource(R.string.widget_intensity),
                    value = brightness,
                    range = 0.05f..1.0f,
                    onChange = { store.setFlashlightBrightness(it) },
                    format = { "%.0f%%".format(it * 100f) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val presets = listOf(0.25f, 0.50f, 0.75f, 1.0f)
                    presets.forEach { preset ->
                        val isSelected = kotlin.math.abs(brightness - preset) < 0.05f
                        val bg by animateColorAsState(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            label = "presetBg",
                        )
                        val fg by animateColorAsState(
                            if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "presetFg",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(bg)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                    PixelHaptics.tick(view)
                                    store.setFlashlightBrightness(preset)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "%.0f%%".format(preset * 100f),
                                style = MaterialTheme.typography.labelMedium,
                                color = fg,
                            )
                        }
                    }
                }

                // Toggleable Pro Studio Color & Temperature (K) drawer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (showStudioDrawer) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                .clickable {
                                    PixelHaptics.click(view)
                                    showStudioDrawer = !showStudioDrawer
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Pro Studio Color & CCT",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (showStudioDrawer) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable {
                                    PixelHaptics.click(view)
                                    showColorPicker = true
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Wheel",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    HexColorPill(color = color, onColorChanged = { store.setFlashlightColor(it) })
                }

                AnimatedVisibility(
                    visible = showStudioDrawer,
                    enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelSlider(
                            label = "Color Temp (CCT)",
                            value = cctKelvin,
                            range = 2000f..7000f,
                            onChange = { k ->
                                cctKelvin = k
                                store.setFlashlightColor(TileDialogActivity.kelvinToRgb(k.toInt()))
                            },
                            format = { "${it.toInt()}K" },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                2700 to "Warm 2700K",
                                4000 to "Neutral 4000K",
                                5500 to "Daylight 5500K",
                                6500 to "Cool 6500K",
                            ).forEach { (kelvin, label) ->
                                val kColor = remember(kelvin) { TileDialogActivity.kelvinToRgb(kelvin) }
                                val selected = color == kColor
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                        .clickable {
                                            PixelHaptics.tick(view)
                                            cctKelvin = kelvin.toFloat()
                                            store.setFlashlightColor(kColor)
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${kelvin}K",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorSpectrumDialog(
            initialColor = color,
            onDismiss = { showColorPicker = false },
            onColorSelected = { selectedColor ->
                store.setFlashlightColor(selectedColor)
            },
        )
    }
}




