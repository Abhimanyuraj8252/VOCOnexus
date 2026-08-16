package com.voconexus.app.ui.screens.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.db.PronunciationRuleEntity
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.speech.model.PronunciationEntry
import com.voconexus.app.core.speech.model.PronunciationScope
import com.voconexus.app.core.speech.plan.SpeechPlanBuilder
import com.voconexus.app.core.tts.preview.SpeechPreviewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class PronunciationUiState(
    val projectId: String = "",
    val rules: List<PronunciationRuleEntity> = emptyList(),
    val matchText: String = "",
    val replacement: String = "",
    val languageCode: String = "en",
    val scope: PronunciationScope = PronunciationScope.PROJECT,
    val isPreviewPlaying: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class PronunciationViewModel(
    private val projectId: String,
    private val database: VocoNexusDatabase,
    private val speechPlanBuilder: SpeechPlanBuilder = SpeechPlanBuilder(),
    private val previewManager: SpeechPreviewManager? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(PronunciationUiState(projectId = projectId))
    val uiState: StateFlow<PronunciationUiState> = _uiState.asStateFlow()

    init {
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch(ioDispatcher) {
            database.pronunciationRuleDao().getRulesForProjectFlow(projectId).collect { rules ->
                _uiState.value = _uiState.value.copy(rules = rules)
            }
        }
    }

    fun onMatchTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(matchText = text)
    }

    fun onReplacementChanged(text: String) {
        _uiState.value = _uiState.value.copy(replacement = text)
    }

    fun addRule() {
        val match = _uiState.value.matchText.trim()
        val replacement = _uiState.value.replacement.trim()

        if (match.isBlank() || replacement.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Match word and replacement cannot be empty")
            return
        }

        viewModelScope.launch(ioDispatcher) {
            val entity = PronunciationRuleEntity(
                id = UUID.randomUUID().toString(),
                projectId = if (_uiState.value.scope == PronunciationScope.PROJECT) projectId else null,
                matchText = match,
                replacement = replacement,
                languageCode = _uiState.value.languageCode,
                scope = _uiState.value.scope.name
            )
            database.pronunciationRuleDao().insertRule(entity)
            _uiState.value = _uiState.value.copy(
                matchText = "",
                replacement = "",
                infoMessage = "Added pronunciation rule '$match → $replacement'"
            )
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch(ioDispatcher) {
            database.pronunciationRuleDao().deleteRule(id)
            _uiState.value = _uiState.value.copy(infoMessage = "Deleted pronunciation rule")
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    class Factory(
        private val projectId: String,
        private val database: VocoNexusDatabase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PronunciationViewModel(projectId, database) as T
        }
    }
}
