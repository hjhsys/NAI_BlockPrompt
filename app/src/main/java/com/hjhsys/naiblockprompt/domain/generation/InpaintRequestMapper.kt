package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.data.network.nai.dto.NaiImageGenerationRequest
import com.hjhsys.naiblockprompt.domain.image.InpaintCompositor
import com.hjhsys.naiblockprompt.domain.image.InpaintMask

/** Verified against n4.5 inpaint2.txt and n5 inpaint.txt; no curated fallback. */
object InpaintRequestMapper {
    fun supports(model: String?) = model in setOf("nai-diffusion-4-5-full", "nai-diffusion-5-full")

    fun map(
        request: NaiImageGenerationRequest,
        image: String,
        mask: InpaintMask,
        width: Int,
        height: Int,
        strength: Float,
        encodeMaskPng: (ByteArray) -> String,
    ): NaiImageGenerationRequest {
        require(supports(request.model)) { "Inpaint supports V4.5 Full / V5 Full" }
        require(width > 0 && height > 0 && image.isNotBlank())
        mask.requireMatchesSource(width, height)
        require(!mask.isEmpty) { "Inpaint mask is empty" }
        val encodedMask = encodeMaskPng(InpaintCompositor.serverMask(mask).toApiPngBytes())
        require(encodedMask.isNotBlank())
        require(strength.isFinite() && strength in 0f..1f)
        return request.copy(action = "infill", model = request.model + "-inpainting",
            parameters = request.parameters.copy(paramsVersion = 4, width = width, height = height,
                image = image, mask = encodedMask, strength = strength, noise = DEFAULT_NOISE,
                addOriginalImage = false, inpaintImg2ImgStrength = 1f, straightAlpha = true,
                imageFormat = "webp", stream = "msgpack"))
    }

    private const val DEFAULT_NOISE = 0f
}
