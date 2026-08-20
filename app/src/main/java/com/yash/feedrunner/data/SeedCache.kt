package com.yash.feedrunner.data

import android.content.Context
import com.yash.feedrunner.ui.SeedStatus

/**
 * The last seed list the server gave us, kept so the Ideas screen is worth opening
 * away from the laptop.
 *
 * Without it the screen went empty the moment the backend was unreachable, showing
 * only seeds that had not been sent yet: the bank looked lost rather than offline.
 * The streak is cached for the same reason.
 *
 * Stored as the server's own JSON so there is one parser rather than two, and a
 * stale cache can only ever be read by the same code that reads a live response.
 */
class SeedCache(context: Context) {

    private val prefs = context.getSharedPreferences("seed_cache", Context.MODE_PRIVATE)

    fun save(status: SeedStatus?, json: String) {
        prefs.edit().putString(key(status), json).apply()
    }

    fun load(status: SeedStatus?): String? = prefs.getString(key(status), null)

    /** One entry per filter, since each is a different answer. */
    private fun key(status: SeedStatus?) = "seeds_${status?.wire ?: "all"}"
}
