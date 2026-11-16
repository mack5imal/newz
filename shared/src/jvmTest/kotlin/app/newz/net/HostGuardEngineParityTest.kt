package app.newz.net

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * B23 (review R1): HostGuard must judge the host the engine will actually connect to. OkHttp
 * applies full UTS-46 mapping and decodes existing punycode, so a host that HostGuard reads one
 * way can reach Google the other way. These run on the real OkHttp engine with a recording
 * [Dns] that never touches the network.
 */
class HostGuardEngineParityTest {
    /** URLs that OkHttp canonicalises to a Google host. Soft hyphen and ZWSP are escaped. */
    private val disguised =
        listOf(
            "https://xn--google-.com/rss",
            "https://feeds.xn--feedburner-.com/x",
            "https://www.xn--youtube-.com/",
            "https://ⓖoogle.com/",
            "https://goo­gle.com/",
            "https://www.go​ogle.com/",
            "https://x.blogſpot.com/",
            "https://x.blog%C5%BFpot.com/a.jpg",
            "https://feedburner.coᴹ/",
            "https://𝐠oogle.com/",
        )

    private class RecordingDns : Dns {
        val lookups: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun lookup(hostname: String): List<InetAddress> {
            lookups += hostname
            throw UnknownHostException("test DNS: $hostname")
        }
    }

    @Test
    fun okHttpReallyMapsEachInputToAGoogleHost() {
        // Guards the test itself: if OkHttp stopped mapping these, the cases below prove nothing.
        for (url in disguised) {
            val host = url.toHttpUrlOrNull()?.host ?: fail("OkHttp rejects $url")
            assertTrue(HostGuard.isBlockedHost(host), "$url -> $host")
        }
    }

    @Test
    fun hostGuardBlocksEveryDisguisedGoogleHost() {
        for (url in disguised) {
            assertTrue(HostGuard.isBlocked(url), url)
        }
    }

    @Test
    fun theGuardedClientNeverResolvesADisguisedHost() =
        runBlocking {
            val dns = RecordingDns()
            val client = newzHttpClient(okHttpEngine(dns))
            for (url in disguised) {
                runCatching { client.get(url) }
            }
            assertEquals(emptyList(), dns.lookups.toList())
        }

    @Test
    fun theEngineItselfRefusesBlockedHostsWithoutTheKtorGuard() =
        runBlocking {
            // A plain client over the engine, without guardedExecute: the engine's own
            // interceptors and DNS wrapper must still refuse before any lookup.
            val dns = RecordingDns()
            val client = HttpClient(okHttpEngine(dns)) { followRedirects = false }
            val literals = listOf("https://142.250.80.46/x.jpg", "https://[::ffff:8.8.8.8]/", "https://134744072/")
            for (url in disguised + "https://google.com/" + literals) {
                val error = runCatching { client.get(url) }.exceptionOrNull()
                assertTrue(error != null, url)
            }
            assertEquals(emptyList(), dns.lookups.toList())
        }

    @Test
    fun anOrdinaryHostStillReachesDns() =
        runBlocking {
            val dns = RecordingDns()
            val client = newzHttpClient(okHttpEngine(dns))
            runCatching { client.get("https://example.org/feed") }
            assertEquals(listOf("example.org"), dns.lookups.toList())
        }
}
