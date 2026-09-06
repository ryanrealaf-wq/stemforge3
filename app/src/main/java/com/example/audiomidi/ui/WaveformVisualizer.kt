package com.example.audiomidi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Audio Waveform Visualizer.
 * Renders an overview envelope of audio samples with playhead synchronization.
 */
@Composable
fun WaveformVisualizer(
    samples: FloatArray,
    playbackRatio: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
) {
    val colorScheme = MaterialTheme.colorScheme

    // Downsample to ~120 peak buckets for smooth rendering
    val barCount = 96
    val peaks = remember(samples) {
        if (samples.isEmpty()) {
            FloatArray(barCount) { i -> (0.15f + 0.35f * abs(sin(i * 0.22f))).toFloat() }
        } else {
            val bucketSize = maxOf(1, samples.size / barCount)
            FloatArray(barCount) { b ->
                var maxVal = 0f
                val start = b * bucketSize
                val end = minOf(samples.size, start + bucketSize)
                for (i in start until end) {
                    val a = abs(samples[i])
                    if (a > maxVal) maxVal = a
                }
                maxVal.coerceIn(0.04f, 1.0f)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .border(1.dp, colorScheme.outline, RoundedCornerShape(10.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val barWidth = w / barCount
            val centerY = h / 2f

            for (i in 0 until barCount) {
                val peak = peaks[i]
                val barHeight = (peak * (h - 8f)).coerceAtLeast(3f)
                val x = i * barWidth + (barWidth * 0.15f)
                val barW = (barWidth * 0.70f).coerceAtLeast(1.5f)

                val ratio = i.toFloat() / barCount
                val isPlayed = ratio <= playbackRatio

                val color = if (isPlayed) {
                    activeColor
                } else {
                    inactiveColor
                }

                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = Size(barW, barHeight),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }

            // Playhead
            if (playbackRatio in 0f..1f) {
                val px = playbackRatio * w
                drawLine(
                    color = colorScheme.error,
                    start = Offset(px, 0f),
                    end = Offset(px, h),
                    strokeWidth = 2f
                )
            }
        }
    }
}
