package app.newz.engine

import app.newz.catalog.Catalog
import app.newz.store.ArticleStore
import app.newz.store.UserPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What one load produced. [snapshot] was read after the fetch finished, so it reflects every
 * preference, hide, save and history change made while the load ran. [failingFeeds] are the
 * followed feeds whose `failingSince` is set.
 */
class LoadResult(
    val snapshot: FeedSnapshot,
    val offline: Boolean,
    val failingFeeds: Set<String>,
)

/**
 * Runs feed loads one at a time (SPEC S5, S25). [refresh] while a load is running joins it: no
 * second fetch starts. A load fetches the feeds followed when it starts, reporting each through
 * [onProgress], prunes the cache to [ArticleStore.ARTICLE_RETENTION] (and expired hides), then
 * re-reads the current [UserPrefs] and store into a [FeedSnapshot] and hands it to [onLoaded]
 * before the load completes.
 */
@OptIn(ExperimentalAtomicApi::class)
class RefreshCoordinator(
    private val scope: CoroutineScope,
    private val fetcher: FeedFetcher,
    private val store: ArticleStore,
    private val prefs: Flow<UserPrefs>,
    private val catalog: Catalog,
    private val storeDispatcher: CoroutineDispatcher,
    private val clock: () -> Instant = { Clock.System.now() },
    private val onProgress: suspend (FetchProgress) -> Unit = {},
    private val onLoaded: suspend (LoadResult) -> Unit = {},
) {
    private val current = AtomicReference<Deferred<LoadResult>?>(null)
    private val running = MutableStateFlow(false)

    /** True while a load runs. */
    val isRunning: StateFlow<Boolean> = running.asStateFlow()

    /** Starts a load, or returns the one already running. */
    fun refresh(): Deferred<LoadResult> {
        while (true) {
            val active = current.load()
            if (active != null && active.isActive) return active
            val next = scope.async(start = CoroutineStart.LAZY) { load() }
            if (current.compareAndSet(active, next)) {
                next.start()
                return next
            }
            next.cancel()
        }
    }

    private suspend fun load(): LoadResult {
        running.value = true
        try {
            val startPrefs = prefs.first()
            val sources =
                withContext(storeDispatcher) {
                    FeedFetcher.followedSources(startPrefs, catalog, store.listCustomFeeds())
                }
            var offline = false
            fetcher.fetch(sources).collect { progress ->
                if (progress is FetchProgress.Offline) offline = true
                onProgress(progress)
            }
            val latest = prefs.first()
            val result =
                withContext(storeDispatcher) {
                    val now = clock()
                    store.pruneOlderThan(ArticleStore.ARTICLE_RETENTION, now)
                    store.pruneHidden(now)
                    val failing =
                        store
                            .feedStatuses()
                            .filter { it.failingSince != null }
                            .mapTo(HashSet()) { it.feedId }
                            .intersect(sources.mapTo(HashSet()) { it.feedId })
                    LoadResult(FeedSnapshot.read(store, latest), offline, failing)
                }
            onLoaded(result)
            return result
        } finally {
            running.value = false
        }
    }
}
