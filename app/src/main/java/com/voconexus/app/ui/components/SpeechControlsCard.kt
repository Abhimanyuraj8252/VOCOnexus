package com.voconexus.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voconexus.app.core.domain.speech.NaturalnessLevel
import com.voconexus.app.core.util.Formatters

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeechControlsCard(
    speed: Float,
    pitchSemitones: Float,
    targetDurationMs: Long,
    estimatedDurationMs: Long,
    naturalnessLevel: NaturalnessLevel,
    speedWarning: String?,
    pitchWarning: String?,
    isPreviewPlaying: Boolean,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onTargetDurationChange: (Long) -> Unit,
    onPreviewClick: () -> Unit,
    onStopPreviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with Naturalness Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Speech Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                NaturalnessBadge(level = naturalnessLevel)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. TTS Generation Speed Section
            Text("TTS Generation Speed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(String.format("%.2fx", speed), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(if (speed < 0.75f || speed > 1.75f) "Extreme Speed" else "Recommended Range (0.75x–1.75x)", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = speed,
                onValueChange = { onSpeedChange((Math.round(it * 20) / 20.0f)) },
                valueRange = 0.5f..2.5f,
                steps = 39,
                modifier = Modifier.fillMaxWidth()
            )
            // Speed Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { preset ->
                    AssistChip(
                        onClick = { onSpeedChange(preset) },
                        label = { Text("${preset}x") }
                    )
                }
            }

            speedWarning?.let { warning ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Pitch Shift Section
            Text("Generated Pitch (Semitones)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(String.format("%+.1f st", pitchSemitones), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("Recommended (-4.0 to +4.0 st)", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = pitchSemitones,
                onValueChange = { onPitchChange((Math.round(it * 2) / 2.0f)) },
                valueRange = -12.0f..12.0f,
                steps = 47,
                modifier = Modifier.fillMaxWidth()
            )
            // Pitch Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(-4.0f, -2.0f, 0.0f, 2.0f, 4.0f).forEach { preset ->
                    AssistChip(
                        onClick = { onPitchChange(preset) },
                        label = { Text("%+.0f st".format(preset)) }
                    )
                }
            }

            pitchWarning?.let { warning ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Target Duration Section
            Text("Target Duration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            var targetInputText by remember(targetDurationMs) {
                mutableStateOf(if (targetDurationMs > 0L) Formatters.formatDurationMs(targetDurationMs) else "")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = targetInputText,
                    onValueChange = { input ->
                        targetInputText = input
                        val parsedMs = parseDurationStringToMs(input)
                        onTargetDurationChange(parsedMs)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("01:30:00 (HH:MM:SS) or Off") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) }
                )

                if (targetDurationMs > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        targetInputText = ""
                        onTargetDurationChange(0L)
                    }) {
                        Text("Reset")
                    }
                }
            }

            if (estimatedDurationMs > 0L && targetDurationMs > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                val ratio = targetDurationMs.toFloat() / estimatedDurationMs.toFloat()
                val compressionPct = ((1.0f - ratio) * 100).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Estimated: ${Formatters.formatDurationMs(estimatedDurationMs)}", style = MaterialTheme.typography.bodySmall)
                    Text("Target: ${Formatters.formatDurationMs(targetDurationMs)}", style = MaterialTheme.typography.bodySmall)
                    Text("Ratio: ≈ ${String.format("%.2f", ratio)}x ($compressionPct%)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preview Action Button
            Button(
                onClick = { if (isPreviewPlaying) onStopPreviewClick() else onPreviewClick() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPreviewPlaying) "Stop Speech Preview" else "Preview Speech Settings (Short Sample)")
            }
        }
    }
}

@Composable
private fun NaturalnessBadge(level: NaturalnessLevel) {
    val (label, containerColor, textColor) = when (level) {
        NaturalnessLevel.HIGH -> Triple("Naturalness: High", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        NaturalnessLevel.MODERATE -> Triple("Naturalness: Moderate", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        NaturalnessLevel.LOW -> Triple("Naturalness: Low", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }

    Box(
        modifier = Modifier
            .background(containerColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor, fontWeight = FontWeight.Bold)
    }
}

private fun parseDurationStringToMs(input: String): Long {
    val parts = input.split(":").mapNotNull { it.trim().toLongOrNull() }
    return when (parts.size) {
        3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
        2 -> (parts[0] * 60 + parts[1]) * 1000L
        1 -> parts[0] * 1000L
        else -> 0L
    }
}
