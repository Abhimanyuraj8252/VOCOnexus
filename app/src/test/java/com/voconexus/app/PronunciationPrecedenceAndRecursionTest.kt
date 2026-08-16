package com.voconexus.app

import com.voconexus.app.core.speech.model.PronunciationEntry
import com.voconexus.app.core.speech.model.PronunciationScope
import com.voconexus.app.core.speech.pronunciation.PronunciationEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class PronunciationPrecedenceAndRecursionTest {

    private val engine = PronunciationEngine()

    @Test
    fun testRulePrecedenceDocumentOverProjectOverGlobal() {
        val globalRule = PronunciationEntry(
            id = "g1",
            matchText = "AI",
            replacement = "A-I",
            scope = PronunciationScope.GLOBAL
        )
        val projectRule = PronunciationEntry(
            id = "p1",
            matchText = "AI",
            replacement = "Artificial Intelligence",
            scope = PronunciationScope.PROJECT
        )
        val documentRule = PronunciationEntry(
            id = "d1",
            matchText = "AI",
            replacement = "Aye Eye",
            scope = PronunciationScope.DOCUMENT
        )

        val rules = listOf(globalRule, projectRule, documentRule)
        val result = engine.applyPronunciationRules("The AI project is active.", rules)

        assertEquals("The Aye Eye project is active.", result)
    }

    @Test
    fun testCircularRecursionTerminationSafety() {
        val rule1 = PronunciationEntry(id = "r1", matchText = "Alpha", replacement = "Beta", scope = PronunciationScope.DOCUMENT)
        val rule2 = PronunciationEntry(id = "r2", matchText = "Beta", replacement = "Alpha", scope = PronunciationScope.DOCUMENT)

        val rules = listOf(rule1, rule2)
        // Must complete without StackOverflowError or infinite loop
        val result = engine.applyPronunciationRules("Welcome to Alpha", rules)
        assertEquals("Welcome to Alpha", result)
    }
}
