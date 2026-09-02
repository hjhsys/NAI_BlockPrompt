package com.hjhsys.naiblockprompt.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.hjhsys.naiblockprompt.domain.model.AppSettings
import com.hjhsys.naiblockprompt.domain.model.AutocompleteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val historyLimit = intPreferencesKey("history_limit")
        val showFormatter = booleanPreferencesKey("show_formatter")
        val normalizeWeights = booleanPreferencesKey("normalize_weight_closings")
        val autocompleteSource = stringPreferencesKey("autocomplete_source")
        val imageSaveTreeUri = stringPreferencesKey("image_save_tree_uri")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            historyLimit = (prefs[Keys.historyLimit] ?: 20).coerceIn(1, 100),
            showFormatterActions = prefs[Keys.showFormatter] ?: true,
            normalizeWeightClosings = prefs[Keys.normalizeWeights] ?: true,
            autocompleteSource = prefs[Keys.autocompleteSource]
                ?.let { runCatching { AutocompleteSource.valueOf(it) }.getOrNull() }
                ?: AutocompleteSource.BOTH,
            imageSaveTreeUri = prefs[Keys.imageSaveTreeUri],
        )
    }

    suspend fun setShowFormatter(value: Boolean) = context.settingsDataStore.edit { it[Keys.showFormatter] = value }
    suspend fun setNormalizeWeights(value: Boolean) = context.settingsDataStore.edit { it[Keys.normalizeWeights] = value }
    suspend fun setHistoryLimit(value: Int) = context.settingsDataStore.edit { it[Keys.historyLimit] = value.coerceIn(1, 100) }
    suspend fun setAutocompleteSource(value: AutocompleteSource) = context.settingsDataStore.edit { it[Keys.autocompleteSource] = value.name }
}
