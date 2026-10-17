package app.newz.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.newz.ui.components.AppBarSub
import app.newz.ui.theme.LocalNewzPalette

/** Every full-screen destination. [title] is the app-bar title its placeholder shows. */
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

/** Shows the current destination; system Back pops while there is somewhere to go back to. */
@Composable
fun NavHost(
    backStack: BackStack,
    content: @Composable (Screen) -> Unit,
) {
    BackHandler(enabled = backStack.canPop) { backStack.pop() }
    content(backStack.current)
}

/** Stands in for a destination whose phase has not landed yet: its app bar and nothing else. */
@Composable
fun PlaceholderScreen(
    screen: Screen,
    onBack: () -> Unit,
) {
    val c = LocalNewzPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .testTag(Screen.tagOf(screen)),
    ) {
        AppBarSub(title = screen.title, onBack = onBack)
    }
}
