package app.newz.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.store.TextSize
import app.newz.ui.components.AppBarMain
import app.newz.ui.components.AppBarSub
import app.newz.ui.components.AppIcon
import app.newz.ui.components.ArticleCard
import app.newz.ui.components.ArticleImage
import app.newz.ui.components.BoardPanel
import app.newz.ui.components.BottomActionBar
import app.newz.ui.components.BottomSheet
import app.newz.ui.components.Button
import app.newz.ui.components.ButtonVariant
import app.newz.ui.components.CardActions
import app.newz.ui.components.CardStrip
import app.newz.ui.components.Checkbox
import app.newz.ui.components.FlapAge
import app.newz.ui.components.FlapTile
import app.newz.ui.components.GroupButton
import app.newz.ui.components.IconButton
import app.newz.ui.components.ListRow
import app.newz.ui.components.LoadingBoard
import app.newz.ui.components.NavButton
import app.newz.ui.components.NoImagePanel
import app.newz.ui.components.NoteRow
import app.newz.ui.components.OfflineBanner
import app.newz.ui.components.Panel
import app.newz.ui.components.PanelHeader
import app.newz.ui.components.RankTile
import app.newz.ui.components.SavedCard
import app.newz.ui.components.SearchField
import app.newz.ui.components.SegmentedControl
import app.newz.ui.components.SheetActionRow
import app.newz.ui.components.SkeletonCard
import app.newz.ui.components.Snackbar
import app.newz.ui.components.SnackbarAction
import app.newz.ui.components.SplitRule
import app.newz.ui.components.StatePanel
import app.newz.ui.components.StoryRow
import app.newz.ui.components.Switch
import app.newz.ui.components.Tag
import app.newz.ui.components.TagKind
import app.newz.ui.components.TextField
import app.newz.ui.components.TopicChip
import app.newz.ui.components.TopicChipRow
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentGalleryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var dark by mutableStateOf(false)
    private var textSize by mutableStateOf(TextSize.DEFAULT)

    private fun setGallery(content: @Composable () -> Unit = { Gallery() }) {
        composeRule.setContent {
            NewzTheme(darkTheme = dark, textSize = textSize) {
                Box(Modifier.background(LocalNewzPalette.current.paper)) { content() }
            }
        }
    }

    private fun SemanticsNode.spokenLabel(): String {
        val texts = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        val descriptions = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        val editable = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        return "$texts $descriptions $editable".trim()
    }

    @Test
    fun everyClickableHasALabelAndA48dpTarget_lightDarkDefaultExtraLarge() {
        setGallery()
        val minTarget = with(composeRule.density) { 48.dp.roundToPx() }
        var checked = 0
        for (isDark in listOf(false, true)) {
            for (size in listOf(TextSize.DEFAULT, TextSize.EXTRA_LARGE)) {
                dark = isDark
                textSize = size
                composeRule.waitForIdle()
                val nodes = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
                assertTrue("gallery has clickable nodes (dark=$isDark, $size): ${nodes.size}", nodes.size >= 30)
                for (node in nodes) {
                    val where = "node ${node.id} (dark=$isDark, $size, label='${node.spokenLabel()}')"
                    assertTrue("$where has no spoken label", node.spokenLabel().isNotBlank())
                    assertTrue("$where is ${node.size.width}px wide (< 48 dp)", node.size.width >= minTarget)
                    assertTrue("$where is ${node.size.height}px tall (< 48 dp)", node.size.height >= minTarget)
                    checked++
                }
            }
        }
        assertTrue(checked >= 120)
    }

    @Test
    fun groupButtonFlapAgeRankAndDisabledSheetRow() {
        setGallery()
        composeRule.onNodeWithTag(TAG_GROUP_ONE).assertTextEquals("+1 source")
        composeRule.onNodeWithTag(TAG_GROUP_THREE).assertTextEquals("+3 sources")
        composeRule.onNodeWithTag(TAG_FLAP_AGE).assertContentDescriptionEquals("2 hours ago")
        composeRule.onNodeWithTag(TAG_RANK).assertContentDescriptionEquals("Rank 3")
        composeRule
            .onNodeWithTag(TAG_DISABLED_ROW)
            .assert(hasNoClickAction())
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_ENABLED_ROW).assert(hasClickAction())
        composeRule.onNodeWithTag(TAG_PLAIN_SNACKBAR).assert(hasNoClickAction())
        composeRule
            .onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(TAG_PLAIN_SNACKBAR)))
            .assertCountEquals(0)
    }

    @Test
    fun sixLineHeadlineAtExtraLargeIsNotClipped() {
        dark = false
        textSize = TextSize.EXTRA_LARGE
        // Inside a scrolling list, as in the feed: height is unbounded.
        setGallery {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ArticleCard(
                    outlet = "The Harbor Ledger and Evening Courier of the Northern Coast",
                    age = "2h",
                    headline = LONG_HEADLINE,
                    imageUrl = null,
                    topic = "Climate",
                    onOpen = {},
                    onShare = {},
                    onMore = {},
                )
            }
        }
        val node = composeRule.onNode(hasText(LONG_HEADLINE), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertTrue("headline has ${layout.lineCount} lines (want ≥ 6)", layout.lineCount >= 6)
        assertFalse(
            "headline overflows at Extra large: ${layout.lineCount} lines, ${layout.size}",
            layout.hasVisualOverflow,
        )
    }

    @Composable
    private fun Gallery() {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            AppBarMain(onSaved = {}, onSettings = {})
            AppBarSub(
                title = "Sources",
                onBack = {},
                trailing = { NavButton(label = "Add", icon = NewzIcons.iconPlus, onClick = {}) },
            )
            AppBarSub(title = "", onBack = {}, titleContent = {
                SearchField(value = "", onValueChange = {}, placeholder = "Search outlets or paste a link")
            })
            SplitRule()
            TopicChipRow {
                TopicChip(text = "World", onClick = {}, selected = true)
                TopicChip(text = "Science", onClick = {})
                TopicChip(text = "AI", onClick = {})
                TopicChip(text = "crypto", onClick = {}, muted = true)
            }
            ArticleCard(
                outlet = "The Harbor Ledger",
                age = "2h",
                headline = "Deep-sea survey maps the currents carrying warm water toward Antarctica’s ice shelves",
                imageUrl = GOOGLE_IMAGE,
                topic = "Climate",
                onOpen = {},
                onShare = {},
                onMore = {},
                groupCount = 3,
            )
            ArticleCard(
                outlet = "Open Circuit",
                age = "45m",
                headline = "What the new battery recycling rules mean for the old phone in your drawer",
                imageUrl = null,
                topic = "Tech",
                onOpen = {},
                onShare = {},
                onMore = {},
            )
            CardStrip(outlet = "Open Circuit", age = "5m")
            ArticleImage(url = GOOGLE_IMAGE)
            NoImagePanel(topic = "Science")
            CardActions(onShare = {}, onMore = {}, groupCount = 1, onGroup = {})
            GroupButton(count = 1, onClick = {}, modifier = Modifier.testTag(TAG_GROUP_ONE))
            GroupButton(count = 3, onClick = {}, modifier = Modifier.testTag(TAG_GROUP_THREE))
            FlapAge(age = "2h", modifier = Modifier.testTag(TAG_FLAP_AGE))
            FlapTile(text = "N")
            FlapTile(text = "Z", small = true)
            RankTile(rank = 3, modifier = Modifier.testTag(TAG_RANK))
            LoadingBoard(label = "Loading today’s news…")
            SkeletonCard()
            SavedCard(
                outlet = "The Harbor Ledger",
                age = "3d",
                headline = "Harbor dredging plan clears its last review",
                imageUrl = null,
                topic = "Local",
                onOpen = {},
                onShare = {},
                onUnsave = {},
            )
            StoryRow(outlet = "Open Circuit", age = "1h", headline = "Recycling rules explained", onClick = {
            }, tag = TagKind.Paywall)
            Tag(kind = TagKind.Paywall)
            Tag(kind = TagKind.Metered)
            Tag(kind = TagKind.Following)
            Button(text = "Primary", onClick = {})
            Button(text = "Outline", onClick = {}, variant = ButtonVariant.Outline, icon = NewzIcons.iconRetry)
            Button(text = "Text", onClick = {}, variant = ButtonVariant.Text)
            Button(text = "Disabled", onClick = {}, variant = ButtonVariant.Disabled)
            Button(text = "Wide", onClick = {}, wide = true)
            IconButton(icon = NewzIcons.iconShare, contentDescription = "Share", onClick = {})
            Snackbar(message = "Article hidden", actionLabel = "Undo", onAction = {})
            Snackbar(message = "Reading history cleared", modifier = Modifier.testTag(TAG_PLAIN_SNACKBAR))
            SnackbarAction(label = "Retry", onClick = {})
            OfflineBanner(message = "Offline — showing saved articles", onRetry = {})
            StatePanel(
                title = "You’re offline",
                body = "New articles load as soon as you’re connected again.",
                icon = NewzIcons.iconWifiOff,
                actionLabel = "Retry",
                onAction = {},
            )
            BoardPanel(
                title = "You're all caught up",
                body = "That’s everything from the past 7 days.",
                tiles = "END",
                actionLabel = "Review in Settings",
                onAction = {},
            )
            NoteRow(icon = NewzIcons.iconShield, text = "Suggestions are worked out on this phone.")
            NoteRow(icon = NewzIcons.iconCheck, text = "Imported", subtitle = "24 sources")
            Panel(title = "Your feed", count = "24") {
                ListRow(
                    title = "Sources",
                    subtitle = "24 followed",
                    icon = NewzIcons.iconRss,
                    showDivider = false,
                    onClick = {},
                )
                ListRow(
                    title = "Paywall filter",
                    subtitle = "Hide outlets that need a subscription",
                    icon = NewzIcons.iconLock,
                    trailing = { Switch(checked = true, onCheckedChange = {}, contentDescription = "Paywall filter") },
                )
                ListRow(title = "Reading history", trailing = {
                    Switch(checked = false, onCheckedChange = {}, contentDescription = "Reading history")
                })
                ListRow(title = "Clear reading history", trailing = {
                    Button(text = "Clear", onClick = {}, variant = ButtonVariant.Outline)
                })
            }
            PanelHeader(title = "Reading")
            SegmentedControl(
                options = listOf("Small", "Default", "Large", "Extra large"),
                selectedIndex = 1,
                onSelect = {},
            )
            Checkbox(checked = true, onCheckedChange = {}, label = "Include reading history")
            Checkbox(checked = false, onCheckedChange = {}, label = "World section")
            TextField(
                value = "tariffs",
                onValueChange = {},
                label = "Keyword",
                helper = "Hides headlines with this word",
            )
            TextField(
                value = "a very long keyword that goes on and on past the sixty character limit",
                onValueChange = {},
                label = "Keyword",
                error = "Keep it to 60 characters",
            )
            SearchField(value = "harbor", onValueChange = {}, placeholder = "Search")
            AppIcon()
            BottomActionBar(
                status = "3 topics picked",
                subStatus = "Pick at least one",
                buttonLabel = "Start reading",
                onButton = {},
            )
            Box(Modifier.fillMaxWidth().height(SHEET_BOX_HEIGHT.dp)) {
                BottomSheet(onDismiss = {}, label = "Article actions") {
                    SheetActionRow(label = "Save for later", icon = NewzIcons.iconBookmark, onClick = {
                    }, modifier = Modifier.testTag(TAG_ENABLED_ROW))
                    SheetActionRow(
                        label = "Show more like this",
                        icon = NewzIcons.iconArrowUp,
                        onClick = {},
                        enabled = false,
                        explanation = "Already showing much more of Science",
                        modifier = Modifier.testTag(TAG_DISABLED_ROW),
                    )
                }
            }
        }
    }

    private companion object {
        const val GOOGLE_IMAGE = "https://lh3.googleusercontent.com/x.jpg"
        const val SHEET_BOX_HEIGHT = 360
        const val TAG_GROUP_ONE = "group-1"
        const val TAG_GROUP_THREE = "group-3"
        const val TAG_FLAP_AGE = "flap-age"
        const val TAG_RANK = "rank"
        const val TAG_DISABLED_ROW = "sheet-row-disabled"
        const val TAG_ENABLED_ROW = "sheet-row-enabled"
        const val TAG_PLAIN_SNACKBAR = "snackbar-plain"
        const val LONG_HEADLINE =
            "Deep-sea survey maps the currents carrying warm water toward Antarctica’s ice shelves, " +
                "and scientists warn the melt could speed up sea-level rise along every coastline " +
                "within the next few decades unless emissions fall sharply"
    }
}
