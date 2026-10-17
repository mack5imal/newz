package app.newz.testing

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import app.newz.AppGraph
import app.newz.MainActivity
import app.newz.catalog.Catalog
import app.newz.net.newzHttpClient
import app.newz.store.UserPrefs
import app.newz.ui.feed.FeedCardInfo
import app.newz.ui.feed.FeedSemantics
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Launch helpers for UI tests. [install] swaps the [AppGraph] (catalog, connectivity, Custom Tabs
 * provider, clock, HTTP client, a fresh in-memory database and fresh test preferences unless
 * [keepDatabase]) and must run before [launch]. Write preferences with [TestPrefs] after [install].
 * Tests keep their preferences in [PREFS_FILE], never the app's real `newz.preferences_pb`.
 */
object TestApp {
    /** The tests' own preferences file, next to the app's real one. */
    const val PREFS_FILE = "newz-test.preferences_pb"

    fun install(
        catalog: Catalog,
        isOnline: () -> Boolean = { true },
        customTabsProvider: () -> String? = { null },
        clock: () -> Instant = { Clock.System.now() },
        keepDatabase: Boolean = false,
        httpClient: () -> HttpClient = { newzHttpClient() },
    ): AppGraph {
        val graph =
            AppGraph.override {
                this.catalog = catalog
                this.isOnline = isOnline
                this.customTabsProvider = customTabsProvider
                this.clock = clock
                databaseName = null
                prefsFileName = PREFS_FILE
                this.keepDatabase = keepDatabase
                this.httpClient = httpClient
            }
        // A relaunch on the same database keeps its preferences too; anything else starts fresh.
        if (!keepDatabase) runBlocking { graph.preferencesStore.replace(UserPrefs()) }
        return graph
    }

    fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)

    /**
     * The app's own guarded client with a counter behind the guard: [hosts] gets the host of
     * every request the app actually sends (each redirect hop), nothing the guard refused.
     */
    fun recordingClient(hosts: MutableCollection<String>): () -> HttpClient =
        {
            newzHttpClient().also { client ->
                client.plugin(HttpSend).intercept { request ->
                    hosts.add(request.url.host)
                    execute(request)
                }
            }
        }
}

/** Every card the feed has served, in order (read off the list's semantics). */
fun ComposeTestRule.servedCards(): List<FeedCardInfo> {
    val nodes = onAllNodesWithTag(FeedSemantics.LIST_TAG).fetchSemanticsNodes()
    return nodes
        .firstOrNull()
        ?.config
        ?.getOrElse(FeedSemantics.Cards) { emptyList() }
        .orEmpty()
}

/** The cards currently composed on screen, top to bottom. */
fun ComposeTestRule.visibleCards(): List<FeedCardInfo> =
    onAllNodesWithTag(FeedSemantics.CARD_TAG)
        .fetchSemanticsNodes()
        .sortedBy { it.positionInRoot.y }
        .map { it.config[FeedSemantics.Card] }

fun ComposeTestRule.waitForCards(timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) { onAllNodesWithTag(FeedSemantics.CARD_TAG).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeTestRule.waitForText(
    text: String,
    timeoutMs: Long = 10_000,
) {
    waitUntil(timeoutMs) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}

/** Waits until the served card list has stopped changing for [quietMs] (loads have settled). */
fun ComposeTestRule.waitForStableFeed(
    minCards: Int = 1,
    quietMs: Long = 1_000,
    timeoutMs: Long = 15_000,
) {
    var last: List<String> = emptyList()
    var since = System.currentTimeMillis()
    waitUntil(timeoutMs) {
        val ids = servedCards().map { it.id }
        val now = System.currentTimeMillis()
        if (ids != last) {
            last = ids
            since = now
        }
        ids.size >= minCards && now - since >= quietMs
    }
    onNodeWithTag(FeedSemantics.LIST_TAG).assertExists()
}

/** Scrolls to the card headed [title] and opens its card menu with ⋮ ("More actions"). */
fun ComposeTestRule.openCardMenu(title: String) {
    val card = hasTestTag(FeedSemantics.CARD_TAG) and hasText(title)
    onNodeWithTag(FeedSemantics.LIST_TAG).performScrollToNode(card)
    onNode(hasContentDescription("More actions") and hasAnyAncestor(card)).performClick()
    waitForText("Hide this article")
}

/** Taps the sheet row (or button) labelled [label] once it is shown. */
fun ComposeTestRule.tapText(label: String) {
    waitForText(label)
    onNodeWithText(label).performClick()
}

/** The stored preferences right now. */
fun currentPrefs(): UserPrefs =
    runBlocking {
        AppGraph
            .get()
            .preferencesStore.prefs
            .first()
    }

/** The server feed index an article link (`/article/<feed>/<item>`) belongs to. */
fun feedOf(url: String): Int =
    url
        .substringAfter("/article/")
        .substringBefore('/')
        .toInt()
