package app.newz.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import app.newz.store.MAX_KEYWORD_LENGTH
import app.newz.store.normalizeKeyword
import app.newz.ui.components.BottomSheet
import app.newz.ui.components.Button
import app.newz.ui.components.ButtonVariant
import app.newz.ui.components.PlainText
import app.newz.ui.components.TextField
import app.newz.ui.components.TopicChip
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText

/** The intro line, verbatim from the Copy. */
@Suppress("ktlint:standard:max-line-length")
private const val INTRO =
    "Articles that mention it are hidden everywhere in your feed. You can undo this in Settings › Topics & keywords."

/** At most this many headline suggestions. */
private const val MAX_SUGGESTIONS = 5

/** A run of more capitalised words than this is offered word by word, not as one name. */
private const val MAX_RUN_WORDS = 3

/** Words never offered as a suggestion, even capitalised (and they end a name). */
private val STOPWORDS =
    setOf(
        "a",
        "an",
        "the",
        "and",
        "or",
        "but",
        "nor",
        "so",
        "yet",
        "of",
        "in",
        "on",
        "at",
        "to",
        "for",
        "from",
        "by",
        "with",
        "as",
        "into",
        "onto",
        "over",
        "under",
        "after",
        "before",
        "about",
        "up",
        "down",
        "out",
        "off",
        "is",
        "are",
        "was",
        "were",
        "be",
        "been",
        "has",
        "have",
        "had",
        "do",
        "does",
        "did",
        "it",
        "its",
        "this",
        "that",
        "these",
        "those",
        "he",
        "she",
        "they",
        "we",
        "you",
        "i",
        "his",
        "her",
        "their",
        "our",
        "your",
        "my",
        "not",
        "no",
        "new",
        "says",
        "say",
        "said",
        "will",
        "would",
        "can",
        "could",
        "may",
        "might",
        "how",
        "why",
        "what",
        "when",
        "where",
        "who",
        "which",
        "than",
        "then",
        "there",
        "here",
        "more",
        "most",
        "all",
        "just",
        "now",
        "if",
    )

/** Punctuation at the end of a word that ends a name ("Paris, Texas" is two suggestions). */
private const val RUN_BREAKS = ",.;:!?)]}\"”"

/**
 * The suggestions under "From this headline": capitalised words and names (runs of up to
 * [MAX_RUN_WORDS] capitalised words) from [headline], stopwords dropped, possessive "’s" removed,
 * in headline order, without repeats (ignoring case), at most [max].
 */
fun keywordSuggestions(
    headline: String,
    max: Int = MAX_SUGGESTIONS,
): List<String> {
    val runs = mutableListOf<List<String>>()
    var run = mutableListOf<String>()

    fun endRun() {
        if (run.isNotEmpty()) runs += run
        run = mutableListOf()
    }
    for (token in headline.split(Regex("\\s+"))) {
        val word =
            token
                .trim { !it.isLetterOrDigit() }
                .removeSuffix("’s")
                .removeSuffix("'s")
        val capitalised = word.firstOrNull()?.isUpperCase() == true
        if (capitalised && word.lowercase() !in STOPWORDS && word.length > 1) run += word else endRun()
        if (token.lastOrNull()?.let { it in RUN_BREAKS } == true) endRun()
    }
    endRun()
    return runs
        .flatMap { words -> if (words.size <= MAX_RUN_WORDS) listOf(words.joinToString(" ")) else words }
        .filter { it.length <= MAX_KEYWORD_LENGTH }
        .distinctBy { it.lowercase() }
        .take(max)
}

// design: keyword-mute

/**
 * Mute a keyword (design `keyword-mute`): the title, the intro, up to five `TopicChip`
 * suggestions from [headline] (tapping one fills the field and selects it; typing deselects
 * it), the `TextField` "Keyword or name" with its helper and "{n}/60" counter, and Cancel /
 * Mute. Input over 60 characters shows the error and is not cut off; Mute is disabled while the
 * field is blank or too long. The field takes focus and the keyboard opens. Mute calls [onMute]
 * with the trimmed keyword after [onDismiss].
 */
@Composable
fun KeywordMuteSheet(
    headline: String,
    onMute: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalNewzPalette.current
    val suggestions = remember(headline) { keywordSuggestions(headline) }
    var text by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val tooLong = text.length > MAX_KEYWORD_LENGTH
    val keyword = normalizeKeyword(text)?.takeUnless { tooLong }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    BottomSheet(onDismiss = onDismiss, label = "Mute a keyword") {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(
                    start = NewzTokens.spaceXl,
                    end = NewzTokens.spaceXl,
                    bottom =
                        NewzTokens.spaceXl + NewzTokens.spaceXs,
                ),
        ) {
            SignageText(text = "Mute a keyword", style = NewzTokens.typeSheetTitle, color = c.ink)
            PlainText(
                text = INTRO,
                style = NewzTokens.typeIntro,
                color = c.inkMuted,
                modifier = Modifier.padding(top = NewzTokens.spaceXs),
            )
            if (suggestions.isNotEmpty()) {
                SignageText(
                    text = "From this headline",
                    style = NewzTokens.typeLabel,
                    color = c.inkMuted,
                    modifier = Modifier.padding(top = NewzTokens.spaceL, bottom = NewzTokens.spaceS),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceS),
                    verticalArrangement = Arrangement.spacedBy(NewzTokens.spaceS),
                ) {
                    suggestions.forEach { suggestion ->
                        TopicChip(
                            text = suggestion,
                            selected = suggestion == selected,
                            onClick = {
                                text = suggestion
                                selected = suggestion
                            },
                        )
                    }
                }
            }
            TextField(
                value = text,
                onValueChange = {
                    text = it
                    selected = null
                },
                label = "Keyword or name",
                placeholder = "Type a word or name",
                helper = "Matches the whole word, in any capitalization",
                error = if (tooLong) "Keep it to 60 characters or fewer" else null,
                maxLength = MAX_KEYWORD_LENGTH,
                counter = "${text.length}/60",
                fieldModifier = Modifier.focusRequester(focus),
                modifier = Modifier.padding(top = NewzTokens.spaceL + NewzTokens.spaceXs),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = NewzTokens.spaceL),
                horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceS, Alignment.End),
            ) {
                Button(text = "Cancel", onClick = onDismiss, variant = ButtonVariant.Text)
                Button(
                    text = "Mute",
                    onClick = {
                        val chosen = keyword ?: return@Button
                        onDismiss()
                        onMute(chosen)
                    },
                    variant = ButtonVariant.Primary,
                    enabled = keyword != null,
                )
            }
        }
    }
}
