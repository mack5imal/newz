package app.newz.net

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CookieJar

internal actual fun platformHttpEngine(): HttpClientEngine =
    OkHttp.create {
        config {
            followRedirects(false)
            followSslRedirects(false)
            cookieJar(CookieJar.NO_COOKIES)
        }
    }
