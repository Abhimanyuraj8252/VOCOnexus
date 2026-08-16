package com.voconexus.app

import android.content.Context
import android.content.ContextWrapper
import java.io.File

class SimpleTestContext : ContextWrapper(null) {
    private val testDir by lazy {
        File(System.getProperty("java.io.tmpdir"), "voconexus_test_${System.currentTimeMillis()}").apply {
            if (!exists()) mkdirs()
        }
    }
    private val dbDir by lazy {
        File(testDir, "databases").apply { if (!exists()) mkdirs() }
    }

    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = testDir
    override fun getCacheDir(): File = testDir
    override fun getNoBackupFilesDir(): File = testDir
    override fun getDatabasePath(name: String): File = File(dbDir, name)
    override fun getSystemService(name: String): Any? = null
}

object TestContextUtil {
    fun createMockContext(): Context = SimpleTestContext()
}
