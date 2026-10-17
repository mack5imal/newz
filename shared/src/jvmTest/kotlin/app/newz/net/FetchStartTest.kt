package app.newz.net

import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.Source
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** [fetchStart] reads the first 64 KB of an article page and stops (B5). */
class FetchStartTest {
    /**
     * An endless body: it hands out [CHUNK] bytes each time the reader runs dry and counts them,
     * so [handedOut] is everything the client ever pulled. Reading it to the end never finishes.
     */
    @OptIn(InternalAPI::class)
    private class EndlessBody : ByteReadChannel {
        var handedOut = 0L
        var cancelled = false
        private val buffer = Buffer()

        override val closedCause: Throwable? get() = null
        override val isClosedForRead: Boolean get() = cancelled
        override val readBuffer: Source get() = buffer

        override suspend fun awaitContent(min: Int): Boolean {
            if (cancelled) return false
            while (buffer.size < min) {
                buffer.write(ByteArray(CHUNK) { 'x'.code.toByte() })
                handedOut += CHUNK
            }
            return true
        }

        override fun cancel(cause: Throwable?) {
            cancelled = true
        }
    }

    @Test
    fun readsAtMost64KbOfAnEndlessPageThenStops() =
        runTest {
            val body = EndlessBody()
            val ranges = mutableListOf<String?>()
            val engine =
                MockEngine { request ->
                    ranges += request.headers[HttpHeaders.Range]
                    // A server that ignores the range answers 200 with the whole (endless) page.
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
                }

            val result =
                newzHttpClient(
                    engine,
                ).fetchStart("https://news.example.org/a", PageImageFinder.PAGE_START_BYTES)

            assertIs<FetchResult.Success>(result)
            assertEquals(PageImageFinder.PAGE_START_BYTES, result.bytes.size)
            // The body never ends, so returning at all means fetchStart stopped reading; Ktor's
            // own response pipeline may have copied a little further ahead before the close.
            assertTrue(body.cancelled, "the response body was not closed after the first 64 KB")
            assertTrue(body.handedOut < MAX_READ_AHEAD, "the client pulled ${body.handedOut} bytes")
            assertEquals(listOf<String?>(RANGE), ranges, "asked once, for the first 64 KB")
        }

    @Test
    fun partialContentAnswerIsReadAsTheStartOfThePage() =
        runTest {
            val html = "<html><head><meta property=\"og:image\" content=\"/a.jpg\"><title>T</title>"
            val ranges = mutableListOf<String?>()
            val engine =
                MockEngine { request ->
                    ranges += request.headers[HttpHeaders.Range]
                    respond(
                        html,
                        HttpStatusCode.PartialContent,
                        headersOf(
                            HttpHeaders.ContentType to listOf("text/html; charset=utf-8"),
                            HttpHeaders.ContentRange to listOf("bytes 0-${html.length - 1}/250000"),
                        ),
                    )
                }
            val result =
                newzHttpClient(
                    engine,
                ).fetchStart("https://news.example.org/a", PageImageFinder.PAGE_START_BYTES)
            assertIs<FetchResult.Success>(result)
            assertEquals(206, result.status)
            assertEquals(html, result.bytes.decodeToString())
            assertEquals(listOf<String?>(RANGE), ranges)
        }

    @Test
    fun rangeNotSatisfiableIsAskedOnceMoreWithoutTheRange() =
        runTest {
            val html = "<html><head><meta property=\"og:image\" content=\"/a.jpg\"></head></html>"
            val ranges = mutableListOf<String?>()
            val engine =
                MockEngine { request ->
                    val range = request.headers[HttpHeaders.Range]
                    ranges += range
                    if (range != null) respond("", HttpStatusCode.RequestedRangeNotSatisfiable) else respond(html)
                }
            val result =
                newzHttpClient(
                    engine,
                ).fetchStart("https://news.example.org/a", PageImageFinder.PAGE_START_BYTES)
            assertIs<FetchResult.Success>(result)
            assertEquals(html, result.bytes.decodeToString())
            assertEquals(listOf(RANGE, null), ranges)
        }

    /**
     * Over a real socket (the JVM's OkHttp engine against a local server): a 32 MB page is cut
     * off after the first 64 KB. The server counts what it managed to send before the client
     * hung up; socket buffers let some more through, but nowhere near the whole page.
     */
    @Test
    fun realConnectionIsClosedAfterTheFirst64Kb() =
        runBlocking {
            val sent = AtomicLong()
            val finished = CompletableDeferred<Unit>()
            val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            server.createContext("/page") { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/html")
                exchange.sendResponseHeaders(200, BIG_PAGE)
                val chunk = ByteArray(READ_CHUNK) { 'x'.code.toByte() }
                try {
                    exchange.responseBody.use { out ->
                        while (sent.get() < BIG_PAGE) {
                            out.write(chunk)
                            sent.addAndGet(chunk.size.toLong())
                        }
                    }
                } catch (_: IOException) {
                    // The client closed the connection: the expected end.
                } finally {
                    finished.complete(Unit)
                }
            }
            server.start()
            try {
                val url = "http://127.0.0.1:${server.address.port}/page"
                val result = newzHttpClient().fetchStart(url, PageImageFinder.PAGE_START_BYTES)
                assertIs<FetchResult.Success>(result)
                assertEquals(PageImageFinder.PAGE_START_BYTES, result.bytes.size)
                withTimeout(10_000) { finished.await() }
                println("fetchStart: server ignoring Range sent ${sent.get()} bytes of $BIG_PAGE")
                assertTrue(sent.get() < BIG_PAGE / 4, "the server sent ${sent.get()} of $BIG_PAGE bytes")
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun shortPageIsReadWhole() =
        runTest {
            val html = "<html><head><meta property=\"og:image\" content=\"/a.jpg\"></head></html>"
            val engine = MockEngine { respond(html, HttpStatusCode.OK) }
            val result =
                newzHttpClient(
                    engine,
                ).fetchStart("https://news.example.org/a", PageImageFinder.PAGE_START_BYTES)
            assertIs<FetchResult.Success>(result)
            assertEquals(html, result.bytes.decodeToString())
        }

    @Test
    fun errorStatusIsReported() =
        runTest {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond("", HttpStatusCode.Forbidden)
                }
            assertEquals(
                FetchResult.HttpError(403),
                newzHttpClient(engine).fetchStart("https://news.example.org/a", PageImageFinder.PAGE_START_BYTES),
            )
            // A 403 may be the range's fault, so it is asked once more without it; then it stands.
            assertEquals(2, requests)
        }

    @Test
    fun missingPageIsNotAskedTwice() =
        runTest {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond("", HttpStatusCode.NotFound)
                }
            assertEquals(
                FetchResult.HttpError(404),
                newzHttpClient(engine).fetchStart("https://news.example.org/a", PageImageFinder.PAGE_START_BYTES),
            )
            assertEquals(1, requests)
        }

    /**
     * Over a real socket, through a redirect: the server honours the range on the page the
     * redirect leads to, so only the first 64 KB of a 32 MB page ever leaves it.
     */
    @Test
    fun serverThatHonoursTheRangeSendsOnly64Kb() =
        runBlocking {
            val sent = AtomicLong()
            val pageRanges = mutableListOf<String?>()
            val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            server.createContext("/start") { exchange ->
                exchange.responseHeaders.add("Location", "/page")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            server.createContext("/page") { exchange ->
                val range = exchange.requestHeaders.getFirst("Range")
                synchronized(pageRanges) { pageRanges += range }
                val end =
                    Regex("bytes=0-(\\d+)")
                        .matchEntire(range.orEmpty())
                        ?.groupValues
                        ?.get(1)
                        ?.toLong()
                val length = if (end != null) minOf(end + 1, BIG_PAGE) else BIG_PAGE
                exchange.responseHeaders.add("Content-Type", "text/html")
                if (end != null) exchange.responseHeaders.add("Content-Range", "bytes 0-${length - 1}/$BIG_PAGE")
                exchange.sendResponseHeaders(if (end != null) 206 else 200, length)
                val chunk = ByteArray(READ_CHUNK) { 'x'.code.toByte() }
                try {
                    exchange.responseBody.use { out ->
                        while (sent.get() < length) {
                            val n = minOf(chunk.size.toLong(), length - sent.get()).toInt()
                            out.write(chunk, 0, n)
                            sent.addAndGet(n.toLong())
                        }
                    }
                } catch (_: IOException) {
                    // The client hung up.
                }
            }
            server.start()
            try {
                val url = "http://127.0.0.1:${server.address.port}/start"
                val result = newzHttpClient().fetchStart(url, PageImageFinder.PAGE_START_BYTES)
                assertIs<FetchResult.Success>(result)
                assertEquals(206, result.status)
                assertEquals(PageImageFinder.PAGE_START_BYTES, result.bytes.size)
                assertEquals(listOf<String?>(RANGE), pageRanges, "the redirect hop kept the range")
                println("fetchStart: server honouring Range sent ${sent.get()} bytes of $BIG_PAGE")
                assertEquals(PageImageFinder.PAGE_START_BYTES.toLong(), sent.get())
            } finally {
                server.stop(0)
            }
        }

    /**
     * A gzipped page whose ranged answer stops mid-stream: decoding ends with "unexpected end of
     * stream", and the bytes decoded until then still give the page's og:image.
     */
    @Test
    fun cutOffGzippedPartialContentStillGivesThePicture() =
        runBlocking {
            val random = java.util.Random(7)
            val filler =
                buildString {
                    // Barely compressible text, so the cut lands before 64 KB of page is decoded.
                    while (length < 400_000) append(java.lang.Long.toString(random.nextLong(), 36)).append(' ')
                }
            val html =
                "<html><head><meta property=\"og:image\" content=\"https://img.example.org/lead.jpg\">" +
                    "<title>T</title></head><body><p>$filler</p></body></html>"
            val gzipped =
                java.io
                    .ByteArrayOutputStream()
                    .also { out ->
                        java.util.zip
                            .GZIPOutputStream(out)
                            .use { it.write(html.encodeToByteArray()) }
                    }.toByteArray()
            val cut = gzipped.copyOf(CUT_GZIP_BYTES)
            val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            server.createContext("/page") { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.responseHeaders.add("Content-Encoding", "gzip")
                exchange.responseHeaders.add("Content-Range", "bytes 0-${cut.size - 1}/${gzipped.size}")
                exchange.sendResponseHeaders(206, cut.size.toLong())
                exchange.responseBody.use { it.write(cut) }
            }
            server.start()
            try {
                val url = "http://127.0.0.1:${server.address.port}/page"
                val result = newzHttpClient().fetchStart(url, PageImageFinder.PAGE_START_BYTES)
                assertIs<FetchResult.Success>(result)
                assertEquals(206, result.status)
                val text = result.bytes.decodeToString()
                assertTrue(
                    result.bytes.size in 1 until PageImageFinder.PAGE_START_BYTES,
                    "decoded ${result.bytes.size}",
                )
                assertTrue(html.startsWith(text), "the decoded bytes are the start of the page")
                assertEquals("https://img.example.org/lead.jpg", PageImageFinder(newzHttpClient()).find(url))
            } finally {
                server.stop(0)
            }
        }

    /** gzip + range over a real socket when the cut lands after 64 KB of page: a full 64 KB comes back. */
    @Test
    fun gzippedPartialContentLongerThanTheCapGivesTheFirst64Kb() =
        runBlocking {
            val html = "<html><head><title>T</title></head><body>" + "<p>Paragraph text.</p>".repeat(50_000)
            val gzipped =
                java.io
                    .ByteArrayOutputStream()
                    .also { out ->
                        java.util.zip
                            .GZIPOutputStream(out)
                            .use { it.write(html.encodeToByteArray()) }
                    }.toByteArray()
            val cut = gzipped.copyOf(minOf(gzipped.size, PageImageFinder.PAGE_START_BYTES))
            val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            server.createContext("/page") { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/html")
                exchange.responseHeaders.add("Content-Encoding", "gzip")
                exchange.sendResponseHeaders(206, cut.size.toLong())
                exchange.responseBody.use { it.write(cut) }
            }
            server.start()
            try {
                val result =
                    newzHttpClient().fetchStart(
                        "http://127.0.0.1:${server.address.port}/page",
                        PageImageFinder.PAGE_START_BYTES,
                    )
                assertIs<FetchResult.Success>(result)
                assertEquals(PageImageFinder.PAGE_START_BYTES, result.bytes.size)
                assertTrue(html.startsWith(result.bytes.decodeToString()))
            } finally {
                server.stop(0)
            }
        }

    private companion object {
        const val CHUNK = 1024

        const val READ_CHUNK = 8 * 1024

        /** Far less than the endless body; what Ktor's pipeline may copy ahead of the reader. */
        const val MAX_READ_AHEAD = 1024 * 1024

        const val BIG_PAGE = 32L * 1024 * 1024

        /** The Range header fetchStart sends for the first 64 KB. */
        const val RANGE = "bytes=0-65535"

        /** Where the cut gzip answer stops: well short of 64 KB of decoded page. */
        const val CUT_GZIP_BYTES = 20_000
    }
}
