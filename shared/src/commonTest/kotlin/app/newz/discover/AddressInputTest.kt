package app.newz.discover

import kotlin.test.Test
import kotlin.test.assertEquals

class AddressInputTest {
    private fun web(
        url: String,
        host: String,
    ) = Input.WebAddress(url, host)

    @Test
    fun textWithoutADotOrSchemeIsAQuery() {
        assertEquals(Input.Query("not a url"), AddressInput.classify("not a url"))
        assertEquals(Input.Query("solar"), AddressInput.classify("  solar "))
        assertEquals(Input.Query(""), AddressInput.classify("   "))
    }

    @Test
    fun aSchemeOrDotWithoutAValidHostIsNotAnAddress() {
        assertEquals(Input.NotAnAddress, AddressInput.classify("http://"))
        assertEquals(Input.NotAnAddress, AddressInput.classify("foo..bar"))
        assertEquals(Input.NotAnAddress, AddressInput.classify("https:///path"))
        assertEquals(Input.NotAnAddress, AddressInput.classify("ftp://files.example"))
        assertEquals(Input.NotAnAddress, AddressInput.classify(".example"))
        assertEquals(Input.NotAnAddress, AddressInput.classify("3.5"))
        assertEquals(Input.NotAnAddress, AddressInput.classify("https://user@site.example"))
        assertEquals(Input.NotAnAddress, AddressInput.classify("site.example:http"))
    }

    @Test
    fun httpsIsAssumedWithoutAScheme() {
        assertEquals(web("https://tidepool.example", "tidepool.example"), AddressInput.classify("tidepool.example"))
        assertEquals(
            web("https://tidepool.example/rss?x=1", "tidepool.example"),
            AddressInput.classify("tidepool.example/rss?x=1"),
        )
    }

    @Test
    fun explicitSchemesAndPortsAreKeptAndHostsLowercased() {
        assertEquals(web("http://blog.example/Feed", "blog.example"), AddressInput.classify("HTTP://Blog.Example/Feed"))
        assertEquals(
            web("https://localhost.example:8443/a", "localhost.example"),
            AddressInput.classify("https://localhost.example:8443/a"),
        )
        assertEquals(web("https://192.168.1.10", "192.168.1.10"), AddressInput.classify("192.168.1.10"))
        assertEquals(web("https://site.example/feed", "site.example"), AddressInput.classify("site.example/feed#top"))
    }

    @Test
    fun idnHostsAreConvertedToPunycode() {
        assertEquals(
            web("https://xn--bcher-kva.example", "xn--bcher-kva.example"),
            AddressInput.classify("bücher.example"),
        )
        assertEquals(
            web("https://xn--bcher-kva.example/feed", "xn--bcher-kva.example"),
            AddressInput.classify("https://BÜCHER.example/feed"),
        )
    }

    @Test
    fun textWithASpaceAndADotIsStillAQuery() {
        assertEquals(Input.Query("St. Louis"), AddressInput.classify("St. Louis"))
    }
}
