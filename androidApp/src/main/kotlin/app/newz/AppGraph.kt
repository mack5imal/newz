package app.newz

import android.content.Context
import androidx.browser.customtabs.CustomTabsClient
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.newz.catalog.Catalog
import app.newz.catalog.CatalogLoader
import app.newz.db.NewzDatabase
import app.newz.discover.FeedDiscovery
import app.newz.engine.FeedRepository
import app.newz.engine.androidConnectivity
import app.newz.net.newzHttpClient
import app.newz.platform.ArticleOpener
import app.newz.platform.ShareLauncher
import app.newz.store.ArticleStore
import app.newz.store.NewzStorage
import app.newz.store.PreferencesStore
import app.newz.ui.image.NewzImageLoader
import coil3.ImageLoader
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The app's hand-written dependency container: catalog, database ([ArticleStore]),
 * [PreferencesStore], [FeedRepository] (which owns the feed's
 * [app.newz.engine.RefreshCoordinator]), [NewzImageLoader], [ArticleOpener], [ShareLauncher]
 * and the clock. One graph lives per process; [NewzApplication] installs it and screens read it
 * through [get]. Instrumented tests swap parts of it with [override] before launching an activity.
 */
class AppGraph private constructor(
    context: Context,
    val config: Config,
    driver: SqlDriver,
) {
    /** What a test may replace. Production uses [Config.production]. */
    data class Config(
        val catalog: Catalog,
        val clock: () -> Instant,
        val isOnline: () -> Boolean,
        /** The default browser's Custom Tabs provider, or null when it has none. */
        val customTabsProvider: () -> String?,
        /** The database file; null opens a fresh in-memory database. */
        val databaseName: String?,
        /** The preferences file in the app's files directory (one DataStore per file per process). */
        val prefsFileName: String,
        val httpClient: () -> HttpClient,
    ) {
        companion object {
            fun production(context: Context): Config {
                val app = context.applicationContext
                return Config(
                    catalog = CatalogLoader.load(),
                    clock = { Clock.System.now() },
                    isOnline = androidConnectivity(app),
                    customTabsProvider = { CustomTabsClient.getPackageName(app, emptyList()) },
                    databaseName = NewzStorage.DATABASE_NAME,
                    prefsFileName = PreferencesStore.FILE_NAME,
                    httpClient = { newzHttpClient() },
                )
            }
        }
    }

    /** Builder for [override]: every field starts at the current graph's value. */
    class Overrides internal constructor(
        base: Config,
    ) {
        var catalog: Catalog = base.catalog
        var clock: () -> Instant = base.clock
        var isOnline: () -> Boolean = base.isOnline
        var customTabsProvider: () -> String? = base.customTabsProvider
        var databaseName: String? = base.databaseName

        /** Tests point this at their own file so they never touch the user's real preferences. */
        var prefsFileName: String = base.prefsFileName

        /** Builds the guarded HTTP client (a test wraps it to count the hosts the app contacts). */
        var httpClient: () -> HttpClient = base.httpClient

        /** Keep the current graph's open database (and its cached articles) instead of opening [databaseName]. */
        var keepDatabase: Boolean = false

        internal fun build(base: Config): Config =
            base.copy(
                catalog = catalog,
                clock = clock,
                isOnline = isOnline,
                customTabsProvider = customTabsProvider,
                databaseName = databaseName,
                prefsFileName = prefsFileName,
                httpClient = httpClient,
            )
    }

    private val appContext: Context = context.applicationContext
    private val sqlDriver: SqlDriver = driver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val catalog: Catalog get() = config.catalog
    val clock: () -> Instant get() = config.clock
    val articleStore: ArticleStore = ArticleStore(sqlDriver)
    val preferencesStore: PreferencesStore get() = preferences(config.prefsFileName)
    val imageLoader: ImageLoader get() = NewzImageLoader.get(appContext)
    val articleOpener: ArticleOpener = ArticleOpener(config.customTabsProvider)
    val shareLauncher: ShareLauncher = ShareLauncher()

    /** The one guarded HTTP client the feed engine and search's address check share. */
    private val httpClient: HttpClient by lazy { config.httpClient() }

    /** Checks a typed web address for a feed (design `search`); nothing it sees is recorded. */
    val feedDiscovery: FeedDiscovery by lazy {
        val clock = config.clock
        FeedDiscovery(
            client = httpClient,
            catalog = config.catalog,
            clock =
                object : Clock {
                    override fun now(): Instant = clock()
                },
        )
    }

    val feedRepository: FeedRepository by lazy {
        FeedRepository(
            scope = scope,
            store = articleStore,
            prefs = preferencesStore.prefs,
            catalog = config.catalog,
            client = httpClient,
            isOnline = config.isOnline,
            clock = config.clock,
        )
    }

    /**
     * The feed's topic chip selection. It lives as long as the process (SPEC Assumptions) and is
     * never written to preferences; the feed's ViewModel reads and updates it.
     */
    var topicSelection: Set<String> = emptySet()

    private fun dispose(closeDriver: Boolean) {
        scope.cancel()
        if (closeDriver) sqlDriver.close()
    }

    companion object {
        @Volatile
        private var current: AppGraph? = null
        private var application: Context? = null
        private val preferences = mutableMapOf<String, PreferencesStore>()

        /** Called once by [NewzApplication]; the graph itself is built on first use. */
        fun install(context: Context) {
            application = context.applicationContext
            NewzStorage.init(context)
        }

        /** The process's graph. */
        fun get(): AppGraph =
            current ?: synchronized(this) {
                current ?: build(Config.production(app()), keepDriver = null).also { current = it }
            }

        /**
         * Test hook: replaces the graph with one built from the current config plus [block]'s
         * changes (catalog, clock, connectivity, Custom Tabs provider, database, preferences file).
         * The old graph's coroutines stop. Each preferences file keeps one DataStore per process;
         * tests write them through [preferencesStore]. Call before launching an activity.
         */
        fun override(block: Overrides.() -> Unit): AppGraph =
            synchronized(this) {
                val old = current
                val base = old?.config ?: Config.production(app())
                val overrides = Overrides(base).apply(block)
                val keep = if (overrides.keepDatabase) old?.sqlDriver else null
                old?.dispose(closeDriver = keep == null)
                build(overrides.build(base), keepDriver = keep).also { current = it }
            }

        private fun build(
            config: Config,
            keepDriver: SqlDriver?,
        ): AppGraph {
            val context = app()
            val driver = keepDriver ?: openDriver(context, config.databaseName)
            return AppGraph(context, config, driver)
        }

        private fun openDriver(
            context: Context,
            name: String?,
        ): SqlDriver = AndroidSqliteDriver(NewzDatabase.Schema, context, name)

        /** DataStore allows one instance per file per process, so each file's store outlives any graph. */
        private fun preferences(fileName: String): PreferencesStore =
            synchronized(this) {
                preferences.getOrPut(fileName) {
                    PreferencesStore.create(File(app().filesDir, fileName).path)
                }
            }

        private fun app(): Context = checkNotNull(application) { "AppGraph.install(context) was not called" }
    }
}
