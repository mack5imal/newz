package app.newz.net

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual fun platformHttpEngine(): HttpClientEngine =
    Darwin.create {
        configureSession {
            // No cookie storage and no Cookie header: Newz never sends or keeps cookies.
            HTTPShouldSetCookies = false
            HTTPCookieStorage = null
        }
    }

/**
 * NSURLSession offers no public way to ask for its canonical host, so iOS relies on
 * [HostGuard.normalizeHost] alone (which decodes `xn--` labels). iOS is not shipped.
 */
internal actual fun engineCanonicalHost(host: String): String? = host

/** iOS is not shipped; its image downloads are bounded only by the request timeout. */
internal actual fun platformImageEngine(maxBodyBytes: Long): HttpClientEngine = platformHttpEngine()
