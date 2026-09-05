package com.hjhsys.naiblockprompt

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hjhsys.naiblockprompt.ui.NaiBlockPromptApp
import com.hjhsys.naiblockprompt.ui.theme.NaiBlockPromptTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import com.hjhsys.naiblockprompt.domain.model.AppearanceMode

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val startupAppearance = runCatching {
            AppearanceMode.valueOf(
                getSharedPreferences(STARTUP_THEME_PREFS, MODE_PRIVATE)
                    .getString(STARTUP_THEME_KEY, AppearanceMode.SYSTEM.name)
                    ?: AppearanceMode.SYSTEM.name,
            )
        }.getOrDefault(AppearanceMode.SYSTEM)
        applyNightMode(startupAppearance)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NaiBlockPromptApplication).container
        setContent {
            val loadedSettings by container.settingsRepository.settings.collectAsState(initial = null)
            val appearanceMode = loadedSettings?.appearanceMode ?: startupAppearance
            val dark = when (appearanceMode) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            SideEffect {
                loadedSettings?.appearanceMode?.let { loadedMode ->
                    val preferences = getSharedPreferences(STARTUP_THEME_PREFS, MODE_PRIVATE)
                    if (preferences.getString(STARTUP_THEME_KEY, null) != loadedMode.name) {
                        // Persist synchronously before AppCompat recreates the Activity.
                        preferences.edit().putString(STARTUP_THEME_KEY, loadedMode.name).commit()
                    }
                    applyNightMode(loadedMode)
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            NaiBlockPromptTheme(darkTheme = dark) {
                NaiBlockPromptApp(container)
            }
        }
    }

    private fun applyNightMode(mode: AppearanceMode) {
        val delegateMode = when (mode) {
            AppearanceMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppearanceMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppearanceMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != delegateMode) {
            AppCompatDelegate.setDefaultNightMode(delegateMode)
        }
    }

    private companion object {
        const val STARTUP_THEME_PREFS = "startup_theme"
        const val STARTUP_THEME_KEY = "appearance_mode"
    }
}
