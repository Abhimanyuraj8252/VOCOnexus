package com.voconexus.app.ui.screens.audiolibrary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLibraryScreen(
    viewModel: AudioLibraryViewModel,
    onBackClick: () -> Unit,
    onNavigateAudioDetails: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
        uri?.let { viewModel.combineAndExportSelection(it) }
    }

    LaunchedEffect(uiState.errorMessage, uiState.infoMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Library") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isMultiSelectMode) {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { viewModel.playSelectedPlaylist() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Selected")
                        }
                        IconButton(onClick = { exportLauncher.launch("VocoNexus_Combined.wav") }) {
                            Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = "Combine & Export")
                        }
                        IconButton(onClick = { viewModel.deleteSelectedAudio() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
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
            // Storage Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Audio Library Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Generated: ${Formatters.formatFileSize(uiState.projectSummary.totalFileSizeBytes)}", style = MaterialTheme.typography.bodySmall)
                        Text("Duration: ${Formatters.formatDurationMs(uiState.projectSummary.totalDurationMs)}", style = MaterialTheme.typography.bodySmall)
                        Text("Free: ${Formatters.formatFileSize(uiState.availableStorageBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }

                    if (uiState.isExporting) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Exporting audio...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { uiState.exportProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Project Tabs
            if (uiState.projects.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.projects.indexOfFirst { it.id == uiState.selectedProjectId }.coerceAtLeast(0),
                    edgePadding = 16.dp
                ) {
                    uiState.projects.forEach { project ->
                        Tab(
                            selected = project.id == uiState.selectedProjectId,
                            onClick = { viewModel.selectProject(project.id) },
                            text = { Text(project.title) }
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search chunks or text...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            // Chunk Audio Items
            val filteredChunks = uiState.chunks.filter {
                uiState.searchQuery.isBlank() ||
                        it.normalizedText.contains(uiState.searchQuery, ignoreCase = true) ||
                        ("Chunk #${it.sequenceIndex + 1}").contains(uiState.searchQuery, ignoreCase = true)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredChunks, key = { it.id }) { chunk ->
                    val isSelected = uiState.selectedChunkIds.contains(chunk.id)
                    val asset = uiState.audioAssets.firstOrNull { it.chunkId == chunk.id }

                    ChunkAudioCard(
                        chunk = chunk,
                        hasAudio = asset != null,
                        durationMs = asset?.durationMs?.toLong() ?: 0L,
                        fileSizeBytes = asset?.fileSizeBytes ?: 0L,
                        isSelected = isSelected,
                        onToggleSelect = { viewModel.toggleChunkSelection(chunk.id) },
                        onPlayClick = { viewModel.playChunk(chunk) },
                        onDetailsClick = { onNavigateAudioDetails(chunk.id) }
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun ChunkAudioCard(
    chunk: ChunkEntity,
    hasAudio: Boolean,
    durationMs: Long,
    fileSizeBytes: Long,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPlayClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("Chunk #${chunk.sequenceIndex + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(chunk.normalizedText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Formatters.formatDurationMs(durationMs), style = MaterialTheme.typography.labelSmall)
                    Text(Formatters.formatFileSize(fileSizeBytes), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (hasAudio) {
                IconButton(onClick = onPlayClick) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Icon(Icons.Default.Error, contentDescription = "Missing Audio", tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(20.dp))
            }

            IconButton(onClick = onDetailsClick) {
                Icon(Icons.Default.Info, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
