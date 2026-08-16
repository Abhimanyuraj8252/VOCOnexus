package com.voconexus.app

import com.voconexus.app.core.speech.model.PronunciationEntry
import com.voconexus.app.core.speech.model.PronunciationScope
import com.voconexus.app.core.speech.pronunciation.PronunciationEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class PronunciationEngineTest {

    private val engine = PronunciationEngine()

    @Test
    fun testWholeWordPronunciationReplacement() {
        val text = "AI is used by sailors."
        val rules = listOf(
            PronunciationEntry("1", "AI", "A I", scope = PronunciationScope.GLOBAL, isWholeWord = true)
        )

        val result = engine.applyPronunciationRules(text, rules)

        assertEquals("A I is used by sailors.", result)
    }

    @Test
    fun testLongestPhraseMatchingPrecedence() {
        val text = "Welcome to New York City."
        val rules = listOf(
            PronunciationEntry("1", "New York", "New York State", scope = PronunciationScope.PROJECT),
            PronunciationEntry("2", "New York City", "NYC", scope = PronunciationScope.PROJECT)
        )

        val result = engine.applyPronunciationRules(text, rules)

        assertEquals("Welcome to NYC.", result)
    }

    @Test
    fun testScopePrecedence() {
        val text = "ISRO launched."
        val globalRule = PronunciationEntry("1", "ISRO", "I-S-R-O", scope = PronunciationScope.GLOBAL)
        val projectRule = PronunciationEntry("2", "ISRO", "Indian Space Agency", scope = PronunciationScope.PROJECT)

        val result = engine.applyPronunciationRules(text, listOf(globalRule, projectRule))

        assertEquals("Indian Space Agency launched.", result)
    }
}
