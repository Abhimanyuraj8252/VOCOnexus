package com.voconexus.app

import com.voconexus.app.core.speech.model.PronunciationEntry
import com.voconexus.app.core.speech.model.SpeechInstructionType
import com.voconexus.app.core.speech.plan.SpeechPlanBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import kotlinx.coroutines.test.runTest

class SpeechPlanTest {

    private val builder = SpeechPlanBuilder()

    @Test
    fun testSpeechPlanConstructionAndPauseParsing() = runTest {
        val text = "Hello world [Pause: 500ms] take a deep breath."
        val rules = listOf(PronunciationEntry("1", "world", "earth"))

        val plan = builder.buildSpeechPlan(text, rules)

        assertEquals("Hello earth take a deep breath.", plan.normalizedText)
        assertEquals(1, plan.instructions.size)
        assertEquals(SpeechInstructionType.PAUSE, plan.instructions[0].type)
        assertEquals("500", plan.instructions[0].value)
        assertNotNull(plan.speechPlanHash)
        assertTrue(plan.speechPlanHash.isNotBlank())
    }
}
