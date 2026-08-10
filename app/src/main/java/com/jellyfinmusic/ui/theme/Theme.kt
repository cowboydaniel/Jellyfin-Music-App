package com.jellyfinmusic.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A dark-only palette. YouTube Music has no meaningful light mode on mobile and
 * its whole visual language — white type and artwork glowing off a near-black
 * canvas — depends on staying dark, so the app does not follow the system theme.
 */
object AppColors {
    val Background = Color(0xFF0F0F0F)
    val Surface = Color(0xFF1F1F1F)
    val SurfaceVariant = Color(0xFF282828)
    val Elevated = Color(0xFF2A2A2A)
    val OnBackground = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFAAAAAA)
    val Accent = Color(0xFFFF0033)
    val Chip = Color(0xFF272727)
    val ChipSelected = Color(0xFFFFFFFF)
}

private val Scheme = darkColorScheme(
    primary = AppColors.OnBackground,
    onPrimary = AppColors.Background,
    secondary = AppColors.Accent,
    onSecondary = AppColors.OnBackground,
    background = AppColors.Background,
    onBackground = AppColors.OnBackground,
    surface = AppColors.Background,
    onSurface = AppColors.OnBackground,
    surfaceVariant = AppColors.SurfaceVariant,
    onSurfaceVariant = AppColors.Secondary,
    surfaceContainer = AppColors.Surface,
    surfaceContainerHigh = AppColors.Elevated,
    outline = Color(0xFF3F3F3F),
    error = Color(0xFFFF6B6B)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp)
)

@Composable
fun JellyfinMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography
    ) {
        // Text and Icon fall back to LocalContentColor, which defaults to black
        // outside a Surface. Screens drawn over the Scaffold — the full player —
        // would otherwise render every uncoloured label in black on black.
        CompositionLocalProvider(
            LocalContentColor provides AppColors.OnBackground,
            content = content
        )
    }
}
