package com.voconexus.app.ui.screens.trimmermerger

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.voconexus.app.core.tools.trimmermerger.AspectRatioCrop
import com.voconexus.app.core.tools.trimmermerger.ResolutionPreset
import com.voconexus.app.core.tools.trimmermerger.SplitType
import com.voconexus.app.core.tools.trimmermerger.TrimMode
import com.voconexus.app.core.util.Formatters
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimmerMergerScreen(
    viewModel: TrimmerMergerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activeMode by viewModel.activeMode.collectAsState()
    val mediaList by viewModel.mediaList.collectAsState()
    val selectedIndex by viewModel.selectedMediaIndex.collectAsState()
    val trimStartMs by viewModel.trimStartMs.collectAsState()
    val trimEndMs by viewModel.trimEndMs.collectAsState()
    val trimMode by viewModel.trimMode.collectAsState()
    val useFastCopy by viewModel.useFastStreamCopy.collectAsState()
    val speedMultiplier by viewModel.speedMultiplier.collectAsState()
    val fadeInSec by viewModel.fadeInSeconds.collectAsState()
    val fadeOutSec by viewModel.fadeOutSeconds.collectAsState()
    val extractAudio by viewModel.extractAudioOnly.collectAsState()
    val muteVideo by viewModel.muteVideoAudio.collectAsState()
    val volumeBoost by viewModel.volumeBoost.collectAsState()
    val cropRatio by viewModel.cropRatio.collectAsState()
    val targetRes by viewModel.targetResolution.collectAsState()
    val audioBitrate by viewModel.audioBitrateKbps.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val splitType by viewModel.splitType.collectAsState()
    val splitSegmentSec by viewModel.splitSegmentSeconds.collectAsState()
    val splitPartsCount by viewModel.splitPartsCount.collectAsState()
    val silenceThresholdDb by viewModel.silenceThresholdDb.collectAsState()
    val silenceMinDurationSec by viewModel.silenceMinDurationSec.collectAsState()
    val normalizeVol by viewModel.normalizeVolume.collectAsState()
    val customName by viewModel.customOutputName.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportedFile by viewModel.exportedFile.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addMediaUri(it) }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    val selectedItem = mediaList.getOrNull(selectedIndex)
    val isMergeMode = activeMode == ToolOperationMode.MERGE
    val totalPreviewDurationMs = if (isMergeMode) mediaList.sumOf { it.durationMs } else (selectedItem?.durationMs ?: 0L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Media Trimmer & Merger",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "10+ Pro Features • Hardware Accelerated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isExporting) {
                        Button(
                            onClick = { viewModel.cancelExport() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.processOperation() },
                            enabled = mediaList.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (mediaList.isNotEmpty()) Color(0xFF6366F1) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (mediaList.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(36.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Mode Selector Tabs (Horizontal Swipe Scrollable -> 0% Overlapping Guarantee!)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val modes = listOf(
                    ToolOperationMode.TRIM to "✂️ Precision Trimmer",
                    ToolOperationMode.MERGE to "🔗 Multi-File Merger",
                    ToolOperationMode.SPLIT to "🧩 Auto Splitter",
                    ToolOperationMode.SILENCE_CUT to "🔇 Auto Silence Cut"
                )

                items(modes) { (mode, label) ->
                    val isSelected = activeMode == mode
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { viewModel.selectMode(mode) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Media3 ExoPlayer Preview Box & Interactive Seeker Progress Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF3B82F6)))
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        if (mediaList.isNotEmpty()) {
                            val activeDisplayItem = selectedItem ?: mediaList.first()
                            if (activeDisplayItem.isVideo) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = viewModel.getPlayer()
                                            useController = false
                                            layoutParams = FrameLayout.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Video Header Badge Overlay
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isMergeMode) "🔗 ${mediaList.size} Files Combined Preview" else "🎬 ${activeDisplayItem.width}x${activeDisplayItem.height} • ${"%.0f".format(activeDisplayItem.frameRate)}FPS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isMergeMode) "MERGE PLAYLIST" else activeDisplayItem.videoCodec.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (isMergeMode) "Combined Merger Playlist (${mediaList.size} Files)" else activeDisplayItem.file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Total Duration: ${Formatters.formatDurationMs(totalPreviewDurationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { filePickerLauncher.launch("*/*") },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Tap to Select Audio or Video File", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Supports MP4, MKV, AVI, MOV, MP3, AAC, WAV", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Interactive Media Seekbar / Progress Bar & Playback Controls
                    if (mediaList.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            // Timeline Progress Slider (Supports Combined Total Duration across all merged files!)
                            Slider(
                                value = currentPositionMs.toFloat(),
                                onValueChange = { viewModel.seekToMs(it.toLong()) },
                                valueRange = 0f..(totalPreviewDurationMs.toFloat().coerceAtLeast(100f)),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.seekRelative(-5000L) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.FastRewind, contentDescription = "-5s", tint = MaterialTheme.colorScheme.primary)
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clickable { viewModel.togglePlayPause() }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Play/Pause",
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }

                                    IconButton(onClick = { viewModel.seekRelative(5000L) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.FastForward, contentDescription = "+5s", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Text(
                                    text = "${Formatters.formatDurationMs(currentPositionMs)} / ${Formatters.formatDurationMs(totalPreviewDurationMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Toolbar: Add File & Auto-Fit 0.1s Fast Stream Copy Chip
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Add File", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                item {
                    FilterChip(
                        selected = useFastCopy,
                        onClick = { viewModel.setUseFastStreamCopy(!useFastCopy) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (useFastCopy) Color(0xFF10B981) else Color.Gray,
                                    shape = CircleShape,
                                    modifier = Modifier.size(6.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("⚡ 0.1s Fast Copy", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                item {
                    FilterChip(
                        selected = extractAudio,
                        onClick = { viewModel.setExtractAudioOnly(!extractAudio) },
                        label = { Text("🎵 MP3 Extract") }
                    )
                }

                item {
                    FilterChip(
                        selected = muteVideo,
                        onClick = { viewModel.setMuteVideoAudio(!muteVideo) },
                        label = { Text("🔇 Mute Audio") }
                    )
                }

                item {
                    FilterChip(
                        selected = volumeBoost > 1.0f,
                        onClick = { viewModel.setVolumeBoost(if (volumeBoost > 1.0f) 1.0f else 1.5f) },
                        label = { Text("🔊 Vol ${"%.1fx".format(volumeBoost)}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode Specific Advanced Controls
            when (activeMode) {
                ToolOperationMode.TRIM -> {
                    if (selectedItem != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Card 1: Range Scrubber & Trim Mode
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("✂️ Precision Cut (A to B)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Start: ${Formatters.formatDurationMs(trimStartMs)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text("End: ${Formatters.formatDurationMs(trimEndMs)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text("Length: ${Formatters.formatDurationMs((trimEndMs - trimStartMs).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }

                                    RangeSlider(
                                        value = trimStartMs.toFloat()..trimEndMs.toFloat(),
                                        onValueChange = { range ->
                                            viewModel.setTrimStartMs(range.start.toLong())
                                            viewModel.setTrimEndMs(range.endInclusive.toLong())
                                        },
                                        valueRange = 0f..(selectedItem.durationMs.toFloat().coerceAtLeast(100f)),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isKeep = trimMode == TrimMode.KEEP_SELECTED_RANGE
                                        FilterChip(
                                            selected = isKeep,
                                            onClick = { viewModel.setTrimMode(TrimMode.KEEP_SELECTED_RANGE) },
                                            label = { Text("Keep A to B") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = !isKeep,
                                            onClick = { viewModel.setTrimMode(TrimMode.REMOVE_SELECTED_RANGE) },
                                            label = { Text("Cut Out A to B") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            // Card 2: Speed Multiplier & Audio Bitrate / Fade Effects
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("⚡ Speed, Bitrate & Audio Fade Effects", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Speed Multiplier Chips
                                    Text("Speed: ${"%.2fx".format(speedMultiplier)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        items(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) { spd ->
                                            FilterChip(
                                                selected = speedMultiplier == spd,
                                                onClick = { viewModel.setSpeedMultiplier(spd) },
                                                label = { Text("${spd}x") }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Audio Bitrate Selector
                                    Text("Audio Quality Bitrate:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        items(listOf(128, 192, 256, 320)) { bit ->
                                            FilterChip(
                                                selected = audioBitrate == bit,
                                                onClick = { viewModel.setAudioBitrateKbps(bit) },
                                                label = { Text("${bit} kbps") }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Fade In & Out Sliders
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Fade In: ${fadeInSec}s", style = MaterialTheme.typography.labelSmall)
                                            Slider(
                                                value = fadeInSec.toFloat(),
                                                onValueChange = { viewModel.setFadeInSeconds(it.toInt()) },
                                                valueRange = 0f..5f,
                                                steps = 4
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Fade Out: ${fadeOutSec}s", style = MaterialTheme.typography.labelSmall)
                                            Slider(
                                                value = fadeOutSec.toFloat(),
                                                onValueChange = { viewModel.setFadeOutSeconds(it.toInt()) },
                                                valueRange = 0f..5f,
                                                steps = 4
                                            )
                                        }
                                    }
                                }
                            }

                            // Card 3: Advanced Volume Boost & Crop Presets
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("🔊 Volume Boost & Aspect Ratio Crop", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text("Volume Boost: ${"%.1fx".format(volumeBoost)} (${"%.0f%%".format(volumeBoost * 100)})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = volumeBoost,
                                        onValueChange = { viewModel.setVolumeBoost(it) },
                                        valueRange = 0.5f..2.5f,
                                        steps = 7
                                    )

                                    if (selectedItem.isVideo && !extractAudio) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Aspect Ratio Crop:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            val crops = listOf(
                                                AspectRatioCrop.ORIGINAL to "Original",
                                                AspectRatioCrop.RATIO_16_9 to "16:9 Landscape",
                                                AspectRatioCrop.RATIO_9_16 to "9:16 Reels/TikTok",
                                                AspectRatioCrop.RATIO_1_1 to "1:1 Square",
                                                AspectRatioCrop.RATIO_4_5 to "4:5 Portrait"
                                            )
                                            items(crops) { (crop, label) ->
                                                FilterChip(
                                                    selected = cropRatio == crop,
                                                    onClick = { viewModel.setCropRatio(crop) },
                                                    label = { Text(label) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ToolOperationMode.MERGE -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔗 Multi-File Merger Queue (${mediaList.size} files)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Surface(
                                    color = Color(0xFF6366F1).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Total: ${Formatters.formatDurationMs(totalPreviewDurationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF6366F1),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (mediaList.isEmpty()) {
                                Text("No files added to merger queue yet. Tap '+ Add File' above to import multiple clips.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    mediaList.forEachIndexed { index, item ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (index == selectedIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.fillMaxWidth().clickable { viewModel.selectMediaIndex(index) }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("${index + 1}. ${item.file.name}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("${Formatters.formatDurationMs(item.durationMs)} • ${if (item.isVideo) "Video (${item.width}x${item.height})" else "Audio"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Row {
                                                    IconButton(onClick = { viewModel.moveMediaIndex(index, (index - 1).coerceAtLeast(0)) }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(onClick = { viewModel.moveMediaIndex(index, (index + 1).coerceAtMost(mediaList.size - 1)) }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(onClick = { viewModel.removeMediaIndex(index) }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Normalize Audio Volume (EBU R128)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Switch(checked = normalizeVol, onCheckedChange = { viewModel.setNormalizeVolume(it) })
                                }
                            }
                        }
                    }
                }
                ToolOperationMode.SPLIT -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("🧩 10+ Pro Auto Splitter (Equal Clips / Equal Parts)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = splitType == SplitType.BY_DURATION_SECONDS,
                                    onClick = { viewModel.setSplitType(SplitType.BY_DURATION_SECONDS) },
                                    label = { Text("By Clip Duration") },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = splitType == SplitType.BY_PARTS_COUNT,
                                    onClick = { viewModel.setSplitType(SplitType.BY_PARTS_COUNT) },
                                    label = { Text("By Equal Parts Count") },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (splitType == SplitType.BY_DURATION_SECONDS) {
                                Text("Select Clip Duration (WhatsApp Status & Reels):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    listOf(15, 30, 60).forEach { sec ->
                                        val isSelected = splitSegmentSec == sec
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.setSplitSegmentSeconds(sec) },
                                            label = { Text("${sec}s Clips") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            } else {
                                Text("Split into $splitPartsCount Equal Clips:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = splitPartsCount.toFloat(),
                                    onValueChange = { viewModel.setSplitPartsCount(it.toInt()) },
                                    valueRange = 2f..10f,
                                    steps = 7,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                ToolOperationMode.SILENCE_CUT -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("🔇 10+ Pro Silence Cut & Gap Remover", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Detects quiet pauses and strips silent gaps from your audio/video file.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Silence Threshold: ${silenceThresholdDb} dB", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Slider(
                                value = silenceThresholdDb.toFloat(),
                                onValueChange = { viewModel.setSilenceThresholdDb(it.toInt()) },
                                valueRange = -50f..-20f,
                                steps = 5
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text("Minimum Silence Pause: ${"%.1fs".format(silenceMinDurationSec)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Slider(
                                value = silenceMinDurationSec,
                                onValueChange = { viewModel.setSilenceMinDurationSec(it) },
                                valueRange = 0.2f..2.0f,
                                steps = 8
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Output Filename TextField
            OutlinedTextField(
                value = customName,
                onValueChange = { viewModel.setCustomOutputName(it) },
                label = { Text("Custom Output Filename (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Prominent Live Progress Bar Card & Cancel Export Button during Processing
            if (isExporting) {
                Spacer(modifier = Modifier.height(12.dp))
                val pct = exportProgress?.percent ?: 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(exportProgress?.currentStepText ?: "Processing...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("$pct%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.cancelExport() },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel Export", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Action Button (Process Operation)
            Button(
                onClick = { viewModel.processOperation() },
                enabled = mediaList.isNotEmpty() && !isExporting,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isExporting) {
                    val pct = exportProgress?.percent ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exporting $pct%...", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Icon(
                        imageVector = when (activeMode) {
                            ToolOperationMode.TRIM -> Icons.Default.ContentCut
                            ToolOperationMode.MERGE -> Icons.AutoMirrored.Filled.MergeType
                            ToolOperationMode.SPLIT -> Icons.AutoMirrored.Filled.CallSplit
                            ToolOperationMode.SILENCE_CUT -> Icons.Default.MicOff
                        },
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (activeMode) {
                            ToolOperationMode.TRIM -> "Export Advanced Trimmed File"
                            ToolOperationMode.MERGE -> "Export Merged Master File (${Formatters.formatDurationMs(totalPreviewDurationMs)})"
                            ToolOperationMode.SPLIT -> if (splitType == SplitType.BY_PARTS_COUNT) "Split into $splitPartsCount Equal Clips" else "Split into ${splitSegmentSec}s Clips"
                            ToolOperationMode.SILENCE_CUT -> "Auto-Cut Quiet Silence Gaps"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Export Success Card & Direct Share Intent
            exportedFile?.let { file ->
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Export Successful! 🎉", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(file.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                                type = if (file.extension.lowercase() in listOf("mp4", "mkv", "mov", "avi")) "video/*" else "audio/*"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Media via"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}
