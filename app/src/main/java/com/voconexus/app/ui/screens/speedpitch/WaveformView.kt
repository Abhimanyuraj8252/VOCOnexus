package com.voconexus.app.ui.screens.speedpitch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * Premium animated waveform visualization drawn via Canvas.
 * Shows amplitude bars with gradient coloring and A/B trim markers.
 */
@Composable
fun WaveformView(
    waveform: FloatArray?,
    modifier: Modifier = Modifier,
    trimStartFraction: Float = 0f,   // 0.0 – 1.0
    trimEndFraction: Float = 1f,
    playbackFraction: Float = 0f,
    barColor: Color = MaterialTheme.colorScheme.primary,
    dimColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
    accentColor: Color = MaterialTheme.colorScheme.secondary,
    barWidthFraction: Float = 0.6f,   // bar vs gap ratio
    cornerRadius: Dp = 2.dp
) {
    val animatedPlayback by animateFloatAsState(
        targetValue = playbackFraction,
        animationSpec = spring(),
        label = "playback"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bars = waveform ?: FloatArray(80) { 0.1f + (it % 7) * 0.06f }
            val barCount = bars.size
            val totalWidth = size.width
            val totalHeight = size.height
            val barTotalWidth = totalWidth / barCount
            val barWidth = barTotalWidth * barWidthFraction
            val gap = barTotalWidth * (1 - barWidthFraction)
            val midY = totalHeight / 2f
            val radiusPx = cornerRadius.toPx()

            // Gradient brushes
            val activeGradient = Brush.verticalGradient(
                colors = listOf(accentColor.copy(alpha = 0.9f), barColor, barColor.copy(alpha = 0.7f)),
                startY = 0f,
                endY = totalHeight
            )
            val dimBrush = SolidColor(dimColor)
            val playGradient = Brush.verticalGradient(
                colors = listOf(accentColor, accentColor.copy(alpha = 0.5f)),
                startY = 0f, endY = totalHeight
            )

            bars.forEachIndexed { i, amp ->
                val fraction = i.toFloat() / barCount
                val barHeight = (amp * totalHeight * 0.85f).coerceAtLeast(4f)
                val left = i * barTotalWidth + gap / 2
                val top = midY - barHeight / 2
                val right = left + barWidth
                val bottom = midY + barHeight / 2

                val brush = when {
                    fraction < trimStartFraction || fraction > trimEndFraction -> dimBrush
                    fraction <= animatedPlayback -> playGradient
                    else -> activeGradient
                }

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx)
                )
            }

            // Trim start marker
            if (trimStartFraction > 0f) {
                val x = trimStartFraction * totalWidth
                drawLine(
                    color = Color(0xFF10B981),
                    start = Offset(x, 0f),
                    end = Offset(x, totalHeight),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
            }
            // Trim end marker
            if (trimEndFraction < 1f) {
                val x = trimEndFraction * totalWidth
                drawLine(
                    color = Color(0xFFF87171),
                    start = Offset(x, 0f),
                    end = Offset(x, totalHeight),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
            }
            // Playback cursor
            if (animatedPlayback > 0f && animatedPlayback < 1f) {
                val x = animatedPlayback * totalWidth
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = Offset(x, 0f),
                    end = Offset(x, totalHeight),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

/** Skeleton waveform placeholder while loading */
@Composable
fun WaveformSkeleton(modifier: Modifier = Modifier) {
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bars = 80
            val barTotalWidth = size.width / bars
            val barWidth = barTotalWidth * 0.6f
            val midY = size.height / 2f
            val heights = floatArrayOf(0.3f, 0.5f, 0.4f, 0.7f, 0.6f, 0.4f, 0.3f, 0.5f)
            for (i in 0 until bars) {
                val amp = heights[i % heights.size]
                val barH = amp * size.height * 0.7f
                val left = i * barTotalWidth + barTotalWidth * 0.2f
                drawRoundRect(
                    color = dimColor,
                    topLeft = Offset(left, midY - barH / 2),
                    size = androidx.compose.ui.geometry.Size(barWidth, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                )
            }
        }
    }
}
