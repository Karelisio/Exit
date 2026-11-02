package com.karelisio.exit.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karelisio.exit.appContainer
import com.karelisio.exit.domain.HealthMilestones
import com.karelisio.exit.domain.MINUTE_MS
import com.karelisio.exit.domain.Phase
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val pending = goAsync()
        container.scope.launch {
            try {
                val repo = container.repository
                val config = repo.config()
                when (intent.action) {
                    ACTION_NEXT_ALLOWED -> {
                        val snapshot = repo.snapshot()
                        val minute = AlarmScheduler.minuteOfDay(System.currentTimeMillis())
                        if (config.notifyNextAllowed && snapshot.phase == Phase.SPACING && snapshot.isOk(System.currentTimeMillis()) &&
                            isAwake(minute, config.wakeMinutes, config.sleepMinutes)
                        ) {
                            container.notifications.showNextAllowed(snapshot.intervalMinutes)
                        }
                    }
                    ACTION_MILESTONE -> {
                        val snapshot = repo.snapshot()
                        val start = listOfNotNull(snapshot.lastSmokeAt, config.quitStartedAt).maxOrNull()
                        if (config.notifyMilestones && snapshot.phase == Phase.QUIT && start != null) {
                            val minutes = (System.currentTimeMillis() - start) / MINUTE_MS + 1
                            HealthMilestones.reached(minutes).lastOrNull()?.let(container.notifications::showMilestone)
                        }
                    }
                    ACTION_MIDNIGHT -> {
                        repo.runDriftCheck()?.let { container.notifications.showPlanAdjusted(it.message) }
                    }
                }
                container.refreshSchedules()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_NEXT_ALLOWED = "com.karelisio.exit.NEXT_ALLOWED"
        const val ACTION_MILESTONE = "com.karelisio.exit.MILESTONE"
        const val ACTION_MIDNIGHT = "com.karelisio.exit.MIDNIGHT"

        /** Pas de notification la nuit : le minuteur affiche « OK », c'est suffisant. */
        fun isAwake(minute: Int, wake: Int, sleep: Int): Boolean =
            if (wake <= sleep) minute in wake until sleep else minute >= wake || minute < sleep
    }
}
