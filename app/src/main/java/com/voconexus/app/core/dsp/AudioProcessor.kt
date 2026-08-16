package com.voconexus.app.core.dsp

import java.io.File

data class DspParameters(
    val timeStretchRatio: Float = 1.0f,
    val pitchShiftSemitones: Float = 0.0f
)

interface AudioProcessor {
    suspend fun process(
        sourceFile: File,
        outputFile: File,
        parameters: DspParameters,
        onProgress: (Float) -> Unit = {}
    ): Boolean
}
