package com.voconexus.app.ui.screens.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voconexus.app.core.tts.ModelStatus
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.core.tts.device.CompatibilityLevel
import com.voconexus.app.core.tts.device.CompatibilityReport
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    viewModel: ModelManagerViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Model & Engine Manager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hardware Profiling & Active Engine Selection",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Device Hardware Profile Card
            uiState.deviceProfile?.let { profile ->
                item {
                    HardwareProfileCard(
                        ramGb = profile.totalRamMb / 1024,
                        cores = profile.cpuCores,
                        abi = profile.primaryAbi,
                        storageMb = profile.availableStorageMb
                    )
                }
            }

            // 2. RTF Benchmark Result Banner
            uiState.lastBenchmarkResult?.let { result ->
                item {
                    BenchmarkResultCard(result = result)
                }
            }

            // 3. Section Title
            item {
                Text(
                    text = "Installed & Available Engines (${uiState.models.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // 4. Models List
            items(uiState.models, key = { it.id }) { model ->
                val report = uiState.compatibilityReports[model.id]
                ManagerModelCard(
                    model = model,
                    report = report,
                    isInstallingThis = uiState.installingModelId == model.id,
                    installProgress = uiState.installProgress,
                    isRunningBenchmark = uiState.isRunningBenchmark,
                    isActiveModel = uiState.activeModelId == model.id,
                    onInstallClick = { viewModel.installModel(model.id) },
                    onDeleteClick = { viewModel.deleteModel(model.id) },
                    onBenchmarkClick = { viewModel.runBenchmark(model.id) },
                    onSetActiveClick = { viewModel.setActiveModel(model.id) }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Components & Cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HardwareProfileCard(ramGb: Int, cores: Int, abi: String, storageMb: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text("System Hardware Profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${ramGb}GB RAM • $cores Cores CPU • $abi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Free Storage: ${storageMb / 1024} GB",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BenchmarkResultCard(result: com.voconexus.app.core.tts.device.BenchmarkResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Local RTF Benchmark Result", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                Text(
                    text = "Real-Time Factor: ${String.format(Locale.US, "%.2f", result.realTimeFactor)}x • Duration: ${result.synthesisDurationMs}ms • RAM: ${result.peakMemoryMb}MB",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ManagerModelCard(
    model: TtsModel,
    report: CompatibilityReport?,
    isInstallingThis: Boolean,
    installProgress: Float,
    isRunningBenchmark: Boolean,
    isActiveModel: Boolean,
    onInstallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onBenchmarkClick: () -> Unit,
    onSetActiveClick: () -> Unit
) {
    val isCloudOrSystem = model.id in setOf("edge-tts", "google-cloud-tts", "android-tts") || model.sizeBytes == 0L
    val effectiveStatus = if (isCloudOrSystem) ModelStatus.INSTALLED else model.status
    val sizeMb = if (isCloudOrSystem) "Cloud / System Engine" else "~${model.sizeBytes / (1024 * 1024)} MB"

    val gradientColors = if (isActiveModel) {
        listOf(Color(0xFF10B981), Color(0xFF059669))
    } else if (isCloudOrSystem) {
        listOf(Color(0xFF3B82F6), Color(0xFF06B6D4))
    } else {
        listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    }

    val cardBorder = if (isActiveModel) {
        BorderStroke(2.dp, Color(0xFF10B981))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = cardBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header Row: Icon + Name + Active/Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isActiveModel) Icons.Default.CheckCircle else if (isCloudOrSystem) Icons.Default.CloudQueue else Icons.Default.Memory,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = model.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Engine: ${model.engineId} • v${model.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                if (isActiveModel) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF10B981)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Active Engine", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                } else {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        val labelText = when {
                            model.id == "android-tts" -> "Built-in System"
                            isCloudOrSystem -> "Online Cloud"
                            effectiveStatus == ModelStatus.INSTALLED -> "Installed"
                            else -> "Not Installed"
                        }
                        Text(labelText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(10.dp))

            // Info Chips Row
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChipInfoItem(icon = Icons.Default.SdStorage, text = sizeMb)
                ChipInfoItem(icon = Icons.Default.RecordVoiceOver, text = "${model.voicesCount} Voices")
                ChipInfoItem(icon = Icons.Default.Language, text = "${model.supportedLanguages.size} Languages")
            }

            report?.let { comp ->
                if (!isCloudOrSystem) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val compColor = if (comp.level == CompatibilityLevel.RECOMMENDED || comp.level == CompatibilityLevel.COMPATIBLE) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                        Icon(Icons.Default.Speed, contentDescription = null, tint = compColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Compatibility: ${comp.level.name} (${comp.recommendedRtf})",
                            style = MaterialTheme.typography.labelSmall,
                            color = compColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isInstallingThis) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { installProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(4.dp))
                val phaseText = if (installProgress < 0.7f) {
                    "Downloading model weights: ${(installProgress / 0.7f * 100).toInt()}%"
                } else {
                    "Finalizing model setup..."
                }
                Text(phaseText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(14.dp))

            // Action Buttons Row: Set Active, Benchmark, Delete, Install
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (effectiveStatus == ModelStatus.INSTALLED || isCloudOrSystem) {
                    if (!isActiveModel) {
                        Button(
                            onClick = onSetActiveClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Set Active", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    if (!isCloudOrSystem) {
                        OutlinedButton(
                            onClick = onBenchmarkClick,
                            enabled = !isRunningBenchmark,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isRunningBenchmark) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Benchmark", style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onDeleteClick) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Model", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    Button(
                        onClick = onInstallClick,
                        enabled = !isInstallingThis,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Install Model", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipInfoItem(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
