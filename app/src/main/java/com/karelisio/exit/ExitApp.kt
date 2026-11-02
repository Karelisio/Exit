package com.karelisio.exit

import android.app.Application
import com.karelisio.exit.notify.DailyWorker
import kotlinx.coroutines.launch

class ExitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.createChannels()
        runCatching { DailyWorker.schedule(this) }
        container.scope.launch {
            container.repository.runDriftCheck()
            container.refreshSchedules()
        }
    }
}
