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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.voconexus.app.core.tts.ModelStatus
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.ui.components.VocoNexusEmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel,
    onBackClick: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateVoices: () -> Unit,
    onNavigateAudio: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val models by viewModel.modelsState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Speech Models & Engines",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Neural Speech & Cloud Voice Engines",
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (models.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    VocoNexusEmptyState(
                        title = "No Models Available",
                        description = "Initializing speech engines catalog...",
                        icon = Icons.Default.GraphicEq
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            text = "Available Speech Engines (${models.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    items(models, key = { it.id }) { model ->
                        ModelListItemCard(
                            model = model,
                            onDownloadClick = { viewModel.downloadModel(model.id) },
                            onDeleteClick = { viewModel.deleteModel(model.id) }
                        )
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ModelListItemCard(
    model: TtsModel,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isCloudOrSystem = model.id in setOf("edge-tts", "google-cloud-tts", "android-tts") || model.sizeBytes == 0L
    val effectiveStatus = if (isCloudOrSystem) ModelStatus.INSTALLED else model.status
    val sizeMb = if (isCloudOrSystem) "Cloud Engine" else "~${model.sizeBytes / (1024 * 1024)} MB"

    val gradientColors = if (isCloudOrSystem) {
        listOf(Color(0xFF10B981), Color(0xFF059669))
    } else {
        listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header Row: Icon + Title + Status Badge
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
                            imageVector = if (isCloudOrSystem) Icons.Default.CloudQueue else Icons.Default.Memory,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Engine: ${model.engineId} • v${model.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCloudOrSystem || effectiveStatus == ModelStatus.INSTALLED) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    val labelText = when {
                        model.id == "android-tts" -> "Built-in System"
                        isCloudOrSystem -> "Online Cloud"
                        effectiveStatus == ModelStatus.INSTALLED -> "Installed"
                        else -> "Available"
                    }
                    val labelColor = if (isCloudOrSystem || effectiveStatus == ModelStatus.INSTALLED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = labelColor, modifier = Modifier.size(14.dp))
                        Text(labelText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = labelColor)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(10.dp))

            // Details Row: Size, Voices, Languages
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailChip(icon = Icons.Default.SdStorage, text = sizeMb)
                DetailChip(icon = Icons.Default.RecordVoiceOver, text = "${model.voicesCount} Voices")
                DetailChip(icon = Icons.Default.Language, text = "${model.supportedLanguages.size} Languages")
            }

            // Progress bar if downloading
            if (model.status == ModelStatus.DOWNLOADING ||
                model.status == ModelStatus.VERIFYING ||
                model.status == ModelStatus.INSTALLING
            ) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { model.downloadProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Downloading: ${(model.downloadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Error message if any
            model.errorMessage?.let { err ->
                Spacer(Modifier.height(6.dp))
                Text(text = "Error: $err", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            // Action Button Row
            if (!isCloudOrSystem) {
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (effectiveStatus == ModelStatus.INSTALLED) {
                        OutlinedButton(
                            onClick = onDeleteClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete Model")
                        }
                    } else if (effectiveStatus != ModelStatus.DOWNLOADING) {
                        Button(
                            onClick = onDownloadClick,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download Model")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailChip(icon: ImageVector, text: String) {
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
