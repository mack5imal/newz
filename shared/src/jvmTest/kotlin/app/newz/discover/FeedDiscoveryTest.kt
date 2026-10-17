package app.newz.discover

import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.Outlet
import app.newz.catalog.PaywallStatus
import app.newz.catalog.Topic
import app.newz.feed.FeedKind
import app.newz.net.DEFAULT_MAX_BYTES
import app.newz.net.newzHttpClient
import app.newz.store.ArticleStore
import app.newz.store.CustomFeed
import app.newz.store.UserPrefs
import app.newz.store.createSqlDriver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class FeedDiscoveryTest {
    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }

    private val catalog =
        Catalog(
            topics = listOf(Topic("tech", "Tech", "")),
            outlets = listOf(Outlet("wire", "The Wire", "wire.example", PaywallStatus.NONE, 0.5)),
            feeds = listOf(CatalogFeed("wire-tech", "wire", "Tech", "https://wire.example/tech.xml", listOf("tech"))),
        )

    private val rss =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>Tidepool Notes</title><link>https://tidepool.example/</link>
        <item><title>Older</title><link>https://tidepool.example/1</link><pubDate>Fri, 16 Oct 2026 06:00:00 GMT</pubDate></item>
        <item><title>Volunteers count a record number of sea stars</title><link>https://tidepool.example/2</link>
        <pubDate>Fri, 16 Oct 2026 09:00:00 GMT</pubDate></item>
        </channel></rss>
        """.trimIndent()

    private val atom =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom"><title>Orbit Daily</title>
        <link href="https://orbit.example/"/><id>urn:orbit</id><updated>2026-10-16T10:00:00Z</updated>
        <entry><title>Launch window opens</title><link href="https://orbit.example/a"/><id>urn:a</id>
        <updated>2026-10-16T10:00:00Z</updated></entry></feed>
        """.trimIndent()

    private fun html(head: String) =
        "<!DOCTYPE html><html><head><title>Site</title>$head</head><body><p>Hello</p></body></html>"

    private val xmlHeaders = headersOf(HttpHeaders.ContentType, "application/xml; charset=utf-8")
    private val htmlHeaders = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")

    /** A MockEngine answering from [routes] (full URL -> response); anything else is a 404. */
    private fun engine(routes: Map<String, MockRequestHandleScope.() -> HttpResponseData>) =
        MockEngine { request ->
            val url = request.url.toString()
            (routes[url] ?: routes[url.removeSuffix("/")])?.invoke(this) ?: respond("", HttpStatusCode.NotFound)
        }

    private fun discovery(engine: MockEngine) = FeedDiscovery(newzHttpClient(engine), catalog, clock)

    private suspend fun FeedDiscovery.discover(input: String) = discover(input, UserPrefs(), emptyList())

    @Test
    fun htmlPageWithRssLinkFindsTheFeed() =
        runTest {
            val e =
                engine(
                    mapOf(
                        "https://tidepool.example" to {
                            respond(
                                html(
                                    """<link rel="alternate" type="application/rss+xml" title="RSS" href="/rss?a=1&amp;b=2">""",
                                ),
                                headers = htmlHeaders,
                            )
                        },
                        "https://tidepool.example/rss?a=1&b=2" to { respond(rss, headers = xmlHeaders) },
                    ),
                )
            val found = assertIs<DiscoveryResult.Found>(discovery(e).discover("tidepool.example"))
            assertEquals("https://tidepool.example/rss?a=1&b=2", found.feedUrl)
            assertEquals(FeedKind.RSS, found.kind)
            assertEquals("Tidepool Notes", found.siteName)
            assertEquals("Volunteers count a record number of sea stars", found.latestTitle)
            assertEquals(3.hours, found.latestAge)
            assertNull(found.catalogFeedId)
            assertEquals(2, e.requestHistory.size)
        }

    @Test
    fun htmlPageWithRelativeAtomLinkFindsTheFeed() =
        runTest {
            val e =
                engine(
                    mapOf(
                        "https://orbit.example/blog/" to {
                            respond(
                                html("""<link href='feed.atom' type='application/atom+xml' rel='alternate'/>"""),
                                headers = htmlHeaders,
                            )
                        },
                        "https://orbit.example/blog/feed.atom" to { respond(atom, headers = xmlHeaders) },
                    ),
                )
            val found = assertIs<DiscoveryResult.Found>(discovery(e).discover("https://orbit.example/blog/"))
            assertEquals("https://orbit.example/blog/feed.atom", found.feedUrl)
            assertEquals(FeedKind.ATOM, found.kind)
            assertEquals("Orbit Daily", found.siteName)
            assertEquals("Launch window opens", found.latestTitle)
            assertEquals(2.hours, found.latestAge)
        }

    @Test
    fun directRssAndAtomUrlsAreFound() =
        runTest {
            val e =
                engine(
                    mapOf(
                        "https://tidepool.example/rss" to { respond(rss, headers = xmlHeaders) },
                        "https://orbit.example/feed" to { respond(atom, headers = xmlHeaders) },
                    ),
                )
            val d = discovery(e)
            val direct = assertIs<DiscoveryResult.Found>(d.discover("tidepool.example/rss"))
            assertEquals(FeedKind.RSS, direct.kind)
            assertEquals("https://tidepool.example/rss", direct.feedUrl)
            val directAtom = assertIs<DiscoveryResult.Found>(d.discover("https://orbit.example/feed"))
            assertEquals(FeedKind.ATOM, directAtom.kind)
            assertEquals(2, e.requestHistory.size)
        }

    @Test
    fun htmlPageWithoutAFeedLinkIsNoFeed() =
        runTest {
            val e =
                engine(
                    mapOf(
                        "https://plain.example" to {
                            respond(
                                html(
                                    """<link rel="stylesheet" href="/s.css"><link rel="alternate" hreflang="fr" href="/fr">""",
                                ),
                                headers = htmlHeaders,
                            )
                        },
                        "https://text.example" to { respond("just some text", headers = htmlHeaders) },
                    ),
                )
            assertEquals(DiscoveryResult.NoFeed, discovery(e).discover("plain.example"))
            assertEquals(DiscoveryResult.NoFeed, discovery(e).discover("text.example"))
            assertEquals(2, e.requestHistory.size)
        }

    @Test
    fun googleOwnedAddressesAreRefusedWithZeroRequests() =
        runTest {
            val e = engine(emptyMap())
            val d = discovery(e)
            for (input in listOf(
                "feedburner.com",
                "https://feeds.feedburner.com/x",
                "news.google.com",
                "blogspot.com",
                "youtube.com/feeds",
            )) {
                assertEquals(DiscoveryResult.Google, d.discover(input), input)
            }
            assertEquals(0, e.requestHistory.size)
        }

    @Test
    fun redirectToGoogleIsRefused() =
        runTest {
            val e =
                engine(
                    mapOf(
                        "https://sneaky.example" to {
                            respond(
                                "",
                                HttpStatusCode.Found,
                                headersOf(HttpHeaders.Location, "https://news.google.com/rss"),
                            )
                        },
                    ),
                )
            assertEquals(DiscoveryResult.Google, discovery(e).discover("sneaky.example"))
            assertEquals(listOf("sneaky.example"), e.requestHistory.map { it.url.host })
        }

    @Test
    fun declaredFeedLinkOnGoogleIsRefusedBeforeItIsRequested() =
        runTest {
            val e =
                engine(
                    mapOf(
                        "https://kitchen.example" to {
                            respond(
                                html(
                                    """<link rel="alternate" type="application/rss+xml" href="https://feeds.feedburner.com/kitchen">""",
                                ),
                                headers = htmlHeaders,
                            )
                        },
                    ),
                )
            assertEquals(DiscoveryResult.Google, discovery(e).discover("kitchen.example"))
            assertEquals(1, e.requestHistory.size)
        }

    @Test
    fun bodyOverFiveMegabytesIsTooLarge() =
        runTest {
            val big = ByteArray((DEFAULT_MAX_BYTES + 1).toInt()) { 'a'.code.toByte() }
            val e = engine(mapOf("https://huge.example" to { respond(big, headers = htmlHeaders) }))
            assertEquals(DiscoveryResult.TooLarge, discovery(e).discover("huge.example"))
        }

    @Test
    fun idnAddressIsAcceptedAndRequestedAsPunycode() =
        runTest {
            val e = engine(mapOf("https://xn--bcher-kva.example" to { respond(rss, headers = xmlHeaders) }))
            val found = assertIs<DiscoveryResult.Found>(discovery(e).discover("bücher.example"))
            assertEquals("https://xn--bcher-kva.example", found.feedUrl)
            assertEquals(listOf("xn--bcher-kva.example"), e.requestHistory.map { it.url.host })
        }

    @Test
    fun plainWordsAreAQueryAndNothingIsRequested() =
        runTest {
            val e = engine(emptyMap())
            assertEquals(Input.Query("not a url"), AddressInput.classify("not a url"))
            assertEquals(DiscoveryResult.NotAnAddress, discovery(e).discover("not a url"))
            assertEquals(DiscoveryResult.NotAnAddress, discovery(e).discover("foo..bar"))
            assertEquals(0, e.requestHistory.size)
        }

    @Test
    fun httpErrorsAreFailed() =
        runTest {
            val e = engine(emptyMap())
            assertEquals(DiscoveryResult.Failed, discovery(e).discover("missing.example"))
        }

    @Test
    fun alreadyFollowedCustomFeedIsAlreadyFollowingAndAddingTwiceKeepsOneRow() =
        runTest {
            val e = engine(mapOf("https://tidepool.example/rss" to { respond(rss, headers = xmlHeaders) }))
            val d = discovery(e)
            val store = ArticleStore(createSqlDriver())
            val found =
                assertIs<DiscoveryResult.Found>(
                    d.discover("tidepool.example/rss", UserPrefs(), store.listCustomFeeds()),
                )
            val added = store.followCustomFeed(found, now)
            assertEquals(customFeedId("https://TIDEPOOL.example/rss/#top"), added.id)
            assertEquals("Tidepool Notes", added.title)
            assertEquals("RSS", added.kind)

            // Typed again with a different case, a trailing slash and a fragment.
            val again = d.discover("https://Tidepool.Example/rss/#latest", UserPrefs(), store.listCustomFeeds())
            val following = assertIs<DiscoveryResult.AlreadyFollowing>(again)
            assertEquals(added.id, following.feedId)

            store.followCustomFeed(found, now)
            store.followCustomFeed(found.copy(feedUrl = "https://tidepool.example/rss/"), now)
            assertEquals(1, store.listCustomFeeds().size)
        }

    @Test
    fun followedCatalogFeedIsAlreadyFollowingAndUnfollowedOneIsFoundAsCatalog() =
        runTest {
            val e = engine(mapOf("https://wire.example/tech.xml" to { respond(rss, headers = xmlHeaders) }))
            val d = discovery(e)
            val notFollowed = assertIs<DiscoveryResult.Found>(d.discover("wire.example/tech.xml"))
            assertEquals("wire-tech", notFollowed.catalogFeedId)
            val prefs = UserPrefs().followOutlet("wire", catalog)
            val following =
                assertIs<DiscoveryResult.AlreadyFollowing>(
                    d.discover("wire.example/tech.xml", prefs, emptyList<CustomFeed>()),
                )
            assertEquals("wire-tech", following.feedId)
        }

    @Test
    fun feedLinkIgnoresNonFeedAlternatesAndResolvesAgainstTheFinalUrl() {
        val page =
            """<link rel="alternate" type="text/html" href="/m"><LINK REL="Alternate feed" TYPE="application/atom+xml" HREF="//cdn.example/a.xml">"""
        assertEquals("https://cdn.example/a.xml", FeedDiscovery.feedLink(page, "https://site.example/x/y"))
        assertNull(FeedDiscovery.feedLink("<link rel=alternate type=application/rss+xml>", "https://site.example/"))
    }
}
