package app.newz.net

import app.newz.core.NewzInfo
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class NewzHttpClientTest {
    private val gzipBody =
        byteArrayOf(
            31,
            -117,
            8,
            0,
            0,
            0,
            0,
            0,
            2,
            -1,
            -77,
            41,
            42,
            46,
            -74,
            75,
            -81,
            -54,
            44,
            80,
            -56,
            -49,
            -74,
            -47,
            7,
            113,
            0,
            79,
            -20,
            48,
            57,
            18,
            0,
            0,
            0,
        )

    @Test
    fun redirectToGoogleHostIsRefusedBeforeItIsSent() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.url.host == "publisher.example") {
                        respond(
                            "",
                            HttpStatusCode.MovedPermanently,
                            headersOf(HttpHeaders.Location, "https://feedproxy.google.com/x"),
                        )
                    } else {
                        respond("should never be reached")
                    }
                }
            val client = newzHttpClient(engine)
            val error = assertFailsWith<BlockedHostException> { client.fetchCapped("https://publisher.example/feed") }
            assertEquals("https://feedproxy.google.com/x", error.url)
            assertEquals(listOf("publisher.example"), engine.requestHistory.map { it.url.host })
        }

    @Test
    fun blockedInitialUrlNeverReachesEngine() =
        runTest {
            val engine = MockEngine { respond("x") }
            val client = newzHttpClient(engine)
            assertFailsWith<BlockedHostException> { client.fetchCapped("https://www.youtube.com/feeds/videos.xml") }
            assertEquals(0, engine.requestHistory.size)
        }

    @Test
    fun followsAllowedRedirectsUpToFive() =
        runTest {
            val engine =
                MockEngine { request ->
                    val hop =
                        request.url.encodedPath
                            .removePrefix("/")
                            .toIntOrNull() ?: 0
                    if (hop < 5) {
                        respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "/${hop + 1}"))
                    } else {
                        respond("done")
                    }
                }
            val result = newzHttpClient(engine).fetchCapped("https://publisher.example/0")
            assertIs<FetchResult.Success>(result)
            assertEquals("done", result.bytes.decodeToString())
            assertEquals("https://publisher.example/5", result.finalUrl)
            assertEquals(6, engine.requestHistory.size)
        }

    @Test
    fun sixthRedirectFails() =
        runTest {
            val engine =
                MockEngine { request ->
                    val hop =
                        request.url.encodedPath
                            .removePrefix("/")
                            .toInt()
                    respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/${hop + 1}"))
                }
            assertFailsWith<TooManyRedirectsException> {
                newzHttpClient(
                    engine,
                ).fetchCapped("https://publisher.example/0")
            }
            assertEquals(6, engine.requestHistory.size)
        }

    @Test
    fun gzipBodyIsDecoded() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        ByteReadChannel(gzipBody),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentEncoding, "gzip"),
                    )
                }
            val result = newzHttpClient(engine).fetchCapped("https://publisher.example/feed")
            assertIs<FetchResult.Success>(result)
            assertEquals("<rss>gzip ok</rss>", result.bytes.decodeToString())
        }

    @Test
    fun bodyOverFiveMegabytesIsTooLarge() =
        runTest {
            val engine = MockEngine { respond(ByteArray(5_000_001) { 'a'.code.toByte() }) }
            assertSame(FetchResult.TooLarge, newzHttpClient(engine).fetchCapped("https://publisher.example/big"))
        }

    @Test
    fun bodyOfExactlyFiveMegabytesIsReturned() =
        runTest {
            val engine = MockEngine { respond(ByteArray(5_000_000)) }
            val result = newzHttpClient(engine).fetchCapped("https://publisher.example/max")
            assertIs<FetchResult.Success>(result)
            assertEquals(5_000_000, result.bytes.size)
        }

    @Test
    fun sendsFixedUserAgentAndNoCookiesOrExtraParameters() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        "ok",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/rss+xml; charset=ISO-8859-1"),
                    )
                }
            val result = newzHttpClient(engine).fetchCapped("https://publisher.example/feed?a=1")
            assertIs<FetchResult.Success>(result)
            assertEquals("ISO-8859-1", result.charset)
            val sent = engine.requestHistory.single()
            assertEquals(NewzInfo.USER_AGENT, sent.headers[HttpHeaders.UserAgent])
            assertNull(sent.headers[HttpHeaders.Cookie])
            assertEquals("https://publisher.example/feed?a=1", sent.url.toString())
        }

    @Test
    fun httpErrorStatusIsReported() =
        runTest {
            val engine = MockEngine { respond("nope", HttpStatusCode.Forbidden) }
            assertEquals(
                FetchResult.HttpError(403),
                newzHttpClient(engine).fetchCapped("https://publisher.example/feed"),
            )
        }
}
