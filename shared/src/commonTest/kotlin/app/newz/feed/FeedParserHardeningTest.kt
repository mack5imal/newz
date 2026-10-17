package app.newz.feed

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/** Hostile and extreme feeds (phase 19): entity attacks, huge and empty feeds, odd headlines and links. */
class FeedParserHardeningTest {
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

    @Test
    fun feedWithNoItemsParses() =
        runTest {
            val feed = parse(rss(emptyList()))
            assertEquals(0, feed.items.size)
            assertEquals("Edge", feed.title)
        }

    @Test
    fun fiveThousandItemsAreCutToTheNewestThreeHundredInFeedOrder() =
        runTest {
            val base = Instant.parse("2026-10-17T12:00:00Z")
            // Oldest first, so the newest 300 are the last 300 in the document.
            val items =
                (0 until 5_000).map { i ->
                    Item(
                        "Story number $i",
                        "https://e.example/$i",
                        base - (5_000 - i).hours,
                    )
                }
            val mark = TimeSource.Monotonic.markNow()
            val feed = parse(rss(items))
            assertTrue(mark.elapsedNow() < 10.seconds, "5,000 items took ${mark.elapsedNow()}")
            assertEquals(FeedParser.MAX_ITEMS, feed.items.size)
            assertEquals((4_700 until 5_000).map { "https://e.example/$it" }, feed.items.map { it.url })
        }

    @Test
    fun undatedItemsAreDroppedBeforeDatedOnesWhenCapping() {
        val dated =
            (0 until 300).map {
                ParsedItem("d$it", "https://e.example/d$it", "D$it", null, null, Instant.parse("2026-10-17T00:00:00Z"))
            }
        val undated = (0 until 10).map { ParsedItem("u$it", "https://e.example/u$it", "U$it", null, null, null) }
        assertEquals(dated, FeedParser.newest(undated + dated))
    }

    @Test
    fun longEmojiAndRightToLeftHeadlinesSurviveWhole() =
        runTest {
            val long = "Harbor council weighs a long plan " + "x".repeat(300 - 34)
            val emoji = "Storm season 🌀🌊 brings record surf 🏄‍♀️ to the coast"
            val rtl = "Talks resume in القاهرة as מערכת delegates arrive"
            val feed =
                parse(
                    rss(
                        listOf(
                            Item(long, "https://e.example/long", null),
                            Item(emoji, "https://e.example/emoji", null),
                            Item(rtl, "https://e.example/rtl", null),
                        ),
                    ),
                )
            assertEquals(listOf(long, emoji, rtl), feed.items.map { it.title })
            assertEquals(300, feed.items[0].title.length)
        }

    @Test
    fun nonWebLinksAreDropped() =
        runTest {
            val feed =
                parse(
                    rss(
                        listOf(
                            Item("Script", "javascript:alert(1)", null),
                            Item("Intent", "intent://scan/#Intent;scheme=zxing;end", null),
                            Item("File", "file:///sdcard/secret.txt", null),
                            Item("Relative", "/2026/10/story", null),
                            Item("Fine", "https://e.example/fine", null),
                        ),
                    ),
                )
            assertEquals(listOf("Fine"), feed.items.map { it.title })
        }

    @Test
    fun brokenImageUrlIsKeptAsAnUnloadableImageNotAnError() =
        runTest {
            val xml =
                "<rss version=\"2.0\"><channel><title>I</title><link>https://i.example/</link>" +
                    "<item><title>Broken image</title><link>https://i.example/a</link>" +
                    "<enclosure url=\"https://i.example/missing.jpg\" type=\"image/jpeg\"/></item>" +
                    "<item><title>Not a URL</title><link>https://i.example/b</link>" +
                    "<enclosure url=\"ht!tp:/broken\" type=\"image/jpeg\"/></item></channel></rss>"
            val feed = parse(xml)
            assertEquals("https://i.example/missing.jpg", feed.items[0].imageUrl)
            assertEquals(null, feed.items[1].imageUrl)
        }

    private data class Item(
        val title: String,
        val link: String,
        val date: Instant?,
    )

    private fun rss(items: List<Item>): String =
        buildString {
            append("<rss version=\"2.0\"><channel><title>Edge</title><link>https://e.example/</link>")
            for (item in items) {
                append("<item><title>").append(escape(item.title)).append("</title>")
                append("<link>").append(escape(item.link)).append("</link>")
                if (item.date != null) append("<pubDate>").append(item.date.toString()).append("</pubDate>")
                append("</item>")
            }
            append("</channel></rss>")
        }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
}
