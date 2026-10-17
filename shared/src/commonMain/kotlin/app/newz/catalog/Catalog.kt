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
 * landed on after redirects, when it differs from [url].
 */
data class CatalogFeed(
    val id: String,
    val outletId: String,
    val section: String,
    val url: String,
    val topics: List<String>,
    val checkedUrl: String? = null,
)

/** The bundled catalog of topics, outlets and feeds, with lookups. */
class Catalog(
    val topics: List<Topic>,
    val outlets: List<Outlet>,
    val feeds: List<CatalogFeed>,
) {
    private val outletsById = outlets.associateBy { it.id }
    private val feedsById = feeds.associateBy { it.id }
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
}

/** Loads the catalog compiled into the app from `catalog/catalog.json` at build time. */
object CatalogLoader {
    private val catalog: Catalog by lazy {
        Catalog(
            topics = CatalogData.topics(),
            outlets = CatalogData.outlets(),
            feeds = CatalogData.feeds(),
        )
    }

    fun load(): Catalog = catalog
}
