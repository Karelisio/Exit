package com.karelisio.exit

import android.content.Context
import com.karelisio.exit.data.ExitDatabase
import com.karelisio.exit.data.ExitRepository
import com.karelisio.exit.notify.AlarmScheduler
import com.karelisio.exit.notify.ExitNotifications
import com.karelisio.exit.update.UpdateManager
import com.karelisio.exit.widget.WidgetUpdater
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/** Dépendances de l'appli (injection manuelle, aucune bibliothèque de DI). */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        explicitNulls = false
    }

    val database: ExitDatabase = ExitDatabase.create(app)
    val repository = ExitRepository(database, json)
    val notifications = ExitNotifications(app)
    val alarms = AlarmScheduler(app)
    val autoBackup = com.karelisio.exit.data.AutoBackup(app, repository)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val updates = UpdateManager(
        context = app,
        http = http,
        json = json,
        repo = BuildConfig.UPDATE_REPO,
        currentVersion = BuildConfig.VERSION_NAME,
        scope = scope,
    )

    init {
        repository.onChanged = { refreshSchedules() }
    }

    /** Replanifie les alarmes (prochaine autorisée, jalons, minuit) et rafraîchit le widget. */
    suspend fun refreshSchedules() {
        alarms.reschedule(repository)
        WidgetUpdater.update(app)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as ExitApp).container
