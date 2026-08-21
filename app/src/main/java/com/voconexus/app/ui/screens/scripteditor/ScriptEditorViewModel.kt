package com.voconexus.app.ui.screens.scripteditor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.domain.DurationEstimator
import com.voconexus.app.core.parser.ParseResult
import com.voconexus.app.core.preprocessing.PreprocessingOptions
import com.voconexus.app.core.preprocessing.PreprocessingResult
import com.voconexus.app.core.preprocessing.TextPreprocessingEngine
import com.voconexus.app.core.storage.FileImportManager
import com.voconexus.app.core.util.Formatters
import com.voconexus.app.core.util.TextStatistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScriptPart(
    val index: Int,
    val text: String,
    val charCount: Int,
    val wordCount: Int
) {
    val characterCount: Int get() = charCount
}

data class ImportPreviewState(
    val parseResult: ParseResult,
    val preprocessingResult: PreprocessingResult
)

enum class TextCaseMode {
    UPPERCASE,
    LOWERCASE,
    TITLE_CASE,
    SENTENCE_CASE
}

class ScriptEditorViewModel(
    val projectId: String,
    private val projectRepository: ProjectRepository,
    private val fileImportManager: FileImportManager,
    private val preprocessingEngine: TextPreprocessingEngine,
    private val durationEstimator: DurationEstimator
) : ViewModel() {

    enum class EditorViewMode {
        FULL_SCRIPT,
        PART_BY_PART
    }

    val projectState: StateFlow<ProjectEntity?> = projectRepository.getProjectById(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _scriptText = MutableStateFlow("")
    val scriptText: StateFlow<String> = _scriptText.asStateFlow()

    // --- Feature 5: Sentence-Aware Part-by-Part Script Division System ---
    private val _parts = MutableStateFlow<List<ScriptPart>>(emptyList())
    val parts: StateFlow<List<ScriptPart>> = _parts.asStateFlow()

    private val _selectedPartIndex = MutableStateFlow<Int>(-1) // -1 = Full Script, 0 = Part 1, 1 = Part 2...
    val selectedPartIndex: StateFlow<Int> = _selectedPartIndex.asStateFlow()

    private val _viewMode = MutableStateFlow<EditorViewMode>(EditorViewMode.FULL_SCRIPT)
    val viewMode: StateFlow<EditorViewMode> = _viewMode.asStateFlow()

    fun setViewMode(mode: EditorViewMode) {
        _viewMode.value = mode
        if (mode == EditorViewMode.PART_BY_PART) {
            recalculateParts(_scriptText.value)
            if (_selectedPartIndex.value == -1 && _parts.value.isNotEmpty()) {
                _selectedPartIndex.value = 0
            }
        }
    }

    fun selectPart(index: Int) {
        _selectedPartIndex.value = index.coerceIn(-1, (_parts.value.size - 1).coerceAtLeast(-1))
    }

    fun setSelectedPartIndex(index: Int) {
        if (index in _parts.value.indices) {
            _selectedPartIndex.value = index
        }
    }

    fun nextPart() {
        val maxIdx = _parts.value.size - 1
        if (maxIdx >= 0) {
            val current = _selectedPartIndex.value
            if (current == -1) {
                _selectedPartIndex.value = 0
            } else if (current < maxIdx) {
                _selectedPartIndex.value = current + 1
            }
        }
    }

    fun selectNextPart() {
        val maxIdx = _parts.value.size - 1
        if (maxIdx >= 0 && _selectedPartIndex.value < maxIdx) {
            _selectedPartIndex.value = _selectedPartIndex.value + 1
        }
    }

    fun prevPart() {
        val current = _selectedPartIndex.value
        if (current > 0) {
            _selectedPartIndex.value = current - 1
        } else if (current == 0) {
            _selectedPartIndex.value = -1 // back to Full Script
        }
    }

    fun selectPreviousPart() {
        if (_selectedPartIndex.value > 0) {
            _selectedPartIndex.value = _selectedPartIndex.value - 1
        }
    }

    fun recalculateParts(text: String = _scriptText.value) {
        if (text.isBlank()) {
            _parts.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val generatedParts = splitTextIntoSentenceParts(text, targetChars = 1000)
            withContext(Dispatchers.Main) {
                _parts.value = generatedParts
                if (_selectedPartIndex.value >= generatedParts.size) {
                    _selectedPartIndex.value = if (generatedParts.isEmpty()) -1 else generatedParts.size - 1
                }
            }
        }
    }

    fun updateCurrentPartText(newPartText: String, partIndex: Int = _selectedPartIndex.value) {
        val targetIndex = if (partIndex >= 0) partIndex else _selectedPartIndex.value
        if (targetIndex in _parts.value.indices) {
            val currentParts = _parts.value.toMutableList()
            val updatedPart = currentParts[targetIndex].copy(
                text = newPartText,
                charCount = newPartText.length,
                wordCount = if (newPartText.isBlank()) 0 else newPartText.trim().split("""\s+""".toRegex()).size
            )
            currentParts[targetIndex] = updatedPart
            _parts.value = currentParts
            
            // Recombine full script from parts
            val fullText = currentParts.joinToString("\n\n") { it.text }
            onScriptTextChanged(fullText, isUserAction = true, skipRecalculateParts = true)
        }
    }

    fun updateCurrentPartText(partIndex: Int, newPartText: String) {
        updateCurrentPartText(newPartText, partIndex)
    }

    private fun splitTextIntoSentenceParts(fullText: String, targetChars: Int = 1000): List<ScriptPart> {
        if (fullText.isBlank()) return emptyList()

        val sentenceMatches = Regex("""[^.!?\n]+[.!?]*[\s"'\)]*""").findAll(fullText)
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val sentences = if (sentenceMatches.isNotEmpty()) sentenceMatches else listOf(fullText.trim())

        val rawChunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (sentence in sentences) {
            if (currentChunk.isEmpty()) {
                currentChunk.append(sentence)
            } else {
                val potentialLength = currentChunk.length + 1 + sentence.length
                if (currentChunk.length >= targetChars || potentialLength > targetChars + 250) {
                    rawChunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder(sentence)
                } else {
                    currentChunk.append(" ").append(sentence)
                }
            }
        }

        if (currentChunk.isNotBlank()) {
            rawChunks.add(currentChunk.toString().trim())
        }

        return rawChunks.mapIndexed { idx, chunkText ->
            ScriptPart(
                index = idx + 1,
                text = chunkText,
                charCount = chunkText.length,
                wordCount = if (chunkText.isBlank()) 0 else chunkText.trim().split("""\s+""".toRegex()).size
            )
        }
    }


    private var initialText: String = ""

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _textStats = MutableStateFlow(TextStatistics())
    val textStats: StateFlow<TextStatistics> = _textStats.asStateFlow()

    private val _importPreviewState = MutableStateFlow<ImportPreviewState?>(null)
    val importPreviewState: StateFlow<ImportPreviewState?> = _importPreviewState.asStateFlow()

    private val _hasGeneratedAudio = MutableStateFlow(false)
    val hasGeneratedAudio: StateFlow<Boolean> = _hasGeneratedAudio.asStateFlow()

    private val _showAudioRegenWarning = MutableStateFlow(false)
    val showAudioRegenWarning: StateFlow<Boolean> = _showAudioRegenWarning.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // --- Feature 1: Memory-Optimized Undo / Redo Buffer ---
    private val undoStack = java.util.ArrayDeque<String>()
    private val redoStack = java.util.ArrayDeque<String>()
    private var lastUndoSnapshotTime: Long = 0L

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // --- Feature 2: Find & Replace State ---
    private val _showFindReplaceBar = MutableStateFlow(false)
    val showFindReplaceBar: StateFlow<Boolean> = _showFindReplaceBar.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _replaceQuery = MutableStateFlow("")
    val replaceQuery: StateFlow<String> = _replaceQuery.asStateFlow()

    private val _matchCase = MutableStateFlow(false)
    val matchCase: StateFlow<Boolean> = _matchCase.asStateFlow()

    private val _matchIndex = MutableStateFlow(0)
    val matchIndex: StateFlow<Int> = _matchIndex.asStateFlow()

    private val _matchCount = MutableStateFlow(0)
    val matchCount: StateFlow<Int> = _matchCount.asStateFlow()

    // --- Feature 3: Target Word Count Goal ---
    private val _targetWordGoal = MutableStateFlow(500)
    val targetWordGoal: StateFlow<Int> = _targetWordGoal.asStateFlow()

    // --- Feature 4: Font Size & Speed Pacing Controls ---
    private val _fontSizeSp = MutableStateFlow(16f)
    val fontSizeSp: StateFlow<Float> = _fontSizeSp.asStateFlow()

    private val _readingWpm = MutableStateFlow(150)
    val readingWpm: StateFlow<Int> = _readingWpm.asStateFlow()

    private val _showLineNumbers = MutableStateFlow(false)
    val showLineNumbers: StateFlow<Boolean> = _showLineNumbers.asStateFlow()

    init {
        loadDocument()
        observeStatsAndAutosave()
    }

    private fun loadDocument() {
        viewModelScope.launch {
            val document = projectRepository.getDocumentForProjectDirect(projectId)
            val chunks = projectRepository.getChunksForProjectDirect(projectId)
            _hasGeneratedAudio.value = chunks.any { it.status == ChunkStatus.COMPLETED }

            val documentText = when {
                document?.rawText?.isNotBlank() == true -> document.rawText
                chunks.isNotEmpty() -> chunks.joinToString("\n\n") { it.sourceText }
                else -> ""
            }

            initialText = documentText
            _scriptText.value = documentText
            recalculateParts(documentText)
            _isDirty.value = false

            // Asynchronous Off-Thread Stats Calculation for Large Files
            val stats = withContext(Dispatchers.Default) {
                Formatters.calculateTextStatistics(documentText, durationEstimator)
            }
            _textStats.value = stats
            
            undoStack.clear()
            redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeStatsAndAutosave() {
        // Off-thread stats recalculation with 600ms debounce to prevent UI lag on 30k+ word scripts
        _scriptText
            .debounce(600)
            .onEach { text ->
                val stats = withContext(Dispatchers.Default) {
                    Formatters.calculateTextStatistics(text, durationEstimator)
                }
                _textStats.value = stats
                updateSearchMatches()
            }
            .launchIn(viewModelScope)

        // Fast 1000ms Auto-Save to prevent any script data loss
        _scriptText
            .debounce(1000)
            .onEach { text ->
                if (_isDirty.value && text.isNotBlank() && text != initialText) {
                    saveScriptInternal(text)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onScriptTextChanged(newText: String, isUserAction: Boolean = true, skipRecalculateParts: Boolean = false) {
        val oldText = _scriptText.value
        if (oldText != newText) {
            val currentTime = System.currentTimeMillis()
            if (isUserAction && oldText.isNotEmpty()) {
                // Save undo snapshot only if 1.5 seconds have passed or on word space break to conserve memory
                if (currentTime - lastUndoSnapshotTime > 1500 || newText.endsWith(" ") || newText.endsWith("\n")) {
                    if (undoStack.isEmpty() || undoStack.peek() != oldText) {
                        undoStack.push(oldText)
                        if (undoStack.size > 20) undoStack.removeLast()
                        lastUndoSnapshotTime = currentTime
                    }
                    redoStack.clear()
                    _canUndo.value = undoStack.isNotEmpty()
                    _canRedo.value = false
                }
            }
            _scriptText.value = newText
            if (!skipRecalculateParts && _selectedPartIndex.value == -1) {
                recalculateParts(newText)
            }
            _isDirty.value = (newText != initialText)
            if (_hasGeneratedAudio.value && _isDirty.value) {
                _showAudioRegenWarning.value = true
            }
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = _scriptText.value
            redoStack.push(current)
            val previous = undoStack.pop()
            _scriptText.value = previous
            _isDirty.value = (previous != initialText)
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = true
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = _scriptText.value
            undoStack.push(current)
            val next = redoStack.pop()
            _scriptText.value = next
            _isDirty.value = (next != initialText)
            _canUndo.value = true
            _canRedo.value = redoStack.isNotEmpty()
        }
    }

    // --- Font Size & Line Numbers ---
    fun increaseFontSize() {
        if (_fontSizeSp.value < 28f) {
            _fontSizeSp.value += 2f
        }
    }

    fun decreaseFontSize() {
        if (_fontSizeSp.value > 12f) {
            _fontSizeSp.value -= 2f
        }
    }

    fun toggleLineNumbers() {
        _showLineNumbers.value = !_showLineNumbers.value
    }

    fun setReadingWpm(wpm: Int) {
        _readingWpm.value = wpm
    }

    // --- Template Snippets Inserter ---
    fun insertTemplateSnippet(title: String, snippetText: String) {
        val current = _scriptText.value
        val newText = if (current.isBlank()) snippetText else "$current\n\n$snippetText"
        onScriptTextChanged(newText)
        _userMessage.value = "Inserted $title template"
    }

    // --- Find & Replace Actions ---
    fun toggleFindReplaceBar() {
        _showFindReplaceBar.value = !_showFindReplaceBar.value
        if (_showFindReplaceBar.value) {
            updateSearchMatches()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _matchIndex.value = 0
        updateSearchMatches()
    }

    fun onReplaceQueryChanged(query: String) {
        _replaceQuery.value = query
    }

    fun toggleMatchCase() {
        _matchCase.value = !_matchCase.value
        updateSearchMatches()
    }

    private fun updateSearchMatches() {
        val query = _searchQuery.value
        val text = _scriptText.value
        if (query.isEmpty() || text.isEmpty()) {
            _matchCount.value = 0
            _matchIndex.value = 0
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val options = if (_matchCase.value) setOf<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
            val regex = Regex(Regex.escape(query), options)
            val count = regex.findAll(text).count()
            _matchCount.value = count
            if (count == 0) {
                _matchIndex.value = 0
            } else if (_matchIndex.value >= count) {
                _matchIndex.value = 0
            }
        }
    }

    fun findNext() {
        if (_matchCount.value > 0) {
            _matchIndex.value = (_matchIndex.value + 1) % _matchCount.value
        }
    }

    fun findPrevious() {
        if (_matchCount.value > 0) {
            _matchIndex.value = if (_matchIndex.value > 0) _matchIndex.value - 1 else _matchCount.value - 1
        }
    }

    fun replaceCurrent() {
        val query = _searchQuery.value
        val replacement = _replaceQuery.value
        val text = _scriptText.value
        if (query.isEmpty() || text.isEmpty() || _matchCount.value == 0) return

        viewModelScope.launch(Dispatchers.Default) {
            val options = if (_matchCase.value) setOf<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
            val regex = Regex(Regex.escape(query), options)
            val matches = regex.findAll(text).toList()

            if (matches.isNotEmpty()) {
                val idx = _matchIndex.value.coerceIn(0, matches.size - 1)
                val targetMatch = matches[idx]
                val newText = text.replaceRange(targetMatch.range, replacement)
                withContext(Dispatchers.Main) {
                    onScriptTextChanged(newText)
                    _userMessage.value = "Replaced match ${idx + 1}"
                }
            }
        }
    }

    fun replaceAll() {
        val query = _searchQuery.value
        val replacement = _replaceQuery.value
        val text = _scriptText.value
        if (query.isEmpty() || text.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            val options = if (_matchCase.value) setOf<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
            val regex = Regex(Regex.escape(query), options)
            val count = regex.findAll(text).count()

            if (count > 0) {
                val newText = text.replace(regex, replacement)
                withContext(Dispatchers.Main) {
                    onScriptTextChanged(newText)
                    _userMessage.value = "Replaced $count occurrences"
                }
            }
        }
    }

    // --- Advanced Features: Case Converters, Smart Auto-Format, Bracket Cues Cleaner ---
    /**
     * Convert text case.
     *
     * @param selectionStart If a selection exists (selectionStart < selectionEnd),
     *   only the selected portion is converted. Otherwise the full text is converted.
     */
    fun convertCase(mode: TextCaseMode, selectionStart: Int = -1, selectionEnd: Int = -1) {
        val current = _scriptText.value
        if (current.isBlank()) return

        val hasSelection = selectionStart >= 0 && selectionEnd > selectionStart && selectionEnd <= current.length

        viewModelScope.launch(Dispatchers.Default) {
            fun applyCase(text: String): String = when (mode) {
                TextCaseMode.UPPERCASE -> text.uppercase()
                TextCaseMode.LOWERCASE -> text.lowercase()
                TextCaseMode.TITLE_CASE -> text.split(" ").joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
                TextCaseMode.SENTENCE_CASE -> text.lowercase().split(". ").joinToString(". ") { sentence ->
                    sentence.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }

            val result: String
            if (hasSelection) {
                val before = current.substring(0, selectionStart)
                val selected = current.substring(selectionStart, selectionEnd)
                val after = current.substring(selectionEnd)
                result = before + applyCase(selected) + after
            } else {
                result = applyCase(current)
            }

            withContext(Dispatchers.Main) {
                onScriptTextChanged(result)
                val scopeLabel = if (hasSelection) "selection" else "full script"
                _userMessage.value = "Converted case to ${mode.name.lowercase().replace("_", " ")} ($scopeLabel)"
            }
        }
    }

    /**
     * Remove speaker notes / bracket cues from the script.
     *
     * @param selectionStart If a selection exists (selectionStart < selectionEnd),
     *   only the selected portion is processed. Otherwise the full text is processed.
     */
    fun removeBracketCues(selectionStart: Int = -1, selectionEnd: Int = -1) {
        val current = _scriptText.value
        if (current.isBlank()) return

        val hasSelection = selectionStart >= 0 && selectionEnd > selectionStart && selectionEnd <= current.length

        viewModelScope.launch(Dispatchers.Default) {
            val result: String
            if (hasSelection) {
                val before = current.substring(0, selectionStart)
                val selected = current.substring(selectionStart, selectionEnd)
                val after = current.substring(selectionEnd)
                val cleaned = selected.replace("\\[.*?\\]".toRegex(), "").replace("\\(.*?\\)".toRegex(), "").replace(" +".toRegex(), " ").trim()
                result = before + cleaned + after
            } else {
                result = current.replace("\\[.*?\\]".toRegex(), "").replace("\\(.*?\\)".toRegex(), "").replace(" +".toRegex(), " ").trim()
            }
            withContext(Dispatchers.Main) {
                onScriptTextChanged(result)
                val scopeLabel = if (hasSelection) "selection" else "full script"
                _userMessage.value = "Removed speaker notes and bracket cues ($scopeLabel)"
            }
        }
    }

    /**
     * Apply smart auto-formatting to the script.
     *
     * @param selectionStart If a selection exists (selectionStart < selectionEnd),
     *   only the selected portion is formatted. Otherwise the full text is formatted.
     */
    fun smartAutoFormat(selectionStart: Int = -1, selectionEnd: Int = -1) {
        val current = _scriptText.value
        if (current.isBlank()) return

        val hasSelection = selectionStart >= 0 && selectionEnd > selectionStart && selectionEnd <= current.length

        viewModelScope.launch(Dispatchers.Default) {
            fun applyFormat(text: String): String {
                var formatted = text
                    .replace("\r\n", "\n")
                    .replace("“", "\"").replace("”", "\"")
                    .replace("‘", "'").replace("’", "'")
                    .replace(" ,", ",").replace(" .", ".").replace(" !", "!").replace(" ?", "?")
                    .replace(",([^\\s\\d])".toRegex(), ", \$1")
                    .replace("\\.([^\\s\\d\\.])".toRegex(), ". \$1")
                    .replace("[ \\t]+".toRegex(), " ")
                    .replace("\n{3,}".toRegex(), "\n\n")
                    .trim()

                formatted = formatted.split("\n").joinToString("\n") { line ->
                    line.split(". ").joinToString(". ") { sentence ->
                        sentence.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                }
                return formatted
            }

            val result: String
            if (hasSelection) {
                val before = current.substring(0, selectionStart)
                val selected = current.substring(selectionStart, selectionEnd)
                val after = current.substring(selectionEnd)
                result = before + applyFormat(selected) + after
            } else {
                result = applyFormat(current)
            }

            withContext(Dispatchers.Main) {
                onScriptTextChanged(result)
                val scopeLabel = if (hasSelection) "selection" else "full script"
                _userMessage.value = "Applied smart script auto-formatting ($scopeLabel)"
            }
        }
    }

    fun insertSsmlTag(ssmlTag: String) {
        val current = _scriptText.value
        val newText = if (current.isBlank()) ssmlTag else "$current $ssmlTag"
        onScriptTextChanged(newText)
        _userMessage.value = "Inserted tag: $ssmlTag"
    }

    fun setTargetWordGoal(goal: Int) {
        if (goal > 0) {
            _targetWordGoal.value = goal
        }
    }

    fun clearScriptText() {
        onScriptTextChanged("")
        _userMessage.value = "Script cleared"
    }

    fun revertToOriginal() {
        onScriptTextChanged(initialText)
        _userMessage.value = "Reverted to saved original script"
    }

    fun dismissRegenWarning() {
        _showAudioRegenWarning.value = false
    }

    fun saveScript() {
        viewModelScope.launch {
            saveScriptInternal(_scriptText.value)
        }
    }

    suspend fun saveScriptDirectly() {
        saveScriptInternal(_scriptText.value)
    }

    private suspend fun saveScriptInternal(textToSave: String) {
        if (textToSave.isBlank()) return
        _isSaving.value = true
        try {
            withContext(Dispatchers.IO) {
                projectRepository.updateDocumentScript(projectId, textToSave)
            }
            initialText = textToSave
            _isDirty.value = false
            undoStack.clear()
            redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
            _userMessage.value = "Script saved successfully!"
        } catch (e: Exception) {
            _userMessage.value = "Failed to save script: ${e.message}"
        } finally {
            _isSaving.value = false
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val parseResult = withContext(Dispatchers.IO) {
                    fileImportManager.importFileFromUri(uri)
                }
                val preprocessingResult = withContext(Dispatchers.Default) {
                    preprocessingEngine.preprocess(
                        text = parseResult.extractedText,
                        options = PreprocessingOptions(
                            removeSrtTimestamps = parseResult.sourceType == "SRT" || parseResult.sourceType == "VTT",
                            removeSrtNumbering = parseResult.sourceType == "SRT" || parseResult.sourceType == "VTT"
                        )
                    )
                }
                _importPreviewState.value = ImportPreviewState(
                    parseResult = parseResult,
                    preprocessingResult = preprocessingResult
                )
            } catch (e: Exception) {
                _userMessage.value = "Import failed: ${e.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun preprocessCurrentScript(options: PreprocessingOptions = PreprocessingOptions()) {
        val currentText = _scriptText.value
        if (currentText.isBlank()) return

        viewModelScope.launch {
            val preprocessingResult = withContext(Dispatchers.Default) {
                preprocessingEngine.preprocess(currentText, options)
            }
            _importPreviewState.value = ImportPreviewState(
                parseResult = ParseResult(
                    extractedText = currentText,
                    sourceType = "TXT",
                    originalFileName = "Current Editor Script"
                ),
                preprocessingResult = preprocessingResult
            )
        }
    }

    fun applyImportPreview() {
        val preview = _importPreviewState.value ?: return
        val newText = preview.preprocessingResult.normalizedText
        onScriptTextChanged(newText)
        _importPreviewState.value = null
        _userMessage.value = "Preprocessing applied to script"
    }

    fun cancelImportPreview() {
        _importPreviewState.value = null
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    class Factory(
        private val projectId: String,
        private val projectRepository: ProjectRepository,
        private val fileImportManager: FileImportManager,
        private val preprocessingEngine: TextPreprocessingEngine,
        private val durationEstimator: DurationEstimator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScriptEditorViewModel(
                projectId,
                projectRepository,
                fileImportManager,
                preprocessingEngine,
                durationEstimator
            ) as T
        }
    }
}
