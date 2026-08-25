package com.yash.feedrunner.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext

/** How the app decides between light and dark. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/**
 * The user's theme choice, process-global.
 *
 * Global rather than passed around because the overlay windows build their own
 * composition roots: a CompositionLocal set in the activity would never reach
 * them, and the panels are exactly where a forced dark theme matters most.
 */
object ThemePreference {

    val mode = mutableStateOf(ThemeMode.SYSTEM)

    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val saved = prefs(context).getString(KEY_MODE, null)
        mode.value = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun set(context: Context, value: ThemeMode) {
        mode.value = value
        prefs(context).edit().putString(KEY_MODE, value.name).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("theme", Context.MODE_PRIVATE)

    private const val KEY_MODE = "mode"
}

/** The one place "is it dark" is answered, preference first, system as default. */
@Composable
fun appInDarkTheme(): Boolean {
    ThemePreference.ensureLoaded(LocalContext.current)
    return when (ThemePreference.mode.value) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
