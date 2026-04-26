package com.kakeibo.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = SkyAccent,
    onPrimary = LightBg1,
    secondary = Info,
    onSecondary = LightBg1,
    background = LightBg0,
    onBackground = LightText1,
    surface = LightBg1,
    onSurface = LightText1,
    surfaceVariant = LightBg2,
    onSurfaceVariant = LightText2,
    surfaceContainer = LightBg2,
    surfaceContainerHigh = LightBg3,
    error = Negative,
    outline = LightText4,
)

private val DarkColors = darkColorScheme(
    primary = SkyAccentDark,
    onPrimary = DarkBg0,
    secondary = InfoDark,
    onSecondary = DarkBg0,
    background = DarkBg0,
    onBackground = DarkText1,
    surface = DarkBg1,
    onSurface = DarkText1,
    surfaceVariant = DarkBg2,
    onSurfaceVariant = DarkText2,
    surfaceContainer = DarkBg2,
    surfaceContainerHigh = DarkBg3,
    error = NegativeDark,
    outline = DarkText4,
)

private val CashflowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun CashflowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CashflowTypography,
            shapes = CashflowShapes,
            content = content,
        )
    }
}
