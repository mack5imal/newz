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
