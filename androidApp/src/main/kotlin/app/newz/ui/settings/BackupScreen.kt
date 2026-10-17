package app.newz.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.newz.ui.components.Button
import app.newz.ui.components.ButtonVariant
import app.newz.ui.components.Checkbox
import app.newz.ui.components.NoteRow
import app.newz.ui.components.Panel
import app.newz.ui.components.PlainText
import app.newz.ui.nav.Screen
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens

/** The MIME types the import picker offers: JSON first, then any file (some providers mislabel .json). */
private val IMPORT_TYPES = arrayOf("application/json", "*/*")

@Suppress("ktlint:standard:max-line-length")
private const val BACKUP_INTRO =
    "Save all your choices — sources, topics, muted words, hidden and saved articles — to a file you keep. Nothing goes to a cloud."

// design: backup

/**
 * Backup & restore (design `backup`): Export (a "Include reading history" checkbox, off by
 * default, and "Export to a file" through the system create-document picker) and Import ("Choose
 * a backup file" through the open-document picker). A note under each button reports the
 * result. No cloud and no network: the file goes wherever the user saves it.
 */
@Composable
fun BackupScreen(
    settings: SettingsViewModel,
    viewModel: BackupViewModel,
    onBack: () -> Unit,
) {
    val includeHistory by viewModel.includeHistory.collectAsState()
    val note by viewModel.note.collectAsState()
    val resolver = LocalContext.current.applicationContext.contentResolver
    var exportName by rememberSaveable { mutableStateOf("") }
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) viewModel.export(resolver, uri, exportName)
        }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.import(resolver, uri)
        }
    val c = LocalNewzPalette.current
    val buttonPadding =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = NewzTokens.spaceL, vertical = NewzTokens.spaceL)
    SettingsFrame(Screen.Backup, "Backup & restore", settings, onBack) {
        Intro(BACKUP_INTRO)
        Panel(title = "Export") {
            Checkbox(
                checked = includeHistory,
                onCheckedChange = viewModel::setIncludeHistory,
                label = "Include reading history",
                subtitle = "Left out unless you tick it",
                modifier = Modifier.padding(horizontal = NewzTokens.spaceL, vertical = NewzTokens.spaceS),
            )
            Divider()
            Button(
                text = "Export to a file",
                onClick = {
                    exportName = viewModel.suggestedFileName()
                    exporter.launch(exportName)
                },
                icon = NewzIcons.iconDownload,
                wide = true,
                modifier = buttonPadding,
            )
            (note as? BackupNote.Exported)?.let { exported ->
                val name = exported.fileName
                val folder = exported.folder ?: "the folder you chose"
                NoteRow(
                    icon = NewzIcons.iconCheck,
                    text = "Backup saved",
                    subtitle = "$name is in $folder. Copy it to your new phone and import it there.",
                )
            }
        }
        Panel(title = "Import") {
            PlainText(
                text = "Restore choices from a Newz backup file. They replace the choices on this phone.",
                style = NewzTokens.typeRowSub,
                color = c.ink,
                modifier =
                    Modifier.padding(
                        start = NewzTokens.spaceL,
                        end = NewzTokens.spaceL,
                        top = NewzTokens.spaceL,
                    ),
            )
            Button(
                text = "Choose a backup file",
                onClick = { importer.launch(IMPORT_TYPES) },
                variant = ButtonVariant.Outline,
                icon = NewzIcons.iconUpload,
                wide = true,
                modifier = buttonPadding,
            )
            when (val n = note) {
                is BackupNote.Imported -> {
                    val counts = n.counts
                    NoteRow(
                        icon = NewzIcons.iconCheck,
                        text = "Backup imported",
                        subtitle =
                            "Restored ${counts.sources} sources, ${counts.topics} topics, " +
                                "${counts.mutedKeywords} muted keywords, ${counts.hidden} hidden and " +
                                "${counts.saved} saved articles.",
                    )
                }

                BackupNote.ImportFailed -> {
                    NoteRow(
                        icon = NewzIcons.iconWarning,
                        text = "That file isn’t a valid Newz backup",
                        subtitle = "Nothing was changed. Choose a file exported from Newz — its name ends in .json.",
                        color = c.alert,
                    )
                }

                else -> {}
            }
        }
    }
}
