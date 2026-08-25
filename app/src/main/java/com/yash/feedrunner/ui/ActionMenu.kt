package com.yash.feedrunner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yash.feedrunner.R
import com.yash.feedrunner.ui.theme.Hairline
import com.yash.feedrunner.ui.theme.Motion
import com.yash.feedrunner.ui.theme.Radius
import com.yash.feedrunner.ui.theme.SegmentedControl
import com.yash.feedrunner.ui.theme.Space
import com.yash.feedrunner.ui.theme.pressClickable
import com.yash.feedrunner.ui.theme.Accent

/** Fixed geometry keeps the menu's position exact on first frame — no measure-then-jump. */
private val MenuWidth = 192.dp
private val RowHeight = 52.dp
private val PlatformRowHeight = 50.dp
private val MenuHeight = RowHeight * 4 + PlatformRowHeight
private val EdgeGap = 10.dp
private val IconSize = 30.dp

data class MenuAnchor(
    val bubbleX: Int,
    val bubbleY: Int,
    val bubbleSize: Int,
    val dockedRight: Boolean,
    val screenWidth: Int,
    val screenHeight: Int,
)

/**
 * The bubble's menu: a scrim over the feed, and one card beside the bubble.
 *
 * One connected surface rather than a stack of floating pills. The feed under the
 * old pills bled through the gaps between them, which made every open a different
 * picture; a single card with hairline rows reads the same over any feed. The
 * scrim also buys the card contrast without it having to shout.
 */
@Composable
fun ActionMenu(
    anchor: MenuAnchor,
    platform: Platform,
    lastResultAge: String?,
    repostDraftsAge: String?,
    onPlatform: (Platform) -> Unit,
    onCapture: () -> Unit,
    onHold: () -> Unit,
    onRepost: () -> Unit,
    onLastResult: () -> Unit,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        visible = true
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

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

    val scrim by animateFloatAsState(
        targetValue = if (visible) 0.30f else 0f,
        animationSpec = tween(180),
        label = "menuScrim",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(140)) + scaleIn(
                initialScale = 0.86f,
                animationSpec = Motion.enter(),
                // Grow out of the bubble's side, so the card feels attached to it.
                transformOrigin = TransformOrigin(
                    pivotFractionX = if (anchor.dockedRight) 1f else 0f,
                    pivotFractionY = 0.5f,
                ),
            ),
            modifier = Modifier.offset { IntOffset(x, y) },
        ) {
            Surface(
                shape = RoundedCornerShape(Radius.panel * 2 / 3),
                color = MaterialTheme.colorScheme.surface,
                // The one place in the app that floats with no surface of ours
                // behind it, so it keeps a real shadow.
                shadowElevation = 12.dp,
                modifier = Modifier.width(MenuWidth),
            ) {
                Column {
                    // Which network the drafts are for. Preset from the app under
                    // the bubble; one tap corrects it, and the choice is remembered.
                    Box(
                        modifier = Modifier
                            .height(PlatformRowHeight)
                            .padding(horizontal = Space.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Icons, not words: at this width three brand marks read
                        // faster than "LinkedIn" squeezed next to "General".
                        SegmentedControl(
                            options = Platform.entries.toList(),
                            selected = platform,
                            label = { it.label },
                            onSelect = onPlatform,
                            thumbColor = platform.hue,
                            onThumbColor = Color.White,
                            iconRes = {
                                when (it) {
                                    Platform.X -> R.drawable.ic_brand_x
                                    Platform.LINKEDIN -> R.drawable.ic_brand_linkedin
                                    Platform.GENERAL -> R.drawable.ic_brand_general
                                }
                            },
                        )
                    }

                    Hairline()

                    MenuRow(
                        glyph = "◎",
                        accent = Accent.capture,
                        title = "Capture",
                        subtitle = "this screen",
                        onClick = onCapture,
                    )
                    Hairline(modifier = Modifier.padding(horizontal = Space.md))
                    MenuRow(
                        glyph = "⇊",
                        accent = Accent.hold,
                        title = "Hold",
                        subtitle = "scroll & capture",
                        onClick = onHold,
                    )
                    Hairline(modifier = Modifier.padding(horizontal = Space.md))
                    MenuRow(
                        glyph = "⇄",
                        accent = Accent.repost,
                        title = "Repost",
                        subtitle = repostDraftsAge?.let { "drafts ready · $it" }
                            ?: "caption this post",
                        onClick = onRepost,
                    )
                    Hairline(modifier = Modifier.padding(horizontal = Space.md))
                    MenuRow(
                        glyph = "↺",
                        accent = Accent.lastResult,
                        title = "Last result",
                        subtitle = lastResultAge ?: "nothing yet",
                        enabled = lastResultAge != null,
                        onClick = onLastResult,
                    )
                }
            }
        }
    }
}

/**
 * One action. The icon is the same soft treatment as the accent chips — a pale
 * wash of the hue with the hue as the glyph — so the menu speaks the app's own
 * colour language instead of four solid saturated circles.
 */
@Composable
private fun MenuRow(
    glyph: String,
    accent: Color,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .pressClickable(enabled = enabled, pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = Space.md),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(IconSize)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f * alpha)),
        ) {
            Text(text = glyph, color = accent.copy(alpha = alpha), fontSize = 14.sp)
        }
        Column(modifier = Modifier.padding(start = Space.md)) {
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}
