package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.model.LiveAgentState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GeminiOrbVisualizer(
    agentState: LiveAgentState,
    geminiAmplitude: Float,
    userAmplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbAnimation")

    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulsePhase"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingScale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbRotation"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = minOf(size.width, size.height) * 0.28f

            // Adjust radius based on active amplitude
            val activeAmp = when (agentState) {
                LiveAgentState.SPEAKING -> geminiAmplitude.coerceIn(0f, 1f)
                LiveAgentState.LISTENING -> userAmplitude.coerceIn(0f, 1f)
                LiveAgentState.THINKING -> 0.35f
                LiveAgentState.IDLE -> 0.05f
            }

            val dynamicRadius = baseRadius * breathingScale * (1f + activeAmp * 0.45f)

            // Outer reactive ripples
            val rippleCount = 3
            for (i in 1..rippleCount) {
                val ripplePhaseOffset = (pulsePhase + i * 120f) % 360f
                val rippleProgress = ripplePhaseOffset / 360f
                val rippleRadius = dynamicRadius + rippleProgress * (baseRadius * 0.7f)
                val rippleAlpha = ((1f - rippleProgress) * 0.35f * (0.3f + activeAmp * 0.7f)).coerceIn(0f, 1f)

                val rippleColor = when (agentState) {
                    LiveAgentState.SPEAKING -> Color(0xFF8B5CF6).copy(alpha = rippleAlpha) // Electric violet
                    LiveAgentState.LISTENING -> Color(0xFF06B6D4).copy(alpha = rippleAlpha) // Bright cyan
                    LiveAgentState.THINKING -> Color(0xFF3B82F6).copy(alpha = rippleAlpha) // Blue
                    LiveAgentState.IDLE -> Color(0xFF6366F1).copy(alpha = rippleAlpha * 0.5f)
                }

                drawCircle(
                    color = rippleColor,
                    radius = rippleRadius,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // Glow Aura layer
            val auraColorStops = when (agentState) {
                LiveAgentState.SPEAKING -> arrayOf(
                    0.0f to Color(0xFFC084FC).copy(alpha = 0.85f),
                    0.4f to Color(0xFF818CF8).copy(alpha = 0.55f),
                    0.8f to Color(0xFF06B6D4).copy(alpha = 0.25f),
                    1.0f to Color.Transparent
                )
                LiveAgentState.LISTENING -> arrayOf(
                    0.0f to Color(0xFF38BDF8).copy(alpha = 0.85f),
                    0.4f to Color(0xFF06B6D4).copy(alpha = 0.6f),
                    0.8f to Color(0xFF3B82F6).copy(alpha = 0.25f),
                    1.0f to Color.Transparent
                )
                LiveAgentState.THINKING -> arrayOf(
                    0.0f to Color(0xFFA855F7).copy(alpha = 0.8f),
                    0.4f to Color(0xFFEC4899).copy(alpha = 0.55f),
                    0.8f to Color(0xFF6366F1).copy(alpha = 0.2f),
                    1.0f to Color.Transparent
                )
                LiveAgentState.IDLE -> arrayOf(
                    0.0f to Color(0xFF6366F1).copy(alpha = 0.6f),
                    0.5f to Color(0xFF4338CA).copy(alpha = 0.3f),
                    1.0f to Color.Transparent
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = auraColorStops,
                    center = center,
                    radius = dynamicRadius * 1.5f
                ),
                radius = dynamicRadius * 1.5f,
                center = center
            )

            // Dynamic organic harmonic satellite nodes
            val nodeCount = 6
            val radAngle = Math.toRadians(rotationAngle.toDouble())
            for (j in 0 until nodeCount) {
                val angleOffset = radAngle + (j * 2 * Math.PI / nodeCount)
                val nodeDistance = dynamicRadius * 0.75f + (sin(angleOffset * 2 + radAngle) * (dynamicRadius * 0.2f)).toFloat()
                val nodeX = center.x + (cos(angleOffset) * nodeDistance).toFloat()
                val nodeY = center.y + (sin(angleOffset) * nodeDistance).toFloat()

                val nodeRadius = (baseRadius * 0.22f) * (0.8f + activeAmp * 0.5f)
                val nodeColor = when (j % 3) {
                    0 -> Color(0xFF06B6D4).copy(alpha = 0.7f) // Cyan
                    1 -> Color(0xFFA855F7).copy(alpha = 0.75f) // Violet
                    else -> Color(0xFF3B82F6).copy(alpha = 0.65f) // Electric blue
                }

                drawCircle(
                    color = nodeColor,
                    radius = nodeRadius,
                    center = Offset(nodeX, nodeY)
                )
            }

            // Core nucleus with radiant gradient
            val coreGradient = Brush.radialGradient(
                colors = when (agentState) {
                    LiveAgentState.SPEAKING -> listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFE9D5FF),
                        Color(0xFFA855F7),
                        Color(0xFF4F46E5)
                    )
                    LiveAgentState.LISTENING -> listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFE0F2FE),
                        Color(0xFF38BDF8),
                        Color(0xFF0284C7)
                    )
                    LiveAgentState.THINKING -> listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFFCE7F3),
                        Color(0xFFF472B6),
                        Color(0xFF9333EA)
                    )
                    LiveAgentState.IDLE -> listOf(
                        Color(0xFFE0E7FF),
                        Color(0xFF818CF8),
                        Color(0xFF4F46E5),
                        Color(0xFF1E1B4B)
                    )
                },
                center = center,
                radius = dynamicRadius * 0.85f
            )

            drawCircle(
                brush = coreGradient,
                radius = dynamicRadius * 0.75f,
                center = center
            )
        }
    }
}
