package app.newz.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.newz.db.NewzDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun createSqlDriver(): SqlDriver = NativeSqliteDriver(NewzDatabase.Schema, "newz.db")

/** Application Support: app-private, not shown in the Files app. */
@OptIn(ExperimentalForeignApi::class)
actual fun prefsPath(): String {
    val dir =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return requireNotNull(dir?.path) { "No Application Support directory" } + "/" + PreferencesStore.FILE_NAME
}
