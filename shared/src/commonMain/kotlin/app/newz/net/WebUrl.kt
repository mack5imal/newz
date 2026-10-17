package app.newz.net

/**
 * The only links Newz hands to a browser or the share sheet: absolute http(s) URLs with a host.
 * Anything else (`javascript:`, `intent:`, `file:`, `content:`, `data:`, a relative path, or a
 * link with surrounding whitespace or control characters) is refused.
 */
object WebUrl {
    fun isWebUrl(url: String): Boolean {
        if (url.isEmpty() || url != url.trim()) return false
        if (url.any { it.isWhitespace() || it.isISOControl() }) return false
        return HostGuard.hostOf(url) != null
    }
}
