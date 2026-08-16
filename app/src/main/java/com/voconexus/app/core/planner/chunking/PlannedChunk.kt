package com.voconexus.app.core.planner.chunking

data class PlannedChunk(
    val sequenceIndex: Int,
    val partSequenceIndex: Int = 0,
    val sourceText: String,
    val normalizedText: String,
    val sourceTextHash: String,
    val normalizedTextHash: String,
    val startOffset: Int,
    val endOffset: Int,
    val paragraphIndex: Int,
    val sentenceIndex: Int,
    val estimatedTokenCount: Int,
    val estimatedDurationMs: Long,
    val language: String = "en-US",
    val isOversizedSplit: Boolean = false
)
