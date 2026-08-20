package com.yash.feedrunner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.yash.feedrunner.R

/**
 * A serif for the headings, a sans for everything else.
 *
 * That split is deliberate and it is where most of the app's look comes from: a
 * screen title in Fraunces reads as something written, and the drafts underneath
 * stay in a plain sans because they are text you are about to paste into X.
 *
 * Card titles stay sans too. Serif is for the few big headings only; using it for
 * every heading-shaped thing turns a voice into a costume.
 *
 * Both files are variable fonts, so one file per family covers every weight.
 */

/** Fraunces has a softness axis. A little rounds off the serifs; none looks stern. */
private const val SOFTNESS = 30f

/** Its wonk axis swaps in quirkier shapes. Off: we want warm, not novelty. */
private const val WONK = 0f

/**
 * Optical size is a real axis here, so it is set per style rather than left at the
 * default. Larger text gets finer strokes and tighter spacing, which is the whole
 * point of having the axis.
 */
@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: Int, opticalSize: TextUnit) = FontFamily(
    Font(
        resId = R.font.fraunces,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.opticalSizing(opticalSize),
            FontVariation.Setting("SOFT", SOFTNESS),
            FontVariation.Setting("WONK", WONK),
        ),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun figtreeWeight(weight: Int) = Font(
    resId = R.font.figtree,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

internal val Figtree = FontFamily(
    figtreeWeight(400),
    figtreeWeight(500),
    figtreeWeight(600),
    figtreeWeight(700),
)

internal val AppTypography: Typography = Typography().let { base ->
    base.copy(
        // Serif, the big headings only.
        displayLarge = base.displayLarge.copy(
            fontFamily = fraunces(700, 60.sp),
            letterSpacing = (-1).sp,
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = fraunces(700, 45.sp),
            letterSpacing = (-0.8).sp,
        ),
        displaySmall = base.displaySmall.copy(
            fontFamily = fraunces(700, 36.sp),
            letterSpacing = (-0.6).sp,
        ),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = fraunces(700, 32.sp),
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = fraunces(700, 28.sp),
            letterSpacing = (-0.4).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = fraunces(700, 24.sp),
            letterSpacing = (-0.3).sp,
        ),

        // Sans, everything you actually read or act on.
        titleLarge = base.titleLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        // Looser line height than stock: drafts are read at a glance, one handed,
        // and 20sp on 14sp text runs tight once a reply wraps.
        bodyLarge = base.bodyLarge.copy(fontFamily = Figtree, lineHeight = 25.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = Figtree, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontFamily = Figtree, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = Figtree, fontWeight = FontWeight.Medium),
    )
}
