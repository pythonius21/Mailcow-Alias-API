package com.pythonius.mailcowapi.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView

private val FuturisticDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = BackgroundDark,
    primaryContainer = NeonCyan.copy(alpha = 0.15f),
    onPrimaryContainer = NeonCyan,
    secondary = NeonMagenta,
    onSecondary = BackgroundDark,
    secondaryContainer = NeonMagenta.copy(alpha = 0.15f),
    onSecondaryContainer = NeonMagenta,
    tertiary = NeonPurple,
    onTertiary = Color.White,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = BackgroundSurface,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundCard,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    outlineVariant = Divider.copy(alpha = 0.5f),
)

@Composable
fun MailcowAPITheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FuturisticDarkColorScheme,
        typography = Typography,
        content = content
    )
}
