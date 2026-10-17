package app.newz.net

import app.newz.feed.FeedParser
import app.newz.feed.HtmlText
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException

/**
 * Finds an article's picture on its own page, for Settings › Better pictures (B5): reads only the
 * first [PAGE_START_BYTES] of the page through the guarded [client] (no cookies; [HostGuard] on
 * every redirect hop) and takes its `og:image`, else its `twitter:image`. Nothing is sent but the
 * page's own address.
 */
class PageImageFinder(
    private val client: HttpClient,
) {
    /**
     * The picture [articleUrl]'s page names, as an absolute http(s) URL on a host [HostGuard]
     * allows; null when the page names none, can't be read, or is on a blocked host.
     */
    suspend fun find(articleUrl: String): String? {
        if (HostGuard.isBlocked(articleUrl)) return null
        val page =
            try {
                client.fetchStart(articleUrl, PAGE_START_BYTES)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return null
            }
        if (page !is FetchResult.Success) return null
        return imageOf(FeedParser.decode(page.bytes, page.charset), page.finalUrl)
    }

    companion object {
        /** How much of an article page is read: enough for its `<head>` on almost every site. */
        const val PAGE_START_BYTES = 64 * 1024

        private val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
        private val HTTP_SCHEMES = setOf("http", "https")

        /** The meta names that hold a page's picture, best first. */
        private val OPEN_GRAPH = setOf("og:image", "og:image:url", "og:image:secure_url")
        private val TWITTER = setOf("twitter:image", "twitter:image:src")

        /**
         * The page's picture in [html]: the first usable `<meta property|name="og:image" content>`,
         * else the first usable `twitter:image`. Attributes may come in any order and quoting;
         * the content is entity-decoded and resolved against [baseUrl] (the page's address after
         * redirects). A value that isn't http(s) or whose host [HostGuard] blocks is skipped.
         */
        fun imageOf(
            html: String,
            baseUrl: String,
        ): String? {
            val openGraph = mutableListOf<String>()
            val twitter = mutableListOf<String>()
            for (tag in META_TAG.findAll(html)) {
                val attrs =
                    ATTRIBUTE.findAll(tag.value).associate { m ->
                        val value = m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value.orEmpty()
                        m.groupValues[1].lowercase() to HtmlText.decodeEntities(value).trim()
                    }
                val key = (attrs["property"] ?: attrs["name"]).orEmpty().lowercase()
                val content = attrs["content"].orEmpty()
                if (content.isEmpty()) continue
                when (key) {
                    in OPEN_GRAPH -> openGraph += content
                    in TWITTER -> twitter += content
                }
            }
            return (openGraph + twitter).firstNotNullOfOrNull { resolve(it, baseUrl) }
        }

        private fun resolve(
            value: String,
            baseUrl: String,
        ): String? {
            val url =
                try {
                    URLBuilder(baseUrl).takeFrom(value).buildString()
                } catch (_: Exception) {
                    return null
                }
            val scheme = url.substringBefore("://", "").lowercase()
            if (scheme !in HTTP_SCHEMES || HostGuard.isBlocked(url)) return null
            return url
        }
    }
}
