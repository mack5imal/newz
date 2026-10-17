package app.newz.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.newz.store.HiddenArticle
import app.newz.ui.components.Button
import app.newz.ui.components.ButtonVariant
import app.newz.ui.components.Panel
import app.newz.ui.components.PlainText
import app.newz.ui.components.StatePanel
import app.newz.ui.nav.Screen
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import kotlin.time.Instant

/** Test tags on the Hidden articles rows. */
object HiddenSemantics {
    /** The row of a hidden article, `hidden:<articleId>`. */
    fun row(articleId: String): String = "hidden:$articleId"
}

@Suppress("ktlint:standard:max-line-length")
private const val HIDDEN_EMPTY_BODY =
    "When you choose Hide this article from an article’s ⋮ menu, it’s listed here so you can bring it back."

// design: settings-hidden

/**
 * Hidden articles (design `settings-hidden`, SPEC S7): an intro, then Panel "Hidden" with every
 * remembered hide, newest first, each with Restore; or a `StatePanel` when there are none. Hides
 * older than 30 days are forgotten on entry.
 */
@Composable
fun HiddenScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val hidden by viewModel.hidden.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadHidden() }
    SettingsFrame(Screen.Hidden, "Hidden articles", viewModel, onBack) {
        val rows = hidden ?: return@SettingsFrame
        if (rows.isEmpty()) {
            StatePanel(
                icon = NewzIcons.iconEyeOff,
                title = "No hidden articles",
                body = HIDDEN_EMPTY_BODY,
            )
            return@SettingsFrame
        }
        Intro("Hidden articles stay out of your feed. Restore one to see it again.")
        val now = viewModel.now()
        Panel(title = "Hidden", count = "${rows.size}") {
            rows.forEachIndexed { index, article ->
                HiddenRow(
                    article = article,
                    outlet = viewModel.outletName(article.outletId),
                    now = now,
                    showDivider = index > 0,
                    onRestore = { viewModel.restoreHidden(article.articleId) },
                )
            }
        }
    }
}

@Composable
private fun HiddenRow(
    article: HiddenArticle,
    outlet: String,
    now: Instant,
    showDivider: Boolean,
    onRestore: () -> Unit,
) {
    val c = LocalNewzPalette.current
    Column(Modifier.fillMaxWidth().testTag(HiddenSemantics.row(article.articleId))) {
        if (showDivider) Divider()
        Row(
            modifier =
                Modifier.padding(
                    start = NewzTokens.spaceL,
                    top = NewzTokens.spaceM,
                    end = NewzTokens.spaceS,
                    bottom = NewzTokens.spaceM,
                ),
            horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceCardGap),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NewzTokens.spaceXs)) {
                PlainText(text = article.title, style = NewzTokens.typeRowTitle, color = c.ink)
                PlainText(
                    text = "$outlet · Hidden ${SettingsViewModel.hiddenWhen(article.hiddenAt, now)}",
                    style = NewzTokens.typeRowSub,
                    color = c.inkMuted,
                )
            }
            Button(
                text = "Restore",
                onClick = onRestore,
                variant = ButtonVariant.Text,
                icon = NewzIcons.iconRestore,
            )
        }
    }
}
