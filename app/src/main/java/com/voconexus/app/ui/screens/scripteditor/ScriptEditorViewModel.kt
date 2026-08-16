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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportPreviewState(
    val parseResult: ParseResult,
    val preprocessingResult: PreprocessingResult
)

class ScriptEditorViewModel(
    val projectId: String,
    private val projectRepository: ProjectRepository,
    private val fileImportManager: FileImportManager,
    private val preprocessingEngine: TextPreprocessingEngine,
    private val durationEstimator: DurationEstimator
) : ViewModel() {

    val projectState: StateFlow<ProjectEntity?> = projectRepository.getProjectById(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _scriptText = MutableStateFlow("")
    val scriptText: StateFlow<String> = _scriptText.asStateFlow()

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

    init {
        loadDocument()
        observeStatsAndAutosave()
    }

    private fun loadDocument() {
        viewModelScope.launch {
            val chunks = projectRepository.getChunksForProjectDirect(projectId)
            _hasGeneratedAudio.value = chunks.any { it.status == ChunkStatus.COMPLETED }

            val documentText = chunks.joinToString("\n\n") { it.sourceText }
            initialText = documentText
            _scriptText.value = documentText
            _isDirty.value = false
            _textStats.value = Formatters.calculateTextStatistics(documentText, durationEstimator)
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeStatsAndAutosave() {
        _scriptText
            .debounce(300)
            .onEach { text ->
                _textStats.value = Formatters.calculateTextStatistics(text, durationEstimator)
            }
            .launchIn(viewModelScope)

        _scriptText
            .debounce(3000)
            .onEach { text ->
                if (_isDirty.value && text.isNotBlank() && text != initialText) {
                    saveScriptInternal(text)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onScriptTextChanged(newText: String) {
        _scriptText.value = newText
        _isDirty.value = (newText != initialText)
        if (_hasGeneratedAudio.value && _isDirty.value) {
            _showAudioRegenWarning.value = true
        }
    }

    fun dismissRegenWarning() {
        _showAudioRegenWarning.value = false
    }

    fun saveScript() {
        viewModelScope.launch {
            saveScriptInternal(_scriptText.value)
        }
    }

    private suspend fun saveScriptInternal(textToSave: String) {
        if (textToSave.isBlank()) return
        _isSaving.value = true
        try {
            projectRepository.updateDocumentScript(projectId, textToSave)
            initialText = textToSave
            _isDirty.value = false
            _userMessage.value = "Script saved successfully"
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
                val parseResult = fileImportManager.importFileFromUri(uri)
                val preprocessingResult = preprocessingEngine.preprocess(
                    text = parseResult.extractedText,
                    options = PreprocessingOptions(
                        removeSrtTimestamps = parseResult.sourceType == "SRT",
                        removeSrtNumbering = parseResult.sourceType == "SRT"
                    )
                )
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
            val preprocessingResult = preprocessingEngine.preprocess(currentText, options)
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
