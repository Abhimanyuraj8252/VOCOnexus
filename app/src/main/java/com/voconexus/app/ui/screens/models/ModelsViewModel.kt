package com.voconexus.app.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.repository.TtsRepository
import com.voconexus.app.core.tts.TtsModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelsViewModel(
    private val ttsRepository: TtsRepository
) : ViewModel() {

    val modelsState: StateFlow<List<TtsModel>> = ttsRepository.getAllModels()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            try {
                android.util.Log.i("ModelsViewModel", "Starting model download: $modelId")
                ttsRepository.installModel(modelId)
                android.util.Log.i("ModelsViewModel", "Model download & install finished: $modelId")
            } catch (e: Exception) {
                android.util.Log.e("ModelsViewModel", "Model download failed: $modelId", e)
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            try {
                ttsRepository.deleteModel(modelId)
            } catch (e: Exception) {
                android.util.Log.e("ModelsViewModel", "Model delete failed: $modelId", e)
            }
        }
    }

    class Factory(
        private val ttsRepository: TtsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ModelsViewModel(ttsRepository) as T
        }
    }
}
