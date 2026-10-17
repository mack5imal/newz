package app.newz.testing

import app.newz.AppGraph
import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.Metro
import app.newz.catalog.Outlet
import app.newz.catalog.PaywallStatus
import app.newz.catalog.Topic
import app.newz.store.UserPrefs
import kotlinx.coroutines.runBlocking

/**
 * A catalog whose feeds all point at a [MockFeedServer]: feed `n` belongs to outlet `out-n`
 * ("Outlet n"), is catalog feed `feed-n`, and carries one topic from [FEED_TOPICS] in turn
 * (world, science, tech, business, world, ...). [HEALTH] is a catalog topic with no feeds, for
 * the empty-filter state.
 */
object TestCatalog {
    const val WORLD = "world"
    const val SCIENCE = "science"
    const val TECH = "tech"
    const val BUSINESS = "business"
    const val HEALTH = "health"
    const val LOCAL = Catalog.LOCAL_TOPIC

    /** Metro ids as the bundled zip table names them (98101 lies in Seattle). */
    const val SEATTLE = "seattle"
    const val CHICAGO = "chicago"

    val FEED_TOPICS = listOf(WORLD, SCIENCE, TECH, BUSINESS)

    val topics: List<Topic> =
        listOf(
            Topic(WORLD, "World", "World news"),
            Topic(SCIENCE, "Science", "Science news"),
            Topic(TECH, "Tech", "Technology news"),
            Topic(BUSINESS, "Business", "Business news"),
            Topic(HEALTH, "Health", "Health news"),
            Topic(LOCAL, "Local", "News near you"),
        )

    val metros: List<Metro> =
        listOf(
            Metro(CHICAGO, "16980", "Chicago", 9_434_123, 41.75878, -87.841887),
            Metro(SEATTLE, "42660", "Seattle", 4_161_883, 47.475046, -121.813743),
        )

    fun outletId(feed: Int): String = "out-$feed"

    fun outletName(feed: Int): String = "Outlet $feed"

    fun feedId(feed: Int): String = "feed-$feed"

    fun topicOf(feed: Int): String = FEED_TOPICS[feed % FEED_TOPICS.size]

    /**
     * One outlet and one feed per server feed, the first [feeds] of them. [paywall] sets each
     * outlet's catalog paywall status (default none). A feed [localMetro] names is a Local feed
     * of that metro (topic [LOCAL]) instead of a [topicOf] feed. The catalog carries [metros].
     */
    fun build(
        server: MockFeedServer,
        feeds: Int = server.feedCount,
        localMetro: (feed: Int) -> String? = { null },
        paywall: (feed: Int) -> PaywallStatus = { PaywallStatus.NONE },
    ): Catalog =
        Catalog(
            topics = topics,
            outlets = List(feeds) { Outlet(outletId(it), outletName(it), "outlet$it.test", paywall(it), 0.5) },
            feeds =
                List(feeds) {
                    val metro = localMetro(it)
                    val topic = if (metro != null) LOCAL else topicOf(it)
                    CatalogFeed(feedId(it), outletId(it), "Main", server.feedUrl(it), listOf(topic), metroId = metro)
                },
            metros = metros,
        )
}

/** Writes preferences before an activity launches. Call after [AppGraph.override]. */
object TestPrefs {
    /**
     * Onboarded and following [topics] (default: every feed topic plus [TestCatalog.HEALTH]), with
     * every catalog outlet that has a feed in those topics followed with all its feeds.
     */
    fun onboarded(vararg topics: String): UserPrefs {
        val catalog = AppGraph.get().catalog
        val followed = if (topics.isEmpty()) TestCatalog.FEED_TOPICS + TestCatalog.HEALTH else topics.toList()
        val outlets =
            catalog.feeds
                .filter { feed -> feed.topics.any { it in followed } }
                .groupBy({ it.outletId }, { it.id })
                .mapValues { (_, ids) -> ids.toSet() }
        return write(UserPrefs(onboarded = true, followedTopics = followed, followedOutlets = outlets))
    }

    /** Replaces the stored preferences with [prefs] and returns them. */
    fun write(prefs: UserPrefs): UserPrefs {
        runBlocking { AppGraph.get().preferencesStore.replace(prefs) }
        return prefs
    }
}
