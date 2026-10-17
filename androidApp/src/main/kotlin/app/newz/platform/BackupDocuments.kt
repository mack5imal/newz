package app.newz.platform

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Reads and writes backup files at the URIs the Storage Access Framework picker returns (SPEC
 * S24). No storage permission is needed and nothing touches the network: the user chose the file.
 */
object BackupDocuments {
    /** A backup file larger than this is not a valid backup and is not read further. */
    const val MAX_BYTES: Long = 5L * 1024 * 1024

    fun write(
        resolver: ContentResolver,
        uri: Uri,
        text: String,
    ) {
        val out = resolver.openOutputStream(uri, "wt") ?: error("Cannot write $uri")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Best-effort removal of the document at [uri], e.g. the empty file the picker created before a
     * failed write. Never throws.
     */
    fun delete(
        resolver: ContentResolver,
        uri: Uri,
    ) {
        runCatching {
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).delete() }
                ContentResolver.SCHEME_CONTENT -> DocumentsContract.deleteDocument(resolver, uri)
                else -> Unit
            }
        }
    }

    /** The file's text, or null when it cannot be read or is over [MAX_BYTES]. */
    fun read(
        resolver: ContentResolver,
        uri: Uri,
    ): String? {
        val declared = query(resolver, uri, OpenableColumns.SIZE)?.toLongOrNull()
        if (declared != null && declared > MAX_BYTES) return null
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(CHUNK)
                var total = 0L
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) return@use null
                    buffer.write(chunk, 0, n)
                }
                buffer.toString(Charsets.UTF_8.name())
            }
        }.getOrNull()
    }

    /** The name the user gave the file, or null when the provider does not say. */
    fun displayName(
        resolver: ContentResolver,
        uri: Uri,
    ): String? =
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { File(it).name }
        } else {
            query(resolver, uri, OpenableColumns.DISPLAY_NAME)
        }

    /**
     * The folder the file was saved in, as the document provider's path shows it (the last part,
     * e.g. "Downloads"), or null when the provider's document id carries no path.
     */
    fun folderName(uri: Uri): String? {
        val path =
            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                uri.path
            } else {
                val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
                when {
                    id.startsWith(RAW_PREFIX) -> id.removePrefix(RAW_PREFIX)
                    ':' in id -> id.substringAfter(':')
                    else -> null
                }
            } ?: return null
        val parent = path.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "")
        val folder = parent.substringAfterLast('/')
        return when {
            folder.isEmpty() -> null
            folder == "Download" -> "Downloads"
            else -> folder
        }
    }

    private fun query(
        resolver: ContentResolver,
        uri: Uri,
        column: String,
    ): String? =
        runCatching {
            resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(column)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getString(index) else null
            }
        }.getOrNull()

    private const val CHUNK = 64 * 1024
    private const val RAW_PREFIX = "raw:"
}
