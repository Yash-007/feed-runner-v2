package com.yash.feedrunner.data

import android.content.Context
import android.util.Log
import com.yash.feedrunner.ui.DayCount
import com.yash.feedrunner.ui.Streak
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the streak visible and current without the backend.
 *
 * Two problems this solves. The backend runs on a laptop whose address changes with
 * the network, so a fetch failure used to hide the card entirely: a habit tracker
 * that disappears when the server moves is worse than useless. And a reply copied
 * just now should move the number immediately rather than after the next refresh.
 *
 * So the last server answer is cached, and picks are counted locally as they happen.
 * The server stays authoritative whenever it answers.
 */
class StreakStore(context: Context) {

    private val prefs = context.getSharedPreferences("streak", Context.MODE_PRIVATE)

    /** Replaces the cache with a fresh server answer. */
    fun cache(streak: Streak) {
        val days = JSONArray().apply {
            streak.days.forEach { day ->
                put(JSONObject().apply {
                    put("date", day.date)
                    put("count", day.count)
                })
            }
        }
        val payload = JSONObject().apply {
            put("today", streak.today)
            put("current", streak.current)
            put("longest", streak.longest)
            put("total", streak.total)
            put("days", days)
        }
        prefs.edit()
            .putString(KEY_CACHE, payload.toString())
            // The server just told us the truth for today, so local counting can
            // restart from it rather than double counting.
            .putInt(localKey(today()), streak.today)
            .apply()
    }

    /**
     * The streak as the app should show it: the cached server answer, with today's
     * count raised to the local tally when picks have happened since the last fetch.
     */
    fun current(): Streak {
        val cached = readCache()
        val localToday = prefs.getInt(localKey(today()), 0)
        if (localToday <= cached.today) return cached

        // Today just became active, so the run is at least one longer than the
        // cached value, which was computed before this reply existed.
        val current = if (cached.today == 0) cached.current + 1 else cached.current
        return cached.copy(
            today = localToday,
            current = current,
            longest = maxOf(cached.longest, current),
            total = cached.total + (localToday - cached.today),
            days = cached.days.map { day ->
                if (day.date == today()) day.copy(count = localToday) else day
            },
        )
    }

    /** Counts a used draft (reply, post or quote) the moment it is copied. */
    fun recordUse() {
        val key = localKey(today())
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    /** Undoes that when the used marker is cleared again. */
    fun removeUse() {
        val key = localKey(today())
        val next = (prefs.getInt(key, 0) - 1).coerceAtLeast(0)
        prefs.edit().putInt(key, next).apply()
    }

    private fun readCache(): Streak {
        val raw = prefs.getString(KEY_CACHE, null) ?: return Streak()
        return runCatching {
            val json = JSONObject(raw)
            val array = json.optJSONArray("days")
            Streak(
                today = json.optInt("today"),
                current = json.optInt("current"),
                longest = json.optInt("longest"),
                total = json.optInt("total"),
                days = (0 until (array?.length() ?: 0)).mapNotNull { i ->
                    val item = array?.optJSONObject(i) ?: return@mapNotNull null
                    DayCount(item.optString("date"), item.optInt("count"))
                },
            )
        }.onFailure { Log.w(TAG, "Failed to read cached streak", it) }.getOrDefault(Streak())
    }

    private fun today(): String = DAY_FORMAT.format(Date())

    private fun localKey(date: String) = "local_$date"

    private companion object {
        const val TAG = "StreakStore"
        const val KEY_CACHE = "cache"

        /** Matches the backend's day boundaries, which are local too. */
        val DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
