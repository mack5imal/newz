package app.newz.engine

import app.newz.net.PageImageFinder
import app.newz.net.newzHttpClient
import app.newz.store.ArticleStore
import app.newz.store.UserPrefs
import app.newz.store.createSqlDriver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Settings › Better pictures (B5): when article pages are read, how often, and what counts. */
class PageImagesTest {
    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val store = ArticleStore(createSqlDriver())
    private val prefs = MutableStateFlow(UserPrefs(betterPictures = true))
    private var online = true

    /** Article path -> the og:image its page names (absent: the page names none). */
    private val pictures = ConcurrentHashMap<String, String>()
    private val requests = ConcurrentHashMap<String, AtomicInteger>()
    private val inFlight = AtomicInteger()
    private val maxInFlight = AtomicInteger()

    @Volatile private var gate: CompletableDeferred<Unit>? = null

    private val engine =
        MockEngine { request ->
            val path = request.url.encodedPath
            requests.getOrPut(path) { AtomicInteger() }.incrementAndGet()
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            try {
                gate?.await()
                val meta = pictures[path]?.let { """<meta property="og:image" content="$it">""" }.orEmpty()
                respond(
                    "<html><head>$meta</head><body></body></html>",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                )
            } finally {
                inFlight.decrementAndGet()
            }
        }

    private val images =
        PageImages(
            store = store,
            finder = PageImageFinder(newzHttpClient(engine)),
            prefs = prefs,
            isOnline = { online },
            clock = { now },
            storeDispatcher = Dispatchers.IO,
        )

    private fun url(path: String) = "https://news.example.org$path"

    private fun load(
        path: String,
        outlet: String = "out",
    ) = runBlocking { images.load(url(path), outlet) }

    private fun reads(path: String) = requests[path]?.get() ?: 0

    @Test
    fun switchOffReadsNoPage() {
        prefs.value = UserPrefs(betterPictures = false)
        pictures["/a"] = "/img/a.jpg"
        load("/a")
        assertEquals(0, requests.values.sumOf { it.get() })
        assertTrue(images.images.value.isEmpty())
    }

    @Test
    fun offlineReadsNoPageAndTriesAgainLater() {
        pictures["/a"] = "/img/a.jpg"
        online = false
        load("/a")
        assertEquals(0, reads("/a"))
        online = true
        load("/a")
        assertEquals(1, reads("/a"))
        assertEquals("https://news.example.org/img/a.jpg", images.images.value[url("/a")])
    }

    @Test
    fun eachPageIsReadOnceIncludingOnesWithoutAPicture() {
        pictures["/a"] = "/img/a.jpg"
        repeat(3) {
            load("/a")
            load("/b")
        }
        assertEquals(1, reads("/a"))
        assertEquals(1, reads("/b"))
        assertEquals("https://news.example.org/img/a.jpg", images.images.value[url("/a")])
        assertTrue(url("/b") in images.images.value)
        assertNull(images.images.value[url("/b")])
        // Recorded in the store, so a new process (a fresh PageImages) doesn't read them again.
        val fresh = PageImages(store, PageImageFinder(newzHttpClient(engine)), prefs, clock = { now })
        runBlocking { fresh.load(url("/a"), "out") }
        assertEquals(1, reads("/a"))
        assertEquals("https://news.example.org/img/a.jpg", fresh.images.value[url("/a")])
    }

    @Test
    fun anOutletsRepeatedImageIsItsLogoNotAPicture() {
        for (p in listOf("/a", "/b", "/c")) pictures[p] = "/logo.png"
        pictures["/d"] = "/img/d.jpg"
        // Another outlet may use the same image; it doesn't count towards this outlet's logo.
        pictures["/e"] = "/logo.png"
        load("/a")
        load("/b")
        load("/e", outlet = "other")
        assertEquals("https://news.example.org/logo.png", images.images.value[url("/a")])
        load("/c")
        load("/d")
        val found = images.images.value
        assertNull(found[url("/a")])
        assertNull(found[url("/b")])
        assertNull(found[url("/c")])
        assertEquals("https://news.example.org/img/d.jpg", found[url("/d")])
        assertEquals("https://news.example.org/logo.png", found[url("/e")])
    }

    @Test
    fun googleImageOnAPageCountsAsNone() {
        pictures["/a"] = "https://lh3.googleusercontent.com/p.jpg"
        load("/a")
        assertEquals(1, reads("/a"))
        assertNull(images.images.value[url("/a")])
    }

    @Test
    fun atMostTwoPagesAreReadAtOnce() =
        runBlocking {
            val open = CompletableDeferred<Unit>()
            gate = open
            val paths = List(6) { "/p$it" }
            val loads = paths.map { async(Dispatchers.IO) { images.load(url(it), "out") } }
            val deadline = System.currentTimeMillis() + 5_000
            while (requests.size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(10)
            Thread.sleep(200)
            assertEquals(2, inFlight.get())
            open.complete(Unit)
            loads.awaitAll()
            assertEquals(2, maxInFlight.get())
            assertTrue(paths.all { reads(it) == 1 })
        }
}
