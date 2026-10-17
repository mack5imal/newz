package app.newz.net

import app.newz.core.NewzInfo
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.HttpClientCall
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.Sender
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.isSecure
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readAvailable

/** Thrown before a request (or a redirect hop) to a host [HostGuard] blocks is sent. */
class BlockedHostException(
    val url: String,
) : IllegalStateException("Blocked host: $url")

/** Thrown when a request is redirected more than [MAX_REDIRECTS] times. */
class TooManyRedirectsException(
    val url: String,
) : IllegalStateException("Too many redirects: $url")

/** Outcome of [fetchCapped]. */
sealed interface FetchResult {
    class Success(
        val bytes: ByteArray,
        /** The `charset` parameter of the response Content-Type, if any. */
        val charset: String?,
        /** The URL after redirects. */
        val finalUrl: String,
        /** The HTTP status of the final response (always 2xx). */
        val status: Int = 200,
    ) : FetchResult

    /** The body exceeded the cap; reading stopped at the first byte past it. */
    data object TooLarge : FetchResult

    data class HttpError(
        val status: Int,
    ) : FetchResult
}

const val MAX_REDIRECTS = 5
const val REQUEST_TIMEOUT_MILLIS = 15_000L
const val CONNECT_TIMEOUT_MILLIS = 10_000L
const val DEFAULT_MAX_BYTES = 5_000_000L

private val REDIRECT_STATUSES =
    setOf(
        HttpStatusCode.MovedPermanently,
        HttpStatusCode.Found,
        HttpStatusCode.SeeOther,
        HttpStatusCode.TemporaryRedirect,
        HttpStatusCode.PermanentRedirect,
    )

/** The platform's engine with redirects and cookies turned off (Ktor follows redirects itself). */
internal expect fun platformHttpEngine(): HttpClientEngine

/**
 * The one HTTP client Newz uses. Fixed User-Agent, gzip/deflate, 15 s request / 10 s connect
 * timeouts, no cookies, and up to [MAX_REDIRECTS] redirects with [HostGuard] checked before
 * every hop is sent. Pass [engine] only in tests.
 */
fun newzHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        // Redirects are followed by the guarded sender below, never by Ktor or the engine.
        followRedirects = false
        expectSuccess = false
        install(UserAgent) { agent = NewzInfo.USER_AGENT }
        install(ContentEncoding) {
            gzip()
            deflate()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            // Without this the engine's own read timeout (OkHttp: 10 s) cuts off a slow feed
            // before the 15 s request timeout; a feed that answers in 10 s still loads.
            socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
    }
    val client = HttpClient(engine ?: platformHttpEngine(), config)
    client.plugin(HttpSend).intercept { request -> guardedExecute(request) }
    return client
}

// takeFromWithExecutionContext keeps the original request's Job, so HttpTimeout and caller
// cancellation still cover every redirect hop; Ktor's own HttpRedirect does the same.
@OptIn(InternalAPI::class)
private suspend fun Sender.guardedExecute(request: HttpRequestBuilder): HttpClientCall {
    var builder = request
    var hops = 0
    while (true) {
        val target = builder.url.buildString()
        if (HostGuard.isBlocked(target)) throw BlockedHostException(target)
        val call = execute(builder)
        if (call.response.status !in REDIRECT_STATUSES) return call
        val location = call.response.headers[HttpHeaders.Location] ?: return call
        if (hops >= MAX_REDIRECTS) throw TooManyRedirectsException(target)
        hops++
        val wasSecure = builder.url.protocol.isSecure()
        val status = call.response.status
        val previous = builder
        builder =
            HttpRequestBuilder().apply {
                takeFromWithExecutionContext(previous)
                url.parameters.clear()
                url.takeFrom(location)
                if (status == HttpStatusCode.SeeOther) method = HttpMethod.Get
            }
        // Like Ktor's own redirect plugin, never follow an https -> http downgrade.
        if (wasSecure && builder.url.protocol != URLProtocol.HTTPS) return call
    }
}

/**
 * GETs [url] and returns at most [maxBytes] bytes of the decoded body. Bodies larger than
 * that return [FetchResult.TooLarge] after reading one byte past the cap, never more.
 */
suspend fun HttpClient.fetchCapped(
    url: String,
    maxBytes: Long = DEFAULT_MAX_BYTES,
): FetchResult =
    prepareGet(url).execute { response ->
        if (!response.status.isSuccess()) return@execute FetchResult.HttpError(response.status.value)
        val channel = response.bodyAsChannel()
        val chunks = ArrayList<ByteArray>()
        val chunk = ByteArray(CHUNK_SIZE)
        var total = 0L
        while (true) {
            val read = channel.readAvailable(chunk, 0, chunk.size)
            if (read < 0) break
            if (read == 0) continue
            total += read
            if (total > maxBytes) return@execute FetchResult.TooLarge
            chunks.add(chunk.copyOf(read))
        }
        val bytes = ByteArray(total.toInt())
        var offset = 0
        for (part in chunks) {
            part.copyInto(bytes, offset)
            offset += part.size
        }
        FetchResult.Success(
            bytes = bytes,
            charset = response.contentType()?.parameter("charset"),
            finalUrl =
                response.call.request.url
                    .toString(),
            status = response.status.value,
        )
    }

/**
 * GETs [url] and returns only the first [maxBytes] bytes of the decoded body (all of it when it
 * is shorter), then stops: the rest is never read, and the response is closed. Used to read the
 * start of an article page without downloading the page.
 */
suspend fun HttpClient.fetchStart(
    url: String,
    maxBytes: Int,
): FetchResult =
    prepareGet(url).execute { response ->
        if (!response.status.isSuccess()) return@execute FetchResult.HttpError(response.status.value)
        val channel = response.bodyAsChannel()
        val bytes = ByteArray(maxBytes)
        var total = 0
        while (total < maxBytes) {
            // Never asks for more than is still wanted, so no byte past the cap is read.
            val read = channel.readAvailable(bytes, total, minOf(CHUNK_SIZE, maxBytes - total))
            if (read < 0) break
            total += read
        }
        FetchResult.Success(
            bytes = if (total == maxBytes) bytes else bytes.copyOf(total),
            charset = response.contentType()?.parameter("charset"),
            finalUrl =
                response.call.request.url
                    .toString(),
            status = response.status.value,
        )
    }

private const val CHUNK_SIZE = 8 * 1024
