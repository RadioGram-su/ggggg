package su.radiogram.grom

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import su.radiogram.grom.audio.AudioEngine
import su.radiogram.grom.audio.OutputMonitor
import su.radiogram.grom.data.Library
import su.radiogram.grom.data.Store

class GromApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Library.init(this)
        OutputMonitor.init(this)
        AudioEngine.bind(scope)
        if (Library.hasPermission(this)) Library.start()
    }

    companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}
