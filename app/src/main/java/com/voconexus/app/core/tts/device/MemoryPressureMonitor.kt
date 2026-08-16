package com.voconexus.app.core.tts.device

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MemoryPressureState {
    NORMAL,
    MODERATE,
    CRITICAL
}

@Suppress("DEPRECATION")
class MemoryPressureMonitor(private val context: Context) : ComponentCallbacks2 {

    private val _memoryState = MutableStateFlow(MemoryPressureState.NORMAL)
    val memoryState: StateFlow<MemoryPressureState> = _memoryState.asStateFlow()

    init {
        context.registerComponentCallbacks(this)
    }

    override fun onTrimMemory(level: Int) {
        val state = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MemoryPressureState.CRITICAL
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> MemoryPressureState.MODERATE
            else -> MemoryPressureState.NORMAL
        }
        _memoryState.value = state
    }

    @Deprecated("Deprecated in Android API 34+")
    override fun onLowMemory() {
        _memoryState.value = MemoryPressureState.CRITICAL
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op
    }

    fun unregister() {
        context.unregisterComponentCallbacks(this)
    }
}
