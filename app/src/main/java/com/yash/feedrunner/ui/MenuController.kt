package com.yash.feedrunner.ui

import android.content.Context
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import com.yash.feedrunner.data.ResultStore

/** Shows the three-action menu that fans out from the bubble. */
class MenuController(
    context: Context,
    windowManager: WindowManager,
    private val resultStore: ResultStore,
    private val onCapture: () -> Unit,
    private val onHold: () -> Unit,
    private val onRepost: () -> Unit,
    private val onLastResult: () -> Unit,
) {
    private val window = OverlayWindow(context, windowManager)

    val isShowing: Boolean get() = window.isShowing

    fun show(anchor: MenuAnchor) {
        val saved = resultStore.loadAll()
        val lastAge = saved.firstOrNull()?.let { newest ->
            val age = relativeAge(newest.savedAtMillis)
            if (saved.size > 1) "$age · ${saved.size} saved" else age
        }
        window.show {
            MaterialTheme {
                ActionMenu(
                    anchor = anchor,
                    lastResultAge = lastAge,
                    onCapture = { dismiss(); onCapture() },
                    onHold = { dismiss(); onHold() },
                    onRepost = { dismiss(); onRepost() },
                    onLastResult = { dismiss(); onLastResult() },
                    onDismiss = ::dismiss,
                )
            }
        }
    }

    fun dismiss() = window.dismiss()
}
