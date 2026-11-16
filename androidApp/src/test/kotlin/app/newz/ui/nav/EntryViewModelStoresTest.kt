package app.newz.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** B57: each back-stack entry has its own view models, cleared when the entry leaves the stack. */
class EntryViewModelStoresTest {
    private class Probe : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private fun EntryViewModelStores.probe(key: String): Probe {
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = storeFor(key)
            }
        return ViewModelProvider.create(owner, viewModelFactory { initializer { Probe() } })[Probe::class]
    }

    @Test
    fun anEntryKeepsItsViewModelsUntilItIsCleared() {
        val stores = EntryViewModelStores()
        val settings = stores.probe(entryKey(1, Screen.Settings))
        val sources = stores.probe(entryKey(2, Screen.Sources))
        assertNotSame(settings, sources)
        assertSame(settings, stores.probe(entryKey(1, Screen.Settings)))

        stores.clear(entryKey(2, Screen.Sources))
        assertTrue(sources.cleared)
        assertFalse(settings.cleared)
        // The next visit starts with a fresh one.
        assertNotSame(sources, stores.probe(entryKey(2, Screen.Sources)))
    }
}
