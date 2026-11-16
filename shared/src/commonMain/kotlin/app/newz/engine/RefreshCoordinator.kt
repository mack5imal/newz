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
import kotlin.concurrent.atomics.AtomicBoolean
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
 * Runs feed loads one at a time (SPEC S5, S25). A load fetches the feeds followed when it starts,
 * reporting each through [onProgress], prunes the cache to [ArticleStore.ARTICLE_RETENTION] (and
 * expired hides), then re-reads the current [UserPrefs] and store into a [FeedSnapshot] and hands
 * it to [onLoaded] before the load completes. A load whose followed sources all fail counts as
 * offline.
 *
 * A [refresh] or [retry] while a load is still fetching joins it, and the load then also fetches
 * every source followed since it started that it has not fetched yet (B38): a feed followed
 * during the start-up load is fetched by it. Once a load's fetching is over it takes no more
 * requests; one arriving then (a pull while the feed composes) queues a new load to run right
 * after it (B45), so it is never folded into a load that has already read what it needed.
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
    /**
     * One load. [accepting] stays true while it may still widen its fetch; [widen] is set by a
     * request that joined it. The pair is a handshake: a joiner sets [widen] then checks
     * [accepting], the load clears [accepting] then checks [widen], so no join is ever lost.
     */
    private class Load(
        val deferred: Deferred<LoadResult>,
        val accepting: AtomicBoolean = AtomicBoolean(true),
        val widen: AtomicBoolean = AtomicBoolean(false),
    )

    private val current = AtomicReference<Load?>(null)
    private val running = MutableStateFlow(false)

    /** True while a load runs. */
    val isRunning: StateFlow<Boolean> = running.asStateFlow()

    /** Starts a load, or returns the one already running. */
    fun refresh(): Deferred<LoadResult> = startOrJoin(only = null)

    /**
     * Re-fetches the followed feed [feedId] alone (a failing feed's Retry in Settings › Sources).
     * A load already running is joined instead: no second load starts.
     */
    fun retry(feedId: String): Deferred<LoadResult> = startOrJoin(only = feedId)

    private fun startOrJoin(only: String?): Deferred<LoadResult> {
        while (true) {
            val active = current.load()
            val after =
                if (active != null && active.deferred.isActive) {
                    active.widen.store(true)
                    if (active.accepting.load()) return active.deferred
                    // Its fetching is over: run a fresh load once it has finished.
                    active.deferred
                } else {
                    null
                }
            // The load needs its own Load; it is set before the lazy coroutine can start.
            val self = arrayOfNulls<Load>(1)
            val deferred =
                scope.async(start = CoroutineStart.LAZY) {
                    after?.join()
                    load(checkNotNull(self[0]), only)
                }
            val next = Load(deferred).also { self[0] = it }
            if (current.compareAndSet(active, next)) {
                deferred.start()
                return deferred
            }
            deferred.cancel()
        }
    }

    private suspend fun followedNow(): List<FeedSource> {
        val p = prefs.first()
        return withContext(storeDispatcher) { FeedFetcher.followedSources(p, catalog, store.listCustomFeeds()) }
    }

    private suspend fun load(
        self: Load,
        only: String?,
    ): LoadResult {
        running.value = true
        try {
            val followed = followedNow()
            val sources = if (only == null) followed else followed.filter { it.feedId == only }
            val fetched = sources.mapTo(HashSet()) { it.feedId }
            var offline = false
            var anyOk = false

            suspend fun fetch(list: List<FeedSource>) {
                fetcher.fetch(list).collect { progress ->
                    if (progress is FetchProgress.Offline) offline = true
                    if (progress is FetchProgress.FeedDone && progress.outcome is FeedOutcome.Ok) anyOk = true
                    onProgress(progress)
                }
            }
            fetch(sources)
            // Requests that joined meanwhile widen this load to every source followed now (B38).
            // The last round runs after the load stops accepting, for one that joined just before.
            var closed = false
            while (true) {
                if (!self.widen.exchange(false)) {
                    if (closed) break
                    self.accepting.store(false)
                    closed = true
                    continue
                }
                val missing = followedNow().filter { it.feedId !in fetched }
                fetched += missing.map { it.feedId }
                if (missing.isNotEmpty()) fetch(missing)
            }
            // Every source failing reads as offline (SPEC S25), so the cache shows with the banner.
            if (only == null && sources.isNotEmpty() && !anyOk) offline = true
            val latest = prefs.first()
            val result =
                withContext(storeDispatcher) {
                    val now = clock()
                    store.pruneOlderThan(ArticleStore.ARTICLE_RETENTION, now)
                    store.pruneHidden(now)
                    store.prunePageImages(now)
                    store.pruneHistory(now)
                    val failing =
                        store
                            .feedStatuses()
                            .filter { it.failingSince != null }
                            .mapTo(HashSet()) { it.feedId }
                            .intersect(followed.mapTo(HashSet()) { it.feedId })
                    LoadResult(FeedSnapshot.read(store, latest), offline, failing)
                }
            onLoaded(result)
            return result
        } finally {
            running.value = false
        }
    }
}
