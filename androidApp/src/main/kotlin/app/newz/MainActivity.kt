package app.newz

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.newz.store.TextSize
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzTheme
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Status-bar icons are light in both themes: on the navy app bar (light) and navy paper (dark).
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent { NewzApp() }
    }
}

@Composable
fun NewzApp(textSize: TextSize = TextSize.DEFAULT) {
    NewzTheme(textSize = textSize) {
        val palette = LocalNewzPalette.current
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(palette.paper)
                    .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            SignageText("Newz", NewzTokens.typeWordmark, color = palette.ink)
        }
    }
}
