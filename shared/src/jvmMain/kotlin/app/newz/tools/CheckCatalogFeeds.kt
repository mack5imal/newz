package app.newz.tools

import app.newz.core.NewzInfo
import app.newz.feed.FeedParser
import app.newz.net.BlockedHostException
import app.newz.net.FetchResult
import app.newz.net.HostGuard
import app.newz.net.TooManyRedirectsException
import app.newz.net.fetchCapped
import app.newz.net.newzHttpClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Probes at most this many feeds at once. */
private const val PARALLEL_FETCHES = 8

/** The shipped catalog must keep at least this share of healthy feeds (SPEC S26). */
private const val MIN_HEALTHY_PERCENT = 90.0

/** A feed is fresh when it has an item newer than this. */
private val FRESH_WINDOW = 7.days

/** Item dates this far ahead of the clock are treated as publisher clock skew, not as fresh. */
private val FUTURE_TOLERANCE = 1.days

private data class Probe(
    val id: String,
    val outletId: String,
    val url: String,
)

private data class ProbeResult(
    val probe: Probe,
    val status: String,
    val finalUrl: String?,
    val itemCount: Int?,
    val newestAge: Duration?,
    val healthy: Boolean,
    val note: String,
)

/**
 * Catalog health check (SPEC S26). Fetches every feed in `catalog/catalog.json` (or `--file`)
 * with the app's own guarded client, parses it with [FeedParser], writes a Markdown report and
 * prints `healthy: <h>/<n> (<pct>%)`. Exits 1 when the shipped catalog is below 90% healthy.
 *
 * Usage: `scripts/check-catalog-feeds [--file <json>] [--report <md>] [--json <out.json>]`.
 */
fun main(args: Array<String>) {
    val options = parseArgs(args)
    val input = options["file"] ?: "catalog/catalog.json"
    val report = options["report"] ?: "catalog/health-report.md"
    val probes = readProbes(File(input))
    val now = Clock.System.now()

    val client = newzHttpClient()
    val results =
        try {
            runBlocking {
                val gate = Semaphore(PARALLEL_FETCHES)
                probes.map { probe -> async { gate.withPermit { probe(client, probe, now) } } }.awaitAll()
            }
        } finally {
            client.close()
        }

    val healthy = results.count { it.healthy }
    val percent = if (results.isEmpty()) 0.0 else healthy * 100.0 / results.size
    val summary = "healthy: $healthy/${results.size} (${String.format(Locale.ROOT, "%.1f", percent)}%)"

    File(report).apply { parentFile?.mkdirs() }.writeText(renderReport(input, now, summary, results))
    options["json"]?.let { File(it).apply { parentFile?.mkdirs() }.writeText(renderJson(results)) }
    println(summary)

    if (options["file"] == null && percent < MIN_HEALTHY_PERCENT) {
        System.err.println("Catalog is below ${MIN_HEALTHY_PERCENT.toInt()}% healthy; see $report")
        exitProcess(1)
    }
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val options = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i].removePrefix("--")
        require(args[i].startsWith("--") && key in setOf("file", "report", "json")) { "Unknown argument: ${args[i]}" }
        require(i + 1 < args.size) { "Missing value for ${args[i]}" }
        options[key] = args[i + 1]
        i += 2
    }
    return options
}

private fun readProbes(file: File): List<Probe> {
    val root = Json.parseToJsonElement(file.readText()).jsonObject
    val feeds = root["feeds"]?.jsonArray ?: JsonArray(emptyList())
    return feeds.map { element ->
        val feed = element.jsonObject
        Probe(
            id = feed.string("id"),
            outletId = feed.string("outletId"),
            url = feed.string("url"),
        )
    }
}

private fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.content ?: error("Feed entry is missing \"$key\": $this")

private suspend fun probe(
    client: HttpClient,
    probe: Probe,
    now: Instant,
): ProbeResult {
    fun failure(
        status: String,
        note: String,
        finalUrl: String? = null,
    ) = ProbeResult(probe, status, finalUrl, null, null, healthy = false, note = note)

    if (HostGuard.isBlocked(probe.url)) return failure("-", "feed URL host is blocked (Google)")
    val fetched =
        try {
            client.fetchCapped(probe.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BlockedHostException) {
            return failure("-", "redirected to a blocked host (Google): ${e.url}", e.url)
        } catch (e: TooManyRedirectsException) {
            return failure("-", "too many redirects")
        } catch (e: Exception) {
            return failure("-", "unreachable: ${e::class.simpleName} ${e.message.orEmpty().take(80)}".trim())
        }
    val success =
        when (fetched) {
            is FetchResult.HttpError -> return failure(fetched.status.toString(), "HTTP error")
            FetchResult.TooLarge -> return failure("200", "body over the size cap")
            is FetchResult.Success -> fetched
        }
    val status = success.status.toString()
    val finalUrl = success.finalUrl
    val feed =
        try {
            FeedParser.parse(success.bytes, success.charset)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failure(status, "not a parseable RSS/Atom/RDF feed", finalUrl)
        }
    val dates = feed.items.mapNotNull { it.publishedAt }.filter { it <= now + FUTURE_TOLERANCE }
    val newestAge = dates.maxOrNull()?.let { (now - it).coerceAtLeast(Duration.ZERO) }
    val finalBlocked = HostGuard.isBlocked(finalUrl)
    val fresh = newestAge != null && newestAge <= FRESH_WINDOW
    val note =
        when {
            finalBlocked -> "final URL host is blocked (Google)"
            feed.items.isEmpty() -> "no items"
            newestAge == null -> "no dated items"
            !fresh -> "stale"
            else -> ""
        }
    return ProbeResult(
        probe = probe,
        status = status,
        finalUrl = finalUrl,
        itemCount = feed.items.size,
        newestAge = newestAge,
        healthy = note.isEmpty(),
        note = note,
    )
}

private fun formatAge(age: Duration?): String =
    when {
        age == null -> "-"
        age.inWholeHours < 1 -> "${age.inWholeMinutes}m"
        age.inWholeDays < 2 -> "${age.inWholeHours}h"
        else -> "${age.inWholeDays}d"
    }

private fun cell(text: String): String = text.replace("|", "\\|").replace("\n", " ")

private fun renderReport(
    input: String,
    now: Instant,
    summary: String,
    results: List<ProbeResult>,
): String =
    buildString {
        appendLine("# Catalog feed health report")
        appendLine()
        appendLine("- Source: `$input`")
        appendLine("- Checked: $now")
        appendLine("- User-Agent: `${NewzInfo.USER_AGENT}`")
        appendLine(
            "- Healthy means: a parseable RSS/Atom/RDF feed with at least one item at most " +
                "${FRESH_WINDOW.inWholeDays} days old, whose final URL host passes HostGuard.",
        )
        appendLine("- Result: `$summary`")
        appendLine()
        appendLine("| id | HTTP | final URL | items | newest | healthy | note |")
        appendLine("|---|---|---|---|---|---|---|")
        for (r in results) {
            appendLine(
                "| ${cell(r.probe.id)} | ${r.status} | ${cell(r.finalUrl ?: "-")} | ${r.itemCount ?: "-"} | " +
                    "${formatAge(r.newestAge)} | ${if (r.healthy) "yes" else "no"} | ${cell(r.note)} |",
            )
        }
    }

private fun renderJson(results: List<ProbeResult>): String {
    val array =
        buildJsonArray {
            for (r in results) {
                add(
                    buildJsonObject {
                        put("id", r.probe.id)
                        put("outletId", r.probe.outletId)
                        put("url", r.probe.url)
                        put("status", r.status)
                        put("finalUrl", JsonPrimitive(r.finalUrl))
                        put("items", JsonPrimitive(r.itemCount))
                        put("newestAgeHours", JsonPrimitive(r.newestAge?.inWholeHours))
                        put("healthy", r.healthy)
                        put("note", r.note)
                    },
                )
            }
        }
    return Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), array)
}
