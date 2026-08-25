package com.yash.feedrunner.data

import android.content.Context

/**
 * The word caps behind the length sliders, one per surface.
 *
 * Replies and posts want different ceilings — a 40-word reply is long, a
 * 40-word post is terse — so they are stored apart. Zero means no cap, which
 * is the default: the prompts already know each platform's natural lengths,
 * and the slider exists for the days you want them shorter than that.
 */
class WordLimitStore(context: Context) {

    private val prefs = context.getSharedPreferences("word_limit", Context.MODE_PRIVATE)

    /** Cap for reply drafts, refinements and reply chat. 0 = no cap. */
    var replyLimit: Int
        get() = prefs.getInt(KEY_REPLY, 0)
        set(value) = prefs.edit().putInt(KEY_REPLY, value.coerceAtLeast(0)).apply()

    /** Cap for post and quote drafts and their chat. 0 = no cap. */
    var postLimit: Int
        get() = prefs.getInt(KEY_POST, 0)
        set(value) = prefs.edit().putInt(KEY_POST, value.coerceAtLeast(0)).apply()

    private companion object {
        const val KEY_REPLY = "reply"
        const val KEY_POST = "post"
    }
}
