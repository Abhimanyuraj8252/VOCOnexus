package com.voconexus.app.core.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class QualityPreset {
    BALANCED,
    HIGH_QUALITY,
    STORAGE_EFFICIENT;

    fun toSampleRate(): Int = when (this) {
        HIGH_QUALITY -> 44100
        BALANCED -> 24000
        STORAGE_EFFICIENT -> 16000
    }

    fun toBitrate(): Int = when (this) {
        HIGH_QUALITY -> 320000
        BALANCED -> 192000
        STORAGE_EFFICIENT -> 128000
    }
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColorHex: String = "#3F51B5",
    val isHighContrastEnabled: Boolean = false,
    val isOnboardingCompleted: Boolean = false,
    val defaultPlaybackSpeed: Float = 1.0f,
    val autoPlayNextChunk: Boolean = true,
    val rememberLastPosition: Boolean = true,
    val defaultEngineId: String = "kokoro-82m",
    val defaultModelId: String = "kokoro-v1.0",
    val defaultVoiceId: String = "af_heart",
    val selectedModelId: String = "kokoro-v1.0",  // Globally active model
    val qualityPreset: QualityPreset = QualityPreset.BALANCED,
    val preferredOutputFormat: String = "WAV",
    val isWifiOnlyDownloads: Boolean = true,
    val isAutoCheckUpdates: Boolean = true,
    val isNotificationsEnabled: Boolean = true,
    val isGenerationNotificationEnabled: Boolean = true,
    val isDownloadNotificationEnabled: Boolean = true,
    val fontScaleOption: String = "NORMAL"
)

class UserPreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadPreferences(): UserPreferences {
        val themeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)

        val presetStr = prefs.getString(KEY_QUALITY_PRESET, QualityPreset.BALANCED.name) ?: QualityPreset.BALANCED.name
        val qualityPreset = runCatching { QualityPreset.valueOf(presetStr) }.getOrDefault(QualityPreset.BALANCED)

        return UserPreferences(
            themeMode = themeMode,
            accentColorHex = prefs.getString(KEY_ACCENT_COLOR, "#3F51B5") ?: "#3F51B5",
            isHighContrastEnabled = prefs.getBoolean(KEY_HIGH_CONTRAST, false),
            isOnboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false),
            defaultPlaybackSpeed = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f),
            autoPlayNextChunk = prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true),
            rememberLastPosition = prefs.getBoolean(KEY_REMEMBER_POSITION, true),
            defaultEngineId = prefs.getString(KEY_DEFAULT_ENGINE, "kokoro-82m") ?: "kokoro-82m",
            defaultModelId = prefs.getString(KEY_DEFAULT_MODEL, "kokoro-v1.0") ?: "kokoro-v1.0",
            defaultVoiceId = prefs.getString(KEY_DEFAULT_VOICE, "af_heart") ?: "af_heart",
            selectedModelId = prefs.getString(KEY_SELECTED_MODEL, "kokoro-v1.0") ?: "kokoro-v1.0",
            qualityPreset = qualityPreset,
            preferredOutputFormat = prefs.getString(KEY_OUTPUT_FORMAT, "WAV") ?: "WAV",
            isWifiOnlyDownloads = prefs.getBoolean(KEY_WIFI_ONLY, true),
            isAutoCheckUpdates = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true),
            isNotificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
            isGenerationNotificationEnabled = prefs.getBoolean(KEY_GEN_NOTIF_ENABLED, true),
            isDownloadNotificationEnabled = prefs.getBoolean(KEY_DOWNLOAD_NOTIF_ENABLED, true),
            fontScaleOption = prefs.getString(KEY_FONT_SCALE, "NORMAL") ?: "NORMAL"
        )
    }

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _preferences.value = _preferences.value.copy(themeMode = mode)
    }

    fun setHighContrastEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
        _preferences.value = _preferences.value.copy(isHighContrastEnabled = enabled)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _preferences.value = _preferences.value.copy(isOnboardingCompleted = completed)
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()
        _preferences.value = _preferences.value.copy(defaultPlaybackSpeed = speed)
    }

    fun setAutoPlayNextChunk(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply()
        _preferences.value = _preferences.value.copy(autoPlayNextChunk = enabled)
    }

    fun setQualityPreset(preset: QualityPreset) {
        prefs.edit().putString(KEY_QUALITY_PRESET, preset.name).apply()
        _preferences.value = _preferences.value.copy(qualityPreset = preset)
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        _preferences.value = _preferences.value.copy(isWifiOnlyDownloads = enabled)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        _preferences.value = _preferences.value.copy(isNotificationsEnabled = enabled)
    }

    fun setSelectedModel(modelId: String, targetEngineId: String? = null) {
        // Derive the engine ID from the target engine ID or model ID so voices filter correctly
        val engineId = targetEngineId ?: when {
            modelId.startsWith("edge", ignoreCase = true) -> "edge-tts"
            modelId.startsWith("google", ignoreCase = true) -> "google-cloud-tts"
            modelId.startsWith("piper", ignoreCase = true) -> "piper-onnx"
            modelId.startsWith("kokoro", ignoreCase = true) -> "kokoro-82m"
            modelId.startsWith("sherpa", ignoreCase = true) || modelId.startsWith("vits", ignoreCase = true) -> "sherpa-onnx"
            else -> _preferences.value.defaultEngineId // keep current
        }
        prefs.edit()
            .putString(KEY_SELECTED_MODEL, modelId)
            .putString(KEY_DEFAULT_ENGINE, engineId)
            .apply()
        _preferences.value = _preferences.value.copy(
            selectedModelId = modelId,
            defaultEngineId = engineId
        )
    }

    fun setDefaultEngine(engineId: String) {
        prefs.edit().putString(KEY_DEFAULT_ENGINE, engineId).apply()
        _preferences.value = _preferences.value.copy(defaultEngineId = engineId)
    }

    fun resetPreferencesToDefault() {
        prefs.edit().clear().apply()
        _preferences.value = loadPreferences()
    }

    companion object {
        @Volatile
        private var instance: UserPreferencesManager? = null

        fun getInstance(context: Context): UserPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: UserPreferencesManager(context.applicationContext).also { instance = it }
            }
        }

        private const val PREFS_NAME = "voconexus_user_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        private const val KEY_REMEMBER_POSITION = "remember_position"
        private const val KEY_DEFAULT_ENGINE = "default_engine"
        private const val KEY_DEFAULT_MODEL = "default_model"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_DEFAULT_VOICE = "default_voice"
        private const val KEY_QUALITY_PRESET = "quality_preset"
        private const val KEY_OUTPUT_FORMAT = "output_format"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_GEN_NOTIF_ENABLED = "gen_notif_enabled"
        private const val KEY_DOWNLOAD_NOTIF_ENABLED = "download_notif_enabled"
        private const val KEY_FONT_SCALE = "font_scale"
    }
}
