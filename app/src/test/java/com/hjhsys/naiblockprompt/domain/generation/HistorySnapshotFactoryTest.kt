package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySnapshotFactoryTest {
    @Test fun `history keeps prompt settings model and actual random seed`() {
        val session = Session.empty().copy(
            generationSettings = GenerationSettings("nai-diffusion-4-5-full", samplerId="k_euler_ancestral", steps=28, scale=5f, seedMode=SeedMode.RANDOM),
        )
        val prepared = (NaiRequestMapper { 10L }.prepare(session, false) as PrepareGenerationResult.Ready).generation
        val snapshot = HistorySnapshotFactory.from(prepared, 99L)

        assertEquals(session.base, snapshot.session.base)
        assertEquals("nai-diffusion-4-5-full", snapshot.session.generationSettings.modelId)
        assertEquals(99L, snapshot.session.generationSettings.seed)
        assertEquals(SeedMode.RANDOM, snapshot.session.generationSettings.seedMode)
        assertEquals(99L, snapshot.generation?.usedSeed)
        assertEquals("nai-diffusion-4-5-full", snapshot.generation?.modelId)
        assertTrue(snapshot.snapshotVersion > 0)
    }

    @Test fun `history resolved character positions align with enabled request characters`() {
        val session = Session.empty().copy(
            characters = listOf(
                CharacterPrompt(order = 0, position = CharacterPosition(.1f, .2f)),
                CharacterPrompt(order = 1, enabled = false, position = CharacterPosition(.4f, .5f)),
                CharacterPrompt(order = 2, position = CharacterPosition(.8f, .9f)),
            ),
            generationSettings = GenerationSettings("nai-diffusion-4-5-full", samplerId = "k_euler_ancestral", steps = 28, scale = 5f),
        )
        val prepared = (NaiRequestMapper { 10L }.prepare(session, false) as PrepareGenerationResult.Ready).generation
        val snapshot = HistorySnapshotFactory.from(prepared, 10L)
        assertEquals(listOf(CharacterPosition(.1f, .2f), CharacterPosition(.8f, .9f)), snapshot.generation?.characterPositions)
        assertEquals(2, snapshot.generation?.characterPositive?.size)
        assertEquals(false, snapshot.session.characters[1].enabled)
    }
}
