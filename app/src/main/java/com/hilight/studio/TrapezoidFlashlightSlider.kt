package com.hilight.studio

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Vertical flashlight slider featuring dynamic expanding light-beam geometry,
 * stepped tactile feedback, and Material You dynamic color integration.
 */
@Composable
fun TrapezoidFlashlightSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Int,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var lastStep by remember { mutableIntStateOf((value * 10).roundToInt()) }

    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dynamicBeam"
    )

    fun updateValue(raw: Float) {
        val clamped = raw.coerceIn(0f, 1f)
        val step = (clamped * 10).roundToInt()
        if (step != lastStep) {
            lastStep = step
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onValueChange(clamped)
    }

    Box(
        modifier = modifier
            .width(115.dp)
            .height(175.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val effectiveH = size.height.toFloat() - 38.dp.toPx()
                    val touchY = change.position.y - 4.dp.toPx()
                    updateValue(1f - (touchY / effectiveH))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val effectiveH = size.height.toFloat() - 38.dp.toPx()
                    val touchY = offset.y - 4.dp.toPx()
                    updateValue(1f - (touchY / effectiveH))
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Canvas for expanding beam and level bar
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp)
        ) {
            val w = size.width
            val h = size.height
            val bottomW = w * 0.20f
            val maxTopW = w * 0.82f

            if (animatedValue > 0.005f) {
                val barY = h * (1f - animatedValue)
                val curTopW = bottomW + (maxTopW - bottomW) * animatedValue
                val baseTint = Color(color)

                // 1. Dynamic light beam path that expands from torch base to current bar height
                val dynamicBeamPath = Path().apply {
                    moveTo((w - bottomW) / 2f, h)
                    lineTo((w + bottomW) / 2f, h)
                    lineTo((w + curTopW) / 2f, barY)
                    quadraticTo(w / 2f, barY - 3.dp.toPx() * animatedValue, (w - curTopW) / 2f, barY)
                    close()
                }

                // 2. Luminous beam glow gradient
                val beamGradient = Brush.verticalGradient(
                    colors = listOf(
                        baseTint.copy(alpha = 0.35f),
                        baseTint.copy(alpha = 0.70f),
                        baseTint.copy(alpha = 0.95f)
                    ),
                    startY = barY,
                    endY = h
                )

                // Draw expanding light beam
                drawPath(
                    path = dynamicBeamPath,
                    brush = beamGradient
                )

                // 3. Glowing edge halo
                drawPath(
                    path = dynamicBeamPath,
                    color = Color.White.copy(alpha = 0.22f * animatedValue),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                // 4. Horizontal Level Bar across top of beam
                val barLeft = (w - curTopW) / 2f
                val barWidth = curTopW
                val barThickness = 3.5.dp.toPx()

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(barLeft, barY - barThickness / 2f),
                    size = Size(barWidth, barThickness),
                    cornerRadius = CornerRadius(barThickness / 2f, barThickness / 2f)
                )
            } else {
                // Resting bar right above torch
                val barThickness = 3.5.dp.toPx()
                val restingW = bottomW * 1.3f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset((w - restingW) / 2f, h - barThickness),
                    size = Size(restingW, barThickness),
                    cornerRadius = CornerRadius(barThickness / 2f, barThickness / 2f)
                )
            }
        }

        // Clean Flashlight Torch Icon (30dp, strictly uses user's dynamic Material You primary wallpaper theme)
        Icon(
            painter = painterResource(R.drawable.ic_flashlight_torch),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(bottom = 0.dp)
                .size(30.dp)
        )
    }
}
