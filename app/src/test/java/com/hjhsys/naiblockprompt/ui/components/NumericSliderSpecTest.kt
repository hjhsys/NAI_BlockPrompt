package com.hjhsys.naiblockprompt.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NumericSliderSpecTest {
    @Test fun `history count 40 round trips through an integer tick`() {
        val spec = NumericSliderSpec(1.0, 100.0, 1.0, 0)
        assertEquals(39, spec.tickIndex(40.0))
        assertEquals(40.0, spec.valueAtTick(39), 0.0)
        assertEquals("40", spec.format(40.0))
    }

    @Test fun `guidance 3 point 2 remains selectable and displayed`() {
        val spec = NumericSliderSpec(0.0, 10.0, 0.1, 1)
        assertEquals(32, spec.tickIndex(3.2))
        assertEquals(3.2, spec.valueAtTick(32), 0.0)
        assertEquals("3.2", spec.format(3.2))
    }

    @Test fun `rescale thumb display and value use the same tick`() {
        val spec = NumericSliderSpec(0.0, 1.0, 0.05, 2)
        assertEquals(7, spec.tickIndex(0.35))
        assertEquals(0.35, spec.valueAtTick(7), 0.0)
        assertEquals("0.35", spec.format(0.35))
    }

    @Test fun `every image setting tick round trips`() {
        val spec = NumericSliderSpec(0.0, 1.0, 0.05, 2)
        for (tick in 0..spec.lastTickIndex) {
            assertEquals(tick, spec.tickIndex(spec.valueAtTick(tick)))
        }
    }

    @Test fun `direct input accepts only values on the configured tick grid`() {
        val spec = NumericSliderSpec(0.0, 10.0, 0.1, 1)
        assertEquals(3.2, spec.parseInput("3.2")!!, 0.0)
        assertEquals(3.2, spec.parseInput(" 3.20 ")!!, 0.0)
        assertEquals(null, spec.parseInput("3.25"))
        assertEquals(null, spec.parseInput("11"))
        assertEquals(null, spec.parseInput("not a number"))
    }

    @Test fun `integer direct input preserves exact history count`() {
        val spec = NumericSliderSpec(1.0, 100.0, 1.0, 0)
        assertEquals(40.0, spec.parseInput("40")!!, 0.0)
        assertEquals(null, spec.parseInput("40.5"))
    }
}
