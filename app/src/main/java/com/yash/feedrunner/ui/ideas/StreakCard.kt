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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${streak.today}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (streak.today == 1) " reply today" else " replies today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
                Box(modifier = Modifier.weight(1f))
                if (streak.current > 0) {
                    Text(
                        text = "${streak.current} day streak",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = StreakAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(StreakAccent.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            DayStrip(
                streak = streak,
                modifier = Modifier.padding(top = 12.dp),
            )

            if (streak.longest > 0) {
                Text(
                    text = "best ${streak.longest} days · ${streak.total} replies all time",
                    style = MetaTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 9.dp),
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
