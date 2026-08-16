package com.voconexus.app.ui.screens.audioextractor

import java.util.Locale
import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.voconexus.app.core.tools.audioextractor.AudioExtractorInfo
import com.voconexus.app.core.tools.speedpitch.MediaType
import com.voconexus.app.ui.screens.speedpitch.WaveformSkeleton
import com.voconexus.app.ui.screens.speedpitch.WaveformView

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AudioExtractorScreen(
    viewModel: AudioExtractorViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Permissions launcher
    val permissions = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.READ_MEDIA_AUDIO)
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        list
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    // Media Picker launcher
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onFileSelected(it) }
    }

    val onPickFile = {
        if (permissionState.allPermissionsGranted) {
            pickerLauncher.launch("*/*")
        } else {
            permissionState.launchMultiplePermissionRequest()
            pickerLauncher.launch("*/*")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Audio Extractor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Video to Audio & Converter",
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
                        Button(
                            onClick = viewModel::onStartProcessing,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export Audio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // 1. File Picker Card
            item {
                FilePickerCard(state = state, onPickFile = onPickFile)
            }

            if (state.info != null) {
                // 2. Live Media Preview Player Card
                item {
                    MediaPreviewCard(state = state, viewModel = viewModel)
                }

                // 3. Output Format & Quality Settings Card (Default: Original)
                item {
                    FormatQualityCard(state = state, viewModel = viewModel)
                }

                // 4. Trim / Cut & Segment Card
                item {
                    TrimCard(state = state, viewModel = viewModel)
                }

                // 5. Volume & Fade Controls Card
                item {
                    VolumeFadeCard(state = state, viewModel = viewModel)
                }

                // 6. Speed & Pitch Controls Card
                item {
                    SpeedPitchCard(state = state, viewModel = viewModel)
                }
            }
        }
    }

    // Processing Dialog
    if (state.isProcessing) {
        ProcessingDialog(progress = state.processingProgress, onCancel = viewModel::onCancelProcessing)
    }

    // Success Dialog
    state.exportResult?.let { result ->
        ExportSuccessDialog(result = result, onDismiss = viewModel::onDismissResult)
    }

    // Error Dialog
    state.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissResult,
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Extraction Failed") },
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
// Component Cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToolCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FilePickerCard(state: AudioExtractorUiState, onPickFile: () -> Unit) {
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
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.info?.mediaType == MediaType.VIDEO) Icons.Default.Movie else Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = state.info?.fileName ?: "No file selected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (state.info != null) {
                        val info = state.info
                        val sizeMb = String.format(Locale.US, "%.1f MB", info.fileSizeBytes / (1024f * 1024f))
                        val duration = formatDurationFull(info.durationMs)
                        Text(
                            text = "$sizeMb • $duration • ${info.containerFormat}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Select any Video (MP4, MKV, AVI) or Audio file",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onPickFile,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.info == null) "Select File" else "Change")
            }
        }

        if (state.isLoadingFile) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Media Preview Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MediaPreviewCard(state: AudioExtractorUiState, viewModel: AudioExtractorViewModel) {
    val info = state.info ?: return
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

            FilterChip(
                selected = true,
                onClick = {},
                label = { Text("⏱ ${formatDurationFull(targetDurationMs)}", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            )
        }

        Spacer(Modifier.height(10.dp))

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

        // Position Scrubber
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

        // Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = viewModel::onSetTrimStartToCurrent,
                modifier = Modifier.clip(CircleShape).background(Color(0xFF10B981).copy(alpha = 0.15f))
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = "Set Trim Start", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
            }

            IconButton(onClick = viewModel::rewind10s) {
                Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

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

            IconButton(onClick = viewModel::forward10s) {
                Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(
                onClick = viewModel::onSetTrimEndToCurrent,
                modifier = Modifier.clip(CircleShape).background(Color(0xFFF87171).copy(alpha = 0.15f))
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = "Set Trim End", tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Format & Quality Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormatQualityCard(state: AudioExtractorUiState, viewModel: AudioExtractorViewModel) {
    val info = state.info ?: return

    ToolCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(icon = Icons.Default.Tune, title = "Format & Quality Settings", tint = MaterialTheme.colorScheme.primary)

            if (state.outputFormat == "original" && state.audioBitrate == "original") {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Instant Lossless Copy (0.1s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Output Audio Format Selector
        Text("Audio Format", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val formats = listOf(
                "original" to "Original (${info.audioCodec.uppercase()})",
                "mp3" to "MP3",
                "aac" to "AAC (M4A)",
                "wav" to "WAV (Lossless)",
                "flac" to "FLAC (Lossless)",
                "ogg" to "OGG Vorbis"
            )
            formats.forEach { (key, label) ->
                val selected = state.outputFormat == key
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.onOutputFormatChanged(key) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Bitrate Selector
        val origBitrateKbps = if (info.bitrateBps > 0) "${info.bitrateBps / 1000L}k" else "Auto"
        Text("Audio Bitrate", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val bitrates = listOf(
                "original" to "Original ($origBitrateKbps)",
                "320k" to "320 kbps (HD)",
                "256k" to "256 kbps",
                "192k" to "192 kbps",
                "128k" to "128 kbps",
                "96k" to "96 kbps"
            )
            bitrates.forEach { (key, label) ->
                val selected = state.audioBitrate == key
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.onAudioBitrateChanged(key) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Sample Rate & Channels Dropdowns
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DropdownRowInt(
                label = "Sample Rate",
                options = listOf(-1 to "Original (${if (info.sampleRate > 0) info.sampleRate else 44100} Hz)", 48000 to "48,000 Hz", 44100 to "44,100 Hz", 32000 to "32,000 Hz", 16000 to "16,000 Hz"),
                selected = state.sampleRate,
                onSelected = viewModel::onSampleRateChanged,
                modifier = Modifier.weight(1f)
            )

            DropdownRowInt(
                label = "Channels",
                options = listOf(-1 to "Original (${if (info.channels == 1) "Mono" else "Stereo"})", 2 to "Stereo (2.0)", 1 to "Mono (1.0)"),
                selected = state.channels,
                onSelected = viewModel::onChannelsChanged,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Trim Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TrimCard(state: AudioExtractorUiState, viewModel: AudioExtractorViewModel) {
    val info = state.info ?: return
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
            SectionHeader(icon = Icons.Default.ContentCut, title = "Trim / Cut Audio Segment", tint = Color(0xFFF59E0B))
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

        // Manual Start Time (HH : MM : SS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                Spacer(Modifier.width(6.dp))
                Text("Start Time (HH : MM : SS)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            }

            TextButton(onClick = viewModel::onSetTrimStartToCurrent, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
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
            DurationField(value = startH, fieldLabel = "HH", onValueChange = { h -> viewModel.onTrimStartChanged(((h * 3600L) + (startM * 60L) + startS) * 1000L) }, max = 99, modifier = Modifier.weight(1f))
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            DurationField(value = startM, fieldLabel = "MM", onValueChange = { m -> viewModel.onTrimStartChanged(((startH * 3600L) + (m * 60L) + startS) * 1000L) }, max = 59, modifier = Modifier.weight(1f))
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            DurationField(value = startS, fieldLabel = "SS", onValueChange = { s -> viewModel.onTrimStartChanged(((startH * 3600L) + (startM * 60L) + s) * 1000L) }, max = 59, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        // Manual End Time (HH : MM : SS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF87171)))
                Spacer(Modifier.width(6.dp))
                Text("End Time (HH : MM : SS)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
            }

            TextButton(onClick = viewModel::onSetTrimEndToCurrent, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
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
            DurationField(value = endH, fieldLabel = "HH", onValueChange = { h -> viewModel.onTrimEndChanged(((h * 3600L) + (endM * 60L) + endS) * 1000L) }, max = 99, modifier = Modifier.weight(1f))
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
            DurationField(value = endM, fieldLabel = "MM", onValueChange = { m -> viewModel.onTrimEndChanged(((endH * 3600L) + (m * 60L) + endS) * 1000L) }, max = 59, modifier = Modifier.weight(1f))
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
            DurationField(value = endS, fieldLabel = "SS", onValueChange = { s -> viewModel.onTrimEndChanged(((endH * 3600L) + (endM * 60L) + s) * 1000L) }, max = 59, modifier = Modifier.weight(1f))
        }

        val trimDur = (endMs - state.trimStartMs).coerceAtLeast(0L)
        val percent = if (totalMs > 0) ((trimDur.toFloat() / totalMs) * 100).toInt() else 100

        Spacer(Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("Extracted Audio Length", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDurationFull(trimDur),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${percent}% of video",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Volume & Fade Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VolumeFadeCard(state: AudioExtractorUiState, viewModel: AudioExtractorViewModel) {
    ToolCard {
        SectionHeader(icon = Icons.AutoMirrored.Filled.VolumeUp, title = "Volume & Fade Effects", tint = Color(0xFF8B5CF6))
        Spacer(Modifier.height(10.dp))

        LabeledSlider(
            label = "Volume Boost",
            value = state.volumeDb,
            valueRange = -30f..30f,
            displayText = "${if (state.volumeDb >= 0) "+" else ""}${String.format(Locale.US, "%.1f", state.volumeDb)} dB",
            color = Color(0xFFF59E0B),
            onValueChange = viewModel::onVolumeChanged
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Loudnorm Normalization", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Switch(checked = state.normalize, onCheckedChange = { viewModel.onNormalizeToggled() })
        }

        Spacer(Modifier.height(10.dp))

        LabeledSlider(
            label = "Fade In",
            value = state.fadeInSec,
            valueRange = 0f..10f,
            displayText = "${String.format(Locale.US, "%.1f", state.fadeInSec)}s",
            color = Color(0xFF10B981),
            onValueChange = viewModel::onFadeInChanged
        )

        Spacer(Modifier.height(8.dp))

        LabeledSlider(
            label = "Fade Out",
            value = state.fadeOutSec,
            valueRange = 0f..10f,
            displayText = "${String.format(Locale.US, "%.1f", state.fadeOutSec)}s",
            color = Color(0xFF8B5CF6),
            onValueChange = viewModel::onFadeOutChanged
        )
    }
}

@Composable
private fun SpeedPitchCard(state: AudioExtractorUiState, viewModel: AudioExtractorViewModel) {
    ToolCard {
        SectionHeader(icon = Icons.Default.Speed, title = "Speed, Pitch & Audio FX", tint = Color(0xFF6366F1))
        Spacer(Modifier.height(10.dp))

        // Custom Output Filename Field
        OutlinedTextField(
            value = state.customFileName,
            onValueChange = viewModel::onCustomFileNameChanged,
            label = { Text("Custom Output Filename (Optional)", style = MaterialTheme.typography.labelSmall) },
            placeholder = { Text("e.g. My_Extracted_Audio", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))

        // Speed Section with Slider + Manual Text Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Speed Multiplier", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

            // Manual Speed Input Box
            OutlinedTextField(
                value = state.speedText,
                onValueChange = viewModel::onSpeedTextChanged,
                label = { Text("Manual", style = MaterialTheme.typography.labelSmall) },
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6366F1)
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(100.dp),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Slider(
            value = state.speedMultiplier,
            onValueChange = viewModel::onSpeedChanged,
            valueRange = 0.1f..8.0f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF6366F1),
                activeTrackColor = Color(0xFF6366F1),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val speedChips = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            speedChips.forEach { speedVal ->
                val isSelected = kotlin.math.abs(state.speedMultiplier - speedVal) < 0.01f
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.onSpeedChanged(speedVal) },
                    label = { Text("${speedVal}x", style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF6366F1),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))

        // Pitch Section with Slider + Manual Text Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Pitch Shift", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (state.isPitchLocked) "Pitch locked to speed" else "${state.pitchSemitones} semitones",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!state.isPitchLocked) {
                OutlinedTextField(
                    value = state.pitchText,
                    onValueChange = viewModel::onPitchTextChanged,
                    label = { Text("Semitones", style = MaterialTheme.typography.labelSmall) },
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B5CF6)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.width(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lock", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(4.dp))
                Switch(checked = state.isPitchLocked, onCheckedChange = { viewModel.onPitchLockToggled() })
            }
        }

        if (!state.isPitchLocked) {
            Spacer(Modifier.height(6.dp))
            Slider(
                value = state.pitchSemitones,
                onValueChange = viewModel::onPitchChanged,
                valueRange = -24f..24f,
                steps = 47,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF8B5CF6),
                    activeTrackColor = Color(0xFF8B5CF6),
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))

        // Equalizer Presets
        Text("Equalizer Presets (FX)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val eqPresets = listOf(
                "flat" to "Flat",
                "bass_boost" to "Bass Boost",
                "vocal_boost" to "Vocal Boost",
                "treble_boost" to "Treble Boost",
                "clarity" to "Clarity"
            )
            eqPresets.forEach { (key, label) ->
                val selected = state.eqPreset == key
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.onEqPresetChanged(key) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF10B981),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Reverse Audio Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reverse Audio Track", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Switch(checked = state.reverse, onCheckedChange = { viewModel.onReverseToggled() })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper Composables & Dialogs
// ─────────────────────────────────────────────────────────────────────────────

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun DurationField(
    value: Int,
    fieldLabel: String,
    onValueChange: (Int) -> Unit,
    max: Int,
    modifier: Modifier = Modifier
) {
    val textValue = remember(value) { String.format(Locale.US, "%02d", value) }
    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            val clean = input.filter { c -> c.isDigit() }.take(2)
            val v = clean.toIntOrNull()
            if (v != null) {
                onValueChange(v.coerceIn(0, max))
            }
        },
        label = { Text(fieldLabel, style = MaterialTheme.typography.labelSmall) },
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRowInt(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: "Original"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
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
                    onClick = { onSelected(value); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ProcessingDialog(progress: Float, onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Extracting Audio...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(10.dp)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ExportSuccessDialog(
    result: ExtractedAudioResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(36.dp)) },
        title = { Text("Audio Extracted Successfully!") },
        text = {
            Column {
                Text("Saved to Music/VocoNexus/AudioExtractor/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(result.outputFile.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${String.format(Locale.US, "%.2f", result.outputFile.length() / (1024f * 1024f))} MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Done")
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Format Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun Long.toHms(): Triple<Int, Int, Int> {
    val totalSec = (this / 1000).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return Triple(h, m, s)
}

private fun formatDurationFull(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
