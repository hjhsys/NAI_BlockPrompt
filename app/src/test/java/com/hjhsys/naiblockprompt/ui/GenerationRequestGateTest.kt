package com.hjhsys.naiblockprompt.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationRequestGateTest {
    @Test
    fun `additional taps are ignored until generation finishes`() {
        val gate = GenerationRequestGate()

        assertTrue(gate.tryStart())
        assertTrue(gate.inProgress.value)
        assertFalse(gate.tryStart())

        gate.finish()

        assertFalse(gate.inProgress.value)
        assertTrue(gate.tryStart())
    }
}
