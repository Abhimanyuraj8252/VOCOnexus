package com.voconexus.app.ui.screens.convertercompressor

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
import com.voconexus.app.core.tools.speedpitch.MediaType
import com.voconexus.app.ui.screens.speedpitch.WaveformSkeleton
import com.voconexus.app.ui.screens.speedpitch.WaveformView
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConverterCompressorScreen(
    viewModel: ConverterCompressorViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                            text = "Format Converter & Compressor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Smart Quality Compression & Multi-Format",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
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
                            Text("Export Media", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            item { FilePickerCard(state = state, onPickFile = onPickFile) }

            if (state.info != null) {
                // 2. Media Preview Card
                item { MediaPreviewCard(state = state, viewModel = viewModel) }

                // 3. Smart Compression & Estimated Size Card
                item { CompressionCard(state = state, viewModel = viewModel) }

                // 4. Format, Resolution & Codec Card
                item { FormatResolutionCard(state = state, viewModel = viewModel) }

                // 5. Trim Card
                item { TrimCard(state = state, viewModel = viewModel) }

                // 6. Speed & Pitch Controls Card
                item { SpeedPitchCard(state = state, viewModel = viewModel) }
            }
        }
    }

    if (state.isProcessing) {
        ProcessingDialog(progress = state.processingProgress, onCancel = viewModel::onCancelProcessing)
    }

    state.exportResult?.let { result ->
        ExportSuccessDialog(result = result, onDismiss = viewModel::onDismissResult)
    }

    state.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissResult,
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Conversion Failed") },
            text = {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = { TextButton(onClick = viewModel::onDismissResult) { Text("OK") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Components & Cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToolCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content)
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
private fun FilePickerCard(state: ConverterCompressorUiState, onPickFile: () -> Unit) {
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
                        val extra = if (info.mediaType == MediaType.VIDEO) "${info.videoWidth}x${info.videoHeight}" else "${info.sampleRate}Hz"
                        Text(
                            text = "$sizeMb • $duration • $extra",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Select any Video (MP4, MKV) or Audio (MP3, WAV) file",
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
private fun MediaPreviewCard(state: ConverterCompressorUiState, viewModel: ConverterCompressorViewModel) {
    val info = state.info ?: return
    val speed = state.speedMultiplier.coerceIn(0.10f, 8.0f)
    val rawTrimDur = if (state.trimEndMs > 0) (state.trimEndMs - state.trimStartMs) else info.durationMs
    val targetDurationMs = (rawTrimDur.toFloat() / speed).toLong().coerceAtLeast(100L)
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
                        modifier = Modifier.fillMaxWidth().height(90.dp)
                    )
                } else {
                    WaveformSkeleton(modifier = Modifier.fillMaxWidth().height(90.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDurationFull(scaledCurrentMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(formatDurationFull(targetDurationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::onSetTrimStartToCurrent, modifier = Modifier.clip(CircleShape).background(Color(0xFF10B981).copy(alpha = 0.15f))) {
                Icon(Icons.Default.ContentCut, contentDescription = "Trim Start", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = viewModel::rewind10s) {
                Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledIconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(48.dp), shape = CircleShape) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = viewModel::forward10s) {
                Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = viewModel::onSetTrimEndToCurrent, modifier = Modifier.clip(CircleShape).background(Color(0xFFF87171).copy(alpha = 0.15f))) {
                Icon(Icons.Default.ContentCut, contentDescription = "Trim End", tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Smart Compression & Estimated Size Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompressionCard(state: ConverterCompressorUiState, viewModel: ConverterCompressorViewModel) {
    val info = state.info ?: return
    val origMb = String.format(Locale.US, "%.1f MB", info.fileSizeBytes / (1024f * 1024f))
    val estMb = String.format(Locale.US, "%.1f MB", state.estimatedOutputSizeBytes / (1024f * 1024f))
    val savedMbNum = ((info.fileSizeBytes - state.estimatedOutputSizeBytes) / (1024f * 1024f)).coerceAtLeast(0f)
    val savedMbStr = String.format(Locale.US, "%.1f MB", savedMbNum)
    val pctText = if (state.estimatedReductionPercent > 0) "-${state.estimatedReductionPercent}% Smaller" else "Original Quality"

    val resLabel = when (state.targetResolution.lowercase()) {
        "4k" -> "4K (2160p)"
        "1080p" -> "1080p Full HD"
        "720p" -> "720p HD"
        "480p" -> "480p SD"
        "360p" -> "360p Low"
        "240p" -> "240p Ultra Low"
        else -> "Original Resolution (${info.videoWidth}x${info.videoHeight})"
    }

    val modeLabel = when (state.compressionMode.lowercase()) {
        "ultra_lossless" -> "Ultra Lossless (CRF 18)"
        "visually_lossless" -> "Visually Lossless (CRF 21)"
        "balanced" -> "Recommended Balanced (CRF 24)"
        "high" -> "High Saver (CRF 28)"
        "extreme" -> "Extreme Saver (CRF 32)"
        "custom_mb" -> "Target ${String.format(Locale.US, "%.1f", state.targetSizeMb)} MB"
        else -> "Recommended Balanced"
    }

    ToolCard {
        SectionHeader(icon = Icons.Default.Compress, title = "Smart Compression & Size Estimator", tint = Color(0xFF10B981))
        Spacer(Modifier.height(10.dp))

        // Real-Time Estimation & Comparison Breakdown Display Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Live Size Comparison Breakdown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF10B981)) {
                        Text(pctText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Original Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(origMb, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    }

                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Estimated Export Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(estMb, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))

                // Breakdown of Resolution + Compression Mode impact
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Selected Configuration:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Saved ~$savedMbStr",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "• $resLabel\n• $modeLabel",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Compression Preset Chips
        Text("Compression Mode (CRF Quality Presets)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val modes = listOf(
                "balanced" to "⚡ Balanced (CRF 24)",
                "visually_lossless" to "✨ Visually Lossless (CRF 21)",
                "ultra_lossless" to "💎 Ultra Lossless (CRF 18)",
                "high" to "📦 High Saver (CRF 28)",
                "extreme" to "🚀 Extreme Saver (CRF 32)",
                "custom_mb" to "🎯 Custom Size (MB)"
            )
            modes.forEach { (key, label) ->
                val selected = state.compressionMode == key
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.onCompressionModeChanged(key) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF10B981), selectedLabelColor = Color.White)
                )
            }
        }

        // Custom Size Slider if custom_mb
        if (state.compressionMode == "custom_mb") {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Target File Size (MB)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = state.customSizeMbText,
                    onValueChange = viewModel::onCustomSizeMbTextChanged,
                    label = { Text("MB", style = MaterialTheme.typography.labelSmall) },
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF10B981)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            val maxMb = ((info.fileSizeBytes * 1.2f) / (1024f * 1024f)).coerceAtLeast(100f)
            Slider(
                value = state.targetSizeMb.coerceIn(5f, maxMb),
                onValueChange = viewModel::onTargetSizeMbChanged,
                valueRange = 5f..maxMb,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Format & Resolution Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormatResolutionCard(state: ConverterCompressorUiState, viewModel: ConverterCompressorViewModel) {
    val info = state.info ?: return
    val isVideo = info.mediaType == MediaType.VIDEO

    ToolCard {
        SectionHeader(icon = Icons.Default.Transform, title = "Format, Resolution & Codec", tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))

        // Custom Output Filename
        OutlinedTextField(
            value = state.customFileName,
            onValueChange = viewModel::onCustomFileNameChanged,
            label = { Text("Custom Output Filename (Optional)", style = MaterialTheme.typography.labelSmall) },
            placeholder = { Text("e.g. Compressed_Video_Track", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(Modifier.height(14.dp))

        // Video Format Selector
        if (isVideo) {
            Text("Video Container Format", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val videoFormats = listOf(
                    "original" to "Original (${info.containerFormat})",
                    "mp4" to "MP4",
                    "mkv" to "MKV",
                    "avi" to "AVI",
                    "mov" to "MOV",
                    "webm" to "WebM",
                    "flv" to "FLV",
                    "3gp" to "3GP",
                    "ts" to "TS",
                    "wmv" to "WMV"
                )
                videoFormats.forEach { (key, label) ->
                    val selected = state.outputFormat == key
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.onOutputFormatChanged(key) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Video Resolution Selector
            Text("Video Resolution", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val resolutions = listOf(
                    "original" to "Original (${info.videoWidth}x${info.videoHeight})",
                    "4k" to "4K (2160p)",
                    "1080p" to "1080p Full HD",
                    "720p" to "720p HD",
                    "480p" to "480p SD",
                    "360p" to "360p Low"
                )
                resolutions.forEach { (key, label) ->
                    val selected = state.targetResolution == key
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.onTargetResolutionChanged(key) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondary, selectedLabelColor = Color.White)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
        }

        // Audio Format Selector
        Text("Audio Format (Extracted or Converted)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val audioFormats = listOf(
                "mp3" to "MP3",
                "aac" to "AAC (M4A)",
                "wav" to "WAV (Lossless)",
                "flac" to "FLAC (Lossless)",
                "ogg" to "OGG Vorbis",
                "opus" to "OPUS",
                "ac3" to "AC3 Dolby",
                "aiff" to "AIFF",
                "amr" to "AMR",
                "wma" to "WMA"
            )
            audioFormats.forEach { (key, label) ->
                val selected = state.outputFormat == key
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.onOutputFormatChanged(key) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF8B5CF6), selectedLabelColor = Color.White)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Dropdowns for FPS, Sample Rate, Bitrate
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isVideo) {
                DropdownRowInt(
                    label = "Frame Rate (FPS)",
                    options = listOf(-1 to "Original (${info.frameRate.toInt()} fps)", 60 to "60 FPS", 30 to "30 FPS", 24 to "24 FPS"),
                    selected = state.targetFps,
                    onSelected = viewModel::onTargetFpsChanged,
                    modifier = Modifier.weight(1f)
                )
            }

            DropdownRowInt(
                label = "Sample Rate",
                options = listOf(-1 to "Original (${info.sampleRate} Hz)", 48000 to "48,000 Hz", 44100 to "44,100 Hz", 32000 to "32,000 Hz"),
                selected = state.sampleRate,
                onSelected = viewModel::onSampleRateChanged,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Trim Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TrimCard(state: ConverterCompressorUiState, viewModel: ConverterCompressorViewModel) {
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
            SectionHeader(icon = Icons.Default.ContentCut, title = "Trim / Cut Video Segment", tint = Color(0xFFF59E0B))
            TextButton(onClick = viewModel::onResetTrim, contentPadding = PaddingValues(0.dp)) {
                Text("Reset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        LabeledSlider(label = "Start Position", value = state.trimStartMs.toFloat(), valueRange = 0f..totalMs.toFloat(), displayText = formatDurationFull(state.trimStartMs), color = Color(0xFF10B981), onValueChange = { viewModel.onTrimStartChanged(it.toLong()) })
        Spacer(Modifier.height(6.dp))
        LabeledSlider(label = "End Position", value = endMs.toFloat(), valueRange = 0f..totalMs.toFloat(), displayText = formatDurationFull(endMs), color = Color(0xFFF87171), onValueChange = { viewModel.onTrimEndChanged(it.toLong()) })

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))

        // Manual Start Time (HH : MM : SS)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Start Time (HH : MM : SS)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            TextButton(onClick = viewModel::onSetTrimStartToCurrent, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF10B981))
                Spacer(Modifier.width(4.dp))
                Text("Current Position", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DurationField(value = startH, fieldLabel = "HH", onValueChange = { h -> viewModel.onTrimStartChanged(((h * 3600L) + (startM * 60L) + startS) * 1000L) }, max = 99, modifier = Modifier.weight(1f))
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            DurationField(value = startM, fieldLabel = "MM", onValueChange = { m -> viewModel.onTrimStartChanged(((startH * 3600L) + (m * 60L) + startS) * 1000L) }, max = 59, modifier = Modifier.weight(1f))
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            DurationField(value = startS, fieldLabel = "SS", onValueChange = { s -> viewModel.onTrimStartChanged(((startH * 3600L) + (startM * 60L) + s) * 1000L) }, max = 59, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        // Manual End Time (HH : MM : SS)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("End Time (HH : MM : SS)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
            TextButton(onClick = viewModel::onSetTrimEndToCurrent, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFFF87171))
                Spacer(Modifier.width(4.dp))
                Text("Current Position", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF87171))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DurationField(value = endH, fieldLabel = "HH", onValueChange = { h -> viewModel.onTrimEndChanged(((h * 3600L) + (endM * 60L) + endS) * 1000L) }, max = 99, modifier = Modifier.weight(1f))
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
            DurationField(value = endM, fieldLabel = "MM", onValueChange = { m -> viewModel.onTrimEndChanged(((endH * 3600L) + (endM * 60L) + endS) * 1000L) }, max = 59, modifier = Modifier.weight(1f))
            Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
            DurationField(value = endS, fieldLabel = "SS", onValueChange = { s -> viewModel.onTrimEndChanged(((endH * 3600L) + (endM * 60L) + s) * 1000L) }, max = 59, modifier = Modifier.weight(1f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Speed & Pitch Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpeedPitchCard(state: ConverterCompressorUiState, viewModel: ConverterCompressorViewModel) {
    ToolCard {
        SectionHeader(icon = Icons.Default.Speed, title = "Speed, Pitch & Audio FX", tint = Color(0xFF6366F1))
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Speed Multiplier", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.speedText,
                onValueChange = viewModel::onSpeedTextChanged,
                label = { Text("Manual", style = MaterialTheme.typography.labelSmall) },
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(100.dp),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Slider(value = state.speedMultiplier, onValueChange = viewModel::onSpeedChanged, valueRange = 0.1f..8.0f, colors = SliderDefaults.colors(thumbColor = Color(0xFF6366F1), activeTrackColor = Color(0xFF6366F1)))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val speedChips = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            speedChips.forEach { speedVal ->
                val isSelected = kotlin.math.abs(state.speedMultiplier - speedVal) < 0.01f
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.onSpeedChanged(speedVal) },
                    label = { Text("${speedVal}x", style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF6366F1), selectedLabelColor = Color.White)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Pitch Shift", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(text = if (state.isPitchLocked) "Pitch locked to speed" else "${state.pitchSemitones} semitones", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (!state.isPitchLocked) {
                OutlinedTextField(
                    value = state.pitchText,
                    onValueChange = viewModel::onPitchTextChanged,
                    label = { Text("Semitones", style = MaterialTheme.typography.labelSmall) },
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6)),
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
            Slider(value = state.pitchSemitones, onValueChange = viewModel::onPitchChanged, valueRange = -24f..24f, steps = 47, colors = SliderDefaults.colors(thumbColor = Color(0xFF8B5CF6), activeTrackColor = Color(0xFF8B5CF6)))
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))

        Text("Equalizer Presets (FX)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val eqPresets = listOf("flat" to "Flat", "bass_boost" to "Bass Boost", "vocal_boost" to "Vocal Boost", "treble_boost" to "Treble Boost", "clarity" to "Clarity")
            eqPresets.forEach { (key, label) ->
                val selected = state.eqPreset == key
                FilterChip(selected = selected, onClick = { viewModel.onEqPresetChanged(key) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }, shape = RoundedCornerShape(8.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF10B981), selectedLabelColor = Color.White))
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Reverse Media Track", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Switch(checked = state.reverse, onCheckedChange = { viewModel.onReverseToggled() })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers & Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LabeledSlider(label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, displayText: String, color: Color, onValueChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(text = displayText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
        }
        Slider(value = value.coerceIn(valueRange.start, valueRange.endInclusive), onValueChange = onValueChange, valueRange = valueRange, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
    }
}

@Composable
private fun DurationField(value: Int, fieldLabel: String, onValueChange: (Int) -> Unit, max: Int, modifier: Modifier = Modifier) {
    val textValue = remember(value) { String.format(Locale.US, "%02d", value) }
    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            val clean = input.filter { c -> c.isDigit() }.take(2)
            clean.toIntOrNull()?.let { v -> onValueChange(v.coerceIn(0, max)) }
        },
        label = { Text(fieldLabel, style = MaterialTheme.typography.labelSmall) },
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRowInt(label: String, options: List<Pair<Int, String>>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
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
                DropdownMenuItem(text = { Text(label, style = MaterialTheme.typography.bodySmall) }, onClick = { onSelected(value); expanded = false })
            }
        }
    }
}

@Composable
private fun ProcessingDialog(progress: Float, onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Converting & Compressing...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                Spacer(Modifier.height(12.dp))
                Text(text = "${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ExportSuccessDialog(result: ConvertedMediaResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(36.dp)) },
        title = { Text("Export Completed Successfully!") },
        text = {
            Column {
                Text("Saved to Movies/VocoNexus/FormatConverter/ or Music/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(result.outputFile.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text(text = "${String.format(Locale.US, "%.2f", result.outputFile.length() / (1024f * 1024f))} MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) { Text("Done") } }
    )
}

private fun Long.toHms(): Triple<Int, Int, Int> {
    val totalSec = (this / 1000).toInt()
    return Triple(totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60)
}

private fun formatDurationFull(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}
