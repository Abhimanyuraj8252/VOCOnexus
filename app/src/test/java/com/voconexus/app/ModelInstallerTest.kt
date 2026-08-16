package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.tts.ModelLicenseInfo
import com.voconexus.app.core.tts.ModelStatus
import com.voconexus.app.core.tts.TtsEngineException
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.core.tts.installer.ModelInstaller
import com.voconexus.app.core.tts.installer.ModelStorageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ModelInstallerTest {

    private lateinit var storageManager: ModelStorageManager
    private lateinit var installer: ModelInstaller

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        storageManager = ModelStorageManager(context)
        installer = ModelInstaller(storageManager)
    }

    @After
    fun tearDown() {
        storageManager.clearTempDownloads()
    }

    @Test
    fun testSyntheticModelDownloadAndInstallation() {
        runBlocking {
            val model = TtsModel(
                id = "test-model-1",
                engineId = "kokoro-82m",
                name = "Test Model",
                version = "1.0.0",
                sizeBytes = 1000L,
                downloadUrl = "synthetic",
                checksumSha256 = "",
                status = ModelStatus.NOT_INSTALLED,
                license = ModelLicenseInfo("MIT", "https://license.com")
            )

            val installedDir = installer.downloadAndInstallModel(model)
            assertNotNull(installedDir)
            assertTrue(installedDir.exists())
            assertTrue(storageManager.isModelInstalled(model.id))

            val modelFile = File(installedDir, "model.onnx")
            assertTrue(modelFile.exists())

            storageManager.deleteModelDirectory(model.id)
        }
    }

    @Test(expected = TtsEngineException.PathTraversalDetectedException::class)
    fun testZipSlipPathTraversalDetection() {
        runBlocking {
            val tempZip = File(storageManager.tempDownloadsDir, "malicious.zip")
            ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
                val entry = ZipEntry("../../evil.txt")
                zos.putNextEntry(entry)
                zos.write("evil data".toByteArray())
                zos.closeEntry()
            }

            val extractDir = File(storageManager.tempDownloadsDir, "extract_test")
            extractDir.mkdirs()

            installer.safeExtractZip(tempZip, extractDir)
        }
    }
}
