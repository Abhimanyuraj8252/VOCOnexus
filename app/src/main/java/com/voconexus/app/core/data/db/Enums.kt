package com.voconexus.app.core.data.db

enum class ChunkStatus {
    PENDING,
    QUEUED,
    GENERATING,
    VALIDATING,
    COMPLETED,
    FAILED,
    CANCELLED,
    SKIPPED,
    NEEDS_REGENERATION
}

enum class GenerationJobStatus {
    PENDING,
    QUEUED,
    STARTING,
    RUNNING,
    PAUSE_REQUESTED,
    PAUSED,
    STOP_REQUESTED,
    STOPPED,
    CANCEL_REQUESTED,
    CANCELLED,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED
}

enum class JobType {
    GENERATE_SELECTED,
    REGENERATE_SELECTED,
    RESUME_INCOMPLETE,
    RETRY_FAILED
}

enum class TtsEngineType {
    KOKORO,
    PIPER,
    SYSTEM_TTS,
    CUSTOM
}

enum class AudioFormat {
    WAV,
    MP3,
    FLAC,
    OGG
}
