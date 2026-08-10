package com.yash.feedrunner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Text selection actions for the overlay panels.
 *
 * Android's own floating Copy / Select all bar is an `ActionMode`, which needs an
 * Activity window host. The panels are `TYPE_APPLICATION_OVERLAY` windows, so
 * selecting text worked but no toolbar ever appeared, leaving selection with no way
 * to act on it. This supplies the same actions as ordinary content instead.
 *
 * The bar sits at a fixed spot above the content rather than chasing the selection
 * rectangle: predictable, and it cannot end up off screen or under the keyboard.
 */
private class OverlayTextToolbar(
    private val onShow: (SelectionActions) -> Unit,
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
        onShow(SelectionActions(copy = onCopyRequested, selectAll = onSelectAllRequested))
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        onHide()
    }
}

/** The subset of selection actions worth offering for read-only chat text. */
internal data class SelectionActions(
    val copy: (() -> Unit)?,
    val selectAll: (() -> Unit)?,
)

/**
 * Hosts [content] with a working selection toolbar. Any [SelectionContainer] inside
 * gets Copy and Select all.
 */
@Composable
internal fun SelectionActionsHost(content: @Composable () -> Unit) {
    var actions by remember { mutableStateOf<SelectionActions?>(null) }
    val toolbar = remember {
        OverlayTextToolbar(onShow = { actions = it }, onHide = { actions = null })
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        Box {
            content()
            actions?.let { current ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    current.copy?.let { copy ->
                        ToolbarAction(label = "Copy") {
                            copy()
                            toolbar.hide()
                        }
                    }
                    current.selectAll?.let { selectAll ->
                        ToolbarAction(label = "Select all", onClick = selectAll)
                    }
                    ToolbarAction(label = "Done") { toolbar.hide() }
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
