package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NaiRequestMapperTest {
    @Test fun `maps base and ordered characters while preprocessing blocks`() {
        val session = Session(
            base = BasePrompt(PromptPair(
                positiveBlocks = listOf(PromptBlock(name="off", content="ignored", enabled=false, order=0), PromptBlock(name="on", content="base ##memo## 1::tag2::", order=1)),
                negativeBlocks = listOf(PromptBlock(name="n", content="bad")),
            )),
            characters = listOf(
                character("second", 1, "boy"),
                character("first", 0, "girl ## hidden"),
            ),
            generationSettings = GenerationSettings("nai-diffusion-4-5-full", 832, 1216, "k_euler_ancestral", 28, 5f, SeedMode.RANDOM),
        )
        val ready = NaiRequestMapper { 123L }.prepare(session, true) as PrepareGenerationResult.Ready
        val request = ready.generation.request

        assertEquals("base  1::tag2 ::,", request.input)
        assertEquals("bad,", request.parameters.negativePrompt)
        assertEquals(listOf("girl,", "boy,"), request.parameters.v4Prompt.caption.characterCaptions.map { it.characterCaption })
        assertEquals(1, request.parameters.sampleCount)
        assertEquals(123L, request.parameters.seed)
        assertFalse(request.parameters.v4Prompt.useCoordinates)
        assertTrue(request.parameters.v4Prompt.useOrder)
    }

    @Test fun `serialized DTO uses generation action and swagger field names`() {
        val session = Session.empty().copy(
            characters = listOf(character("character", 0, "girl")),
            generationSettings = GenerationSettings("nai-diffusion-4-5-full", samplerId="k_euler_ancestral", steps=1, scale=1f),
        )
        val ready = NaiRequestMapper { 7 }.prepare(session, false) as PrepareGenerationResult.Ready
        val encoded = Json { encodeDefaults = true }.encodeToString(ready.generation.request)
        assertTrue(encoded.contains("\"n_samples\":1"))
        assertTrue(encoded.contains("\"v4_prompt\""))
        assertTrue(encoded.contains("\"char_captions\""))
        assertTrue(encoded.contains("\"action\":\"generate\""))
        assertTrue(encoded.contains("\"params_version\":3"))
        assertTrue(encoded.contains("\"noise_schedule\":\"karras\""))
        assertTrue(encoded.contains("\"uc\""))
        assertTrue(encoded.contains("\"centers\":[{\"x\":0.5,\"y\":0.5}]"))
    }

    @Test fun `switching model preserves prompts and selects model parameter version`() {
        val base = Session.empty().copy(
            generationSettings = GenerationSettings("nai-diffusion-4-5-full", samplerId="k_euler_ancestral", steps=28, scale=6f),
        )
        val mapper = NaiRequestMapper { 9L }
        val v45 = (mapper.prepare(base, false) as PrepareGenerationResult.Ready).generation.request
        val v5 = (mapper.prepare(base.copy(generationSettings = base.generationSettings.copy(modelId="nai-diffusion-5-full")), false) as PrepareGenerationResult.Ready).generation.request
        assertEquals(v45.input, v5.input)
        assertEquals(v45.parameters.v4Prompt, v5.parameters.v4Prompt)
        assertEquals(3, v45.parameters.paramsVersion)
        assertEquals(4, v5.parameters.paramsVersion)
    }

    private fun character(id: String, order: Int, positive: String) = CharacterPrompt(
        id=id, order=order, prompts=PromptPair(positiveBlocks=listOf(PromptBlock(name="p",content=positive)))
    )
}
