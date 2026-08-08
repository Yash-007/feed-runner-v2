package com.yash.feedrunner.data

import android.content.Context

/**
 * Tracks which results have been looked at, as a high-water mark rather than a
 * set: anything saved after [lastViewedAt] counts as unread.
 *
 * A watermark is the right shape here because result ids are timestamps and only
 * ever increase. It also gives the behaviour you want for the history strip:
 * opening the newest result clears everything, while going back to an older one
 * cannot resurrect unread badges for results you have already seen.
 */
class ReadState(context: Context) {

    private val prefs = context.getSharedPreferences("feed_runner", Context.MODE_PRIVATE)

    val lastViewedAt: Long get() = prefs.getLong(KEY, 0L)

    fun markViewed(savedAtMillis: Long) {
        if (savedAtMillis <= lastViewedAt) return
        prefs.edit().putLong(KEY, savedAtMillis).apply()
    }

    private companion object {
        const val KEY = "last_viewed_result_at"
    }
}
