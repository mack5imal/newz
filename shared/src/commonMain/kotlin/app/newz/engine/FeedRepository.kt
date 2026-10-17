package app.newz.engine

import app.newz.catalog.Catalog
import app.newz.feed.Article
import app.newz.filter.FEED_WINDOW
import app.newz.net.newzHttpClient
import app.newz.store.ArticleStore
import app.newz.store.UserPrefs
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/** Why the feed has no cards. */
enum class EmptyReason {
    /** Articles exist but the user's follows, mutes, hides or topic filter exclude all of them. */
    ALL_FILTERED,

    /** Nothing is cached and the device is offline. */
    NO_CACHE_OFFLINE,
}

/** What the feed screen shows. */
sealed interface FeedState {
    /** Nothing to show yet; [firstLoad] is true until this session's first load finishes. */
    data class Loading(
        val firstLoad: Boolean,
    ) : FeedState

    /** [cards] served so far; [failingFeeds] are the ids of followed feeds currently failing. */
    data class Content(
        val cards: List<FeedCard>,
        val endReached: Boolean,
        val offline: Boolean,
        val failingFeeds: Set<String>,
    ) : FeedState

    data class Empty(
        val reason: EmptyReason,
    ) : FeedState
}

/**
 * The feed (SPEC S5, S6, S10, S16, S25): store -> [app.newz.filter.FeedFilter] ->
 * [app.newz.dedup.StoryGrouper] -> [FeedRanker] -> [FeedPager].
 *
 * Collecting [feed] the first time shows cached eligible cards at once (cache-first), then starts
 * a refresh. Cards appear as soon as the first feed lands when nothing was shown yet; a finished
 * load merges into the served cards without moving or duplicating them. Preferences and the
 * hidden/saved/history tables are re-read at every composition. A preference change recomposes
 * from the cache at once; store-side changes do so through [invalidate].
 */
class FeedRepository(
    private val scope: CoroutineScope,
    private val store: ArticleStore,
    private val prefs: Flow<UserPrefs>,
    private val catalog: Catalog,
    client: HttpClient = newzHttpClient(),
    private val isOnline: () -> Boolean = { true },
    private val clock: () -> Instant = { Clock.System.now() },
    private val storeDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    pageSize: Int = FeedPager.DEFAULT_PAGE_SIZE,
) {
    private val state = MutableStateFlow<FeedState>(FeedState.Loading(firstLoad = true))

    /** True while a load that began with no cards shown is still landing feeds. Guarded by [lock]. */
    private var growing = false

    /**
     * True once the user paged ([loadMore]) or changed the store ([invalidate]) during a growing
     * load: landed feeds then merge into the served cards instead of re-ranking them. Guarded by [lock].
     */
    private var userMoved = false
    private val lock = Mutex()
    private val pager = FeedPager(pageSize)
    private var loadedOnce = false
    private var offline = false
    private var failing: Set<String> = emptySet()
    private var savedIds: Set<String> = emptySet()
    private var composedPrefs: UserPrefs? = null

    /** The cache-first start, launched in [scope] once so a cancelled collector cannot abort it. */
    private val startJob: Job by lazy { scope.launch { start() } }

    private val coordinator =
        RefreshCoordinator(
            scope = scope,
            fetcher = FeedFetcher(client, store, isOnline, storeDispatcher, clock),
            store = store,
            prefs = prefs,
            catalog = catalog,
            storeDispatcher = storeDispatcher,
            clock = clock,
            onProgress = ::onProgress,
            onLoaded = ::onLoaded,
        )

    /** The feed. The first collection triggers the cache-first start. */
    val feed: Flow<FeedState> =
        flow {
            startJob.join()
            emitAll(state)
        }

    /** Reloads every followed feed; joins a load already running. */
    fun refresh(): Job {
        state.value.let { if (it is FeedState.Empty) state.value = FeedState.Loading(firstLoad = !loadedOnce) }
        return coordinator.refresh()
    }

    /** Re-fetches the one followed feed [feedId] (Retry on a failing feed); joins a load already running. */
    fun retryFeed(feedId: String): Job = coordinator.retry(feedId)

    /** Serves the next page of cards. */
    fun loadMore(): Job =
        scope.launch {
            lock.withLock {
                if (state.value !is FeedState.Content || pager.endReached) return@withLock
                userMoved = true
                pager.next()
                state.value = content()
            }
        }

    /** Records that [article] was opened, only while reading history is on. */
    fun recordOpen(article: Article): Job =
        scope.launch {
            if (!prefs.first().readingHistoryOn) return@launch
            withContext(storeDispatcher) { store.recordOpen(article.id, article.outletId, article.topics, clock()) }
        }

    /**
     * Recomposes the feed from the cache without fetching, for store-side changes (hide, unhide,
     * save, unsave) made by phases 12-17: served cards keep their places and excluded ones drop out.
     */
    fun invalidate(): Job =
        scope.launch {
            lock.withLock { userMoved = true }
            recompose()
        }

    private suspend fun recompose() {
        publish(readSnapshot(), reset = false, loading = coordinator.isRunning.value)
    }

    private suspend fun start() {
        offline = !isOnline()
        failing =
            withContext(storeDispatcher) {
                store.feedStatuses().filter { it.failingSince != null }.mapTo(HashSet()) { it.feedId }
            }
        publish(readSnapshot(), reset = true, loading = !offline)
        coordinator.refresh()
        scope.launch {
            prefs.distinctUntilChanged().collect { if (it != composedPrefs) recompose() }
        }
    }

    private suspend fun readSnapshot(): FeedSnapshot {
        val current = prefs.first()
        return withContext(storeDispatcher) { FeedSnapshot.read(store, current) }
    }

    private suspend fun onProgress(progress: FetchProgress) {
        if (progress is FetchProgress.Offline) offline = true
        val landed = progress is FetchProgress.FeedDone && progress.outcome is FeedOutcome.Ok
        if (!landed) return
        if (state.value !is FeedState.Content) {
            offline = false
            lock.withLock {
                growing = true
                userMoved = false
            }
            publish(readSnapshot(), reset = true, loading = true)
        } else if (lock.withLock { growing }) {
            // A load that started with nothing on screen publishes as each feed lands, so one slow
            // feed never holds back the others: it re-ranks until the user pages or acts on the
            // feed, then merges. Loads over shown cards merge once, at the end.
            publish(readSnapshot(), reset = true, loading = true, whileGrowing = true)
        }
    }

    private suspend fun onLoaded(result: LoadResult) {
        loadedOnce = true
        lock.withLock { growing = false }
        offline = result.offline
        failing = result.failingFeeds
        publish(result.snapshot, reset = state.value !is FeedState.Content, loading = false)
    }

    private suspend fun publish(
        snapshot: FeedSnapshot,
        reset: Boolean,
        loading: Boolean,
        whileGrowing: Boolean = false,
    ) {
        val groups = FeedComposer.compose(snapshot, catalog, clock())
        lock.withLock {
            if (whileGrowing && !growing) return
            val rerank = if (whileGrowing) reset && !userMoved else reset
            if (rerank) pager.reset(groups) else pager.submit(groups)
            if (pager.servedGroups.size < pager.pageSize) pager.page(0)
            savedIds = snapshot.savedIds
            composedPrefs = snapshot.prefs
            state.value =
                when {
                    pager.servedGroups.isNotEmpty() -> content()
                    offline && !snapshot.hasArticlesInWindow() -> FeedState.Empty(EmptyReason.NO_CACHE_OFFLINE)
                    offline -> FeedState.Empty(EmptyReason.ALL_FILTERED)
                    loading -> FeedState.Loading(firstLoad = !loadedOnce)
                    else -> FeedState.Empty(EmptyReason.ALL_FILTERED)
                }
        }
    }

    private fun FeedSnapshot.hasArticlesInWindow(): Boolean {
        val now = clock()
        return articles.any { minOf(FeedComposer.toArticle(it).date, now) >= now - FEED_WINDOW }
    }

    private fun content(): FeedState.Content =
        FeedState.Content(
            cards = pager.servedGroups.map { FeedCard(it, it.lead.id in savedIds) },
            endReached = pager.endReached,
            offline = offline,
            failingFeeds = failing,
        )
}
