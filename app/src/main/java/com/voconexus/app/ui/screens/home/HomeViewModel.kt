package com.voconexus.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.dao.ProjectStats
import com.voconexus.app.core.data.db.ProjectEntity
import com.voconexus.app.core.data.repository.PreferencesRepository
import com.voconexus.app.core.data.repository.ProjectRepository
import com.voconexus.app.core.storage.AudioStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SelectedProjectDialogState(
    val project: ProjectEntity? = null,
    val stats: ProjectStats? = null,
    val showRenameDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showInfoSheet: Boolean = false
)

class HomeViewModel(
    private val projectRepository: ProjectRepository,
    private val preferencesRepository: PreferencesRepository,
    private val storageManager: AudioStorageManager
) : ViewModel() {

    val projectsFlow: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userPreferences = preferencesRepository.userPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.voconexus.app.core.data.repository.UserPreferences()
    )

    private val _dialogState = MutableStateFlow(SelectedProjectDialogState())
    val dialogState: StateFlow<SelectedProjectDialogState> = _dialogState.asStateFlow()

    fun getAvailableStorageBytes(): Long {
        return storageManager.getAvailableStorageBytes()
    }

    fun openRenameDialog(project: ProjectEntity) {
        _dialogState.value = SelectedProjectDialogState(
            project = project,
            showRenameDialog = true
        )
    }

    fun openDeleteDialog(project: ProjectEntity) {
        viewModelScope.launch {
            val stats = projectRepository.getProjectStats(project.id)
            _dialogState.value = SelectedProjectDialogState(
                project = project,
                stats = stats,
                showDeleteDialog = true
            )
        }
    }

    fun openInfoSheet(project: ProjectEntity) {
        viewModelScope.launch {
            val stats = projectRepository.getProjectStats(project.id)
            _dialogState.value = SelectedProjectDialogState(
                project = project,
                stats = stats,
                showInfoSheet = true
            )
        }
    }

    fun dismissDialogs() {
        _dialogState.value = SelectedProjectDialogState()
    }

    fun renameProject(newTitle: String) {
        val project = _dialogState.value.project ?: return
        if (newTitle.isBlank()) return

        viewModelScope.launch {
            projectRepository.renameProject(project.id, newTitle)
            dismissDialogs()
        }
    }

    fun confirmDeleteProject() {
        val project = _dialogState.value.project ?: return
        viewModelScope.launch {
            projectRepository.deleteProjectSafely(project.id)
            dismissDialogs()
        }
    }

    class Factory(
        private val projectRepository: ProjectRepository,
        private val preferencesRepository: PreferencesRepository,
        private val storageManager: AudioStorageManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(projectRepository, preferencesRepository, storageManager) as T
        }
    }
}
