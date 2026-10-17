package app.newz.ui.image

import android.graphics.BitmapFactory
import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.getExtra
import coil3.request.ImageRequest
import coil3.request.Options

/**
 * Feed pictures narrower than this many source pixels count as no picture (B5): Phys.org's
 * 90×90 thumbnails, stretched to the card's width, look blocky.
 */
const val MIN_PICTURE_SOURCE_WIDTH = 300

/** Thrown into the error result of a request whose source image is narrower than its minimum. */
class TinyImageException(
    val sourceWidth: Int,
    val minWidth: Int,
) : IllegalStateException("Image source is $sourceWidth px wide, under $minWidth px")

/**
 * The narrowest source image, in pixels, a request accepts; 0 (the default) accepts any. Set it
 * with [minSourceWidth]. Coil's decoded size can't stand in for this: it scales the picture to
 * the view (a 90 px source decodes at the card's full width), so [MinSourceWidthDecoder] reads
 * the width from the encoded image's header instead.
 */
val MIN_SOURCE_WIDTH: Extras.Key<Int> = Extras.Key(default = 0)

/** Refuses a source image narrower than [width] px with [TinyImageException]. */
fun ImageRequest.Builder.minSourceWidth(width: Int): ImageRequest.Builder =
    apply {
        extras[MIN_SOURCE_WIDTH] = width
        // A cached copy decoded for a request without the rule must not satisfy one with it.
        memoryCacheKeyExtra("newz#minSourceWidth", width.toString())
    }

/**
 * Runs before Coil's own decoders. For a request with [MIN_SOURCE_WIDTH], it reads only the
 * image header (from a peek, so the real decoder still sees every byte) and fails the request
 * with [TinyImageException] when the source is too narrow; otherwise it steps aside and Coil's
 * usual decoder runs. A header it can't read is left to Coil's decoder too.
 */
class MinSourceWidthDecoder : Decoder.Factory {
    override fun create(
        result: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        val minWidth = options.getExtra(MIN_SOURCE_WIDTH)
        if (minWidth <= 0) return null
        val width = sourceWidth(result) ?: return null
        if (width >= minWidth) return null
        return object : Decoder {
            override suspend fun decode(): DecodeResult = throw TinyImageException(width, minWidth)
        }
    }

    private fun sourceWidth(result: SourceFetchResult): Int? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            result.source
                .source()
                .peek()
                .inputStream()
                .use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        return bounds.outWidth.takeIf { it > 0 }
    }
}
