package app.newz.feed

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FeedParserTest {
    private suspend fun parse(xml: String) = FeedParser.parse(xml.encodeToByteArray(), null)

    @Test
    fun rss2ItemsHaveTitlesAndUrls() =
        runTest {
            val feed = parse(FeedFixtures.RSS2_BASIC)
            assertEquals(FeedKind.RSS, feed.kind)
            assertEquals("Publisher & Co News", feed.title)
            assertEquals("https://publisher.example/", feed.siteUrl)
            assertEquals(5, feed.items.size)
            feed.items.forEach {
                assertTrue(it.title.isNotBlank())
                assertTrue(it.url.startsWith("https://"))
            }
        }

    @Test
    fun entitiesAreDecodedAndCdataUnwrapped() =
        runTest {
            val items = parse(FeedFixtures.RSS2_BASIC).items
            assertEquals("AT&T buys a thing", items[0].title)
            assertEquals("It’s here", items[1].title)
            assertEquals("CDATA title & more", items[2].title)
            assertEquals("Body bold text", items[0].summary)
            assertEquals("att-1", items[0].guid)
            assertEquals("https://publisher.example/its-here", items[1].guid)
        }

    @Test
    fun imagesAndDates() =
        runTest {
            val items = parse(FeedFixtures.RSS2_BASIC).items
            assertEquals("https://cdn.publisher.example/att.jpg", items[0].imageUrl)
            assertNull(items[1].imageUrl, "item with no image")
            assertNull(items[2].imageUrl, "a YouTube iframe is not an image")
            assertNull(items[3].imageUrl, "a Google-hosted image counts as no image")
            assertEquals("https://cdn.publisher.example/inline.png", items[4].imageUrl)
            assertEquals(Instant.parse("2026-10-16T09:30:00Z"), items[0].publishedAt)
            assertEquals(Instant.parse("2026-10-16T02:00:00Z"), items[1].publishedAt)
            assertNull(items[2].publishedAt, "item with no pubDate")
        }

    @Test
    fun atomFeed() =
        runTest {
            val feed = parse(FeedFixtures.ATOM)
            assertEquals(FeedKind.ATOM, feed.kind)
            assertEquals("Atom Example", feed.title)
            val item = feed.items.single()
            assertEquals("Atom entry one", item.title)
            assertEquals("https://atom.example/one", item.url)
            assertEquals(Instant.parse("2026-10-16T07:00:00Z"), item.publishedAt)
        }

    @Test
    fun rdfFeed() =
        runTest {
            val feed = parse(FeedFixtures.RDF)
            assertEquals(FeedKind.RDF, feed.kind)
            assertEquals("RDF Example", feed.title)
            val item = feed.items.single()
            assertEquals("RDF item A", item.title)
            assertEquals("https://rdf.example/a", item.url)
            assertNotNull(item.publishedAt)
        }

    @Test
    fun latin1AndWindows1252() =
        runTest {
            assertEquals(
                "Café ouvert",
                FeedParser
                    .parse(FeedFixtures.LATIN1, null)
                    .items
                    .single()
                    .title,
            )
            assertEquals(
                "It’s “quoted”",
                FeedParser
                    .parse(FeedFixtures.CP1252, "utf-8")
                    .items
                    .single()
                    .title,
            )
            assertEquals(
                "Naïve",
                FeedParser
                    .parse(FeedFixtures.HEADER_LATIN1, "ISO-8859-1")
                    .items
                    .single()
                    .title,
            )
        }

    @Test
    fun namedAndNumericEntities() {
        assertEquals("AT&T", HtmlText.decodeEntities("AT&amp;T"))
        assertEquals("’", HtmlText.decodeEntities("&#8217;"))
        assertEquals("’", HtmlText.decodeEntities("&#x2019;"))
        assertEquals("’", HtmlText.decodeEntities("&#146;"))
        assertEquals("é ÿ   — …", HtmlText.decodeEntities("&eacute; &yuml; &nbsp; &mdash; &hellip;"))
        assertEquals("😀", HtmlText.decodeEntities("&#x1F600;"))
        assertEquals("&unknown; & &#8217", HtmlText.decodeEntities("&unknown; & &#8217"))
        assertEquals("&#8217;", HtmlText.decodeEntities("&amp;#8217;"))
        assertEquals("<b>", HtmlText.toPlainText("&lt;b&gt;"))
    }

    @Test
    fun rfc822AndIsoDates() {
        assertEquals(Instant.parse("2026-10-16T14:00:00Z"), FeedDates.parse("Fri, 16 Oct 2026 10:00:00 EDT"))
        assertEquals(Instant.parse("2026-10-16T10:00:00Z"), FeedDates.parse("16 Oct 2026 10:00 +0000"))
        assertEquals(Instant.parse("2026-10-16T00:00:00Z"), FeedDates.parse("2026-10-16"))
        assertNull(FeedDates.parse("yesterday"))
        assertNull(FeedDates.parse(null))
    }

    @Test
    fun nonFeedIsRejected() =
        runTest {
            assertFailsWith<FeedParseException> { parse("<html><body>Not a feed</body></html>") }
        }

    @Test
    fun doctypeIsStripped() =
        runTest {
            val xml =
                "<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY x SYSTEM \"https://evil.example/x\">]>" +
                    "<rss version=\"2.0\"><channel><title>D</title><link>https://d.example/</link>" +
                    "<item><title>Safe</title><link>https://d.example/s</link></item></channel></rss>"
            assertEquals("Safe", parse(xml).items.single().title)
        }
}
