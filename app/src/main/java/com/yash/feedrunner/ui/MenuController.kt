package com.yash.feedrunner.ui

import android.content.Context
import android.view.WindowManager
import com.yash.feedrunner.data.ResultStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yash.feedrunner.ui.theme.FeedRunnerTheme

/** Shows the three-action menu that fans out from the bubble. */
class MenuController(
    context: Context,
    windowManager: WindowManager,
    private val resultStore: ResultStore,
    private val onCapture: (Platform) -> Unit,
    private val onHold: (Platform) -> Unit,
    private val onRepost: (Platform) -> Unit,
    private val repostDraftsAge: () -> String?,
    private val onLastResult: () -> Unit,
    /** Persists an explicit platform choice, so it becomes the fallback. */
    private val onPlatformChosen: (Platform) -> Unit,
) {
    private val window = OverlayWindow(context, windowManager)

    val isShowing: Boolean get() = window.isShowing

    fun show(anchor: MenuAnchor, initialPlatform: Platform) {
        val saved = resultStore.loadAll()
        val lastAge = saved.firstOrNull()?.let { newest ->
            val age = relativeAge(newest.savedAtMillis)
            if (saved.size > 1) "$age · ${saved.size} saved" else age
        }
        window.show {
            FeedRunnerTheme {
                // Hoisted here so the toggle survives recomposition of the menu.
                var platform by remember { mutableStateOf(initialPlatform) }
                ActionMenu(
                    anchor = anchor,
                    platform = platform,
                    lastResultAge = lastAge,
                    repostDraftsAge = repostDraftsAge(),
                    onPlatform = { chosen ->
                        platform = chosen
                        onPlatformChosen(chosen)
                    },
                    onCapture = { dismiss(); onCapture(platform) },
                    onHold = { dismiss(); onHold(platform) },
                    onRepost = { dismiss(); onRepost(platform) },
                    onLastResult = { dismiss(); onLastResult() },
                    onDismiss = ::dismiss,
                )
            }
        }
    }

    fun dismiss() = window.dismiss()
}
