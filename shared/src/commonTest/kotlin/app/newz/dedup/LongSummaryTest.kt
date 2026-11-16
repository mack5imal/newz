package app.newz.dedup

import app.newz.feed.FeedParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B35 (review R7): full-text feeds put whole article bodies in the summary. Grouping must judge
 * a story by its headline and opening, or shared everyday words merge unrelated stories.
 */
class LongSummaryTest {
    private val filler = (1..500).joinToString(" ") { "word$it" }

    @Test
    fun onlyTheSummarysOpeningWordsCount() {
        val opening = (1..TextFeatures.SUMMARY_WORDS).joinToString(" ") { "lead$it" }
        val withTail = TextFeatures.terms("Harbor talks resume", "$opening $filler")
        val withoutTail = TextFeatures.terms("Harbor talks resume", opening)
        assertEquals(withoutTail, withTail)
    }

    @Test
    fun theParserCapsASummaryOnAWordBoundary() =
        runTest {
            val xml =
                "<rss version=\"2.0\"><channel><title>F</title><link>https://f.example/</link>" +
                    "<item><title>Long read</title><link>https://f.example/a</link>" +
                    "<description>$filler</description></item></channel></rss>"
            val summary =
                FeedParser
                    .parse(xml.encodeToByteArray(), null)
                    .items
                    .single()
                    .summary!!
            assertTrue(summary.length <= FeedParser.MAX_SUMMARY_CHARS + 1, "${summary.length} chars")
            assertTrue(summary.endsWith("…"), summary.takeLast(20))
            val kept = summary.removeSuffix("…")
            assertTrue(filler.startsWith(kept))
            assertEquals(' ', filler[kept.length], "cut inside a word")
        }
}
