package app.newz

import android.app.Application

/** Installs the [AppGraph]; the graph builds itself on first use. */
class NewzApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.install(this)
    }
}
