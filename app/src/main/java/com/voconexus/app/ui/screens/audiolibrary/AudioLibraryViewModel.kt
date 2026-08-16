package com.voconexus.app.ui.screens.audiolibrary

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.db.AudioAssetEntity
import com.voconexus.app.core.data.db.ChunkEntity
import com.voconexus.app.core.data.db.PartEntity
import com.voconexus.app.core.data.dao.ProjectAudioSummary
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.repository.AudioRepository
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.export.AudioExporter
import com.voconexus.app.core.playback.PlayableItem
import com.voconexus.app.core.playback.PlaybackController
import com.voconexus.app.core.storage.AudioStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class AudioLibraryUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val selectedProjectId: String? = null,
    val selectedPartId: String? = null,
    val parts: List<PartEntity> = emptyList(),
    val chunks: List<ChunkEntity> = emptyList(),
    val audioAssets: List<AudioAssetEntity> = emptyList(),
    val selectedChunkIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val projectSummary: ProjectAudioSummary = ProjectAudioSummary(),
    val storageUsedBytes: Long = 0L,
    val availableStorageBytes: Long = 0L,
    val isMultiSelectMode: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class AudioLibraryViewModel(
    private val audioRepository: AudioRepository,
    private val projectRepository: ProjectRepository,
    private val playbackController: PlaybackController,
    private val audioExporter: AudioExporter,
    private val storageManager: AudioStorageManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioLibraryUiState())
    val uiState: StateFlow<AudioLibraryUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
        updateStorageStats()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            projectRepository.getAllProjects().collect { projects ->
                _uiState.value = _uiState.value.copy(
                    projects = projects,
                    selectedProjectId = _uiState.value.selectedProjectId ?: projects.firstOrNull()?.id
                )
                _uiState.value.selectedProjectId?.let { selectProject(it) }
            }
        }
    }

    fun selectProject(projectId: String) {
        _uiState.value = _uiState.value.copy(
            selectedProjectId = projectId,
            selectedPartId = null,
            selectedChunkIds = emptySet()
        )
        viewModelScope.launch {
            val summary = audioRepository.getProjectAudioSummary(projectId)
            _uiState.value = _uiState.value.copy(projectSummary = summary)
        }
        observePartsAndChunks(projectId)
        observeAudioAssets(projectId)
    }

    private fun observePartsAndChunks(projectId: String) {
        viewModelScope.launch {
            projectRepository.getPartsForProject(projectId).collect { parts ->
                _uiState.value = _uiState.value.copy(parts = parts)
            }
        }
        viewModelScope.launch {
            projectRepository.getChunksForProject(projectId).collect { chunks ->
                _uiState.value = _uiState.value.copy(chunks = chunks)
            }
        }
    }

    private fun observeAudioAssets(projectId: String) {
        viewModelScope.launch {
            audioRepository.getAssetsForProjectFlow(projectId).collect { assets ->
                _uiState.value = _uiState.value.copy(audioAssets = assets)
            }
        }
    }

    private fun updateStorageStats() {
        _uiState.value = _uiState.value.copy(
            availableStorageBytes = storageManager.getAvailableStorageBytes()
        )
    }

    fun selectPart(partId: String?) {
        _uiState.value = _uiState.value.copy(selectedPartId = partId)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleChunkSelection(chunkId: String) {
        val current = _uiState.value.selectedChunkIds.toMutableSet()
        if (current.contains(chunkId)) {
            current.remove(chunkId)
        } else {
            current.add(chunkId)
        }
        _uiState.value = _uiState.value.copy(
            selectedChunkIds = current,
            isMultiSelectMode = current.isNotEmpty()
        )
    }

    fun selectAll() {
        val allIds = _uiState.value.chunks.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedChunkIds = allIds,
            isMultiSelectMode = allIds.isNotEmpty()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedChunkIds = emptySet(),
            isMultiSelectMode = false
        )
    }

    fun invertSelection() {
        val allIds = _uiState.value.chunks.map { it.id }.toSet()
        val current = _uiState.value.selectedChunkIds
        val inverted = allIds.minus(current)
        _uiState.value = _uiState.value.copy(
            selectedChunkIds = inverted,
            isMultiSelectMode = inverted.isNotEmpty()
        )
    }

    fun playChunk(chunk: ChunkEntity) {
        val asset = _uiState.value.audioAssets.firstOrNull { it.chunkId == chunk.id }
        if (asset == null || !File(asset.filePath).exists()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Generated audio file unavailable for Chunk #${chunk.sequenceIndex + 1}")
            return
        }

        playbackController.playItem(
            PlayableItem(
                chunkId = chunk.id,
                partId = chunk.partId,
                projectId = chunk.projectId,
                title = "Chunk #${chunk.sequenceIndex + 1}",
                subtitle = chunk.normalizedText,
                audioPath = asset.filePath,
                durationMs = asset.durationMs.toLong()
            )
        )
    }

    fun playSelectedPlaylist() {
        val selectedIds = _uiState.value.selectedChunkIds
        if (selectedIds.isEmpty()) return

        val selectedChunks = _uiState.value.chunks.filter { selectedIds.contains(it.id) }
            .sortedBy { it.sequenceIndex }

        val playableItems = selectedChunks.mapNotNull { chunk ->
            val asset = _uiState.value.audioAssets.firstOrNull { it.chunkId == chunk.id }
            if (asset != null && File(asset.filePath).exists()) {
                PlayableItem(
                    chunkId = chunk.id,
                    partId = chunk.partId,
                    projectId = chunk.projectId,
                    title = "Chunk #${chunk.sequenceIndex + 1}",
                    subtitle = chunk.normalizedText,
                    audioPath = asset.filePath,
                    durationMs = asset.durationMs.toLong()
                )
            } else null
        }

        if (playableItems.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "No valid generated audio files found in selection")
            return
        }

        playbackController.playPlaylist(playableItems, 0)
    }

    fun deleteSelectedAudio() {
        val selectedIds = _uiState.value.selectedChunkIds.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            val count = audioRepository.deleteAudioAssets(selectedIds)
            clearSelection()
            _uiState.value = _uiState.value.copy(infoMessage = "Deleted audio for $count chunk(s)")
            _uiState.value.selectedProjectId?.let { selectProject(it) }
        }
    }

    fun combineAndExportSelection(targetUri: Uri) {
        val selectedIds = _uiState.value.selectedChunkIds
        val selectedChunks = _uiState.value.chunks.filter { selectedIds.contains(it.id) }
            .sortedBy { it.sequenceIndex }

        val sourceFiles = selectedChunks.mapNotNull { chunk ->
            val asset = _uiState.value.audioAssets.firstOrNull { it.chunkId == chunk.id }
            if (asset != null && File(asset.filePath).exists()) File(asset.filePath) else null
        }

        if (sourceFiles.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "No valid audio files to combine")
            return
        }

        _uiState.value = _uiState.value.copy(isExporting = true, exportProgress = 0f)

        viewModelScope.launch {
            val success = audioExporter.combineAndExportToUri(
                sourceFiles = sourceFiles,
                targetUri = targetUri,
                onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(exportProgress = progress)
                }
            )

            _uiState.value = _uiState.value.copy(
                isExporting = false,
                exportProgress = 0f,
                infoMessage = if (success) "Export complete!" else "Export failed. Check format compatibility."
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    class Factory(
        private val audioRepository: AudioRepository,
        private val projectRepository: ProjectRepository,
        private val playbackController: PlaybackController,
        private val audioExporter: AudioExporter,
        private val storageManager: AudioStorageManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AudioLibraryViewModel(
                audioRepository,
                projectRepository,
                playbackController,
                audioExporter,
                storageManager
            ) as T
        }
    }
}
