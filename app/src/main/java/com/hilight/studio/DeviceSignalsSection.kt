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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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

        ToggleRow(stringResource(R.string.device_signals_charging), settings.chargingEnabled) {
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
                value = settings.fullPercent.toFloat().coerceIn(50f, 100f),
                range = 50f..100f,
                onChange = { percent ->
                    store.deviceSignals.updateSettings { it.copy(fullPercent = percent.roundToInt()) }
                },
                format = { "${it.roundToInt()}%" },
            )
        }

        ToggleRow(stringResource(R.string.device_signals_dnd), settings.dndEnabled) {
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

        ToggleRow(stringResource(R.string.device_signals_calls), settings.callsEnabled) {
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
private fun SignalColorRow(
    label: String,
    color: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onColor: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (expanded) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                )
                .clickable { onToggleExpand() }
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
