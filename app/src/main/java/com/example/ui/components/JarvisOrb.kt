package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.RoseListening
import com.example.ui.theme.SapphireCore
import com.example.ui.theme.SolarGold
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

enum class OrbVisualMode {
    STANDBY,
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun JarvisOrb(
    mode: OrbVisualMode,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 216.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_hud_orb")

    val rotationOuter by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    OrbVisualMode.THINKING -> 1800
                    OrbVisualMode.LISTENING -> 3400
                    OrbVisualMode.SPEAKING -> 3000
                    OrbVisualMode.IDLE -> 8500
                    OrbVisualMode.STANDBY -> 18000
                },
                easing = LinearEasing
            )
        ),
        label = "orb_rot_outer"
    )

    val rotationInner by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    OrbVisualMode.THINKING -> 1400
                    OrbVisualMode.LISTENING -> 2600
                    OrbVisualMode.SPEAKING -> 2400
                    OrbVisualMode.IDLE -> 6500
                    OrbVisualMode.STANDBY -> 14000
                },
                easing = LinearEasing
            )
        ),
        label = "orb_rot_inner"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = if (mode == OrbVisualMode.STANDBY) 0.96f else 0.93f,
        targetValue = if (mode == OrbVisualMode.STANDBY) 1.02f else 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    OrbVisualMode.STANDBY -> 3000
                    OrbVisualMode.IDLE -> 1900
                    else -> 800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val smoothRms by animateFloatAsState(
        targetValue = rmsLevel.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 450f),
        label = "orb_rms"
    )

    val primaryColor = when (mode) {
        OrbVisualMode.STANDBY -> SapphireCore.copy(alpha = 0.45f)
        OrbVisualMode.IDLE -> SolarGold
        OrbVisualMode.LISTENING -> RoseListening
        OrbVisualMode.THINKING -> SapphireCore
        OrbVisualMode.SPEAKING -> EmeraldReady
    }
    val secondaryColor = when (mode) {
        OrbVisualMode.STANDBY -> GlassBorder
        OrbVisualMode.IDLE -> SapphireCore
        OrbVisualMode.LISTENING -> SolarGold
        OrbVisualMode.THINKING -> SolarGold
        OrbVisualMode.SPEAKING -> SolarGold
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = primaryColor),
                onClick = onClick
            )
            .testTag("jarvis_voice_orb")
            .semantics {
                contentDescription = when (mode) {
                    OrbVisualMode.STANDBY -> "Jarvis is off, tap to wake and speak"
                    OrbVisualMode.LISTENING -> "Tap again to stop talking"
                    OrbVisualMode.SPEAKING -> "Tap to stop Jarvis speaking"
                    else -> "Tap once to speak to Jarvis"
                }
            }
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.minDimension / 2f
            val dynamicBoost = if (mode == OrbVisualMode.LISTENING) smoothRms * 0.16f else 0f
            val coreR = maxR * (0.43f * pulse + dynamicBoost)

            // Outer ambient radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (mode == OrbVisualMode.STANDBY) 0.14f else 0.34f),
                        secondaryColor.copy(alpha = if (mode == OrbVisualMode.STANDBY) 0.04f else 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // 24 radial HUD tick marks reacting to microphone RMS
            val tickCount = 24
            val tickInnerBase = coreR * 1.38f
            for (i in 0 until tickCount) {
                val angleRad = Math.toRadians(i * (360.0 / tickCount))
                val waveFactor = if (mode == OrbVisualMode.LISTENING) {
                    0.35f + 0.65f * smoothRms * ((i % 3) + 1) / 3f
                } else if (mode == OrbVisualMode.SPEAKING) {
                    0.3f + 0.4f * (pulse - 0.9f) * 5f
                } else if (mode == OrbVisualMode.STANDBY) {
                    0.15f
                } else {
                    0.35f
                }
                val tickLen = maxR * 0.09f * waveFactor
                val startX = center.x + (tickInnerBase * cos(angleRad)).toFloat()
                val startY = center.y + (tickInnerBase * sin(angleRad)).toFloat()
                val endX = center.x + ((tickInnerBase + tickLen) * cos(angleRad)).toFloat()
                val endY = center.y + ((tickInnerBase + tickLen) * sin(angleRad)).toFloat()
                drawLine(
                    color = if (i % 2 == 0) primaryColor.copy(alpha = 0.55f) else secondaryColor.copy(alpha = 0.35f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (i % 4 == 0) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Segmented outer HUD arc reactor ring
            rotate(degrees = rotationOuter, pivot = center) {
                val arcR = coreR * 1.28f
                val arcBoxTopLeft = Offset(center.x - arcR, center.y - arcR)
                val arcBoxSize = Size(arcR * 2f, arcR * 2f)
                val arcStroke = Stroke(
                    width = if (mode == OrbVisualMode.STANDBY) 2.dp.toPx() else 3.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawArc(
                    color = primaryColor,
                    startAngle = 0f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = arcBoxTopLeft,
                    size = arcBoxSize,
                    style = arcStroke
                )
                drawArc(
                    color = secondaryColor,
                    startAngle = 180f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = arcBoxTopLeft,
                    size = arcBoxSize,
                    style = arcStroke
                )
            }

            // Counter-rotating inner precision ring
            rotate(degrees = rotationInner, pivot = center) {
                val innerArcR = coreR * 1.13f
                val topLeft = Offset(center.x - innerArcR, center.y - innerArcR)
                val boxSize = Size(innerArcR * 2f, innerArcR * 2f)
                drawArc(
                    color = secondaryColor.copy(alpha = if (mode == OrbVisualMode.STANDBY) 0.25f else 0.75f),
                    startAngle = 45f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = boxSize,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = primaryColor.copy(alpha = if (mode == OrbVisualMode.STANDBY) 0.25f else 0.75f),
                    startAngle = 225f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = boxSize,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Inner metallic dark glass core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (mode == OrbVisualMode.STANDBY) {
                        listOf(
                            Color(0xFF1A2336),
                            Color(0xFF0E1420),
                            Color(0xFF07090E)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.26f),
                            primaryColor.copy(alpha = 0.85f),
                            secondaryColor.copy(alpha = 0.78f),
                            Color(0xFF0A0F1A)
                        )
                    },
                    center = Offset(center.x - coreR * 0.22f, center.y - coreR * 0.22f),
                    radius = coreR * 1.15f
                ),
                radius = coreR,
                center = center
            )

            // Crisp core rim
            drawCircle(
                color = primaryColor.copy(alpha = if (mode == OrbVisualMode.STANDBY) 0.45f else 0.9f),
                radius = coreR,
                center = center,
                style = Stroke(width = 1.8.dp.toPx())
            )
        }

        Icon(
            imageVector = when (mode) {
                OrbVisualMode.STANDBY -> Icons.Rounded.PowerSettingsNew
                OrbVisualMode.LISTENING -> Icons.Rounded.Stop
                OrbVisualMode.SPEAKING, OrbVisualMode.THINKING -> Icons.Rounded.GraphicEq
                OrbVisualMode.IDLE -> Icons.Rounded.Mic
            },
            contentDescription = null,
            tint = if (mode == OrbVisualMode.STANDBY) TextSecondary else Color.White,
            modifier = Modifier.size(36.dp)
        )
    }
}
