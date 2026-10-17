package app.newz.ui.image

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.newz.net.HostGuard
import app.newz.net.newzHttpClient
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.serviceLoaderEnabled
import io.ktor.client.HttpClient

/**
 * The app's only image loader (SPEC S2): Coil 3 over the shared guarded [newzHttpClient], with
 * [HostGuardInterceptor] in front so a Google-hosted image URL fails before any request is made,
 * and [MinSourceWidthDecoder] ahead of Coil's decoders for requests that set [minSourceWidth].
 * Memory and disk caches are on. Service-loaded fetchers are off, so Coil never builds its own
 * unguarded HTTP client.
 */
object NewzImageLoader {
    private const val MEMORY_CACHE_FRACTION = 0.2
    private const val DISK_CACHE_BYTES = 64L * 1024 * 1024
    private const val DISK_CACHE_DIR = "newz_images"

    @Volatile
    private var instance: ImageLoader? = null

    /** The process-wide loader. */
    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    /** A new loader over [httpClient]; tests pass a client built on a fake engine. */
    @OptIn(ExperimentalCoilApi::class)
    fun create(
        context: Context,
        httpClient: HttpClient = newzHttpClient(),
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .serviceLoaderEnabled(false)
            .components {
                add(HostGuardInterceptor())
                add(MinSourceWidthDecoder())
                add(KtorNetworkFetcherFactory(httpClient = httpClient))
            }.memoryCache {
                MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_FRACTION).build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve(DISK_CACHE_DIR))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }.build()
}

/** Thrown into the [ErrorResult] for an image URL that [HostGuard] blocks. */
class BlockedImageHostException(
    url: String,
) : IllegalArgumentException("Image host is blocked: $url")

/** Returns an error result, without proceeding to any fetcher, for every URL [HostGuard.isBlocked]. */
class HostGuardInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data.toUrlString()
        if (url != null && HostGuard.isBlocked(url)) {
            return ErrorResult(image = null, request = chain.request, throwable = BlockedImageHostException(url))
        }
        return chain.proceed()
    }

    private fun Any.toUrlString(): String? =
        when (this) {
            is String -> this
            is coil3.Uri -> toString()
            is android.net.Uri -> toString()
            else -> null
        }
}

/** The process-wide [NewzImageLoader] for composables. */
@Composable
fun rememberNewzImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) { NewzImageLoader.get(context) }
}
