package com.voconexus.app.core.error

enum class ErrorCode {
    TTS_MODEL_LOAD_FAILED,
    STORAGE_SPACE_LOW,
    CHUNK_GENERATION_FAILED,
    AUDIO_FILE_UNAVAILABLE,
    PERMISSION_DENIED,
    ENGINE_RUNTIME_ERROR,
    DOWNLOAD_FAILED,
    SECURITY_ZIP_SLIP,
    UNKNOWN_ERROR
}

data class VocoNexusError(
    val errorCode: ErrorCode,
    val title: String,
    val userMessage: String,
    val actionableAdvice: String,
    val technicalDetails: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun modelLoadFailed(modelId: String, cause: String): VocoNexusError {
            return VocoNexusError(
                errorCode = ErrorCode.TTS_MODEL_LOAD_FAILED,
                title = "Model Loading Failed",
                userMessage = "The TTS engine could not load model '$modelId'.",
                actionableAdvice = "Please check if the model is downloaded properly or redownload it from Model Manager.",
                technicalDetails = "ModelId: $modelId | Cause: $cause"
            )
        }

        fun storageSpaceLow(requiredMb: Long, availableMb: Long): VocoNexusError {
            return VocoNexusError(
                errorCode = ErrorCode.STORAGE_SPACE_LOW,
                title = "Insufficient Storage Space",
                userMessage = "Not enough disk space to perform operation (Required: ${requiredMb}MB, Available: ${availableMb}MB).",
                actionableAdvice = "Free up device storage or clean up temporary files in Settings.",
                technicalDetails = "RequiredMB: $requiredMb | AvailableMB: $availableMb"
            )
        }

        fun chunkGenerationFailed(chunkId: String, reason: String): VocoNexusError {
            return VocoNexusError(
                errorCode = ErrorCode.CHUNK_GENERATION_FAILED,
                title = "Chunk Audio Generation Failed",
                userMessage = "Speech synthesis for this chunk could not be completed.",
                actionableAdvice = "Tap Retry to attempt generating this chunk again.",
                technicalDetails = "ChunkId: $chunkId | Reason: $reason"
            )
        }

        fun audioFileUnavailable(path: String): VocoNexusError {
            return VocoNexusError(
                errorCode = ErrorCode.AUDIO_FILE_UNAVAILABLE,
                title = "Audio File Unavailable",
                userMessage = "The requested audio file could not be found on storage.",
                actionableAdvice = "The audio file may have been moved or cleaned up externally. You can regenerate it from the project screen.",
                technicalDetails = "Path: $path"
            )
        }
    }
}
