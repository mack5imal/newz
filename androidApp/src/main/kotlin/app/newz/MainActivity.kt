package app.newz

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import app.newz.store.TextSize
import app.newz.store.UserPrefs
import app.newz.ui.feed.FeedScreen
import app.newz.ui.feed.FeedViewModel
import app.newz.ui.nav.NavHost
import app.newz.ui.nav.Screen
import app.newz.ui.nav.rememberBackStack
import app.newz.ui.onboarding.OnboardingScreen
import app.newz.ui.onboarding.OnboardingViewModel
import app.newz.ui.saved.SavedScreen
import app.newz.ui.saved.SavedViewModel
import app.newz.ui.search.SearchScreen
import app.newz.ui.search.SearchViewModel
import app.newz.ui.settings.AboutScreen
import app.newz.ui.settings.BackupScreen
import app.newz.ui.settings.BackupViewModel
import app.newz.ui.settings.HiddenScreen
import app.newz.ui.settings.SettingsDestinations
import app.newz.ui.settings.SettingsScreen
import app.newz.ui.settings.SettingsViewModel
import app.newz.ui.settings.SourcesScreen
import app.newz.ui.settings.TopicsScreen
import app.newz.ui.suggestions.SuggestionsScreen
import app.newz.ui.suggestions.SuggestionsViewModel
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Status-bar icons are light in both themes: on the navy app bar (light) and navy paper (dark).
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent { NewzApp() }
    }
}

/** The app shell: theme from the user's text size, then the back stack once preferences are read. */
@Composable
fun NewzApp(graph: AppGraph = remember { AppGraph.get() }) {
    val prefs by graph.preferencesStore.prefs.collectAsState(initial = null)
    NewzTheme(textSize = prefs?.textSize ?: TextSize.DEFAULT) {
        Box(Modifier.fillMaxSize().background(LocalNewzPalette.current.paper)) {
            prefs?.let { NewzNavigation(graph, it) }
        }
    }
}

@Composable
private fun NewzNavigation(
    graph: AppGraph,
    prefs: UserPrefs,
) {
    val backStack = rememberBackStack(if (prefs.onboarded) Screen.Feed else Screen.Onboarding)
    NavHost(backStack) { screen ->
        when (screen) {
            Screen.Onboarding -> {
                val onboardingViewModel = viewModel { OnboardingViewModel(graph) }
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onFinished = { backStack.replaceAll(Screen.Feed) },
                )
            }

            Screen.Feed -> {
                val feedViewModel = viewModel { FeedViewModel(graph) }
                FeedScreen(
                    viewModel = feedViewModel,
                    onOpenSaved = { backStack.push(Screen.Saved) },
                    onOpenSettings = { backStack.push(Screen.Settings) },
                )
            }

            Screen.Settings -> {
                SettingsScreen(
                    viewModel = viewModel { SettingsViewModel(graph) },
                    destinations = SettingsDestinations(onBack = backStack::pop, open = backStack::push),
                )
            }

            Screen.Sources -> {
                SourcesScreen(
                    viewModel = viewModel { SettingsViewModel(graph) },
                    onBack = backStack::pop,
                    onAdd = { backStack.push(Screen.Search) },
                )
            }

            Screen.Topics -> {
                TopicsScreen(viewModel = viewModel { SettingsViewModel(graph) }, onBack = backStack::pop)
            }

            Screen.Hidden -> {
                HiddenScreen(viewModel = viewModel { SettingsViewModel(graph) }, onBack = backStack::pop)
            }

            Screen.Saved -> {
                SavedScreen(viewModel = viewModel { SavedViewModel(graph) }, onBack = backStack::pop)
            }

            Screen.Backup -> {
                BackupScreen(
                    settings = viewModel { SettingsViewModel(graph) },
                    viewModel = viewModel { BackupViewModel(graph) },
                    onBack = backStack::pop,
                )
            }

            Screen.Search -> {
                SearchScreen(viewModel = viewModel { SearchViewModel(graph) }, onBack = backStack::pop)
            }

            Screen.Suggestions -> {
                SuggestionsScreen(
                    viewModel = viewModel { SuggestionsViewModel(graph) },
                    onBack = backStack::pop,
                    onFindSources = { backStack.push(Screen.Search) },
                )
            }

            Screen.About -> {
                AboutScreen(onBack = backStack::pop)
            }
        }
    }
}
