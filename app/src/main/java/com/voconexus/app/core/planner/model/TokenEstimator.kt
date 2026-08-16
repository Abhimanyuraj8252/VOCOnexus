package com.voconexus.app.core.planner.model

interface TokenEstimator {
    fun estimateTokens(text: String): Int
}

class HeuristicTokenEstimator : TokenEstimator {

    override fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0

        var totalTokens = 0.0
        var hasWords = false

        Regex("""[\p{L}\p{N}]+""").findAll(text).forEach { match ->
            hasWords = true
            val word = match.value


            val isDevanagari = word.any { it in '\u0900'..'\u097F' }
            if (isDevanagari) {
                // Devanagari words typically tokenize into 1.5-2.0 tokens per word
                totalTokens += (word.length * 0.75).coerceAtLeast(1.2)
            } else {
                // English words average ~1.3 tokens per word (short words 1, long words 2-3)
                val wordTokens = when {
                    word.length <= 4 -> 1.0
                    word.length <= 8 -> 1.3
                    word.length <= 12 -> 1.8
                    else -> (word.length / 4.0)
                }
                totalTokens += wordTokens
            }
        }

        if (!hasWords) return 1

        // Punctuation overhead
        val punctuationCount = text.count { it in ".,!?;:—-\" '()[]" }
        totalTokens += punctuationCount * 0.5

        return totalTokens.toInt().coerceAtLeast(1)
    }
}
