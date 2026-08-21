package com.voconexus.app.ui.screens.scripteditor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voconexus.app.VocoNexusApplication
import com.voconexus.app.core.preprocessing.PreprocessingOptions
import com.voconexus.app.core.util.Formatters
import com.voconexus.app.core.util.TextStatistics
import kotlinx.coroutines.launch

data class SpeechTagItem(
    val tag: String,
    val label: String,
    val category: String,
    val badgeColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    viewModel: ScriptEditorViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val project by viewModel.projectState.collectAsState()
    val scriptText by viewModel.scriptText.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val textStats by viewModel.textStats.collectAsState()
    val importPreviewState by viewModel.importPreviewState.collectAsState()
    val showAudioRegenWarning by viewModel.showAudioRegenWarning.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val showFindReplace by viewModel.showFindReplaceBar.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val replaceQuery by viewModel.replaceQuery.collectAsState()
    val matchCase by viewModel.matchCase.collectAsState()
    val matchIndex by viewModel.matchIndex.collectAsState()
    val matchCount by viewModel.matchCount.collectAsState()
    val targetWordGoal by viewModel.targetWordGoal.collectAsState()
    val fontSizeSp by viewModel.fontSizeSp.collectAsState()
    val readingWpm by viewModel.readingWpm.collectAsState()

    val parts by viewModel.parts.collectAsState()
    val selectedPartIndex by viewModel.selectedPartIndex.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()

    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSsmlSheet by remember { mutableStateOf(false) }
    var showCaseMenu by remember { mutableStateOf(false) }
    var showWpmMenu by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showExtraTools by remember { mutableStateOf(false) }

    // Full Script TextFieldValue
    var textFieldValue by remember { mutableStateOf(TextFieldValue(scriptText)) }
    LaunchedEffect(scriptText) {
        if (textFieldValue.text != scriptText) {
            textFieldValue = TextFieldValue(text = scriptText, selection = TextRange(scriptText.length))
        }
    }

    // Selected Part TextFieldValue
    val currentPart = parts.getOrNull(selectedPartIndex)
    var partTextFieldValue by remember { mutableStateOf(TextFieldValue(currentPart?.text ?: "")) }
    LaunchedEffect(selectedPartIndex, currentPart?.text) {
        val targetText = currentPart?.text ?: ""
        if (partTextFieldValue.text != targetText) {
            partTextFieldValue = TextFieldValue(text = targetText, selection = TextRange(targetText.length))
        }
    }

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

    BackHandler {
        if (isDirty) {
            showUnsavedDialog = true
        } else {
            onBackClick()
        }
    }

    val container = (context.applicationContext as VocoNexusApplication).container
    val isPlayingPreview by container.audioPreviewPlayer.isPlaying.collectAsState()

    Scaffold(
        topBar = {
            // Clean TopBar: ONLY Back Button & Project Title (0 Actions inside TopBar = 100% Zero Overlap!)
            TopAppBar(
                title = {
                    Column(modifier = Modifier.padding(start = 2.dp)) {
                        Text(
                            text = project?.title ?: "Script Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (isDirty) Color(0xFFF59E0B) else Color(0xFF10B981),
                                shape = CircleShape,
                                modifier = Modifier.size(6.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSaving) "Saving..." else if (isDirty) "Unsaved changes" else "Saved to project",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDirty) Color(0xFFF59E0B) else Color(0xFF10B981)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) showUnsavedDialog = true else onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
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
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // Dedicated Control Bar: Horizontal Swipe Scrollable Row (Auto-Fit for All Device Sizes!)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prominent Save Action Button
                    item {
                        Button(
                            onClick = { viewModel.saveScript() },
                            enabled = isDirty && !isSaving,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isDirty) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSaving) "Saving..." else if (isDirty) "Save Script" else "Saved ✓",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Undo Icon
                    item {
                        IconButton(onClick = { viewModel.undo() }, enabled = canUndo, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }

                    // Redo Icon
                    item {
                        IconButton(onClick = { viewModel.redo() }, enabled = canRedo, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }

                    // Search Icon
                    item {
                        IconButton(onClick = { viewModel.toggleFindReplaceBar() }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.FindReplace,
                                contentDescription = "Find & Replace",
                                tint = if (showFindReplace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Speech Audio Preview Icon
                    item {
                        IconButton(
                            onClick = {
                                if (isPlayingPreview) {
                                    container.audioPreviewPlayer.pause()
                                } else {
                                    val textToPlay = if (viewMode == ScriptEditorViewModel.EditorViewMode.PART_BY_PART && currentPart != null) {
                                        currentPart.text
                                    } else {
                                        scriptText.take(1000)
                                    }
                                    if (textToPlay.isNotBlank()) {
                                        kotlinx.coroutines.MainScope().launch {
                                            try {
                                                val prefs = container.userPreferencesManager.preferences.value
                                                val voiceId = prefs.defaultVoiceId
                                                val engineId = prefs.defaultEngineId
                                                container.speechPreviewManager.playSpeechPreview(
                                                    sampleText = textToPlay,
                                                    voiceId = voiceId,
                                                    engineId = engineId
                                                )
                                            } catch (e: Exception) { e.printStackTrace() }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Preview Speech",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Toggle Extra Tools Drawer
                    item {
                        IconButton(onClick = { showExtraTools = !showExtraTools }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (showExtraTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Tools",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Overflow Options Menu
                    item {
                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share Script") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        if (scriptText.isNotBlank()) {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, scriptText)
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Script via"))
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Smart Auto-Format") },
                                    leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        val sel = textFieldValue.selection
                                        if (!sel.collapsed) {
                                            viewModel.smartAutoFormat(sel.min, sel.max)
                                        } else {
                                            viewModel.smartAutoFormat()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove Speaker Notes") },
                                    leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        val sel = textFieldValue.selection
                                        if (!sel.collapsed) {
                                            viewModel.removeBracketCues(sel.min, sel.max)
                                        } else {
                                            viewModel.removeBracketCues()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Set Target Word Goal") },
                                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showGoalDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Revert to Saved Original") },
                                    leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.revertToOriginal()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Script", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showClearDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Collapsible Tools Drawer (Keeps the screen 90%+ clear for the main script editor box!)
            AnimatedVisibility(
                visible = showExtraTools,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    // Stats Pill Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Words: ${textStats.wordCount} • Chars: ${textStats.characterCount} • Est: ${Formatters.formatDurationMs(textStats.estimatedDurationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Goal: ${textStats.wordCount}/$targetWordGoal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showGoalDialog = true }
                        )
                    }

                    // Formatting Buttons Row
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Font Size Zoom
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    IconButton(onClick = { viewModel.decreaseFontSize() }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Remove, contentDescription = "Font Smaller", modifier = Modifier.size(12.dp))
                                    }
                                    Text(
                                        text = "${fontSizeSp.toInt()}sp",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { viewModel.increaseFontSize() }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "Font Larger", modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }

                        // WPM Speed Selector
                        item {
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable { showWpmMenu = true }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("$readingWpm WPM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }

                                DropdownMenu(expanded = showWpmMenu, onDismissRequest = { showWpmMenu = false }) {
                                    DropdownMenuItem(text = { Text("Slow (120 WPM)") }, onClick = { showWpmMenu = false; viewModel.setReadingWpm(120) })
                                    DropdownMenuItem(text = { Text("Normal (150 WPM)") }, onClick = { showWpmMenu = false; viewModel.setReadingWpm(150) })
                                    DropdownMenuItem(text = { Text("Fast (180 WPM)") }, onClick = { showWpmMenu = false; viewModel.setReadingWpm(180) })
                                }
                            }
                        }

                        // File Import
                        item {
                            FilledTonalButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                enabled = !isImporting && !isSaving,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Preprocess
                        item {
                            FilledTonalButton(
                                onClick = { viewModel.preprocessCurrentScript() },
                                enabled = scriptText.isNotBlank() && !isSaving,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Preprocess", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Speech SSML Tags
                        item {
                            Button(
                                onClick = { showSsmlSheet = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Speech Tags", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Case Converter
                        item {
                            Box {
                                FilledTonalButton(
                                    onClick = { showCaseMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.FormatSize, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Case", style = MaterialTheme.typography.labelSmall)
                                }

                                DropdownMenu(
                                    expanded = showCaseMenu,
                                    onDismissRequest = { showCaseMenu = false }
                                ) {
                                    val selForCase = textFieldValue.selection
                                    val s = if (!selForCase.collapsed) selForCase.min else -1
                                    val e = if (!selForCase.collapsed) selForCase.max else -1
                                    DropdownMenuItem(text = { Text("UPPERCASE") }, onClick = { showCaseMenu = false; viewModel.convertCase(TextCaseMode.UPPERCASE, s, e) })
                                    DropdownMenuItem(text = { Text("lowercase") }, onClick = { showCaseMenu = false; viewModel.convertCase(TextCaseMode.LOWERCASE, s, e) })
                                    DropdownMenuItem(text = { Text("Title Case") }, onClick = { showCaseMenu = false; viewModel.convertCase(TextCaseMode.TITLE_CASE, s, e) })
                                    DropdownMenuItem(text = { Text("Sentence case") }, onClick = { showCaseMenu = false; viewModel.convertCase(TextCaseMode.SENTENCE_CASE, s, e) })
                                }
                            }
                        }
                    }
                }
            }

            // Find & Replace Panel (Collapsible)
            if (showFindReplace) {
                Spacer(modifier = Modifier.height(4.dp))
                FindReplaceDockingPanel(
                    searchQuery = searchQuery,
                    replaceQuery = replaceQuery,
                    matchCase = matchCase,
                    matchIndex = matchIndex,
                    matchCount = matchCount,
                    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onReplaceQueryChanged = { viewModel.onReplaceQueryChanged(it) },
                    onToggleMatchCase = { viewModel.toggleMatchCase() },
                    onFindNext = { viewModel.findNext() },
                    onFindPrevious = { viewModel.findPrevious() },
                    onReplaceCurrent = { viewModel.replaceCurrent() },
                    onReplaceAll = { viewModel.replaceAll() },
                    onClose = { viewModel.toggleFindReplaceBar() }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // HUGE & SPACIOUS Ultra-Futuristic Glassmorphic Full-Width Script Writing Canvas Card
            val futuristicGradientBorder = Brush.linearGradient(
                listOf(
                    Color(0xFF6366F1), // Electric Violet
                    Color(0xFF06B6D4), // Holographic Cyan
                    Color(0xFF10B981), // Neon Emerald
                    Color(0xFFF43F5E)  // Laser Pink
                )
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = androidx.compose.foundation.BorderStroke(
                    1.8.dp,
                    if (isDirty) futuristicGradientBorder else SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    // Futuristic Header Control Bar Inside Canvas
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = if (viewMode == ScriptEditorViewModel.EditorViewMode.FULL_SCRIPT) Color(0xFF6366F1) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { viewModel.setViewMode(ScriptEditorViewModel.EditorViewMode.FULL_SCRIPT) }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Surface(color = if (viewMode == ScriptEditorViewModel.EditorViewMode.FULL_SCRIPT) Color(0xFF10B981) else Color.Gray, shape = CircleShape, modifier = Modifier.size(6.dp)) {}
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("⚡ FULL SCRIPT", style = MaterialTheme.typography.labelSmall, color = if (viewMode == ScriptEditorViewModel.EditorViewMode.FULL_SCRIPT) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.ExtraBold)
                                }
                            }

                            Surface(
                                color = if (viewMode == ScriptEditorViewModel.EditorViewMode.PART_BY_PART) Color(0xFF06B6D4) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { viewModel.setViewMode(ScriptEditorViewModel.EditorViewMode.PART_BY_PART) }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Surface(color = if (viewMode == ScriptEditorViewModel.EditorViewMode.PART_BY_PART) Color(0xFF10B981) else Color.Gray, shape = CircleShape, modifier = Modifier.size(6.dp)) {}
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🧩 PARTS (${parts.size})", style = MaterialTheme.typography.labelSmall, color = if (viewMode == ScriptEditorViewModel.EditorViewMode.PART_BY_PART) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (viewMode == ScriptEditorViewModel.EditorViewMode.PART_BY_PART && currentPart != null) {
                                    "Part ${selectedPartIndex + 1}/${parts.size} • ${currentPart.characterCount} chars"
                                } else {
                                    "${textStats.wordCount} words • ${Formatters.formatDurationMs(textStats.estimatedDurationMs)}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Part Navigation Strip (Visible in Part View)
                    if (viewMode == ScriptEditorViewModel.EditorViewMode.PART_BY_PART && parts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.selectPreviousPart() },
                                    enabled = selectedPartIndex > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Part", modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = "Part ${selectedPartIndex + 1} of ${parts.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { viewModel.selectNextPart() },
                                    enabled = selectedPartIndex < parts.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Part", modifier = Modifier.size(18.dp))
                                }
                            }

                            LazyRow(
                                modifier = Modifier.weight(1f).padding(start = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(parts.size) { index ->
                                    val part = parts[index]
                                    val isSelected = index == selectedPartIndex
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.clickable { viewModel.setSelectedPartIndex(index) }
                                    ) {
                                        Text(
                                            text = "P${index + 1} (${part.characterCount}c)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(8.dp))

                    val editorScrollState = rememberScrollState()

                    // Text Input Box (Switches value based on viewMode)
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (viewMode == ScriptEditorViewModel.EditorViewMode.PART_BY_PART) {
                            BasicTextField(
                                value = partTextFieldValue,
                                onValueChange = { newValue ->
                                    partTextFieldValue = newValue
                                    if (currentPart != null && newValue.text != currentPart.text) {
                                        viewModel.updateCurrentPartText(newValue.text)
                                    }
                                },
                                textStyle = TextStyle(
                                    fontSize = fontSizeSp.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = (fontSizeSp * 1.5f).sp,
                                    fontFamily = FontFamily.Default
                                ),
                                cursorBrush = SolidColor(Color(0xFF06B6D4)),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(editorScrollState)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        } else {
                            BasicTextField(
                                value = textFieldValue,
                                onValueChange = { newValue ->
                                    textFieldValue = newValue
                                    if (newValue.text != scriptText) {
                                        viewModel.onScriptTextChanged(newValue.text)
                                    }
                                },
                                textStyle = TextStyle(
                                    fontSize = fontSizeSp.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = (fontSizeSp * 1.5f).sp,
                                    fontFamily = FontFamily.Default
                                ),
                                cursorBrush = SolidColor(Color(0xFF06B6D4)),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(editorScrollState)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        val activeText = if (viewMode == ScriptEditorViewModel.EditorViewMode.PART_BY_PART) partTextFieldValue.text else textFieldValue.text
                        if (activeText.isEmpty()) {
                            Text(
                                text = if (viewMode == ScriptEditorViewModel.EditorViewMode.PART_BY_PART) "Type or paste part text here..." else "Type or paste your voiceover script text here...",
                                style = TextStyle(
                                    fontSize = fontSizeSp.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.padding(4.dp)
                            )
                        }

                        if (isImporting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF06B6D4))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Reading and parsing document...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(6.dp))

                    // Futuristic Footer Status Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (viewMode == ScriptEditorViewModel.EditorViewMode.PART_BY_PART && currentPart != null) {
                                "Part ${selectedPartIndex + 1} • Chars: ${currentPart.characterCount} • Target: ~1000 chars"
                            } else {
                                "Lines: ${textStats.lineCount} • Chars: ${Formatters.formatNumber(scriptText.length.toLong())} • Words: ${textStats.wordCount}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color(0xFF10B981), shape = CircleShape, modifier = Modifier.size(6.dp)) {}
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "UTF-8 • VOICE AI READY",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Dialogs & BottomSheets
        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                title = { Text("Unsaved Changes") },
                text = { Text("You have unsaved changes in your script. Do you want to save them before leaving?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnsavedDialog = false
                            coroutineScope.launch {
                                viewModel.saveScriptDirectly()
                                onBackClick()
                            }
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

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear Entire Script?") },
                text = { Text("Are you sure you want to clear all script text? You can use Undo or Revert to restore.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearDialog = false
                            viewModel.clearScriptText()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showGoalDialog) {
            var goalInput by remember { mutableStateOf(targetWordGoal.toString()) }
            AlertDialog(
                onDismissRequest = { showGoalDialog = false },
                title = { Text("Set Target Word Goal") },
                text = {
                    Column {
                        Text("Set word count target to monitor speech pacing and duration.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = goalInput,
                            onValueChange = { goalInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Target Word Count") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            goalInput.toIntOrNull()?.let { viewModel.setTargetWordGoal(it) }
                            showGoalDialog = false
                        }
                    ) {
                        Text("Set Goal")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoalDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Ultra-Premium Colorful Speech Tags Sheet
        if (showSsmlSheet) {
            ModalBottomSheet(onDismissRequest = { showSsmlSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text("Insert Speech & Modulation Tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Tap a tag to insert voice modulation markers into your script.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    val ssmlItems = listOf(
                        SpeechTagItem("[pause=500ms]", "500ms Short Pause", "Pauses", Color(0xFF10B981)),
                        SpeechTagItem("[pause=1s]", "1 Second Medium Pause", "Pauses", Color(0xFF10B981)),
                        SpeechTagItem("[pause=2s]", "2 Second Long Pause", "Pauses", Color(0xFF10B981)),
                        SpeechTagItem("<break time=\"500ms\"/>", "SSML Break 500ms", "Pauses", Color(0xFF059669)),
                        SpeechTagItem("<break time=\"1s\"/>", "SSML Break 1s", "Pauses", Color(0xFF059669)),
                        SpeechTagItem("<emphasis level=\"strong\">Text</emphasis>", "Strong Emphasis", "Tone", Color(0xFFEC4899)),
                        SpeechTagItem("<emphasis level=\"moderate\">Text</emphasis>", "Moderate Emphasis", "Tone", Color(0xFFF43F5E)),
                        SpeechTagItem("<prosody pitch=\"+2st\">Higher Pitch</prosody>", "Pitch +2 Semitones", "Pitch/Speed", Color(0xFF6366F1)),
                        SpeechTagItem("<prosody pitch=\"-2st\">Lower Pitch</prosody>", "Pitch -2 Semitones", "Pitch/Speed", Color(0xFF8B5CF6)),
                        SpeechTagItem("<prosody rate=\"1.2\">Faster Speed</prosody>", "Speed 1.2x", "Pitch/Speed", Color(0xFF3B82F6)),
                        SpeechTagItem("<prosody rate=\"0.8\">Slower Speed</prosody>", "Speed 0.8x", "Pitch/Speed", Color(0xFF0EA5E9)),
                        SpeechTagItem("[HOOK]", "Hook Section Marker", "Structure", Color(0xFFF59E0B)),
                        SpeechTagItem("[INTRO]", "Intro Section Marker", "Structure", Color(0xFFD97706)),
                        SpeechTagItem("[CALL TO ACTION]", "Call to Action Marker", "Structure", Color(0xFF10B981))
                    )

                    var selectedCategory by remember { mutableStateOf("All") }
                    val categories = listOf("All", "Pauses", "Tone", "Pitch/Speed", "Structure")

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        items(categories) { cat ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedCategory == cat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { selectedCategory = cat }
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedCategory == cat) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    val filteredItems = remember(selectedCategory) {
                        if (selectedCategory == "All") ssmlItems else ssmlItems.filter { it.category == selectedCategory }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 350.dp)
                    ) {
                        items(filteredItems, key = { it.tag }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.insertSsmlTag(item.tag)
                                        showSsmlSheet = false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, item.badgeColor.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(item.category, style = MaterialTheme.typography.labelSmall, color = item.badgeColor)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = item.badgeColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, item.badgeColor.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = item.tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = item.badgeColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showSsmlSheet = false }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Text("Close")
                    }
                }
            }
        }

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
fun FindReplaceDockingPanel(
    searchQuery: String,
    replaceQuery: String,
    matchCase: Boolean,
    matchIndex: Int,
    matchCount: Int,
    onSearchQueryChanged: (String) -> Unit,
    onReplaceQueryChanged: (String) -> Unit,
    onToggleMatchCase: () -> Unit,
    onFindNext: () -> Unit,
    onFindPrevious: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Find & Replace", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onClose, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Find Bar", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Find text...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        Text(
                            text = if (matchCount > 0) "${matchIndex + 1}/$matchCount" else "0/0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )

                IconButton(onClick = onToggleMatchCase) {
                    Text(if (matchCase) "Aa" else "aa", fontWeight = FontWeight.Bold, color = if (matchCase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(onClick = onFindPrevious, enabled = matchCount > 0) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Match")
                }
                IconButton(onClick = onFindNext, enabled = matchCount > 0) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Match")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceQueryChanged,
                    placeholder = { Text("Replace with...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )

                Button(
                    onClick = onReplaceCurrent,
                    enabled = matchCount > 0,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Replace", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onReplaceAll,
                    enabled = matchCount > 0,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("All", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewBottomSheet(
    previewState: com.voconexus.app.ui.screens.scripteditor.ImportPreviewState,
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
