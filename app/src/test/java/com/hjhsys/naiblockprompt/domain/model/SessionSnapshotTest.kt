package com.hjhsys.naiblockprompt.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import com.hjhsys.naiblockprompt.domain.editor.PromptOwner
import com.hjhsys.naiblockprompt.domain.editor.SessionEditor

class SessionSnapshotTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun snapshotRoundTripPreservesEditingState() {
        val original = SessionSnapshot(
            session = Session.empty().copy(
                base = Session.empty().base.copy(textRendering = TextRenderingState(true, "BASE TEXT", "BASE DESCRIPTION")),
                characters = listOf(
                    CharacterPrompt(
                        type = CharacterType.GIRL,
                        textRendering = TextRenderingState(true, "CHARACTER TEXT", "CHARACTER DESCRIPTION"),
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
        assertEquals(true, restored.session.characters.single().enabled)
        assertEquals(false, restored.session.base.collapsed)
        assertEquals(false, restored.session.blockScaleEnabled)
        assertEquals("1.0", restored.session.defaultBlockScale)
    }

    @Test fun `snapshot round trip preserves global and per block scale`() {
        val block = PromptBlock(name = "Artist", content = "artist", scaleMode = BlockScaleMode.CUSTOM, customScale = "1.25")
        val original = SessionSnapshot(session = Session.empty().copy(
            base = BasePrompt(PromptPair(positiveBlocks = listOf(block))),
            blockScaleEnabled = true,
            defaultBlockScale = "0.7",
        ))
        val restored = json.decodeFromString(SessionSnapshot.serializer(), json.encodeToString(SessionSnapshot.serializer(), original))
        assertEquals(original, restored)
    }

    @Test
    fun oldTextRenderingStateWithoutDescriptionUsesEmptyDefault() {
        val restored = json.decodeFromString(
            TextRenderingState.serializer(),
            """{"enabled":true,"content":"HELLO"}""",
        )

        assertEquals(TextRenderingState(true, "HELLO", ""), restored)
    }

    @Test
    fun snapshotRoundTripPreservesSplitAndMergedBlockStructure() {
        val originalBlock = PromptBlock(name = "Imported", content = "ABC, DEF, GHI")
        var session = Session.empty().copy(base = BasePrompt(PromptPair(positiveBlocks = listOf(originalBlock))))
        session = SessionEditor.splitBlockAtCursor(
            session, PromptOwner.Base, PromptPolarity.POSITIVE, originalBlock.id, 10, "Block 2",
        )
        val restoredSplit = json.decodeFromString(
            SessionSnapshot.serializer(),
            json.encodeToString(SessionSnapshot.serializer(), SessionSnapshot(session = session)),
        ).session
        assertEquals(session.base.prompts.positiveBlocks, restoredSplit.base.prompts.positiveBlocks)

        val currentId = restoredSplit.base.prompts.positiveBlocks[1].id
        val merged = SessionEditor.mergeBlockWithPrevious(restoredSplit, PromptOwner.Base, PromptPolarity.POSITIVE, currentId)
        val restoredMerged = json.decodeFromString(
            SessionSnapshot.serializer(),
            json.encodeToString(SessionSnapshot.serializer(), SessionSnapshot(session = merged)),
        ).session
        assertEquals(merged.base.prompts.positiveBlocks, restoredMerged.base.prompts.positiveBlocks)
    }
}
