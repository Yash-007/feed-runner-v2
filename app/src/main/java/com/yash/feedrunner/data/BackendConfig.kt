package com.yash.feedrunner.data

import android.content.Context

/**
 * Where the Idea Bank backend lives.
 *
 * Editable at runtime rather than baked in, because the server runs on a laptop
 * whose LAN address changes with the network. The server prints the address to
 * type in here on startup.
 */
class BackendConfig(context: Context) {

    private val prefs = context.getSharedPreferences("backend", Context.MODE_PRIVATE)

    /** Normalised base URL with no trailing slash, or empty when unset. */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_BASE_URL, normalise(value)).apply()
        }

    val isConfigured: Boolean get() = baseUrl.isNotEmpty()

    /**
     * The address that answered last, tried first next time.
     *
     * Without this, every request pays the full connect timeout against a stale LAN
     * address before falling back to the USB tunnel, which made the whole app feel
     * broken rather than merely offline.
     */
    var lastWorking: String
        get() = prefs.getString(KEY_LAST_WORKING, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LAST_WORKING, value).apply()
        }

    private fun normalise(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        // A bare host:port is what you get from the server log, so accept it.
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_LAST_WORKING = "last_working"
    }
}
