package com.voconexus.app.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.voconexus.app.core.data.dao.AudioAssetDao
import com.voconexus.app.core.data.dao.ChunkDao
import com.voconexus.app.core.data.dao.DocumentDao
import com.voconexus.app.core.data.dao.ExportJobDao
import com.voconexus.app.core.data.dao.GenerationJobDao
import com.voconexus.app.core.data.dao.PartDao
import com.voconexus.app.core.data.dao.ProjectDao
import com.voconexus.app.core.data.dao.TtsModelDao
import com.voconexus.app.core.data.dao.TtsVoiceDao

@Database(
    entities = [
        ProjectEntity::class,
        DocumentEntity::class,
        PartEntity::class,
        ChunkEntity::class,
        GenerationJobEntity::class,
        AudioAssetEntity::class,
        ExportJobEntity::class,
        TtsModelEntity::class,
        TtsVoiceEntity::class,
        VoiceSegmentEntity::class,
        SpeakerMappingEntity::class,
        PronunciationRuleEntity::class,
        BenchmarkEntity::class,
        DownloadTaskEntity::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VocoNexusDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun documentDao(): DocumentDao
    abstract fun partDao(): PartDao
    abstract fun chunkDao(): ChunkDao
    abstract fun generationJobDao(): GenerationJobDao
    abstract fun audioAssetDao(): AudioAssetDao
    abstract fun exportJobDao(): ExportJobDao
    abstract fun ttsModelDao(): TtsModelDao
    abstract fun ttsVoiceDao(): TtsVoiceDao
    abstract fun voiceSegmentDao(): com.voconexus.app.core.data.dao.VoiceSegmentDao
    abstract fun speakerMappingDao(): com.voconexus.app.core.data.dao.SpeakerMappingDao
    abstract fun pronunciationRuleDao(): com.voconexus.app.core.data.dao.PronunciationRuleDao
    abstract fun benchmarkDao(): com.voconexus.app.core.data.dao.BenchmarkDao
    abstract fun downloadTaskDao(): com.voconexus.app.core.data.dao.DownloadTaskDao

    companion object {
        @Volatile
        private var INSTANCE: VocoNexusDatabase? = null

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tts_models ADD COLUMN providerId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN category TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN baseUrl TEXT")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `download_tasks` (
                        `id` TEXT NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `version` TEXT NOT NULL,
                        `bytesDownloaded` INTEGER NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `userPaused` INTEGER NOT NULL DEFAULT 0,
                        `sourceUrl` TEXT NOT NULL,
                        `localStagingPath` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_tasks_modelId` ON `download_tasks` (`modelId`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `benchmark_results` (
                        `id` TEXT NOT NULL,
                        `engineId` TEXT NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `voiceId` TEXT NOT NULL,
                        `coldStartMs` INTEGER NOT NULL,
                        `synthesisMs` INTEGER NOT NULL,
                        `audioDurationMs` INTEGER NOT NULL,
                        `realTimeFactor` REAL NOT NULL,
                        `peakMemoryMb` INTEGER NOT NULL,
                        `isStale` INTEGER NOT NULL DEFAULT 0,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_benchmark_results_modelId` ON `benchmark_results` (`modelId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_benchmark_results_engineId` ON `benchmark_results` (`engineId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_benchmark_results_voiceId` ON `benchmark_results` (`voiceId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pronunciation_rules` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT,
                        `matchText` TEXT NOT NULL,
                        `replacement` TEXT NOT NULL,
                        `languageCode` TEXT NOT NULL DEFAULT 'en',
                        `scope` TEXT NOT NULL DEFAULT 'PROJECT',
                        `voiceId` TEXT,
                        `isCaseSensitive` INTEGER NOT NULL DEFAULT 0,
                        `isWholeWord` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pronunciation_rules_projectId` ON `pronunciation_rules` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pronunciation_rules_scope` ON `pronunciation_rules` (`scope`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pronunciation_rules_languageCode` ON `pronunciation_rules` (`languageCode`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `voice_segments` (
                        `id` TEXT NOT NULL,
                        `chunkId` TEXT NOT NULL,
                        `sequenceIndex` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `languageCode` TEXT NOT NULL DEFAULT 'en',
                        `speakerId` TEXT NOT NULL DEFAULT 'Narrator',
                        `voiceId` TEXT NOT NULL DEFAULT 'af_heart',
                        `engineId` TEXT NOT NULL DEFAULT 'kokoro-82m',
                        `modelId` TEXT NOT NULL DEFAULT 'kokoro-82m-v1.0',
                        `status` TEXT NOT NULL DEFAULT 'PENDING',
                        `audioPath` TEXT,
                        `durationMs` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chunkId`) REFERENCES `chunks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_segments_chunkId` ON `voice_segments` (`chunkId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_segments_languageCode` ON `voice_segments` (`languageCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_segments_speakerId` ON `voice_segments` (`speakerId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `speaker_mappings` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `speakerId` TEXT NOT NULL,
                        `voiceId` TEXT NOT NULL,
                        `engineId` TEXT NOT NULL DEFAULT 'kokoro-82m',
                        `modelId` TEXT NOT NULL DEFAULT 'kokoro-82m-v1.0',
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_speaker_mappings_projectId` ON `speaker_mappings` (`projectId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_speaker_mappings_projectId_speakerId` ON `speaker_mappings` (`projectId`, `speakerId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN speed REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE projects ADD COLUMN pitch REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE projects ADD COLUMN targetDurationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN durationMode TEXT NOT NULL DEFAULT 'OFF'")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audio_assets` (
                        `id` TEXT NOT NULL,
                        `chunkId` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `fileFormat` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sampleRate` INTEGER NOT NULL,
                        `channels` INTEGER NOT NULL,
                        `bitrate` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `checksum` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chunkId`) REFERENCES `chunks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_assets_chunkId` ON `audio_assets` (`chunkId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `export_jobs` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `requestedChunkIds` TEXT NOT NULL,
                        `outputPath` TEXT NOT NULL,
                        `outputFormat` TEXT NOT NULL,
                        `bitrate` INTEGER NOT NULL,
                        `sampleRate` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `startedAt` INTEGER,
                        `completedAt` INTEGER,
                        `errorCode` TEXT,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_export_jobs_projectId` ON `export_jobs` (`projectId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chunks ADD COLUMN startOffset INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chunks ADD COLUMN endOffset INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chunks ADD COLUMN paragraphIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chunks ADD COLUMN sentenceIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chunks ADD COLUMN estimatedTokens INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE documents ADD COLUMN planStatus TEXT NOT NULL DEFAULT 'NOT_ANALYZED'")
                db.execSQL("ALTER TABLE documents ADD COLUMN planVersion TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documents ADD COLUMN chunkCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tts_models ADD COLUMN downloadUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN checksumSha256 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN status TEXT NOT NULL DEFAULT 'NOT_INSTALLED'")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN languagesJson TEXT NOT NULL DEFAULT '[\"en\"]'")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN voicesCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN licenseName TEXT NOT NULL DEFAULT 'Apache-2.0'")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN licenseUrl TEXT NOT NULL DEFAULT 'https://www.apache.org/licenses/LICENSE-2.0'")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN minRamMb INTEGER NOT NULL DEFAULT 2048")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN downloadProgress REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN errorMessage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tts_models ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE tts_voices ADD COLUMN engineId TEXT NOT NULL DEFAULT 'kokoro-82m'")
                db.execSQL("ALTER TABLE tts_voices ADD COLUMN sampleRate INTEGER NOT NULL DEFAULT 24000")
                db.execSQL("ALTER TABLE tts_voices ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tts_voices ADD COLUMN previewSamplePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tts_voices_language` ON `tts_voices` (`language`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tts_voices_engineId` ON `tts_voices` (`engineId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chunks ADD COLUMN generationFingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chunks ADD COLUMN checksumSha256 TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE generation_jobs ADD COLUMN documentId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE generation_jobs ADD COLUMN jobType TEXT NOT NULL DEFAULT 'GENERATE_SELECTED'")
                db.execSQL("ALTER TABLE generation_jobs ADD COLUMN pausedAt INTEGER")
                db.execSQL("ALTER TABLE generation_jobs ADD COLUMN stoppedAt INTEGER")
                db.execSQL("ALTER TABLE generation_jobs ADD COLUMN generationFingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE generation_jobs ADD COLUMN engineId TEXT NOT NULL DEFAULT 'kokoro-82m'")
                db.execSQL("ALTER TABLE generation_jobs ADD COLUMN modelId TEXT NOT NULL DEFAULT 'kokoro-82m-v1.0'")
                db.execSQL("ALTER TABLE generation_jobs ADD COLUMN voiceId TEXT NOT NULL DEFAULT 'af_heart'")
                db.execSQL("ALTER TABLE generation_jobs ADD COLUMN speed REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE generation_jobs ADD COLUMN pitch REAL NOT NULL DEFAULT 1.0")
            }
        }

        fun getInstance(context: Context): VocoNexusDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VocoNexusDatabase::class.java,
                    "voconexus_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemory(context: Context): VocoNexusDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                VocoNexusDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
