package com.voconexus.app.ui.screens.scriptanalysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.db.DocumentEntity
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.planner.engine.ScriptAnalysisPlan
import com.voconexus.app.core.planner.model.ChunkingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ScriptAnalysisUiState {
    data object Idle : ScriptAnalysisUiState
    data object Analyzing : ScriptAnalysisUiState
    data class PlanReady(val plan: ScriptAnalysisPlan) : ScriptAnalysisUiState
    data object Committing : ScriptAnalysisUiState
    data object Committed : ScriptAnalysisUiState
    data class Error(val message: String) : ScriptAnalysisUiState
}

class ScriptAnalysisViewModel(
    val projectId: String,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    val projectState: StateFlow<ProjectEntity?> = projectRepository.getProjectById(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _uiState = MutableStateFlow<ScriptAnalysisUiState>(ScriptAnalysisUiState.Idle)
    val uiState: StateFlow<ScriptAnalysisUiState> = _uiState.asStateFlow()

    private val _expandedPartIndices = MutableStateFlow<Set<Int>>(setOf(0)) // First part expanded by default
    val expandedPartIndices: StateFlow<Set<Int>> = _expandedPartIndices.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        analyzeScript()
    }

    fun analyzeScript(config: ChunkingConfig = ChunkingConfig()) {
        viewModelScope.launch {
            _uiState.value = ScriptAnalysisUiState.Analyzing
            try {
                val plan = projectRepository.analyzeScript(projectId, config)
                _uiState.value = ScriptAnalysisUiState.PlanReady(plan)
            } catch (e: Exception) {
                _uiState.value = ScriptAnalysisUiState.Error(e.message ?: "Failed to analyze script")
            }
        }
    }

    fun commitPlan() {
        val currentState = _uiState.value
        if (currentState !is ScriptAnalysisUiState.PlanReady) return

        viewModelScope.launch {
            _uiState.value = ScriptAnalysisUiState.Committing
            try {
                projectRepository.commitScriptPlan(projectId, currentState.plan)
                _uiState.value = ScriptAnalysisUiState.Committed
                _userMessage.value = "Script structure committed successfully"
            } catch (e: Exception) {
                _uiState.value = ScriptAnalysisUiState.Error(e.message ?: "Failed to commit script plan")
            }
        }
    }

    fun togglePartExpanded(partIndex: Int) {
        val current = _expandedPartIndices.value
        if (current.contains(partIndex)) {
            _expandedPartIndices.value = current - partIndex
        } else {
            _expandedPartIndices.value = current + partIndex
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    class Factory(
        private val projectId: String,
        private val projectRepository: ProjectRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScriptAnalysisViewModel(projectId, projectRepository) as T
        }
    }
}
