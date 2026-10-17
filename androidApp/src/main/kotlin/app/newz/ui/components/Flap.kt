package app.newz.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import app.newz.ui.theme.NewzTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/**
 * `FlapTile` — the split-flap character: a `tile` rectangle, 24×32 (18×24 [small]),
 * `radius-control`, a centred `type-tile` character in `sky` and a 2 dp `strip` line across it
 * 15 dp from the top (11 on small tiles). Live text, drawn at a fixed size so it always fits the
 * tile. A blank [text] draws an empty tile. [size] overrides the tile size (the character and
 * the rule scale with its height); [fill], [letter] and [rule] override the colours.
 */
@Composable
fun FlapTile(
    text: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    size: DpSize = if (small) NewzTokens.tileSizeSmall else NewzTokens.tileSize,
    fill: Color = palette().tile,
    letter: Color = palette().sky,
    rule: Color = palette().strip,
) {
    val ruleOffset =
        when (size) {
            NewzTokens.tileSize -> NewzTokens.tileRuleOffset
            NewzTokens.tileSizeSmall -> NewzTokens.tileRuleOffsetSmall
            else -> NewzTokens.tileRuleOffset * (size.height / NewzTokens.tileSize.height)
        }
    val base = NewzTokens.typeTile.textStyle
    val ratio = size.height / NewzTokens.tileSize.height
    // Tiles have a fixed size, so the character ignores both text-size settings.
    val fontScale = LocalDensity.current.fontScale
    val style =
        base.copy(
            fontSize = (base.fontSize.value * ratio / fontScale).sp,
            lineHeight =
                (
                    base.fontSize.value *
                        ratio /
                        fontScale
                ).sp,
        )
    Box(
        modifier =
            modifier
                .height(size.height)
                // Intrinsic width: the full-width split rule would otherwise stretch the tile across its row.
                .width(IntrinsicSize.Max)
                .widthIn(min = size.width)
                .clip(ControlShape)
                .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            color = letter,
            style = style,
            textAlign = TextAlign.Center,
            softWrap = false,
            modifier = Modifier.padding(horizontal = NewzTokens.space2xs),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(y = ruleOffset)
                .fillMaxWidth()
                .height(NewzTokens.rule)
                .background(rule),
        )
    }
}

/** The spoken form of a compact age: "now" → "just now", "2h" → "2 hours ago", "1d" → "1 day ago". */
fun spokenAgeOf(age: String): String {
    val trimmed = age.trim()
    if (trimmed.equals("now", ignoreCase = true)) return "just now"
    val n = trimmed.dropLast(1).toIntOrNull() ?: return trimmed
    val unit =
        when (trimmed.last().lowercaseChar()) {
            'm' -> "minute"
            'h' -> "hour"
            'd' -> "day"
            else -> return trimmed
        }
    return if (n == 1) "1 $unit ago" else "$n ${unit}s ago"
}

/**
 * `FlapAge` — the article age: one [FlapTile] per character of [age] ("now", "5m", "3h", "2d"),
 * gap 3, spoken as the single label [spokenAge] ("2 hours ago").
 */
@Composable
fun FlapAge(
    age: String,
    modifier: Modifier = Modifier,
    spokenAge: String = spokenAgeOf(age),
    small: Boolean = false,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = spokenAge },
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.tileGap),
    ) {
        age.forEach { FlapTile(text = it.toString(), small = small) }
    }
}

private const val BOARD_WORD = "NEWZ"
private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val FIRST_TILE_FLIPS = 6
private const val TILE_FLIP_STAGGER = 2
private const val HOLD_STEPS = 8

/** Steps until the last tile lands on its letter of "NEWZ". */
private val LANDING_STEPS = FIRST_TILE_FLIPS + TILE_FLIP_STAGGER * (BOARD_WORD.length - 1)
private val CYCLE_STEPS = LANDING_STEPS + HOLD_STEPS

/** The letter tile [index] shows at [step]: it flips one letter per step until it reaches its letter of "NEWZ". */
internal fun boardLetter(
    index: Int,
    step: Int,
): Char {
    val target = BOARD_WORD[index]
    val flips = FIRST_TILE_FLIPS + TILE_FLIP_STAGGER * index
    if (step >= flips) return target
    val pos = (ALPHABET.indexOf(target) - (flips - step)).mod(ALPHABET.length)
    return ALPHABET[pos]
}

/** The board's step, advancing one letter per `flap-flip` and repeating while it is composed. */
@Composable
private fun rememberBoardStep(): Int {
    val transition = rememberInfiniteTransition(label = "loading-board")
    val step by transition.animateValue(
        initialValue = 0,
        targetValue = CYCLE_STEPS,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(tween(CYCLE_STEPS * NewzTokens.flapFlip, easing = LinearEasing)),
        label = "loading-board-step",
    )
    return step
}

/**
 * The state of the [FlapWordmark]'s tiles (B18), hoisted so the feed can keep its pull-to-refresh
 * message up for exactly as long as the tiles turn. Each time the screen turns busy (or [play] is
 * called) the tiles play at least one whole cycle, flipping from the start until they spell NEWZ,
 * so a load that ends within a moment still shows the board turning instead of a flicker. While
 * still busy when a cycle lands, they hold NEWZ (up to as long as the `LoadingBoard` does) and
 * flip again; once a cycle has landed and nothing is busy they stay on NEWZ and [cycling] turns
 * false. With animations switched off (animator duration scale 0) the tiles stay still and
 * [cycling] simply follows busy. Make one with [rememberFlapBoardState], which also drives it.
 */
@Stable
class FlapBoardState internal constructor() {
    private val board = Animatable(LANDING_STEPS.toFloat())
    private var requested by mutableStateOf(false)

    /** The step the tiles show (see [boardLetter]); [LANDING_STEPS] and above spell NEWZ. */
    internal val step: Int by derivedStateOf { board.value.toInt() }

    /** True from the moment the tiles start a cycle until they have settled on NEWZ. */
    var cycling: Boolean by mutableStateOf(false)
        private set

    /** True from a [play] until the cycles it started (or joined) have settled on NEWZ. */
    var played: Boolean by mutableStateOf(false)
        private set

    /** Starts a cycle now, or keeps the running one; marks it [played] until it settles. */
    fun play() {
        requested = true
        played = true
    }

    /** Flips a still board from the start (step 0) until all four tiles spell "NEWZ": one cycle. */
    private suspend fun flipToNewz() {
        board.snapTo(0f)
        board.animateTo(
            LANDING_STEPS.toFloat(),
            tween(LANDING_STEPS * NewzTokens.flapFlip, easing = LinearEasing),
        )
    }

    /** Holds NEWZ one `flap-flip` at a time while [busy]: true when still busy after the full hold. */
    private suspend fun holdWhile(busy: () -> Boolean): Boolean {
        for (held in 1..HOLD_STEPS) {
            if (!busy()) return false
            board.animateTo(
                (LANDING_STEPS + held).toFloat(),
                tween(NewzTokens.flapFlip, easing = LinearEasing),
            )
        }
        return busy()
    }

    internal suspend fun run(busy: () -> Boolean) {
        val busyNow = snapshotFlow(busy)
        while (true) {
            snapshotFlow { busy() || requested }.first { it }
            requested = false
            cycling = true
            try {
                if (currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f) {
                    busyNow.first { !it }
                } else {
                    playCycles(busy)
                }
            } finally {
                requested = false
                cycling = false
                played = false
            }
        }
    }

    private suspend fun playCycles(busy: () -> Boolean) {
        flipToNewz()
        if (!busy()) return
        // Flipping on for as long as the load lasts has no end of its own, so it runs as an
        // infinite animation (which Compose tests switch off unless they drive the clock).
        val keepFlipping: suspend () -> Unit = {
            while (holdWhile(busy)) flipToNewz()
        }
        val policy = currentCoroutineContext()[InfiniteAnimationPolicy]
        try {
            if (policy == null) keepFlipping() else policy.onInfiniteOperation(keepFlipping)
        } catch (stopped: CancellationException) {
            currentCoroutineContext().ensureActive()
            board.snapTo(LANDING_STEPS.toFloat())
            snapshotFlow(busy).first { !it }
        }
    }
}

/**
 * A [FlapBoardState] driven by [busy] for as long as the caller is composed, whether or not a
 * [FlapWordmark] shows it.
 */
@Composable
fun rememberFlapBoardState(busy: Boolean): FlapBoardState {
    val state = remember { FlapBoardState() }
    val latestBusy by rememberUpdatedState(busy)
    LaunchedEffect(state) { state.run { latestBusy } }
    return state
}

/**
 * `FlapWordmark` — the "Newz" wordmark in the app bar and the onboarding header: four
 * [FlapTile]s spelling N E W Z at `tile-size-wordmark` (28×38), gap 3: a `wordmark-tile` (`sky`)
 * face, a `wordmark-letter` (`navy`) letter and a `wordmark-rule` split line, the app icon's tile
 * turned over so it stands out on the navy bar in both themes. When [busy] turns true (the feed is
 * loading or refreshing) the tiles flip through letters back to NEWZ like the `LoadingBoard`: at
 * least one whole cycle, then again for as long as [busy] lasts, always stopping on NEWZ; otherwise
 * they stay still (see [FlapBoardState]; pass a hoisted [board] to share its timing). Read as the
 * [label] "Newz", a heading when [heading] (the app bar).
 */
@Composable
fun FlapWordmark(
    busy: Boolean,
    modifier: Modifier = Modifier,
    label: String = "Newz",
    tileSize: DpSize = NewzTokens.tileSizeWordmark,
    heading: Boolean = true,
    board: FlapBoardState = rememberFlapBoardState(busy),
) {
    val c = palette()
    val step = board.step
    Row(
        modifier =
            modifier.clearAndSetSemantics {
                text = AnnotatedString(label)
                if (heading) heading()
            },
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.tileGap),
    ) {
        BOARD_WORD.indices.forEach {
            FlapTile(
                text = boardLetter(it, step).toString(),
                size = tileSize,
                fill = c.wordmarkTile,
                letter = c.wordmarkLetter,
                rule = c.wordmarkRule,
            )
        }
    }
}

/**
 * `LoadingBoard` — the loading indicator: four [FlapTile]s that flip through letters, one per
 * `flap-flip`, until they spell "NEWZ", then repeat; beside them the `type-row-title` [label]
 * ("Loading today’s news…"). With a null [label] the tiles alone are the pull-to-refresh indicator.
 */
@Composable
fun LoadingBoard(
    label: String?,
    modifier: Modifier = Modifier,
) {
    val c = palette()
    val step = rememberBoardStep()
    Row(
        modifier =
            modifier.padding(
                start = NewzTokens.spaceXs,
                end = NewzTokens.spaceXs,
                top = NewzTokens.spaceXs,
                bottom = NewzTokens.space2xs,
            ),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceCardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(NewzTokens.tileGap),
        ) {
            BOARD_WORD.indices.forEach { FlapTile(text = boardLetter(it, step).toString()) }
        }
        if (label != null) PlainText(text = label, style = NewzTokens.typeRowTitle, color = c.ink)
    }
}

/** `RankTile` — the rank at the start of each suggestion row: one full-size [FlapTile], spoken "Rank {n}". */
@Composable
fun RankTile(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    FlapTile(
        text = rank.toString(),
        modifier = modifier.clearAndSetSemantics { contentDescription = "Rank $rank" },
    )
}
