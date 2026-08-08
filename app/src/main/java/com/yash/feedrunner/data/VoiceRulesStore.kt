package com.yash.feedrunner.data

import android.content.Context

/**
 * Optional extra rules appended after the main system prompt, which already
 * carries the full voice. Empty by default — this is for on-the-fly tweaks
 * ("stop using bhai", "lean more technical this week"), not the base voice.
 */
class VoiceRulesStore(context: Context) {

    private val prefs = context.getSharedPreferences("feed_runner", Context.MODE_PRIVATE)

    var rules: String
        get() = prefs.getString(KEY, DEFAULT).orEmpty()
        set(value) = prefs.edit().putString(KEY, value).apply()

    private companion object {
        const val KEY = "voice_rules"
        const val DEFAULT = ""
    }
}
