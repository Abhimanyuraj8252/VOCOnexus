@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    com.google.accompanist.permissions.ExperimentalPermissionsApi::class
)

package com.voconexus.app.ui.screens.speedpitch

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.voconexus.app.core.tools.speedpitch.MediaType
import kotlin.math.abs

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SpeedPitchScreen(
    viewModel: SpeedPitchViewModel,
    onBackClick: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Permission state
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permState = rememberMultiplePermissionsState(permissions)

    // File picker — audio + video MIME types
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.onFileSelected(it) } }

    fun launchPicker() {
        if (permState.allPermissionsGranted) {
            filePicker.launch(arrayOf("audio/*", "video/*"))
        } else {
            permState.launchMultiplePermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Speed & Pitch",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Controller & Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.selectedUri != null) {
                        // Top Export Button
                        Button(
                            onClick = viewModel::onStartProcessing,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }

                        IconButton(onClick = viewModel::onClearFile) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear file", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
        ) {

            // ── 1. File Picker Card ──────────────────────────────────────────
            item {
                FilePickerCard(
                    state = state,
                    onPickFile = ::launchPicker
                )
            }

            // ── 2. Video / Audio Live Preview Player ─────────────────────────
            if (state.selectedUri != null && state.mediaInfo != null) {
                item {
                    MediaPreviewCard(
                        state = state,
                        viewModel = viewModel
                    )
                }

                // ── 3. Speed Control ─────────────────────────────────────────
                item {
                    SpeedCard(state = state, viewModel = viewModel)
                }

                // ── 4. Pitch Control ─────────────────────────────────────────
                item {
                    PitchCard(state = state, viewModel = viewModel)
                }

                // ── 5. Duration Target ───────────────────────────────────────
                item {
                    DurationTargetCard(state = state, viewModel = viewModel)
                }

                // ── 6. Trim ──────────────────────────────────────────────────
                item {
                    TrimCard(state = state, viewModel = viewModel)
                }

                // ── 7. Fade In/Out ───────────────────────────────────────────
                item {
                    FadeCard(state = state, viewModel = viewModel)
                }

                // ── 8. Volume ────────────────────────────────────────────────
                item {
                    VolumeCard(state = state, viewModel = viewModel)
                }

                // ── 9. Equalizer ─────────────────────────────────────────────
                item {
                    EqualizerCard(state = state, viewModel = viewModel)
                }

                // ── 10. Export Settings ──────────────────────────────────────
                item {
                    ExportSettingsCard(state = state, viewModel = viewModel)
                }

                // ── 11. Summary ──────────────────────────────────────────────
                item {
                    SummaryCard(state = state, viewModel = viewModel)
                }

                // ── 12. Bottom Export Button ─────────────────────────────────
                item {
                    ActionButtons(
                        state = state,
                        onProcess = viewModel::onStartProcessing,
                        onCancel = viewModel::onCancelProcessing
                    )
                }
            }
        }
    }

    // ── Processing Overlay ───────────────────────────────────────────────────
    if (state.isProcessing) {
        ProcessingDialog(progress = state.processingProgress, onCancel = viewModel::onCancelProcessing)
    }

    // ── Export Result Dialog ─────────────────────────────────────────────────
    state.exportResult?.let { result ->
        ExportSuccessDialog(result = result, onDismiss = viewModel::onDismissResult)
    }

    // ── Error Dialog ─────────────────────────────────────────────────────────
    state.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissResult,
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Processing Failed") },
            text = {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDismissResult) { Text("OK") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// File Picker Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FilePickerCard(state: SpeedPitchUiState, onPickFile: () -> Unit) {
    ToolCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.mediaInfo?.mediaType == MediaType.VIDEO) Icons.Default.VideoFile else Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (state.mediaInfo != null) {
                        Text(
                            text = state.mediaInfo.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TypeBadge(
                                text = when (state.mediaInfo.mediaType) {
                                    MediaType.AUDIO -> "AUDIO"
                                    MediaType.VIDEO -> "VIDEO"
                                    MediaType.NONE -> "FILE"
                                },
                                color = when (state.mediaInfo.mediaType) {
                                    MediaType.AUDIO -> MaterialTheme.colorScheme.primary
                                    MediaType.VIDEO -> MaterialTheme.colorScheme.tertiary
                                    MediaType.NONE -> MaterialTheme.colorScheme.secondary
                                }
                            )
                            Text(
                                text = formatDurationFull(state.mediaInfo.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = state.mediaInfo.containerFormat,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        Text(
                            text = "No file selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "MP3, AAC, OGG, FLAC, WAV, MP4, MKV, AVI…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onPickFile,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (state.mediaInfo == null) "Import" else "Change", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (state.isLoadingFile) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Media Preview Card (Video Editor Style Video/Audio Live Player)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MediaPreviewCard(
    state: SpeedPitchUiState,
    viewModel: SpeedPitchViewModel
) {
    val info = state.mediaInfo ?: return
    val speed = state.speedMultiplier.coerceIn(0.10f, 8.0f)
    val rawTrimDur = if (state.trimEndMs > 0) (state.trimEndMs - state.trimStartMs) else info.durationMs
    val sourceDur = rawTrimDur.coerceAtLeast(100L)
    val targetDurationMs = (sourceDur.toFloat() / speed).toLong().coerceAtLeast(100L)

    val relPos = (state.currentPositionMs - state.trimStartMs).coerceAtLeast(0L)
    val scaledCurrentMs = (relPos.toFloat() / speed).toLong().coerceIn(0L, targetDurationMs)

    val playbackFrac = if (info.durationMs > 0) (state.currentPositionMs.toFloat() / info.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val startFrac = if (info.durationMs > 0) state.trimStartMs.toFloat() / info.durationMs else 0f
    val endFrac = if (info.durationMs > 0 && state.trimEndMs > 0) state.trimEndMs.toFloat() / info.durationMs else 1f

    ToolCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (info.mediaType == MediaType.VIDEO) Icons.Default.Movie else Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Media Preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            // Live speed + target duration badge
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeBadge(
                    text = "⚡ ${String.format("%.3f", state.speedMultiplier)}x",
                    color = MaterialTheme.colorScheme.primary
                )
                TypeBadge(
                    text = "⏱ ${formatDurationFull(targetDurationMs)}",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Video Preview View ───────────────────────────────────────────────
        if (info.mediaType == MediaType.VIDEO) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                viewModel.player?.let { p ->
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = p
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // ── Audio Waveform Visualizer Surface ─────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                if (state.waveform != null) {
                    WaveformView(
                        waveform = state.waveform,
                        trimStartFraction = startFrac,
                        trimEndFraction = endFrac,
                        playbackFraction = playbackFrac,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                    )
                } else {
                    WaveformSkeleton(modifier = Modifier.fillMaxWidth().height(90.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Player Position Scrubber Slider (Scaled for Target Duration) ─────
        Slider(
            value = scaledCurrentMs.toFloat(),
            onValueChange = { scaledSeekMs ->
                val sourceSeekMs = state.trimStartMs + (scaledSeekMs * speed).toLong()
                viewModel.seekTo(sourceSeekMs)
            },
            valueRange = 0f..targetDurationMs.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Time Counters (Showing Scaled Current / Target Duration)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationFull(scaledCurrentMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatDurationFull(targetDurationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── Playback Controls Bar ────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rewind -10s
            IconButton(onClick = viewModel::rewind10s) {
                Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.width(16.dp))

            // Play / Pause Button
            FilledIconButton(
                onClick = viewModel::togglePlayPause,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            // Forward +10s
            IconButton(onClick = viewModel::forward10s) {
                Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Speed Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpeedCard(state: SpeedPitchUiState, viewModel: SpeedPitchViewModel) {
    val focusMgr = LocalFocusManager.current

    ToolCard {
        SectionHeader(icon = Icons.Default.Speed, title = "Speed", tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))

        // Big speed display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = String.format("%.3f", state.speedMultiplier),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "x",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
            )
        }

        // Output duration hint
        val outputMs = viewModel.computedOutputDurationMs()
        if (outputMs > 0 && state.speedMultiplier != 1.0f) {
            Text(
                text = "Output duration: ${formatDurationFull(outputMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))

        // Slider
        Slider(
            value = state.speedMultiplier.coerceIn(0.10f, 8.0f),
            onValueChange = viewModel::onSpeedSliderChanged,
            valueRange = 0.10f..8.0f,
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0.10x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("8.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))

        // Manual text input
        OutlinedTextField(
            value = state.speedText,
            onValueChange = viewModel::onSpeedTextChanged,
            label = { Text("Exact Speed (e.g. 1.533)") },
            trailingIcon = { Text("x", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusMgr.clearFocus() }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(Modifier.height(10.dp))

        // Preset chips
        val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 4.0f)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { preset ->
                val selected = abs(state.speedMultiplier - preset) < 0.001f
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.onPresetSpeed(preset) },
                    label = {
                        Text(
                            text = "${String.format("%.2f", preset).trimEnd('0').trimEnd('.')}x",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pitch Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PitchCard(state: SpeedPitchUiState, viewModel: SpeedPitchViewModel) {
    val focusMgr = LocalFocusManager.current

    ToolCard {
        SectionHeader(icon = Icons.Default.Tune, title = "Pitch Control", tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(12.dp))

        // Lock toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (state.isPitchLocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
                .clickable(onClick = viewModel::onPitchLockToggled)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (state.isPitchLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (state.isPitchLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.isPitchLocked) "Auto-Correct Pitch (Recommended)" else "Manual Pitch Control",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.isPitchLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (state.isPitchLocked) "Voice stays natural at any speed" else "Set custom semitone shift",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.isPitchLocked,
                onCheckedChange = { viewModel.onPitchLockToggled() },
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary)
            )
        }

        // Manual pitch controls (visible only when unlocked)
        AnimatedVisibility(visible = !state.isPitchLocked) {
            Column {
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = String.format("%.1f", state.pitchSemitones),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.secondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = " st",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Slider(
                    value = state.pitchSemitones.coerceIn(-24f, 24f),
                    onValueChange = viewModel::onPitchSliderChanged,
                    valueRange = -24f..24f,
                    steps = 47,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("-24 st", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("0 st", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("+24 st", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.pitchText,
                    onValueChange = viewModel::onPitchTextChanged,
                    label = { Text("Semitones (e.g. -3.5)") },
                    trailingIcon = { Text("st", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusMgr.clearFocus() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(Modifier.height(8.dp))
                // Quick semitone presets
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(-12f, -7f, -5f, -3f, -1f, 0f, 1f, 3f, 5f, 7f, 12f).forEach { st ->
                        val selected = abs(state.pitchSemitones - st) < 0.1f
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onPitchSliderChanged(st) },
                            label = {
                                Text(
                                    text = if (st == 0f) "0" else "${if (st > 0) "+" else ""}${st.toInt()}st",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Duration Target Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DurationTargetCard(state: SpeedPitchUiState, viewModel: SpeedPitchViewModel) {
    val focusMgr = LocalFocusManager.current

    ToolCard {
        // ── Playback Controls Bar ────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Set Trim A
            IconButton(
                onClick = viewModel::onSetTrimStartToCurrent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = "Set Trim Start", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
            }

            // Rewind -10s
            IconButton(onClick = viewModel::rewind10s) {
                Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Play / Pause Button
            FilledIconButton(
                onClick = viewModel::togglePlayPause,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(28.dp)
                )
            }

            // Forward +10s
            IconButton(onClick = viewModel::forward10s) {
                Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Set Trim B
            IconButton(
                onClick = viewModel::onSetTrimEndToCurrent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFF87171).copy(alpha = 0.15f))
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = "Set Trim End", tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader(icon = Icons.Default.Timer, title = "Target Duration", tint = MaterialTheme.colorScheme.tertiary)

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Enter desired output duration → speed auto-calculated",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        // HH : MM : SS fields
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DurationField(
                value = state.targetHours,
                label = "HH",
                onValueChange = viewModel::onTargetHoursChanged,
                max = 99,
                modifier = Modifier.weight(1f)
            )
            Text(":", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            DurationField(
                value = state.targetMinutes,
                label = "MM",
                onValueChange = viewModel::onTargetMinutesChanged,
                max = 59,
                modifier = Modifier.weight(1f)
            )
            Text(":", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            DurationField(
                value = state.targetSeconds,
                label = "SS",
                onValueChange = viewModel::onTargetSecondsChanged,
                max = 59,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                focusMgr.clearFocus()
                viewModel.onAutoCalculateSpeed()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Auto-Calculate Speed", fontWeight = FontWeight.SemiBold)
        }

        if (state.isDurationMode) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Speed set to ${String.format("%.3f", state.speedMultiplier)}x",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Trim Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TrimCard(state: SpeedPitchUiState, viewModel: SpeedPitchViewModel) {
    val info = state.mediaInfo ?: return
    val totalMs = info.durationMs.coerceAtLeast(1L)
    val endMs = if (state.trimEndMs < 0) totalMs else state.trimEndMs

    val (startH, startM, startS) = state.trimStartMs.toHms()
    val (endH, endM, endS) = endMs.toHms()

    ToolCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(icon = Icons.Default.ContentCut, title = "Trim / Cut & Clip", tint = Color(0xFFF59E0B))
            TextButton(onClick = viewModel::onResetTrim, contentPadding = PaddingValues(0.dp)) {
                Text("Reset All", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))

        // Visual Sliders
        LabeledSlider(
            label = "Start Position",
            value = state.trimStartMs.toFloat(),
            valueRange = 0f..totalMs.toFloat(),
            displayText = formatDurationFull(state.trimStartMs),
            color = Color(0xFF10B981),
            onValueChange = { viewModel.onTrimStartChanged(it.toLong()) }
        )

        Spacer(Modifier.height(6.dp))

        LabeledSlider(
            label = "End Position",
            value = endMs.toFloat(),
            valueRange = 0f..totalMs.toFloat(),
            displayText = formatDurationFull(endMs),
            color = Color(0xFFF87171),
            onValueChange = { viewModel.onTrimEndChanged(it.toLong()) }
        )

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))

        // ── Manual Start Time (HH : MM : SS) ──────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(Modifier.width(6.dp))
                Text("Start Time (HH : MM : SS)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            }

            TextButton(
                onClick = viewModel::onSetTrimStartToCurrent,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF10B981))
                Spacer(Modifier.width(4.dp))
                Text("Current Position", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DurationField(
                value = startH,
                label = "HH",
                onValueChange = { h ->
                    val newMs = ((h * 3600L) + (startM * 60L) + startS) * 1000L
                    viewModel.onTrimStartChanged(newMs)
                },
                max = 99,
                modifier = Modifier.weight(1f)
            )
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            DurationField(
                value = startM,
                label = "MM",
                onValueChange = { m ->
                    val newMs = ((startH * 3600L) + (m * 60L) + startS) * 1000L
                    viewModel.onTrimStartChanged(newMs)
                },
                max = 59,
                modifier = Modifier.weight(1f)
            )
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            DurationField(
                value = startS,
                label = "SS",
                onValueChange = { s ->
                    val newMs = ((startH * 3600L) + (startM * 60L) + s) * 1000L
                    viewModel.onTrimStartChanged(newMs)
                },
                max = 59,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Manual End Time (HH : MM : SS) ────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF87171))
                )
                Spacer(Modifier.width(6.dp))
                Text("End Time (HH : MM : SS)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
            }

            TextButton(
                onClick = viewModel::onSetTrimEndToCurrent,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFFF87171))
                Spacer(Modifier.width(4.dp))
                Text("Current Position", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF87171))
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DurationField(
                value = endH,
                label = "HH",
                onValueChange = { h ->
                    val newMs = ((h * 3600L) + (endM * 60L) + endS) * 1000L
                    viewModel.onTrimEndChanged(newMs)
                },
                max = 99,
                modifier = Modifier.weight(1f)
            )
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
            DurationField(
                value = endM,
                label = "MM",
                onValueChange = { m ->
                    val newMs = ((endH * 3600L) + (m * 60L) + endS) * 1000L
                    viewModel.onTrimEndChanged(newMs)
                },
                max = 59,
                modifier = Modifier.weight(1f)
            )
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
            DurationField(
                value = endS,
                label = "SS",
                onValueChange = { s ->
                    val newMs = ((endH * 3600L) + (endM * 60L) + s) * 1000L
                    viewModel.onTrimEndChanged(newMs)
                },
                max = 59,
                modifier = Modifier.weight(1f)
            )
        }

        // Summary box
        val trimDur = (endMs - state.trimStartMs).coerceAtLeast(0L)
        val percent = if (totalMs > 0) ((trimDur.toFloat() / totalMs) * 100).toInt() else 100

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Trimmed Clip Duration:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "${formatDurationFull(trimDur)} (${percent}% kept)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fade Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FadeCard(state: SpeedPitchUiState, viewModel: SpeedPitchViewModel) {
    ToolCard {
        SectionHeader(icon = Icons.Default.Gradient, title = "Fade In / Out", tint = Color(0xFF8B5CF6))
        Spacer(Modifier.height(10.dp))

        LabeledSlider(
            label = "Fade In",
            value = state.fadeInSec,
            valueRange = 0f..10f,
            displayText = "${String.format("%.1f", state.fadeInSec)}s",
            color = Color(0xFF10B981),
            onValueChange = viewModel::onFadeInChanged
        )
        Spacer(Modifier.height(8.dp))
        LabeledSlider(
            label = "Fade Out",
            value = state.fadeOutSec,
            valueRange = 0f..10f,
            displayText = "${String.format("%.1f", state.fadeOutSec)}s",
            color = Color(0xFF8B5CF6),
            onValueChange = viewModel::onFadeOutChanged
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Volume Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VolumeCard(state: SpeedPitchUiState, viewModel: SpeedPitchViewModel) {
    ToolCard {
        SectionHeader(icon = Icons.AutoMirrored.Filled.VolumeUp, title = "Volume & Loudness", tint = Color(0xFFF59E0B))
        Spacer(Modifier.height(10.dp))

        LabeledSlider(
            label = "Volume",
            value = state.volumeDb,
            valueRange = -30f..30f,
            displayText = "${if (state.volumeDb >= 0) "+" else ""}${String.format("%.1f", state.volumeDb)} dB",
            color = Color(0xFFF59E0B),
            onValueChange = viewModel::onVolumeChanged
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (state.normalize) Color(0xFFF59E0B).copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
                .clickable(onClick = viewModel::onNormalizeToggled)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = if (state.normalize) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Normalize Loudness",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.normalize) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Auto-level to -14 LUFS (broadcast standard)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                checked = state.normalize,
                onCheckedChange = { viewModel.onNormalizeToggled() },
                colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF59E0B))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Equalizer Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EqualizerCard(state: SpeedPitchUiState, viewModel: SpeedPitchViewModel) {
    val expandRotation by animateFloatAsState(
        targetValue = if (state.isEqExpanded) 180f else 0f, label = "eq_arrow"
    )

    ToolCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = viewModel::onEqExpandToggled),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(icon = Icons.Default.Equalizer, title = "Equalizer (5-Band)", tint = Color(0xFF38BDF8))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isEqExpanded) {
                    TextButton(
                        onClick = viewModel::onResetEq,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { Text("Reset", style = MaterialTheme.typography.labelSmall) }
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(expandRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = state.isEqExpanded) {
            Column {
                Spacer(Modifier.height(12.dp))
                val bands = listOf(
                    Triple("Bass", "80Hz", state.eqBass) to viewModel::onEqBassChanged,
                    Triple("Low-Mid", "250Hz", state.eqLowMid) to viewModel::onEqLowMidChanged,
                    Triple("Mid", "1kHz", state.eqMid) to viewModel::onEqMidChanged,
                    Triple("High-Mid", "4kHz", state.eqHighMid) to viewModel::onEqHighMidChanged,
                    Triple("Treble", "12kHz", state.eqTreble) to viewModel::onEqTrebleChanged,
                )
                bands.forEach { (info, onChange) ->
                    val (name, freq, value) = info
                    EqBand(name = name, freq = freq, value = value, onValueChange = onChange)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun EqBand(name: String, freq: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(freq, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "${if (value >= 0) "+" else ""}${String.format("%.1f", value)} dB",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF38BDF8),
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -15f..15f,
            steps = 29,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF38BDF8),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Export Settings Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExportSettingsCard(state: SpeedPitchUiState, viewModel: SpeedPitchViewModel) {
    val info = state.mediaInfo ?: return

    ToolCard {
        SectionHeader(icon = Icons.Default.FileUpload, title = "Export Settings", tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))

        // Output format
        val audioFormats = listOf("" to "Same as Input", "mp3" to "MP3", "aac" to "AAC (M4A)", "ogg" to "OGG Vorbis",
            "opus" to "OPUS", "flac" to "FLAC", "wav" to "WAV", "aiff" to "AIFF")
        val videoFormats = listOf("" to "Same as Input", "mp4" to "MP4 (H.264)", "mkv" to "MKV", "webm" to "WebM",
            "avi" to "AVI", "mov" to "MOV", "3gp" to "3GP")
        val formats = if (info.mediaType == MediaType.VIDEO) videoFormats else audioFormats

        DropdownRow(
            label = "Format",
            options = formats,
            selected = state.outputFormat,
            onSelected = viewModel::onOutputFormatChanged
        )

        Spacer(Modifier.height(8.dp))

        // Audio bitrate
        val audioBitrates = listOf("" to "Auto", "64k" to "64 kbps", "96k" to "96 kbps",
            "128k" to "128 kbps", "192k" to "192 kbps", "256k" to "256 kbps", "320k" to "320 kbps")
        DropdownRow(
            label = "Audio Quality",
            options = audioBitrates,
            selected = state.audioBitrate,
            onSelected = viewModel::onAudioBitrateChanged
        )

        if (info.mediaType == MediaType.VIDEO) {
            Spacer(Modifier.height(8.dp))
            val videoBitrates = listOf("" to "Auto", "500k" to "500 kbps (Low)", "1M" to "1 Mbps",
                "2M" to "2 Mbps", "4M" to "4 Mbps (Good)", "8M" to "8 Mbps (High)", "20M" to "20 Mbps (Max)")
            DropdownRow(
                label = "Video Quality",
                options = videoBitrates,
                selected = state.videoBitrate,
                onSelected = viewModel::onVideoBitrateChanged
            )
        }

        Spacer(Modifier.height(8.dp))

        // Sample rate
        val sampleRates = listOf(-1 to "Original", 8000 to "8 kHz", 22050 to "22.05 kHz",
            44100 to "44.1 kHz", 48000 to "48 kHz", 96000 to "96 kHz")
        DropdownRowInt(
            label = "Sample Rate",
            options = sampleRates,
            selected = state.outputSampleRate,
            onSelected = viewModel::onSampleRateChanged
        )

        Spacer(Modifier.height(8.dp))

        // Channels
        val channelOptions = listOf(-1 to "Original", 1 to "Mono", 2 to "Stereo")
        DropdownRowInt(
            label = "Channels",
            options = channelOptions,
            selected = state.outputChannels,
            onSelected = viewModel::onChannelsChanged
        )

        Spacer(Modifier.height(10.dp))

        // Reverse toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (state.reverse) Color(0xFFF87171).copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
                .clickable(onClick = viewModel::onReverseToggled)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = if (state.reverse) Color(0xFFF87171) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Reverse Audio / Video", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = if (state.reverse) Color(0xFFF87171) else MaterialTheme.colorScheme.onSurface)
                Text("Play file backwards", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(checked = state.reverse, onCheckedChange = { viewModel.onReverseToggled() }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF87171)))
        }

        Spacer(Modifier.height(6.dp))

        // Preserve metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.preserveMetadata,
                onCheckedChange = { viewModel.onPreserveMetadataToggled() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Text("Preserve original metadata (tags)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }

        // Output path info
        Spacer(Modifier.height(6.dp))
        val folder = if (info.mediaType == MediaType.VIDEO) "Movies/VocoNexus/" else "Music/VocoNexus/"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(4.dp))
            Text("Saved to: $folder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(state: SpeedPitchUiState, viewModel: SpeedPitchViewModel) {
    val outputMs = viewModel.computedOutputDurationMs()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
                    )
                )
            )
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Summarize, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Processing Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            val rows = buildList {
                add("⚡ Speed" to "${String.format("%.3f", state.speedMultiplier)}x")
                add("🎵 Pitch" to if (state.isPitchLocked) "Auto-locked (natural)" else "${String.format("%.1f", state.pitchSemitones)} st")
                if (state.trimStartMs > 0 || (state.trimEndMs > 0 && state.trimEndMs < (state.mediaInfo?.durationMs ?: Long.MAX_VALUE))) {
                    add("✂️ Trim" to "${formatDurationFull(state.trimStartMs)} → ${formatDurationFull(if (state.trimEndMs > 0) state.trimEndMs else 0)}")
                }
                if (state.fadeInSec > 0 || state.fadeOutSec > 0) {
                    add("🌊 Fade" to "In ${state.fadeInSec}s / Out ${state.fadeOutSec}s")
                }
                if (state.volumeDb != 0f) add("🔊 Volume" to "${if (state.volumeDb >= 0) "+" else ""}${String.format("%.1f", state.volumeDb)} dB")
                if (state.normalize) add("📣 Normalize" to "-14 LUFS")
                if (state.reverse) add("↩️ Reverse" to "Yes")
                if (outputMs > 0) add("⏱ Output" to formatDurationFull(outputMs))
            }
            rows.forEach { (k, v) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Action Buttons (Bottom Export)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActionButtons(
    state: SpeedPitchUiState,
    onProcess: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.isProcessing) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text("Cancel", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Button(
                onClick = onProcess,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Process & Export", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Processing Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProcessingDialog(progress: Float, onCancel: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp)
                .widthIn(min = 280.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(400),
                    label = "progress"
                )

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                    CircularProgressIndicator(
                        progress = { animatedProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("Processing Media", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("FFmpeg is applying your settings…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Export Success Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExportSuccessDialog(result: ExportResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
        },
        title = { Text("Export Complete!", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "File saved to:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = result.outputFile.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                TypeBadge(
                    text = if (result.mediaType == MediaType.VIDEO) "📁 Movies/VocoNexus" else "📁 Music/VocoNexus",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Done") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToolCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TypeBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayText: String,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(displayText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DurationField(value: Int, label: String, max: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val focusMgr = LocalFocusManager.current
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { txt ->
            val v = txt.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
            onValueChange(v.coerceIn(0, max))
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusMgr.clearFocus() }),
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        textStyle = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        ),
        placeholder = { Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: "Auto"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            readOnly = true,
            value = selectedLabel,
            onValueChange = {},
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelected(value); expanded = false },
                    leadingIcon = if (value == selected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) } } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRowInt(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: "Original"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            readOnly = true,
            value = selectedLabel,
            onValueChange = {},
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelected(value); expanded = false },
                    leadingIcon = if (value == selected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) } } else null
                )
            }
        }
    }
}
