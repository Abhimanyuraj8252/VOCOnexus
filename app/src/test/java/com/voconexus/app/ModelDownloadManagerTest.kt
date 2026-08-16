package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.storage.StorageManager
import com.voconexus.app.core.tts.catalog.DefaultModelProvider
import com.voconexus.app.core.tts.installer.DownloadStatus
import com.voconexus.app.core.tts.installer.ModelDownloadManager
import com.voconexus.app.core.tts.installer.ModelInstaller
import com.voconexus.app.core.tts.installer.ModelStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ModelDownloadManagerTest {

    private lateinit var downloadManager: ModelDownloadManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storageManager = StorageManager(context)
        val modelStorageManager = ModelStorageManager(context)
        val modelInstaller = ModelInstaller(modelStorageManager)

        downloadManager = ModelDownloadManager(
            context = context,
            storageManager = storageManager,
            modelInstaller = modelInstaller
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDownloadProgressInitialState() {
        val progress = downloadManager.getProgress("kokoro-82m-v1.0")

        assertNotNull(progress)
        assertEquals(DownloadStatus.NOT_STARTED, progress.status)
        assertEquals(0L, progress.bytesDownloaded)
    }

    @Test
    fun testSyntheticDownloadAndReadyState() = runBlocking {
        val descriptor = DefaultModelProvider.defaultModel.copy(
            downloadSourceUrl = "file:///tmp/synthetic.zip"
        )

        downloadManager.downloadModel(descriptor)

        val progress = downloadManager.getProgress(descriptor.modelId)
        assertEquals(DownloadStatus.READY, progress.status)
        assertEquals(1.0f, progress.progressPercent, 0.01f)
    }
}
