package com.example.eduappchatbot.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = TextOnPrimary,
    background = BackgroundPrimary,
    onBackground = TextPrimary,
    surface = BackgroundSecondary,
    onSurface = TextPrimary,
    error = ColorError,
    onError = TextOnPrimary
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = TextOnPrimary,
    background = Black,
    onBackground = TextOnPrimary,
    surface = Black,
    onSurface = TextOnPrimary,
    error = ColorError,
    onError = TextOnPrimary
)

@Composable
fun EduAppChatBotTheme(
    darkTheme: Boolean =  isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // the theme background so status bar matches app background
            window.statusBarColor = colorScheme.background.toArgb()
            //  dark icons on light backgrounds, light icons on dark backgrounds
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}