package com.jellyfinmusic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Jellyfin = Color(0xFF00A4DC)
private val JellyfinDeep = Color(0xFF7B2FF7)

private val DarkColors = darkColorScheme(
    primary = Jellyfin,
    secondary = JellyfinDeep,
    background = Color(0xFF101014),
    surface = Color(0xFF17171D)
)

private val LightColors = lightColorScheme(
    primary = Jellyfin,
    secondary = JellyfinDeep
)

@Composable
fun JellyfinMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
