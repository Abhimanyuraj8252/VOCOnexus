package com.voconexus.app.core.planner.segmentation

data class Sentence(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val paragraphIndex: Int,
    val sentenceIndex: Int,
    val estimatedTokenCount: Int = 0,
    val estimatedDurationMs: Long = 0L,
    val language: String = "en-US"
)
