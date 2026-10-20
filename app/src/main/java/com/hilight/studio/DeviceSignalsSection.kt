package com.hilight.studio

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
fun DeviceSignalsSection(store: Store) {
    val settings by store.deviceSignals.settings.collectAsStateWithLifecycle()
    var expandedPicker by remember { mutableStateOf<String?>(null) }

    PixelCard {
        SectionTitle(stringResource(R.string.device_signals_title))
        Caption(stringResource(R.string.device_signals_description))

        SignalGroupToggleRow(
            icon = Icons.Rounded.BatteryChargingFull,
            primaryAccent = Color(0xFF00E676),
            secondaryAccent = Color(0xFF00B0FF),
            label = stringResource(R.string.device_signals_charging),
            checked = settings.chargingEnabled,
        ) {
            if (!it && (expandedPicker == "charging" || expandedPicker == "charged")) expandedPicker = null
            store.deviceSignals.updateSettings { s -> s.copy(chargingEnabled = it) }
        }
        if (settings.chargingEnabled) {
            ToggleRow(stringResource(R.string.device_signals_gauge), settings.chargingGauge) {
                store.deviceSignals.updateSettings { s -> s.copy(chargingGauge = it) }
            }
            Caption(
                stringResource(
                    if (settings.chargingGauge) R.string.device_signals_gauge_description
                    else R.string.device_signals_charging_description
                )
            )
            if (!settings.chargingGauge) {
                Caption(stringResource(R.string.device_signals_charging_style))
                PatternCarousel(
                    selected = settings.chargingPattern,
                    options = chargingPatterns,
                    onSelect = { pattern ->
                        store.deviceSignals.updateSettings { it.copy(chargingPattern = pattern) }
                    },
                )
            }
            if (settings.chargingGauge || settings.chargingPattern != Pattern.RAINBOW) {
                SignalColorRow(
                    label = stringResource(R.string.device_signals_charging_color),
                    color = settings.chargingColor,
                    expanded = expandedPicker == "charging",
                    onToggleExpand = {
                        expandedPicker = if (expandedPicker == "charging") null else "charging"
                    },
                    onColor = { color ->
                        store.deviceSignals.updateSettings { it.copy(chargingColor = color) }
                    },
                )
            }
            SignalColorRow(
                label = stringResource(R.string.device_signals_charged_color),
                color = settings.chargedColor,
                expanded = expandedPicker == "charged",
                onToggleExpand = {
                    expandedPicker = if (expandedPicker == "charged") null else "charged"
                },
                onColor = { color ->
                    store.deviceSignals.updateSettings { it.copy(chargedColor = color) }
                },
            )
            PixelSlider(
                label = stringResource(R.string.device_signals_full_percent, settings.fullPercent),
                value = settings.fullPercent.toFloat().coerceIn(1f, 100f),
                range = 1f..100f,
                onChange = { percent ->
                    store.deviceSignals.updateSettings { it.copy(fullPercent = percent.roundToInt()) }
                },
                format = { "${it.roundToInt()}%" },
            )
        }

        SignalGroupToggleRow(
            icon = Icons.Rounded.DoNotDisturbOn,
            primaryAccent = Color(0xFFB388FF),
            secondaryAccent = Color(0xFF7C4DFF),
            label = stringResource(R.string.device_signals_dnd),
            checked = settings.dndEnabled,
        ) {
            if (!it && expandedPicker == "dnd") expandedPicker = null
            store.deviceSignals.updateSettings { s -> s.copy(dndEnabled = it) }
        }
        if (settings.dndEnabled) {
            Caption(stringResource(R.string.device_signals_dnd_description))
            SignalColorRow(
                label = stringResource(R.string.device_signals_dnd),
                color = settings.dndColor,
                expanded = expandedPicker == "dnd",
                onToggleExpand = {
                    expandedPicker = if (expandedPicker == "dnd") null else "dnd"
                },
                onColor = { color ->
                    store.deviceSignals.updateSettings { it.copy(dndColor = color) }
                },
            )
        }

        SignalGroupToggleRow(
            icon = Icons.Rounded.Call,
            primaryAccent = Color(0xFF4ADE80),
            secondaryAccent = Color(0xFF00C853),
            label = stringResource(R.string.device_signals_calls),
            checked = settings.callsEnabled,
        ) {
            if (!it && expandedPicker == "calls") expandedPicker = null
            store.deviceSignals.updateSettings { s -> s.copy(callsEnabled = it) }
        }
        if (settings.callsEnabled) {
            Caption(stringResource(R.string.device_signals_calls_description))
            SignalColorRow(
                label = stringResource(R.string.device_signals_calls),
                color = settings.callColor,
                expanded = expandedPicker == "calls",
                onToggleExpand = {
                    expandedPicker = if (expandedPicker == "calls") null else "calls"
                },
                onColor = { color ->
                    store.deviceSignals.updateSettings { it.copy(callColor = color) }
                },
            )
        }
    }
}

@Composable
private fun SignalGroupToggleRow(
    icon: ImageVector,
    primaryAccent: Color,
    secondaryAccent: Color = primaryAccent,
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = if (checked) 0.30f else 0.16f),
                            secondaryAccent.copy(alpha = if (checked) 0.14f else 0.06f),
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = if (checked) 0.65f else 0.30f),
                            secondaryAccent.copy(alpha = if (checked) 0.45f else 0.18f),
                        )
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) primaryAccent else primaryAccent.copy(alpha = 0.78f),
                modifier = Modifier.size(20.dp),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            ToggleRow(label = label, checked = checked, onChange = onChange)
        }
    }
}

@Composable
private fun SignalColorRow(
    label: String,
    color: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onColor: (Int) -> Unit,
) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (expanded) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                )
                .clickable(role = Role.Button) {
                    PixelHaptics.click(view)
                    onToggleExpand()
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HexColorPill(color = color, onColorChanged = onColor)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            CircleShape,
                        )
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            ColorPicker(color = color, onColor = onColor, label = label)
        }
    }
}
