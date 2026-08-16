package com.voconexus.app.core.tts.installer

import com.voconexus.app.core.engine.GenerationFingerprint
import com.voconexus.app.core.tts.TtsEngineException
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.core.tts.security.SecuritySanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

class ModelInstaller(
    private val storageManager: ModelStorageManager
) {

    suspend fun downloadAndInstallModel(
        model: TtsModel,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val downloadId = model.id + "_" + System.currentTimeMillis()
        val tempDir = storageManager.getTempDownloadDirectory(downloadId)
        val tempFile = File(tempDir, if (model.downloadUrl.endsWith(".tar.bz2")) "model_archive.tar.bz2" else "model_archive.zip")
        val extractDir = File(tempDir, "extracted").also { it.mkdirs() }

        try {
            var downloadSuccess = false
            if (model.downloadUrl.startsWith("http")) {
                try {
                    downloadFileHttp(model.downloadUrl, tempFile) { dlProgress ->
                        onProgress(dlProgress * 0.7f)
                    }
                    downloadSuccess = tempFile.exists() && tempFile.length() > 1024
                } catch (e: Exception) {
                    e.printStackTrace()
                    downloadSuccess = false
                }
            }

            if (downloadSuccess && tempFile.exists()) {
                onProgress(0.75f)
                if (isZipFile(tempFile)) {
                    try {
                        safeExtractZip(tempFile, extractDir)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw Exception("Failed to extract ZIP archive: ${e.message}", e)
                    }
                } else if (model.downloadUrl.endsWith(".tar.bz2") || tempFile.name.endsWith(".tar.bz2")) {
                    try {
                        safeExtractTarBz2(tempFile, extractDir)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw Exception("Failed to extract TAR.BZ2 archive: ${e.message}", e)
                    }
                } else {
                    throw Exception("Unrecognized archive format")
                }
            } else {
                throw Exception("Failed to download model archive")
            }
            onProgress(0.95f)

            // Validate essential ONNX model assets exist in extractDir
            val hasOnnx = extractDir.walkTopDown().any { it.isFile && it.name.endsWith(".onnx") }
            if (!hasOnnx) {
                throw Exception("Invalid model package: no .onnx file found")
            }

            // Atomic move to installed location
            val installedDir = storageManager.getInstalledModelDirectory(model.id)
            installedDir.deleteRecursively()
            installedDir.mkdirs()

            // If extracted folder has a root subdirectory (like kokoro-en-v0_19), copy its contents
            val subFolders = extractDir.listFiles()?.filter { it.isDirectory }
            val sourceDir = if (subFolders?.size == 1 && extractDir.listFiles()?.size == 1) {
                subFolders[0]
            } else {
                extractDir
            }

            sourceDir.copyRecursively(installedDir, overwrite = true)

            return@withContext installedDir
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            throw e
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun safeExtractZip(zipFile: File, destDir: File) = withContext(Dispatchers.IO) {
        var totalExtractedBytes = 0L
        ZipInputStream(java.io.BufferedInputStream(FileInputStream(zipFile), 131072)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val targetFile = SecuritySanitizer.validateZipEntryPath(entry.name, destDir)

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { out ->
                        val buffer = ByteArray(131072)
                        var read: Int
                        while (zipIn.read(buffer).also { read = it } != -1) {
                            SecuritySanitizer.checkDecompressedBounds(totalExtractedBytes, read.toLong())
                            out.write(buffer, 0, read)
                            totalExtractedBytes += read
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    suspend fun safeExtractTarBz2(tarBz2File: File, destDir: File) = withContext(Dispatchers.IO) {
        var totalExtractedBytes = 0L
        TarArchiveInputStream(BZip2CompressorInputStream(java.io.BufferedInputStream(FileInputStream(tarBz2File), 131072))).use { tarIn ->
            var entry = tarIn.nextEntry
            while (entry != null) {
                val targetFile = SecuritySanitizer.validateZipEntryPath(entry.name, destDir)
                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { out ->
                        val buffer = ByteArray(131072)
                        var read: Int
                        while (tarIn.read(buffer).also { read = it } != -1) {
                            SecuritySanitizer.checkDecompressedBounds(totalExtractedBytes, read.toLong())
                            out.write(buffer, 0, read)
                            totalExtractedBytes += read
                        }
                    }
                }
                entry = tarIn.nextEntry
            }
        }
    }

    private fun downloadFileHttp(urlStr: String, destFile: File, onProgress: (Float) -> Unit) {
        var currentUrl = urlStr
        var redirects = 0
        var connection: HttpURLConnection
        
        while (true) {
            val url = URL(currentUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:100.0)")
            connection.connect()

            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                status == HttpURLConnection.HTTP_MOVED_PERM || 
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                if (newUrl != null && redirects < 8) {
                    currentUrl = if (newUrl.startsWith("http")) newUrl else URL(url, newUrl).toString()
                    redirects++
                    continue
                }
            }
            break
        }

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("HTTP Error: ${connection.responseCode} ${connection.responseMessage}")
        }

        val totalLength = connection.contentLengthLong.coerceAtLeast(1L)
        var downloadedBytes = 0L

        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(32768)
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    onProgress((downloadedBytes.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f))
                    bytesRead = input.read(buffer)
                }
            }
        }
        connection.disconnect()
    }


    fun computeSha256(file: File): String {
        return FileInputStream(file).use { input ->
            GenerationFingerprint.sha256Stream(input)
        }
    }

    private fun isZipFile(file: File): Boolean {
        if (file.length() < 4) return false
        FileInputStream(file).use { input ->
            val b1 = input.read()
            val b2 = input.read()
            val b3 = input.read()
            val b4 = input.read()
            return b1 == 0x50 && b2 == 0x4B && (b3 == 0x03 || b3 == 0x05 || b3 == 0x07) && (b4 == 0x04 || b4 == 0x06 || b4 == 0x08)
        }
    }
}
