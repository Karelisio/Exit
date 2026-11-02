package com.karelisio.exit.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.exit.appContainer
import java.util.concurrent.TimeUnit

/**
 * Filet de sécurité (MagicOS peut supprimer les alarmes d'une appli tuée) : toutes les 6 h,
 * vérifie le plan, replanifie les alarmes et rafraîchit le widget.
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        container.repository.runDriftCheck()?.let { container.notifications.showPlanAdjusted(it.message) }
        container.refreshSchedules()
        container.autoBackup.runIfDue()
        container.updates.checkInBackground()?.let { container.notifications.showUpdateAvailable(it.version) }
        return Result.success()
    }

    companion object {
        private const val NAME = "exit-daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
