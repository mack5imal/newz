package app.newz.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.newz.AppGraph
import app.newz.platform.BackupDocuments
import app.newz.store.BackupCodec
import app.newz.store.BackupCounts
import app.newz.store.BackupRestorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.toJavaInstant

/** The note shown after an export or import. */
sealed interface BackupNote {
    /** [folder] is null when the document provider does not say where the file went. */
    data class Exported(
        val fileName: String,
        val folder: String?,
    ) : BackupNote

    data class Imported(
        val counts: BackupCounts,
    ) : BackupNote

    data object ImportFailed : BackupNote

    /** The backup could not be written; the empty file the picker created was removed. */
    data object ExportFailed : BackupNote
}

/**
 * Backup & restore (design `backup`, SPEC S24): exports every choice to a file the user picks
 * and imports one with replace semantics. An import is parsed and validated in full before
 * [BackupRestorer] changes anything, so an invalid file leaves the phone as it was.
 */
class BackupViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val includeHistoryState = MutableStateFlow(false)
    val includeHistory: StateFlow<Boolean> = includeHistoryState.asStateFlow()

    private val noteState = MutableStateFlow<BackupNote?>(null)
    val note: StateFlow<BackupNote?> = noteState.asStateFlow()

    fun setIncludeHistory(on: Boolean) {
        includeHistoryState.value = on
    }

    /** `newz-backup-{yyyy-mm-dd}.json`, dated today in the phone's time zone. */
    fun suggestedFileName(): String {
        val day = graph.clock().toJavaInstant().atZone(ZoneId.systemDefault())
        return "newz-backup-${DateTimeFormatter.ISO_LOCAL_DATE.format(day)}.json"
    }

    /** Writes the backup to [uri], the document the system picker created. */
    fun export(
        resolver: ContentResolver,
        uri: Uri,
        suggestedName: String,
    ) {
        val withHistory = includeHistoryState.value
        viewModelScope.launch {
            val prefs = graph.preferencesStore.prefs.first()
            val written =
                withContext(Dispatchers.IO) {
                    val store = graph.articleStore
                    val text =
                        BackupCodec.export(
                            prefs = prefs,
                            hidden = store.listHidden(),
                            saved = store.listSaved(),
                            customFeeds = store.listCustomFeeds(),
                            history = if (withHistory) store.listHistory() else null,
                            includeHistory = withHistory,
                        )
                    runCatching { BackupDocuments.write(resolver, uri, text) }
                        .onFailure { BackupDocuments.delete(resolver, uri) }
                        .isSuccess
                }
            noteState.value =
                if (written) {
                    BackupNote.Exported(
                        fileName = BackupDocuments.displayName(resolver, uri) ?: suggestedName,
                        folder = BackupDocuments.folderName(uri),
                    )
                } else {
                    BackupNote.ExportFailed
                }
        }
    }

    /** Reads, validates and then applies the backup at [uri]; any failure changes nothing. */
    fun import(
        resolver: ContentResolver,
        uri: Uri,
    ) {
        viewModelScope.launch {
            val contents =
                withContext(Dispatchers.IO) {
                    BackupDocuments.read(resolver, uri)?.let { BackupCodec.import(it).getOrNull() }
                }
            if (contents == null) {
                noteState.value = BackupNote.ImportFailed
                return@launch
            }
            val restored =
                withContext(Dispatchers.IO) {
                    BackupRestorer(graph.articleStore, graph.preferencesStore).restore(contents)
                }
            graph.feedRepository.invalidate()
            noteState.value = restored.fold({ BackupNote.Imported(it) }, { BackupNote.ImportFailed })
        }
    }
}
