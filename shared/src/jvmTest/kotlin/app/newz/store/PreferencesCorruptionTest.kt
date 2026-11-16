package app.newz.store

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B32 (review R19): a damaged preferences file resets to defaults instead of crashing every launch. */
class PreferencesCorruptionTest {
    private fun file() = Files.createTempDirectory("newz-prefs").resolve(PreferencesStore.FILE_NAME)

    @Test
    fun aDamagedFileReadsAsDefaultsAndSaysSo() =
        runTest {
            val path = file()
            Files.write(path, byteArrayOf(0x0a, 0x7f, 0x12, 0x00, 0x55, 0x33))
            val store = PreferencesStore.create(path.toString())
            assertEquals(UserPrefs(), store.prefs.first())
            assertTrue(store.wasReset.value)
            // The replaced file is writable again.
            store.update { it.copy(zip = "02134") }
            assertEquals("02134", store.prefs.first().zip)
        }

    @Test
    fun aHealthyFileIsNotReported() =
        runTest {
            // DataStore allows one store per file per process, so this one is written and read.
            val store = PreferencesStore.create(file().toString())
            store.replace(UserPrefs(zip = "10001"))
            assertEquals("10001", store.prefs.first().zip)
            assertFalse(store.wasReset.value)
        }
}
