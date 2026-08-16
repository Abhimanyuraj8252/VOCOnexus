package com.voconexus.app.core.tts.device

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThermalStateLevel(val code: Int) {
    NONE(0),
    LIGHT(1),
    MODERATE(2),
    SEVERE(3),
    CRITICAL(4),
    EMERGENCY(5);

    companion object {
        fun fromPowerManagerCode(code: Int): ThermalStateLevel = when (code) {
            0 -> NONE
            1 -> LIGHT
            2 -> MODERATE
            3 -> SEVERE
            4 -> CRITICAL
            5, 6 -> EMERGENCY
            else -> NONE
        }
    }
}

enum class ThermalActionPolicy {
    FULL_SPEED,
    REDUCE_CONCURRENCY,
    PAUSE_NON_ESSENTIAL,
    PAUSE_ALL_GENERATION
}

class ThermalMonitor(private val context: Context) {

    private val _thermalLevel = MutableStateFlow(ThermalStateLevel.NONE)
    val thermalLevel: StateFlow<ThermalStateLevel> = _thermalLevel.asStateFlow()

    private var listener: Any? = null

    init {
        initThermalListener()
    }

    private fun initThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                _thermalLevel.value = ThermalStateLevel.fromPowerManagerCode(powerManager.currentThermalStatus)

                val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                    _thermalLevel.value = ThermalStateLevel.fromPowerManagerCode(status)
                }
                listener = thermalListener
                powerManager.addThermalStatusListener(thermalListener)
            }
        }
    }

    fun getPolicyForCurrentThermalState(): ThermalActionPolicy {
        return when (_thermalLevel.value) {
            ThermalStateLevel.NONE, ThermalStateLevel.LIGHT -> ThermalActionPolicy.FULL_SPEED
            ThermalStateLevel.MODERATE -> ThermalActionPolicy.REDUCE_CONCURRENCY
            ThermalStateLevel.SEVERE -> ThermalActionPolicy.PAUSE_NON_ESSENTIAL
            ThermalStateLevel.CRITICAL, ThermalStateLevel.EMERGENCY -> ThermalActionPolicy.PAUSE_ALL_GENERATION
        }
    }
}
