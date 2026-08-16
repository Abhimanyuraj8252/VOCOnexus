package com.voconexus.app.core.generation

import com.voconexus.app.core.storage.StorageManager
import com.voconexus.app.core.tts.device.MemoryPressureMonitor
import com.voconexus.app.core.tts.device.MemoryPressureState
import com.voconexus.app.core.tts.device.ThermalActionPolicy
import com.voconexus.app.core.tts.device.ThermalMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SchedulerStatus {
    IDLE,
    GENERATING,
    THROTTLED,
    WAITING_FOR_RESOURCES,
    PAUSED,
    FAILED
}

enum class PauseReason {
    NONE,
    USER,
    THERMAL,
    MEMORY,
    STORAGE,
    MODEL,
    ERROR,
    SYSTEM
}

data class SchedulerState(
    val status: SchedulerStatus = SchedulerStatus.IDLE,
    val pauseReason: PauseReason = PauseReason.NONE,
    val maxConcurrency: Int = 1,
    val statusMessage: String = "Idle"
)

class AdaptiveGenerationScheduler(
    private val thermalMonitor: ThermalMonitor,
    private val memoryMonitor: MemoryPressureMonitor,
    private val storageManager: StorageManager
) {

    private val _schedulerState = MutableStateFlow(SchedulerState())
    val schedulerState: StateFlow<SchedulerState> = _schedulerState.asStateFlow()

    fun evaluateSchedulerPolicy(isGenerating: Boolean): SchedulerState {
        if (!isGenerating) {
            val idleState = SchedulerState(status = SchedulerStatus.IDLE, statusMessage = "Idle")
            _schedulerState.value = idleState
            return idleState
        }

        // 1. Storage Preflight Check
        val storageCheck = storageManager.checkStoragePreflight(requiredBytes = 50 * 1024 * 1024L) // 50MB buffer
        if (!storageCheck.isEnoughStorage) {
            val state = SchedulerState(
                status = SchedulerStatus.WAITING_FOR_RESOURCES,
                pauseReason = PauseReason.STORAGE,
                maxConcurrency = 0,
                statusMessage = "Waiting: Insufficient storage available."
            )
            _schedulerState.value = state
            return state
        }

        // 2. Memory Pressure Check
        if (memoryMonitor.memoryState.value == MemoryPressureState.CRITICAL) {
            val state = SchedulerState(
                status = SchedulerStatus.THROTTLED,
                pauseReason = PauseReason.MEMORY,
                maxConcurrency = 1,
                statusMessage = "Throttled: High memory pressure detected."
            )
            _schedulerState.value = state
            return state
        }

        // 3. Thermal Check
        val thermalPolicy = thermalMonitor.getPolicyForCurrentThermalState()
        val newState = when (thermalPolicy) {
            ThermalActionPolicy.FULL_SPEED -> SchedulerState(
                status = SchedulerStatus.GENERATING,
                pauseReason = PauseReason.NONE,
                maxConcurrency = 2,
                statusMessage = "Generating (Full Speed)"
            )
            ThermalActionPolicy.REDUCE_CONCURRENCY -> SchedulerState(
                status = SchedulerStatus.THROTTLED,
                pauseReason = PauseReason.THERMAL,
                maxConcurrency = 1,
                statusMessage = "Throttled: Device temperature is elevated."
            )
            ThermalActionPolicy.PAUSE_NON_ESSENTIAL, ThermalActionPolicy.PAUSE_ALL_GENERATION -> SchedulerState(
                status = SchedulerStatus.PAUSED,
                pauseReason = PauseReason.THERMAL,
                maxConcurrency = 0,
                statusMessage = "Paused: High device temperature protection."
            )
        }

        _schedulerState.value = newState
        return newState
    }

    fun userRequestPause() {
        _schedulerState.value = _schedulerState.value.copy(
            status = SchedulerStatus.PAUSED,
            pauseReason = PauseReason.USER,
            maxConcurrency = 0,
            statusMessage = "Paused by user"
        )
    }

    fun canAutoResume(reason: PauseReason): Boolean {
        // User pauses MUST remain paused until explicit user action
        return reason != PauseReason.USER && reason != PauseReason.NONE
    }
}
