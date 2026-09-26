package com.hjhsys.naiblockprompt.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEditPressTimingTest {
    @Test
    fun `quick edit timing accepts documented bounds`() {
        assertTrue(QuickEditPressTiming.isValidLongPress(100))
        assertTrue(QuickEditPressTiming.isValidLongPress(3_000))
        assertFalse(QuickEditPressTiming.isValidLongPress(99))
        assertFalse(QuickEditPressTiming.isValidLongPress(3_001))

        assertTrue(QuickEditPressTiming.isValidRepeatInterval(50))
        assertTrue(QuickEditPressTiming.isValidRepeatInterval(2_000))
        assertFalse(QuickEditPressTiming.isValidRepeatInterval(49))
        assertFalse(QuickEditPressTiming.isValidRepeatInterval(2_001))
    }
}
