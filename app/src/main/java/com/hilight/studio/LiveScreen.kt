package com.hilight.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.abs
import kotlin.math.roundToInt
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
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(status.ambientRemainingMs, status.ambientHeld, status.resting, owner) {
        if (!status.resting && !status.ambientHeld && status.ambientRemainingMs > 0L) {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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

/** Home surface: the phone itself, the master switch, flagship flashlight studio, and one-tap effects. */
@Composable
fun LiveScreen(store: Store) {
    val launchPreview = rememberPreviewLauncher(store)
    val enabled by store.enabled.collectAsStateWithLifecycle()
    val ambient by store.ambient.collectAsStateWithLifecycle()
    val status by store.status.collectAsStateWithLifecycle()
    val rules by store.rules.collectAsStateWithLifecycle()
    val suppression by store.suppression.collectAsStateWithLifecycle()
    val previewLook by store.previewLook.collectAsStateWithLifecycle()
    val view = LocalView.current

    val profile = rememberDeviceProfile()
    // A real model name is not translated; only the fallback profile has a resource to read.
    val modelName = (profile.labelRes?.let { stringResource(it) } ?: profile.label)
        .ifBlank { "Pixel 11 Pro XL" }

    // 1. Unified Pixel 11 Pro XL Visor Hero & Master Power Card
    PixelCard(tone = 2) {
        // While a test is running the hero shows the test, not the ambient look.
        val shown = previewLook ?: ambient
        val showing = enabled || previewLook != null
        val modeLabel = if (previewLook != null) {
            "Testing · ${stringResource(previewLook!!.pattern.shortLabelRes)}"
        } else if (enabled) {
            "Active · ${stringResource(ambient.pattern.shortLabelRes)}"
        } else {
            "Standby"
        }

        // Top Header Row: Device Mode Pill Badge + Master Power Pill Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
                ),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BreathingDot(
                        color = if (showing && status.alive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animate = showing && status.alive,
                        size = 8,
                    )
                    Text(
                        text = "$modelName · $modeLabel",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            val masterContainerColor by animateColorAsState(
                targetValue = if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                label = "masterPillBg",
            )
            val masterContentColor by animateColorAsState(
                targetValue = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "masterPillFg",
            )

            Surface(
                shape = CircleShape,
                color = masterContainerColor,
                border = BorderStroke(
                    1.dp,
                    if (enabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                    },
                ),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Switch) {
                        PixelHaptics.toggle(view, !enabled)
                        store.setEnabled(!enabled)
                    },
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Master",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = masterContentColor,
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            PixelHaptics.toggle(view, it)
                            store.setEnabled(it)
                        },
                    )
                }
            }
        }

        // Centerpiece: Interactive Pixel 11 Pro XL Visor Hero
        DeviceHero(
            pattern = if (showing) shown.pattern else Pattern.OFF,
            cfg = shown,
            active = showing && status.alive,
            profile = profile,
        )

        // Bottom Status & Action Strip
        val liveStatusText = when {
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
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LivePill(
                text = liveStatusText,
                ok = status.alive && profile.hasHiLight,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Caption(
                stringResource(
                    if (enabled) R.string.live_toggle_driving else R.string.live_toggle_system
                )
            )
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

        if (!status.alive) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BreathingDot(
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        animate = false,
                        size = 8,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Renderer not connected — open Setup tab to start Shizuku, Root, or Wireless ADB",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.live_hint_no_renderer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }

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

    // 2. Flagship HiLight Flashlight Studio Card — 1:1 with TileDialogActivity.kt
    Android17FlashlightCard(store = store)

    // 3. Interactive Quick-Fire Effect Studio Grid
    PixelCard {
        SectionTitle(
            text = stringResource(R.string.live_tests_title),
            trailing = if (previewLook != null) {
                {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button) {
                                PixelHaptics.click(view)
                                store.stopPreview()
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            BreathingDot(
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                animate = true,
                                size = 6,
                            )
                            Text(
                                text = "Stop Test",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            } else null,
        )
        Caption(stringResource(R.string.live_tests_caption))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QUICK_TESTS.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { (labelRes, icon, spec) ->
                        val isTileActive = previewLook?.pattern == spec.first
                        PixelTile(
                            label = stringResource(labelRes),
                            icon = icon,
                            accent = tileAccent(spec.first, spec.second),
                            enabled = enabled && status.alive,
                            active = isTileActive,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (isTileActive) {
                                store.stopPreview()
                            } else {
                                launchPreview(spec.first, spec.second, 1200, 1f, 4_000)
                            }
                        }
                    }
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // 4. Upgraded Active Rules Summary Card
    PixelCard {
        SectionTitle(
            stringResource(R.string.live_rules_title),
            trailing = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = stringResource(R.string.live_rules_on_count, rules.count { it.enabled }),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            },
        )
        if (rules.isEmpty()) {
            Caption(stringResource(R.string.live_rules_empty))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rules.forEach { r ->
                    val ruleDotColor = if (r.randomColor) {
                        Color(0xFF7C4DFF)
                    } else {
                        Color(r.color)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (r.enabled) ruleDotColor else ruleDotColor.copy(alpha = 0.4f),
                                        shape = CircleShape,
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        CircleShape,
                                    )
                            )
                            Text(
                                text = ruleLabel(r),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = if (r.enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        // One format string rather than three pieces joined with a dot, so a translator
                        // can put the trigger first if that is the natural order.
                        Surface(
                            shape = CircleShape,
                            color = if (r.enabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.live_rule_summary,
                                    if (r.randomColor) stringResource(R.string.live_rule_random)
                                    else stringResource(r.pattern.labelRes),
                                    stringResource(
                                        if (r.trigger == Trigger.NOTIFICATION) R.string.live_rule_notify
                                        else R.string.live_rule_in_app
                                    ),
                                ),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = if (r.enabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
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

private val FLASHLIGHT_PRESET_SWATCHES: List<Int> = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFF1744.toInt(),
    0xFFFF9100.toInt(),
    0xFFFFEA00.toInt(),
    0xFF00E676.toInt(),
    0xFF00E5FF.toInt(),
    0xFF2979FF.toInt(),
    0xFFD500F9.toInt(),
)

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

/**
 * Flagship HiLight Flashlight Studio Card — 1:1 with [TileDialogActivity]:
 * Features interactive [TrapezoidFlashlightSlider] in a dark studio well, quick-brightness pills,
 * signature dual luminous 38.dp action buttons (Color Wheel stencil filter + White/Temperature auto-select
 * circle with goldish shimmer), [HexColorPill], and expandable Spectrum | Temperature (K) drawer.
 */
@Composable
fun Android17FlashlightCard(
    store: Store,
) {
    val active by store.flashlightActive.collectAsStateWithLifecycle()
    val color by store.flashlightColor.collectAsStateWithLifecycle()
    val brightness by store.flashlightBrightness.collectAsStateWithLifecycle()
    val view = LocalView.current

    var showColorPalette by remember { mutableStateOf(false) }
    var colorMode by remember { mutableIntStateOf(0) } // 0 = Spectrum, 1 = Temperature (K)
    var selectedKelvin by remember { mutableIntStateOf(4500) }
    var whiteTempColor by remember { mutableIntStateOf(0xFFFFFFFF.toInt()) }

    val currentPercentage = (brightness * 100).roundToInt()
    val selectedColor = Color(color)

    val iconContainerBg by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "torchIconBg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "torchIconTint",
    )
    val badgeBg by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "torchBadgeBg",
    )
    val badgeFg by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "torchBadgeFg",
    )

    PixelCard(tone = if (active) 3 else 2) {
        // 1. Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconContainerBg, CircleShape)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            PixelHaptics.toggle(view, !active)
                            store.setFlashlight(!active)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (active) R.drawable.ic_flashlight else R.drawable.ic_flashlight_off
                        ),
                        contentDescription = stringResource(R.string.live_flashlight_title),
                        tint = iconTint,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "HiLight Flashlight",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Caption(
                        text = "8-LED Rear Visor Torch · " + stringResource(
                            if (active) R.string.live_flashlight_subtitle_on
                            else R.string.live_flashlight_subtitle_off
                        ),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = badgeBg,
                ) {
                    Text(
                        text = if (active && currentPercentage > 0) "$currentPercentage%" else "Off",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Switch(
                    checked = active,
                    onCheckedChange = {
                        PixelHaptics.toggle(view, it)
                        store.setFlashlight(it)
                    },
                )
            }
        }

        // 2. Centerpiece — Interactive TrapezoidFlashlightSlider in dark-elevated studio well + Quick Brightness Pills
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.65f),
                    RoundedCornerShape(24.dp),
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                    RoundedCornerShape(24.dp),
                )
                .padding(vertical = 14.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            TrapezoidFlashlightSlider(
                value = if (active) brightness else 0f,
                onValueChange = { v ->
                    if (v > 0.01f) {
                        store.setFlashlightBrightness(v)
                        if (!active) store.setFlashlight(true)
                    } else {
                        store.setFlashlight(false)
                    }
                },
                color = color,
            )
        }

        // 4 Quick-Brightness Pill Buttons (25%, 50%, 75%, 100%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val presets = listOf(0.25f, 0.50f, 0.75f, 1.0f)
            presets.forEach { preset ->
                val isSelected = active && abs(brightness - preset) < 0.05f
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
                        .clickable(role = Role.Button) {
                            PixelHaptics.tick(view)
                            store.setFlashlightBrightness(preset)
                            if (!active) store.setFlashlight(true)
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%.0f%%".format(preset * 100f),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = fg,
                    )
                }
            }
        }

        // 3. Signature Dual Luminous Action Buttons + HexColorPill (Exact 1:1 from TileDialogActivity.kt:202-445)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "luminousSpinner")

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

            val isWhiteActive = (color == whiteTempColor) && !showColorPalette
            val isColorWheelActive = (color != whiteTempColor) || showColorPalette

            val goldShimmerBrush = Brush.sweepGradient(
                0.0f to Color(0xFFFFF9E6).copy(alpha = 0.98f * shimmerPulse),
                0.18f to Color(0xFFFFD54F).copy(alpha = 0.85f * shimmerPulse),
                0.40f to Color(0xFFFFB300).copy(alpha = 0.35f * shimmerPulse),
                0.65f to Color.Transparent,
                0.82f to Color(0xFFFFE082).copy(alpha = 0.50f * shimmerPulse),
                1.0f to Color(0xFFFFF9E6).copy(alpha = 0.98f * shimmerPulse),
            )

            val colorWheelSpinBrush = Brush.sweepGradient(
                0.0f to selectedColor.copy(alpha = 0.98f * shimmerPulse),
                0.18f to selectedColor.copy(alpha = 0.85f * shimmerPulse),
                0.40f to selectedColor.copy(alpha = 0.35f * shimmerPulse),
                0.65f to Color.Transparent,
                0.82f to selectedColor.copy(alpha = 0.50f * shimmerPulse),
                1.0f to selectedColor.copy(alpha = 0.98f * shimmerPulse),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Button 1: Color Wheel Stencil Filter Button (38.dp)
                val rainbowBrush = Brush.sweepGradient(
                    listOf(
                        Color.Red, Color.Yellow, Color.Green,
                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                    )
                )
                val wheelScale by animateFloatAsState(
                    targetValue = if (isColorWheelActive) 1.08f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "wheelScale",
                )

                Box(
                    modifier = Modifier
                        .scale(wheelScale)
                        .size(38.dp)
                        .then(
                            if (isColorWheelActive) {
                                Modifier.drawBehind {
                                    val radius = size.minDimension / 2f
                                    val ringRadius = radius + 4.dp.toPx()

                                    // 1. Soft radial luminous aura using selected color
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                selectedColor.copy(alpha = 0.40f * shimmerPulse),
                                                selectedColor.copy(alpha = 0.12f * shimmerPulse),
                                                Color.Transparent,
                                            ),
                                            center = center,
                                            radius = ringRadius + 9.dp.toPx(),
                                        )
                                    )

                                    // 2. Material 3 Expressive spinning luminous circle in selected color
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
                            if (!isColorWheelActive) {
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
                            showColorPalette = !showColorPalette
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.ColorLens,
                        contentDescription = "Color Wheel",
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithCache {
                                val filterLightBrush = if (isColorWheelActive) {
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.98f),
                                            selectedColor.copy(alpha = 0.95f),
                                            selectedColor,
                                            selectedColor.copy(alpha = 0.65f),
                                        ),
                                        center = Offset(size.width * 0.45f, size.height * 0.40f),
                                        radius = size.minDimension * 0.85f,
                                    )
                                } else {
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFE0E0E0),
                                            Color(0xFF9E9E9E),
                                            Color(0xFF616161),
                                        ),
                                        center = Offset(size.width * 0.45f, size.height * 0.40f),
                                        radius = size.minDimension * 0.85f,
                                    )
                                }
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(brush = filterLightBrush, blendMode = BlendMode.SrcIn)
                                }
                            },
                    )
                }

                // Button 2: White / Temperature Auto-Select Circle (38.dp) with Goldish Luminous Glow
                val whiteScale by animateFloatAsState(
                    targetValue = if (isWhiteActive) 1.08f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "whiteScale",
                )

                Box(
                    modifier = Modifier
                        .scale(whiteScale)
                        .size(38.dp)
                        .then(
                            if (isWhiteActive) {
                                Modifier.drawBehind {
                                    val radius = size.minDimension / 2f
                                    val ringRadius = radius + 4.dp.toPx()

                                    // 1. Soft goldish shimmering radial aura
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFFFDF73).copy(alpha = 0.40f * shimmerPulse),
                                                Color(0xFFFFB300).copy(alpha = 0.12f * shimmerPulse),
                                                Color.Transparent,
                                            ),
                                            center = center,
                                            radius = ringRadius + 9.dp.toPx(),
                                        )
                                    )

                                    // 2. Material 3 Expressive spinning goldish shimmering circle
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
                            } else Modifier
                        )
                        .clip(CircleShape)
                        .background(Color(whiteTempColor))
                        .border(
                            width = if (isWhiteActive) 2.dp else 1.dp,
                            color = if (isWhiteActive) {
                                Color(0xFFFFDF73).copy(alpha = 0.85f * shimmerPulse)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            },
                            shape = CircleShape,
                        )
                        .clickable(role = Role.Button) {
                            PixelHaptics.click(view)
                            store.setFlashlightColor(whiteTempColor)
                            if (!active) store.setFlashlight(true)
                            showColorPalette = false
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Pure illuminated circle — no inner dot
                }

                // 3. HexColorPill right next to the two 38.dp buttons
                HexColorPill(
                    color = color,
                    onColorChanged = { store.setFlashlightColor(it) },
                )
            }

            // 4. Expandable Spectrum | Temperature (K) Drawer (Exact 1:1 from TileDialogActivity.kt:448-607)
            AnimatedVisibility(
                visible = showColorPalette,
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
                    // Mode Switcher: Spectrum (0) vs Temperature (K) (1)
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val isSpectrum = colorMode == 0
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSpectrum) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable {
                                    PixelHaptics.click(view)
                                    colorMode = 0
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Spectrum",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSpectrum) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        val isCct = colorMode == 1
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isCct) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable {
                                    PixelHaptics.click(view)
                                    colorMode = 1
                                    val c = TileDialogActivity.kelvinToRgb(selectedKelvin)
                                    whiteTempColor = c
                                    store.setFlashlightColor(c)
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Temperature (K)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isCct) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (colorMode == 0) {
                        // High-precision 360° Color Spectrum Wheel
                        ColorSpectrumWheel(
                            color = color,
                            onColorChanged = { store.setFlashlightColor(it) },
                            sizeDp = 210,
                        )

                        // Preset Color Swatches
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FLASHLIGHT_PRESET_SWATCHES.forEach { swatchInt ->
                                val isSwatchSelected = color == swatchInt
                                Box(
                                    modifier = Modifier
                                        .size(if (isSwatchSelected) 30.dp else 26.dp)
                                        .clip(CircleShape)
                                        .background(Color(swatchInt), CircleShape)
                                        .border(
                                            width = if (isSwatchSelected) 2.dp else 1.dp,
                                            color = if (isSwatchSelected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            },
                                            shape = CircleShape,
                                        )
                                        .clickable(role = Role.Button) {
                                            PixelHaptics.tick(view)
                                            store.setFlashlightColor(swatchInt)
                                        },
                                )
                            }
                        }
                    } else {
                        // Planckian Blackbody Temperature (K) Selector
                        val kelvinColor = Color(TileDialogActivity.kelvinToRgb(selectedKelvin))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = getKelvinDescription(selectedKelvin),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = kelvinColor,
                            )

                            // Realistic Blackbody Radiation Gradient Bar (2000K..7000K)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(TileDialogActivity.kelvinToRgb(2000)),
                                                Color(TileDialogActivity.kelvinToRgb(3000)),
                                                Color(TileDialogActivity.kelvinToRgb(4000)),
                                                Color(TileDialogActivity.kelvinToRgb(5500)),
                                                Color(TileDialogActivity.kelvinToRgb(7000)),
                                            )
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        CircleShape,
                                    )
                            )

                            // Temperature Slider (2000K to 7000K)
                            Slider(
                                value = selectedKelvin.toFloat(),
                                onValueChange = { k ->
                                    selectedKelvin = k.roundToInt()
                                    val c = TileDialogActivity.kelvinToRgb(selectedKelvin)
                                    whiteTempColor = c
                                    store.setFlashlightColor(c)
                                },
                                valueRange = 2000f..7000f,
                                steps = 25,
                                colors = SliderDefaults.colors(
                                    thumbColor = kelvinColor,
                                    activeTrackColor = kelvinColor.copy(alpha = 0.85f),
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            )

                            // Quick CCT Preset Pills: Warm (2700K), Neutral (4500K), Cool (6500K)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    2700 to "Warm 2700K",
                                    4500 to "Neutral 4500K",
                                    6500 to "Cool 6500K",
                                ).forEach { (k, label) ->
                                    val isCur = selectedKelvin == k
                                    val presetColor = Color(TileDialogActivity.kelvinToRgb(k))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CircleShape)
                                            .background(
                                                if (isCur) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                            )
                                            .clickable(role = Role.Button) {
                                                PixelHaptics.click(view)
                                                selectedKelvin = k
                                                val rgb = TileDialogActivity.kelvinToRgb(k)
                                                whiteTempColor = rgb
                                                store.setFlashlightColor(rgb)
                                            }
                                            .padding(vertical = 8.dp, horizontal = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                            color = if (isCur) presetColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
