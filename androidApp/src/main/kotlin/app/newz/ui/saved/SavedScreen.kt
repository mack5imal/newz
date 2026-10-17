package app.newz.ui.saved

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import app.newz.ui.components.OfflineBanner
import app.newz.ui.components.SavedCard
import app.newz.ui.components.StatePanel
import app.newz.ui.nav.Screen
import app.newz.ui.settings.ScreenFrame
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens

/** Test tags on the Saved list. */
object SavedSemantics {
    /** The card of a saved article, `saved:<articleId>`. */
    fun card(articleId: String): String = "saved:$articleId"
}

private const val SAVED_OFFLINE = "You’re offline. Your list is here; articles open once you’re back online."

// design: saved

/**
 * Saved for later (design `saved`): `SavedCard`s, newest saved first (12 margins, 14 gaps). A tap
 * opens the article; Share opens the share sheet; Unsave removes the card with Undo. Offline, an
 * `OfflineBanner` with Retry sits above the still-complete list; with nothing saved, a
 * `StatePanel`.
 */
@Composable
fun SavedScreen(
    viewModel: SavedViewModel,
    onBack: () -> Unit,
) {
    val saved by viewModel.saved.collectAsState()
    val offline by viewModel.offline.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load() }
    ScreenFrame(Screen.Saved, "Saved", message, viewModel::dismissMessage, onBack) {
        if (offline) OfflineBanner(message = SAVED_OFFLINE, onRetry = viewModel::retry)
        val items = saved ?: return@ScreenFrame
        if (items.isEmpty()) {
            StatePanel(
                icon = NewzIcons.iconBookmark,
                title = "Nothing saved yet",
                body = "Tap ⋮ on any article and choose Save for later. Your saved list stays on this phone.",
            )
            return@ScreenFrame
        }
        items.forEach { item ->
            val article = item.article
            SavedCard(
                outlet = viewModel.outletName(article),
                age = viewModel.shortAge(article),
                spokenAge = viewModel.spokenAge(article),
                headline = article.title,
                imageUrl = article.imageUrl,
                topic = viewModel.topicName(article),
                onOpen = { viewModel.open(context, article) },
                onShare = { viewModel.share(context, article) },
                onUnsave = { viewModel.unsave(item) },
                modifier =
                    Modifier
                        .padding(start = NewzTokens.spaceM, end = NewzTokens.spaceM, bottom = NewzTokens.spaceCardGap)
                        .testTag(SavedSemantics.card(article.id)),
            )
        }
    }
}
