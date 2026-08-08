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
    private var params: WindowManager.LayoutParams? = null

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

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Touchable but not focusable: taps and scrolling work, and we never
            // steal the keyboard or back button from the app underneath.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        windowManager.addView(composeView, layoutParams)
        view = composeView
        params = layoutParams
        lifecycleOwner = owner
    }

    /**
     * A not-focusable overlay can never receive keyboard input, so text entry
     * requires flipping the flag at runtime and flipping it back afterwards.
     * While focusable, this window also owns the back button.
     */
    fun setFocusable(focusable: Boolean) {
        val current = view ?: return
        val layoutParams = params ?: return
        val wanted = if (focusable) {
            layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (layoutParams.flags == wanted) return
        layoutParams.flags = wanted
        runCatching { windowManager.updateViewLayout(current, layoutParams) }
    }

    fun dismiss() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        params = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }
}
