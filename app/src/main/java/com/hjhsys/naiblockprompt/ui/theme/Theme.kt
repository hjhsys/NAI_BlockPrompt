package com.hjhsys.naiblockprompt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppLightColors = lightColorScheme(
    primary = Color(0xFF6842A0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8D7FF),
    onPrimaryContainer = Color(0xFF281047),
    secondary = Color(0xFF675079),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBDDF4),
    onSecondaryContainer = Color(0xFF271831),
    background = Color(0xFFF8F7FA),
    onBackground = Color(0xFF1C1A1F),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1C1A1F),
    surfaceVariant = Color(0xFFE7E1E9),
    onSurfaceVariant = Color(0xFF49434D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F0F6),
    surfaceContainer = Color(0xFFEEE9F1),
    surfaceContainerHigh = Color(0xFFE8E2EB),
    surfaceContainerHighest = Color(0xFFE1DAE5),
    outline = Color(0xFF7A717E),
    outlineVariant = Color(0xFFCBC2CF),
)

private val AppDarkColors = darkColorScheme(
    primary = Color(0xFFAA7CF5),
    onPrimary = Color(0xFF2D0759),
    primaryContainer = Color(0xFF50307E),
    onPrimaryContainer = Color(0xFFECDFFF),
    secondary = Color(0xFFB78DD8),
    onSecondary = Color(0xFF321442),
    secondaryContainer = Color(0xFF452A58),
    onSecondaryContainer = Color(0xFFF0D9FF),
    background = Color(0xFF0C0910),
    onBackground = Color(0xFFEAE3ED),
    surface = Color(0xFF120E16),
    onSurface = Color(0xFFEAE3ED),
    surfaceVariant = Color(0xFF2B2133),
    onSurfaceVariant = Color(0xFFD0C3D5),
    surfaceContainerLowest = Color(0xFF0A080C),
    surfaceContainerLow = Color(0xFF140F19),
    surfaceContainer = Color(0xFF19131F),
    surfaceContainerHigh = Color(0xFF201727),
    surfaceContainerHighest = Color(0xFF291E32),
    outline = Color(0xFFA497AA),
    outlineVariant = Color(0xFF514557),
)

@Composable
fun NaiBlockPromptTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AppDarkColors else AppLightColors,
        content = content,
    )
}
