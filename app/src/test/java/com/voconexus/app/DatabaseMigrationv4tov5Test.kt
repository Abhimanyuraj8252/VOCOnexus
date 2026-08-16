package com.voconexus.app

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.VocoNexusDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseMigrationv4tov5Test {

    @Test
    fun testMigration4To5SchemaColumns() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `chunks` (
                                `id` TEXT NOT NULL,
                                `projectId` TEXT NOT NULL,
                                `documentId` TEXT NOT NULL,
                                `partId` TEXT NOT NULL,
                                `sequenceIndex` INTEGER NOT NULL,
                                `sourceText` TEXT NOT NULL,
                                `sourceTextHash` TEXT NOT NULL,
                                `normalizedText` TEXT NOT NULL,
                                `normalizedTextHash` TEXT NOT NULL,
                                `engineId` TEXT NOT NULL,
                                `modelId` TEXT NOT NULL,
                                `voiceId` TEXT NOT NULL,
                                `speed` REAL NOT NULL DEFAULT 1.0,
                                `pitch` REAL NOT NULL DEFAULT 1.0,
                                `status` TEXT NOT NULL,
                                `audioPath` TEXT,
                                `durationMs` INTEGER NOT NULL DEFAULT 0,
                                `fileSizeBytes` INTEGER NOT NULL DEFAULT 0,
                                `attemptCount` INTEGER NOT NULL DEFAULT 0,
                                `errorCode` TEXT,
                                `errorMessage` TEXT,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                        """.trimIndent())

                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `generation_jobs` (
                                `id` TEXT NOT NULL,
                                `projectId` TEXT NOT NULL,
                                `status` TEXT NOT NULL,
                                `totalChunks` INTEGER NOT NULL,
                                `completedChunks` INTEGER NOT NULL DEFAULT 0,
                                `failedChunks` INTEGER NOT NULL DEFAULT 0,
                                `activeChunkId` TEXT,
                                `startedAt` INTEGER NOT NULL,
                                `endedAt` INTEGER,
                                `errorMessage` TEXT,
                                PRIMARY KEY(`id`)
                            )
                        """.trimIndent())
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val openDb = helper.writableDatabase
        VocoNexusDatabase.MIGRATION_4_5.migrate(openDb)

        val chunkCursor = openDb.query("PRAGMA table_info(chunks)", emptyArray())
        val chunkColumns = mutableListOf<String>()
        while (chunkCursor.moveToNext()) {
            val nameIndex = chunkCursor.getColumnIndex("name")
            if (nameIndex >= 0) {
                chunkColumns.add(chunkCursor.getString(nameIndex))
            }
        }
        chunkCursor.close()

        assertTrue(chunkColumns.contains("generationFingerprint"))
        assertTrue(chunkColumns.contains("checksumSha256"))

        val jobCursor = openDb.query("PRAGMA table_info(generation_jobs)", emptyArray())
        val jobColumns = mutableListOf<String>()
        while (jobCursor.moveToNext()) {
            val nameIndex = jobCursor.getColumnIndex("name")
            if (nameIndex >= 0) {
                jobColumns.add(jobCursor.getString(nameIndex))
            }
        }
        jobCursor.close()

        assertTrue(jobColumns.contains("documentId"))
        assertTrue(jobColumns.contains("jobType"))

        openDb.close()
    }
}
