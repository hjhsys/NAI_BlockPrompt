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
    val quickEditLongPressMillis: Int = QuickEditPressTiming.DEFAULT_LONG_PRESS_MILLIS,
    val quickEditRepeatIntervalMillis: Int = QuickEditPressTiming.DEFAULT_REPEAT_INTERVAL_MILLIS,
    val quickEditToolbarPlacement: QuickEditToolbarPlacement = QuickEditToolbarPlacement.BELOW_SELECTION,
    val alternateCharacterColors: Boolean = true,
    val showStickyPromptControls: Boolean = true,
    val promptEditorOverlayPlacement: PromptEditorOverlayPlacement = PromptEditorOverlayPlacement.DYNAMIC,
    val showExclusionConfirmationHelp: Boolean = true,
)

@Serializable enum class AutocompleteSource { NOVEL_AI, DANBOORU, BOTH }
@Serializable enum class AppearanceMode { SYSTEM, LIGHT, DARK }
@Serializable enum class ColorHelperMode { ALWAYS, WHILE_EDITING, OFF }
@Serializable enum class QuickEditToolbarPlacement { BELOW_SELECTION, BOTTOM }
@Serializable enum class PromptEditorOverlayPlacement { TOP, BOTTOM, DYNAMIC }

object QuickEditPressTiming {
    const val DEFAULT_LONG_PRESS_MILLIS = 450
    const val DEFAULT_REPEAT_INTERVAL_MILLIS = 250
    const val MIN_LONG_PRESS_MILLIS = 100
    const val MAX_LONG_PRESS_MILLIS = 3_000
    const val MIN_REPEAT_INTERVAL_MILLIS = 50
    const val MAX_REPEAT_INTERVAL_MILLIS = 2_000

    fun isValidLongPress(value: Int) = value in MIN_LONG_PRESS_MILLIS..MAX_LONG_PRESS_MILLIS
    fun isValidRepeatInterval(value: Int) = value in MIN_REPEAT_INTERVAL_MILLIS..MAX_REPEAT_INTERVAL_MILLIS
}
