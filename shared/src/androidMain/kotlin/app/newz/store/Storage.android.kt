package app.newz.store

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.newz.db.NewzDatabase
import java.io.File

/** Hands the shared store the application context. Call [init] from Application/Activity onCreate. */
object NewzStorage {
    const val DATABASE_NAME = "newz.db"

    private var filesDir: File? = null
    private var driverFactory: (() -> SqlDriver)? = null

    fun init(context: Context) {
        val app = context.applicationContext
        filesDir = app.filesDir
        driverFactory = { AndroidSqliteDriver(NewzDatabase.Schema, app, DATABASE_NAME) }
    }

    internal fun driver(): SqlDriver = checkNotNull(driverFactory) { "Call NewzStorage.init(context) first" }()

    internal fun files(): File = checkNotNull(filesDir) { "Call NewzStorage.init(context) first" }
}

actual fun createSqlDriver(): SqlDriver = NewzStorage.driver()

actual fun prefsPath(): String = File(NewzStorage.files(), PreferencesStore.FILE_NAME).path
