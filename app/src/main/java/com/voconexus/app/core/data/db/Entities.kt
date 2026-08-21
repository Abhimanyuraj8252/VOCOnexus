package com.voconexus.app.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val estimatedDurationMs: Long = 0L,
    val actualDurationMs: Long = 0L,
    val partCount: Int = 0,
    val chunkCount: Int = 0,
    val speed: Float = 1.0f,
    val pitch: Float = 0.0f,
    val targetDurationMs: Long = 0L,
    val durationMode: String = "OFF"
)

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val rawText: String,
    val textHash: String,
    val partCount: Int,
    val wordCount: Int,
    val characterCount: Int,
    val createdAt: Long,
    val planStatus: String = "NOT_ANALYZED",
    val planVersion: String = "",
    val chunkCount: Int = 0
)

@Entity(
    tableName = "parts",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["documentId"])]
)
data class PartEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val documentId: String,
    val title: String,
    val sequenceIndex: Int,
    val chunkCount: Int,
    val wordCount: Int,
    val characterCount: Int
)

@Entity(
    tableName = "chunks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["documentId"]),
        Index(value = ["partId"]),
        Index(value = ["status"]),
        Index(value = ["sequenceIndex"]),
        Index(value = ["normalizedTextHash"]),
        Index(value = ["partId", "sequenceIndex"], unique = true)
    ]
)
data class ChunkEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val documentId: String,
    val partId: String,
    val sequenceIndex: Int,
    val sourceText: String,
    val sourceTextHash: String,
    val normalizedText: String,
    val normalizedTextHash: String,
    val engineId: String,
    val modelId: String,
    val voiceId: String,
    val language: String = "en-US",
    val locale: String = "en_US",
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val audioFormat: AudioFormat = AudioFormat.WAV,
    val sampleRate: Int = 24000,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val audioPath: String? = null,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val attemptCount: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val paragraphIndex: Int = 0,
    val sentenceIndex: Int = 0,
    val estimatedTokens: Int = 0,
    val generationFingerprint: String = "",
    val checksumSha256: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
)

@Entity(
    tableName = "generation_jobs",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class GenerationJobEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val documentId: String = "",
    val jobType: String = JobType.GENERATE_SELECTED.name,
    val status: GenerationJobStatus = GenerationJobStatus.QUEUED,
    val totalChunks: Int = 0,
    val completedChunks: Int = 0,
    val failedChunks: Int = 0,
    val activeChunkId: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val pausedAt: Long? = null,
    val stoppedAt: Long? = null,
    val errorMessage: String? = null,
    val generationFingerprint: String = "",
    val engineId: String = "kokoro-82m",
    val modelId: String = "kokoro-82m-v1.0",
    val voiceId: String = "af_heart",
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f
)

@Entity(
    tableName = "audio_assets",
    foreignKeys = [
        ForeignKey(
            entity = ChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chunkId"], unique = true)]
)
data class AudioAssetEntity(
    @PrimaryKey val id: String,
    val chunkId: String,
    val filePath: String,
    val fileFormat: String,
    val mimeType: String,
    val sampleRate: Int,
    val channels: Int,
    val bitrate: Int,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val checksum: String,
    val createdAt: Long
)

@Entity(
    tableName = "export_jobs",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class ExportJobEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val requestedChunkIds: String,
    val outputPath: String,
    val outputFormat: String,
    val bitrate: Int,
    val sampleRate: Int,
    val status: String,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null
)

object ModelStatus {
    const val NOT_INSTALLED = "NOT_INSTALLED"
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val PAUSED = "PAUSED"
    const val VERIFYING = "VERIFYING"
    const val INSTALLING = "INSTALLING"
    const val INSTALLED = "INSTALLED"
    const val FAILED = "FAILED"
    const val UPDATE_AVAILABLE = "UPDATE_AVAILABLE"
    const val CORRUPTED = "CORRUPTED"
}

@Entity(tableName = "tts_models")
data class TtsModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val engineId: String,
    val version: String,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
    val sizeBytes: Long = 0L,
    val downloadUrl: String = "",
    val checksumSha256: String = "",
    val status: String = ModelStatus.NOT_INSTALLED,
    val languagesJson: String = "[\"en\"]",
    val voicesCount: Int = 1,
    val licenseName: String = "Apache-2.0",
    val licenseUrl: String = "https://www.apache.org/licenses/LICENSE-2.0",
    val minRamMb: Int = 2048,
    val downloadProgress: Float = 0f,
    val errorMessage: String = "",
    val providerId: String = "",
    val category: String = "LOCAL",
    val baseUrl: String? = null,
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tts_voices",
    foreignKeys = [
        ForeignKey(
            entity = TtsModelEntity::class,
            parentColumns = ["id"],
            childColumns = ["modelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["modelId"]),
        Index(value = ["language"]),
        Index(value = ["engineId"])
    ]
)
data class TtsVoiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val modelId: String,
    val engineId: String = "kokoro-82m",
    val gender: String = "UNKNOWN",
    val language: String = "en",
    val locale: String = "en-US",
    val sampleUrl: String? = null,
    val sampleRate: Int = 24000,
    val isDefault: Boolean = false,
    val previewSamplePath: String = ""
)

@Entity(
    tableName = "voice_segments",
    foreignKeys = [
        ForeignKey(
            entity = ChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chunkId"]),
        Index(value = ["languageCode"]),
        Index(value = ["speakerId"])
    ]
)
data class VoiceSegmentEntity(
    @PrimaryKey val id: String,
    val chunkId: String,
    val sequenceIndex: Int,
    val text: String,
    val languageCode: String = "en",
    val speakerId: String = "Narrator",
    val voiceId: String = "af_heart",
    val engineId: String = "kokoro-82m",
    val modelId: String = "kokoro-82m-v1.0",
    val status: String = "PENDING",
    val audioPath: String? = null,
    val durationMs: Long = 0L
)

@Entity(
    tableName = "speaker_mappings",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["projectId", "speakerId"], unique = true)
    ]
)
data class SpeakerMappingEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val speakerId: String,
    val voiceId: String,
    val engineId: String = "kokoro-82m",
    val modelId: String = "kokoro-82m-v1.0"
)

@Entity(
    tableName = "pronunciation_rules",
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["scope"]),
        Index(value = ["languageCode"])
    ]
)
data class PronunciationRuleEntity(
    @PrimaryKey val id: String,
    val projectId: String? = null,
    val matchText: String,
    val replacement: String,
    val languageCode: String = "en",
    val scope: String = "PROJECT",
    val voiceId: String? = null,
    val isCaseSensitive: Boolean = false,
    val isWholeWord: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "benchmark_results",
    indices = [
        Index(value = ["modelId"]),
        Index(value = ["engineId"]),
        Index(value = ["voiceId"])
    ]
)
data class BenchmarkEntity(
    @PrimaryKey val id: String,
    val engineId: String,
    val modelId: String,
    val voiceId: String,
    val coldStartMs: Long,
    val synthesisMs: Long,
    val audioDurationMs: Long,
    val realTimeFactor: Float,
    val peakMemoryMb: Int,
    val isStale: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "download_tasks",
    indices = [Index(value = ["modelId"])]
)
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val modelId: String,
    val version: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: String,
    val userPaused: Boolean = false,
    val sourceUrl: String,
    val localStagingPath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)


