package app.newz.net

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Settings › Better pictures (B5): the `og:image` / `twitter:image` parser and page reads. */
class PageImageFinderTest {
    private val page = "https://news.example.org/2026/10/story.html"

    private fun head(vararg tags: String) =
        "<!doctype html><html><head><title>Story</title>${tags.joinToString("")}</head><body>…</body></html>"

    @Test
    fun ogImageWithPropertyFirst() {
        val html = head("""<meta property="og:image" content="https://cdn.example.org/a.jpg">""")
        assertEquals("https://cdn.example.org/a.jpg", PageImageFinder.imageOf(html, page))
    }

    @Test
    fun ogImageWithContentFirstSingleQuotesAndSelfClosing() {
        val html = head("""<META content='https://cdn.example.org/b.jpg' Property='og:image' />""")
        assertEquals("https://cdn.example.org/b.jpg", PageImageFinder.imageOf(html, page))
    }

    @Test
    fun ogImageAsNameAndUnquoted() {
        val html = head("""<meta name=og:image content=https://cdn.example.org/c.jpg>""")
        assertEquals("https://cdn.example.org/c.jpg", PageImageFinder.imageOf(html, page))
    }

    @Test
    fun ogImageWinsOverAnEarlierTwitterImage() {
        val html =
            head(
                """<meta name="twitter:image" content="https://cdn.example.org/t.jpg">""",
                """<meta property="og:image" content="https://cdn.example.org/o.jpg">""",
            )
        assertEquals("https://cdn.example.org/o.jpg", PageImageFinder.imageOf(html, page))
    }

    @Test
    fun twitterImageIsTheFallback() {
        val html =
            head(
                """<meta property="og:title" content="Story">""",
                """<meta name="twitter:image" content="https://cdn.example.org/t.jpg">""",
            )
        assertEquals("https://cdn.example.org/t.jpg", PageImageFinder.imageOf(html, page))
    }

    @Test
    fun relativeAndProtocolRelativeUrlsResolveAgainstThePage() {
        assertEquals(
            "https://news.example.org/img/d.jpg",
            PageImageFinder.imageOf(head("""<meta property="og:image" content="/img/d.jpg">"""), page),
        )
        assertEquals(
            "https://news.example.org/2026/10/e.jpg",
            PageImageFinder.imageOf(head("""<meta property="og:image" content="e.jpg">"""), page),
        )
        assertEquals(
            "https://cdn.example.org/f.jpg",
            PageImageFinder.imageOf(head("""<meta property="og:image" content="//cdn.example.org/f.jpg">"""), page),
        )
    }

    @Test
    fun entitiesInTheUrlAreDecoded() {
        val html = head("""<meta property="og:image" content="https://cdn.example.org/g.jpg?w=1200&amp;h=630">""")
        assertEquals("https://cdn.example.org/g.jpg?w=1200&h=630", PageImageFinder.imageOf(html, page))
    }

    @Test
    fun googleHostedImageIsRejectedAndTheNextOneUsed() {
        val html =
            head(
                """<meta property="og:image" content="https://lh3.googleusercontent.com/x.jpg">""",
                """<meta name="twitter:image" content="https://cdn.example.org/h.jpg">""",
            )
        assertEquals("https://cdn.example.org/h.jpg", PageImageFinder.imageOf(html, page))
        val onlyGoogle = head("""<meta property="og:image" content="https://i.ytimg.com/vi/x/hq.jpg">""")
        assertNull(PageImageFinder.imageOf(onlyGoogle, page))
    }

    @Test
    fun nonWebSchemesAndEmptyContentAreIgnored() {
        val html =
            head(
                """<meta property="og:image" content="">""",
                """<meta property="og:image" content="data:image/png;base64,AAAA">""",
                """<meta property="og:image" content="javascript:alert(1)">""",
            )
        assertNull(PageImageFinder.imageOf(html, page))
    }

    @Test
    fun pageWithoutAPictureHasNone() {
        assertNull(PageImageFinder.imageOf(head("""<meta name="description" content="x">"""), page))
    }

    @Test
    fun findReadsThePageAndResolvesAgainstItsFinalAddress() =
        runTest {
            val requested = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    requested += request.url.toString()
                    when (request.url.encodedPath) {
                        "/old" -> {
                            respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "/new/story"))
                        }

                        else -> {
                            respond(
                                head("""<meta property="og:image" content="pic.jpg">"""),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                            )
                        }
                    }
                }
            val finder = PageImageFinder(newzHttpClient(engine))
            assertEquals("https://news.example.org/new/pic.jpg", finder.find("https://news.example.org/old"))
            assertEquals(listOf("https://news.example.org/old", "https://news.example.org/new/story"), requested)
        }

    @Test
    fun findSendsNothingForAGoogleArticleOrRedirect() =
        runTest {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://news.google.com/x"))
                }
            val finder = PageImageFinder(newzHttpClient(engine))
            assertNull(finder.find("https://www.youtube.com/watch?v=x"))
            assertEquals(0, requests)
            assertNull(finder.find("https://news.example.org/story"))
            assertEquals(1, requests)
        }

    @Test
    fun findReturnsNullForAnErrorStatus() =
        runTest {
            val engine = MockEngine { respond("gone", HttpStatusCode.NotFound) }
            assertNull(PageImageFinder(newzHttpClient(engine)).find(page))
        }
}
