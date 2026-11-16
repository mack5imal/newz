package app.newz.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.newz.AppGraph
import app.newz.platform.BackupDocuments
import app.newz.store.BackupCodec
import app.newz.store.BackupContents
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

    private val pendingState = MutableStateFlow<PendingImport?>(null)

    /** A checked backup waiting for the user to confirm the restore; null when none is. */
    val pendingImport: StateFlow<PendingImport?> = pendingState.asStateFlow()

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

    /**
     * Reads and validates the backup at [uri], then holds it in [pendingImport] for the user to
     * confirm (B29): a restore replaces saved articles and possibly history, which cannot be got
     * back. An invalid file changes nothing and says so.
     */
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
            val onPhone =
                withContext(Dispatchers.IO) {
                    graph.articleStore.listSaved().size to graph.articleStore.listHistory().size
                }
            pendingState.value = PendingImport(contents, savedOnPhone = onPhone.first, historyOnPhone = onPhone.second)
        }
    }

    /** The user declined the restore: nothing changes. */
    fun cancelImport() {
        pendingState.value = null
    }

    /** Applies the backup the user confirmed. */
    fun confirmImport() {
        val pending = pendingState.value ?: return
        pendingState.value = null
        viewModelScope.launch {
            val restored =
                withContext(Dispatchers.IO) {
                    BackupRestorer(graph.articleStore, graph.preferencesStore).restore(pending.contents)
                }
            graph.feedRepository.invalidate()
            // The backup usually follows sources the cache has never fetched (a new phone), so
            // load them now rather than waiting for a pull (B30); re-rank, as after a pull.
            if (restored.isSuccess) graph.feedRepository.refresh(rerank = true)
            noteState.value = restored.fold({ BackupNote.Imported(it) }, { BackupNote.ImportFailed })
        }
    }
}

/** A valid backup waiting for the user's go-ahead, with what it would replace on this phone. */
data class PendingImport(
    val contents: BackupContents,
    val savedOnPhone: Int,
    val historyOnPhone: Int,
)

/** The confirm sheet's message: what the restore replaces, in numbers (B29). */
fun importConfirmMessage(pending: PendingImport): String {
    val counts = pending.contents.counts()
    val saved =
        "your ${articles(pending.savedOnPhone, "saved article")} with its ${counts.saved}"
    val history =
        if (pending.contents.history == null) {
            "Your reading history on this phone stays as it is."
        } else {
            "Your reading history (${articles(
                pending.historyOnPhone,
                "opened article",
            )}) is replaced with the backup's too."
        }
    return "This replaces your sources, topics and settings with the backup’s, and $saved. $history It can’t be undone."
}

private fun articles(
    n: Int,
    noun: String,
): String = if (n == 1) "1 $noun" else "$n ${noun}s"
