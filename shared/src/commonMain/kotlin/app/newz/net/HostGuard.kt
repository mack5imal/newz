package app.newz.net

/**
 * The single gate every network request in Newz passes (SPEC S2): it refuses any URL whose
 * host is owned by Google, and any URL that is not plain http(s).
 */
object HostGuard {
    /**
     * Blocked domains. A host is blocked when it equals one of these or is a subdomain of one.
     * `google.<TLD>` (google.com, google.co.uk, news.google.com, …) is matched separately by
     * [isGoogleTldHost], because a suffix list cannot express "any country TLD".
     */
    val GOOGLE_HOSTS: List<String> =
        listOf(
            // SPEC S2's list (google.* is the 19th entry, matched by pattern).
            "googleusercontent.com",
            "gstatic.com",
            "googleapis.com",
            "feedburner.com",
            "feedproxy.google.com",
            "blogspot.com",
            "youtube.com",
            "ytimg.com",
            "doubleclick.net",
            "googlesyndication.com",
            "googletagmanager.com",
            "google-analytics.com",
            "youtube-nocookie.com",
            "googlevideo.com",
            "ggpht.com",
            "withgoogle.com",
            "goo.gl",
            "g.co",
            // Further Google-owned domains.
            "youtu.be",
            "blogger.com",
            "appspot.com",
            "googleblog.com",
            "android.com",
            "firebaseio.com",
            "firebaseapp.com",
            "app.goo.gl",
        )

    /** Second-level labels that sit between `google` and a country code, as in google.co.uk. */
    private val SECOND_LEVEL_LABELS =
        setOf("co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go", "gob", "mil", "nom")

    /** True when [url] must not be requested: a non-http(s) scheme, no host, or a blocked host. */
    fun isBlocked(url: String): Boolean {
        val host = hostOf(url) ?: return true
        return isBlockedHost(host)
    }

    /** True when [host] is Google-owned. Case-insensitive, ignores a trailing dot, IDN-aware. */
    fun isBlockedHost(host: String): Boolean {
        val normalized = normalizeHost(host) ?: return true
        if (normalized.isEmpty()) return true
        if (GOOGLE_HOSTS.any { normalized == it || normalized.endsWith(".$it") }) return true
        return isGoogleTldHost(normalized)
    }

    /**
     * Lower-cases [host], drops a trailing dot and converts each non-ASCII label to its
     * punycode (`xn--`) form. Fullwidth ASCII and ideographic full stops are mapped to their
     * ASCII forms first, as IDNA mapping does. Returns null when the host cannot be encoded.
     */
    internal fun normalizeHost(host: String): String? {
        val decoded = percentDecode(host.trim()) ?: return null
        if (!isValidHostSyntax(decoded)) return null
        val mapped =
            buildString {
                for (c in decoded) {
                    append(
                        when (c) {
                            '。', '．', '｡' -> '.'
                            in '！'..'～' -> (c.code - 0xFEE0).toChar()
                            else -> c
                        },
                    )
                }
            }.lowercase().removeSuffix(".")
        val labels = mapped.split('.')
        return labels
            .map { label ->
                if (label.all { it.code < 0x80 }) label else "xn--" + (Punycode.encode(label) ?: return null)
            }.joinToString(".")
    }

    /**
     * Decodes `%XX` escapes as UTF-8, as OkHttp does before it connects, so `%67oogle.com`
     * is matched as `google.com`. Returns null for a malformed escape or invalid UTF-8.
     */
    private fun percentDecode(host: String): String? {
        if ('%' !in host) return host
        val bytes = ArrayList<Byte>(host.length)
        var i = 0
        while (i < host.length) {
            val c = host[i]
            if (c == '%') {
                val hex = if (i + 3 <= host.length) host.substring(i + 1, i + 3) else return null
                val value = hex.toIntOrNull(16)
                if (value == null || hex.any { it == '+' || it == '-' }) return null
                bytes.add(value.toByte())
                i += 3
            } else {
                c.toString().encodeToByteArray().forEach { bytes.add(it) }
                i++
            }
        }
        return try {
            bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            null
        }
    }

    /**
     * False when [host] holds a character OkHttp refuses in a host (controls, space, and
     * `"#%/:?@[\]`) — so a host that survives decoding with a stray `%` is blocked. A bracketed
     * IPv6 literal is allowed.
     */
    private fun isValidHostSyntax(host: String): Boolean {
        if (host.length > 2 && host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length - 1).all { it.isLetterOrDigit() || it == ':' || it == '.' }
        }
        return host.none { it.code <= 0x20 || it.code == 0x7f || it in INVALID_HOST_CHARS }
    }

    private const val INVALID_HOST_CHARS = "\"#%/:?@[\\]"

    /** Google's brand registry TLDs: any host under them (or the bare TLD) is Google's. */
    private val GOOGLE_BRAND_TLDS =
        setOf("google", "goog", "youtube", "gle", "android", "chrome", "gmail", "googl")

    private fun isGoogleTldHost(host: String): Boolean {
        val labels = host.split('.')
        if (labels.last() in GOOGLE_BRAND_TLDS) return true
        for (i in labels.indices) {
            if (labels[i] != "google") continue
            val rest = labels.subList(i + 1, labels.size)
            if (rest.size == 1 && rest[0].isNotEmpty()) return true
            if (rest.size == 2 && rest[0] in SECOND_LEVEL_LABELS && rest[1].isNotEmpty()) return true
        }
        return false
    }

    /** Extracts the host of an http(s) URL; null for any other scheme or a malformed URL. */
    internal fun hostOf(url: String): String? {
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val rest = trimmed.substring(schemeEnd + 3)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' || it == '\\' }
        val authority = if (authorityEnd < 0) rest else rest.substring(0, authorityEnd)
        val hostPort = authority.substringAfterLast('@')
        val host =
            if (hostPort.startsWith("[")) {
                hostPort.substringBefore(']') + "]"
            } else {
                hostPort.substringBefore(':')
            }
        return host.ifEmpty { null }
    }
}

/** RFC 3492 punycode encoder, used to compare IDN hosts in their ASCII form. */
internal object Punycode {
    private const val BASE = 36
    private const val T_MIN = 1
    private const val T_MAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 128

    fun encode(label: String): String? {
        val codePoints = codePointsOf(label) ?: return null
        val output = StringBuilder()
        codePoints.filter { it < 0x80 }.forEach { output.append(it.toChar()) }
        val basicCount = output.length
        var handled = basicCount
        if (basicCount > 0) output.append('-')
        var n = INITIAL_N
        var delta = 0L
        var bias = INITIAL_BIAS
        while (handled < codePoints.size) {
            val m = codePoints.filter { it >= n }.min()
            delta += (m - n).toLong() * (handled + 1)
            n = m
            for (c in codePoints) {
                if (c < n) delta++
                if (c == n) {
                    var q = delta
                    var k = BASE
                    while (true) {
                        val t =
                            when {
                                k <= bias -> T_MIN
                                k >= bias + T_MAX -> T_MAX
                                else -> k - bias
                            }
                        if (q < t) break
                        output.append(digit((t + (q - t) % (BASE - t)).toInt()))
                        q = (q - t) / (BASE - t)
                        k += BASE
                    }
                    output.append(digit(q.toInt()))
                    bias = adapt(delta, handled + 1, handled == basicCount)
                    delta = 0
                    handled++
                }
            }
            delta++
            n++
        }
        return output.toString()
    }

    private fun adapt(
        delta: Long,
        numPoints: Int,
        first: Boolean,
    ): Int {
        var d = if (first) delta / DAMP else delta / 2
        d += d / numPoints
        var k = 0
        while (d > ((BASE - T_MIN) * T_MAX) / 2) {
            d /= BASE - T_MIN
            k += BASE
        }
        return (k + (BASE - T_MIN + 1) * d / (d + SKEW)).toInt()
    }

    private fun digit(d: Int): Char = if (d < 26) 'a' + d else '0' + (d - 26)

    private fun codePointsOf(s: String): List<Int>? {
        val result = ArrayList<Int>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isHighSurrogate()) {
                val low = s.getOrNull(i + 1)
                if (low == null || !low.isLowSurrogate()) return null
                result.add(((c.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000)
                i += 2
            } else {
                if (c.isLowSurrogate()) return null
                result.add(c.code)
                i++
            }
        }
        return result
    }
}
