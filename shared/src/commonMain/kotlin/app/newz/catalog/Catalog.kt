package app.newz.catalog

/** One onboarding topic (SPEC S26). [id] is the stable key stored in preferences. */
data class Topic(
    val id: String,
    val name: String,
    val description: String,
)

/** Whether an outlet's articles sit behind a paywall. Hand-assigned; users can correct it (S12). */
enum class PaywallStatus { NONE, METERED, HARD }

/** A publisher. [popularity] is a 0..1 score baked in from a public domain ranking. */
data class Outlet(
    val id: String,
    val name: String,
    val domain: String,
    val paywall: PaywallStatus,
    val popularity: Double,
)

/**
 * One bundled feed: an outlet's section. [checkedUrl] is the URL the catalog health check
 * landed on after redirects, when it differs from [url]. A local feed names its [metroId].
 */
data class CatalogFeed(
    val id: String,
    val outletId: String,
    val section: String,
    val url: String,
    val topics: List<String>,
    val checkedUrl: String? = null,
    val metroId: String? = null,
)

/**
 * A US metropolitan statistical area the Local topic covers (SPEC S29). [cbsa] is its Census
 * CBSA code, [population] the latest Census estimate, and [lat]/[lon] its Census internal point.
 */
data class Metro(
    val id: String,
    val cbsa: String,
    val name: String,
    val population: Int,
    val lat: Double,
    val lon: Double,
)

/** The bundled catalog of topics, metros, outlets and feeds, with lookups. */
class Catalog(
    val topics: List<Topic>,
    val outlets: List<Outlet>,
    val feeds: List<CatalogFeed>,
    val metros: List<Metro> = emptyList(),
) {
    private val outletsById = outlets.associateBy { it.id }
    private val feedsById = feeds.associateBy { it.id }
    private val metrosById = metros.associateBy { it.id }
    private val feedsByMetro: Map<String, List<CatalogFeed>> =
        feeds
            .filter { LOCAL_TOPIC in it.topics }
            .mapNotNull { feed -> feed.metroId?.let { it to feed } }
            .groupBy({ it.first }, { it.second })
    private val feedsByTopic: Map<String, List<CatalogFeed>> =
        buildMap<String, MutableList<CatalogFeed>> {
            for (feed in feeds) {
                for (topic in feed.topics) getOrPut(topic) { mutableListOf() }.add(feed)
            }
        }

    /** Feeds tagged with [topicId], in catalog order; empty for an unknown topic. */
    fun feedsForTopic(topicId: String): List<CatalogFeed> = feedsByTopic[topicId].orEmpty()

    fun outlet(id: String): Outlet? = outletsById[id]

    fun feed(id: String): CatalogFeed? = feedsById[id]

    fun topic(id: String): Topic? = topics.firstOrNull { it.id == id }

    fun metro(id: String): Metro? = metrosById[id]

    /** The Local-topic feeds for [metroId], in catalog order; empty for an unknown metro. */
    fun localFeeds(metroId: String): List<CatalogFeed> = feedsByMetro[metroId].orEmpty()

    companion object {
        /** The topic id local feeds carry. */
        const val LOCAL_TOPIC = "local"
    }
}

/** Loads the catalog compiled into the app from `catalog/catalog.json` at build time. */
object CatalogLoader {
    private val catalog: Catalog by lazy {
        Catalog(
            topics = CatalogData.topics(),
            outlets = CatalogData.outlets(),
            feeds = CatalogData.feeds(),
            metros = CatalogData.metros(),
        )
    }

    fun load(): Catalog = catalog
}
