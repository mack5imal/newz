package app.newz.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant

class PrefsOpsTest {
    private val t0 = Instant.fromEpochMilliseconds(1_760_000_000_000)
    private val t1 = Instant.fromEpochMilliseconds(1_760_000_060_000)

    @Test
    fun blankAndOverlongKeywordsAreRejected() {
        assertNull(normalizeKeyword("  "))
        assertNull(normalizeKeyword(""))
        assertNull(normalizeKeyword("a".repeat(61)))
        assertFailsWith<IllegalArgumentException> { UserPrefs().muteKeyword("  ", t0) }
        assertFailsWith<IllegalArgumentException> { UserPrefs().muteKeyword("a".repeat(61), t0) }
    }

    @Test
    fun sixtyCharKeywordIsAcceptedAndTrimmed() {
        val sixty = "b".repeat(60)
        assertEquals(sixty, normalizeKeyword("  $sixty  "))
        val p = UserPrefs().muteKeyword(" $sixty ", t0)
        assertEquals(listOf(MutedKeyword(sixty, t0)), p.mutedKeywords)
    }

    @Test
    fun unicodeKeywordsAreCountedInCharactersNotUtf16Units() {
        // Accented, CJK and right-to-left letters are one UTF-16 unit each; emoji are two.
        for (unit in listOf("é", "東", "ש", "😀")) {
            val sixty = unit.repeat(60)
            assertEquals(60, sixty.characterCount())
            assertEquals(sixty, normalizeKeyword(sixty), "60 × $unit")
            assertNull(normalizeKeyword(unit.repeat(61)), "61 × $unit")
        }
        assertEquals(listOf("Zürich 😀"), UserPrefs().muteKeyword("Zürich 😀", t0).mutedKeywords.map { it.text })
        // A lone surrogate still counts as one character.
        assertEquals(2, "a\uD800".characterCount())
    }

    @Test
    fun mutingIsCaseInsensitiveUnique() {
        val p = UserPrefs().muteKeyword("Trump", t0).muteKeyword("trump", t1)
        assertEquals(listOf(MutedKeyword("Trump", t0)), p.mutedKeywords)
        assertEquals(emptyList(), p.unmuteKeyword("TRUMP ").mutedKeywords)
    }

    @Test
    fun raiseLevelStopsAtMuchMore() {
        var p = UserPrefs().followTopic("tech")
        assertEquals(Level.NORMAL, p.levelOf("tech"))
        p = p.raiseLevel("tech")
        assertEquals(Level.MORE, p.levelOf("tech"))
        p = p.raiseLevel("tech")
        assertEquals(Level.MUCH_MORE, p.levelOf("tech"))
        p = p.raiseLevel("tech")
        assertEquals(Level.MUCH_MORE, p.levelOf("tech"))
        assertEquals(Level.NORMAL, p.setLevel("tech", Level.NORMAL).levelOf("tech"))
    }

    @Test
    fun removeThenRestoreOutletReturnsPreviousFollowState() {
        val before =
            UserPrefs(followedOutlets = mapOf("npr" to setOf("npr-news", "npr-politics"), "bbc" to setOf("bbc-world")))
        val removed = before.removeOutlet("npr", t0)
        assertEquals(mapOf("npr" to t0), removed.removedOutlets)
        assertEquals(before, removed.restoreOutlet("npr"))
    }

    @Test
    fun followTopicKeepsOrderAndUnfollowForgetsLevel() {
        val p =
            UserPrefs()
                .followTopic("world")
                .followTopic("tech")
                .followTopic("world")
                .raiseLevel("tech")
        assertEquals(listOf("world", "tech"), p.followedTopics)
        val q = p.unfollowTopic("tech")
        assertEquals(listOf("world"), q.followedTopics)
        assertEquals(emptyMap(), q.topicLevels)
    }

    @Test
    fun muteThenRestoreTopicKeepsFollowState() {
        val before = UserPrefs().followTopic("sports").setLevel("sports", Level.MORE)
        val muted = before.muteTopic("sports", t0)
        assertEquals(mapOf("sports" to t0), muted.mutedTopics)
        assertEquals(before, muted.restoreTopic("sports"))
    }

    @Test
    fun paywallFlagsAddAndRemove() {
        val p = UserPrefs().flagPaywall("nyt", t0)
        assertEquals(mapOf("nyt" to t0), p.paywallFlags)
        assertSame(p, p.flagPaywall("nyt", t1))
        assertEquals(emptyMap(), p.unflagPaywall("nyt").paywallFlags)
    }

    @Test
    fun zipIsValidatedAndClearable() {
        val p = UserPrefs().setZip("02134", "boston")
        assertEquals("02134", p.zip)
        assertEquals("boston", p.localMetroId)
        assertFailsWith<IllegalArgumentException> { UserPrefs().setZip("2134", null) }
        assertFailsWith<IllegalArgumentException> { UserPrefs().setZip("02134-1", null) }
        val cleared = p.clearZip()
        assertNull(cleared.zip)
        assertNull(cleared.localMetroId)
    }
}
