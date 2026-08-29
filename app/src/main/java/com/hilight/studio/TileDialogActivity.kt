package com.hilight.studio

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/**
 * Floating preference dialog for Quick Settings tile long-press.
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

            val currentPercentage = if (active) (intensity * 100).roundToInt() else 0

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
                        .padding(horizontal = 16.dp, vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Material 3 Expressive Container Shell tinted with User's Material You Wallpaper Theme
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 10.dp,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {} // swallow clicks inside card
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Title & Live Percentage readout
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "HiLight Strength",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = if (active && currentPercentage > 0) "$currentPercentage%" else "Off",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (active && currentPercentage > 0) Color(color) else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Slider + Large Color wheel side-by-side
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(215.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
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

                                ColorSpectrumWheel(
                                    color = color,
                                    onColorChanged = { store.setFlashlightColor(it) },
                                    sizeDp = 200
                                )
                            }

                            // Bottom action buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        store.setFlashlight(false)
                                        finishAndRemoveTask()
                                    }
                                ) {
                                    Text(
                                        text = "Turn off",
                                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                TextButton(
                                    onClick = { finishAndRemoveTask() }
                                ) {
                                    Text(
                                        text = "Done",
                                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                                        color = MaterialTheme.colorScheme.primary
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
}
