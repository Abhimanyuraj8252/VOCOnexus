@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.voconexus.app.ui.screens.projectdetail

import com.voconexus.app.core.generation.GenerationJob
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Slider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.voconexus.app.core.data.db.GenerationJobStatus
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.util.Formatters
import com.voconexus.app.ui.components.ChunkStatusBadge
import com.voconexus.app.ui.components.VocoNexusButton
import com.voconexus.app.ui.components.VocoNexusCard
import com.voconexus.app.ui.components.VocoNexusEmptyState
import com.voconexus.app.ui.components.VocoNexusLoadingState
import com.voconexus.app.ui.components.VocoNexusProgress
import com.voconexus.app.ui.components.VocoNexusSecondaryButton
import com.voconexus.app.ui.components.VocoNexusStat
import com.voconexus.app.ui.components.VocoNexusTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel,
    onBackClick: () -> Unit,
    onNavigateScriptEditor: (String) -> Unit,
    onNavigateGenerationQueue: (String) -> Unit = {}
) {
    val project by viewModel.projectState.collectAsState()
    val parts by viewModel.partsState.collectAsState()
    val chunks by viewModel.chunksState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val completedChunks = chunks.count { it.status == ChunkStatus.COMPLETED }
    val progress = if (chunks.isNotEmpty()) completedChunks.toFloat() / chunks.size.toFloat() else 0f

    Scaffold(
        topBar = {
            VocoNexusTopBar(
                title = project?.title ?: "Project Details",
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = { onNavigateScriptEditor(viewModel.projectId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Script")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (project == null) {
            VocoNexusLoadingState(modifier = Modifier.padding(innerPadding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val audioAssets by viewModel.audioAssetsState.collectAsState()

                // Primary Tab Navigation (Scrollable & Responsive across all screen sizes)
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Count Parts that have at least 1 generated audio chunk
                    val generatedPartsCount = remember(chunks, parts) {
                        parts.count { part ->
                            chunks.any { it.partId == part.id && !it.audioPath.isNullOrBlank() && java.io.File(it.audioPath!!).exists() }
                        }
                    }
                    val tabs = listOf(
                        "Overview",
                        "Parts (${parts.size})",
                        "Chunks (${chunks.size})",
                        "Controls",
                        if (generatedPartsCount > 0) "Audio ($generatedPartsCount)" else "Audio"
                    )
                    
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { 
                                Text(
                                    title, 
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            }
                        )
                    }
                }

                val job by viewModel.jobState.collectAsState()
                val allVoices by viewModel.allVoicesState.collectAsState()

                when (selectedTab) {
                    0 -> OverviewTabContent(
                        project = project!!,
                        parts = parts,
                        chunks = chunks,
                        allVoices = allVoices,
                        progress = progress,
                        job = job,
                        onStartGeneration = viewModel::startGeneration,
                        onPauseJob = viewModel::pauseJob,
                        onCancelJob = viewModel::cancelJob,
                        onEditScript = { onNavigateScriptEditor(viewModel.projectId) },
                        onUpdateVoice = { voiceId, engineId -> viewModel.updateProjectVoice(voiceId, engineId) }
                    )
                    1 -> PartsTabContent(parts = parts, chunks = chunks, job = job, viewModel = viewModel, currentVoiceId = chunks.firstOrNull()?.voiceId ?: "af_heart", engineId = (viewModel.defaultEngineIdState.collectAsState().value))
                    2 -> ChunksTabContent(chunks = chunks, job = job, viewModel = viewModel, engineId = (viewModel.defaultEngineIdState.collectAsState().value))
                    3 -> SpeechControlsTabContent(projectId = viewModel.projectId, currentVoiceId = chunks.firstOrNull()?.voiceId ?: "af_heart")
                    4 -> GeneratedAudioTabContent(projectTitle = project!!.title, parts = parts, chunks = chunks, audioAssets = audioAssets, job = job, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun SpeechControlsTabContent(projectId: String, currentVoiceId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = (context.applicationContext as com.voconexus.app.VocoNexusApplication).container
    val viewModel: com.voconexus.app.ui.screens.speechcontrols.SpeechControlsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.voconexus.app.ui.screens.speechcontrols.SpeechControlsViewModel.Factory(
            projectId = projectId,
            projectRepository = container.projectRepository,
            durationEstimator = container.durationEstimator,
            targetPlanner = container.targetDurationPlanner,
            previewManager = container.speechPreviewManager,
            prefsManager = container.userPreferencesManager
        )
    )
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            com.voconexus.app.ui.components.SpeechControlsCard(
                speed = state.speed,
                pitchSemitones = state.pitchSemitones,
                targetDurationMs = state.targetDurationMs,
                estimatedDurationMs = state.estimatedDurationMs,
                naturalnessLevel = state.naturalnessLevel,
                speedWarning = state.speedWarning,
                pitchWarning = state.pitchWarning,
                isPreviewPlaying = state.isPreviewPlaying,
                onSpeedChange = viewModel::updateSpeed,
                onPitchChange = viewModel::updatePitch,
                onTargetDurationChange = viewModel::updateTargetDuration,
                onPreviewClick = { viewModel.playPreview(currentVoiceId) },
                onStopPreviewClick = viewModel::stopPreview
            )
        }
    }
}

@Composable
fun OverviewTabContent(
    project: ProjectEntity,
    parts: List<PartEntity>,
    chunks: List<ChunkEntity>,
    allVoices: List<com.voconexus.app.core.data.db.TtsVoiceEntity>,
    progress: Float,
    job: com.voconexus.app.core.generation.GenerationJob? = null,
    onStartGeneration: () -> Unit,
    onPauseJob: () -> Unit = {},
    onCancelJob: () -> Unit = {},
    onEditScript: () -> Unit,
    onUpdateVoice: (String, String) -> Unit = { _, _ -> }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val estDurationStr = Formatters.formatDurationMs(project.estimatedDurationMs)
    val actDurationStr = Formatters.formatDurationMs(project.actualDurationMs)
    val currentVoiceId = chunks.firstOrNull()?.voiceId ?: "af_heart"
    val currentEngineId = chunks.firstOrNull()?.engineId ?: (context.applicationContext as com.voconexus.app.VocoNexusApplication).container.userPreferencesManager.preferences.value.defaultEngineId
    
    val engineVoices = remember(allVoices, currentEngineId) {
        val norm = currentEngineId.lowercase()
        val dbFiltered = allVoices.filter { 
            val e = it.engineId.lowercase()
            val m = it.modelId.lowercase()
            (norm.contains("kokoro") && (e.contains("kokoro") || m.contains("kokoro"))) ||
            (norm.contains("edge") && e.contains("edge")) ||
            (norm.contains("google") && e.contains("google")) ||
            (norm.contains("piper") && (e.contains("piper") || m.contains("piper"))) ||
            (norm.contains("sherpa") && e.contains("sherpa")) ||
            (norm.contains("android") && e.contains("android")) ||
            e == norm
        }

        if (dbFiltered.isNotEmpty()) {
            dbFiltered.map { it.id to it.name }
        } else {
            when {
                norm.contains("kokoro") -> listOf(
                    "hf_alpha" to "Alpha (Kokoro Hindi Female / हिन्दी 🇮🇳)",
                    "hm_omega" to "Omega (Kokoro Hindi Male / हिन्दी 🇮🇳)",
                    "hf_beta" to "Beta (Kokoro Hindi Female / हिन्दी 🇮🇳)",
                    "hf_psi" to "Psi (Kokoro Hindi Female / हिन्दी 🇮🇳)",
                    "af_heart" to "AF Heart (US English Female)",
                    "af_bella" to "AF Bella (US English Female)",
                    "af_sky" to "AF Sky (US English Female)",
                    "af_nicole" to "AF Nicole (US English Female)",
                    "am_adam" to "AM Adam (US English Male)",
                    "am_michael" to "AM Michael (US English Male)",
                    "bf_emma" to "BF Emma (UK English Female)",
                    "bm_george" to "BM George (UK English Male)",
                    "ff_siwis" to "FF Siwis (French Female 🇫🇷)",
                    "ef_dora" to "EF Dora (Spanish Female 🇪🇸)",
                    "em_alex" to "EM Alex (Spanish Male 🇪🇸)",
                    "if_sara" to "IF Sara (Italian Female 🇮🇹)",
                    "jf_alpha" to "JF Alpha (Japanese Female 🇯🇵)",
                    "zf_xiaoxiao" to "ZF Xiaoxiao (Mandarin Female 🇨🇳)",
                    "zm_yunjian" to "ZM Yunjian (Mandarin Male 🇨🇳)"
                )
                norm.contains("edge") -> listOf(
                    "hi-IN-SwaraNeural" to "Swara (Edge Hindi Female / हिन्दी 🇮🇳)",
                    "hi-IN-MadhurNeural" to "Madhur (Edge Hindi Male / हिन्दी 🇮🇳)",
                    "en-IN-NeerjaNeural" to "Neerja (Edge Indian Female 🇮🇳)",
                    "en-IN-PrabhatNeural" to "Prabhat (Edge Indian Male 🇮🇳)",
                    "en-US-AvaMultilingualNeural" to "Ava (Edge US Multilingual Female)",
                    "en-US-AndrewMultilingualNeural" to "Andrew (Edge US Multilingual Male)",
                    "en-US-BrianMultilingualNeural" to "Brian (Edge US Multilingual Male)",
                    "en-US-EmmaMultilingualNeural" to "Emma (Edge US Multilingual Female)",
                    "en-US-JennyNeural" to "Jenny (Edge US English Female)",
                    "en-US-GuyNeural" to "Guy (Edge US English Male)",
                    "en-GB-SoniaNeural" to "Sonia (Edge UK English Female)",
                    "en-GB-RyanNeural" to "Ryan (Edge UK English Male)"
                )
                norm.contains("google") -> listOf(
                    "hi-IN-Wavenet-A" to "Google Swara (Hindi Female / हिन्दी 🇮🇳)",
                    "hi-IN-Wavenet-B" to "Google Madhur (Hindi Male / हिन्दी 🇮🇳)",
                    "hi-IN-Neural2-A" to "Google Neural2 (Hindi Female / हिन्दी 🇮🇳)",
                    "hi-IN-Neural2-B" to "Google Neural2 (Hindi Male / हिन्दी 🇮🇳)",
                    "en-US-Journey-F" to "Google Journey (Female)",
                    "en-US-Journey-D" to "Google Journey (Male)",
                    "en-US-Neural2-F" to "Google Neural2 (US Female)",
                    "en-US-Neural2-D" to "Google Neural2 (US Male)"
                )
                norm.contains("piper") -> listOf(
                    "piper_hi_female" to "Piper Swara (Hindi Female / हिन्दी 🇮🇳)",
                    "piper_hi_male" to "Piper Madhur (Hindi Male / हिन्दी 🇮🇳)",
                    "piper_en_lessac" to "Piper Lessac (US English Female)",
                    "piper_en_ryan" to "Piper Ryan (US English Male)"
                )
                norm.contains("sherpa") -> listOf(
                    "sherpa_hi_alpha" to "Sherpa Alpha (Hindi Female / हिन्दी 🇮🇳)",
                    "sherpa_hi_omega" to "Sherpa Omega (Hindi Male / हिन्दी 🇮🇳)",
                    "sherpa_af_heart" to "Sherpa Heart (US Female)",
                    "sherpa_am_adam" to "Sherpa Adam (US Male)"
                )
                else -> listOf(
                    "hf_alpha" to "Alpha (Kokoro Hindi Female / हिन्दी 🇮🇳)",
                    "hm_omega" to "Omega (Kokoro Hindi Male / हिन्दी 🇮🇳)",
                    "af_heart" to "AF Heart (US English Female)",
                    "am_adam" to "AM Adam (US English Male)"
                )
            }
        }
    }

    val engineOptions = listOf(
        "kokoro-v1.0" to "Kokoro v1.0",
        "sherpa-onnx" to "Sherpa ONNX",
        "piper-tts" to "Piper TTS",
        "edge-tts" to "Microsoft Edge TTS",
        "google-cloud-tts" to "Google Cloud TTS",
        "android-tts" to "Android Built-in TTS"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (job != null && (job.status == GenerationJobStatus.RUNNING || job.status == GenerationJobStatus.QUEUED || job.status == GenerationJobStatus.PAUSED)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Generation: ${job.status}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${job.completedChunks}/${job.totalChunks} Chunks (${if (job.totalChunks > 0) (job.completedChunks * 100 / job.totalChunks) else 0}%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (job.totalChunks > 0) job.completedChunks.toFloat() / job.totalChunks.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (job.status == GenerationJobStatus.RUNNING) {
                                OutlinedButton(onClick = onPauseJob, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pause")
                                }
                            } else if (job.status == GenerationJobStatus.PAUSED) {
                                Button(onClick = onStartGeneration, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resume")
                                }
                            }
                            OutlinedButton(
                                onClick = onCancelJob,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (project.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    VocoNexusProgress(
                        progress = progress,
                        statusText = "Generation Status: ${project.status}"
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Target Engine & Neural Voice",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    var voiceMenuExpanded by remember { mutableStateOf(false) }
                    var engineMenuExpanded by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { engineMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Engine: ${engineOptions.find { it.first == currentEngineId }?.second ?: currentEngineId}",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { voiceMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Voice: ${engineVoices.find { it.first == currentVoiceId }?.second ?: currentVoiceId}",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                val container = (context.applicationContext as com.voconexus.app.VocoNexusApplication).container
                                val voice = com.voconexus.app.core.tts.TtsVoice(
                                    id = currentVoiceId,
                                    modelId = "kokoro-v1.0",
                                    engineId = currentEngineId,
                                    name = engineVoices.find { it.first == currentVoiceId }?.second ?: currentVoiceId,
                                    language = if (currentVoiceId.startsWith("h")) "hi" else "en",
                                    locale = if (currentVoiceId.startsWith("h")) "hi-IN" else "en-US"
                                )
                                kotlinx.coroutines.MainScope().launch {
                                    try {
                                        val engine = container.ttsEngineRegistry.getRequiredEngine(currentEngineId)
                                        container.voicePreviewManager.generateAndPlayPreview(engine, voice)
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Voice Preview", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }

                        if (voiceMenuExpanded) {
                            Dialog(onDismissRequest = { voiceMenuExpanded = false }) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 300.dp, max = 500.dp)
                                        .padding(8.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 12.dp
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(20.dp)
                                    ) {
                                        Text(
                                            text = "Select Neural Voice",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        LazyColumn(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(engineVoices, key = { it.first }) { (vId, label) ->
                                                val isSelected = vId == currentVoiceId
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                        )
                                                        .clickable {
                                                            onUpdateVoice(vId, currentEngineId)
                                                            voiceMenuExpanded = false
                                                        }
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isSelected) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                    } else {
                                                        Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "Unselected", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                    }
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { voiceMenuExpanded = false },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Close")
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (engineMenuExpanded) {
                            Dialog(onDismissRequest = { engineMenuExpanded = false }) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 250.dp, max = 450.dp)
                                        .padding(8.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 12.dp
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(20.dp)
                                    ) {
                                        Text(
                                            text = "Select Speech Engine",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        LazyColumn(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(engineOptions, key = { it.first }) { (eId, label) ->
                                                val isSelected = eId == currentEngineId
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                        .clickable {
                                                            val defaultVoiceForEngine = allVoices.find { it.engineId == eId }?.id ?: "af_heart"
                                                            onUpdateVoice(defaultVoiceForEngine, eId)
                                                            engineMenuExpanded = false
                                                        }
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isSelected) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                    } else {
                                                        Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "Unselected", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                    }
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { engineMenuExpanded = false },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Close")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VocoNexusStat(
                    label = "Est. Duration",
                    value = estDurationStr,
                    icon = Icons.Default.Queue,
                    modifier = Modifier.weight(1f)
                )
                VocoNexusStat(
                    label = "Generated",
                    value = actDurationStr,
                    icon = Icons.Default.AudioFile,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VocoNexusStat(
                    label = "Parts",
                    value = Formatters.formatNumber(parts.size.toLong()),
                    icon = Icons.AutoMirrored.Filled.List,
                    modifier = Modifier.weight(1f)
                )
                VocoNexusStat(
                    label = "Chunks",
                    value = Formatters.formatNumber(chunks.size.toLong()),
                    icon = Icons.Default.Tune,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStartGeneration,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Audio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                val container = (context.applicationContext as com.voconexus.app.VocoNexusApplication).container
                val isPlayingPreview by container.audioPreviewPlayer.isPlaying.collectAsState()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPlayingPreview) {
                        OutlinedButton(
                            onClick = { container.audioPreviewPlayer.pause() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { container.audioPreviewPlayer.stop() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                kotlinx.coroutines.MainScope().launch {
                                    try {
                                        val textToPlay = chunks.joinToString(" ") { it.normalizedText }.take(1000)
                                        container.speechPreviewManager.playSpeechPreview(
                                            sampleText = textToPlay.ifBlank { "No text available." },
                                            voiceId = currentVoiceId,
                                            engineId = currentEngineId
                                        )
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Script", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play Script", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = onEditScript,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Script", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Script", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveJobProgressCard(
    job: GenerationJob?,
    onStopJob: () -> Unit
) {
    if (job == null) return
    val isJobActive = job.status == GenerationJobStatus.RUNNING ||
            job.status == GenerationJobStatus.QUEUED ||
            job.status == GenerationJobStatus.STARTING

    val isStopping = job.status == GenerationJobStatus.STOP_REQUESTED

    if (isJobActive || isStopping) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isStopping) "Stopping Generation..." else "Generating: ${job.completedChunks} / ${job.totalChunks} Chunks",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${(job.progressFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isJobActive) {
                        FilledTonalButton(
                            onClick = onStopJob,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { job.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                )
            }
        }
    }
}

@Composable
fun PartsTabContent(
    parts: List<PartEntity>,
    chunks: List<ChunkEntity>,
    job: GenerationJob?,
    viewModel: ProjectDetailViewModel,
    currentVoiceId: String,
    engineId: String = "kokoro-82m"
) {
    var selectedPartIds by remember { mutableStateOf(setOf<String>()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (parts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            VocoNexusEmptyState(
                title = "No Parts Found",
                description = "This script has not been split into parts yet."
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            ActiveJobProgressCard(job = job, onStopJob = viewModel::stopJob)

            // Selection & Action Header Bar
            val isAllSelected = parts.isNotEmpty() && selectedPartIds.size == parts.size
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        selectedPartIds = if (isAllSelected || selectedPartIds.isNotEmpty()) emptySet() else parts.map { it.id }.toSet()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isAllSelected || selectedPartIds.isNotEmpty()) "Deselect All" else "Select All (${selectedPartIds.size}/${parts.size})",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Button(
                    onClick = { viewModel.startGeneration(selectedPartIds = selectedPartIds.toList()) },
                    enabled = selectedPartIds.isNotEmpty() || parts.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Generate (${if (selectedPartIds.isEmpty()) parts.size else selectedPartIds.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(parts, key = { it.id }) { part ->
                    val isSelected = selectedPartIds.contains(part.id)
                    val partChunks = remember(chunks, part.id) { chunks.filter { it.partId == part.id } }
                    val partScriptText = remember(partChunks) { partChunks.joinToString(" ") { it.normalizedText } }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedPartIds = if (checked) selectedPartIds + part.id else selectedPartIds - part.id
                                    }
                                )
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                    Text(
                                        text = part.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${part.chunkCount} Chunks • ${Formatters.formatNumber(part.wordCount.toLong())} words",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (partScriptText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = partScriptText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val container = (context.applicationContext as com.voconexus.app.VocoNexusApplication).container
                                val isPlayingPreview by container.audioPreviewPlayer.isPlaying.collectAsState()

                                if (isPlayingPreview) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.clickable { container.audioPreviewPlayer.pause() }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pause", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.clickable { container.audioPreviewPlayer.stop() }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Stop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.clickable {
                                            kotlinx.coroutines.MainScope().launch {
                                                try {
                                                    container.speechPreviewManager.playSpeechPreview(
                                                        sampleText = partScriptText.ifBlank { "Reading ${part.title} script speech." },
                                                        voiceId = currentVoiceId,
                                                        engineId = engineId
                                                    )
                                                } catch (e: Exception) { e.printStackTrace() }
                                            }
                                        }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Play Preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.clickable { viewModel.startGeneration(selectedPartIds = listOf(part.id)) }
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bolt, contentDescription = "Generate", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Generate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChunksTabContent(
    chunks: List<ChunkEntity>,
    job: GenerationJob?,
    viewModel: ProjectDetailViewModel,
    engineId: String = "kokoro-82m"
) {
    var selectedChunkIds by remember { mutableStateOf(setOf<String>()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (chunks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            VocoNexusEmptyState(
                title = "No Chunks Found",
                description = "No chunks available in this project."
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            ActiveJobProgressCard(job = job, onStopJob = viewModel::stopJob)

            // Selection & Action Header Bar
            val isAllSelected = chunks.isNotEmpty() && selectedChunkIds.size == chunks.size
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        selectedChunkIds = if (isAllSelected || selectedChunkIds.isNotEmpty()) emptySet() else chunks.map { it.id }.toSet()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isAllSelected || selectedChunkIds.isNotEmpty()) "Deselect All" else "Select All (${selectedChunkIds.size}/${chunks.size})",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Button(
                    onClick = { viewModel.startGeneration(selectedChunkIds = selectedChunkIds.toList()) },
                    enabled = selectedChunkIds.isNotEmpty() || chunks.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Generate (${if (selectedChunkIds.isEmpty()) chunks.size else selectedChunkIds.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(chunks, key = { it.id }) { chunk ->
                    val isSelected = selectedChunkIds.contains(chunk.id)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedChunkIds = if (checked) selectedChunkIds + chunk.id else selectedChunkIds - chunk.id
                                    }
                                )
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Chunk #${chunk.sequenceIndex + 1}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        ChunkStatusBadge(status = chunk.status)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = chunk.normalizedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val container = (context.applicationContext as com.voconexus.app.VocoNexusApplication).container
                                val isPlayingPreview by container.audioPreviewPlayer.isPlaying.collectAsState()

                                if (isPlayingPreview) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.clickable { container.audioPreviewPlayer.pause() }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pause", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.clickable { container.audioPreviewPlayer.stop() }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Stop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.clickable {
                                            kotlinx.coroutines.MainScope().launch {
                                                try {
                                                    container.speechPreviewManager.playSpeechPreview(
                                                        sampleText = chunk.normalizedText.take(500),
                                                        voiceId = chunk.voiceId,
                                                        engineId = engineId
                                                    )
                                                } catch (e: Exception) { e.printStackTrace() }
                                            }
                                        }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Play", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.clickable { viewModel.startGeneration(selectedChunkIds = listOf(chunk.id)) }
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bolt, contentDescription = "Generate", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Generate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class GeneratedPartAudioGroup(
    val id: String,
    val title: String,
    val chunkCount: Int,
    val completedChunkCount: Int,
    val chunks: List<ChunkEntity>,
    val totalDurationMs: Long,
    val totalSizeBytes: Long
)

@Composable
fun GeneratedAudioTabContent(
    projectTitle: String,
    parts: List<PartEntity>,
    chunks: List<ChunkEntity>,
    audioAssets: List<com.voconexus.app.core.data.db.AudioAssetEntity>,
    job: GenerationJob? = null,
    viewModel: ProjectDetailViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isCombining by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedExportFormat by remember { mutableStateOf("MP3") }

    val partAudioGroups = remember(chunks, parts) {
        val groups = mutableListOf<GeneratedPartAudioGroup>()
        parts.forEach { part ->
            val partChunks = chunks.filter { it.partId == part.id }
            val completed = partChunks.filter { !it.audioPath.isNullOrBlank() && File(it.audioPath!!).exists() }
            if (completed.isNotEmpty()) {
                groups.add(
                    GeneratedPartAudioGroup(
                        id = part.id,
                        title = part.title,
                        chunkCount = partChunks.size,
                        completedChunkCount = completed.size,
                        chunks = completed,
                        totalDurationMs = completed.sumOf { it.durationMs },
                        totalSizeBytes = completed.sumOf { it.fileSizeBytes }
                    )
                )
            }
        }
        val knownPartIds = parts.map { it.id }.toSet()
        val orphanCompleted = chunks.filter { !knownPartIds.contains(it.partId) && !it.audioPath.isNullOrBlank() && File(it.audioPath!!).exists() }
        if (orphanCompleted.isNotEmpty()) {
            groups.add(
                GeneratedPartAudioGroup(
                    id = "orphan_audio",
                    title = "Generated Audio Chunks",
                    chunkCount = orphanCompleted.size,
                    completedChunkCount = orphanCompleted.size,
                    chunks = orphanCompleted,
                    totalDurationMs = orphanCompleted.sumOf { it.durationMs },
                    totalSizeBytes = orphanCompleted.sumOf { it.fileSizeBytes }
                )
            )
        }
        groups
    }

    var selectedGroupIds by remember { mutableStateOf(setOf<String>()) }

    if (showExportDialog) {
        val targetGroups = partAudioGroups.filter { selectedGroupIds.contains(it.id) || selectedGroupIds.isEmpty() }
        val targetChunks = targetGroups.flatMap { it.chunks }
        val totalDurationMs = targetChunks.sumOf { it.durationMs }
        val totalDurationSec = totalDurationMs / 1000
        val minutes = totalDurationSec / 60
        val seconds = totalDurationSec % 60

        val estimatedMp3SizeMb = String.format("%.1f", (totalDurationSec * 192.0 * 1000 / 8) / (1024 * 1024))
        val estimatedWavSizeMb = String.format("%.1f", (totalDurationSec * 24000.0 * 2) / (1024 * 1024))

        AlertDialog(
            onDismissRequest = { if (!isCombining) showExportDialog = false },
            title = {
                Text("Export Project Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Combining ${targetChunks.size} audio chunks (${minutes}m ${seconds}s total duration). Select format:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        onClick = { selectedExportFormat = "MP3" },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedExportFormat == "MP3") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (selectedExportFormat == "MP3") MaterialTheme.colorScheme.primary else Color.Transparent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedExportFormat == "MP3",
                                onClick = { selectedExportFormat = "MP3" }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "MP3 (Compressed)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selectedExportFormat == "MP3") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "192kbps • Recommended (~$estimatedMp3SizeMb MB)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { selectedExportFormat = "WAV" },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedExportFormat == "WAV") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (selectedExportFormat == "WAV") MaterialTheme.colorScheme.primary else Color.Transparent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedExportFormat == "WAV",
                                onClick = { selectedExportFormat = "WAV" }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "WAV (Lossless PCM)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selectedExportFormat == "WAV") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Uncompressed audio (~$estimatedWavSizeMb MB)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        isCombining = true
                        kotlinx.coroutines.MainScope().launch {
                            try {
                                val path = viewModel.combineAndExportAudio(
                                    selectedChunks = targetChunks,
                                    projectTitle = projectTitle,
                                    exportFormat = selectedExportFormat,
                                    context = context
                                )
                                android.widget.Toast.makeText(context, "Exported $selectedExportFormat to Music/VocoNexus folder!", android.widget.Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            } finally {
                                isCombining = false
                            }
                        }
                    },
                    enabled = !isCombining
                ) {
                    Text(if (isCombining) "Exporting..." else "Export")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    enabled = !isCombining
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ActiveJobProgressCard(job = job, onStopJob = viewModel::stopJob)
        val isAllSelected = partAudioGroups.isNotEmpty() && selectedGroupIds.size == partAudioGroups.size
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    selectedGroupIds = if (isAllSelected || selectedGroupIds.isNotEmpty()) emptySet() else partAudioGroups.map { it.id }.toSet()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isAllSelected || selectedGroupIds.isNotEmpty()) "Deselect All" else "Select All (${selectedGroupIds.size}/${partAudioGroups.size})",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectedGroupIds.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val chunksToDelete = partAudioGroups.filter { selectedGroupIds.contains(it.id) }.flatMap { it.chunks }.map { it.id }
                            viewModel.deleteChunkAudios(chunksToDelete)
                            selectedGroupIds = emptySet()
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                    }
                }

                Button(
                    onClick = {
                        showExportDialog = true
                    },
                    enabled = !isCombining && (partAudioGroups.isNotEmpty() || audioAssets.isNotEmpty()),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCombining) "Stitching..." else "Combine & Export (${if (selectedGroupIds.isEmpty()) partAudioGroups.size else selectedGroupIds.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (partAudioGroups.isEmpty() && audioAssets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                VocoNexusEmptyState(
                    title = "No Generated Audio Parts",
                    description = "Select parts from the Parts tab and tap Generate to create audio."
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(partAudioGroups, key = { "group_${it.id}" }) { group ->
                    val isSelected = selectedGroupIds.contains(group.id)
                    val container = (context.applicationContext as com.voconexus.app.VocoNexusApplication).container
                    val isPlayingPreview by container.audioPreviewPlayer.isPlaying.collectAsState()
                    val activeVoiceId by container.audioPreviewPlayer.activeVoiceId.collectAsState()
                    val currentPositionMs by container.audioPreviewPlayer.currentPositionMs.collectAsState()
                    val playerDurationMs by container.audioPreviewPlayer.durationMs.collectAsState()

                    val isThisGroupPlaying = activeVoiceId == group.id && isPlayingPreview

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedGroupIds = if (checked) selectedGroupIds + group.id else selectedGroupIds - group.id
                                    }
                                )
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = group.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val badgeColor = when {
                                            group.completedChunkCount == group.chunkCount && group.chunkCount > 0 -> MaterialTheme.colorScheme.primaryContainer
                                            group.completedChunkCount > 0 -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                        val badgeTextColor = when {
                                            group.completedChunkCount == group.chunkCount && group.chunkCount > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
                                            group.completedChunkCount > 0 -> MaterialTheme.colorScheme.onTertiaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        val badgeText = when {
                                            group.completedChunkCount == group.chunkCount && group.chunkCount > 0 -> "READY"
                                            group.completedChunkCount > 0 -> "${group.completedChunkCount}/${group.chunkCount} READY"
                                            else -> "NOT GENERATED"
                                        }
                                        Surface(
                                            color = badgeColor,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = badgeText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = badgeTextColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${group.completedChunkCount}/${group.chunkCount} Chunks • ${Formatters.formatDurationMs(group.totalDurationMs)} • ${Formatters.formatFileSize(group.totalSizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (activeVoiceId == group.id) {
                                val totalMs = if (playerDurationMs > 0L) playerDurationMs else group.totalDurationMs
                                val currentMs = currentPositionMs.coerceIn(0L, totalMs.coerceAtLeast(1L))
                                val activeChunkIdx by container.audioPreviewPlayer.activeChunkIndex.collectAsState()
                                val totalChunks by container.audioPreviewPlayer.totalChunksCount.collectAsState()

                                Spacer(modifier = Modifier.height(8.dp))
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            Formatters.formatDurationMs(currentMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (totalChunks > 1) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Chunk ${activeChunkIdx + 1}/$totalChunks",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            Formatters.formatDurationMs(totalMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = currentMs.toFloat(),
                                        onValueChange = { newPos ->
                                            container.audioPreviewPlayer.seekTo(newPos.toLong())
                                        },
                                        valueRange = 0f..totalMs.coerceAtLeast(1L).toFloat(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (group.completedChunkCount < group.chunkCount) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier.clickable {
                                            viewModel.startGeneration(selectedPartIds = listOf(group.id))
                                            android.widget.Toast.makeText(context, "Generating audio for ${group.title}...", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Bolt, contentDescription = "Generate Audio", tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Generate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (group.completedChunkCount > 0) {
                                    if (isThisGroupPlaying) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.clickable { container.audioPreviewPlayer.pause() }
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Pause", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            modifier = Modifier.clickable { container.audioPreviewPlayer.stop() }
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Stop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.clickable {
                                                if (activeVoiceId == group.id && !isPlayingPreview) {
                                                    // Resume paused playback for this group
                                                    container.audioPreviewPlayer.resume()
                                                } else {
                                                    // Play ALL completed chunks gaplessly as a playlist
                                                    val sortedFiles = group.chunks
                                                        .sortedBy { it.sequenceIndex }
                                                        .mapNotNull { chunk ->
                                                            chunk.audioPath?.let { path ->
                                                                val f = File(path)
                                                                if (f.exists()) f else null
                                                            }
                                                        }
                                                    if (sortedFiles.isNotEmpty()) {
                                                        container.audioPreviewPlayer.playFiles(group.id, sortedFiles)
                                                    }
                                                }
                                            }
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Play Audio", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Play All", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.clickable {
                                            if (group.completedChunkCount < group.chunkCount) {
                                                android.widget.Toast.makeText(context, "Part generation incomplete (${group.completedChunkCount}/${group.chunkCount} ready). Generate all chunks first.", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                kotlinx.coroutines.MainScope().launch {
                                                    try {
                                                        viewModel.combineAndExportAudio(
                                                            selectedChunks = group.chunks,
                                                            projectTitle = projectTitle,
                                                            exportFormat = "MP3",
                                                            context = context
                                                        )
                                                        android.widget.Toast.makeText(context, "Exported ${group.title} to Music/VocoNexus", android.widget.Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Download, contentDescription = "Export", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Export", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.clickable {
                                            viewModel.deleteChunkAudios(group.chunks.map { it.id })
                                            selectedGroupIds = selectedGroupIds - group.id
                                        }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
