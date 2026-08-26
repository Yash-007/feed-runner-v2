package com.yash.feedrunner.data

import android.content.Context

/**
 * Where the Idea Bank backend lives.
 *
 * Defaults to the deployed service, which is the answer almost always. It stays
 * editable because the alternative is a laptop whose LAN address changes with the
 * network, and pointing at one is how you work on the backend.
 */
class BackendConfig(context: Context) {

    private val prefs = context.getSharedPreferences("backend", Context.MODE_PRIVATE)

    /** Normalised base URL with no trailing slash. Never empty. */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "").orEmpty().ifEmpty { DEFAULT_BASE_URL }
        set(value) {
            // Clearing the field returns you to the deployed service rather than
            // to a broken app with nowhere to talk to.
            val normalised = normalise(value)
            prefs.edit().putString(KEY_BASE_URL, normalised).apply()
        }

    /** True since there is always a default. Kept so callers read as intended. */
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

    /**
     * The signed-in account's session token. Empty when signed out, in which
     * case requests fall back to the legacy shared token. Never expires; only
     * signing out clears it.
     */
    var authToken: String
        get() = prefs.getString(KEY_AUTH_TOKEN, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()
        }

    /** Display name of the signed-in account; for the UI only. */
    var accountName: String
        get() = prefs.getString(KEY_ACCOUNT_NAME, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_ACCOUNT_NAME, value).apply()
        }

    var accountUsername: String
        get() = prefs.getString(KEY_ACCOUNT_USERNAME, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_ACCOUNT_USERNAME, value).apply()
        }

    val isLoggedIn: Boolean get() = authToken.isNotEmpty()

    fun signOut() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_ACCOUNT_USERNAME)
            .apply()
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
        /** The deployed Idea Bank. Public URL, not a secret; the token is the secret. */
        const val DEFAULT_BASE_URL = "https://feed-runner-backend.onrender.com"

        const val KEY_BASE_URL = "base_url"
        const val KEY_LAST_WORKING = "last_working"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_ACCOUNT_NAME = "account_name"
        const val KEY_ACCOUNT_USERNAME = "account_username"
    }
}
