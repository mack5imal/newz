package app.newz.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/** Every full-screen destination. [title] is its app-bar title. */
sealed interface Screen {
    val route: String
    val title: String

    data object Onboarding : Screen {
        override val route = "onboarding"
        override val title = "Newz"
    }

    data object Feed : Screen {
        override val route = "feed"
        override val title = "Newz"
    }

    data object Settings : Screen {
        override val route = "settings"
        override val title = "Settings"
    }

    data object Sources : Screen {
        override val route = "sources"
        override val title = "Sources"
    }

    data object Topics : Screen {
        override val route = "topics"
        override val title = "Topics"
    }

    data object Hidden : Screen {
        override val route = "hidden"
        override val title = "Hidden"
    }

    data object Saved : Screen {
        override val route = "saved"
        override val title = "Saved"
    }

    data object Search : Screen {
        override val route = "search"
        override val title = "Search"
    }

    data object Suggestions : Screen {
        override val route = "suggestions"
        override val title = "Suggestions"
    }

    data object Backup : Screen {
        override val route = "backup"
        override val title = "Backup"
    }

    data object About : Screen {
        override val route = "about"
        override val title = "About"
    }

    companion object {
        val all: List<Screen> by lazy {
            listOf(Onboarding, Feed, Settings, Sources, Topics, Hidden, Saved, Search, Suggestions, Backup, About)
        }

        fun of(route: String): Screen = all.first { it.route == route }

        /** The test tag on a destination's root, e.g. `screen:settings`. */
        fun tagOf(screen: Screen): String = "screen:${screen.route}"
    }
}

/** The app's back stack: the last entry is showing; system Back pops (see [NavHost]). */
class BackStack internal constructor(
    internal val entries: SnapshotStateList<Screen>,
) {
    val current: Screen get() = entries.last()

    val canPop: Boolean get() = entries.size > 1

    fun push(screen: Screen) {
        if (current != screen) entries.add(screen)
    }

    fun pop() {
        if (canPop) entries.removeAt(entries.lastIndex)
    }

    /** Clears the stack down to [screen] alone (e.g. Feed once onboarding finishes). */
    fun replaceAll(screen: Screen) {
        entries.clear()
        entries.add(screen)
    }
}

/** A back stack starting at [start] that survives configuration changes and process death. */
@Composable
fun rememberBackStack(start: Screen): BackStack {
    val entries =
        rememberSaveable(
            saver =
                listSaver<SnapshotStateList<Screen>, String>(
                    save = { list -> list.map { it.route } },
                    restore = { routes -> mutableStateListOf<Screen>().apply { addAll(routes.map(Screen::of)) } },
                ),
        ) { mutableStateListOf(start) }
    return BackStack(entries)
}

/** The key of the back-stack entry at [index]: an entry keeps its place for as long as it lives. */
internal fun entryKey(
    index: Int,
    screen: Screen,
): String = "$index/${screen.route}"

/**
 * The view models of every back-stack entry, by [entryKey] (B57). It lives in the activity's own
 * store, so an entry's view models outlive rotation, and an entry's are cleared when it leaves
 * the stack instead of living as long as the activity.
 */
internal class EntryViewModelStores : ViewModel() {
    private val stores = HashMap<String, ViewModelStore>()

    fun storeFor(key: String): ViewModelStore = stores.getOrPut(key) { ViewModelStore() }

    fun clear(key: String) {
        stores.remove(key)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach(ViewModelStore::clear)
        stores.clear()
    }
}

/**
 * Shows the current destination; system Back pops while there is somewhere to go back to.
 *
 * Each entry has its own saved state and its own view models. Its rememberSaveable state (the
 * feed's scroll position, a section's "Show all") is kept while it is covered by another screen
 * and restored on Back (B47), and survives process death. Both are dropped when the entry is
 * popped, so the next visit starts fresh rather than replaying, say, an old snackbar (B57).
 */
@Composable
fun NavHost(
    backStack: BackStack,
    content: @Composable (Screen) -> Unit,
) {
    BackHandler(enabled = backStack.canPop) { backStack.pop() }
    val keys = backStack.entries.mapIndexed(::entryKey)
    val key = keys.last()
    val holder = rememberSaveableStateHolder()
    val stores: EntryViewModelStores = viewModel()
    val owner =
        remember(key) {
            val store = stores.storeFor(key)
            object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = store
            }
        }
    holder.SaveableStateProvider(key) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            content(backStack.current)
        }
    }
    val known = remember { mutableSetOf<String>() }
    SideEffect {
        val live = keys.toSet()
        for (gone in known - live) {
            holder.removeState(gone)
            stores.clear(gone)
        }
        known.clear()
        known.addAll(live)
    }
}
