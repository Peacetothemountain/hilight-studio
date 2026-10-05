package com.hilight.studio

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Pixel 11 1-for-1 Trapezoid Flashlight Strength Slider:
 * Implements Google's exact AOSP SystemUI specs (Specs.TRACK_LENGTH=140dp,
 * MIN_TRACK_HEIGHT=22dp, MAX_TRACK_HEIGHT=80dp, THUMB_WIDTH=4dp),
 * with perceptual luminous beam diffusion, stepped tactile haptics,
 * and the iconic animated Pixel flashlight torch.
 */
@Composable
fun TrapezoidFlashlightSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Int,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var lastStep by remember { mutableIntStateOf((value * 10).roundToInt()) }

    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dynamicBeam"
    )

    fun updateValue(raw: Float) {
        val clamped = raw.coerceIn(0f, 1f)
        val step = (clamped * 10).roundToInt()
        if (step != lastStep) {
            lastStep = step
            PixelHaptics.tick(view)
        }
        onValueChange(clamped)
    }

    // Geometry constants from Google SystemUI Specs
    val trackLengthDp = 140.dp
    val minTrackWidthDp = 22.dp
    val maxTrackWidthDp = 80.dp
    val torchIconSizeDp = 34.dp
    val totalHeightDp = trackLengthDp + torchIconSizeDp + 16.dp
    val restingBarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
    val torchTintOff = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .width(maxTrackWidthDp + 40.dp)
            .height(totalHeightDp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val totalH = size.height.toFloat()
                    val trackPx = trackLengthDp.toPx()
                    val emitterY = totalH - torchIconSizeDp.toPx() - 4.dp.toPx()
                    val touchY = change.position.y
                    val fraction = ((emitterY - touchY) / trackPx).coerceIn(0f, 1f)
                    updateValue(fraction)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val totalH = size.height.toFloat()
                    val trackPx = trackLengthDp.toPx()
                    val emitterY = totalH - torchIconSizeDp.toPx() - 4.dp.toPx()
                    val touchY = offset.y
                    val fraction = ((emitterY - touchY) / trackPx).coerceIn(0f, 1f)
                    updateValue(fraction)
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Canvas for expanding beam and level bar
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = torchIconSizeDp + 2.dp)
        ) {
            val w = size.width
            val h = size.height
            val bottomW = minTrackWidthDp.toPx()
            val maxTopW = maxTrackWidthDp.toPx()
            val trackPx = trackLengthDp.toPx()

            val baseTint = Color(color)

            if (animatedValue > 0.005f) {
                val curTopY = h - (trackPx * animatedValue)
                val curTopW = bottomW + (maxTopW - bottomW) * animatedValue

                // 1. Expanding light cone path (Trapezoid from torch emitter to current thumb height)
                val beamPath = Path().apply {
                    moveTo((w - bottomW) / 2f, h)
                    lineTo((w + bottomW) / 2f, h)
                    lineTo((w + curTopW) / 2f, curTopY)
                    // Soft curvature across top edge
                    quadraticTo(w / 2f, curTopY - 2.dp.toPx() * animatedValue, (w - curTopW) / 2f, curTopY)
                    close()
                }

                // 2. Luminous beam vertical & radial gradient (dense glow at emitter, soft diffusion at thumb)
                val beamGradient = Brush.verticalGradient(
                    colors = listOf(
                        baseTint.copy(alpha = 0.35f * animatedValue),
                        baseTint.copy(alpha = 0.65f * animatedValue),
                        baseTint.copy(alpha = 0.90f * animatedValue),
                    ),
                    startY = curTopY,
                    endY = h
                )

                // Draw expanding light beam
                drawPath(
                    path = beamPath,
                    brush = beamGradient
                )

                // 3. Subtle edge halo
                drawPath(
                    path = beamPath,
                    color = Color.White.copy(alpha = 0.18f * animatedValue),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.2.dp.toPx()
                    )
                )

                // 4. Horizontal Level Bar (Thumb) across top of beam
                val thumbWidth = curTopW + 4.dp.toPx()
                val thumbHeight = 4.dp.toPx()

                // Glow behind thumb
                drawRoundRect(
                    color = baseTint.copy(alpha = 0.45f * animatedValue),
                    topLeft = Offset((w - thumbWidth) / 2f - 2.dp.toPx(), curTopY - thumbHeight / 2f - 1.dp.toPx()),
                    size = Size(thumbWidth + 4.dp.toPx(), thumbHeight + 2.dp.toPx()),
                    cornerRadius = CornerRadius(thumbHeight, thumbHeight)
                )

                // Crisp thumb capsule
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset((w - thumbWidth) / 2f, curTopY - thumbHeight / 2f),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(thumbHeight / 2f, thumbHeight / 2f)
                )
            } else {
                // Resting level bar resting just above the torch nozzle when off
                val thumbHeight = 3.5.dp.toPx()
                val restingW = bottomW * 1.25f
                drawRoundRect(
                    color = restingBarColor,
                    topLeft = Offset((w - restingW) / 2f, h - thumbHeight),
                    size = Size(restingW, thumbHeight),
                    cornerRadius = CornerRadius(thumbHeight / 2f, thumbHeight / 2f)
                )
            }
        }

        // Clean Google Pixel Flashlight Torch Icon (active vs off switch position)
        Icon(
            painter = painterResource(
                if (value > 0.01f) R.drawable.ic_flashlight_torch else R.drawable.ic_flashlight_torch_off
            ),
            contentDescription = "Flashlight Torch",
            tint = if (value > 0.01f) Color(color) else torchTintOff,
            modifier = Modifier
                .size(torchIconSizeDp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        val next = if (value > 0.01f) 0f else 1f
                        PixelHaptics.toggle(view, next > 0f)
                        onValueChange(next)
                    }
                )
        )
    }
}
