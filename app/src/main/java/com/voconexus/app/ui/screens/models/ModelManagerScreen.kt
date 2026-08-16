package com.voconexus.app.ui.screens.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voconexus.app.core.tts.ModelStatus
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.core.tts.device.CompatibilityLevel
import com.voconexus.app.core.tts.device.CompatibilityReport

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
                title = { Text("Model & Engine Manager") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Device Compatibility Header Card
            uiState.deviceProfile?.let { profile ->
                DeviceStatusCard(
                    profileSummary = "Device: ${profile.totalRamMb / 1024} GB RAM • ${profile.cpuCores} cores • ${profile.primaryAbi}",
                    storageMb = profile.availableStorageMb
                )
            }

            // Benchmark Result Banner
            uiState.lastBenchmarkResult?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Local RTF Benchmark Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Real-Time Factor: ${String.format("%.2f", result.realTimeFactor)}x • Gen: ${result.synthesisDurationMs}ms • Peak RAM: ${result.peakMemoryMb}MB", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Installed & Available Models",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(uiState.models, key = { it.id }) { model ->
                    val report = uiState.compatibilityReports[model.id]
                    ModelCard(
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

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DeviceStatusCard(
    profileSummary: String,
    storageMb: Long
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("System Hardware Profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(profileSummary, style = MaterialTheme.typography.bodySmall)
                Text("Available Storage: ${storageMb / 1024} GB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModelCard(
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Engine: ${model.engineId} • v${model.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val statusBadgeText = when (model.status) {
                    ModelStatus.INSTALLED -> "Installed"
                    ModelStatus.DOWNLOADING -> "Downloading..."
                    ModelStatus.VERIFYING -> "Verifying..."
                    else -> "Not Installed"
                }

                val statusColor = when (model.status) {
                    ModelStatus.INSTALLED -> MaterialTheme.colorScheme.primary
                    ModelStatus.DOWNLOADING -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.secondary
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (model.status == ModelStatus.INSTALLED) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                        }
                        Text(statusBadgeText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                }

                if (model.status == ModelStatus.INSTALLED) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Model", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details row
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Size: ~${model.sizeBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall)
                Text("Languages: ${model.supportedLanguages.joinToString()}", style = MaterialTheme.typography.bodySmall)
                Text("Voices: ${model.voicesCount}", style = MaterialTheme.typography.bodySmall)
            }

            report?.let { comp ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val compIcon = if (comp.level == CompatibilityLevel.RECOMMENDED || comp.level == CompatibilityLevel.COMPATIBLE) Icons.Default.CheckCircle else Icons.Default.Warning
                    val compColor = if (comp.level == CompatibilityLevel.RECOMMENDED || comp.level == CompatibilityLevel.COMPATIBLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Icon(compIcon, contentDescription = null, tint = compColor, modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Compatibility: ${comp.level.name} (${comp.recommendedRtf})", style = MaterialTheme.typography.labelSmall, color = compColor)
                }
            }

            if (isInstallingThis) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { installProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                val phaseText = if (installProgress < 0.7f) {
                    "Downloading model archive: ${(installProgress / 0.7f * 100).toInt()}%"
                } else if (installProgress < 0.95f) {
                    "Unpacking neural weights & voice files (Please wait...)"
                } else {
                    "Finalizing installation..."
                }
                Text(phaseText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (model.status == ModelStatus.INSTALLED) {
                    if (isActiveModel) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            colors = ButtonDefaults.outlinedButtonColors(
                                disabledContentColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.height(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Active")
                        }
                    } else {
                        Button(onClick = onSetActiveClick) {
                            Text("Set Active")
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = onBenchmarkClick,
                        enabled = !isRunningBenchmark
                    ) {
                        if (isRunningBenchmark) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.height(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Benchmark RTF")
                    }
                } else {
                    Button(
                        onClick = onInstallClick,
                        enabled = !isInstallingThis
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.height(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install Model")
                    }
                }
            }
        }
    }
}
