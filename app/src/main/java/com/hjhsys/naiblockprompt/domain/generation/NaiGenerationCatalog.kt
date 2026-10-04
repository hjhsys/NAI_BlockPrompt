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

    fun resolveModelId(metadataValue: String?): String? {
        val value = metadataValue?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val baseValue = value
            .replace(Regex("(?i)(?:-inpainting| inpainting)$"), "")
            .replace(Regex("(?i)\\s+[0-9a-f]{8}$"), "")
        val candidates = listOf(value, baseValue).distinct()
        val exact = models.firstOrNull { option ->
            candidates.any { candidate ->
                option.apiId.equals(candidate, ignoreCase = true) ||
                    option.displayName.equals(candidate, ignoreCase = true)
            }
        }?.apiId
        if (exact != null) return exact

        return when (baseValue.lowercase()) {
            "novelai diffusion v5" -> "nai-diffusion-5-full"
            "novelai diffusion v5 curated" -> "nai-diffusion-5-curated"
            "novelai diffusion v4.5" -> "nai-diffusion-4-5-full"
            "novelai diffusion v4.5 curated" -> "nai-diffusion-4-5-curated"
            "novelai diffusion v4" -> "nai-diffusion-4-full"
            "novelai diffusion v4 curated" -> "nai-diffusion-4-curated-preview"
            else -> null
        }
    }

    fun parameterVersion(modelId: String): Int = if (modelId.startsWith("nai-diffusion-5-")) 4 else 3

    fun supportsSelectableNoiseSchedule(modelId: String?): Boolean =
        modelId?.startsWith("nai-diffusion-4-5-") == true

    fun requestNoiseSchedule(modelId: String, selected: String): String =
        if (supportsSelectableNoiseSchedule(modelId) && noiseSchedules.any { it.apiId == selected }) selected else DEFAULT_NOISE_SCHEDULE

    const val DEFAULT_NOISE_SCHEDULE = "karras"

    val noiseSchedules = listOf(
        NaiCatalogOption("karras", "Karras"),
        NaiCatalogOption("exponential", "Exponential"),
        NaiCatalogOption("polyexponential", "Polyexponential"),
    )

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
