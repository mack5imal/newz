package app.newz.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.newz.catalog.Catalog
import app.newz.ui.components.BottomActionBar
import app.newz.ui.components.FlapWordmark
import app.newz.ui.components.PlainText
import app.newz.ui.components.SplitRule
import app.newz.ui.components.TopicChip
import app.newz.ui.local.LocalAreaNote
import app.newz.ui.local.ZipCodeSheet
import app.newz.ui.nav.Screen
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText

/** The privacy sentence under the header's heading. */
@Suppress("ktlint:standard:max-line-length")
private const val PRIVACY = "No accounts and no trackers — what you read and choose never leaves this phone."

// design: onboarding

/**
 * Pick your topics (design `onboarding`), shown on first launch only: the header (the still
 * `FlapWordmark`, "News that stays on your phone." and the privacy sentence), `SplitRule`, "Pick at
 * least 3 topics" over the catalog's topics as `TopicChip`s in catalog order, and the
 * `BottomActionBar`. Continue is enabled with [MIN_TOPICS] picked (and, when Local is picked, a
 * saved zip); it saves the topics and calls [onFinished]. Picking Local opens the [ZipCodeSheet];
 * cancelling it unpicks Local.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
) {
    val c = LocalNewzPalette.current
    val picked by viewModel.picked.collectAsState()
    val local by viewModel.local.collectAsState()
    var zipSheetOpen by rememberSaveable { mutableStateOf(false) }
    val count = picked.size
    val ready = count >= MIN_TOPICS

    Box(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .testTag(Screen.tagOf(Screen.Onboarding)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                OnboardingHeader()
                SplitRule()
                Column(Modifier.padding(horizontal = NewzTokens.spaceL, vertical = NewzTokens.onboardingGap)) {
                    SignageText(
                        text = "Pick at least 3 topics",
                        style = NewzTokens.typeLabel,
                        color = c.inkMuted,
                        modifier = Modifier.padding(bottom = NewzTokens.spaceS).semantics { heading() },
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceS),
                        verticalArrangement = Arrangement.spacedBy(NewzTokens.spaceS),
                    ) {
                        viewModel.topics.forEach { topic ->
                            TopicChip(
                                text = topic.name,
                                selected = topic.id in picked,
                                onClick = {
                                    viewModel.toggle(topic.id)
                                    if (topic.id == Catalog.LOCAL_TOPIC && topic.id !in picked) zipSheetOpen = true
                                },
                            )
                        }
                    }
                    local?.takeIf { Catalog.LOCAL_TOPIC in picked && !zipSheetOpen }?.let { area ->
                        LocalAreaNote(area, Modifier.padding(top = NewzTokens.spaceS))
                    }
                }
            }
            BottomActionBar(
                status = if (ready) "$count picked" else "$count picked — pick at least 3",
                subStatus = if (ready) "Change them any time in Settings" else null,
                buttonLabel = "Continue",
                onButton = { viewModel.finish(onFinished) },
                buttonEnabled = viewModel.canContinue(picked, local),
                buttonTrailingIcon = if (ready) NewzIcons.iconArrowRight else null,
            )
        }
        if (zipSheetOpen) {
            ZipCodeSheet(
                resolver = viewModel.zipResolver,
                initialZip = local?.zip,
                onSave = { area ->
                    viewModel.saveZip(area)
                    zipSheetOpen = false
                },
                onDismiss = {
                    zipSheetOpen = false
                    val localPicked = Catalog.LOCAL_TOPIC in viewModel.picked.value
                    if (local == null && localPicked) viewModel.toggle(Catalog.LOCAL_TOPIC)
                },
            )
        }
    }
}

/** The header band: `navy` in light; in dark, `paper` with a `sky` rule. */
@Composable
private fun OnboardingHeader() {
    val c = LocalNewzPalette.current
    Column(Modifier.fillMaxWidth().background(if (c.isDark) c.paper else c.navy)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Column(
            Modifier.padding(
                start = NewzTokens.spaceXl,
                end = NewzTokens.spaceXl,
                top = NewzTokens.onboardingGap,
                bottom = NewzTokens.onboardingHeaderBottom,
            ),
        ) {
            FlapWordmark(busy = false, heading = false)
            PlainText(
                text = "News that stays on your phone.",
                style = NewzTokens.typeHeading,
                color = c.sky,
                modifier = Modifier.padding(top = NewzTokens.onboardingGap).semantics { heading() },
            )
            PlainText(
                text = PRIVACY,
                style = NewzTokens.typeBody,
                color = c.sky,
                modifier = Modifier.padding(top = NewzTokens.spaceS),
            )
        }
        if (c.isDark) Box(Modifier.fillMaxWidth().height(NewzTokens.rule).background(c.sky))
    }
}
