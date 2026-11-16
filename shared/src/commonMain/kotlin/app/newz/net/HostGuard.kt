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
            // Ad, analytics and tag hosts that feed images and links point at (B24).
            "2mdn.net",
            "doubleclick.com",
            "googleadservices.com",
            "googletagservices.com",
            "app-measurement.com",
            "admob.com",
            // Blogger's image host, Google-hosted custom domains and Google's own network.
            "blogblog.com",
            "googlehosted.com",
            "1e100.net",
            "gvt1.com",
            "gvt2.com",
            "gvt3.com",
            // Firebase hosting, dynamic links and storage.
            "page.link",
            "web.app",
            "firebasestorage.app",
            // Mirrors of Google services under other names.
            "recaptcha.net",
            "gstatic.cn",
            "googleapis.cn",
            "googleusercontent.cn",
            // The AMP cache is served by Google.
            "cdn.ampproject.org",
            "ampproject.net",
            "gmail.com",
            "googlemail.com",
            "youtubekids.com",
        )

    /** Second-level labels that sit between `google` and a country code, as in google.co.uk. */
    private val SECOND_LEVEL_LABELS =
        setOf("co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go", "gob", "mil", "nom")

    /** True when [url] must not be requested: a non-http(s) scheme, no host, or a blocked host. */
    fun isBlocked(url: String): Boolean {
        val host = hostOf(url) ?: return true
        return isBlockedHost(host)
    }

    /**
     * True when [host] is Google-owned. Case-insensitive, ignores a trailing dot, IDN-aware.
     *
     * The host is judged twice: in HostGuard's own normalised form, and in the form the HTTP
     * engine will really connect to ([engineCanonicalHost]). OkHttp applies full UTS-46 mapping,
     * so `ⓖoogle.com` or `goo<soft hyphen>gle.com` reach google.com even though they look like
     * other names here (B23). A host is blocked when either form is Google's or either cannot be
     * read.
     */
    fun isBlockedHost(host: String): Boolean {
        // OkHttp reports an IPv6 host without its brackets; put them back so it parses here.
        val bracketed = if (':' in host && !host.startsWith("[")) "[$host]" else host
        val own = normalizeHost(bracketed) ?: return true
        val engineHost = engineCanonicalHost(bracketed) ?: return true
        val engine =
            normalizeHost(if (':' in engineHost && !engineHost.startsWith("[")) "[$engineHost]" else engineHost)
                ?: return true
        if (isRefusedIpLiteral(own) || isRefusedIpLiteral(engine)) return true
        return isGoogleHost(own) || isGoogleHost(engine)
    }

    /**
     * True for an IP-address host other than loopback (B26). A name check cannot tell a Google
     * address (142.250.80.46) from any other, and a literal lets a feed make the phone open a
     * connection to an address it chose, LAN addresses included. Loopback never leaves the
     * phone, and the UI tests' feed server listens on 127.0.0.1.
     *
     * A host whose last label is numeric counts as an address in any spelling: the resolvers
     * also read `2130706433` or `0x7f.1` as addresses, and no real top-level domain is numeric.
     */
    internal fun isRefusedAddress(host: String): Boolean {
        val bracketed = if (':' in host && !host.startsWith("[")) "[$host]" else host
        return isRefusedIpLiteral(normalizeHost(bracketed) ?: return false)
    }

    private fun isRefusedIpLiteral(normalized: String): Boolean {
        if (normalized.startsWith("[")) return normalized != "[::1]"
        val last = normalized.substringAfterLast('.')
        val numeric = last.isNotEmpty() && (last.all { it.isDigit() } || last.startsWith("0x"))
        if (!numeric) return false
        return !LOOPBACK_V4.matches(normalized)
    }

    private val LOOPBACK_V4 = Regex("127\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){2}")

    private fun isGoogleHost(normalized: String): Boolean {
        if (normalized.isEmpty()) return true
        if (GOOGLE_HOSTS.any { normalized == it || normalized.endsWith(".$it") }) return true
        return isGoogleTldHost(normalized)
    }

    /**
     * Lower-cases [host], drops a trailing dot and converts each non-ASCII label to its
     * punycode (`xn--`) form. Existing `xn--` labels are decoded first, because the engine
     * decodes them too (`xn--google-` is `google`). Fullwidth ASCII and ideographic full stops
     * are mapped to their ASCII forms, as IDNA mapping does. Returns null when the host cannot
     * be decoded or encoded.
     */
    internal fun normalizeHost(host: String): String? {
        val percentDecoded = percentDecode(host.trim()) ?: return null
        if (!isValidHostSyntax(percentDecoded)) return null
        val decoded =
            percentDecoded
                .split('.')
                .map { label ->
                    if (label.startsWith("xn--", ignoreCase = true)) {
                        Punycode.decode(label.substring(4)) ?: return null
                    } else {
                        label
                    }
                }.joinToString(".")
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

/**
 * [host] as the platform's HTTP engine will connect to it, or null when the engine rejects it.
 * On JVM and Android this is OkHttp's own canonical form (B23).
 */
internal expect fun engineCanonicalHost(host: String): String?

/** RFC 3492 punycode encoder and decoder, used to compare IDN hosts in their ASCII form. */
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

    /**
     * Decodes the part of an `xn--` label after the prefix, or returns null when it is not
     * valid punycode. HostGuard needs this because the engine decodes `xn--google-` to `google`
     * before it connects (B23).
     */
    fun decode(input: String): String? {
        val delimiter = input.lastIndexOf('-').coerceAtLeast(0)
        val output = ArrayList<Int>(input.length)
        for (c in input.substring(0, delimiter)) {
            if (c.code >= 0x80) return null
            output.add(c.code)
        }
        var n = INITIAL_N.toLong()
        var i = 0L
        var bias = INITIAL_BIAS
        var pos = if (delimiter > 0) delimiter + 1 else 0
        while (pos < input.length) {
            val oldI = i
            var w = 1L
            var k = BASE
            while (true) {
                if (pos >= input.length) return null
                val d = digitValue(input[pos++]) ?: return null
                i += d * w
                if (i > MAX_CODE_POINT * (output.size + 1L)) return null
                val t =
                    when {
                        k <= bias -> T_MIN
                        k >= bias + T_MAX -> T_MAX
                        else -> k - bias
                    }
                if (d < t) break
                w *= BASE - t
                k += BASE
            }
            bias = adapt(i - oldI, output.size + 1, oldI == 0L)
            n += i / (output.size + 1)
            if (n > MAX_CODE_POINT || n in 0xD800L..0xDFFFL) return null
            i %= (output.size + 1)
            output.add(i.toInt(), n.toInt())
            i++
        }
        return buildString {
            for (cp in output) {
                if (cp < 0x10000) {
                    append(cp.toChar())
                } else {
                    append((0xD800 + ((cp - 0x10000) shr 10)).toChar())
                    append((0xDC00 + ((cp - 0x10000) and 0x3FF)).toChar())
                }
            }
        }
    }

    private const val MAX_CODE_POINT = 0x10FFFFL

    private fun digitValue(c: Char): Long? =
        when (c) {
            in 'a'..'z' -> (c - 'a').toLong()
            in 'A'..'Z' -> (c - 'A').toLong()
            in '0'..'9' -> (c - '0' + 26).toLong()
            else -> null
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
