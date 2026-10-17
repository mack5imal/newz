package app.newz.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import app.newz.ui.theme.NewzColors

/**
 * Opens an article in the user's default browser (SPEC S20): a Custom Tab when that browser
 * provides one, otherwise a plain VIEW intent. No package is ever set, no session is bound and
 * nothing is warmed up or prefetched, so Android resolves the default browser and Newz never
 * names one. The URL is passed exactly as the feed gave it.
 *
 * [customTabsProvider] answers whether the default browser offers Custom Tabs (the production
 * graph asks `CustomTabsClient.getPackageName` with an empty package list); tests inject it.
 */
class ArticleOpener(
    private val customTabsProvider: () -> String?,
) {
    /** Returns false when no app on the device can open [url]. */
    fun open(
        context: Context,
        url: String,
    ): Boolean {
        val uri = url.toUri()
        return try {
            if (customTabsProvider() != null) {
                customTabsIntent().launchUrl(context, uri)
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
            }
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun customTabsIntent(): CustomTabsIntent {
        val light = CustomTabColorSchemeParams.Builder().setToolbarColor(NewzColors.Light.navy.toArgb()).build()
        val dark = CustomTabColorSchemeParams.Builder().setToolbarColor(NewzColors.Dark.paper.toArgb()).build()
        return CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .setDefaultColorSchemeParams(light)
            .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, dark)
            .build()
    }
}
