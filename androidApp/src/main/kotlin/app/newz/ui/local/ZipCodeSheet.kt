package app.newz.ui.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.newz.local.ZipError
import app.newz.local.ZipResolver
import app.newz.local.ZipResult
import app.newz.ui.components.BottomSheet
import app.newz.ui.components.Button
import app.newz.ui.components.ButtonVariant
import app.newz.ui.components.NoteRow
import app.newz.ui.components.PlainText
import app.newz.ui.components.TextField
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The zip sheet is not drawn in the design package (SPEC reconciliation #10): this is the
// builder's wording, built from the package's BottomSheet, TextField, Button and NoteRow.

/** The intro line. */
@Suppress("ktlint:standard:max-line-length")
private const val INTRO = "Local news comes from outlets near this zip code. It stays on this phone and is never sent anywhere."

private const val ZIP_LENGTH = 5

/**
 * The zip code Local uses and the metro it resolved to. [nearest] is true when the zip lies in no
 * covered metro and [metroName] is the closest one.
 */
data class LocalArea(
    val zip: String,
    val metroId: String,
    val metroName: String,
    val nearest: Boolean,
)

/** [result] as a [LocalArea] for [zip], or null when the zip did not resolve. */
fun localAreaOf(
    zip: String,
    result: ZipResult,
): LocalArea? =
    when (result) {
        is ZipResult.InMetro -> LocalArea(zip, result.metro.id, result.metro.name, nearest = false)
        is ZipResult.Nearest -> LocalArea(zip, result.metro.id, result.metro.name, nearest = true)
        is ZipResult.Invalid -> null
    }

private fun messageOf(error: ZipError): String =
    when (error) {
        ZipError.NOT_FIVE_DIGITS -> "Enter a 5-digit zip code"
        ZipError.NOT_FOUND -> "We couldn’t find that zip code"
    }

/** The `NoteRow` naming the metro Local news comes from ("…, the nearest area we cover" when [LocalArea.nearest]). */
@Composable
fun LocalAreaNote(
    area: LocalArea,
    modifier: Modifier = Modifier,
) {
    val text =
        if (area.nearest) {
            "Showing local news from ${area.metroName}, the nearest area we cover"
        } else {
            "Showing local news from ${area.metroName}"
        }
    NoteRow(icon = NewzIcons.iconCompass, text = text, modifier = modifier)
}

/**
 * The Local zip code sheet: title, intro, the "Zip code" `TextField` with a numeric keyboard, the
 * metro it resolves to (as soon as five digits are typed) and Cancel / Save. Save checks the zip
 * on the device with [resolver]: a zip that is not five digits, or not found, shows its error and
 * nothing is saved; otherwise [onSave] gets the [LocalArea] (callers store it with
 * `PrefsOps.setZip`). Cancel, a swipe down, a tap outside and Back call [onDismiss]. The zip is
 * never logged or sent anywhere. [initialZip] pre-fills the field (Settings › Topics).
 */
@Composable
fun ZipCodeSheet(
    resolver: ZipResolver,
    onSave: (LocalArea) -> Unit,
    onDismiss: () -> Unit,
    initialZip: String? = null,
) {
    val c = LocalNewzPalette.current
    var text by rememberSaveable { mutableStateOf(initialZip.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<ZipError?>(null) }
    var lookup by remember { mutableStateOf<ZipResult?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(text) {
        val zip = text.trim()
        lookup =
            if (zip.length == ZIP_LENGTH && zip.all { it in '0'..'9' }) {
                withContext(Dispatchers.Default) { resolver.resolve(zip) }
            } else {
                null
            }
    }
    val found = lookup?.let { localAreaOf(text.trim(), it) }
    val shownError = error ?: (lookup as? ZipResult.Invalid)?.reason

    fun save() {
        val zip = text.trim()
        scope.launch {
            val result = withContext(Dispatchers.Default) { resolver.resolve(zip) }
            val area = localAreaOf(zip, result)
            if (area == null) {
                error = (result as ZipResult.Invalid).reason
            } else {
                onSave(area)
            }
        }
    }

    BottomSheet(onDismiss = onDismiss, label = "Your zip code") {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(
                    start = NewzTokens.spaceXl,
                    end = NewzTokens.spaceXl,
                    bottom = NewzTokens.spaceXl + NewzTokens.spaceXs,
                ),
        ) {
            SignageText(text = "Your zip code", style = NewzTokens.typeSheetTitle, color = c.ink)
            PlainText(
                text = INTRO,
                style = NewzTokens.typeIntro,
                color = c.inkMuted,
                modifier = Modifier.padding(top = NewzTokens.spaceXs),
            )
            TextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                label = "Zip code",
                error = shownError?.let(::messageOf),
                maxLength = ZIP_LENGTH,
                counter = "",
                fieldModifier = Modifier.focusRequester(focus),
                keyboardOptions =
                    KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                modifier = Modifier.padding(top = NewzTokens.spaceL + NewzTokens.spaceXs),
            )
            if (found != null && shownError == null) {
                LocalAreaNote(found, Modifier.padding(top = NewzTokens.spaceXs))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = NewzTokens.spaceL),
                horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceS, Alignment.End),
            ) {
                Button(text = "Cancel", onClick = onDismiss, variant = ButtonVariant.Text)
                Button(text = "Save", onClick = ::save, variant = ButtonVariant.Primary)
            }
        }
    }
}
