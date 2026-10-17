package app.newz.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Opens the Android share sheet with "<headline> <url>" as plain text (SPEC S13): no Newz
 * branding and no tracking parameters.
 */
class ShareLauncher {
    fun share(
        context: Context,
        headline: String,
        url: String,
    ): Boolean {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "$headline $url")
        return try {
            context.startActivity(Intent.createChooser(send, null))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
