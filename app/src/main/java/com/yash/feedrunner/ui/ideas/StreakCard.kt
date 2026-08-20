package com.yash.feedrunner.ui.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yash.feedrunner.ui.Streak
import com.yash.feedrunner.ui.theme.MetaTextStyle
import com.yash.feedrunner.ui.theme.Space
import com.yash.feedrunner.ui.theme.Radius
import com.yash.feedrunner.ui.theme.HairlineCard

/**
 * The daily reply habit.
 *
 * Counts come from picks, so a bar means a reply you actually copied out and used,
 * not a draft the app generated. That makes the number honest, and it is the only
 * reason it is worth looking at.
 *
 * Deliberately quiet: a number, a run, and fourteen bars. A louder treatment would
 * compete with the seeds, which are the point of this screen.
 */
@Composable
internal fun StreakCard(streak: Streak, modifier: Modifier = Modifier) {
    // Deliberately not on a wash. The header band is directly above it, and two
    // gradients touching read as one smear rather than two surfaces.
    HairlineCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
    ) {
        Column(modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.lg)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${streak.today}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (streak.today == 1) " reply today" else " replies today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs, bottom = 4.dp),
                )
                Box(modifier = Modifier.weight(1f))
                if (streak.current > 0) {
                    Text(
                        text = "${streak.current} day streak",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = StreakAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.chip))
                            .background(StreakAccent.copy(alpha = 0.18f))
                            .padding(horizontal = Space.md, vertical = Space.xs),
                    )
                }
            }

            DayStrip(
                streak = streak,
                modifier = Modifier.padding(top = Space.lg),
            )

            if (streak.longest > 0) {
                Text(
                    text = "best ${streak.longest} days · ${streak.total} replies all time",
                    style = MetaTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md),
                )
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
private fun DayStrip(streak: Streak, modifier: Modifier = Modifier) {
    val busiest = streak.busiestDay.coerceAtLeast(1)

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.height(MaxBarHeight),
    ) {
        streak.days.forEachIndexed { index, day ->
            val isToday = index == streak.days.lastIndex
            val fraction = day.count.toFloat() / busiest
            val height = if (day.count == 0) {
                EmptyBarHeight
            } else {
                // Floor at a visible height: one reply should not be a sliver.
                MinBarHeight + (MaxBarHeight - MinBarHeight) * fraction
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(3.dp))
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

/** Same green used for a posted seed, so "kept it up" reads the same everywhere. */
private val StreakAccent = androidx.compose.ui.graphics.Color(0xFF00BA7C)

private val MaxBarHeight = 34.dp
private val MinBarHeight = 10.dp
private val EmptyBarHeight = 4.dp
