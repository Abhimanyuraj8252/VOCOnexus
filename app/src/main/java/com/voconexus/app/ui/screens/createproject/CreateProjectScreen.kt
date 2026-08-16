package com.voconexus.app.ui.screens.createproject

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.AutoAwesome
import kotlinx.coroutines.launch
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voconexus.app.ui.components.VocoNexusButton
import com.voconexus.app.ui.components.VocoNexusTopBar
import com.voconexus.app.core.utils.FastTextHelpers

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateProjectScreen(
    viewModel: CreateProjectViewModel,
    onBackClick: () -> Unit,
    onProjectCreated: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var voiceDropdownExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // File Import Launcher (.txt, .srt, document)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val rawImported = inputStream?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() } ?: ""
                val importedText = rawImported.replace("\uFEFF", "").trim()
                if (importedText.isNotBlank()) {
                    viewModel.onScriptTextChanged(importedText)
                }
            } catch (_: Exception) {}
        }
    }

    // Dynamic Script Analysis Calculations
    val charCount = uiState.scriptText.length
    val wordCount = remember(uiState.scriptText) { FastTextHelpers.fastWordCount(uiState.scriptText) }
    val estimatedSeconds = (wordCount / 2.5).toInt()
    val estMins = estimatedSeconds / 60
    val estSecs = estimatedSeconds % 60
    val formattedDuration = if (estMins > 0) "${estMins}m ${estSecs}s" else "${estSecs}s"

    Scaffold(
        topBar = {
            VocoNexusTopBar(
                title = "Create New Project",
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Input
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text("Project Title") },
                placeholder = { Text("e.g., Chapter 1 - The Solar System") },
                leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Description Input
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChanged,
                label = { Text("Description (Optional)") },
                placeholder = { Text("Short notes about voice style, target audience...") },
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Scrollable Voice Selector Dropdown
            Text(
                text = "Target Voice Engine & Speaker",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                val selectedVoice = uiState.availableVoices.find { it.id == uiState.selectedVoiceId }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { voiceDropdownExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = selectedVoice?.name ?: "AF Heart (US English)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${selectedVoice?.gender ?: "FEMALE"} • ${selectedVoice?.language ?: "en"} (${selectedVoice?.locale ?: "en-US"})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = voiceDropdownExpanded,
                    onDismissRequest = { voiceDropdownExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .heightIn(max = 280.dp)
                ) {
                    uiState.availableVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(voice.name, fontWeight = FontWeight.Bold)
                                    Text("${voice.gender} • ${voice.language} (${voice.locale})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                viewModel.onVoiceSelected(voice.id)
                                voiceDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Script Text Header
            Text(
                text = "Script Content",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            OutlinedTextField(
                value = uiState.scriptText,
                onValueChange = viewModel::onScriptTextChanged,
                placeholder = { Text("Paste or import your script text here. VocoNexus will automatically format, segment into chapters/chunks, and generate offline speech.") },
                minLines = 8,
                maxLines = 15,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Dynamic Script Analysis Details (Clean Auto-Responsive Surface)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Est. Audio Duration:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "~$formattedDuration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$wordCount words",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$charCount characters",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // FlowRow for Buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { filePickerLauncher.launch("*/*") }
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Import", modifier = Modifier.height(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import")
                }

                OutlinedButton(
                    onClick = {
                        if (uiState.scriptText.isNotBlank()) {
                            val container = (context.applicationContext as com.voconexus.app.VocoNexusApplication).container
                            kotlinx.coroutines.MainScope().launch {
                                try {
                                    container.speechPreviewManager.playSpeechPreview(
                                        sampleText = uiState.scriptText.take(500),
                                        voiceId = uiState.selectedVoiceId,
                                        engineId = uiState.selectedEngineId
                                    )
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Text", modifier = Modifier.height(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play")
                }

                OutlinedButton(
                    onClick = {
                        // "Generate Audio" button triggers project creation and immediate processing.
                        viewModel.createProject { newProjectId ->
                            onProjectCreated(newProjectId)
                        }
                    }
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Generate Audio", modifier = Modifier.height(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Generate")
                }
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            VocoNexusButton(
                text = "Save & Preprocess Project",
                onClick = {
                    viewModel.createProject { newProjectId ->
                        onProjectCreated(newProjectId)
                    }
                },
                isLoading = uiState.isCreating,
                icon = Icons.Default.Check,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

