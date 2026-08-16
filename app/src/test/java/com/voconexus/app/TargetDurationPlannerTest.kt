package com.voconexus.app

import com.voconexus.app.core.domain.duration.TargetDurationPlanner
import com.voconexus.app.core.domain.speech.NaturalnessLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetDurationPlannerTest {

    private val planner = TargetDurationPlanner()

    @Test
    fun testTargetDurationPlanningAndTolerance() {
        val estimated = 7200000L // 2 hours
        val targetHigh = 6480000L // 1.8 hours (ratio = 0.90)
        val targetMod = 5400000L  // 1.5 hours (ratio = 0.75)

        val planHigh = planner.planTargetDuration(estimated, targetHigh, currentSpeed = 1.0f)
        assertEquals(0.90f, planHigh.requiredRatio, 0.01f)
        assertFalse(planHigh.isWithinTolerance)
        assertEquals(NaturalnessLevel.HIGH, planHigh.naturalnessLevel)

        val planMod = planner.planTargetDuration(estimated, targetMod, currentSpeed = 1.0f)
        assertEquals(0.75f, planMod.requiredRatio, 0.01f)
        assertEquals(1.33f, planMod.recommendedTtsSpeed, 0.05f)
        assertFalse(planMod.isWithinTolerance)
        assertEquals(NaturalnessLevel.MODERATE, planMod.naturalnessLevel)
    }

    @Test
    fun testExtremeTargetDurationWarning() {
        val estimated = 7200000L // 2 hours
        val extremeTarget = 1200000L // 20 mins (ratio = 0.16x)

        val plan = planner.planTargetDuration(estimated, extremeTarget)

        assertTrue(plan.isExtremeAdjustment)
        assertNotNull(plan.warningMessage)
        assertTrue(plan.warningMessage!!.contains("extreme"))
    }
}
