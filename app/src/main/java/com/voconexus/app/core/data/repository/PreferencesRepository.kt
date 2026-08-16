package com.voconexus.app.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "voconexus_preferences")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultSpeed: Float = 1.0f,
    val defaultPitch: Float = 1.0f,
    val isAutoGenerationEnabled: Boolean = false
)

interface PreferencesRepository {
    val userPreferencesFlow: Flow<UserPreferences>
    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun setDefaultSpeed(speed: Float)
    suspend fun setDefaultPitch(pitch: Float)
    suspend fun setAutoGenerationEnabled(enabled: Boolean)
}

class PreferencesRepositoryImpl(
    private val context: Context
) : PreferencesRepository {

    private object PreferenceKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val DEFAULT_PITCH = floatPreferencesKey("default_pitch")
        val AUTO_GENERATION = booleanPreferencesKey("auto_generation")
    }

    override val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        val themeString = preferences[PreferenceKeys.THEME_MODE] ?: ThemeMode.DARK.name
        val themeMode = runCatching { ThemeMode.valueOf(themeString) }.getOrDefault(ThemeMode.DARK)
        val speed = preferences[PreferenceKeys.DEFAULT_SPEED] ?: 1.0f
        val pitch = preferences[PreferenceKeys.DEFAULT_PITCH] ?: 1.0f
        val autoGen = preferences[PreferenceKeys.AUTO_GENERATION] ?: false

        UserPreferences(
            themeMode = themeMode,
            defaultSpeed = speed,
            defaultPitch = pitch,
            isAutoGenerationEnabled = autoGen
        )
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.THEME_MODE] = themeMode.name
        }
    }

    override suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_SPEED] = speed
        }
    }

    override suspend fun setDefaultPitch(pitch: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_PITCH] = pitch
        }
    }

    override suspend fun setAutoGenerationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.AUTO_GENERATION] = enabled
        }
    }
}
