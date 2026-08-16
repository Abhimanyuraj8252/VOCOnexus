package com.voconexus.app.core.generation

sealed class GenerationFailure(
    val errorCode: String,
    val message: String,
    val isRetryable: Boolean
) {
    class TransientStorageError(msg: String) : GenerationFailure("ERR_STORAGE_TRANSIENT", msg, true)
    class PermanentConfigurationError(msg: String) : GenerationFailure("ERR_CONFIG_PERMANENT", msg, false)
    class InsufficientStorageError(msg: String) : GenerationFailure("ERR_STORAGE_FULL", msg, false)
    class InsufficientMemoryError(msg: String) : GenerationFailure("ERR_OOM", msg, false)
    class NativeInferenceError(msg: String, retryable: Boolean = true) : GenerationFailure("ERR_NATIVE_INFERENCE", msg, retryable)
    class StalePlanError(msg: String) : GenerationFailure("ERR_STALE_PLAN", msg, false)
}
