package app.newz.testing

import android.graphics.Bitmap
import android.graphics.Color
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * An on-device RSS server for UI tests (phases 12-17 reuse it). It listens on `127.0.0.1` (the
 * debug build's network security config allows cleartext to loopback only) and serves:
 *
 * - `GET /feed/<n>.xml` — feed `n` (0 until [feedCount]) as RSS 2.0, built from [feed]`(n)`:
 *   its [FeedConfig.items], after [FeedConfig.delayMs], with [FeedConfig.status] (a non-200
 *   status sends an empty body).
 * - `GET /img/<name>.png` — a small solid PNG, for article images ([imageUrl]).
 *
 * Typical use:
 * ```
 * val server = MockFeedServer(feedCount = 30).start()
 * server.fill(itemsPerFeed = 3)                 // unique headlines, newest first, every 2nd with an image
 * server.feed(4).status = 500                   // one failing source
 * server.feeds.forEach { it.delayMs = 1_000 }   // slow network
 * val catalog = TestCatalog.build(server)       // one outlet + feed per server feed
 * ...
 * server.close()                                // later requests fail: the app reads it as offline
 * ```
 * Configuration may change between requests; reads and writes are thread-safe enough for tests
 * (each [FeedConfig] field is volatile). [requests] counts every request served; [requestLog]
 * keeps each one's line, URL, headers and body.
 */
class MockFeedServer(
    val feedCount: Int = 30,
) : Closeable {
    /** One served feed. [items] are written in list order. */
    class FeedConfig {
        @Volatile var delayMs: Long = 0

        @Volatile var status: Int = 200

        @Volatile var items: List<FeedItem> = emptyList()
    }

    /** One RSS `<item>`. [published] null omits `<pubDate>`; [imageUrl] null sends no image. */
    data class FeedItem(
        val guid: String,
        val title: String,
        val link: String,
        val published: Instant?,
        val imageUrl: String? = null,
        val summary: String? = null,
    )

    private val server = MockWebServer()
    private val configs = List(feedCount) { FeedConfig() }
    private val png: ByteArray by lazy { solidPng() }

    /** Every request served so far. */
    val requests = AtomicInteger()

    private val log = ConcurrentLinkedQueue<String>()

    /**
     * Every request served so far as text: its request line, full URL, headers and body. Privacy
     * tests search it for data that must never leave the phone (the Local zip code).
     */
    val requestLog: List<String> get() = log.toList()

    val feeds: List<FeedConfig> get() = configs

    fun feed(index: Int): FeedConfig = configs[index]

    /** Starts listening on 127.0.0.1 at a free port. */
    fun start(): MockFeedServer {
        server.dispatcher = FeedDispatcher()
        server.start(InetAddress.getByName(HOST), 0)
        return this
    }

    val baseUrl: String get() = "http://$HOST:${server.port}"

    fun feedUrl(index: Int): String = "$baseUrl/feed/$index.xml"

    /** An article link; never fetched by the app (articles open in the browser). */
    fun articleUrl(
        feed: Int,
        item: Int,
    ): String = "$baseUrl/article/$feed/$item"

    fun imageUrl(name: String): String = "$baseUrl/img/$name.png"

    /**
     * Gives every feed [itemsPerFeed] items with [Headlines.next] titles (never similar enough to
     * be grouped as one story), dated [spacing] apart starting [newestAge] before [now]; every
     * [imageEvery]-th item (1-based) carries an image, the rest none (0 = no images).
     */
    fun fill(
        itemsPerFeed: Int,
        now: Instant = Clock.System.now(),
        newestAge: Duration = 1.hours,
        spacing: Duration = 1.hours,
        imageEvery: Int = 2,
    ) {
        for (f in 0 until feedCount) {
            configs[f].items =
                List(itemsPerFeed) { i ->
                    item(
                        feed = f,
                        index = i,
                        published = now - newestAge - spacing * i - spacing * f / feedCount,
                        withImage = imageEvery > 0 && (i + 1) % imageEvery == 0,
                    )
                }
        }
    }

    /** One item of feed [feed] with a unique headline; [index] makes its guid and link. */
    fun item(
        feed: Int,
        index: Int,
        published: Instant?,
        withImage: Boolean = false,
        title: String = Headlines.next(),
    ): FeedItem =
        FeedItem(
            guid = "f$feed-i$index",
            title = title,
            link = articleUrl(feed, index),
            published = published,
            imageUrl = if (withImage) imageUrl("f$feed-i$index") else null,
            summary = null,
        )

    /** Stops the server; requests made afterwards fail to connect. Safe to call twice. */
    override fun close() {
        server.close()
    }

    private inner class FeedDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            requests.incrementAndGet()
            val body = request.body?.utf8().orEmpty()
            log.add("${request.requestLine}\n${request.url}\n${request.headers}\n$body")
            val path = request.url.encodedPath
            FEED_PATH.matchEntire(path)?.let { m ->
                val index = m.groupValues[1].toInt()
                val config = configs.getOrNull(index) ?: return notFound()
                val builder = MockResponse.Builder().code(config.status)
                if (config.delayMs > 0) builder.headersDelay(config.delayMs, TimeUnit.MILLISECONDS)
                if (config.status == 200) {
                    builder
                        .setHeader(
                            "Content-Type",
                            "application/rss+xml; charset=utf-8",
                        ).body(rss(index, config.items))
                }
                return builder.build()
            }
            if (IMAGE_PATH.matches(path)) {
                return MockResponse
                    .Builder()
                    .setHeader("Content-Type", "image/png")
                    .body(Buffer().write(png))
                    .build()
            }
            return notFound()
        }

        private fun notFound(): MockResponse = MockResponse.Builder().code(404).build()
    }

    private fun rss(
        index: Int,
        items: List<FeedItem>,
    ): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<rss version="2.0"><channel>""")
            append("<title>Test feed $index</title><link>${xml(baseUrl)}/</link><description>Feed $index</description>")
            for (item in items) {
                append("<item>")
                append("<title>${xml(item.title)}</title>")
                append("<link>${xml(item.link)}</link>")
                append("""<guid isPermaLink="false">${xml(item.guid)}</guid>""")
                item.published?.let { append("<pubDate>$it</pubDate>") }
                item.summary?.let { append("<description>${xml(it)}</description>") }
                item.imageUrl?.let { append("""<enclosure url="${xml(it)}" type="image/png" length="0"/>""") }
                append("</item>")
            }
            append("</channel></rss>")
        }

    private fun xml(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun solidPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(IMAGE_W, IMAGE_H, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(0x6A, 0x8F, 0xB8))
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    companion object {
        const val HOST = "127.0.0.1"
        private const val IMAGE_W = 32
        private const val IMAGE_H = 18
        private val FEED_PATH = Regex("/feed/(\\d+)\\.xml")
        private val IMAGE_PATH = Regex("/img/[^/]+\\.png")
    }
}

/**
 * Headlines that never look like the same story: each is six made-up words used nowhere else in
 * the process, so the on-device story grouper never joins two of them.
 */
object Headlines {
    private val syllables = listOf("ka", "lo", "mi", "ru", "te", "va", "zo", "pe", "ni", "sa", "do", "fu")
    private val counter = AtomicInteger()

    fun next(): String = List(WORDS) { word(counter.getAndIncrement()) }.joinToString(" ")

    private fun word(n: Int): String {
        var k = n
        val sb = StringBuilder()
        repeat(SYLLABLES_PER_WORD) {
            sb.append(syllables[k % syllables.size])
            k /= syllables.size
        }
        return sb.toString().replaceFirstChar { it.uppercase() }
    }

    private const val WORDS = 6
    private const val SYLLABLES_PER_WORD = 4
}
