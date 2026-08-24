package com.yash.feedrunner.data

import android.content.Context
import com.yash.feedrunner.ui.Platform

/**
 * The platform to assume when the app under the bubble is neither X nor
 * LinkedIn: whatever the user chose or used last. One value, so the bubble
 * works from a screenshots gallery or a chat app without asking every time.
 */
class PlatformStore(context: Context) {

    private val prefs = context.getSharedPreferences("platform", Context.MODE_PRIVATE)

    var last: Platform
        get() = Platform.fromWire(prefs.getString(KEY_LAST, null))
        set(value) {
            prefs.edit().putString(KEY_LAST, value.wire).apply()
        }

    private companion object {
        const val KEY_LAST = "last"
    }
}
