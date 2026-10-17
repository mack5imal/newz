package app.newz.engine

import app.newz.net.PageImageFinder
import app.newz.store.ArticleStore
import app.newz.store.PageImage
import app.newz.store.UserPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Pictures from article pages, for Settings › Better pictures (B5). A feed card whose own picture
 * is missing or tiny calls [load] while it is composed; with [UserPrefs.betterPictures] on and the
 * phone online, the article's page is read once through [finder] (at most
 * [MAX_CONCURRENT_READS] at a time) and what it found is kept in the [store], a failure as null,
 * so the page is never read again. [images] maps each article URL read so far to its picture.
 *
 * An outlet's logo often stands in for its pictures: an image an outlet's pages name for
 * [LOGO_ARTICLES] or more articles counts as no picture for all of them.
 */
class PageImages(
    private val store: ArticleStore,
    private val finder: PageImageFinder,
    private val prefs: Flow<UserPrefs>,
    private val isOnline: () -> Boolean = { true },
    private val clock: () -> Instant = { Clock.System.now() },
    private val storeDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    private val found = MutableStateFlow<Map<String, String?>>(emptyMap())

    /** Article URL -> the picture its page names (null: none, unreadable, or the outlet's logo). */
    val images: StateFlow<Map<String, String?>> = found.asStateFlow()

    private val reading = MutableStateFlow<Set<String>>(emptySet())
    private val reads = Semaphore(MAX_CONCURRENT_READS)

    /**
     * Finds a picture for the article at [articleUrl] from [outletId], unless the switch is off,
     * the result is already known, or the phone is offline. Cancelling it (the card left the
     * screen) drops a read still waiting for its turn; a read already under way finishes and is
     * recorded, so no page is read twice.
     */
    suspend fun load(
        articleUrl: String,
        outletId: String,
    ) {
        if (articleUrl in found.value) return
        if (!prefs.first().betterPictures) return
        val cached = withContext(storeDispatcher) { store.pageImage(articleUrl) }
        if (cached != null) return publish(outletId)
        if (!isOnline() || !claim(articleUrl)) return
        try {
            reads.withPermit {
                withContext(NonCancellable) {
                    val image = finder.find(articleUrl)
                    withContext(storeDispatcher) { store.recordPageImage(articleUrl, outletId, image, clock()) }
                    publish(outletId)
                }
            }
        } finally {
            reading.update { it - articleUrl }
        }
    }

    /** Marks [articleUrl] as being read; false when another card's [load] is already reading it. */
    private fun claim(articleUrl: String): Boolean {
        var claimed = false
        reading.update {
            claimed = articleUrl !in it
            it + articleUrl
        }
        return claimed
    }

    /** Re-reads [outletId]'s page pictures into [images], applying the logo rule. */
    private suspend fun publish(outletId: String) {
        val rows = withContext(storeDispatcher) { store.pageImagesOf(outletId) }
        val pictures = pictures(rows)
        found.update { it + pictures }
    }

    companion object {
        /** Article pages read at the same time, at most. */
        const val MAX_CONCURRENT_READS = 2

        /** An outlet's image named by this many articles' pages is its logo, not their picture. */
        const val LOGO_ARTICLES = 3

        /** Each row's picture, or null where [LOGO_ARTICLES] or more rows name the same image. */
        internal fun pictures(rows: List<PageImage>): Map<String, String?> {
            val uses = rows.mapNotNull { it.imageUrl }.groupingBy { it }.eachCount()
            return rows.associate { row ->
                row.articleUrl to row.imageUrl?.takeIf { (uses[it] ?: 0) < LOGO_ARTICLES }
            }
        }
    }
}
