package app.newz.net

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Article open and share accept only http(s) links (phase 19 security pass). */
class WebUrlTest {
    @Test
    fun httpAndHttpsAreAccepted() {
        for (url in listOf(
            "https://www.example.com/2026/10/17/story",
            "http://example.org/a?b=c#d",
            "HTTPS://EXAMPLE.COM/",
            "https://[2001:db8::1]/x",
        )) {
            assertTrue(WebUrl.isWebUrl(url), url)
        }
    }

    @Test
    fun everyOtherSchemeAndMalformedLinkIsRefused() {
        for (url in listOf(
            "javascript:alert(1)",
            "JavaScript:alert(document.cookie)",
            "intent://scan/#Intent;scheme=zxing;package=com.example;end",
            "file:///sdcard/Download/backup.json",
            "content://app.newz.provider/x",
            "data:text/html,<script>alert(1)</script>",
            "market://details?id=x",
            "ftp://example.com/file",
            "/relative/path",
            "example.com/no-scheme",
            "https://",
            "https:///path-only",
            " https://example.com/leading-space",
            "https://example.com/new\nline",
            "https://exa mple.com/",
            "",
        )) {
            assertFalse(WebUrl.isWebUrl(url), url)
        }
    }
}
