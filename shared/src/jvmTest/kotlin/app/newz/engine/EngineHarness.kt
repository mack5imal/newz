package app.newz.engine

import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.Outlet
import app.newz.catalog.PaywallStatus
import app.newz.catalog.Topic
import app.newz.net.newzHttpClient
import app.newz.store.ArticleStore
import app.newz.store.UserPrefs
import app.newz.store.createSqlDriver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** One RSS item served by [EngineHarness]. */
data class TestItem(
    val guid: String,
    val title: String,
    val age: Duration = 1.hours,
    val url: String = "https://news.example/$guid",
)

/**
 * A FeedRepository wired to an in-memory store and a counting MockEngine. Feeds `a`, `b`, `c`
 * (outlets of the same name, topic `world`) are served from [items]; a feed listed in
 * [failing] answers HTTP 500. While [gate] is set every request waits on it.
 */
class EngineHarness(
    feedIds: List<String> = listOf("a", "b", "c"),
) {
    val now: Instant = Instant.parse("2026-10-16T12:00:00Z")

    val catalog =
        Catalog(
            topics = listOf(Topic("world", "World", ""), Topic("tech", "Tech", "")),
            outlets = feedIds.map { Outlet(it, it.uppercase(), "$it.example", PaywallStatus.NONE, 1.0) },
            feeds = feedIds.map { CatalogFeed("$it-world", it, "World", urlOf(it), listOf("world")) },
        )

    val prefs =
        MutableStateFlow(
            UserPrefs(
                followedTopics = listOf("world"),
                followedOutlets = feedIds.associateWith { setOf("$it-world") },
            ),
        )

    val store = ArticleStore(createSqlDriver())
    val items = ConcurrentHashMap<String, List<TestItem>>()
    val failing: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val requests = ConcurrentHashMap<String, AtomicInteger>()
    private val inFlight = AtomicInteger()
    val maxInFlight = AtomicInteger()

    @Volatile var gate: CompletableDeferred<Unit>? = null

    @Volatile var online = true

    /** Completed once [expected] requests have arrived. */
    @Volatile var arrived: Pair<Int, CompletableDeferred<Unit>>? = null

    val engine =
        MockEngine { request ->
            val url = request.url.toString()
            requests.computeIfAbsent(url) { AtomicInteger() }.incrementAndGet()
            val total = requests.values.sumOf { it.get() }
            arrived?.let { (expected, signal) -> if (total >= expected) signal.complete(Unit) }
            val running = inFlight.incrementAndGet()
            maxInFlight.accumulateAndGet(running) { a, b -> maxOf(a, b) }
            try {
                gate?.await()
                val feedId = feedIds.first { urlOf(it) == url }
                if (feedId in failing) {
                    respond("", HttpStatusCode.InternalServerError)
                } else {
                    respond(
                        rss(items[feedId].orEmpty()),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/rss+xml; charset=utf-8"),
                    )
                }
            } finally {
                inFlight.decrementAndGet()
            }
        }

    fun requestsTo(feedId: String): Int = requests[urlOf(feedId)]?.get() ?: 0

    fun repository(
        scope: CoroutineScope,
        pageSize: Int = FeedPager.DEFAULT_PAGE_SIZE,
    ) = FeedRepository(
        scope = scope,
        store = store,
        prefs = prefs,
        catalog = catalog,
        client = newzHttpClient(engine),
        isOnline = { online },
        clock = { now },
        pageSize = pageSize,
    )

    fun rss(list: List<TestItem>): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>T</title>")
            for (item in list) {
                val date = (now - item.age).format(DateTimeComponents.Formats.RFC_1123)
                append("<item><title>${item.title}</title><link>${item.url}</link>")
                append("<guid isPermaLink=\"false\">${item.guid}</guid><pubDate>$date</pubDate></item>")
            }
            append("</channel></rss>")
        }

    companion object {
        fun urlOf(feedId: String) = "https://$feedId.example/feed.xml"

        /** A title of made-up words unique to [n], so no two titles share a term. */
        fun uniqueTitle(n: Int): String = listOf(n, n + 100_000, n + 200_000).joinToString(" ") { word(it) }

        private fun word(n: Int): String {
            var x = n
            val sb = StringBuilder("q")
            do {
                sb.append('a' + x % 26)
                x /= 26
            } while (x > 0)
            return sb.toString()
        }
    }
}
