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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
    var speedInputText by remember(speed) { mutableStateOf(String.format(java.util.Locale.US, "%.4f", speed).trimEnd('0').trimEnd('.')) }
    var pitchInputText by remember(pitchSemitones) { mutableStateOf(String.format(java.util.Locale.US, "%.4f", pitchSemitones).trimEnd('0').trimEnd('.')) }
    var targetInputText by remember(targetDurationMs) {
        mutableStateOf(if (targetDurationMs > 0L) Formatters.formatDurationMs(targetDurationMs) else "")
    }

    Card(
        modifier = modifier.fillMaxWidth().imePadding(),
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

            // 1. TTS Generation Speed Section (Slider + High Precision Manual Decimal Input)
            Text("TTS Generation Speed (Manual Decimal Input)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = speedInputText,
                    onValueChange = { input ->
                        speedInputText = input
                        input.toFloatOrNull()?.let { parsed ->
                            if (parsed > 0.05f) onSpeedChange(parsed)
                        }
                    },
                    modifier = Modifier.width(130.dp),
                    label = { Text("Speed (x)") },
                    singleLine = true
                )
                Text(
                    text = if (speed < 0.75f || speed > 1.75f) "Custom Decimal Speed" else "Recommended (0.75x–1.75x)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = speed.coerceIn(0.2f, 3.0f),
                onValueChange = {
                    val rounded = Math.round(it * 100) / 100.0f
                    onSpeedChange(rounded)
                },
                valueRange = 0.2f..3.0f,
                modifier = Modifier.fillMaxWidth()
            )

            // Speed Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.1615f).forEach { preset ->
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

            // 2. Pitch Shift Section (Slider + High Precision Manual Decimal Input)
            Text("Generated Pitch (Manual Decimal Input)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = pitchInputText,
                    onValueChange = { input ->
                        pitchInputText = input
                        input.toFloatOrNull()?.let { parsed ->
                            onPitchChange(parsed)
                        }
                    },
                    modifier = Modifier.width(130.dp),
                    label = { Text("Pitch (st)") },
                    singleLine = true
                )
                Text(
                    text = "Recommended (-4.0 to +4.0 st)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = pitchSemitones.coerceIn(-12.0f, 12.0f),
                onValueChange = {
                    val rounded = Math.round(it * 10) / 10.0f
                    onPitchChange(rounded)
                },
                valueRange = -12.0f..12.0f,
                modifier = Modifier.fillMaxWidth()
            )

            // Pitch Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(-4.0f, -2.0f, 0.0f, 0.82f, 2.0f, 4.0f).forEach { preset ->
                    AssistChip(
                        onClick = { onPitchChange(preset) },
                        label = { Text("%+.2f st".format(preset)) }
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

            // 3. Target Duration Section with Hours, Minutes, Seconds Picker Dialog
            var showDurationDialog by remember { mutableStateOf(false) }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Script Natural Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = formatDurationHms(estimatedDurationMs),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Target Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (targetDurationMs > 0L) formatDurationHms(targetDurationMs) else "Not Set",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (targetDurationMs > 0L) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showDurationDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Target Duration (hr, min, sec)")
                        }

                        if (targetDurationMs > 0L) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    onTargetDurationChange(0L)
                                    onSpeedChange(1.0f)
                                    onPitchChange(0.0f)
                                }
                            ) {
                                Text("Reset")
                            }
                        }
                    }

                    if (estimatedDurationMs > 0L && targetDurationMs > 0L) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val ratio = estimatedDurationMs.toFloat() / targetDurationMs.toFloat()
                        Text(
                            text = "Auto Calculated Speed: %.4fx (Pitch adjusted naturally)".format(java.util.Locale.US, ratio),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (showDurationDialog) {
                var hrText by remember { mutableStateOf((targetDurationMs / 3600000L).toString()) }
                var minText by remember { mutableStateOf(((targetDurationMs % 3600000L) / 60000L).toString()) }
                var secText by remember { mutableStateOf(((targetDurationMs % 60000L) / 1000L).toString()) }

                androidx.compose.ui.window.Dialog(onDismissRequest = { showDurationDialog = false }) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Set Target Duration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Specify exact hours, minutes, and seconds for script playback.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = hrText,
                                    onValueChange = { hrText = it },
                                    label = { Text("Hours") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = minText,
                                    onValueChange = { minText = it },
                                    label = { Text("Minutes") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = secText,
                                    onValueChange = { secText = it },
                                    label = { Text("Seconds") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                androidx.compose.material3.TextButton(onClick = { showDurationDialog = false }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val hrs = hrText.toLongOrNull() ?: 0L
                                        val mins = minText.toLongOrNull() ?: 0L
                                        val secs = secText.toLongOrNull() ?: 0L
                                        val totalMs = (hrs * 3600 + mins * 60 + secs) * 1000L
                                        onTargetDurationChange(totalMs)
                                        if (totalMs > 0L && estimatedDurationMs > 0L) {
                                            val autoSpeed = estimatedDurationMs.toFloat() / totalMs.toFloat()
                                            val autoPitchCompensation = (autoSpeed - 1.0f) * 0.8f
                                            onSpeedChange(Math.round(autoSpeed * 10000) / 10000.0f)
                                            onPitchChange(Math.round(autoPitchCompensation * 100) / 100.0f)
                                        }
                                        showDurationDialog = false
                                    }
                                ) {
                                    Text("Apply Target Duration")
                                }
                            }
                        }
                    }
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

private fun formatDurationHms(durationMs: Long): String {
    if (durationMs <= 0L) return "00h 00m 00s"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02dh %02dm %02ds".format(hours, minutes, seconds)
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
