package com.hjhsys.naiblockprompt.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val historyLimit: Int = 20,
    val showFormatterActions: Boolean = true,
    val normalizeWeightClosings: Boolean = true,
    val autocompleteSource: AutocompleteSource = AutocompleteSource.BOTH,
    val imageSaveTreeUri: String? = null,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
)

@Serializable enum class AutocompleteSource { NOVEL_AI, DANBOORU, BOTH }
@Serializable enum class AppearanceMode { SYSTEM, LIGHT, DARK }
