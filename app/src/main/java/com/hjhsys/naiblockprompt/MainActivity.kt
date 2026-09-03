package com.hjhsys.naiblockprompt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hjhsys.naiblockprompt.ui.NaiBlockPromptApp
import com.hjhsys.naiblockprompt.ui.theme.NaiBlockPromptTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import com.hjhsys.naiblockprompt.domain.model.AppearanceMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NaiBlockPromptApplication).container
        setContent {
            val settings by container.settingsRepository.settings.collectAsState(initial = com.hjhsys.naiblockprompt.domain.model.AppSettings())
            val dark = when (settings.appearanceMode) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            NaiBlockPromptTheme(darkTheme = dark) {
                NaiBlockPromptApp(container)
            }
        }
    }
}
