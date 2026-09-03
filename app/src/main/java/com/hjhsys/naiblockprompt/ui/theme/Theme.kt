package com.hjhsys.naiblockprompt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppDarkColors = darkColorScheme(
    background = Color(0xFF151219),
    surface = Color(0xFF1B171F),
    surfaceVariant = Color(0xFF332C38),
    primary = Color(0xFFD0BCFF),
)

@Composable
fun NaiBlockPromptTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AppDarkColors else lightColorScheme(),
        content = content,
    )
}
