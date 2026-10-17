package app.newz.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.newz.net.newzHttpClient
import app.newz.ui.image.BlockedImageHostException
import app.newz.ui.image.NewzImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ImageGuardTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun request(url: String) =
        ImageRequest
            .Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()

    @Test
    fun googleHostedImageFailsWithoutAnyNetworkRequest() {
        val requests = AtomicInteger(0)
        val engine = MockEngine { respond(ByteArray(0), HttpStatusCode.OK).also { requests.incrementAndGet() } }
        val loader = NewzImageLoader.create(context, newzHttpClient(engine))

        val result = runBlocking { loader.execute(request("https://lh3.googleusercontent.com/x.jpg")) }

        assertTrue("expected an error result, got $result", result is ErrorResult)
        assertTrue((result as ErrorResult).throwable is BlockedImageHostException)
        assertEquals("the fake engine saw a request", 0, requests.get())
    }

    @Test
    fun otherHostsDoReachTheGuardedClient() {
        // Control: proves the counting engine sees requests the guard lets through.
        val requests = AtomicInteger(0)
        val engine = MockEngine { respond(ByteArray(0), HttpStatusCode.OK).also { requests.incrementAndGet() } }
        val loader = NewzImageLoader.create(context, newzHttpClient(engine))

        runBlocking { loader.execute(request("https://images.example.org/photo.jpg")) }

        assertTrue("the fake engine saw no request", requests.get() >= 1)
    }
}
