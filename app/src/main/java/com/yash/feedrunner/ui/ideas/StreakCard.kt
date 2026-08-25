package com.yash.feedrunner.ui.ideas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yash.feedrunner.ui.Streak
import com.yash.feedrunner.ui.theme.MetaTextStyle
import com.yash.feedrunner.ui.theme.Motion
import com.yash.feedrunner.ui.theme.Radius
import com.yash.feedrunner.ui.theme.Space
import com.yash.feedrunner.ui.theme.HairlineCard

/**
 * The daily shipping habit.
 *
 * Counts come from picks — replies, posts and quotes you actually copied out —
 * so a bar means something went out, not that the app generated drafts.
 *
 * One compact row by default: this card sits above the seeds, which are the
 * point of the screen, so it earns one line and expands on tap for the history.
 */
@Composable
internal fun StreakCard(streak: Streak, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    HairlineCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(animationSpec = Motion.enter())
                .padding(horizontal = Space.lg, vertical = Space.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${streak.today}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = " sent today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs),
                )
                Box(modifier = Modifier.weight(1f))
                if (streak.current > 0) {
                    Text(
                        text = "${streak.current} day streak",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = StreakAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.chip))
                            .background(StreakAccent.copy(alpha = 0.18f))
                            .padding(horizontal = Space.sm, vertical = 3.dp),
                    )
                }
                // The collapsed card still shows the shape of the fortnight,
                // just small enough to stay one line tall.
                if (!expanded) {
                    DayStrip(
                        streak = streak,
                        maxBarHeight = 18.dp,
                        modifier = Modifier
                            .padding(start = Space.md)
                            .width(88.dp),
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    DayStrip(
                        streak = streak,
                        maxBarHeight = 34.dp,
                        modifier = Modifier
                            .padding(top = Space.md)
                            .fillMaxWidth(),
                    )
                    if (streak.longest > 0) {
                        Text(
                            text = "best ${streak.longest} days · ${streak.total} sent all time" +
                                " · replies, posts and quotes count",
                            style = MetaTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.md),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fourteen days, oldest to newest. Bars are scaled against the busiest day rather
 * than a fixed ceiling, so a quiet fortnight still shows shape instead of a flat
 * line of stubs.
 */
@Composable
private fun DayStrip(streak: Streak, maxBarHeight: Dp, modifier: Modifier = Modifier) {
    val busiest = streak.busiestDay.coerceAtLeast(1)
    val minBarHeight = maxBarHeight * 0.3f
    val emptyBarHeight = 3.dp

    // Bars grow in left to right on first show. Purely a hello — the stagger is
    // short enough to be over before the eye starts reading the shape.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.height(maxBarHeight),
    ) {
        streak.days.forEachIndexed { index, day ->
            val isToday = index == streak.days.lastIndex
            val fraction = day.count.toFloat() / busiest
            val height = if (day.count == 0) {
                emptyBarHeight
            } else {
                // Floor at a visible height: one send should not be a sliver.
                minBarHeight + (maxBarHeight - minBarHeight) * fraction
            }
            val animatedHeight by animateDpAsState(
                targetValue = if (appeared) height else emptyBarHeight,
                animationSpec = tween(durationMillis = 300, delayMillis = index * 25),
                label = "streakBar",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            day.count == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.18f)
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        },
                    ),
            )
        }
    }
}

/** Same green as a posted seed, so "kept it up" reads the same everywhere. */
private val StreakAccent = androidx.compose.ui.graphics.Color(0xFF00BA7C)
