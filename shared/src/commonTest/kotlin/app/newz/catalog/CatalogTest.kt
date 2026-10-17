package app.newz.catalog

import app.newz.net.HostGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CatalogTest {
    private val catalog = CatalogLoader.load()

    private val designTopics =
        listOf(
            "World",
            "US",
            "Politics",
            "Business",
            "Tech",
            "Science",
            "Health",
            "Sports",
            "Entertainment",
            "Climate",
            "Culture",
            "Travel",
            "Food",
            "Gaming",
            "Space",
            "Education",
            "Markets",
            "Books",
            "Local",
            "Opinion",
        )

    private val local get() = catalog.topics.single { it.name == "Local" }

    private fun outletByDomain(domain: String): Outlet? = catalog.outlets.firstOrNull { it.domain == domain }

    @Test
    fun loaderReturnsTheSameCatalog() {
        assertSame(catalog, CatalogLoader.load())
    }

    @Test
    fun hasAtLeast150NationalFeedsFromAtLeast90Outlets() {
        val national = catalog.feeds.filter { feed -> feed.topics.any { it != local.id } }
        assertTrue(national.size >= 150, "national feeds: ${national.size}")
        val outlets = national.map { it.outletId }.toSet()
        assertTrue(outlets.size >= 90, "distinct outlets: ${outlets.size}")
    }

    @Test
    fun topicsAreExactlyTheDesignTopicsInOrder() {
        assertEquals(designTopics, catalog.topics.map { it.name })
        assertEquals(
            catalog.topics.size,
            catalog.topics
                .map { it.id }
                .toSet()
                .size,
        )
        catalog.topics.forEach { assertTrue(it.description.isNotBlank(), "${it.id} has no description") }
    }

    @Test
    fun everyNonLocalTopicHasAtLeastThreeFeeds() {
        for (topic in catalog.topics) {
            if (topic.id == local.id) continue
            val count = catalog.feedsForTopic(topic.id).size
            assertTrue(count >= 3, "${topic.name} has $count feeds")
        }
    }

    @Test
    fun everyFeedIsComplete() {
        val topicIds = catalog.topics.map { it.id }.toSet()
        for (feed in catalog.feeds) {
            assertTrue(feed.url.startsWith("https://") || feed.url.startsWith("http://"), "${feed.id}: url")
            assertTrue(feed.topics.isNotEmpty(), "${feed.id}: no topic")
            feed.topics.forEach { assertTrue(it in topicIds, "${feed.id}: unknown topic $it") }
            assertTrue(feed.section.isNotBlank(), "${feed.id}: section")
            val outlet = assertNotNull(catalog.outlet(feed.outletId), "${feed.id}: unknown outlet ${feed.outletId}")
            assertTrue(outlet.popularity in 0.0..1.0, "${outlet.id}: popularity ${outlet.popularity}")
            assertTrue(outlet.paywall in PaywallStatus.entries, "${outlet.id}: paywall")
            assertTrue(outlet.name.isNotBlank() && outlet.domain.isNotBlank(), "${outlet.id}: name/domain")
        }
    }

    @Test
    fun feedIdsAndUrlsAreUnique() {
        assertEquals(
            catalog.feeds.size,
            catalog.feeds
                .map { it.id }
                .toSet()
                .size,
            "duplicate feed id",
        )
        assertEquals(
            catalog.feeds.size,
            catalog.feeds
                .map { it.url }
                .toSet()
                .size,
            "duplicate feed url",
        )
        assertEquals(
            catalog.outlets.size,
            catalog.outlets
                .map { it.id }
                .toSet()
                .size,
            "duplicate outlet id",
        )
    }

    @Test
    fun noFeedOrCheckedUrlIsBlockedByHostGuard() {
        for (feed in catalog.feeds) {
            assertFalse(HostGuard.isBlocked(feed.url), "${feed.id}: ${feed.url}")
            feed.checkedUrl?.let { assertFalse(HostGuard.isBlocked(it), "${feed.id}: checked $it") }
        }
    }

    @Test
    fun paywallsMatchTheHandAssignedClasses() {
        val hard = listOf("wsj.com", "ft.com", "economist.com", "theathletic.com", "fortune.com", "businessinsider.com")
        val metered =
            listOf(
                "nytimes.com",
                "bloomberg.com",
                "theatlantic.com",
                "newyorker.com",
                "wired.com",
                "latimes.com",
                "time.com",
            )
        for (domain in hard) {
            outletByDomain(domain)?.let { assertEquals(PaywallStatus.HARD, it.paywall, domain) }
        }
        for (domain in metered) {
            outletByDomain(domain)?.let { assertEquals(PaywallStatus.METERED, it.paywall, domain) }
        }
        // The hard-paywall outlets the spec names as definitely present.
        listOf("wsj.com", "ft.com", "economist.com", "fortune.com", "businessinsider.com").forEach {
            assertNotNull(outletByDomain(it), "$it missing")
        }
    }

    @Test
    fun excludedOutletsAreAbsent() {
        val excluded =
            listOf("apnews.com", "ap.org", "reuters.com", "cnn.com", "usatoday.com", "washingtonpost.com", "ign.com")

        fun covers(
            host: String,
            domain: String,
        ) = host == domain || host.endsWith(".$domain")
        for (outlet in catalog.outlets) {
            excluded.forEach { assertFalse(covers(outlet.domain.lowercase(), it), "${outlet.id} is excluded ($it)") }
        }
        for (feed in catalog.feeds) {
            val urls = listOfNotNull(feed.url, feed.checkedUrl)
            for (url in urls) {
                val host =
                    url
                        .substringAfter("://")
                        .substringBefore('/')
                        .substringBefore('?')
                        .lowercase()
                excluded.forEach { assertFalse(covers(host, it), "${feed.id} is served by excluded $it") }
            }
        }
    }

    @Test
    fun lookupsFindFeedsAndOutlets() {
        val first = catalog.feeds.first()
        assertEquals(first, catalog.feed(first.id))
        assertEquals(first.outletId, catalog.outlet(first.outletId)?.id)
        assertTrue(first in catalog.feedsForTopic(first.topics.first()))
        assertTrue(catalog.feedsForTopic("no-such-topic").isEmpty())
        assertEquals(null, catalog.feed("no-such-feed"))
    }
}
