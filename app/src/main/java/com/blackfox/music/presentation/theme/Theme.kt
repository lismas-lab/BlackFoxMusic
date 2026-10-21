package com.blackfox.music.presentation.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.blackfox.music.data.local.AccentTheme
import com.blackfox.music.data.local.LocalAccentTheme

@Composable
fun BlackFoxTheme(
    accentTheme: AccentTheme = AccentTheme.FOX_ORANGE,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ObsidianBlack.toArgb()
            window.navigationBarColor = ObsidianBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    val colorScheme = darkColorScheme(
        primary = accentTheme.color,
        onPrimary = ObsidianBlack,
        secondary = accentTheme.lightColor,
        onSecondary = ObsidianBlack,
        background = ObsidianBlack,
        onBackground = TextPrimary,
        surface = ObsidianSurface,
        onSurface = TextPrimary,
        surfaceVariant = ObsidianCard,
        onSurfaceVariant = TextSecondary
    )

    CompositionLocalProvider(LocalAccentTheme provides accentTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
