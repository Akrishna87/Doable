package io.github.akrishna87.doable.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.akrishna87.doable.model.Accent
import io.github.akrishna87.doable.model.ThemeMode

/** Colours that aren't part of Material's scheme: due-date tones and swipe actions. */
@Immutable
data class Extra(
    val overdue: Color,
    val today: Color,
    val tomorrow: Color,
    val thisWeek: Color,
    val later: Color,
    val complete: Color,
    val delete: Color,
    val highlight: Color,
)

private val LightExtra = Extra(
    overdue = Color(0xFFD1453B),
    today = Color(0xFF058527),
    tomorrow = Color(0xFFAD6200),
    thisWeek = Color(0xFF692EC2),
    later = Color(0xFF6B6D7B),
    complete = Color(0xFF058527),
    delete = Color(0xFFD1453B),
    highlight = Color(0xFFE3E3FB),
)

private val DarkExtra = Extra(
    overdue = Color(0xFFFF7066),
    today = Color(0xFF4CC26E),
    tomorrow = Color(0xFFF2A33A),
    thisWeek = Color(0xFFB794F6),
    later = Color(0xFFA6A7B5),
    complete = Color(0xFF2F9E4F),
    delete = Color(0xFFD1453B),
    highlight = Color(0xFF34346E),
)

val LocalExtra = staticCompositionLocalOf { LightExtra }

private fun lightColors(a: Accent): androidx.compose.material3.ColorScheme {
    val k = LightKit
    val accent = Color(a.light)
    return lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = lerp(Color.White, accent, 0.12f),
        onPrimaryContainer = lerp(accent, Color.Black, 0.35f),
        secondary = k.sub,
        onSecondary = Color.White,
        secondaryContainer = k.chip,
        onSecondaryContainer = k.text,
        tertiary = Color(0xFF0B8463),
        background = k.bg,
        onBackground = k.text,
        surface = k.bg,
        onSurface = k.text,
        surfaceVariant = k.chip,
        onSurfaceVariant = k.sub,
        surfaceContainerLowest = k.card,
        surfaceContainerLow = k.card,
        surfaceContainer = k.card,
        surfaceContainerHigh = k.card,
        surfaceContainerHighest = k.chip,
        outline = Color(0xFFC9CAD6),
        outlineVariant = k.line,
        error = Color(0xFFD93036),
        onError = Color.White,
        inverseSurface = Color(0xFF26262F),
        inverseOnSurface = Color(0xFFF2F2F7),
        inversePrimary = Color(a.dark),
        scrim = Color(0xFF0A0A14),
    )
}

private fun darkColors(a: Accent): androidx.compose.material3.ColorScheme {
    val k = DarkKit
    val accent = Color(a.dark)
    return darkColorScheme(
        primary = accent,
        onPrimary = lerp(Color(a.light), Color.Black, 0.65f),
        primaryContainer = lerp(k.card, Color(a.light), 0.32f),
        onPrimaryContainer = lerp(Color.White, accent, 0.25f),
        secondary = k.sub,
        onSecondary = k.bg,
        secondaryContainer = k.chip,
        onSecondaryContainer = k.text,
        tertiary = Color(0xFF3DD6A6),
        background = k.bg,
        onBackground = k.text,
        surface = k.bg,
        onSurface = k.text,
        surfaceVariant = k.chip,
        onSurfaceVariant = k.sub,
        surfaceContainerLowest = k.card,
        surfaceContainerLow = k.card,
        surfaceContainer = k.card,
        surfaceContainerHigh = Color(0xFF22232B),
        surfaceContainerHighest = k.chip,
        outline = Color(0xFF4A4B57),
        outlineVariant = k.line,
        error = Color(0xFFFF6B6F),
        onError = Color(0xFF3A0905),
        inverseSurface = Color(0xFFE8E8EE),
        inverseOnSurface = Color(0xFF1C1C21),
        inversePrimary = Color(a.light),
        scrim = Color.Black,
    )
}

private val AppTypography = kitTypography()

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun DoableTheme(dark: Boolean, accent: Accent = Accent.INDIGO, content: @Composable () -> Unit) {
    val extra = if (dark) DarkExtra.copy(highlight = lerp(Color(0xFF121216), Color(accent.light), 0.4f))
    else LightExtra.copy(highlight = lerp(Color.White, Color(accent.light), 0.14f))
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides extra, LocalKit provides if (dark) DarkKit else LightKit) {
        MaterialTheme(colorScheme = if (dark) darkColors(accent) else lightColors(accent), typography = AppTypography, shapes = KitShapes, content = content)
    }
}
