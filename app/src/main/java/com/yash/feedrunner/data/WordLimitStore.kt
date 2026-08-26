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

    /**
     * Cap for the draft cards: the first-capture batch, regenerate, and the
     * refine chips that rewrite a card in place. 0 = no cap.
     */
    var draftLimit: Int
        get() = prefs.getInt(KEY_REPLY_DRAFTS, prefs.getInt(KEY_REPLY, 0))
        set(value) = prefs.edit().putInt(KEY_REPLY_DRAFTS, value.coerceAtLeast(0)).apply()

    /** Cap for the reply chat and its angle batches. 0 = no cap. */
    var chatLimit: Int
        get() = prefs.getInt(KEY_REPLY_CHAT, prefs.getInt(KEY_REPLY, 0))
        set(value) = prefs.edit().putInt(KEY_REPLY_CHAT, value.coerceAtLeast(0)).apply()

    /** Cap for post and quote drafts and their chat. 0 = no cap. */
    var postLimit: Int
        get() = prefs.getInt(KEY_POST, 0)
        set(value) = prefs.edit().putInt(KEY_POST, value.coerceAtLeast(0)).apply()

    private companion object {
        /** The old single reply cap; still read as the default for both halves. */
        const val KEY_REPLY = "reply"
        const val KEY_REPLY_DRAFTS = "reply_drafts"
        const val KEY_REPLY_CHAT = "reply_chat"
        const val KEY_POST = "post"
    }
}
