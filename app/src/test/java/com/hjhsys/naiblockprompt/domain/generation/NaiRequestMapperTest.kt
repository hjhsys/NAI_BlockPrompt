package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.data.network.nai.dto.NaiCoordinate
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

    @Test fun `maps base and character text rendering at positive prompt end only`() {
        val session = Session(
            base = BasePrompt(
                prompts = PromptPair(
                    positiveBlocks = listOf(PromptBlock(name = "p", content = "base")),
                    negativeBlocks = listOf(PromptBlock(name = "n", content = "undesired")),
                ),
                textRendering = TextRenderingState(true, "BASE TEXT"),
            ),
            characters = listOf(
                CharacterPrompt(
                    prompts = PromptPair(
                        positiveBlocks = listOf(PromptBlock(name = "p", content = "girl")),
                        negativeBlocks = listOf(PromptBlock(name = "n", content = "bad hands")),
                    ),
                    textRendering = TextRenderingState(true, "CHARACTER TEXT"),
                ),
            ),
            generationSettings = GenerationSettings("nai-diffusion-4-5-full", samplerId = "k_euler_ancestral", steps = 28, scale = 5f),
        )
        val request = (NaiRequestMapper { 11L }.prepare(session, true) as PrepareGenerationResult.Ready).generation.request

        assertEquals("base,\nText: BASE TEXT", request.input)
        assertEquals("base,\nText: BASE TEXT", request.parameters.v4Prompt.caption.baseCaption)
        assertEquals("girl,\nText: CHARACTER TEXT", request.parameters.v4Prompt.caption.characterCaptions.single().characterCaption)
        assertEquals("undesired,", request.parameters.v4NegativePrompt.caption.baseCaption)
        assertEquals("bad hands,", request.parameters.v4NegativePrompt.caption.characterCaptions.single().characterCaption)
    }

    @Test fun `global text rendering setting can omit retained session text`() {
        val session = Session.empty().copy(
            base = Session.empty().base.copy(
                prompts = PromptPair(positiveBlocks = listOf(PromptBlock(name = "p", content = "base"))),
                textRendering = TextRenderingState(true, "HELLO", "speech bubble,"),
            ),
            generationSettings = GenerationSettings("nai-diffusion-4-5-full", samplerId = "k_euler_ancestral", steps = 28, scale = 5f),
        )

        val request = (NaiRequestMapper { 11L }.prepare(
            session,
            normalizeWeights = true,
            includeTextRendering = false,
        ) as PrepareGenerationResult.Ready).generation.request

        assertEquals("base,", request.input)
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

    @Test fun `maps guidance rescale to official cfg rescale parameter`() {
        val session = Session.empty().copy(
            generationSettings = GenerationSettings(
                modelId = "nai-diffusion-4-5-full",
                samplerId = "k_euler_ancestral",
                steps = 28,
                scale = 5f,
                guidanceRescale = 0.4f,
            ),
        )
        val request = (NaiRequestMapper { 13L }.prepare(session, false) as PrepareGenerationResult.Ready).generation.request
        assertEquals(0.4f, request.parameters.guidanceRescale)
        assertTrue(Json.encodeToString(request).contains("\"cfg_rescale\":0.4"))
    }

    @Test fun `V4_5 maps each supported noise schedule`() {
        NaiGenerationCatalog.noiseSchedules.forEach { option ->
            val session = Session.empty().copy(
                generationSettings = GenerationSettings(
                    modelId = "nai-diffusion-4-5-full",
                    samplerId = "k_euler_ancestral",
                    steps = 28,
                    scale = 5f,
                    noiseSchedule = option.apiId,
                ),
            )

            val request = (NaiRequestMapper { 13L }.prepare(session, false) as PrepareGenerationResult.Ready).generation.request
            assertEquals(option.apiId, request.parameters.noiseSchedule)
        }
    }

    @Test fun `unsupported model does not send retained selectable noise schedule`() {
        val settings = GenerationSettings(
            modelId = "nai-diffusion-5-full",
            samplerId = "k_euler_ancestral",
            steps = 28,
            scale = 5f,
            noiseSchedule = "polyexponential",
        )

        val v5 = (NaiRequestMapper { 14L }.prepare(Session.empty().copy(generationSettings = settings), false) as PrepareGenerationResult.Ready).generation
        assertEquals("karras", v5.request.parameters.noiseSchedule)
        assertEquals("polyexponential", v5.sourceSession.generationSettings.noiseSchedule)

        val v45 = (NaiRequestMapper { 14L }.prepare(
            v5.sourceSession.copy(generationSettings = settings.copy(modelId = "nai-diffusion-4-5-full")),
            false,
        ) as PrepareGenerationResult.Ready).generation.request
        assertEquals("polyexponential", v45.parameters.noiseSchedule)
    }

    @Test fun `invalid noise schedule falls back to stable default`() {
        val session = Session.empty().copy(
            generationSettings = GenerationSettings(
                modelId = "nai-diffusion-4-5-full",
                samplerId = "k_euler_ancestral",
                steps = 28,
                scale = 5f,
                noiseSchedule = "not-a-schedule",
            ),
        )

        val request = (NaiRequestMapper { 15L }.prepare(session, false) as PrepareGenerationResult.Ready).generation.request
        assertEquals("karras", request.parameters.noiseSchedule)
    }

    @Test fun `maps custom character centers and enables coordinates only for positive condition`() {
        val session = Session.empty().copy(
            characters = listOf(
                character("a", 0, "girl").copy(position = CharacterPosition(0.1f, 0.3f)),
                character("b", 1, "boy").copy(position = CharacterPosition(0.7f, 0.9f)),
            ),
            generationSettings = GenerationSettings("nai-diffusion-4-5-full", samplerId = "k_euler_ancestral", steps = 28, scale = 5f),
        )
        val parameters = (NaiRequestMapper { 1L }.prepare(session, false) as PrepareGenerationResult.Ready).generation.request.parameters

        assertTrue(parameters.useCoordinates)
        assertTrue(parameters.v4Prompt.useCoordinates)
        assertFalse(parameters.v4NegativePrompt.useCoordinates)
        assertEquals(NaiCoordinate(0.1f, 0.3f), parameters.v4Prompt.caption.characterCaptions[0].centers.single())
        assertEquals(NaiCoordinate(0.7f, 0.9f), parameters.v4Prompt.caption.characterCaptions[1].centers.single())
        assertEquals(parameters.v4Prompt.caption.characterCaptions.map { it.centers }, parameters.v4NegativePrompt.caption.characterCaptions.map { it.centers })
        assertEquals(NaiCoordinate(0.1f, 0.3f), parameters.characterPrompts[0].center)
        assertEquals("girl,", parameters.characterPrompts[0].prompt)
        assertTrue(parameters.characterPrompts[0].enabled)
    }

    @Test fun `serializes documented external reference field names without web cache fields`() {
        val session = Session.empty().copy(
            generationSettings = GenerationSettings("nai-diffusion-4-5-full", samplerId = "k_euler_ancestral", steps = 28, scale = 5f),
        )
        val request = (NaiRequestMapper { 1L }.prepare(session, false) as PrepareGenerationResult.Ready).generation.request
        val parameters = request.parameters.copy(
            referenceImages = listOf("vibe"),
            referenceInformationExtracted = listOf(0.7f),
            referenceStrengths = listOf(0.6f),
            directorReferenceImages = listOf("image"),
            directorReferenceDescriptions = listOf(request.parameters.v4Prompt.copy(useCoordinates = false, useOrder = false)),
            directorReferenceInformationExtracted = listOf(1f),
            directorReferenceStrengths = listOf(1f),
            directorReferenceSecondaryStrengths = listOf(0f),
        )
        val encoded = Json.encodeToString(request.copy(parameters = parameters))

        assertTrue(encoded.contains("\"reference_image_multiple\":[\"vibe\"]"))
        assertTrue(encoded.contains("\"director_reference_images\":[\"image\"]"))
        assertTrue(encoded.contains("\"director_reference_strength_values\":[1.0]"))
        assertTrue(encoded.contains("\"director_reference_secondary_strength_values\":[0.0]"))
        assertFalse(encoded.contains("cache_secret_key"))
        assertFalse(encoded.contains("_cached"))
    }

    @Test fun `V5 omits vibe fields while retaining the session state`() {
        val vibe = ImageInputState(
            uri = "content://vibe/source",
            mode = ImageInputMode.VIBE_TRANSFER,
            strength = 0.65f,
            informationExtracted = 0.8f,
        )
        val session = Session.empty().copy(
            generationSettings = GenerationSettings(
                modelId = "nai-diffusion-5-full",
                samplerId = "k_euler_ancestral",
                steps = 28,
                scale = 5f,
                imageInput = vibe,
            ),
        )
        val prepared = (NaiRequestMapper { 15L }.prepare(session, false) as PrepareGenerationResult.Ready).generation
        val request = VibeTransferRequestMapper.attach(prepared.request, "encoded-vibe", 0.8f, 0.65f)

        assertEquals(vibe, prepared.sourceSession.generationSettings.imageInput)
        assertNull(request.parameters.referenceImages)
        assertNull(request.parameters.referenceInformationExtracted)
        assertNull(request.parameters.referenceStrengths)
    }

    @Test fun `V4_5 attaches retained vibe state to request`() {
        val session = Session.empty().copy(
            generationSettings = GenerationSettings(
                modelId = "nai-diffusion-4-5-full",
                samplerId = "k_euler_ancestral",
                steps = 28,
                scale = 5f,
                imageInput = ImageInputState("content://vibe/source", ImageInputMode.VIBE_TRANSFER),
            ),
        )
        val prepared = (NaiRequestMapper { 16L }.prepare(session, false) as PrepareGenerationResult.Ready).generation
        val request = VibeTransferRequestMapper.attach(prepared.request, "encoded-vibe", 0.75f, 0.6f)

        assertEquals(listOf("encoded-vibe"), request.parameters.referenceImages)
        assertEquals(listOf(0.75f), request.parameters.referenceInformationExtracted)
        assertEquals(listOf(0.6f), request.parameters.referenceStrengths)
        assertEquals(ImageInputMode.VIBE_TRANSFER, prepared.sourceSession.generationSettings.imageInput?.mode)
    }

    private fun character(id: String, order: Int, positive: String) = CharacterPrompt(
        id=id, order=order, prompts=PromptPair(positiveBlocks=listOf(PromptBlock(name="p",content=positive)))
    )
}
