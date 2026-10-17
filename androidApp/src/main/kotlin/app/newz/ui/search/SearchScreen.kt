package app.newz.ui.search

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import app.newz.discover.DiscoveryResult
import app.newz.discover.OutletResult
import app.newz.discover.TopicResult
import app.newz.feed.FeedKind
import app.newz.store.UserPrefs
import app.newz.ui.components.BoardPanel
import app.newz.ui.components.Button
import app.newz.ui.components.ButtonVariant
import app.newz.ui.components.NewzIcon
import app.newz.ui.components.NoteRow
import app.newz.ui.components.Panel
import app.newz.ui.components.PlainText
import app.newz.ui.components.SearchField
import app.newz.ui.components.StatePanel
import app.newz.ui.components.Tag
import app.newz.ui.components.TagKind
import app.newz.ui.components.tagKind
import app.newz.ui.nav.Screen
import app.newz.ui.settings.ScreenFrame
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import kotlin.time.Duration

/** Test tags on the search results. */
object SearchSemantics {
    /** An outlet result row, `search-outlet:<outletId>`. */
    fun outlet(outletId: String): String = "search-outlet:$outletId"

    /** A topic result row, `search-topic:<topicId>`. */
    fun topic(topicId: String): String = "search-topic:$topicId"

    const val FIELD = "search:field"
    const val CUSTOM_TOPIC = "search:custom-topic"
    const val ADDRESS_PANEL = "search:address"
}

@Suppress("ktlint:standard:max-line-length")
private const val HINT_FIND =
    "Find an outlet or topic by name, or paste any site’s address — Newz looks for its RSS or Atom feed."

@Suppress("ktlint:standard:max-line-length")
private const val HINT_PRIVACY =
    "Searching the built-in list happens on this phone. Checking an address only contacts that site."

@Suppress("ktlint:standard:max-line-length")
private const val GOOGLE_BODY =
    "Addresses on Google-owned sites can’t be added, so nothing you do in Newz ever reaches Google. Try the outlet’s own website instead."

// design: search

/**
 * Find sources & topics (design `search`, SPEC S17): a [SearchField] in the app bar (focused on
 * open); an empty field shows the hint panel, a word shows catalog Outlets and Topics with Follow
 * and the custom-topic offer, and a web address shows the one-site check and its result,
 * including the Google refusal. The text stays in UI state only and is never recorded.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
) {
    val text by viewModel.text.collectAsState()
    val content by viewModel.content.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Leaving search forgets the text (it is kept across a rotation only).
    val activity = LocalActivity.current
    DisposableEffect(viewModel) {
        onDispose { if (activity?.isChangingConfigurations != true) viewModel.onTextChange("") }
    }
    ScreenFrame(
        screen = Screen.Search,
        title = "Search",
        message = null,
        onDismissMessage = {},
        onBack = onBack,
        titleContent = {
            SearchField(
                value = text,
                onValueChange = viewModel::onTextChange,
                placeholder = "Outlets, topics or a web address",
                description = "Search outlets, topics or a web address",
                clearLabel = "Clear search",
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focus)
                        .testTag(SearchSemantics.FIELD),
            )
        },
    ) {
        when (val shown = content) {
            SearchContent.Hint -> HintPanel()
            is SearchContent.Results -> prefs?.let { CatalogResults(viewModel, shown.query, it) }
            is SearchContent.Address -> AddressPanel(viewModel, shown)
            SearchContent.NotAnAddress -> NotAnAddressPanel()
        }
    }
}

@Composable
private fun HintPanel() {
    Panel(title = null) {
        NoteRow(
            icon = NewzIcons.iconSearch,
            text = HINT_FIND,
        )
        NoteRow(
            icon = NewzIcons.iconRss,
            text = "Already have a feed address? Paste it here and add it directly.",
        )
        NoteRow(
            icon = NewzIcons.iconShield,
            text = HINT_PRIVACY,
        )
    }
}

@Composable
private fun CatalogResults(
    viewModel: SearchViewModel,
    query: String,
    prefs: UserPrefs,
) {
    val results = remember(query, prefs) { viewModel.results(query, prefs) }
    if (results.outlets.isNotEmpty()) {
        Panel(title = "Outlets", count = "${results.outlets.size}") {
            results.outlets.forEachIndexed { index, result ->
                OutletRow(result, showDivider = index > 0, onFollow = { viewModel.followOutlet(result.outlet.id) })
            }
        }
    }
    if (results.topics.isNotEmpty()) {
        Panel(title = "Topics", count = "${results.topics.size}") {
            results.topics.forEachIndexed { index, result ->
                TopicRow(result, showDivider = index > 0, onFollow = { viewModel.followTopic(result.topic.id) })
            }
        }
    }
    results.customTopicOffer?.let { word ->
        CustomTopicRow(word = word, onAdd = { viewModel.addCustomTopic(word) })
    }
}

@Composable
private fun OutletRow(
    result: OutletResult,
    showDivider: Boolean,
    onFollow: () -> Unit,
) {
    val topic = result.primaryTopic?.name
    val feeds = if (result.feedCount == 1) "1 feed" else "${result.feedCount} section feeds"
    ResultRow(
        title = result.outlet.name,
        subtitle =
            when {
                topic == null -> feeds
                result.feedCount == 1 -> "$topic · 1 feed"
                else -> "$topic · ${result.feedCount} section feeds"
            },
        showDivider = showDivider,
        modifier = Modifier.testTag(SearchSemantics.outlet(result.outlet.id)),
        tag = result.outlet.paywall.tagKind(),
        following = result.following && !result.removed,
        onFollow = onFollow,
    )
}

@Composable
private fun TopicRow(
    result: TopicResult,
    showDivider: Boolean,
    onFollow: () -> Unit,
) {
    val following = result.following && !result.muted
    ResultRow(
        title = result.topic.name,
        subtitle = if (following) "In your topics" else result.topic.description,
        showDivider = showDivider,
        modifier = Modifier.testTag(SearchSemantics.topic(result.topic.id)),
        following = following,
        onFollow = onFollow,
    )
}

/** A result row: title (with an optional [tag]), subtitle, then Outline "Follow" or a "Following" `Tag`. */
@Composable
internal fun ResultRow(
    title: String,
    subtitle: String?,
    showDivider: Boolean,
    following: Boolean,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
    tag: TagKind? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = LocalNewzPalette.current
    Column(modifier.fillMaxWidth()) {
        if (showDivider) RowDivider()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NewzTokens.rowHeight)
                    .padding(
                        start = NewzTokens.spaceL,
                        top = NewzTokens.spaceS,
                        end = NewzTokens.spaceS,
                        bottom = NewzTokens.spaceS,
                    ),
            horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceCardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlainText(
                        text = title,
                        style = NewzTokens.typeRowTitle,
                        color = c.ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    tag?.let { Tag(it) }
                }
                subtitle?.let { PlainText(text = it, style = NewzTokens.typeRowSub, color = c.inkMuted) }
            }
            if (following) {
                Tag(TagKind.Following, modifier = Modifier.padding(end = NewzTokens.spaceS))
            } else {
                Button(text = "Follow", onClick = onFollow, variant = ButtonVariant.Outline)
            }
        }
    }
}

@Composable
private fun RowDivider() {
    val c = LocalNewzPalette.current
    Box(Modifier.fillMaxWidth().height(NewzTokens.divider).background(c.lineSubtle))
}

@Composable
private fun CustomTopicRow(
    word: String,
    onAdd: () -> Unit,
) {
    Panel(title = null, modifier = Modifier.testTag(SearchSemantics.CUSTOM_TOPIC)) {
        NoteRow(
            icon = NewzIcons.iconPlus,
            text = "Add '$word' as a custom topic",
            subtitle = "Shows articles from your sources that mention it",
            modifier = Modifier.clickable(onClick = onAdd),
        )
    }
}

@Composable
private fun AddressPanel(
    viewModel: SearchViewModel,
    shown: SearchContent.Address,
) {
    when (val result = shown.result) {
        null -> CheckingPanel(shown.host)
        is DiscoveryResult.Found -> FoundPanel(result, alreadyFollowing = false, onAdd = { viewModel.addFeed(result) })
        is DiscoveryResult.AlreadyFollowing -> FoundPanel(result.feed, alreadyFollowing = true, onAdd = {})
        DiscoveryResult.Google -> GooglePanel()
        DiscoveryResult.NotAnAddress -> NotAnAddressPanel()
        DiscoveryResult.NoFeed, DiscoveryResult.TooLarge, DiscoveryResult.Failed -> NoFeedPanel()
    }
}

@Composable
private fun CheckingPanel(host: String) {
    val c = LocalNewzPalette.current
    val turn = rememberInfiniteTransition(label = "spinner")
    val angle by turn.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "spinner-angle",
    )
    Panel(title = "Web address", modifier = Modifier.testTag(SearchSemantics.ADDRESS_PANEL)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NewzTokens.rowHeight)
                    .padding(horizontal = NewzTokens.spaceL, vertical = NewzTokens.spaceS),
            horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceCardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewzIcon(NewzIcons.iconSpinner, c.icon, modifier = Modifier.rotate(angle))
            PlainText(text = "Looking for a feed…", style = NewzTokens.typeRowTitle, color = c.ink)
        }
        NoteRow(icon = NewzIcons.iconShield, text = "Only $host is contacted to check.", color = c.inkMuted)
    }
}

@Composable
private fun FoundPanel(
    found: DiscoveryResult.Found,
    alreadyFollowing: Boolean,
    onAdd: () -> Unit,
) {
    val c = LocalNewzPalette.current
    val address = found.feedUrl.substringAfter("://").removeSuffix("/")
    val kindLine = if (found.kind == FeedKind.ATOM) "$address · Atom" else "$address · RSS"
    Panel(title = "Feed found", modifier = Modifier.testTag(SearchSemantics.ADDRESS_PANEL)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = NewzTokens.spaceL, top = NewzTokens.spaceL, end = NewzTokens.spaceL)
                    .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceCardGap),
            verticalAlignment = Alignment.Top,
        ) {
            NewzIcon(NewzIcons.iconRss, c.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NewzTokens.spaceXs)) {
                PlainText(text = found.siteName, style = NewzTokens.typeRowTitle, color = c.ink)
                PlainText(text = kindLine, style = NewzTokens.typeRowSub, color = c.inkMuted)
                found.latestTitle?.let { headline ->
                    val line =
                        found.latestAge?.let { "Latest: “$headline” · ${shortAge(it)}" } ?: "Latest: “$headline”"
                    PlainText(text = line, style = NewzTokens.typeRowSub, color = c.inkMuted)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(NewzTokens.spaceL),
            horizontalArrangement = Arrangement.End,
        ) {
            if (alreadyFollowing) {
                Button(
                    text = "Already following",
                    onClick = {},
                    variant = ButtonVariant.Disabled,
                    icon = NewzIcons.iconCheck,
                )
            } else {
                Button(text = "Add", onClick = onAdd, icon = NewzIcons.iconPlus)
            }
        }
    }
}

@Composable
private fun NoFeedPanel() {
    StatePanel(
        title = "No feed found on this site",
        body = "Some sites don’t publish one. If you know the feed’s own address, paste that instead.",
        icon = NewzIcons.iconRssOff,
        modifier = Modifier.testTag(SearchSemantics.ADDRESS_PANEL),
    )
}

@Composable
private fun NotAnAddressPanel() {
    StatePanel(
        title = "That doesn't look like a web address",
        body = "Check the spelling — for example tidepool.example or https://tidepool.example/rss.",
        icon = NewzIcons.iconWarning,
        isError = true,
        modifier = Modifier.testTag(SearchSemantics.ADDRESS_PANEL),
    )
}

@Composable
private fun GooglePanel() {
    BoardPanel(
        title = "Newz never contacts Google",
        body = GOOGLE_BODY,
        icon = NewzIcons.iconShieldCheck,
        modifier = Modifier.testTag(SearchSemantics.ADDRESS_PANEL),
    )
}

/** `3h`-style age of the latest item, as on feed cards. */
private fun shortAge(age: Duration): String =
    when {
        age.inWholeMinutes < 1 -> "now"
        age.inWholeHours < 1 -> "${age.inWholeMinutes}m"
        age.inWholeDays < 1 -> "${age.inWholeHours}h"
        else -> "${age.inWholeDays}d"
    }

private const val FULL_TURN = 360f
private const val SPIN_MS = 900
