package app.newz.net

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B27 (review R16): the image client stops a picture download past [MAX_IMAGE_BYTES], so a feed
 * that names a huge file cannot use up the user's data.
 */
class ImageSizeCapTest {
    private val chunk = ByteArray(64 * 1024) { 'x'.code.toByte() }

    private fun withServer(
        total: Long,
        declareLength: Boolean,
        block: suspend (url: String, sent: AtomicLong) -> Unit,
    ) {
        val sent = AtomicLong()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/img") { exchange ->
            exchange.responseHeaders.add("Content-Type", "image/jpeg")
            exchange.sendResponseHeaders(200, if (declareLength) total else 0)
            try {
                exchange.responseBody.use { out ->
                    while (sent.get() < total) {
                        out.write(chunk)
                        sent.addAndGet(chunk.size.toLong())
                    }
                }
            } catch (_: IOException) {
                // The client closed the connection: the expected end.
            }
        }
        server.start()
        try {
            runBlocking { block("http://127.0.0.1:${server.address.port}/img", sent) }
        } finally {
            server.stop(0)
        }
    }

    // Over loopback the kernel's socket buffers take the whole body before the client stops
    // reading, so the server's byte count proves nothing; the cap's own exception does.
    private fun Throwable?.isCap(): Boolean =
        generateSequence(this) { it.cause }.any { it is ResponseTooLargeException }

    @Test
    fun aDeclaredLengthOverTheCapIsRefusedBeforeTheBodyIsRead() =
        withServer(total = 3 * MAX_IMAGE_BYTES, declareLength = true) { url, _ ->
            val error = runCatching { newzImageHttpClient().get(url).bodyAsBytes() }.exceptionOrNull()
            assertTrue(error.isCap(), "expected the size cap, got $error")
        }

    @Test
    fun anUndeclaredLengthIsCutOffAtTheCap() =
        withServer(total = 3 * MAX_IMAGE_BYTES, declareLength = false) { url, _ ->
            val error = runCatching { newzImageHttpClient().get(url).bodyAsBytes() }.exceptionOrNull()
            assertTrue(error.isCap(), "expected the size cap, got $error")
        }

    @Test
    fun anImageUnderTheCapLoads() =
        withServer(total = 1_000_000, declareLength = false) { url, _ ->
            val bytes = newzImageHttpClient().get(url).bodyAsBytes()
            // The server writes whole chunks until it has sent at least a million bytes.
            assertEquals(16 * chunk.size, bytes.size)
        }
}
