package app.newz.net

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

// This file is compiled into both the jvm and the android targets (shared/build.gradle.kts adds
// src/okhttpMain/kotlin to each), so the guard on the real engine exists exactly once.

internal actual fun platformHttpEngine(): HttpClientEngine = okHttpEngine()

/**
 * OkHttp with redirects and cookies off, and with [HostGuard] applied again inside the engine
 * (B23): an application interceptor, a network interceptor that runs on every connection, and a
 * DNS wrapper. Ktor's guarded sender already checks every hop, but OkHttp canonicalises hosts
 * itself, so the last word belongs to the host OkHttp will actually connect to. [dns] is the
 * resolver to wrap; tests pass a recording one.
 */
internal fun okHttpEngine(dns: Dns = Dns.SYSTEM): HttpClientEngine =
    OkHttp.create {
        config {
            followRedirects(false)
            followSslRedirects(false)
            cookieJar(CookieJar.NO_COOKIES)
            dns(GuardedDns(dns))
            addInterceptor(EngineHostGuard)
            addNetworkInterceptor(EngineHostGuard)
        }
    }

/** OkHttp's own reading of [host]: percent-decoded, UTS-46 mapped and punycode-encoded. */
internal actual fun engineCanonicalHost(host: String): String? = "http://$host/".toHttpUrlOrNull()?.host

/**
 * Thrown inside OkHttp for a blocked host. It must be an [IOException]: OkHttp turns any other
 * exception from an interceptor into a crash on its dispatcher thread.
 */
class EngineBlockedHostException(
    host: String,
) : IOException("Blocked host: $host")

private object EngineHostGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (HostGuard.isBlockedHost(host)) throw EngineBlockedHostException(host)
        return chain.proceed(chain.request())
    }
}

private class GuardedDns(
    private val delegate: Dns,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (HostGuard.isBlockedHost(hostname)) throw UnknownHostException("Blocked host: $hostname")
        return delegate.lookup(hostname)
    }
}
