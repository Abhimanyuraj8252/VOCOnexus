package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.core.storage.StorageManager
import com.voconexus.app.core.tts.device.DeviceProfileEvaluator
import com.voconexus.app.core.tts.device.ThermalMonitor
import com.voconexus.app.ui.screens.device.DeviceDashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceDashboardViewModelTest {

    private lateinit var database: VocoNexusDatabase
    private lateinit var viewModel: DeviceDashboardViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)

        val evaluator = DeviceProfileEvaluator(context)
        val thermalMonitor = ThermalMonitor(context)
        val storageManager = StorageManager(context)

        viewModel = DeviceDashboardViewModel.Factory(
            evaluator = evaluator,
            thermalMonitor = thermalMonitor,
            storageManager = storageManager,
            database = database
        ).create(DeviceDashboardViewModel::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testDashboardViewModelInitialization() = runBlocking {
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNotNull(uiState.deviceProfile)
        assertNotNull(uiState.compatibilityReport)
        assertNotNull(uiState.recommendedModel)
    }
}
