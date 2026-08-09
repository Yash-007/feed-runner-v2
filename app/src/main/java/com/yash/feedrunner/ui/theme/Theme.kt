package com.yash.feedrunner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One theme for the whole app, including the overlay windows.
 *
 * The overlays sit on top of X, so a fixed light panel is a flashbang when you are
 * scrolling at night. Following the system means the sheet matches whatever X is
 * already doing.
 *
 * The violet primary is the bubble's own colour, so the panel reads as belonging
 * to the thing you tapped.
 */
private val Violet = Color(0xFF6B4EFF)
private val VioletLight = Color(0xFFB9A8FF)
private val SkyBlue = Color(0xFF1D9BF0)

private val LightScheme = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1F0C63),
    secondary = SkyBlue,
    onSecondary = Color.White,
    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF17131F),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF17131F),
    surfaceVariant = Color(0xFFEBE6F7),
    onSurfaceVariant = Color(0xFF5B5570),
    outline = Color(0xFF7C7590),
    outlineVariant = Color(0xFFDCD5EC),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = VioletLight,
    // Dark themes flip the pairing: text on a light violet button must be dark.
    onPrimary = Color(0xFF23106B),
    primaryContainer = Color(0xFF3A2A8C),
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Color(0xFF64C4FF),
    onSecondary = Color(0xFF00344F),
    background = Color(0xFF0E0B14),
    onBackground = Color(0xFFE8E4F2),
    surface = Color(0xFF15111D),
    onSurface = Color(0xFFE8E4F2),
    surfaceVariant = Color(0xFF272132),
    onSurfaceVariant = Color(0xFFB6AEC9),
    outline = Color(0xFF8B83A0),
    outlineVariant = Color(0xFF3A3348),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/**
 * Slightly looser line height than the default on body styles: drafts are read at
 * a glance while holding the phone in one hand, and the stock 20sp on 14sp text
 * runs tight for multi-line replies.
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(lineHeight = 25.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    )
}

/** Colours that carry meaning and so stay recognisable in both themes. */
object Accent {
    val capture = SkyBlue
    val hold = Color(0xFF7856FF)
    val repost = Color(0xFF00B8D9)
    val lastResult = Color(0xFF00BA7C)
    val warning = Color(0xFFF5A623)
}

@Composable
fun FeedRunnerTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}

/** Text style helper for the small meta lines used across the panels. */
internal val MetaTextStyle = TextStyle(fontSize = 10.sp, lineHeight = 13.sp)
