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
            val engine =
                MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html")) }

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
            val engine = MockEngine { respond("", HttpStatusCode.Forbidden) }
            assertEquals(
                FetchResult.HttpError(403),
                newzHttpClient(engine).fetchStart("https://news.example.org/a", PageImageFinder.PAGE_START_BYTES),
            )
        }

    private companion object {
        const val CHUNK = 1024

        const val READ_CHUNK = 8 * 1024

        /** Far less than the endless body; what Ktor's pipeline may copy ahead of the reader. */
        const val MAX_READ_AHEAD = 1024 * 1024

        const val BIG_PAGE = 32L * 1024 * 1024
    }
}
