package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IronPulseDarkColorScheme = darkColorScheme(
    primary = KineticVolt,
    onPrimary = ObsidianBg,
    primaryContainer = SlateCard,
    onPrimaryContainer = KineticVolt,
    secondary = TelemetryCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = SlateCard,
    onSecondaryContainer = TelemetryCyan,
    tertiary = StatusApproved,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = GraphiteSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = StatusRejected,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IronPulseDarkColorScheme,
        typography = Typography,
        content = content
    )
}
