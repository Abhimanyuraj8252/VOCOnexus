package com.voconexus.app.ui.screens.scripteditor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voconexus.app.core.util.Formatters
import com.voconexus.app.ui.components.VocoNexusButton
import com.voconexus.app.ui.components.VocoNexusCard
import com.voconexus.app.ui.components.VocoNexusTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    viewModel: ScriptEditorViewModel,
    onBackClick: () -> Unit
) {
    val project by viewModel.projectState.collectAsState()
    val scriptText by viewModel.scriptText.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val textStats by viewModel.textStats.collectAsState()
    val importPreviewState by viewModel.importPreviewState.collectAsState()
    val showAudioRegenWarning by viewModel.showAudioRegenWarning.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    var showUnsavedDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFile(it) }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    // Handle Back Button press
    BackHandler {
        if (isDirty) {
            showUnsavedDialog = true
        } else {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            VocoNexusTopBar(
                title = if (isDirty) "${project?.title ?: "Script Editor"} *" else (project?.title ?: "Script Editor"),
                onBackClick = {
                    if (isDirty) {
                        showUnsavedDialog = true
                    } else {
                        onBackClick()
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Live Statistics Banner
            StatisticsBanner(stats = textStats)

            Spacer(modifier = Modifier.height(12.dp))

            // Action Toolbar (Import, Preprocess, Save) - Scrollable Row
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = !isImporting && !isSaving,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import File", maxLines = 1)
                    }
                }

                item {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { viewModel.preprocessCurrentScript() },
                        enabled = scriptText.isNotBlank() && !isSaving,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Preprocess", maxLines = 1)
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.saveScript() },
                        enabled = isDirty && !isSaving,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSaving) "Saving..." else "Save Script", maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fixed Visual Height Editor Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = { viewModel.onScriptTextChanged(it) },
                    placeholder = { Text("Paste or type your script text here...") },
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp)
                )

                if (isImporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Reading and parsing document...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Unsaved Changes Confirmation Dialog
        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                title = { Text("Unsaved Changes") },
                text = { Text("You have unsaved changes in your script. Do you want to save them before leaving?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnsavedDialog = false
                            viewModel.saveScript()
                            onBackClick()
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                showUnsavedDialog = false
                                onBackClick()
                            }
                        ) {
                            Text("Discard", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { showUnsavedDialog = false }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }

        // Audio Regeneration Warning Dialog
        if (showAudioRegenWarning) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissRegenWarning() },
                title = { Text("Generated Audio Warning") },
                text = { Text("This project has generated audio chunks. Modifying the script will mark modified sections for regeneration.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissRegenWarning() }) {
                        Text("I Understand")
                    }
                }
            )
        }

        // Preprocessing & Import Preview Bottom Sheet
        importPreviewState?.let { preview ->
            ImportPreviewBottomSheet(
                previewState = preview,
                onApply = { viewModel.applyImportPreview() },
                onCancel = { viewModel.cancelImportPreview() }
            )
        }
    }
}

@Composable
fun StatisticsBanner(stats: com.voconexus.app.core.util.TextStatistics) {
    VocoNexusCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Characters",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatters.formatNumber(stats.characterCount.toLong()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(
                    text = "Words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatters.formatNumber(stats.wordCount.toLong()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(
                    text = "Paragraphs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatters.formatNumber(stats.paragraphCount.toLong()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(
                    text = "Est. Duration",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = Formatters.formatDurationMs(stats.estimatedDurationMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewBottomSheet(
    previewState: ImportPreviewState,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val summary = previewState.preprocessingResult.summary

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Import & Preprocessing Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Source: ${previewState.parseResult.originalFileName ?: "Input Script"} (${previewState.parseResult.sourceType})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics Summary Grid
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Summary of Changes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Characters:", style = MaterialTheme.typography.bodySmall)
                        Text("${summary.charCountBefore} → ${summary.charCountAfter}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Words:", style = MaterialTheme.typography.bodySmall)
                        Text("${summary.wordCountBefore} → ${summary.wordCountAfter}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Timestamps Removed:", style = MaterialTheme.typography.bodySmall)
                        Text("${summary.timestampsRemoved}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("HTML/Formatting Tags Removed:", style = MaterialTheme.typography.bodySmall)
                        Text("${summary.tagsRemoved}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text Preview Sample Comparison
            Text("Processed Output Preview", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = previewState.preprocessingResult.normalizedText.take(1500),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply & Use Script")
                }
            }
        }
    }
}
