package app.newz.discover

import app.newz.net.HostGuard

/** What the search field holds: a word to search the catalog for, or a web address to check. */
sealed interface Input {
    /** A web address. [url] is absolute http(s) with an ASCII (punycode) host; [host] is that host. */
    data class WebAddress(
        val url: String,
        val host: String,
    ) : Input

    /** Text to search the catalog for, trimmed. */
    data class Query(
        val text: String,
    ) : Input

    /** It has a scheme or a dot, but no usable host. */
    data object NotAnAddress : Input
}

/**
 * Tells a web address from a search word (design `search`: "it contains a dot or a scheme").
 * https is assumed when there is no scheme, and IDN hosts are lowercased and punycode-encoded
 * (a UTS-46-lite mapping), so `bücher.example` becomes `https://xn--bcher-kva.example`.
 */
object AddressInput {
    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://")
    private val PORT = Regex("\\d{1,5}")
    private val ASCII_LABEL = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
    private val IPV4 = Regex("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})")

    fun classify(input: String): Input {
        val text = input.trim()
        val scheme = SCHEME.find(text)
        if (scheme == null) {
            // Without a scheme, text with spaces or without a dot is a search: no address has a space.
            if ('.' !in text || text.any { it.isWhitespace() }) return Input.Query(text)
            return parse("https", text)
        }
        val name = scheme.groupValues[1].lowercase()
        if (name != "http" && name != "https") return Input.NotAnAddress
        return parse(name, text.substring(scheme.value.length))
    }

    private fun parse(
        scheme: String,
        rest: String,
    ): Input {
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (authorityEnd < 0) rest else rest.substring(0, authorityEnd)
        // The fragment is never sent and never part of a feed's identity, so it is dropped.
        val tail = if (authorityEnd < 0) "" else rest.substring(authorityEnd).substringBefore('#')
        if (authority.isEmpty() || '@' in authority || '\\' in authority) return Input.NotAnAddress
        if (tail.any { it.isWhitespace() }) return Input.NotAnAddress
        val rawHost = authority.substringBeforeLast(':').takeIf { ':' in authority } ?: authority
        val port = if (':' in authority) authority.substringAfterLast(':') else null
        if (port != null && !PORT.matches(port)) return Input.NotAnAddress
        val host = asciiHost(rawHost) ?: return Input.NotAnAddress
        val portPart = port?.let { ":$it" }.orEmpty()
        return Input.WebAddress("$scheme://$host$portPart$tail", host)
    }

    /** The lowercased punycode host, or null when it is not a usable public host name. */
    internal fun asciiHost(raw: String): String? {
        if (raw.isEmpty() || raw.endsWith("..")) return null
        val host = HostGuard.normalizeHost(raw) ?: return null
        IPV4.matchEntire(host)?.let { ip ->
            return host.takeIf { ip.groupValues.drop(1).all { it.toInt() <= 255 } }
        }
        val labels = host.split('.')
        if (labels.size < 2 || host.length > 253) return null
        if (labels.any { !ASCII_LABEL.matches(it) }) return null
        val tld = labels.last()
        if (tld.all { it.isDigit() }) return null
        return host
    }
}
