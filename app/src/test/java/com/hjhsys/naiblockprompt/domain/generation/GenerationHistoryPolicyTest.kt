package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.domain.model.GeneratedPromptSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class GenerationHistoryPolicyTest {
    private val generation = GeneratedPromptSnapshot(
        basePositive = "girl,",
        baseNegative = "lowres,",
        characterPositive = listOf("smile,"),
        characterNegative = listOf("sad,"),
        usedSeed = 42,
        modelId = "nai-diffusion-4-5-full",
        samplerId = "k_euler_ancestral",
        width = 832,
        height = 1216,
        steps = 28,
        scale = 5f,
        guidanceRescale = 0.4f,
    )

    @Test fun `duplicate warning requires an existing original image`() {
        assertTrue(GenerationHistoryPolicy.shouldWarnForDuplicate(generation, true, generation))
        assertFalse(GenerationHistoryPolicy.shouldWarnForDuplicate(generation, false, generation))
    }

    @Test fun `missing original with same generation repairs existing history`() {
        assertTrue(GenerationHistoryPolicy.shouldRepairExisting(generation, false, generation))
        assertFalse(GenerationHistoryPolicy.shouldRepairExisting(generation, true, generation))
        assertFalse(GenerationHistoryPolicy.shouldRepairExisting(generation.copy(usedSeed = 43), false, generation))
    }

    @Test fun `result affecting advanced setting prevents a duplicate match`() {
        assertFalse(GenerationHistoryPolicy.shouldWarnForDuplicate(
            generation.copy(guidanceRescale = 0.2f),
            true,
            generation,
        ))
    }

    @Test fun `older generation snapshot without advanced fields keeps compatible defaults`() {
        val decoded = Json.decodeFromString<GeneratedPromptSnapshot>(
            """{"basePositive":"girl,","baseNegative":"","characterPositive":[],"characterNegative":[],"usedSeed":1,"modelId":"model","samplerId":"sampler","width":832,"height":1216,"steps":28,"scale":5.0}""",
        )

        assertTrue(decoded.useOrder)
        assertFalse(decoded.useCoordinates)
        assertFalse(decoded.dynamicThresholding)
    }
}
