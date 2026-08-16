package com.voconexus.app.core.tts

sealed class TtsEngineException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ModelNotFoundException(modelId: String) : TtsEngineException("Model not found or not installed: $modelId")
    class ModelCorruptedException(modelId: String, reason: String) : TtsEngineException("Model corrupted ($modelId): $reason")
    class VoiceNotFoundException(voiceId: String) : TtsEngineException("Voice not found: $voiceId")
    class UnsupportedLanguageException(language: String, modelId: String) : TtsEngineException("Language '$language' not supported by model '$modelId'")
    class OutOfMemoryTtsException(message: String) : TtsEngineException("Native TTS out of memory: $message")
    class NativeRuntimeTtsException(message: String, cause: Throwable? = null) : TtsEngineException("Native TTS runtime error: $message", cause)
    class CancelledTtsException : TtsEngineException("TTS synthesis was cancelled by caller")
    class StorageFullException(requiredBytes: Long, availableBytes: Long) : TtsEngineException("Insufficient storage: required $requiredBytes bytes, available $availableBytes bytes")
    class PathTraversalDetectedException(path: String) : TtsEngineException("Path traversal vulnerability detected in archive entry: $path")
    class EngineNotReadyException(reason: String) : TtsEngineException("Engine not ready: $reason")
    class RateLimitException(message: String) : TtsEngineException("Rate limit exceeded: $message")
}
