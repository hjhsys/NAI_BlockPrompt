package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SeedSelectionTest {
    @Test fun `history seed is applied as fixed without changing other generation settings`() {
        val settings = GenerationSettings(
            modelId = "model",
            width = 1024,
            height = 1536,
            samplerId = "sampler",
            steps = 31,
            scale = 4.2f,
            seedMode = SeedMode.RANDOM,
            seed = null,
            guidanceRescale = 0.25f,
        )

        val applied = SeedSelection.applyFixedSeed(settings, 123456789L)

        assertEquals(SeedMode.FIXED, applied.seedMode)
        assertEquals(123456789L, applied.seed)
        assertEquals(settings.copy(seedMode = SeedMode.FIXED, seed = 123456789L), applied)
    }

    @Test fun `invalid history seed is rejected without changing settings`() {
        val settings = GenerationSettings(seedMode = SeedMode.RANDOM)
        assertEquals(settings, SeedSelection.applyFixedSeed(settings, -1L))
        assertEquals(settings, SeedSelection.applyFixedSeed(settings, 4_294_967_296L))
        assertFalse(SeedSelection.isValid(null))
    }

    @Test fun `invalid saved seeds are ignored and zero is valid`() {
        assertEquals(0L, SeedSelection.changeMode(GenerationSettings(), SeedMode.FIXED, -1, 0) { error("Unexpected random") }.seed)
        assertEquals(56L, SeedSelection.changeMode(GenerationSettings(), SeedMode.FIXED, 4_294_967_296L, -1) { 56 }.seed)
    }
    @Test fun `fixed picks selected then persisted last seed without randomizing`() {
        val settings = GenerationSettings()
        assertEquals(34L, SeedSelection.changeMode(settings, SeedMode.FIXED, 34, 12) { error("Unexpected random") }.seed)
        assertEquals(12L, SeedSelection.changeMode(settings, SeedMode.FIXED, null, 12) { error("Unexpected random") }.seed)
    }
    @Test fun `empty state gets seed and repeated fixed selection preserves it`() {
        val fixed = SeedSelection.changeMode(GenerationSettings(), SeedMode.FIXED, null) { 56 }
        assertEquals(56L, fixed.seed)
        assertEquals(fixed, SeedSelection.changeMode(fixed, SeedMode.FIXED, 78) { error("Unexpected random") })
    }

    @Test fun `toggle from random prefers selected then last successful seed`() {
        val settings = GenerationSettings(seedMode = SeedMode.RANDOM)
        assertEquals(34L, SeedSelection.toggleMode(settings, 34L, 12L) { error("Unexpected random") }.seed)
        assertEquals(12L, SeedSelection.toggleMode(settings, null, 12L) { error("Unexpected random") }.seed)
    }

    @Test fun `toggle from random always creates a valid fixed seed`() {
        val toggled = SeedSelection.toggleMode(GenerationSettings(), null, null) { 4_294_967_296L }
        assertEquals(SeedMode.FIXED, toggled.seedMode)
        assertEquals(0L, toggled.seed)
        assertTrue(SeedSelection.isValid(toggled.seed))
    }

    @Test fun `toggle from fixed returns to random and clears seed`() {
        val settings = GenerationSettings(seedMode = SeedMode.FIXED, seed = 99L, steps = 31)
        val toggled = SeedSelection.toggleMode(settings, 12L, 34L) { error("Unexpected random") }
        assertEquals(settings.copy(seedMode = SeedMode.RANDOM, seed = null), toggled)
    }
}
