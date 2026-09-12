package com.hjhsys.naiblockprompt.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val historyLimit: Int = 20,
    val showFormatterActions: Boolean = true,
    val normalizeWeightClosings: Boolean = true,
    val showTokenEstimates: Boolean = true,
    val useTextRendering: Boolean = false,
    val colorHelperMode: ColorHelperMode = ColorHelperMode.OFF,
    val favoriteColors: List<String> = emptyList(),
    val favoriteColorUsage: Map<String, Int> = emptyMap(),
    val favoriteColorAddedAt: Map<String, Long> = emptyMap(),
    val recentColors: List<String> = emptyList(),
    val autocompleteSource: AutocompleteSource = AutocompleteSource.BOTH,
    val imageSaveTreeUri: String? = null,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val lastUsedSeed: Long? = null,
    val quickEditWeightStep: String = "0.1",
    val showExclusionConfirmationHelp: Boolean = true,
)

@Serializable enum class AutocompleteSource { NOVEL_AI, DANBOORU, BOTH }
@Serializable enum class AppearanceMode { SYSTEM, LIGHT, DARK }
@Serializable enum class ColorHelperMode { ALWAYS, WHILE_EDITING, OFF }
