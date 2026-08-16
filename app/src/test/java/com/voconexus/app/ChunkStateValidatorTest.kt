package com.voconexus.app

import com.voconexus.app.core.data.db.ChunkStatus
import com.voconexus.app.core.domain.ChunkStateValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkStateValidatorTest {

    @Test
    fun testLegalStateTransitions() {
        assertTrue(ChunkStateValidator.isValidTransition(ChunkStatus.PENDING, ChunkStatus.QUEUED))
        assertTrue(ChunkStateValidator.isValidTransition(ChunkStatus.QUEUED, ChunkStatus.GENERATING))
        assertTrue(ChunkStateValidator.isValidTransition(ChunkStatus.GENERATING, ChunkStatus.VALIDATING))
        assertTrue(ChunkStateValidator.isValidTransition(ChunkStatus.VALIDATING, ChunkStatus.COMPLETED))
        assertTrue(ChunkStateValidator.isValidTransition(ChunkStatus.COMPLETED, ChunkStatus.NEEDS_REGENERATION))
        assertTrue(ChunkStateValidator.isValidTransition(ChunkStatus.FAILED, ChunkStatus.QUEUED))
    }

    @Test
    fun testIllegalStateTransitions() {
        assertFalse(ChunkStateValidator.isValidTransition(ChunkStatus.PENDING, ChunkStatus.COMPLETED))
        assertFalse(ChunkStateValidator.isValidTransition(ChunkStatus.COMPLETED, ChunkStatus.GENERATING))
    }

    @Test(expected = IllegalStateException::class)
    fun testValidateTransitionThrowsOnIllegal() {
        ChunkStateValidator.validateTransition(ChunkStatus.PENDING, ChunkStatus.COMPLETED)
    }
}
