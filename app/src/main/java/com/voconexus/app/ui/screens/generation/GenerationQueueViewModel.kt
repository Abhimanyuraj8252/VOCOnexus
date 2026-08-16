package com.voconexus.app.ui.screens.generation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.repository.GenerationRepository
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.generation.GenerationJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GenerationQueueUiState(
    val job: GenerationJob? = null,
    val parts: List<PartEntity> = emptyList(),
    val chunks: List<ChunkEntity> = emptyList(),
    val overallProgress: Float = 0f,
    val completedChunksCount: Int = 0,
    val totalChunksCount: Int = 0,
    val failedChunksCount: Int = 0,
    val activeChunkId: String? = null,
    val estimatedTimeRemainingMs: Long = 0L,
    val isOfflineBadge: Boolean = true,
    val errorMessage: String? = null
)

class GenerationQueueViewModel(
    private val projectId: String,
    private val generationRepository: GenerationRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerationQueueUiState())
    val uiState: StateFlow<GenerationQueueUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            generationRepository.runStartupRecovery(projectId)
            observeJob()
            observeChunks()
            observeParts()
        }
    }

    private fun observeJob() {
        viewModelScope.launch {
            generationRepository.getLatestJobFlow(projectId).collect { job ->
                _uiState.value = _uiState.value.copy(
                    job = job,
                    activeChunkId = job?.activeChunkId
                )
            }
        }
    }

    private fun observeChunks() {
        viewModelScope.launch {
            generationRepository.getChunksFlow(projectId).collect { chunks ->
                val completed = chunks.count { it.status == com.voconexus.app.core.data.db.ChunkStatus.COMPLETED }
                val failed = chunks.count { it.status == com.voconexus.app.core.data.db.ChunkStatus.FAILED }
                val total = chunks.size
                val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f

                val remainingChunks = total - completed
                val estRemainingMs = remainingChunks * 4000L // 4s average per chunk

                _uiState.value = _uiState.value.copy(
                    chunks = chunks,
                    completedChunksCount = completed,
                    failedChunksCount = failed,
                    totalChunksCount = total,
                    overallProgress = progress,
                    estimatedTimeRemainingMs = estRemainingMs
                )
            }
        }
    }

    private fun observeParts() {
        viewModelScope.launch {
            projectRepository.getPartsForProject(projectId).collect { parts ->
                _uiState.value = _uiState.value.copy(parts = parts)
            }
        }
    }

    fun startGeneration(documentId: String) {
        viewModelScope.launch {
            try {
                generationRepository.createAndStartJob(
                    projectId = projectId,
                    documentId = documentId
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Failed to start generation")
            }
        }
    }

    fun pauseGeneration() {
        viewModelScope.launch {
            generationRepository.pauseActiveJob(projectId)
        }
    }

    fun stopGeneration() {
        viewModelScope.launch {
            generationRepository.stopActiveJob(projectId)
        }
    }

    fun cancelGeneration() {
        viewModelScope.launch {
            generationRepository.cancelActiveJob(projectId)
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            generationRepository.retryFailedChunks(projectId)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    companion object {
        fun provideFactory(
            projectId: String,
            generationRepository: GenerationRepository,
            projectRepository: ProjectRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GenerationQueueViewModel(projectId, generationRepository, projectRepository) as T
            }
        }
    }
}
