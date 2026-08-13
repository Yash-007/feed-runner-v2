package com.yash.feedrunner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Text selection and editing actions for the overlay panels.
 *
 * Android's Cut / Copy / Paste / Select all bar is an `ActionMode`, which needs an
 * Activity window to host it. The panels are `TYPE_APPLICATION_OVERLAY` windows, so
 * selection worked but no toolbar ever appeared: text could be selected and then
 * not acted on, and there was no way to paste into a composer at all. This supplies
 * the same actions.
 *
 * Which actions appear is decided by Compose, which passes only the callbacks that
 * apply: no copy without a selection, no paste without something on the clipboard.
 */
private class OverlayTextToolbar(
    private val onShow: (SelectionRequest) -> Unit,
    private val onHide: () -> Unit,
) : TextToolbar {

    override var status: TextToolbarStatus by mutableStateOf(TextToolbarStatus.Hidden)
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        status = TextToolbarStatus.Shown
        onShow(
            SelectionRequest(
                rect = rect,
                copy = onCopyRequested,
                paste = onPasteRequested,
                cut = onCutRequested,
                selectAll = onSelectAllRequested,
            ),
        )
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        onHide()
    }
}

/** What to offer, and where the selection is, in window coordinates. */
internal data class SelectionRequest(
    val rect: Rect,
    val copy: (() -> Unit)?,
    val paste: (() -> Unit)?,
    val cut: (() -> Unit)?,
    val selectAll: (() -> Unit)?,
)

/**
 * Hosts [content] with a working selection toolbar, for both read-only text and
 * text fields. Anything selectable or editable inside gets the bar.
 */
@Composable
internal fun SelectionActionsHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var request by remember { mutableStateOf<SelectionRequest?>(null) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var barSize by remember { mutableStateOf(IntOffset.Zero) }
    val density = LocalDensity.current

    val toolbar = remember {
        OverlayTextToolbar(onShow = { request = it }, onHide = { request = null })
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        Box(
            modifier = modifier.onGloballyPositioned { hostOrigin = it.positionInWindow() },
        ) {
            content()

            request?.let { current ->
                val gap = with(density) { 8.dp.toPx() }
                // Sit just above the selection, like the platform bar, and fall
                // below it when the selection is near the top of the panel.
                val localTop = current.rect.top - hostOrigin.y
                val above = localTop - barSize.y - gap
                val y = if (above >= 0f) above else current.rect.bottom - hostOrigin.y + gap
                val x = (current.rect.left - hostOrigin.x).coerceAtLeast(0f)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .onGloballyPositioned {
                            barSize = IntOffset(it.size.width, it.size.height)
                        },
                ) {
                    current.cut?.let { cut ->
                        ToolbarAction("Cut") {
                            cut()
                            toolbar.hide()
                        }
                    }
                    current.copy?.let { copy ->
                        ToolbarAction("Copy") {
                            copy()
                            toolbar.hide()
                        }
                    }
                    current.paste?.let { paste ->
                        ToolbarAction("Paste") {
                            paste()
                            toolbar.hide()
                        }
                    }
                    current.selectAll?.let { selectAll ->
                        ToolbarAction("Select all", onClick = selectAll)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarAction(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 4.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.inverseSurface)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
