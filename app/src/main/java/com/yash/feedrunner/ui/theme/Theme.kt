package com.yash.feedrunner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
    background = Color(0xFFFCFBFE),
    // Near black rather than soft grey: headings are meant to be the darkest thing
    // on screen, with everything else stepping back from them.
    onBackground = Color(0xFF14101B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14101B),
    surfaceVariant = Color(0xFFF3F0FA),
    // Body text sits well back from the headings, and carries a little of the
    // violet so the greys never read as dirty.
    onSurfaceVariant = Color(0xFF5A5470),
    outline = Color(0xFF9A93AE),
    // The hairline. Almost the whole layout is built from this one colour, so it
    // has to be visible on white and never assertive.
    outlineVariant = Color(0xFFE4DEF0),
    error = Color(0xFFB4232B),
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
    background = Color(0xFF0D0A12),
    onBackground = Color(0xFFF1EEF8),
    surface = Color(0xFF141019),
    onSurface = Color(0xFFF1EEF8),
    surfaceVariant = Color(0xFF221C2E),
    onSurfaceVariant = Color(0xFFAEA6C2),
    outline = Color(0xFF8B83A0),
    outlineVariant = Color(0xFF322B41),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

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
internal val MetaTextStyle = TextStyle(
    fontFamily = Figtree,
    fontSize = 10.sp,
    lineHeight = 13.sp,
)
