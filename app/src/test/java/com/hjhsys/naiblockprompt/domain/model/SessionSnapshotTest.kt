package com.hjhsys.naiblockprompt.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSnapshotTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun snapshotRoundTripPreservesEditingState() {
        val original = SessionSnapshot(
            session = Session.empty().copy(
                characters = listOf(
                    CharacterPrompt(
                        type = CharacterType.GIRL,
                        prompts = PromptPair(
                            positiveBlocks = listOf(
                                PromptBlock(
                                    name = "Style",
                                    content = "1.2::soft light :: ## memo ##",
                                    enabled = false,
                                    locked = true,
                                    collapsed = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val restored = json.decodeFromString(
            SessionSnapshot.serializer(),
            json.encodeToString(SessionSnapshot.serializer(), original),
        )

        assertEquals(original, restored)
    }
}
