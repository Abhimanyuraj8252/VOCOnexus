package com.voconexus.app.ui.screens.createproject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.data.repository.VoiceRepository
import com.voconexus.app.core.data.db.TtsVoiceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.voconexus.app.core.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.combine

data class CreateProjectUiState(
    val title: String = "",
    val description: String = "",
    val scriptText: String = "",
    val selectedVoiceId: String = "af_heart",
    val selectedEngineId: String = "kokoro-82m",
    val availableVoices: List<TtsVoiceEntity> = emptyList(),
    val isCreating: Boolean = false,
    val errorMessage: String? = null
)

class CreateProjectViewModel(
    private val projectRepository: ProjectRepository,
    private val voiceRepository: VoiceRepository,
    private val userPrefsManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState: StateFlow<CreateProjectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                voiceRepository.getAllVoices(),
                userPrefsManager.preferences
            ) { voices, prefs ->
                val filteredVoices = voices.filter { it.engineId == prefs.defaultEngineId }
                Triple(filteredVoices, prefs.defaultVoiceId, prefs.defaultEngineId)
            }.collect { (filteredVoices, defaultVoiceId, defaultEngineId) ->
                val currentSelected = _uiState.value.selectedVoiceId
                val isCurrentValid = filteredVoices.any { it.id == currentSelected }
                
                val newSelected = if (isCurrentValid) {
                    currentSelected
                } else {
                    filteredVoices.firstOrNull { it.id == defaultVoiceId }?.id
                        ?: filteredVoices.firstOrNull()?.id
                        ?: "af_heart"
                }

                _uiState.value = _uiState.value.copy(
                    availableVoices = filteredVoices,
                    selectedVoiceId = newSelected,
                    selectedEngineId = defaultEngineId
                )
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        _uiState.value = _uiState.value.copy(title = newTitle, errorMessage = null)
    }

    fun onDescriptionChanged(newDescription: String) {
        _uiState.value = _uiState.value.copy(description = newDescription)
    }

    fun onScriptTextChanged(newText: String) {
        _uiState.value = _uiState.value.copy(scriptText = newText, errorMessage = null)
    }

    fun onVoiceSelected(voiceId: String) {
        _uiState.value = _uiState.value.copy(selectedVoiceId = voiceId)
    }

    fun createProject(onSuccess: (String) -> Unit) {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter a project title")
            return
        }
        if (state.scriptText.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter or paste your script text")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            try {
                val projectId = projectRepository.createProject(
                    title = state.title,
                    description = state.description,
                    rawScriptText = state.scriptText,
                    voiceId = state.selectedVoiceId
                )
                _uiState.value = _uiState.value.copy(isCreating = false)
                onSuccess(projectId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    errorMessage = e.localizedMessage ?: "Failed to create project"
                )
            }
        }
    }

    class Factory(
        private val projectRepository: ProjectRepository,
        private val voiceRepository: VoiceRepository,
        private val userPrefsManager: UserPreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CreateProjectViewModel(projectRepository, voiceRepository, userPrefsManager) as T
        }
    }
}
