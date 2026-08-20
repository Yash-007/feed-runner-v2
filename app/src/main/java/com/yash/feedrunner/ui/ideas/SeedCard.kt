package com.yash.feedrunner.ui.ideas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yash.feedrunner.ui.SEED_QUICK_PROMPTS
import com.yash.feedrunner.ui.SeedStatus
import com.yash.feedrunner.ui.StoredSeed
import com.yash.feedrunner.ui.relativeAge
import com.yash.feedrunner.ui.theme.MetaTextStyle
import com.yash.feedrunner.ui.theme.Space
import com.yash.feedrunner.ui.theme.Radius
import com.yash.feedrunner.ui.theme.HairlineCard

/**
 * One banked seed.
 *
 * Read first: the card body is a tap target for expanding, not for selecting, and
 * selection has its own checkbox. Tapping to read something and tapping to tick it
 * for generation are different intentions, and sharing one gesture between them
 * meant every glance risked a selection.
 *
 * Collapsed shows only what identifies the seed. The angle hint, the originating
 * post, the destructive actions and the conversation all live behind the expand,
 * because a list of fully expanded seeds is unreadable at ten items.
 */
@Composable
internal fun SeedCard(seed: StoredSeed, onOpen: () -> Unit) {
    HairlineCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (seed.isPending) null else onOpen,
    ) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipRow(seed = seed, modifier = Modifier.weight(1f))
                Text(
                    text = if (seed.isPending) "syncing" else relativeAge(seed.createdAtMillis),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Selectable in the list too: a tension is often the thing you want to
            // lift out, and the card tap still opens the thread.
            SelectionContainer {
                Column {
                    Text(
                        text = seed.headline,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Space.md),
                    )

                    if (seed.seed.themeTags.isNotEmpty()) {
                        Text(
                            text = seed.seed.themeTags.joinToString(" · "),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Space.sm),
                        )
                    }
                }
            }

            // The one number worth surfacing in the list: whether this seed has
            // already produced posts you have not dealt with.
            if (seed.ideas.isNotEmpty()) {
                Text(
                    text = "${seed.ideas.size} ideas",
                    style = MetaTextStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Space.sm),
                )
            }
        }
    }
}

/**
 * Source, status and shelf life.
 *
 * Filled chips carry a paired content colour rather than a hardcoded white: in the
 * dark theme the container colours are light, and white on light violet was
 * unreadable.
 */
@Composable
private fun ChipRow(seed: StoredSeed, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        Chip(
            text = seed.source.label,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Chip(
            text = seed.status.label,
            container = seed.status.color.copy(alpha = 0.18f),
            content = seed.status.color,
        )
        if (seed.seed.shelfLife.isNotBlank()) {
            // Outlined rather than filled: it is the least important of the three,
            // and three filled chips in a row fights for attention.
            Chip(
                text = seed.seed.shelfLife,
                container = Color.Transparent,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                border = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun Chip(text: String, container: Color, content: Color, border: Color? = null) {
    Surface(
        shape = RoundedCornerShape(Radius.chip),
        color = container,
        border = border?.let { BorderStroke(Space.hair, it) },
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = 3.dp),
        )
    }
}
