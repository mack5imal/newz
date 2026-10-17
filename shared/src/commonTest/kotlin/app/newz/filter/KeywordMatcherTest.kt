package app.newz.filter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeywordMatcherTest {
    private data class Case(
        val keyword: String,
        val text: String,
        val expected: Boolean,
    )

    private val cases =
        listOf(
            Case("Trump", "Trump", true),
            Case("Trump", "trump's", true),
            Case("Trump", "Trump’s", true),
            Case("Trump", "Trumps", true),
            Case("Trump", "Donald Trump", true),
            Case("Trump", "Trump, again", true),
            Case("Trump", "(Trump)", true),
            Case("Trump", "TRUMP rallies", true),
            Case("Trump", "the trumpet player", false),
            Case("Trump", "Trumpington Street", false),
            Case("Elon Musk", "elon musk's plan", true),
            Case("Elon Musk", "Musk ox herd", false),
            Case("Elon Musk", "Elon met Musk", false),
            Case("Zoë", "Zoe wins", true),
            Case("Zoë", "ZOË WINS", true),
            Case("café", "Cafe opens", true),
            Case("Cafe", "The cafés of Paris", true),
            Case("box", "Boxes of mail", true),
            Case("Jesus", "Jesus' words", true),
            Case("rust", "rusty nails", false),
            Case("O'Brien", "O‘Brien said", true),
            Case("Łódź", "Lodz council", true),
            Case("5G", "New 5G towers", true),
            Case("Musk", "Elon Musk's company", true),
        )

    @Test
    fun caseTableMatchesExpected() {
        val failures =
            cases.filter { KeywordMatcher(listOf(it.keyword)).matches(it.text) != it.expected }
        assertEquals(emptyList(), failures, "cases whose actual result differs from expected")
        assertTrue(cases.size >= 15)
    }

    @Test
    fun firstMatchReturnsKeywordAsGivenInOrder() {
        val m = KeywordMatcher(listOf("Mars Rover", "NASA", "Trump"))
        assertEquals("NASA", m.firstMatch("NASA and Trump"))
        assertEquals("Mars Rover", m.firstMatch("Trump praises the mars rovers"))
        assertNull(m.firstMatch("nothing relevant"))
    }

    @Test
    fun emptyAndPunctuationOnlyKeywordsNeverMatch() {
        assertFalse(KeywordMatcher(emptyList()).matches("anything"))
        assertFalse(KeywordMatcher(listOf("!!!", " ")).matches("!!! wow"))
    }

    @Test
    fun normalizeFoldsAccentsCombiningMarksAndApostrophes() {
        assertEquals("zoe's cafe", KeywordMatcher.normalize("ZOË’S Café"))
        assertEquals(listOf("trump's", "plan"), KeywordMatcher.tokens("(Trump’s) plan!"))
    }
}
