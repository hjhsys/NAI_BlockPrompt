package com.hjhsys.naiblockprompt.data.network.nai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NaiImageGenerationRequest(
    val action: String = "generate",
    val input: String,
    val model: String,
    val parameters: NaiRequestParameters,
)

@Serializable
data class NaiRequestParameters(
    @SerialName("params_version") val paramsVersion: Int,
    val width: Int,
    val height: Int,
    @SerialName("n_samples") val sampleCount: Int = 1,
    val sampler: String,
    val steps: Int,
    val scale: Float,
    @SerialName("cfg_rescale") val guidanceRescale: Float? = null,
    val seed: Long,
    val prompt: String,
    @SerialName("negative_prompt") val negativePrompt: String,
    val uc: String,
    @SerialName("noise_schedule") val noiseSchedule: String = "karras",
    val sm: Boolean = false,
    @SerialName("sm_dyn") val smDynamic: Boolean = false,
    @SerialName("dynamic_thresholding") val dynamicThresholding: Boolean = false,
    @SerialName("use_coords") val useCoordinates: Boolean = false,
    @SerialName("characterPrompts") val characterPrompts: List<NaiLegacyCharacterPrompt> = emptyList(),
    @SerialName("image_format") val imageFormat: String = "png",
    /** Base64 source image for Swagger's img2img action. */
    val image: String? = null,
    val mask: String? = null,
    @SerialName("add_original_image") val addOriginalImage: Boolean? = null,
    @SerialName("inpaintImg2ImgStrength") val inpaintImg2ImgStrength: Float? = null,
    @SerialName("straight_alpha") val straightAlpha: Boolean? = null,
    val stream: String? = null,
    val strength: Float? = null,
    val noise: Float? = null,
    @SerialName("reference_image_multiple") val referenceImages: List<String>? = null,
    @SerialName("reference_information_extracted_multiple") val referenceInformationExtracted: List<Float>? = null,
    @SerialName("reference_strength_multiple") val referenceStrengths: List<Float>? = null,
    @SerialName("director_reference_images") val directorReferenceImages: List<String>? = null,
    @SerialName("director_reference_descriptions") val directorReferenceDescriptions: List<NaiV4ConditionInput>? = null,
    @SerialName("director_reference_information_extracted") val directorReferenceInformationExtracted: List<Float>? = null,
    @SerialName("director_reference_strength_values") val directorReferenceStrengths: List<Float>? = null,
    @SerialName("director_reference_secondary_strength_values") val directorReferenceSecondaryStrengths: List<Float>? = null,
    @SerialName("v4_prompt") val v4Prompt: NaiV4ConditionInput,
    @SerialName("v4_negative_prompt") val v4NegativePrompt: NaiV4ConditionInput,
)

@Serializable
data class NaiV4ConditionInput(
    val caption: NaiV4ExternalCaption,
    @SerialName("legacy_uc") val legacyUc: Boolean = false,
    @SerialName("use_coords") val useCoordinates: Boolean = false,
    @SerialName("use_order") val useOrder: Boolean = true,
)

@Serializable
data class NaiV4ExternalCaption(
    @SerialName("base_caption") val baseCaption: String,
    @SerialName("char_captions") val characterCaptions: List<NaiV4CharacterCaption>,
)

@Serializable
data class NaiV4CharacterCaption(
    @SerialName("char_caption") val characterCaption: String,
    /**
     * The service rejects an empty centers array for non-empty character captions.
     * This neutral compatibility value is ignored while use_coords=false and does not map positioning.
     */
    val centers: List<NaiCoordinate> = listOf(NaiCoordinate(0.5f, 0.5f)),
)

@Serializable
data class NaiCoordinate(val x: Float, val y: Float)

@Serializable
data class NaiLegacyCharacterPrompt(
    val prompt: String,
    val uc: String,
    val center: NaiCoordinate,
    val enabled: Boolean = true,
)

@Serializable
data class NaiImageGenerationResponse(val images: List<NaiGeneratedImage> = emptyList())

@Serializable
data class NaiGeneratedImage(
    val image: String,
    val index: Int? = null,
    val seed: Long? = null,
)

@Serializable
data class NaiEncodeVibeRequest(
    val image: String,
    @SerialName("information_extracted") val informationExtracted: Float,
    val model: String,
)

@Serializable
data class NaiTagSuggestionResponse(val tags: List<NaiSuggestedTag> = emptyList())

@Serializable
data class NaiSuggestedTag(val tag: String? = null)
