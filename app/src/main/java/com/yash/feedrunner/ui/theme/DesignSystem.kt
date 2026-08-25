package com.yash.feedrunner.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size as GeometrySize

/**
 * The shared look: washes, hairlines, and the two kinds of button.
 *
 * Collected in one file because the point of the redesign is that every surface
 * makes the same handful of decisions. Anything that reaches for a colour or a
 * radius of its own is either a mistake or worth adding here.
 */

/** One rhythm for the whole app, instead of a different number in every file. */
object Space {
    val hair = 1.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/**
 * Corners. Large on things that contain other things, small on the things
 * themselves, which is the trick that keeps a bordered layout from looking like a
 * pile of lozenges.
 */
object Radius {
    val panel = 28.dp
    val card = 12.dp
    val control = 10.dp
    val chip = 8.dp
}

/**
 * One spring vocabulary for the whole app.
 *
 * Everything that moves uses one of these three, so unrelated surfaces still feel
 * like the same object physics. Numbers over names ("snappy") because the numbers
 * are what get tuned.
 */
object Motion {
    /** Selection thumbs, press scales: quick and just barely soft. */
    fun <T> settle() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** Entrances: enough bounce to feel alive, never enough to wobble. */
    fun <T> enter() = spring<T>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
}

/**
 * Press feedback: the element gives slightly under the finger.
 *
 * This is the app's one universal micro-interaction, standing in for ripples,
 * which overlay windows render inconsistently. Applied through [pressClickable]
 * rather than by hand so every tappable thing gives by the same amount.
 */
fun Modifier.pressClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = Motion.settle(),
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * The segmented control: one track, a thumb that slides between options instead
 * of teleporting. Used for theme, platform and post/quote choices, which were
 * three hand-rolled copies of the same row before this.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    onThumbColor: Color = MaterialTheme.colorScheme.onPrimary,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
) {
    val trackShape = RoundedCornerShape(Radius.control)
    val thumbShape = RoundedCornerShape(Radius.chip)
    val index = options.indexOf(selected).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .border(Space.hair, MaterialTheme.colorScheme.outlineVariant, trackShape)
            .padding(Space.xs),
    ) {
        val segmentWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * index,
            animationSpec = Motion.settle(),
            label = "segThumb",
        )
        val animatedThumbColor by androidx.compose.animation.animateColorAsState(
            targetValue = thumbColor,
            animationSpec = tween(200),
            label = "segThumbColor",
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .height(SegmentHeight)
                .clip(thumbShape)
                .background(animatedThumbColor),
        )

        Row {
            options.forEach { option ->
                val active = option == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(SegmentHeight)
                        .clip(thumbShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = enabled && !active,
                        ) { onSelect(option) },
                ) {
                    val textColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (active) {
                            onThumbColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(200),
                        label = "segText",
                    )
                    Text(
                        text = label(option),
                        style = textStyle,
                        maxLines = 1,
                        color = textColor,
                    )
                }
            }
        }
    }
}

private val SegmentHeight = 34.dp

/**
 * The length slider: how many words a draft may spend.
 *
 * Drawn by hand rather than themed from Material so it matches the rest of the
 * system: a hairline track, an evergreen fill, a white thumb that gives under
 * the finger. Snaps to [step]-word notches with a haptic tick; the notch past
 * the top is "auto", meaning no cap, which is also the default. Left is tight,
 * right is free.
 */
@Composable
fun WordLimitSlider(
    /** The stored cap. 0 means auto (no cap). */
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "length",
) {
    val autoNotch = range.last + step
    val current = if (value <= 0) autoNotch else value.coerceIn(range.first, range.last)
    val span = (autoNotch - range.first).toFloat()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // Read inside the gesture handlers without restarting them: keying the
    // pointerInput on the value would abort a drag at its first notch.
    val liveNotch = androidx.compose.runtime.rememberUpdatedState(current)
    val liveOnChange = androidx.compose.runtime.rememberUpdatedState(onValueChange)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BoxWithConstraints(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.md)
                .height(SliderTouchHeight)
                .pointerInput(range, step) {
                    // One lambda for tap and drag: both are "the finger says here".
                    fun settle(x: Float) {
                        val fraction = (x / size.width.toFloat()).coerceIn(0f, 1f)
                        val raw = range.first + fraction * span
                        val notch = (range.first +
                            ((raw - range.first) / step).roundToInt() * step)
                            .coerceIn(range.first, autoNotch)
                        if (notch != liveNotch.value) {
                            haptics.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                            )
                            liveOnChange.value(if (notch >= autoNotch) 0 else notch)
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> settle(offset.x) },
                    ) { change, _ ->
                        change.consume()
                        settle(change.position.x)
                    }
                }
                .pointerInput(range, step) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val raw = range.first + fraction * span
                        val notch = (range.first +
                            ((raw - range.first) / step).roundToInt() * step)
                            .coerceIn(range.first, autoNotch)
                        liveOnChange.value(if (notch >= autoNotch) 0 else notch)
                    }
                },
        ) {
            val fraction = (current - range.first) / span
            val thumbCenter = SliderThumb / 2 + (maxWidth - SliderThumb) * fraction

            // Track: the same hairline grammar as every border in the app.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SliderTrack)
                    .clip(RoundedCornerShape(SliderTrack / 2))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            // Fill: evergreen, brightening toward the thumb.
            Box(
                modifier = Modifier
                    .width(thumbCenter)
                    .height(SliderTrack)
                    .clip(RoundedCornerShape(SliderTrack / 2))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.primary,
                            ),
                        ),
                    ),
            )
            val animatedOffset by animateDpAsState(
                targetValue = thumbCenter - SliderThumb / 2,
                animationSpec = Motion.settle(),
                label = "sliderThumb",
            )
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .size(SliderThumb)
                    .clip(RoundedCornerShape(SliderThumb / 2))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(SliderThumb / 2),
                    ),
            )
        }

        // The readout doubles as the "auto" explainer: no cap unless you set one.
        Text(
            text = if (current >= autoNotch) "auto" else "≤ $current words",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 78.dp)
                .clip(RoundedCornerShape(Radius.chip))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .padding(horizontal = Space.sm, vertical = Space.xs),
        )
    }
}

private val SliderTouchHeight = 30.dp
private val SliderTrack = 4.dp
private val SliderThumb = 18.dp

/**
 * The loading texture: a soft band of the primary drifting across a pale block.
 * Skeletons built from this read as "the real thing is coming", where a lone
 * spinner reads as "nothing is happening".
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100)),
        label = "shimmerSweep",
    )
    val base = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    val highlight = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.045f)
    return this.drawBehind {
        drawRect(base)
        val bandWidth = size.width * 0.6f
        val start = progress * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(start, 0f),
                end = Offset(start + bandWidth, size.height),
            ),
        )
    }
}

/**
 * Pastel fields, four hues drifting into each other.
 *
 * Kept desaturated on purpose. They are a ground for near-black text to sit on, so
 * anything stronger starts fighting the words.
 */
private val LightWash = listOf(
    Color(0xFFCCF1E4),
    Color(0xFFE2F3D9),
    Color(0xFFFBEFD9),
    Color(0xFFD6ECFF),
)

/**
 * The same drift, at a depth that reads as a tinted dark rather than a colour.
 * A pastel band would glow in a dark room, which is where the overlays get used.
 */
private val DarkWash = listOf(
    Color(0xFF11241F),
    Color(0xFF15221A),
    Color(0xFF241E14),
    Color(0xFF101C2E),
)

/**
 * Diagonal rather than straight down, so the hues drift across the band the way
 * they do on the page that inspired this instead of stacking into stripes.
 */
@Composable
internal fun washBrush(): Brush = Brush.linearGradient(
    colors = if (appInDarkTheme()) DarkWash else LightWash,
    start = Offset.Zero,
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)

/**
 * The halftone dot texture over the washes.
 *
 * Drawn as a one-tile bitmap repeated by a shader rather than thousands of
 * circles: the whole thing costs a single draw call, which matters because one of
 * these sits in an overlay window on top of a scrolling feed.
 */
@Composable
internal fun Modifier.halftone(
    spacing: Dp = 5.dp,
    dotRadius: Dp = 0.7.dp,
): Modifier {
    val density = LocalDensity.current
    val dark = appInDarkTheme()
    // White dots lift a pastel ground; on a dark ground they have to be darker
    // than it or the texture turns into static.
    val dotColor = if (dark) Color.Black.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.55f)

    val brush = remember(density, spacing, dotRadius, dotColor) {
        ShaderBrush(
            ImageShader(
                dotTile(density, spacing, dotRadius, dotColor),
                TileMode.Repeated,
                TileMode.Repeated,
            ),
        )
    }
    return this.drawBehind { drawRect(brush) }
}

/** One dot in a transparent square, which the shader then tiles. */
private fun dotTile(
    density: Density,
    spacing: Dp,
    dotRadius: Dp,
    color: Color,
): ImageBitmap {
    val side = with(density) { spacing.toPx() }.coerceAtLeast(2f)
    val radius = with(density) { dotRadius.toPx() }.coerceAtLeast(0.5f)
    val bitmap = ImageBitmap(side.toInt().coerceAtLeast(2), side.toInt().coerceAtLeast(2))
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = GraphicsCanvas(bitmap),
        size = GeometrySize(bitmap.width.toFloat(), bitmap.height.toFloat()),
    ) {
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(bitmap.width / 2f, bitmap.height / 2f),
        )
    }
    return bitmap
}

/**
 * A gradient header band with the dot texture over it.
 *
 * Screen headings live in here. It is the one place the app raises its voice, and
 * having it be a band rather than a whole background means the content below stays
 * plain and readable.
 */
@Composable
internal fun WashHeader(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    /** True on activity screens, where the wash runs behind the status bar. */
    padStatusBar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(washBrush())
            .halftone()
            .then(
                if (padStatusBar) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier,
            ),
        content = content,
    )
}

/**
 * The card. A hairline and nothing else: no fill, no shadow.
 *
 * Elevation is reserved for things that genuinely float above the page, which in
 * this app is the overlay sheet itself and the selection toolbar. Everything
 * inside a page separates by line alone.
 */
@Composable
internal fun HairlineCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.card),
    onClick: (() -> Unit)? = null,
    fill: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickable = if (onClick != null) {
        Modifier.pressClickable(pressedScale = 0.985f, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .clip(shape)
            .background(fill)
            .border(Space.hair, MaterialTheme.colorScheme.outlineVariant, shape),
        content = content,
    )
}

/** Solid violet, small radius. Deliberately not a pill: the round ones are icons. */
@Composable
internal fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.control)
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(200), label = "btnAlpha")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressClickable(enabled = enabled, pressedScale = 0.96f, onClick = onClick)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** The quieter half of the pair: violet on a violet tint, same geometry. */
@Composable
internal fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.control)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressClickable(enabled = enabled, pressedScale = 0.96f, onClick = onClick)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .border(Space.hair, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), shape)
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/** A solid violet circle. For glyphs only, which is what earns it the shape. */
@Composable
internal fun RoundIconButton(
    glyph: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 40.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(200), label = "iconAlpha")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(diameter)
            .pressClickable(enabled = enabled, pressedScale = 0.9f, onClick = onClick)
            .clip(RoundedCornerShape(diameter / 2))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * A chip that names a take: an angle, a register, a style.
 *
 * A pale wash of its own hue with the hue itself as the text. These used to be
 * solid saturated blocks, which made them the loudest thing on a screen that is
 * otherwise pastel and hairlines. The hue still does its job, which is why these
 * are the one exception to using violet for everything.
 */
@Composable
internal fun SoftAccentChip(
    text: String,
    hue: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = hue,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.chip))
            .background(hue.copy(alpha = 0.16f))
            .padding(horizontal = Space.sm, vertical = Space.xs),
    )
}

/** A single hairline, for splitting a card into rows. */
@Composable
internal fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(top = Space.hair),
    )
}

/** Row of items divided by vertical hairlines, the way the reference splits cards. */
@Composable
internal fun DividedRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

/** Vertical hairline for [DividedRow]. */
@Composable
internal fun VerticalHairline(height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = Space.hair, height = height)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
