package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.preferences.QualityPreset
import com.voconexus.app.core.preferences.ThemeMode
import com.voconexus.app.core.preferences.UserPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserPreferencesManagerTest {

    private lateinit var preferencesManager: UserPreferencesManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferencesManager = UserPreferencesManager.getInstance(context)
        preferencesManager.resetPreferencesToDefault()
    }

    @Test
    fun testDefaultPreferences() {
        val prefs = preferencesManager.preferences.value

        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
        assertFalse(prefs.isOnboardingCompleted)
        assertTrue(prefs.autoPlayNextChunk)
        assertEquals(QualityPreset.BALANCED, prefs.qualityPreset)
        assertTrue(prefs.isWifiOnlyDownloads)
    }

    @Test
    fun testUpdateThemeModeAndOnboarding() {
        preferencesManager.setThemeMode(ThemeMode.DARK)
        preferencesManager.setOnboardingCompleted(true)

        val updated = preferencesManager.preferences.value
        assertEquals(ThemeMode.DARK, updated.themeMode)
        assertTrue(updated.isOnboardingCompleted)
    }

    @Test
    fun testQualityPresetAndWifiToggle() {
        preferencesManager.setQualityPreset(QualityPreset.HIGH_QUALITY)
        preferencesManager.setWifiOnlyDownloads(false)

        val updated = preferencesManager.preferences.value
        assertEquals(QualityPreset.HIGH_QUALITY, updated.qualityPreset)
        assertFalse(updated.isWifiOnlyDownloads)
    }
}
