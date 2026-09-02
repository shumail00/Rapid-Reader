package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.engine.ReadingThemeMode

val LightColorScheme = lightColorScheme(
    primary = MdLightPrimary,
    onPrimary = MdLightOnPrimary,
    primaryContainer = MdLightPrimaryContainer,
    onPrimaryContainer = MdLightOnPrimaryContainer,
    secondary = MdLightSecondary,
    onSecondary = MdLightOnSecondary,
    secondaryContainer = MdLightSecondaryContainer,
    onSecondaryContainer = MdLightOnSecondaryContainer,
    tertiary = MdLightTertiary,
    onTertiary = MdLightOnTertiary,
    tertiaryContainer = MdLightTertiaryContainer,
    onTertiaryContainer = MdLightOnTertiaryContainer,
    background = MdLightBackground,
    onBackground = MdLightOnBackground,
    surface = MdLightSurface,
    onSurface = MdLightOnSurface,
    surfaceVariant = MdLightSurfaceVariant,
    onSurfaceVariant = MdLightOnSurfaceVariant,
    outline = MdLightOutline
)

val DarkColorScheme = darkColorScheme(
    primary = MdDarkPrimary,
    onPrimary = MdDarkOnPrimary,
    primaryContainer = MdDarkPrimaryContainer,
    onPrimaryContainer = MdDarkOnPrimaryContainer,
    secondary = MdDarkSecondary,
    onSecondary = MdDarkOnSecondary,
    secondaryContainer = MdDarkSecondaryContainer,
    onSecondaryContainer = MdDarkOnSecondaryContainer,
    tertiary = MdDarkTertiary,
    onTertiary = MdDarkOnTertiary,
    tertiaryContainer = MdDarkTertiaryContainer,
    onTertiaryContainer = MdDarkOnTertiaryContainer,
    background = MdDarkBackground,
    onBackground = MdDarkOnBackground,
    surface = MdDarkSurface,
    onSurface = MdDarkOnSurface,
    surfaceVariant = MdDarkSurfaceVariant,
    onSurfaceVariant = MdDarkOnSurfaceVariant,
    outline = MdDarkOutline
)

@Composable
fun RsvpAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

data class RsvpCanvasPalette(
    val backgroundColor: androidx.compose.ui.graphics.Color,
    val surfaceColor: androidx.compose.ui.graphics.Color,
    val textColor: androidx.compose.ui.graphics.Color,
    val mutedTextColor: androidx.compose.ui.graphics.Color,
    val guideColor: androidx.compose.ui.graphics.Color,
    val isDark: Boolean
)

@Composable
fun getRsvpCanvasPalette(themeMode: ReadingThemeMode, systemDark: Boolean): RsvpCanvasPalette {
    val m3Scheme = MaterialTheme.colorScheme

    return when (themeMode) {
        ReadingThemeMode.DYNAMIC -> {
            RsvpCanvasPalette(
                backgroundColor = m3Scheme.background,
                surfaceColor = m3Scheme.surfaceVariant,
                textColor = m3Scheme.onBackground,
                mutedTextColor = m3Scheme.onSurfaceVariant.copy(alpha = 0.6f),
                guideColor = m3Scheme.outline.copy(alpha = 0.35f),
                isDark = systemDark
            )
        }
        ReadingThemeMode.OLED_DARK -> {
            RsvpCanvasPalette(
                backgroundColor = OledBackground,
                surfaceColor = OledSurface,
                textColor = OledText,
                mutedTextColor = OledMutedText,
                guideColor = androidx.compose.ui.graphics.Color(0xFF333333),
                isDark = true
            )
        }
        ReadingThemeMode.WARM_SEPIA -> {
            RsvpCanvasPalette(
                backgroundColor = SepiaBackground,
                surfaceColor = SepiaSurface,
                textColor = SepiaText,
                mutedTextColor = SepiaMutedText,
                guideColor = androidx.compose.ui.graphics.Color(0xFFD4C1A5),
                isDark = false
            )
        }
        ReadingThemeMode.MINT_FOCUS -> {
            RsvpCanvasPalette(
                backgroundColor = MintBackground,
                surfaceColor = MintSurface,
                textColor = MintText,
                mutedTextColor = MintMutedText,
                guideColor = androidx.compose.ui.graphics.Color(0xFF264E3D),
                isDark = true
            )
        }
        ReadingThemeMode.SOLARIZED_DARK -> {
            RsvpCanvasPalette(
                backgroundColor = SolarizedBackground,
                surfaceColor = SolarizedSurface,
                textColor = SolarizedText,
                mutedTextColor = SolarizedMutedText,
                guideColor = androidx.compose.ui.graphics.Color(0xFF073642),
                isDark = true
            )
        }
    }
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    RsvpAppTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
