package com.hilight.studio

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
        val action = intent?.action
        if (action != null &&
            action != android.service.quicksettings.TileService.ACTION_QS_TILE_PREFERENCES &&
            action != android.content.Intent.ACTION_MAIN
        ) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.68f)
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply { blurBehindRadius = 75 }

        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)

        val store = Store.get(this)

        setContent {
            val active by store.flashlightActive.collectAsStateWithLifecycle()
            val color by store.flashlightColor.collectAsStateWithLifecycle()
            val intensity by store.flashlightBrightness.collectAsStateWithLifecycle()
            val dynamic by store.dynamicColor.collectAsStateWithLifecycle()
            val view = LocalView.current

            val currentPercentage = if (active) (intensity * 100).roundToInt() else 0

            HiLightTheme(dynamicColor = dynamic) {
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
                                    text = stringResource(R.string.flashlight_dialog_title),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.semantics { heading() },
                                )

                                // Live Percentage Pill Badge (Material colors)
                                Text(
                                    text = if (active && currentPercentage > 0) {
                                        stringResource(R.string.flashlight_level_percent, currentPercentage)
                                    } else {
                                        stringResource(R.string.flashlight_level_off)
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (active && currentPercentage > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
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
                                    onValueChange = { v -> store.applyFlashlightLevel(v) },
                                    color = color
                                )
                            }

                            // 3. Colour wheel and white / temperature controls, shared with the Live tab
                            FlashlightColorControls(store = store, wheelSize = 190.dp)

                            // 4. Bottom Action Buttons (1-for-1 Pixel 11 Reproduction)
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
                                        text = stringResource(R.string.flashlight_turn_off),
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
                                        text = stringResource(R.string.flashlight_done),
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
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}
