package com.hjhsys.naiblockprompt.domain.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NaiGenerationCatalogTest {
    @Test fun `resolves api id display name and inpainting derivative`() {
        assertEquals("nai-diffusion-5-full", NaiGenerationCatalog.resolveModelId("nai-diffusion-5-full"))
        assertEquals("nai-diffusion-5-full", NaiGenerationCatalog.resolveModelId("NovelAI Diffusion V5 Full"))
        assertEquals("nai-diffusion-5-full", NaiGenerationCatalog.resolveModelId("nai-diffusion-5-full-inpainting"))
        assertEquals("nai-diffusion-4-5-full", NaiGenerationCatalog.resolveModelId("NovelAI Diffusion V4.5 Full Inpainting"))
        assertEquals("nai-diffusion-5-full", NaiGenerationCatalog.resolveModelId("NovelAI Diffusion V5"))
        assertEquals("nai-diffusion-5-full", NaiGenerationCatalog.resolveModelId("NovelAI Diffusion V5 0ADF9AB7"))
        assertEquals("nai-diffusion-5-curated", NaiGenerationCatalog.resolveModelId("NovelAI Diffusion V5 Curated"))
    }

    @Test fun `does not guess unknown metadata model`() {
        assertNull(NaiGenerationCatalog.resolveModelId("unknown-model-hash"))
    }
}
