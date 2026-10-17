package app.newz.testing

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import app.newz.AppGraph
import app.newz.MainActivity
import app.newz.catalog.Catalog
import app.newz.ui.feed.FeedCardInfo
import app.newz.ui.feed.FeedSemantics
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Launch helpers for UI tests. [install] swaps the [AppGraph] (catalog, connectivity, Custom Tabs
 * provider, clock, a fresh in-memory database unless [keepDatabase]) and must run before
 * [launch]. Write preferences with [TestPrefs] after [install].
 */
object TestApp {
    fun install(
        catalog: Catalog,
        isOnline: () -> Boolean = { true },
        customTabsProvider: () -> String? = { null },
        clock: () -> Instant = { Clock.System.now() },
        keepDatabase: Boolean = false,
    ): AppGraph =
        AppGraph.override {
            this.catalog = catalog
            this.isOnline = isOnline
            this.customTabsProvider = customTabsProvider
            this.clock = clock
            databaseName = null
            this.keepDatabase = keepDatabase
        }

    fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)
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
