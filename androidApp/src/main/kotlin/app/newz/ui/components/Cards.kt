package app.newz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import app.newz.ui.image.rememberNewzImageLoader
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.NewzType
import app.newz.ui.theme.SignageText
import coil3.ImageLoader
import coil3.compose.AsyncImage

/**
 * `ArticleCard` — one article in the feed: a `card` surface with a [CardStrip], the
 * `type-headline` [headline] (padding 14 16, wraps, never truncated), an [ArticleImage] or
 * [NoImagePanel], then [CardActions]. Tapping anywhere outside its buttons calls [onOpen].
 * [groupCount] is the number of other outlets carrying the story (0 = not grouped).
 * [onLongPress], when set, is the long-press shortcut to the card menu.
 */
@Composable
fun ArticleCard(
    outlet: String,
    age: String,
    headline: String,
    imageUrl: String?,
    topic: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    spokenAge: String = spokenAgeOf(age),
    groupCount: Int = 0,
    onGroup: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    imageLoader: ImageLoader = rememberNewzImageLoader(),
) {
    val c = palette()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(c.card)
                .combinedClickable(onLongClick = onLongPress, onClick = onOpen),
    ) {
        CardStrip(outlet = outlet, age = age, spokenAge = spokenAge)
        PlainText(
            text = headline,
            style = NewzTokens.typeHeadline,
            color = c.ink,
            modifier =
                Modifier.fillMaxWidth().padding(
                    horizontal = NewzTokens.spaceL,
                    vertical = NewzTokens.spaceCardGap,
                ),
        )
        if (imageUrl != null) {
            ArticleImage(url = imageUrl, imageLoader = imageLoader, modifier = Modifier.testTag(ARTICLE_IMAGE_TAG))
        } else {
            Box(Modifier.testTag(NO_IMAGE_TAG)) { NoImagePanel(topic = topic) }
        }
        CardActions(onShare = onShare, onMore = onMore, groupCount = groupCount, onGroup = onGroup)
    }
}

/** Test tag on an [ArticleCard]'s [ArticleImage]. */
const val ARTICLE_IMAGE_TAG = "articleImage"

/** Test tag on the box holding an [ArticleCard]'s [NoImagePanel]. */
const val NO_IMAGE_TAG = "noImagePanel"

/**
 * `CardStrip` — the header band of an [ArticleCard]: `strip`, min height 48, padding 0 8 0 14,
 * gap 12; the outlet in `type-outlet` `sky` (wraps) with a [FlapAge] at the right. At Extra large
 * it gets 10 dp of top and bottom padding and the tiles top-align.
 */
@Composable
fun CardStrip(
    outlet: String,
    age: String,
    modifier: Modifier = Modifier,
    spokenAge: String = spokenAgeOf(age),
) {
    val c = palette()
    val xl = isExtraLargeText()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = NewzTokens.stripHeight)
                .background(c.strip)
                .padding(start = NewzTokens.spaceCardGap, end = NewzTokens.spaceS)
                .then(if (xl) Modifier.padding(vertical = NewzTokens.stripPaddingXl) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
        verticalAlignment = if (xl) Alignment.Top else Alignment.CenterVertically,
    ) {
        SignageText(text = outlet, style = NewzTokens.typeOutlet, color = c.sky, modifier = Modifier.weight(1f))
        FlapAge(age = age, spokenAge = spokenAge)
    }
}

/**
 * `ArticleImage` — the article photo at full width and 16:9, cropped to fill. It shows the
 * `image-placeholder` with `image-hatch` lines every 11 dp until it loads and keeps it if loading
 * fails. Loaded only through the guarded [imageLoader].
 */
@Composable
fun ArticleImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "Article image",
    imageLoader: ImageLoader = rememberNewzImageLoader(),
) {
    val c = palette()
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(NewzTokens.imageRatio)
            .clipToBounds()
            .hatch(c.imagePlaceholder, c.imageHatch, NewzTokens.imageHatchSpacing),
    ) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** A word drawn in [style] with its baseline [drop] below the bottom of a [height]-tall box, so it is cropped. */
@Composable
private fun CroppedWord(
    word: String,
    style: NewzType,
    height: Dp,
    inset: Dp,
    drop: Dp,
    modifier: Modifier = Modifier,
) {
    val c = palette()
    Layout(
        content = {
            Text(text = word.uppercase(), color = c.sky, style = style.textStyle, softWrap = false)
        },
        modifier = modifier.clipToBounds().clearAndSetSemantics { },
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        val h = height.roundToPx()
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        val baseline = placeable[FirstBaseline].takeIf { it > 0 } ?: placeable.height
        layout(w, h) {
            placeable.place(x = inset.roundToPx(), y = h + drop.roundToPx() - baseline)
        }
    }
}

/**
 * `NoImagePanel` — replaces [ArticleImage] when there is no image: a 104 dp `strip` panel with
 * the first [topic] in `type-tile-display` `sky`, 12 from the left, its baseline 26 dp below the
 * bottom edge so the word is cropped. Decorative: hidden from screen readers.
 */
@Composable
fun NoImagePanel(
    topic: String,
    modifier: Modifier = Modifier,
) {
    val c = palette()
    CroppedWord(
        word = topic,
        style = NewzTokens.typeTileDisplay,
        height = NewzTokens.noImageHeight,
        inset = NewzTokens.spaceM,
        drop = NewzTokens.noImageBaselineDrop,
        modifier = modifier.fillMaxWidth().background(c.strip),
    )
}

/**
 * `CardActions` — the bottom row of an [ArticleCard]: 64 tall, padding 0 4 0 12, a
 * [GroupButton] at the left when [groupCount] > 0, and Share and ⋮ [IconButton]s at the right.
 */
@Composable
fun CardActions(
    onShare: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    groupCount: Int = 0,
    onGroup: () -> Unit = {},
    shareLabel: String = "Share",
    moreLabel: String = "More actions",
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = NewzTokens.rowHeight)
                .padding(start = NewzTokens.spaceM, end = NewzTokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (groupCount > 0) GroupButton(count = groupCount, onClick = onGroup)
        Spacer(Modifier.weight(1f))
        IconButton(icon = NewzIcons.iconShare, contentDescription = shareLabel, onClick = onShare)
        IconButton(icon = NewzIcons.iconMore, contentDescription = moreLabel, onClick = onMore)
    }
}

/**
 * `SkeletonCard` — a placeholder card while the feed loads, hidden from screen readers: an
 * [ArticleCard] shape with a `tile` bar and blank [FlapTile]s in the strip, `image-placeholder`
 * headline bars, a plain image block and an empty 64 dp actions area.
 */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val c = palette()
    Column(modifier.fillMaxWidth().background(c.card).clearAndSetSemantics { }) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(NewzTokens.stripHeight)
                    .background(c.strip)
                    .padding(start = NewzTokens.spaceCardGap, end = NewzTokens.spaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.fillMaxWidth(SKELETON_OUTLET_FRACTION).height(NewzTokens.skeletonBarSmall).background(c.tile))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(NewzTokens.tileGap)) {
                FlapTile(text = "")
                FlapTile(text = "")
            }
        }
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(
                    horizontal = NewzTokens.spaceL,
                    vertical = NewzTokens.skeletonPaddingV,
                ),
            verticalArrangement = Arrangement.spacedBy(NewzTokens.skeletonGap),
        ) {
            SKELETON_LINE_FRACTIONS.forEach {
                Box(Modifier.fillMaxWidth(it).height(NewzTokens.skeletonBar).background(c.imagePlaceholder))
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(NewzTokens.imageRatio).background(c.imagePlaceholder))
        Spacer(Modifier.height(NewzTokens.rowHeight))
    }
}

private const val SKELETON_OUTLET_FRACTION = 0.4f
private val SKELETON_LINE_FRACTIONS = listOf(1f, 0.9f, 0.6f)

/**
 * `SavedCard` — one article in Saved: a 40 dp strip with the outlet at 16 sp and a small
 * [FlapAge]; a row (padding 12 12 0 16, gap 12) with the 18/24 headline and a 72 dp thumbnail
 * (hatch every 9 dp; with no image, a `strip` square with the topic in signage 800 20 `sky`);
 * then a 52 dp action row with Share and Unsave (filled bookmark) at the right.
 */
@Composable
fun SavedCard(
    outlet: String,
    age: String,
    headline: String,
    imageUrl: String?,
    topic: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onUnsave: () -> Unit,
    modifier: Modifier = Modifier,
    spokenAge: String = spokenAgeOf(age),
    shareLabel: String = "Share",
    unsaveLabel: String = "Unsave",
    imageLoader: ImageLoader = rememberNewzImageLoader(),
) {
    val c = palette()
    Column(modifier.fillMaxWidth().background(c.card).clickable(onClick = onOpen)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NewzTokens.stripHeightSaved)
                    .background(c.strip)
                    .padding(start = NewzTokens.spaceCardGap, end = NewzTokens.spaceS),
            horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignageText(
                text = outlet,
                style = NewzTokens.typeOutletSaved,
                color = c.sky,
                modifier = Modifier.weight(1f),
            )
            FlapAge(age = age, spokenAge = spokenAge, small = true)
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = NewzTokens.spaceL, top = NewzTokens.spaceM, end = NewzTokens.spaceM),
            horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
        ) {
            PlainText(
                text = headline,
                style = NewzTokens.typeHeadlineSaved,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            if (imageUrl != null) {
                Box(
                    Modifier
                        .size(NewzTokens.thumbSize)
                        .clipToBounds()
                        .hatch(c.imagePlaceholder, c.imageHatch, NewzTokens.imageHatchSpacingThumb),
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            } else {
                Box(
                    Modifier
                        .size(NewzTokens.thumbSize)
                        .background(c.strip)
                        .clipToBounds()
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = topic.uppercase(),
                        color = c.sky,
                        style = NewzTokens.typeThumbTopic.textStyle,
                        softWrap = false,
                    )
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = NewzTokens.savedActionsHeight,
                    ).padding(end = NewzTokens.spaceXs),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(icon = NewzIcons.iconShare, contentDescription = shareLabel, onClick = onShare)
            IconButton(icon = NewzIcons.iconBookmarkFilled, contentDescription = unsaveLabel, onClick = onUnsave)
        }
    }
}

/**
 * `StoryRow` — one outlet in the story-group sheet: the whole row is tappable, padding 14 12 14
 * 20, gap 12, a divider above; the outlet in `type-outlet` `ink` with an optional [Tag] and a
 * small [FlapAge] at the right, the headline 6 below in `type-row-title` 500, and a trailing
 * 22 dp `icon-open`.
 */
@Composable
fun StoryRow(
    outlet: String,
    age: String,
    headline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spokenAge: String = spokenAgeOf(age),
    tag: TagKind? = null,
    showDivider: Boolean = true,
) {
    val c = palette()
    Column(modifier.fillMaxWidth()) {
        if (showDivider) Box(Modifier.fillMaxWidth().height(NewzTokens.divider).background(c.lineSubtle))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NewzTokens.touchTarget)
                    .clickable(onClick = onClick)
                    .padding(
                        start = NewzTokens.spaceXl,
                        top = NewzTokens.spaceCardGap,
                        end = NewzTokens.spaceM,
                        bottom = NewzTokens.spaceCardGap,
                    ),
            horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceS),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SignageText(
                            text = outlet,
                            style = NewzTokens.typeOutlet,
                            color = c.ink,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (tag != null) Tag(kind = tag)
                    }
                    FlapAge(age = age, spokenAge = spokenAge, small = true)
                }
                Spacer(Modifier.height(NewzTokens.gapIconLabel))
                PlainText(
                    text = headline,
                    style = NewzTokens.typeRowTitle,
                    color = c.ink,
                    fontWeight = FontWeight.Medium,
                )
            }
            NewzIcon(NewzIcons.iconOpen, c.icon)
        }
    }
}
