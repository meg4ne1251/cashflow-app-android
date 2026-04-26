package com.kakeibo.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors not covered by Material 3's ColorScheme. Mirrors `--pos / --neg /
 * --warn / --info` from web/src/theme/global.css.
 */
@Immutable
data class ExtendedColors(
    val positive: Color,
    val negative: Color,
    val warn: Color,
    val info: Color,
)

val LightExtendedColors = ExtendedColors(
    positive = Positive,
    negative = Negative,
    warn = Warn,
    info = Info,
)

val DarkExtendedColors = ExtendedColors(
    positive = PositiveDark,
    negative = NegativeDark,
    warn = WarnDark,
    info = InfoDark,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
