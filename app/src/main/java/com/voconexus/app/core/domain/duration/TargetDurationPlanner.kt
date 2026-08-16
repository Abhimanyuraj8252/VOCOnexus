package com.voconexus.app.core.domain.duration

import com.voconexus.app.core.domain.speech.NaturalnessLevel
import com.voconexus.app.core.domain.speech.calculateNaturalness

data class TargetDurationPlan(
    val requiredRatio: Float,
    val recommendedTtsSpeed: Float,
    val residualTimeStretchRatio: Float,
    val isWithinTolerance: Boolean,
    val isExtremeAdjustment: Boolean,
    val naturalnessLevel: NaturalnessLevel,
    val warningMessage: String?
)

class TargetDurationPlanner {

    fun planTargetDuration(
        estimatedDurationMs: Long,
        targetDurationMs: Long,
        currentSpeed: Float = 1.0f,
        currentPitchSemitones: Float = 0.0f,
        toleranceFraction: Float = 0.01f
    ): TargetDurationPlan {
        if (estimatedDurationMs <= 0L || targetDurationMs <= 0L) {
            return TargetDurationPlan(
                requiredRatio = 1.0f,
                recommendedTtsSpeed = currentSpeed,
                residualTimeStretchRatio = 1.0f,
                isWithinTolerance = true,
                isExtremeAdjustment = false,
                naturalnessLevel = NaturalnessLevel.HIGH,
                warningMessage = null
            )
        }

        val requiredRatio = targetDurationMs.toFloat() / estimatedDurationMs.toFloat()
        val diffFraction = Math.abs(1.0f - requiredRatio)

        if (diffFraction <= toleranceFraction) {
            return TargetDurationPlan(
                requiredRatio = requiredRatio,
                recommendedTtsSpeed = currentSpeed,
                residualTimeStretchRatio = 1.0f,
                isWithinTolerance = true,
                isExtremeAdjustment = false,
                naturalnessLevel = NaturalnessLevel.HIGH,
                warningMessage = null
            )
        }

        // Primary: Adjust TTS Speed (Target Speed = Current Speed / Required Ratio)
        val rawTtsSpeed = currentSpeed / requiredRatio
        val clampedTtsSpeed = rawTtsSpeed.coerceIn(0.75f, 1.75f)

        // Residual ratio to be handled by DSP time-stretching if TTS speed clamps
        val residualRatio = (currentSpeed / clampedTtsSpeed) / requiredRatio

        val isExtreme = requiredRatio < 0.5f || requiredRatio > 2.0f
        val naturalness = calculateNaturalness(clampedTtsSpeed, currentPitchSemitones, requiredRatio)

        val warning = when {
            isExtreme -> "This target requires an extreme speech-rate change. Audio may become unnatural."
            naturalness == NaturalnessLevel.LOW -> "Aggressive duration adjustment. Speech naturalness may decrease."
            else -> null
        }

        return TargetDurationPlan(
            requiredRatio = requiredRatio,
            recommendedTtsSpeed = clampedTtsSpeed,
            residualTimeStretchRatio = residualRatio.coerceIn(0.80f, 1.25f),
            isWithinTolerance = false,
            isExtremeAdjustment = isExtreme,
            naturalnessLevel = naturalness,
            warningMessage = warning
        )
    }

    fun computeBoundedCorrection(
        actualDurationMs: Long,
        targetDurationMs: Long,
        currentSpeed: Float,
        toleranceFraction: Float = 0.01f
    ): Float {
        if (actualDurationMs <= 0L || targetDurationMs <= 0L) return currentSpeed

        val ratio = targetDurationMs.toFloat() / actualDurationMs.toFloat()
        if (Math.abs(1.0f - ratio) <= toleranceFraction) {
            return currentSpeed
        }

        // Bound single-pass correction pass to max +/- 15% change
        val correctionFactor = ratio.coerceIn(0.85f, 1.15f)
        return (currentSpeed / correctionFactor).coerceIn(0.5f, 3.0f)
    }
}
