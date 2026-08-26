package com.warvis.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val WarvisColorScheme = lightColorScheme(
    primary = WarvisBlue,
    secondary = WarvisGreen,
    tertiary = WarvisAmber,
    background = WarvisSurface,
    surface = WarvisSurface,
    error = WarvisRed,
)

@Composable
fun WarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WarvisColorScheme,
        typography = WarvisTypography,
        content = content,
    )
}
