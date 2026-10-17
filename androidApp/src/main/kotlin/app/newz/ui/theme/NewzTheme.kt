package app.newz.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import app.newz.store.TextSize

/**
 * The Newz theme. Provides [LocalNewzPalette] (light or dark, following the system by default),
 * [LocalNewzTextScale] from the user's [textSize], and a Material colour scheme mapped from the
 * palette. The app draws edge to edge; status-bar icons are light in both themes (on navy in
 * light, on the navy `paper` in dark).
 */
@Composable
fun NewzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    textSize: TextSize,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) NewzColors.Dark else NewzColors.Light
    val scale = remember(textSize) { NewzTextScale.of(textSize) }
    val colorScheme = remember(palette) { palette.toColorScheme() }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalNewzPalette provides palette,
        LocalNewzTextScale provides scale,
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun NewzPalette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = button,
        onPrimary = onButton,
        primaryContainer = card,
        onPrimaryContainer = ink,
        secondary = strip,
        onSecondary = sky,
        background = paper,
        onBackground = ink,
        surface = card,
        onSurface = ink,
        surfaceVariant = field,
        onSurfaceVariant = inkMuted,
        surfaceContainer = card,
        surfaceContainerLow = card,
        surfaceContainerHigh = card,
        inverseSurface = snackbar,
        inverseOnSurface = onSnackbar,
        outline = line,
        outlineVariant = lineSubtle,
        error = alert,
        onError = onButton,
        scrim = scrim,
    )
}

/** The string a [SignageText] actually draws (its capitals), for tests; semantics text stays Copy case. */
val SignageDisplayText = SemanticsPropertyKey<String>("SignageDisplayText")

private var SemanticsPropertyReceiver.signageDisplayText by SignageDisplayText

/**
 * Text in a design type style. A signage style ([NewzType.caps]) is drawn in capitals, while
 * the semantics text stays the original Copy-case string, so TalkBack and Compose tests see
 * the copy as written (SPEC §Design reconciliation #3). Role and click semantics from
 * [modifier] are kept. The style is scaled by [LocalNewzTextScale].
 */
@Composable
fun SignageText(
    text: String,
    style: NewzType,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val shown = if (style.caps) text.uppercase() else text
    Text(
        text = shown,
        modifier =
            modifier.clearAndSetSemantics {
                this.text = AnnotatedString(text)
                signageDisplayText = shown
            },
        color = color,
        style = style.scaled(),
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}
