package com.voconexus.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.util.Formatters
import com.voconexus.app.ui.components.VocoNexusButton
import com.voconexus.app.ui.components.VocoNexusCard
import com.voconexus.app.ui.components.VocoNexusEmptyState
import com.voconexus.app.ui.components.VocoNexusSectionHeader
import com.voconexus.app.ui.components.VocoNexusStat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCreateProjectClick: () -> Unit,
    onProjectClick: (String) -> Unit,
    onNavigateVoices: () -> Unit,
    onNavigateModels: () -> Unit,
    onNavigateAudio: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateSpeedPitch: () -> Unit = {}
) {
    val projects by viewModel.projectsFlow.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    val availableStorageBytes = viewModel.getAvailableStorageBytes()
    val storageText = Formatters.formatFileSize(availableStorageBytes)

    var renameText by remember(dialogState.showRenameDialog) {
        mutableStateOf(dialogState.project?.title ?: "")
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "VocoNexus",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Offline Long-Form Text-To-Speech Workspace",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VocoNexusStat(
                            label = "Projects",
                            value = projects.size.toString(),
                            icon = Icons.Default.Folder,
                            modifier = Modifier.weight(1f)
                        )
                        VocoNexusStat(
                            label = "Storage Free",
                            value = storageText,
                            icon = Icons.Default.Storage,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    VocoNexusButton(
                        text = "Create New Project",
                        onClick = onCreateProjectClick,
                        icon = Icons.Default.Add,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Projects List Section
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                VocoNexusSectionHeader(
                    title = "Recent Projects",
                    actionText = if (projects.isNotEmpty()) "${projects.size} total" else null
                )

                if (projects.isEmpty()) {
                    VocoNexusEmptyState(
                        title = "No Projects Yet",
                        description = "Create your first offline TTS project to convert long scripts, documents, and books into synthesized speech.",
                        icon = Icons.Default.Folder,
                        actionText = "Create Project",
                        onActionClick = onCreateProjectClick
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(projects, key = { it.id }) { project ->
                            ProjectItemCard(
                                project = project,
                                onClick = { onProjectClick(project.id) },
                                onRename = { viewModel.openRenameDialog(project) },
                                onInfo = { viewModel.openInfoSheet(project) },
                                onDelete = { viewModel.openDeleteDialog(project) }
                            )
                        }
                    }
                }
            }
        }

        // Rename Dialog
        if (dialogState.showRenameDialog && dialogState.project != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialogs,
                title = { Text("Rename Project") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Project Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.renameProject(renameText) }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialogs) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Safe Delete Confirmation Dialog
        if (dialogState.showDeleteDialog && dialogState.project != null) {
            val project = dialogState.project!!
            val stats = dialogState.stats
            val sizeStr = if (stats != null) Formatters.formatFileSize(stats.totalSizeBytes) else "0 B"

            AlertDialog(
                onDismissRequest = viewModel::dismissDialogs,
                title = { Text("Delete Project '${project.title}'?") },
                text = {
                    Text(
                        text = "Deleting this project will permanently remove its persistent metadata and locally stored generated audio files ($sizeStr). This action cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDeleteProject) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialogs) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Project Information Sheet
        if (dialogState.showInfoSheet && dialogState.project != null) {
            val project = dialogState.project!!
            val stats = dialogState.stats
            ModalBottomSheet(onDismissRequest = viewModel::dismissDialogs) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Project Information",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "Title: ${project.title}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "ID: ${project.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Parts: ${project.partCount}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Total Chunks: ${project.chunkCount}", style = MaterialTheme.typography.bodyMedium)
                    if (stats != null) {
                        Text(text = "Completed Chunks: ${stats.completedChunks}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Audio Storage Footprint: ${Formatters.formatFileSize(stats.totalSizeBytes)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectItemCard(
    project: ProjectEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(project.updatedAt))
    val estDurationStr = Formatters.formatDurationMs(project.estimatedDurationMs)

    VocoNexusCard(onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Project actions"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Information") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onInfo()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (project.description.isNotBlank()) {
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${project.partCount} Parts • ${project.chunkCount} Chunks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Est. $estDurationStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


