package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.domain.model.CharacterPosition
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

    @Test fun `drag delta moves v5 position continuously and clamps to canvas`() {
        val moved = CharacterPositioning.moveByPixels(
            "nai-diffusion-5-full",
            CharacterPosition(0.25f, 0.5f),
            deltaX = 50f,
            deltaY = -25f,
            width = 200f,
            height = 100f,
        )
        assertEquals(0.5f, moved.normalizedX)
        assertEquals(0.25f, moved.normalizedY)

        val clamped = CharacterPositioning.moveByPixels(
            "nai-diffusion-5-full",
            moved,
            deltaX = 500f,
            deltaY = -500f,
            width = 200f,
            height = 100f,
        )
        assertEquals(1f, clamped.normalizedX)
        assertEquals(0f, clamped.normalizedY)
    }
}
