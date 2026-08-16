package com.voconexus.app.ui.screens.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.db.BenchmarkEntity
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.storage.StorageBreakdown
import com.voconexus.app.core.storage.StorageManager
import com.voconexus.app.core.tts.device.CompatibilityReport
import com.voconexus.app.core.tts.device.DeviceProfile
import com.voconexus.app.core.tts.device.DeviceProfileEvaluator
import com.voconexus.app.core.tts.device.ModelRecommendation
import com.voconexus.app.core.tts.device.ModelRecommendationEngine
import com.voconexus.app.core.tts.device.ModelRequirements
import com.voconexus.app.core.tts.device.ThermalMonitor
import com.voconexus.app.core.tts.device.ThermalStateLevel
import com.voconexus.app.core.tts.device.TtsBenchmarkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceDashboardUiState(
    val deviceProfile: DeviceProfile? = null,
    val compatibilityReport: CompatibilityReport? = null,
    val storageBreakdown: StorageBreakdown? = null,
    val thermalLevel: ThermalStateLevel = ThermalStateLevel.NONE,
    val recommendedModel: ModelRecommendation? = null,
    val benchmarks: List<BenchmarkEntity> = emptyList(),
    val isRunningBenchmark: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class DeviceDashboardViewModel(
    private val evaluator: DeviceProfileEvaluator,
    private val thermalMonitor: ThermalMonitor,
    private val storageManager: StorageManager,
    private val recommendationEngine: ModelRecommendationEngine,
    private val benchmarkManager: TtsBenchmarkManager,
    private val database: VocoNexusDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceDashboardUiState())
    val uiState: StateFlow<DeviceDashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
        observeThermalAndBenchmarks()
    }

    fun loadDashboardData() {
        val profile = evaluator.evaluateDeviceProfile()
        val report = evaluator.classifyCompatibility(profile)
        val storage = storageManager.getStorageBreakdown()

        val kokoroReqs = ModelRequirements(
            modelId = "kokoro-82m-v1.0",
            modelName = "Kokoro 82M",
            minimumRamMb = 2048,
            recommendedRamMb = 4096,
            estimatedRuntimeMemoryMb = 600,
            modelFileSizeBytes = 340 * 1024 * 1024L
        )

        val recommendation = recommendationEngine.evaluateRecommendation(profile, kokoroReqs)

        _uiState.value = _uiState.value.copy(
            deviceProfile = profile,
            compatibilityReport = report,
            storageBreakdown = storage,
            recommendedModel = recommendation
        )
    }

    private fun observeThermalAndBenchmarks() {
        viewModelScope.launch(ioDispatcher) {
            thermalMonitor.thermalLevel.collect { level ->
                _uiState.value = _uiState.value.copy(thermalLevel = level)
            }
        }

        viewModelScope.launch(ioDispatcher) {
            database.benchmarkDao().getAllBenchmarksFlow().collect { list ->
                _uiState.value = _uiState.value.copy(benchmarks = list)
            }
        }
    }

    fun runPerformanceBenchmark() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = _uiState.value.copy(isRunningBenchmark = true, errorMessage = null)
            try {
                val dummyEngine = com.voconexus.app.core.tts.engine.FakeTtsEngine()
                val result = benchmarkManager.runLocalBenchmark(
                    engine = dummyEngine,
                    modelId = "kokoro-82m-v1.0",
                    voiceId = "af_heart"
                )

                val entity = BenchmarkEntity(
                    id = result.id,
                    engineId = result.engineId,
                    modelId = result.modelId,
                    voiceId = result.voiceId,
                    coldStartMs = result.coldStartMs,
                    synthesisMs = result.synthesisMs,
                    audioDurationMs = result.audioDurationMs,
                    realTimeFactor = result.realTimeFactor,
                    peakMemoryMb = result.peakMemoryMb
                )

                database.benchmarkDao().insertBenchmark(entity)
                _uiState.value = _uiState.value.copy(
                    isRunningBenchmark = false,
                    infoMessage = "Benchmark completed! RTF: ${"%.2f".format(result.realTimeFactor)}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunningBenchmark = false,
                    errorMessage = e.message ?: "Failed to run performance test"
                )
            }
        }
    }

    fun clearTempCache() {
        viewModelScope.launch(ioDispatcher) {
            val freedBytes = storageManager.clearTemporaryCache()
            val freedMb = freedBytes / (1024 * 1024)
            loadDashboardData()
            _uiState.value = _uiState.value.copy(infoMessage = "Cleared ${freedMb} MB temporary cache.")
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    class Factory(
        private val evaluator: DeviceProfileEvaluator,
        private val thermalMonitor: ThermalMonitor,
        private val storageManager: StorageManager,
        private val database: VocoNexusDatabase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceDashboardViewModel(
                evaluator = evaluator,
                thermalMonitor = thermalMonitor,
                storageManager = storageManager,
                recommendationEngine = ModelRecommendationEngine(),
                benchmarkManager = TtsBenchmarkManager(),
                database = database
            ) as T
        }
    }
}
