package app.newz.engine

import app.newz.catalog.Catalog
import app.newz.feed.FeedParser
import app.newz.net.FetchResult
import app.newz.net.fetchCapped
import app.newz.store.ArticleStore
import app.newz.store.CustomFeed
import app.newz.store.UserPrefs
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/** One feed to fetch. A custom (URL-added) feed uses its own id as [outletId] and has no topics. */
data class FeedSource(
    val feedId: String,
    val outletId: String,
    val url: String,
    val topics: List<String>,
)

/** How one feed's fetch ended. */
sealed interface FeedOutcome {
    data class Ok(
        val added: Int,
    ) : FeedOutcome

    data class Failed(
        val error: String,
    ) : FeedOutcome
}

/** Emitted by [FeedFetcher.fetch] as the load goes. [completed] of [total] feeds are done. */
sealed interface FetchProgress {
    val completed: Int
    val total: Int

    /** There was no connectivity: nothing was requested and no feed status changed. */
    data class Offline(
        override val total: Int,
    ) : FetchProgress {
        override val completed: Int get() = 0
    }

    data class FeedDone(
        val source: FeedSource,
        val outcome: FeedOutcome,
        override val completed: Int,
        override val total: Int,
    ) : FetchProgress
}

/**
 * Fetches followed feeds through the guarded client (SPEC S5, S25): at most [maxConcurrent] at a
 * time, each parsed with [FeedParser] and upserted into [store] as it lands. A feed that fails
 * (HTTP error, oversize body, timeout, blocked host, parse error) is skipped and its
 * `failingSince` recorded; a success clears it. Only feed URLs are requested here, never article
 * pages. All [store] calls run on [storeDispatcher], so pass a single-threaded one when the
 * driver is not thread-safe.
 */
class FeedFetcher(
    private val client: HttpClient,
    private val store: ArticleStore,
    private val isOnline: () -> Boolean,
    private val storeDispatcher: CoroutineDispatcher,
    private val clock: () -> Instant = { Clock.System.now() },
    private val maxConcurrent: Int = MAX_CONCURRENT_FETCHES,
) {
    /** Emits one [FetchProgress.FeedDone] per source as each completes, or one [FetchProgress.Offline]. */
    fun fetch(sources: List<FeedSource>): Flow<FetchProgress> =
        channelFlow {
            val unique = sources.distinctBy { it.url }
            if (!isOnline()) {
                send(FetchProgress.Offline(unique.size))
                return@channelFlow
            }
            val done = Channel<Pair<FeedSource, FeedOutcome>>(Channel.UNLIMITED)
            val permits = Semaphore(maxConcurrent)
            for (source in unique) {
                launch { permits.withPermit { done.send(source to fetchOne(source)) } }
            }
            repeat(unique.size) { i ->
                val (source, outcome) = done.receive()
                send(FetchProgress.FeedDone(source, outcome, i + 1, unique.size))
            }
        }

    private suspend fun fetchOne(source: FeedSource): FeedOutcome {
        val outcome =
            try {
                when (val result = client.fetchCapped(source.url)) {
                    is FetchResult.HttpError -> {
                        FeedOutcome.Failed("HTTP ${result.status}")
                    }

                    FetchResult.TooLarge -> {
                        FeedOutcome.Failed("Feed too large")
                    }

                    is FetchResult.Success -> {
                        val parsed = FeedParser.parse(result.bytes, result.charset)
                        val added =
                            withContext(storeDispatcher) {
                                store.upsert(source.feedId, source.outletId, source.topics, parsed.items, clock())
                            }
                        FeedOutcome.Ok(added)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FeedOutcome.Failed(e.message ?: e::class.simpleName ?: "Fetch failed")
            }
        withContext(storeDispatcher) {
            when (outcome) {
                is FeedOutcome.Ok -> store.markFeedOk(source.feedId, clock())
                is FeedOutcome.Failed -> store.markFeedFailed(source.feedId, outcome.error, clock())
            }
        }
        return outcome
    }

    companion object {
        const val MAX_CONCURRENT_FETCHES = 8

        /**
         * The feeds [prefs] follow: ticked catalog sections of outlets not removed, the local
         * feeds of [UserPrefs.localMetroId] when Local is followed, and every custom feed.
         */
        fun followedSources(
            prefs: UserPrefs,
            catalog: Catalog,
            customFeeds: List<CustomFeed>,
        ): List<FeedSource> {
            val catalogFeeds =
                prefs.followedOutlets
                    .filterKeys { it !in prefs.removedOutlets }
                    .values
                    .flatten()
                    .mapNotNull(catalog::feed)
                    .filter { it.metroId == null }
            val localFeeds =
                prefs.localMetroId
                    ?.takeIf { Catalog.LOCAL_TOPIC in prefs.followedTopics }
                    ?.let(catalog::localFeeds)
                    .orEmpty()
                    .filter { it.outletId !in prefs.removedOutlets }
            val fromCatalog =
                (catalogFeeds + localFeeds).map { FeedSource(it.id, it.outletId, it.checkedUrl ?: it.url, it.topics) }
            val custom =
                customFeeds
                    .filter { it.id !in prefs.removedOutlets }
                    .map { FeedSource(it.id, it.id, it.url, emptyList()) }
            return (fromCatalog + custom).distinctBy { it.feedId }
        }
    }
}
