package app.newz.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.newz.db.NewzDatabase
import java.io.File
import java.util.Properties

/** The JVM target only runs tools and tests, so its database is a fresh in-memory one. */
actual fun createSqlDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), NewzDatabase.Schema)

actual fun prefsPath(): String = File(System.getProperty("user.home"), ".newz/${PreferencesStore.FILE_NAME}").path
