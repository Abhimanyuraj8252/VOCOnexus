package com.voconexus.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Card(
        modifier = modifier.fillMaxWidth().imePadding(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            // === HEADER WITH NATURALNESS BADGE ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Speech Engine Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Speed, Pitch & Time Target Tuning", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                NaturalnessBadge(level = naturalnessLevel)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            // === 1. SPEED CONTROL SECTION ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generation Speed Multiplier", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = speedInputText,
                    onValueChange = { input ->
                        speedInputText = input
                        input.toFloatOrNull()?.let { parsed ->
                            if (parsed > 0.05f) onSpeedChange(parsed)
                        }
                    },
                    suffix = { Text("x", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.1615f).forEach { preset ->
                    val isSelected = Math.abs(speed - preset) < 0.02f
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSpeedChange(preset) },
                        label = { Text("${preset}x", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            speedWarning?.let { warning ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            // === 2. PITCH SHIFT SECTION ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pitch Shift (Semitones)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = pitchInputText,
                    onValueChange = { input ->
                        pitchInputText = input
                        input.toFloatOrNull()?.let { parsed -> onPitchChange(parsed) }
                    },
                    suffix = { Text("st", fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6)) },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        focusedContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.08f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Slider(
                value = pitchSemitones.coerceIn(-12.0f, 12.0f),
                onValueChange = {
                    val rounded = Math.round(it * 10) / 10.0f
                    onPitchChange(rounded)
                },
                valueRange = -12.0f..12.0f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF8B5CF6),
                    activeTrackColor = Color(0xFF8B5CF6)
                )
            )

            // Pitch Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(-4.0f, -2.0f, 0.0f, 0.82f, 2.0f, 4.0f).forEach { preset ->
                    val isSelected = Math.abs(pitchSemitones - preset) < 0.05f
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPitchChange(preset) },
                        label = { Text("%+.1f st".format(java.util.Locale.US, preset), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8B5CF6),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            pitchWarning?.let { warning ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            // === 3. TARGET DURATION SECTION ===
            var showDurationDialog by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Estimated Script Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                color = if (targetDurationMs > 0L) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Set Target (HH:MM:SS)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        if (targetDurationMs > 0L) {
                            OutlinedButton(
                                onClick = {
                                    onTargetDurationChange(0L)
                                    onSpeedChange(1.0f)
                                    onPitchChange(0.0f)
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reset")
                            }
                        }
                    }

                    if (estimatedDurationMs > 0L && targetDurationMs > 0L) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val ratio = estimatedDurationMs.toFloat() / targetDurationMs.toFloat()
                        Text(
                            text = "⚡ Auto Calculated Speed: %.4fx (Pitch compensation applied)".format(java.util.Locale.US, ratio),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
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
                                TextButton(onClick = { showDurationDialog = false }) {
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
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Apply Duration")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // === PREVIEW ACTION BUTTON ===
            Button(
                onClick = { if (isPreviewPlaying) onStopPreviewClick() else onPreviewClick() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPreviewPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPreviewPlaying) "Stop Audio Preview" else "Play Audio Preview",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun NaturalnessBadge(level: NaturalnessLevel) {
    val (label, color) = when (level) {
        NaturalnessLevel.HIGH -> Pair("Naturalness: High", Color(0xFF10B981))
        NaturalnessLevel.MODERATE -> Pair("Naturalness: Moderate", Color(0xFFF59E0B))
        NaturalnessLevel.LOW -> Pair("Naturalness: Low", Color(0xFFEF4444))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
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
