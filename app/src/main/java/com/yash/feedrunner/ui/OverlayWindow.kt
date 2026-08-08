package com.yash.feedrunner.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A full-screen Compose overlay window, with the lifecycle plumbing a
 * ComposeView needs when it isn't hosted by an Activity.
 */
class OverlayWindow(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private var view: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = view != null

    fun show(gravity: Int = Gravity.TOP or Gravity.START, content: @Composable () -> Unit) {
        dismiss()

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent(content)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Touchable but not focusable: taps and scrolling work, and we never
            // steal the keyboard or back button from the app underneath.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { this.gravity = gravity }

        windowManager.addView(composeView, params)
        view = composeView
        lifecycleOwner = owner
    }

    fun dismiss() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }
}
