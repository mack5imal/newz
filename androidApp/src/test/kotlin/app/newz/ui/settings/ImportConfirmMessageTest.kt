package app.newz.ui.settings

import app.newz.store.BackupContents
import app.newz.store.HistoryEntry
import app.newz.store.UserPrefs
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/** B29: the restore confirm sheet says, in numbers, what the restore replaces. */
class ImportConfirmMessageTest {
    private val entry = HistoryEntry("a", "o", emptyList(), Instant.fromEpochMilliseconds(0))

    @Test
    fun aBackupWithoutHistorySaysTheHistoryStays() {
        val message =
            importConfirmMessage(PendingImport(BackupContents(UserPrefs()), savedOnPhone = 12, historyOnPhone = 40))
        assertTrue(message, "your 12 saved articles with its 0" in message)
        assertTrue(message, "Your reading history on this phone stays as it is." in message)
    }

    @Test
    fun aBackupWithHistorySaysTheHistoryIsReplaced() {
        val contents = BackupContents(UserPrefs(), history = listOf(entry))
        val message = importConfirmMessage(PendingImport(contents, savedOnPhone = 1, historyOnPhone = 1))
        assertTrue(message, "your 1 saved article with its 0" in message)
        assertTrue(message, "Your reading history (1 opened article) is replaced" in message)
    }
}
