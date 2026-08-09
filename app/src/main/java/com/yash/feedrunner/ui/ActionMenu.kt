package com.yash.feedrunner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fixed geometry keeps the menu's position exact on first frame — no measure-then-jump. */
private val MenuWidth = 168.dp
private val PillHeight = 44.dp
private val PillGap = 7.dp
private val MenuHeight = PillHeight * 4 + PillGap * 3
private val EdgeGap = 8.dp
private val IconSize = 28.dp

private val CaptureColor = Color(0xFF1D9BF0)
private val HoldColor = Color(0xFF7856FF)
private val RepostColor = Color(0xFF00B8D9)
private val LastColor = Color(0xFF00BA7C)

data class MenuAnchor(
    val bubbleX: Int,
    val bubbleY: Int,
    val bubbleSize: Int,
    val dockedRight: Boolean,
    val screenWidth: Int,
    val screenHeight: Int,
)

@Composable
fun ActionMenu(
    anchor: MenuAnchor,
    lastResultAge: String?,
    repostDraftsAge: String?,
    onCapture: () -> Unit,
    onHold: () -> Unit,
    onRepost: () -> Unit,
    onLastResult: () -> Unit,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val density = LocalDensity.current
    val menuWidthPx = with(density) { MenuWidth.roundToPx() }
    val menuHeightPx = with(density) { MenuHeight.roundToPx() }
    val edgeGapPx = with(density) { EdgeGap.roundToPx() }

    // Sit beside the bubble on whichever side has room, vertically centred on it.
    val x = if (anchor.dockedRight) {
        anchor.bubbleX - menuWidthPx - edgeGapPx
    } else {
        anchor.bubbleX + anchor.bubbleSize + edgeGapPx
    }.coerceIn(edgeGapPx, (anchor.screenWidth - menuWidthPx - edgeGapPx).coerceAtLeast(edgeGapPx))

    val y = (anchor.bubbleY + anchor.bubbleSize / 2 - menuHeightPx / 2)
        .coerceIn(edgeGapPx, (anchor.screenHeight - menuHeightPx - edgeGapPx).coerceAtLeast(edgeGapPx))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .offset { IntOffset(x, y) }
                .width(MenuWidth),
            verticalArrangement = Arrangement.spacedBy(PillGap),
        ) {
            ActionPill(
                visible = visible,
                index = 0,
                fromRight = anchor.dockedRight,
                glyph = "◎",
                accent = CaptureColor,
                title = "Capture",
                subtitle = "This screen",
                onClick = onCapture,
            )
            ActionPill(
                visible = visible,
                index = 1,
                fromRight = anchor.dockedRight,
                glyph = "⇊",
                accent = HoldColor,
                title = "Hold",
                subtitle = "Scroll & capture",
                onClick = onHold,
            )
            ActionPill(
                visible = visible,
                index = 2,
                fromRight = anchor.dockedRight,
                glyph = "⇄",
                accent = RepostColor,
                title = "Repost",
                subtitle = repostDraftsAge?.let { "drafts · $it" } ?: "Caption this post",
                onClick = onRepost,
            )
            ActionPill(
                visible = visible,
                index = 3,
                fromRight = anchor.dockedRight,
                glyph = "↺",
                accent = LastColor,
                title = "Last result",
                subtitle = lastResultAge ?: "nothing yet",
                enabled = lastResultAge != null,
                onClick = onLastResult,
            )
        }
    }
}

@Composable
private fun ActionPill(
    visible: Boolean,
    index: Int,
    fromRight: Boolean,
    glyph: String,
    accent: Color,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val delay = index * 45
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160, delayMillis = delay)) +
            scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            ) +
            slideInHorizontally(
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                initialOffsetX = { full -> if (fromRight) full / 3 else -full / 3 },
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(PillHeight / 2),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 5.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(PillHeight)
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(IconSize)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (enabled) 1f else 0.35f)),
                ) {
                    Text(text = glyph, color = Color.White, fontSize = 13.sp)
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (enabled) 1f else 0.4f,
                        ),
                    )
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (enabled) 1f else 0.4f,
                        ),
                    )
                }
            }
        }
    }
}
