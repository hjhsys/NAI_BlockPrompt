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
                base = Session.empty().base.copy(textRendering = TextRenderingState(true, "BASE TEXT")),
                characters = listOf(
                    CharacterPrompt(
                        type = CharacterType.GIRL,
                        textRendering = TextRenderingState(true, "CHARACTER TEXT"),
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


    @Test
    fun snapshotWithoutTextRenderingFieldsRestoresWithDisabledEmptyDefaults() {
        val restored = json.decodeFromString(
            SessionSnapshot.serializer(),
            """{"snapshotVersion":1,"session":{"base":{"prompts":{}},"characters":[{"id":"old","prompts":{}}]}}""",
        )

        assertEquals(TextRenderingState(), restored.session.base.textRendering)
        assertEquals(TextRenderingState(), restored.session.characters.single().textRendering)
        assertEquals(false, restored.session.characters.single().collapsed)
    }
}
