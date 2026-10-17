package app.newz.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

/** Where the preferences file lives on this platform (app-private storage). */
expect fun prefsPath(): String

/**
 * [UserPrefs] in DataStore Preferences, as one JSON-encoded key. Keep one instance per file per
 * process (DataStore's rule); [create] opens the platform file.
 */
class PreferencesStore(
    private val dataStore: DataStore<Preferences>,
) {
    val prefs: Flow<UserPrefs> = dataStore.data.map { decode(it[KEY]) }

    suspend fun update(transform: (UserPrefs) -> UserPrefs) {
        dataStore.edit { it[KEY] = encode(transform(decode(it[KEY]))) }
    }

    suspend fun replace(p: UserPrefs) {
        dataStore.edit { it[KEY] = encode(p) }
    }

    companion object {
        const val FILE_NAME = "newz.preferences_pb"
        private val KEY = stringPreferencesKey("user_prefs")
        private val json =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }

        fun create(path: String = prefsPath()): PreferencesStore =
            PreferencesStore(PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() }))

        internal fun encode(p: UserPrefs): String = json.encodeToString(UserPrefs.serializer(), p)

        /** Unreadable or missing preferences fall back to defaults rather than crash the app. */
        internal fun decode(raw: String?): UserPrefs =
            raw?.let { runCatching { json.decodeFromString(UserPrefs.serializer(), it) }.getOrNull() } ?: UserPrefs()
    }
}
