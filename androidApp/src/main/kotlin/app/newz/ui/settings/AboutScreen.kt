package app.newz.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.newz.ui.components.AppBarSub
import app.newz.ui.components.PlainText
import app.newz.ui.nav.Screen
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzTokens

/** The About page's paragraphs, under their headings. Not drawn in the design; plain text. */
private val ABOUT: List<Pair<String, List<String>>> =
    listOf(
        "Privacy" to
            listOf(
                "Newz has no accounts, no trackers and no analytics. Everything you choose — topics, " +
                    "sources, muted words, saved articles and reading history — stays on this phone.",
                "Newz contacts only publishers’ feeds and images, and the pages you open. It never contacts Google.",
                "Your zip code is matched to an area on this phone and is never sent anywhere.",
            ),
        "Fonts" to
            listOf(
                "Big Shoulders Display and Schibsted Grotesk, used under the SIL Open Font License 1.1 (OFL).",
            ),
        "Data" to
            listOf(
                "Outlet popularity: Majestic Million, © Majestic, CC BY 3.0 " +
                    "(creativecommons.org/licenses/by/3.0). Only a score per outlet ships with the app.",
                "Zip codes and metro areas: US Census Bureau, public domain.",
            ),
    )

/**
 * About (not drawn): the privacy statement and credits as plain `type-body` text on `paper`,
 * under `AppBarSub` "About".
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = LocalNewzPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .testTag(Screen.tagOf(Screen.About)),
    ) {
        AppBarSub(title = "About", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NewzTokens.spaceL, vertical = NewzTokens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
        ) {
            ABOUT.forEach { (heading, paragraphs) ->
                PlainText(
                    text = heading,
                    style = NewzTokens.typeBody,
                    color = c.ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                paragraphs.forEach { PlainText(text = it, style = NewzTokens.typeBody, color = c.ink) }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
