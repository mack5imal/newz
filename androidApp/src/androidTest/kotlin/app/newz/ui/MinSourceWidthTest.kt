package app.newz.ui

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.newz.net.newzHttpClient
import app.newz.ui.image.MIN_PICTURE_SOURCE_WIDTH
import app.newz.ui.image.NewzImageLoader
import app.newz.ui.image.TinyImageException
import app.newz.ui.image.minSourceWidth
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * The 300 px rule (B5) measures the picture's source, not what Coil decodes: Coil scales a
 * 90 px source up to the card's width, so the decoded size can't tell a thumbnail from a photo.
 */
@RunWith(AndroidJUnit4::class)
class MinSourceWidthTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun png(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    private fun loader(bytes: ByteArray) =
        NewzImageLoader.create(
            context,
            newzHttpClient(
                MockEngine { respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png")) },
            ),
        )

    /** A card-sized request (1080×608, exact), as `AsyncImage` makes for a full-width card. */
    private fun request(withRule: Boolean) =
        ImageRequest
            .Builder(context)
            .data("https://images.example.org/picture.png")
            .size(CARD_W, CARD_H)
            .precision(Precision.EXACT)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .apply { if (withRule) minSourceWidth(MIN_PICTURE_SOURCE_WIDTH) }
            .build()

    @Test
    fun decodedSizeHidesATinySource() {
        val result = runBlocking { loader(png(90, 90)).execute(request(withRule = false)) }
        assertTrue("expected success, got $result", result is SuccessResult)
        // Coil decoded the 90 px thumbnail at the card's size: its width says nothing of the source.
        assertTrue((result as SuccessResult).image.width >= CARD_H)
    }

    @Test
    fun sourceUnder300IsRefused() {
        val result = runBlocking { loader(png(90, 90)).execute(request(withRule = true)) }
        assertTrue("expected an error, got $result", result is ErrorResult)
        val error = (result as ErrorResult).throwable
        assertTrue("unexpected $error", error is TinyImageException)
        assertEquals(90, (error as TinyImageException).sourceWidth)
    }

    @Test
    fun sourceOf300OrMoreLoads() {
        val result = runBlocking { loader(png(300, 169)).execute(request(withRule = true)) }
        assertTrue("expected success, got $result", result is SuccessResult)
    }

    private companion object {
        const val CARD_W = 1080
        const val CARD_H = 608
    }
}
