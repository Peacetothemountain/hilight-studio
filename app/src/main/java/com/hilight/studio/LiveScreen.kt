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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
private fun tileAccent(pattern: Pattern, color: Int): Color = when {
    color != 0xFFFFFFFF.toInt() -> Color(color)
    pattern == Pattern.RAINBOW -> Color(0xFF7C4DFF)
    else -> Color(0xFFFFB300)
}

/** Home surface: the phone itself, the master switch, the flashlight, and one-tap effects. */
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
        .ifBlank { stringResource(R.string.live_device_fallback) }

    PixelCard(tone = 2) {
        // While a test is running the hero shows the test, not the ambient look.
        val preview = previewLook
        val shown = preview ?: ambient
        val showing = enabled || preview != null
        val modeLabel = when {
            preview != null -> stringResource(R.string.live_mode_testing, stringResource(preview.pattern.shortLabelRes))
            enabled -> stringResource(R.string.live_mode_active, stringResource(ambient.pattern.shortLabelRes))
            else -> stringResource(R.string.live_mode_standby)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BreathingDot(
                        color = if (showing && status.alive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animate = showing && status.alive,
                        size = 8,
                    )
                    Text(
                        text = stringResource(R.string.live_device_mode, modelName, modeLabel),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))
            MasterSwitchPill(enabled) {
                PixelHaptics.toggle(view, it)
                store.setEnabled(it)
            }
        }

        DeviceHero(
            pattern = if (showing) shown.pattern else Pattern.OFF,
            cfg = shown,
            active = showing && status.alive,
            profile = profile,
        )

        val liveStatusText = when {
            !profile.hasHiLight -> stringResource(R.string.live_status_unavailable)
            preview != null -> stringResource(R.string.live_status_testing, stringResource(shown.pattern.labelRes))
            enabled -> stringResource(R.string.live_status_on, stringResource(ambient.pattern.labelRes))
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
                stringResource(if (enabled) R.string.live_toggle_driving else R.string.live_toggle_system)
            )
        }

        when {
            !profile.hasHiLight -> Caption(stringResource(R.string.live_hint_no_array, modelName))
            !status.alive -> RendererMissingBanner()
            enabled -> Caption(stringResource(R.string.live_hint_look))
            else -> Caption(stringResource(R.string.live_hint_take_over))
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

    FlashlightCard(store)

    PixelCard {
        SectionTitle(
            text = stringResource(R.string.live_tests_title),
            trailing = if (previewLook != null) {
                {
                    StopTestPill {
                        PixelHaptics.click(view)
                        store.stopPreview()
                    }
                }
            } else null,
        )
        Caption(stringResource(R.string.live_tests_caption))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QUICK_TESTS.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { test ->
                        val isTileActive = previewLook?.pattern == test.pattern
                        PixelTile(
                            label = stringResource(test.labelRes),
                            icon = test.icon,
                            accent = tileAccent(test.pattern, test.color),
                            enabled = enabled && status.alive,
                            active = isTileActive,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (isTileActive) {
                                store.stopPreview()
                            } else {
                                launchPreview(test.pattern, test.color, 1200, 1f, 4_000)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    PixelCard {
        SectionTitle(
            stringResource(R.string.live_rules_title),
            trailing = {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(
                        text = stringResource(R.string.live_rules_on_count, rules.count { it.enabled }),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
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
                rules.forEach { r -> RuleSummaryRow(r) }
            }
        }
    }
}

@Composable
private fun MasterSwitchPill(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val container by animateColorAsState(
        if (enabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "masterPillBg",
    )
    val content by animateColorAsState(
        if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "masterPillFg",
    )
    Surface(
        shape = CircleShape,
        color = container,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        ),
        // One toggleable target for the label and switch, announced as a single switch.
        modifier = Modifier
            .clip(CircleShape)
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.live_master),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = content,
            )
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun RendererMissingBanner() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BreathingDot(color = MaterialTheme.colorScheme.onErrorContainer, animate = false, size = 8)
            Text(
                text = stringResource(R.string.live_hint_no_renderer),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun StopTestPill(onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BreathingDot(color = MaterialTheme.colorScheme.onErrorContainer, animate = true, size = 6)
            Text(
                text = stringResource(R.string.live_stop_test),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun RuleSummaryRow(r: AppRule) {
    val dot = if (r.randomColor) Color(0xFF7C4DFF) else Color(r.color)
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
                    .background(if (r.enabled) dot else dot.copy(alpha = 0.4f), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
            )
            Text(
                text = ruleLabel(r),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (r.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // One format string rather than three pieces joined with a dot, so a translator can put the
        // trigger first if that is the natural order.
        Surface(
            shape = CircleShape,
            color = if (r.enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
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
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (r.enabled) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

private class QuickTest(val labelRes: Int, val icon: ImageVector, val pattern: Pattern, val color: Int)

// Each tile borrows its pattern's own name, so the label is a string resource id. Random is the
// exception: a tile is too narrow for "Random colours".
private val QUICK_TESTS: List<QuickTest> = listOf(
    QuickTest(Pattern.RAINBOW.shortLabelRes, Icons.Rounded.AutoAwesome, Pattern.RAINBOW, 0xFFFFFFFF.toInt()),
    QuickTest(R.string.live_test_random, Icons.Rounded.Casino, Pattern.RANDOM, 0xFFFFFFFF.toInt()),
    QuickTest(Pattern.STROBE.shortLabelRes, Icons.Rounded.FlashOn, Pattern.STROBE, 0xFFFFFFFF.toInt()),
    QuickTest(Pattern.COMET.shortLabelRes, Icons.Rounded.Flare, Pattern.COMET, 0xFF00E5FF.toInt()),
    QuickTest(Pattern.PULSE.shortLabelRes, Icons.Rounded.Bolt, Pattern.PULSE, 0xFFFF1744.toInt()),
    QuickTest(Pattern.HEARTBEAT.shortLabelRes, Icons.Rounded.Favorite, Pattern.HEARTBEAT, 0xFFFF1744.toInt()),
    QuickTest(Pattern.BREATHE.shortLabelRes, Icons.Rounded.Nightlight, Pattern.BREATHE, 0xFF7C4DFF.toInt()),
    QuickTest(Pattern.WAVE.shortLabelRes, Icons.Rounded.Waves, Pattern.WAVE, 0xFF00E676.toInt()),
    QuickTest(Pattern.RADAR.shortLabelRes, Icons.Rounded.Radar, Pattern.RADAR, 0xFF00E5FF.toInt()),
)

private val BRIGHTNESS_PRESETS = listOf(0.25f, 0.50f, 0.75f, 1.0f)

/**
 * The Live tab's flashlight: the same trapezoid slider and colour controls as the Quick Settings
 * dialog ([TileDialogActivity]), plus an on/off header and one-tap brightness presets.
 */
@Composable
private fun FlashlightCard(store: Store) {
    val active by store.flashlightActive.collectAsStateWithLifecycle()
    val color by store.flashlightColor.collectAsStateWithLifecycle()
    val brightness by store.flashlightBrightness.collectAsStateWithLifecycle()
    val view = LocalView.current
    val percent = (brightness * 100).roundToInt()

    val iconBg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "torchIconBg",
    )
    val onIcon by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "torchIconTint",
    )

    PixelCard(tone = if (active) 3 else 2) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .toggleable(value = active, role = Role.Switch) { on ->
                    PixelHaptics.toggle(view, on)
                    store.setFlashlight(on)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (active) R.drawable.ic_flashlight else R.drawable.ic_flashlight_off),
                    contentDescription = null,
                    tint = onIcon,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.live_flashlight_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Caption(
                    if (active) stringResource(R.string.live_flashlight_subtitle_on, percent)
                    else stringResource(R.string.live_flashlight_subtitle_off)
                )
            }
            Switch(checked = active, onCheckedChange = null)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.65f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                .padding(vertical = 14.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            TrapezoidFlashlightSlider(
                value = if (active) brightness else 0f,
                onValueChange = store::applyFlashlightLevel,
                color = color,
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BRIGHTNESS_PRESETS.forEach { preset ->
                val selected = active && abs(brightness - preset) < 0.05f
                val bg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "presetBg",
                )
                val fg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "presetFg",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(bg)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            PixelHaptics.tick(view)
                            store.applyFlashlightLevel(preset)
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.flashlight_level_percent, (preset * 100).roundToInt()),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = fg,
                    )
                }
            }
        }

        FlashlightColorControls(
            store = store,
            modifier = Modifier.padding(top = 4.dp),
            wheelSize = 210.dp,
            turnOnWhenPicking = true,
        )
    }
}
