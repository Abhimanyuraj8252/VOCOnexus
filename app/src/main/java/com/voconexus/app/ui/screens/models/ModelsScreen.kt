package com.voconexus.app.ui.screens.models

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voconexus.app.ui.components.VocoNexusCard
import com.voconexus.app.ui.components.VocoNexusEmptyState
import com.voconexus.app.ui.components.VocoNexusTopBar

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
            VocoNexusTopBar(
                title = "TTS Models",
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp)
        ) {
            Text(
                text = "Offline TTS Engine Models",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Manage downloaded neural speech models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (models.isEmpty()) {
                VocoNexusEmptyState(
                    title = "No Models Installed",
                    description = "Download a Kokoro or Piper model to enable offline synthesis.",
                    icon = Icons.Default.Tune
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(models, key = { it.id }) { model ->
                        val sizeMb = model.sizeBytes / (1024 * 1024)
                        VocoNexusCard {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.padding(start = 12.dp)) {
                                            Text(
                                                text = model.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Engine: ${model.engineId} • Size: ${sizeMb}MB",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    when (model.status) {
                                        com.voconexus.app.core.tts.ModelStatus.INSTALLED -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                androidx.compose.material3.Surface(
                                                    shape = MaterialTheme.shapes.small,
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = "Installed",
                                                        color = MaterialTheme.colorScheme.primary,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                androidx.compose.material3.IconButton(
                                                    onClick = { viewModel.deleteModel(model.id) }
                                                ) {
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                                                        contentDescription = "Delete Model",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                        com.voconexus.app.core.tts.ModelStatus.DOWNLOADING,
                                        com.voconexus.app.core.tts.ModelStatus.INSTALLING,
                                        com.voconexus.app.core.tts.ModelStatus.VERIFYING -> {
                                            Text(
                                                text = "${(model.downloadProgress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        else -> {
                                            androidx.compose.material3.Button(
                                                onClick = { viewModel.downloadModel(model.id) }
                                            ) {
                                                Icon(
                                                    imageVector = androidx.compose.material.icons.Icons.Default.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(end = 4.dp)
                                                )
                                                Text("Download")
                                            }
                                        }
                                    }
                                }

                                if (model.status == com.voconexus.app.core.tts.ModelStatus.DOWNLOADING ||
                                    model.status == com.voconexus.app.core.tts.ModelStatus.VERIFYING ||
                                    model.status == com.voconexus.app.core.tts.ModelStatus.INSTALLING
                                ) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { model.downloadProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (model.errorMessage != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Error: ${model.errorMessage}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
