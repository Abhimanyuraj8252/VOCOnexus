package com.voconexus.app.core.domain.speech

enum class SpeechSpeedSource {
    DEFAULT,
    MANUAL,
    TARGET_DURATION
}

data class SpeechSpeedConfig(
    val value: Float = 1.0f,
    val source: SpeechSpeedSource = SpeechSpeedSource.DEFAULT
) {
    val isValid: Boolean get() = isSpeedValid(value)
    val isExtreme: Boolean get() = value < 0.75f || value > 1.75f

    val warningMessage: String?
        get() = when {
            !isValid -> "Speed must be a positive finite number"
            isExtreme -> "Extreme speed. Speech naturalness may decrease."
            else -> null
        }
}

enum class PitchSource {
    DEFAULT,
    MANUAL
}

data class PitchConfig(
    val semitones: Float = 0.0f,
    val source: PitchSource = PitchSource.DEFAULT
) {
    val isValid: Boolean get() = isPitchValid(semitones)
    val isExtreme: Boolean get() = semitones < -4.0f || semitones > 4.0f

    val warningMessage: String?
        get() = when {
            !isValid -> "Pitch must be a valid finite number"
            isExtreme -> "Extreme pitch changes may sound unnatural."
            else -> null
        }
}

data class PlaybackSpeedConfig(
    val value: Float = 1.0f
) {
    val isValid: Boolean get() = isSpeedValid(value)
}

enum class DurationMode {
    OFF,
    ESTIMATE_ONLY,
    TARGET
}

data class TargetDurationConfig(
    val durationMode: DurationMode = DurationMode.OFF,
    val targetDurationMs: Long = 0L,
    val toleranceFraction: Float = 0.01f
) {
    val isValid: Boolean get() = durationMode != DurationMode.TARGET || targetDurationMs > 0L

    fun getRequiredRatio(estimatedDurationMs: Long): Float {
        if (estimatedDurationMs <= 0L || targetDurationMs <= 0L) return 1.0f
        return targetDurationMs.toFloat() / estimatedDurationMs.toFloat()
    }
}

enum class NaturalnessLevel {
    HIGH,
    MODERATE,
    LOW
}

fun isSpeedValid(speed: Float): Boolean {
    return !speed.isNaN() && !speed.isInfinite() && speed > 0.0f
}

fun isPitchValid(semitones: Float): Boolean {
    return !semitones.isNaN() && !semitones.isInfinite()
}

fun calculateNaturalness(
    speed: Float,
    pitchSemitones: Float,
    targetRatio: Float = 1.0f
): NaturalnessLevel {
    val isSpeedNormal = speed in 0.75f..1.75f
    val isPitchNormal = pitchSemitones in -4.0f..4.0f
    val isRatioNormal = targetRatio in 0.85f..1.20f

    if (isSpeedNormal && isPitchNormal && isRatioNormal) {
        return NaturalnessLevel.HIGH
    }

    val isSpeedModerate = speed in 0.5f..2.5f
    val isPitchModerate = pitchSemitones in -8.0f..8.0f
    val isRatioModerate = targetRatio in 0.70f..1.40f

    if (isSpeedModerate && isPitchModerate && isRatioModerate) {
        return NaturalnessLevel.MODERATE
    }

    return NaturalnessLevel.LOW
}
