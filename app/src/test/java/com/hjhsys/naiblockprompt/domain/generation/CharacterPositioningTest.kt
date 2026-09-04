package com.hjhsys.naiblockprompt.domain.generation

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterPositioningTest {
    @Test fun `v45 coordinates snap to five by five cell centers`() {
        val position = CharacterPositioning.normalize("nai-diffusion-4-5-full", 0.01f, 0.79f)
        assertEquals(0.1f, position.normalizedX)
        assertEquals(0.7f, position.normalizedY)
    }

    @Test fun `v5 coordinates remain continuous`() {
        val position = CharacterPositioning.normalize("nai-diffusion-5-full", 0.172f, 0.543f)
        assertEquals(0.172f, position.normalizedX)
        assertEquals(0.543f, position.normalizedY)
    }
}
