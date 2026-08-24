package com.yash.feedrunner.ui

import androidx.compose.ui.graphics.Color

/**
 * Which network a capture, a result, or a seed belongs to.
 *
 * The flows are identical on both; what differs is the prompt behind them and
 * the vocabulary on screen, so this enum is deliberately small: a wire name for
 * the backend, a label, and a brand hue for the chips.
 */
enum class Platform(val wire: String, val label: String, val hue: Color) {
    X("x", "X", Color(0xFF657786)),
    LINKEDIN("linkedin", "LinkedIn", Color(0xFF0A66C2));

    companion object {
        /** Absent or unknown means X: everything stored before LinkedIn existed. */
        fun fromWire(wire: String?): Platform =
            entries.firstOrNull { it.wire == wire } ?: X

        /**
         * Maps the app under the bubble to a platform, or null when it is
         * neither, in which case the caller falls back to the remembered choice.
         */
        fun fromPackage(packageName: String?): Platform? = when {
            packageName == null -> null
            "linkedin" in packageName -> LINKEDIN
            packageName == "com.twitter.android" || packageName.startsWith("com.x.android") -> X
            else -> null
        }
    }
}
