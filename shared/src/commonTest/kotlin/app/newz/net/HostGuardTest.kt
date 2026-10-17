package app.newz.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostGuardTest {
    // SPEC S2's list: google.* (as google.com) plus the 18 named domains.
    private val specHosts =
        listOf(
            "google.com",
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
        )

    @Test
    fun blocksEverySpecS2Host() {
        assertEquals(19, specHosts.size)
        for (host in specHosts) {
            assertTrue(HostGuard.isBlocked("https://$host/path"), host)
        }
        assertTrue(HostGuard.isBlocked("https://news.google.com/rss"))
        assertTrue(HostGuard.isBlocked("https://www.google.co.uk/"))
    }

    @Test
    fun blocksSubdomainsAndCountryTlds() {
        assertTrue(HostGuard.isBlocked("https://lh3.googleusercontent.com/a.jpg"))
        assertTrue(HostGuard.isBlocked("https://i.ytimg.com/vi/x/hq.jpg"))
        assertTrue(HostGuard.isBlocked("http://feeds.feedburner.com/x"))
        assertTrue(HostGuard.isBlocked("https://google.de/"))
        assertTrue(HostGuard.isBlocked("https://www.google.com.au/"))
        assertTrue(HostGuard.isBlocked("https://youtu.be/abc"))
        assertTrue(HostGuard.isBlocked("https://app.goo.gl/abc"))
        assertTrue(HostGuard.isBlocked("https://example.blogspot.com/feeds/posts/default"))
        assertTrue(HostGuard.isBlocked("https://x.firebaseio.com/"))
        assertTrue(HostGuard.isBlocked("https://user:pw@www.youtube.com:443/embed/x"))
    }

    @Test
    fun allowsPublisherHosts() {
        assertFalse(HostGuard.isBlocked("https://bbc.co.uk/news"))
        assertFalse(HostGuard.isBlocked("https://feeds.bbci.co.uk/news/rss.xml"))
        assertFalse(HostGuard.isBlocked("https://nytimes.com/"))
        assertFalse(HostGuard.isBlocked("https://googleplex-fan.example/"))
        assertFalse(HostGuard.isBlocked("https://notgoogle.com/"))
        assertFalse(HostGuard.isBlocked("https://mygoo.gl.example/"))
        assertFalse(HostGuard.isBlocked("http://192.0.2.1:8080/feed"))
    }

    @Test
    fun handlesUppercaseAndTrailingDots() {
        assertTrue(HostGuard.isBlocked("HTTPS://WWW.YOUTUBE.COM/watch"))
        assertTrue(HostGuard.isBlockedHost("FeedProxy.Google.COM"))
        assertTrue(HostGuard.isBlocked("https://www.google.com./"))
        assertTrue(HostGuard.isBlockedHost("gstatic.com."))
        assertFalse(HostGuard.isBlockedHost("NYTIMES.COM."))
    }

    @Test
    fun handlesIdnHosts() {
        assertEquals("xn--bcher-kva.example", HostGuard.normalizeHost("bücher.example"))
        assertEquals("xn--mnchen-3ya.de", HostGuard.normalizeHost("MÜNCHEN.de"))
        assertFalse(HostGuard.isBlocked("https://bücher.example/feed"))
        assertFalse(HostGuard.isBlocked("https://xn--bcher-kva.example/feed"))
        // Fullwidth letters and an ideographic full stop map to ASCII before matching.
        assertTrue(HostGuard.isBlocked("https://ｙｏｕｔｕｂｅ.com/x"))
        assertTrue(HostGuard.isBlocked("https://www.google。com/"))
        // A Unicode subdomain of a blocked domain is still blocked once punycoded.
        assertTrue(HostGuard.isBlocked("https://bücher.blogspot.com/"))
    }

    @Test
    fun blocksNonHttpSchemesAndMalformedUrls() {
        assertTrue(HostGuard.isBlocked("ftp://bbc.co.uk/x"))
        assertTrue(HostGuard.isBlocked("javascript:alert(1)"))
        assertTrue(HostGuard.isBlocked("file:///etc/hosts"))
        assertTrue(HostGuard.isBlocked("data:image/png;base64,AAAA"))
        assertTrue(HostGuard.isBlocked("bbc.co.uk/news"))
        assertTrue(HostGuard.isBlocked("https:///nohost"))
        assertTrue(HostGuard.isBlocked("https://www.youtube.com\\@bbc.co.uk/"))
    }

    @Test
    fun percentEncodedHostsAreDecodedBeforeMatching() {
        assertTrue(HostGuard.isBlocked("https://%67oogle.com/x"))
        assertTrue(HostGuard.isBlocked("https://www.google.com%2e/x"))
        assertTrue(HostGuard.isBlocked("https://lh3.googleusercontent%2ecom/x"))
        assertTrue(HostGuard.isBlocked("https://%zz.example/"))
        assertTrue(HostGuard.isBlocked("https://%ff.example/"))
        assertTrue(HostGuard.isBlocked("https://%2567oogle.com/x"))
        assertFalse(HostGuard.isBlocked("https://%62bc.co.uk/news"))
        assertFalse(HostGuard.isBlocked("https://b%C3%BCcher.example/feed"))
        assertFalse(HostGuard.isBlocked("https://nytimes.com/"))
        assertFalse(HostGuard.isBlocked("https://[2001:db8::1]:8443/feed"))
    }

    @Test
    fun blocksGoogleBrandTlds() {
        assertTrue(HostGuard.isBlocked("https://blog.google/rss/"))
        assertTrue(HostGuard.isBlocked("https://about.google/"))
        assertTrue(HostGuard.isBlocked("https://x.goog/"))
        for (tld in listOf("google", "goog", "youtube", "gle", "android", "chrome", "gmail", "googl")) {
            assertTrue(HostGuard.isBlockedHost("www.example.$tld"), tld)
            assertTrue(HostGuard.isBlockedHost(tld), tld)
        }
        assertFalse(HostGuard.isBlocked("https://googleplex-fan.example/"))
        assertFalse(HostGuard.isBlocked("https://notgoogle.com/"))
        assertFalse(HostGuard.isBlocked("https://bbc.co.uk/news"))
        assertFalse(HostGuard.isBlocked("https://nytimes.com/"))
        assertFalse(HostGuard.isBlocked("https://bücher.example/feed"))
        assertFalse(HostGuard.isBlocked("https://google.example.org/"))
    }

    @Test
    fun googleHostsListCoversSpecDomains() {
        for (host in specHosts.drop(1)) assertTrue(host in HostGuard.GOOGLE_HOSTS, host)
    }
}
