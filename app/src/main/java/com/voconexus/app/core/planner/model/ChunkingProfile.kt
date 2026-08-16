package com.voconexus.app.core.planner.model

enum class SentenceSplitPolicy {
    PRESERVE_SENTENCE_STRICT,
    CLAUSE_THEN_WHITESPACE,
    WHITESPACE_ONLY
}

data class ChunkingProfile(
    val profileId: String = "kokoro-default",
    val preferredTokenCount: Int = 120,
    val softMaxTokenCount: Int = 180,
    val hardMaxTokenCount: Int = 250,
    val preferredDurationMs: Long = 12000L,
    val softMaxDurationMs: Long = 20000L,
    val hardMaxDurationMs: Long = 30000L,
    val preserveParagraphs: Boolean = true,
    val preserveDialogue: Boolean = true,
    val allowSentenceSplitting: Boolean = false
)

data class ChunkingConfig(
    val profile: ChunkingProfile = ChunkingProfile(),
    val targetPartDurationMs: Long = 600000L, // 10 minutes per Part (5-15 min target)
    val sentenceSplitPolicy: SentenceSplitPolicy = SentenceSplitPolicy.CLAUSE_THEN_WHITESPACE
)
