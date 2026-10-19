package com.hilight.studio

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun interpolateGradientLeds(startColor: Int, endColor: Int): List<Int> {
    val start = Color(startColor)
    val end = Color(endColor)
    return List(LED_COUNT) { i ->
        val k = i.toFloat() / (LED_COUNT - 1).coerceAtLeast(1)
        val r = (start.red * (1f - k) + end.red * k).coerceIn(0f, 1f)
        val g = (start.green * (1f - k) + end.green * k).coerceIn(0f, 1f)
        val b = (start.blue * (1f - k) + end.blue * k).coerceIn(0f, 1f)
        android.graphics.Color.valueOf(r, g, b).toArgb()
    }
}

/** The always-on look: pattern, colours, timing. */
@Composable
fun AmbientScreen(store: Store) {
    val launchPreview = rememberPreviewLauncher(store)
    val ambient by store.ambient.collectAsStateWithLifecycle()
    val enabled by store.enabled.collectAsStateWithLifecycle()
    val previewLook by store.previewLook.collectAsStateWithLifecycle()
    var editingLed by rememberSaveable { mutableIntStateOf(0) }
    var editingGradientStop by rememberSaveable { mutableIntStateOf(0) }
    var lastActivePatternKey by rememberSaveable {
        mutableStateOf(if (ambient.pattern != Pattern.OFF) ambient.pattern.key else Pattern.SOLID.key)
    }
    LaunchedEffect(ambient.pattern) {
        if (ambient.pattern != Pattern.OFF) {
            lastActivePatternKey = ambient.pattern.key
        }
    }

    PresetsCard(store)

    PixelCard(tone = 2) {
        val displayedAmbient = previewLook ?: ambient
        // 1. Studio Header Row: Title + Pattern/Brightness Pill Badge + Test on Visor Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Visor Style Studio",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${stringResource(displayedAmbient.pattern.shortLabelRes)} · ${(displayedAmbient.brightness * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(
                onClick = {
                    val previewPattern = if (ambient.pattern == Pattern.OFF) {
                        Pattern.of(lastActivePatternKey)
                    } else {
                        ambient.pattern
                    }
                    val look = ambient.copy(pattern = previewPattern)
                    launchPreview(
                        look.pattern,
                        look.color,
                        look.speedMs,
                        look.brightness,
                        2500,
                        direction = look.direction,
                        look = look,
                    )
                },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                ButtonLabel("Test on Visor")
            }
        }

        // 2. Dark-Elevated Studio Frame with Live HiLightDiffuserPreview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                    RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            HiLightDiffuserPreview(
                pattern = displayedAmbient.pattern,
                cfg = displayedAmbient,
                active = displayedAmbient.pattern != Pattern.OFF,
            )
        }

        // 3. Keep Lit Toggle Row
        val isKeepLit = ambient.pattern != Pattern.OFF
        PixelToggleRow(
            title = stringResource(R.string.style_always_on_style),
            subtitle = if (isKeepLit) null else stringResource(R.string.style_off_body),
            checked = isKeepLit,
            onChange = { keepLit ->
                val targetPattern = if (keepLit) Pattern.of(lastActivePatternKey) else Pattern.OFF
                store.setAmbient(ambient.copy(pattern = targetPattern))
            },
        )

        // 4. 2-Column Expressive Pattern Studio Grid
        PatternCarousel(
            selected = ambient.pattern,
            options = Pattern.entries.filter { it != Pattern.OFF },
            onSelect = {
                lastActivePatternKey = it.key
                store.setAmbient(ambient.copy(pattern = it))
            },
        )

        if (!enabled) {
            Text(
                stringResource(R.string.style_control_off_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    // options morph rather than jump when the pattern changes
    AnimatedContent(
        targetState = ambient.pattern,
        transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(120))) },
        label = "patternOptions",
    ) { pattern ->
        Column {
            when (pattern) {
                Pattern.RANDOM -> PixelCard {
                    SectionTitle(stringResource(R.string.pattern_random))
                    PixelSlider(
                        stringResource(R.string.style_change_every),
                        ambient.randomIntervalMs.toFloat(),
                        150f..8000f,
                        { store.setAmbient(ambient.copy(randomIntervalMs = it.toInt())) },
                        typeInSeconds = true,
                    ) {
                        // shown to a tenth of a second, so dragging does not spray digits
                        val tenths = (it / 100).toInt() / 10f
                        stringResource(R.string.duration_seconds_fraction, tenths.toString())
                    }
                    ToggleRow(stringResource(R.string.style_colour_per_led), ambient.randomPerLed) {
                        store.setAmbient(ambient.copy(randomPerLed = it))
                    }
                    ToggleRow(stringResource(R.string.style_fade_between_colours), ambient.randomSmooth) {
                        store.setAmbient(ambient.copy(randomSmooth = it))
                    }
                    PixelSlider(
                        stringResource(R.string.style_saturation),
                        ambient.randomSaturation,
                        0.2f..1f,
                        { store.setAmbient(ambient.copy(randomSaturation = it)) },
                    ) { stringResource(R.string.style_percent, (it * 100).toInt()) }
                }

                Pattern.RAINBOW -> PixelCard {
                    SectionTitle(stringResource(R.string.pattern_rainbow))
                    ToggleRow(stringResource(R.string.style_rainbow_spread), ambient.rainbowSpread) {
                        store.setAmbient(ambient.copy(rainbowSpread = it))
                    }
                    Caption(stringResource(R.string.style_rainbow_spread_off))
                }

                Pattern.CUSTOM -> PixelCard {
                    SectionTitle(stringResource(R.string.style_per_led_colours))
                    Caption(stringResource(R.string.style_per_led_hint))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ambient.perLed.forEachIndexed { i, c ->
                            LedSwatch(
                                color = c,
                                selected = i == editingLed,
                                label = stringResource(R.string.style_led_number, i + 1),
                                modifier = Modifier.weight(1f),
                            ) { editingLed = i }
                        }
                    }
                    val safeEditingLed = editingLed.coerceIn(0, (ambient.perLed.size - 1).coerceAtLeast(0))
                    ColorPicker(
                        color = ambient.perLed.getOrElse(safeEditingLed) { ambient.color },
                        onColor = { c ->
                            if (ambient.perLed.isNotEmpty()) {
                                store.setAmbient(
                                    ambient.copy(
                                        perLed = ambient.perLed.toMutableList().also { it[safeEditingLed] = c }
                                    )
                                )
                            }
                        },
                        label = stringResource(R.string.style_led_number, safeEditingLed + 1),
                    )
                    PixelSlider(
                        stringResource(R.string.style_rotate_around_array),
                        ambient.rotateMs.toFloat(),
                        0f..2000f,
                        { store.setAmbient(ambient.copy(rotateMs = it.toInt())) },
                    ) {
                        if (it < 50) stringResource(R.string.style_rotate_off)
                        else stringResource(R.string.duration_ms, it.toInt())
                    }
                    val wallpaper = wallpaperLedColours()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = {
                                store.setAmbient(
                                    ambient.copy(
                                        perLed = List(LED_COUNT) { i -> Renderer.hsv(i * 360f / LED_COUNT) })
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { ButtonLabel(stringResource(Pattern.RAINBOW.shortLabelRes)) }
                        FilledTonalButton(
                            onClick = { store.setAmbient(ambient.copy(perLed = wallpaper)) },
                            modifier = Modifier.weight(1f),
                        ) { ButtonLabel(stringResource(R.string.style_wallpaper)) }
                        FilledTonalButton(
                            onClick = {
                                store.setAmbient(ambient.copy(perLed = List(LED_COUNT) { ambient.color }))
                            },
                            modifier = Modifier.weight(1f),
                        ) { ButtonLabel(stringResource(R.string.pattern_solid)) }
                    }
                }

                Pattern.GRADIENT -> PixelCard {
                    SectionTitle(stringResource(R.string.pattern_gradient))
                    // Live gradient preview bar between Start and End stops
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(ambient.color), Color(ambient.secondColor))
                                )
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                CircleShape,
                            )
                    )
                    // 2-Stop Selector (Start Color / End Color)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf(
                            0 to ("Start Color" to ambient.color),
                            1 to ("End Color" to ambient.secondColor),
                        ).forEach { (stopIndex, pair) ->
                            val (stopLabel, stopColor) = pair
                            val selected = editingGradientStop == stopIndex
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(18.dp),
                                    )
                                    .clickable { editingGradientStop = stopIndex }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(stopColor))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                            CircleShape,
                                        )
                                )
                                Column {
                                    Text(
                                        text = stopLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = String.format("#%06X", 0xFFFFFF and stopColor),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    ColorPicker(
                        color = if (editingGradientStop == 0) ambient.color else ambient.secondColor,
                        onColor = { c ->
                            val newStart = if (editingGradientStop == 0) c else ambient.color
                            val newEnd = if (editingGradientStop == 1) c else ambient.secondColor
                            store.setAmbient(
                                ambient.copy(
                                    color = newStart,
                                    secondColor = newEnd,
                                    perLed = interpolateGradientLeds(newStart, newEnd),
                                )
                            )
                        },
                        label = if (editingGradientStop == 0) "Start Color" else "End Color",
                    )
                    PixelSlider(
                        stringResource(R.string.style_rotate_around_array),
                        ambient.rotateMs.toFloat(),
                        0f..2000f,
                        { store.setAmbient(ambient.copy(rotateMs = it.toInt())) },
                    ) {
                        if (it < 50) stringResource(R.string.style_rotate_off)
                        else stringResource(R.string.duration_ms, it.toInt())
                    }
                    val wallpaper = wallpaperLedColours()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val s = 0xFFFF1744.toInt()
                                val e = 0xFFFFD600.toInt()
                                store.setAmbient(
                                    ambient.copy(
                                        color = s,
                                        secondColor = e,
                                        perLed = interpolateGradientLeds(s, e),
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { ButtonLabel("Sunset") }
                        FilledTonalButton(
                            onClick = {
                                val s = 0xFF00E5FF.toInt()
                                val e = 0xFFD500F9.toInt()
                                store.setAmbient(
                                    ambient.copy(
                                        color = s,
                                        secondColor = e,
                                        perLed = interpolateGradientLeds(s, e),
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { ButtonLabel("Cyber") }
                        FilledTonalButton(
                            onClick = {
                                val s = wallpaper.firstOrNull() ?: ambient.color
                                val e = wallpaper.lastOrNull() ?: ambient.secondColor
                                store.setAmbient(
                                    ambient.copy(
                                        color = s,
                                        secondColor = e,
                                        perLed = wallpaper,
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { ButtonLabel(stringResource(R.string.style_wallpaper)) }
                        FilledTonalButton(
                            onClick = {
                                val s = ambient.secondColor
                                val e = ambient.color
                                store.setAmbient(
                                    ambient.copy(
                                        color = s,
                                        secondColor = e,
                                        perLed = interpolateGradientLeds(s, e),
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { ButtonLabel("Swap") }
                    }
                }

                Pattern.OFF -> PixelCard {
                    SectionTitle(stringResource(R.string.pattern_off))
                    Caption(stringResource(R.string.style_off_body))
                }

                Pattern.CHASE, Pattern.COMET, Pattern.WAVE -> PixelCard {
                    SectionTitle(stringResource(pattern.labelRes))
                    ColorPicker(ambient.color, { store.setAmbient(ambient.copy(color = it)) })
                    Spacer(Modifier.size(12.dp))
                    DirectionSelector(
                        selected = ambient.direction,
                        onSelect = { store.setAmbient(ambient.copy(direction = it)) },
                    )
                }

                else -> PixelCard {
                    SectionTitle(stringResource(R.string.style_colour))
                    ColorPicker(ambient.color, { store.setAmbient(ambient.copy(color = it)) })
                }
            }

            if (pattern != Pattern.OFF) {
                PixelCard {
                    SectionTitle(stringResource(R.string.style_timing))
                    if (pattern.usesSpeed) {
                        PixelSlider(
                            stringResource(R.string.style_time_per_cycle),
                            ambient.speedMs.toFloat(),
                            150f..8000f,
                            { store.setAmbient(ambient.copy(speedMs = it.toInt())) },
                            typeInSeconds = true,
                        ) { formatDuration(it.toInt()) }
                        pattern.cycleMeaningRes?.let { Caption(stringResource(it)) }
                        Caption(stringResource(R.string.style_shorter_is_faster))
                    }
                    PixelSlider(
                        stringResource(R.string.style_brightness),
                        ambient.brightness,
                        0.02f..1f,
                        { store.setAmbient(ambient.copy(brightness = it)) },
                    ) { stringResource(R.string.style_percent, (it * 100).toInt()) }
                    Caption(stringResource(R.string.style_brightness_note))
                }
            }
        }
    }
}

/** Saved looks: apply with a tap, save the current one, and move them between devices as JSON. */
@Composable
private fun PresetsCard(store: Store) {
    val ctx = LocalContext.current
    val presets by store.presets.collectAsStateWithLifecycle()
    var naming by rememberSaveable { mutableStateOf(false) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var importText by rememberSaveable { mutableStateOf("") }

    PixelCard {
        SectionTitle(
            stringResource(R.string.style_presets),
            trailing = { Caption(stringResource(R.string.style_presets_saved, presets.size)) },
        )
        if (presets.isEmpty()) {
            Caption(stringResource(R.string.style_presets_empty))
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    Row(
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                CircleShape,
                            )
                            .clickable { store.applyPreset(preset) }
                            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(preset.name, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "✕",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { store.deletePreset(preset) }
                                .padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = { name = ""; naming = true },
                modifier = Modifier.weight(1f),
            ) { ButtonLabel(stringResource(R.string.common_save)) }
            FilledTonalButton(
                onClick = { shareText(ctx, store.exportPresets()) },
                enabled = presets.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { ButtonLabel(stringResource(R.string.style_export)) }
            FilledTonalButton(
                onClick = { importText = ""; importing = true },
                modifier = Modifier.weight(1f),
            ) { ButtonLabel(stringResource(R.string.style_import)) }
        }
    }

    if (naming) {
        // An empty field falls back to a numbered name. Resolved here rather than in the store,
        // because the store has no business holding a piece of copy in one language.
        val fallbackName = stringResource(R.string.style_preset_default_name, presets.size + 1)
        AlertDialog(
            onDismissRequest = { naming = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.style_name_this_look)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.style_name_field)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.savePreset(name.trim().ifEmpty { fallbackName })
                    naming = false
                }) { ButtonLabel(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { naming = false }) {
                    ButtonLabel(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (importing) {
        AlertDialog(
            onDismissRequest = { importing = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.style_paste_exported_presets)) },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text(stringResource(R.string.style_import_json_field)) },
                    modifier = Modifier.heightIn(max = 220.dp),
                )
            },
            confirmButton = {
                val res = LocalResources.current
                TextButton(onClick = {
                    val added = store.importPresets(importText)
                    Toast.makeText(
                        ctx,
                        // Read through LocalResources rather than the context: a Context captured in
                        // composition is not invalidated when the configuration changes, so after a
                        // locale switch this toast would have been built from the previous language's
                        // resources.
                        if (added == null) res.getString(R.string.style_import_failed)
                        else res.getString(R.string.style_import_count, added),
                        Toast.LENGTH_SHORT,
                    ).show()
                    importing = false
                }) { ButtonLabel(stringResource(R.string.style_import)) }
            },
            dismissButton = {
                TextButton(onClick = { importing = false }) {
                    ButtonLabel(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private fun shareText(ctx: android.content.Context, text: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    runCatching {
        ctx.startActivity(
            android.content.Intent.createChooser(send, ctx.getString(R.string.style_export_chooser))
        )
    }
}

private fun patternStudioIcon(pattern: Pattern): ImageVector = when (pattern) {
    Pattern.SOLID -> Icons.Rounded.BrightnessHigh
    Pattern.GRADIENT -> Icons.Rounded.Gradient
    Pattern.CUSTOM -> Icons.Rounded.Tune
    Pattern.BREATHE -> Icons.Rounded.Nightlight
    Pattern.BLINK -> Icons.Rounded.FlashOn
    Pattern.PULSE -> Icons.Rounded.Bolt
    Pattern.WAVE -> Icons.Rounded.Waves
    Pattern.CHASE -> Icons.Rounded.Sync
    Pattern.COMET -> Icons.Rounded.Flare
    Pattern.BOUNCE -> Icons.Rounded.SwapHoriz
    Pattern.RADAR -> Icons.Rounded.Radar
    Pattern.CONVERGE -> Icons.Rounded.Compress
    Pattern.HEARTBEAT -> Icons.Rounded.Favorite
    Pattern.GLITCH -> Icons.Rounded.ElectricBolt
    Pattern.STROBE -> Icons.Rounded.FlashOn
    Pattern.RAINBOW -> Icons.Rounded.AutoAwesome
    Pattern.RANDOM -> Icons.Rounded.Casino
    Pattern.METER -> Icons.Rounded.Equalizer
    Pattern.OFF -> Icons.Rounded.BrightnessHigh
}

@Composable
private fun patternStudioCaption(pattern: Pattern): String = when (pattern) {
    Pattern.SOLID -> "Steady continuous illumination across the visor"
    Pattern.GRADIENT -> "Smooth two-stop linear blend across all 8 LEDs"
    Pattern.CUSTOM -> stringResource(R.string.style_per_led_hint)
    Pattern.RANDOM -> "Generative shifting hues across the array"
    Pattern.OFF -> stringResource(R.string.style_off_body)
    else -> pattern.cycleMeaningRes?.let { stringResource(it) } ?: stringResource(pattern.labelRes)
}

/** 2-Column Expressive Pattern Studio Grid with glowing icon badges and animated selection. */
@Composable
fun PatternCarousel(
    selected: Pattern,
    options: List<Pattern> = Pattern.entries.filter { it != Pattern.OFF },
    onSelect: (Pattern) -> Unit,
) {
    val rows = remember(options) {
        options.filter { it != Pattern.OFF }.chunked(2)
    }
    val view = androidx.compose.ui.platform.LocalView.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { rowPatterns ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowPatterns.forEach { p ->
                    val isSelected = p == selected
                    val cardShape = RoundedCornerShape(20.dp)
                    val bg by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        label = "patternCardBg",
                    )
                    val borderColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
                        label = "patternCardBorder",
                    )
                    val titleColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        label = "patternTitleColor",
                    )
                    val captionColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.80f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "patternCaptionColor",
                    )
                    val iconTint by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "patternIconTint",
                    )
                    val scale by animateFloatAsState(
                        if (isSelected) 1.02f else 1f,
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "patternCardScale",
                    )
                    val glowColor = MaterialTheme.colorScheme.primary

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .scale(scale)
                            .clip(cardShape)
                            .background(bg, cardShape)
                            .border(
                                width = if (isSelected) 1.8.dp else 1.dp,
                                color = borderColor,
                                shape = cardShape,
                            )
                            .clickable(role = Role.Button) {
                                PixelHaptics.click(view)
                                onSelect(p)
                            }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .then(
                                        if (isSelected) {
                                            Modifier.drawBehind {
                                                drawCircle(
                                                    brush = Brush.radialGradient(
                                                        colors = listOf(
                                                            glowColor.copy(alpha = 0.36f),
                                                            glowColor.copy(alpha = 0.10f),
                                                            Color.Transparent,
                                                        ),
                                                        center = center,
                                                        radius = size.minDimension * 0.72f,
                                                    )
                                                )
                                            }
                                        } else Modifier
                                    )
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = patternStudioIcon(p),
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = stringResource(p.labelRes),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            text = patternStudioCaption(p),
                            style = MaterialTheme.typography.bodySmall,
                            color = captionColor,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (rowPatterns.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LedSwatch(
    color: Int,
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val scale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "ledSwatch",
    )
    Box(
        modifier
            .scale(scale)
            .aspectRatio(1f)
            .background(Color(color), CircleShape)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                CircleShape,
            )
            .semantics { contentDescription = label }
            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                PixelHaptics.click(view)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {}
}

/** 3-segment direction selector for moving patterns (Forward, Reverse, Bilateral). */
@Composable
fun DirectionSelector(
    selected: Direction,
    onSelect: (Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption(stringResource(R.string.direction_label))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Direction.entries.forEach { dir ->
                val isSelected = dir == selected
                val bg by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "dirBg",
                )
                val fg by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "dirFg",
                )
                val icon = when (dir) {
                    Direction.FORWARD -> "→"
                    Direction.REVERSE -> "←"
                    Direction.BILATERAL -> "↔"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(bg, CircleShape)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            PixelHaptics.click(view)
                            onSelect(dir)
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$icon ${stringResource(dir.labelRes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = fg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}


