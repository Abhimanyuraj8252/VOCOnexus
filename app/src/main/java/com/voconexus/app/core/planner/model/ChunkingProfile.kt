package com.voconexus.app.core.planner.model

enum class SentenceSplitPolicy {
    PRESERVE_SENTENCE_STRICT,
    CLAUSE_THEN_WHITESPACE,
    WHITESPACE_ONLY
}

data class ChunkingProfile(
    val profileId: String = "kokoro-default",
    // Target ~500 characters per chunk (≈100 tokens at ~5 chars/token).
    // The planner will never split a sentence mid-way; it completes the current
    // sentence before closing a chunk — even if that pushes slightly over preferred.
    val preferredTokenCount: Int = 100,   // ~500 chars
    val softMaxTokenCount: Int = 150,     // ~750 chars — finalize chunk here
    val hardMaxTokenCount: Int = 200,     // ~1000 chars — oversized split fallback
    val preferredDurationMs: Long = 10000L,
    val softMaxDurationMs: Long = 16000L,
    val hardMaxDurationMs: Long = 24000L,
    val preserveParagraphs: Boolean = true,
    val preserveDialogue: Boolean = true,
    val allowSentenceSplitting: Boolean = false  // Never split mid-sentence
)

data class ChunkingConfig(
    val profile: ChunkingProfile = ChunkingProfile(),
    val targetPartCharCount: Int = 1000, // ~1,000 characters per Part (sentence boundary aligned)
    val targetPartDurationMs: Long = 60000L, // ~1 minute per Part
    val sentenceSplitPolicy: SentenceSplitPolicy = SentenceSplitPolicy.CLAUSE_THEN_WHITESPACE
)
