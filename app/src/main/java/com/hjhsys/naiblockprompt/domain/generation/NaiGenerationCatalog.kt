package com.hjhsys.naiblockprompt.domain.generation

data class NaiCatalogOption(val apiId: String, val displayName: String)

object NaiGenerationCatalog {
    val models = listOf(
        NaiCatalogOption("nai-diffusion-5-full", "NovelAI Diffusion V5 Full"),
        NaiCatalogOption("nai-diffusion-5-curated", "NovelAI Diffusion V5 Curated"),
        NaiCatalogOption("nai-diffusion-4-5-full", "NovelAI Diffusion V4.5 Full"),
        NaiCatalogOption("nai-diffusion-4-5-curated", "NovelAI Diffusion V4.5 Curated"),
        NaiCatalogOption("nai-diffusion-4-full", "NovelAI Diffusion V4 Full"),
        NaiCatalogOption("nai-diffusion-4-curated-preview", "NovelAI Diffusion V4 Curated"),
        NaiCatalogOption("nai-diffusion-3", "NovelAI Diffusion Anime V3"),
        NaiCatalogOption("nai-diffusion-furry-3", "NovelAI Diffusion Furry V3"),
    )

    fun parameterVersion(modelId: String): Int = if (modelId.startsWith("nai-diffusion-5-")) 4 else 3

    val samplers = listOf(
        NaiCatalogOption("k_dpmpp_2m", "DPM++ 2M"),
        NaiCatalogOption("k_euler_ancestral", "Euler Ancestral"),
        NaiCatalogOption("k_euler", "Euler"),
        NaiCatalogOption("k_dpm_2", "DPM2"),
        NaiCatalogOption("k_dpmpp_2s_ancestral", "DPM++ 2S Ancestral"),
        NaiCatalogOption("k_dpmpp_sde", "DPM++ SDE"),
        NaiCatalogOption("k_dpm_fast", "DPM Fast"),
        NaiCatalogOption("ddim_v3", "DDIM"),
    )
}
