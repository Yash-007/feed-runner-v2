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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
) {
    /**
     * Cut and paste are only ever offered for an editable field, so their presence
     * tells the two cases apart without Compose saying which it is.
     */
    val editable: Boolean get() = cut != null || paste != null
}

/**
 * Hosts [content] with a working selection toolbar, for both read-only text and
 * text fields. Anything selectable or editable inside gets the bar.
 */
@Composable
internal fun SelectionActionsHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Held as state objects and read only inside ToolbarLayer. Reading them here
    // would recompose content() every time the toolbar appeared, and the selection
    // machinery responds to that relayout by hiding the toolbar again: showing it
    // was cancelling itself within ~80ms.
    val request = remember { mutableStateOf<SelectionRequest?>(null) }
    val hostOrigin = remember { mutableStateOf(Offset.Zero) }

    // Where the bar ended up, so a tap on it is not mistaken for a tap away.
    val barBounds = remember { mutableStateOf(Rect.Zero) }

    // For read-only text, hide() is ignored. Compose hides the toolbar whenever the
    // selection moves on screen, and starting a selection scrolls it into view, so
    // every long press ended on a hide while the selection was still sitting there
    // highlighted. The platform bar survives that because an ActionMode outlives the
    // relayout; here the bar stays until the user acts on it or touches elsewhere.
    //
    // Fields never had that problem and hide() is accurate for them, so it is obeyed:
    // otherwise the bar hangs around after the text it applied to has been deleted.
    val toolbar = remember {
        OverlayTextToolbar(
            onShow = { request.value = it },
            onHide = { if (request.value?.editable == true) request.value = null },
        )
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        Box(
            modifier = modifier
                .onGloballyPositioned { hostOrigin.value = it.positionInWindow() }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            // Initial pass: watched, never consumed, so selection and
                            // scrolling still see the gesture.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Press) continue
                            val point = event.changes.first().position + hostOrigin.value
                            if (!barBounds.value.contains(point)) request.value = null
                        }
                    }
                },
        ) {
            content()
            // matchParentSize takes the host's size instead of contributing to it.
            // As a plain child the bar grew the host when it appeared, which
            // relaid out the text underneath, and the selection machinery answers a
            // relayout by hiding the toolbar, which shrank the host again: the bar
            // flickered between shown and hidden for as long as you looked at it.
            Box(modifier = Modifier.matchParentSize()) {
                ToolbarLayer(
                    request = request,
                    hostOrigin = hostOrigin,
                    onBounds = { barBounds.value = it },
                    onDone = { request.value = null },
                )
            }
        }
    }
}

/** The bar itself, the only thing that recomposes when a selection changes. */
@Composable
private fun ToolbarLayer(
    request: State<SelectionRequest?>,
    hostOrigin: State<Offset>,
    onBounds: (Rect) -> Unit,
    onDone: () -> Unit,
) {
    val current = request.value ?: return
    val density = LocalDensity.current
    var barSize by remember { mutableStateOf(IntOffset.Zero) }

    val gap = with(density) { 8.dp.toPx() }
    // Sit just above the selection, like the platform bar, and drop below it when
    // the selection is near the top of the panel.
    val localTop = current.rect.top - hostOrigin.value.y
    val above = localTop - barSize.y - gap
    val y = if (above >= 0f) above else current.rect.bottom - hostOrigin.value.y + gap
    val x = (current.rect.left - hostOrigin.value.x).coerceAtLeast(0f)

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onGloballyPositioned {
                barSize = IntOffset(it.size.width, it.size.height)
                onBounds(it.boundsInWindow())
            },
    ) {
        current.cut?.let { cut ->
            ToolbarAction("Cut") {
                cut()
                onDone()
            }
        }
        current.copy?.let { copy ->
            ToolbarAction("Copy") {
                copy()
                onDone()
            }
        }
        current.paste?.let { paste ->
            ToolbarAction("Paste") {
                paste()
                onDone()
            }
        }
        current.selectAll?.let { selectAll ->
            ToolbarAction("Select all", onClick = selectAll)
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
