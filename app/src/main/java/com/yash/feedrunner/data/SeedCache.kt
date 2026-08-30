package com.yash.feedrunner.data

import android.content.Context
import com.yash.feedrunner.ui.SeedLane
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

    fun save(status: SeedStatus?, lane: SeedLane, json: String) {
        prefs.edit().putString(key(status, lane), json).apply()
    }

    fun load(status: SeedStatus?, lane: SeedLane): String? =
        prefs.getString(key(status, lane), null)

    /**
     * One entry per filter combination, since each is a different answer.
     *
     * The lane is part of the key because it is applied server-side: caching
     * the harvested lane under the same key as the whole bank would make an
     * offline "All" show only what the engine found.
     */
    private fun key(status: SeedStatus?, lane: SeedLane) =
        "seeds_${status?.wire ?: "all"}_${lane.wire ?: "all"}"
}
