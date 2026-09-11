package com.hjhsys.naiblockprompt.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.hjhsys.naiblockprompt.domain.model.AppSettings
import com.hjhsys.naiblockprompt.domain.model.AutocompleteSource
import com.hjhsys.naiblockprompt.domain.model.AppearanceMode
import com.hjhsys.naiblockprompt.domain.model.ColorHelperMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val historyLimit = intPreferencesKey("history_limit")
        val lastUsedSeed = longPreferencesKey("last_used_seed")
        val showFormatter = booleanPreferencesKey("show_formatter")
        val normalizeWeights = booleanPreferencesKey("normalize_weight_closings")
        val showTokenEstimates = booleanPreferencesKey("show_token_estimates")
        val useTextRendering = booleanPreferencesKey("use_text_rendering")
        val colorHelperMode = stringPreferencesKey("color_helper_mode")
        val favoriteColors = stringPreferencesKey("favorite_colors")
        val favoriteColorUsage = stringPreferencesKey("favorite_color_usage")
        val favoriteColorAddedAt = stringPreferencesKey("favorite_color_added_at")
        val recentColors = stringPreferencesKey("recent_colors")
        val autocompleteSource = stringPreferencesKey("autocomplete_source")
        val imageSaveTreeUri = stringPreferencesKey("image_save_tree_uri")
        val appearanceMode = stringPreferencesKey("appearance_mode")
        val bundledTagVersion = intPreferencesKey("bundled_tag_version")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            lastUsedSeed = prefs[Keys.lastUsedSeed],
            historyLimit = (prefs[Keys.historyLimit] ?: 20).coerceIn(1, 100),
            showFormatterActions = prefs[Keys.showFormatter] ?: true,
            normalizeWeightClosings = prefs[Keys.normalizeWeights] ?: true,
            showTokenEstimates = prefs[Keys.showTokenEstimates] ?: true,
            useTextRendering = prefs[Keys.useTextRendering] ?: false,
            colorHelperMode = prefs[Keys.colorHelperMode]?.let { runCatching { ColorHelperMode.valueOf(it) }.getOrNull() } ?: ColorHelperMode.OFF,
            favoriteColors = decodeColors(prefs[Keys.favoriteColors]),
            favoriteColorUsage = decodeColorUsage(prefs[Keys.favoriteColorUsage]),
            favoriteColorAddedAt = decodeColorDates(prefs[Keys.favoriteColorAddedAt]),
            recentColors = decodeColors(prefs[Keys.recentColors]),
            autocompleteSource = prefs[Keys.autocompleteSource]
                ?.let { runCatching { AutocompleteSource.valueOf(it) }.getOrNull() }
                ?: AutocompleteSource.BOTH,
            imageSaveTreeUri = prefs[Keys.imageSaveTreeUri],
            appearanceMode = prefs[Keys.appearanceMode]?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() } ?: AppearanceMode.SYSTEM,
        )
    }

    suspend fun setShowFormatter(value: Boolean) = context.settingsDataStore.edit { it[Keys.showFormatter] = value }
    suspend fun setLastUsedSeed(value: Long?) = context.settingsDataStore.edit {
        if (value == null) it.remove(Keys.lastUsedSeed) else it[Keys.lastUsedSeed] = value
    }
    suspend fun setNormalizeWeights(value: Boolean) = context.settingsDataStore.edit { it[Keys.normalizeWeights] = value }
    suspend fun setShowTokenEstimates(value: Boolean) = context.settingsDataStore.edit { it[Keys.showTokenEstimates] = value }
    suspend fun setUseTextRendering(value: Boolean) = context.settingsDataStore.edit { it[Keys.useTextRendering] = value }
    suspend fun setColorHelperMode(value: ColorHelperMode) = context.settingsDataStore.edit { it[Keys.colorHelperMode] = value.name }
    suspend fun setFavoriteColors(value: List<String>) = context.settingsDataStore.edit { it[Keys.favoriteColors] = value.distinct().joinToString(",") }
    suspend fun setFavoriteColorAddedAt(value: Map<String, Long>) = context.settingsDataStore.edit { prefs ->
        prefs[Keys.favoriteColorAddedAt] = value.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
    suspend fun recordFavoriteColorUsage(colors: Collection<String>) = context.settingsDataStore.edit { prefs ->
        val usage = decodeColorUsage(prefs[Keys.favoriteColorUsage]).toMutableMap()
        colors.forEach { usage[it] = (usage[it] ?: 0) + 1 }
        prefs[Keys.favoriteColorUsage] = usage.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
    suspend fun setRecentColors(value: List<String>) = context.settingsDataStore.edit { it[Keys.recentColors] = value.distinct().take(12).joinToString(",") }
    suspend fun setHistoryLimit(value: Int) = context.settingsDataStore.edit { it[Keys.historyLimit] = value.coerceIn(1, 100) }
    suspend fun setAutocompleteSource(value: AutocompleteSource) = context.settingsDataStore.edit { it[Keys.autocompleteSource] = value.name }
    suspend fun setAppearanceMode(value: AppearanceMode) = context.settingsDataStore.edit { it[Keys.appearanceMode] = value.name }
    suspend fun setImageSaveTreeUri(value: String?) = context.settingsDataStore.edit { prefs ->
        if (value == null) prefs.remove(Keys.imageSaveTreeUri) else prefs[Keys.imageSaveTreeUri] = value
    }
    suspend fun bundledTagVersion(): Int = context.settingsDataStore.data.map { it[Keys.bundledTagVersion] ?: 0 }.first()
    suspend fun setBundledTagVersion(value: Int) = context.settingsDataStore.edit { it[Keys.bundledTagVersion] = value }
    suspend fun current(): AppSettings = settings.first()
    suspend fun apply(settings: AppSettings) {
        setLastUsedSeed(settings.lastUsedSeed)
        setHistoryLimit(settings.historyLimit)
        setShowFormatter(settings.showFormatterActions)
        setNormalizeWeights(settings.normalizeWeightClosings)
        setShowTokenEstimates(settings.showTokenEstimates)
        setUseTextRendering(settings.useTextRendering)
        setColorHelperMode(settings.colorHelperMode)
        setFavoriteColors(settings.favoriteColors)
        context.settingsDataStore.edit { prefs -> prefs[Keys.favoriteColorUsage] = settings.favoriteColorUsage.entries.joinToString(";") { "${it.key}=${it.value}" } }
        setFavoriteColorAddedAt(settings.favoriteColorAddedAt)
        setRecentColors(settings.recentColors)
        setAutocompleteSource(settings.autocompleteSource)
        setAppearanceMode(settings.appearanceMode)
        setImageSaveTreeUri(settings.imageSaveTreeUri)
    }

    private fun decodeColors(value: String?): List<String> = value.orEmpty().split(',').map(String::trim).filter(String::isNotBlank)
    private fun decodeColorUsage(value: String?): Map<String, Int> = value.orEmpty().split(';').mapNotNull { entry ->
        val parts = entry.split('=', limit = 2)
        if (parts.size == 2) parts[1].toIntOrNull()?.let { parts[0] to it } else null
    }.toMap()
    private fun decodeColorDates(value: String?): Map<String, Long> = value.orEmpty().split(';').mapNotNull { entry ->
        val parts = entry.split('=', limit = 2)
        if (parts.size == 2) parts[1].toLongOrNull()?.let { parts[0] to it } else null
    }.toMap()
}
