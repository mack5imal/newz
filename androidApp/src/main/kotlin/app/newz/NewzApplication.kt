package app.newz

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode

/** Installs the [AppGraph]; the graph builds itself on first use. */
class NewzApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Debug builds log any disk or network work on the main thread (the UI tests grep for it).
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy
                    .Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
        }
        AppGraph.install(this)
    }
}
