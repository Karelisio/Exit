package com.karelisio.exit.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.karelisio.exit.data.ExitRepository
import com.karelisio.exit.domain.HealthMilestones
import com.karelisio.exit.domain.MINUTE_MS
import com.karelisio.exit.domain.Phase
import com.karelisio.exit.domain.PlanEngine
import java.time.Instant
import java.time.ZoneId

/**
 * Alarmes : « prochaine autorisée » à l'heure pile (alarme exacte si autorisée), prochain jalon santé,
 * et minuit (compteur du jour du widget, vérification du plan).
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    private fun pending(action: String, request: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        request,
        Intent(context, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    suspend fun reschedule(repository: ExitRepository) {
        val config = repository.config()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        val nextIntent = pending(AlarmReceiver.ACTION_NEXT_ALLOWED, REQ_NEXT)
        alarmManager.cancel(nextIntent)
        val snapshot = repository.snapshot(now)
        val next = snapshot.nextAllowedAt
        if (config.onboardingDone && snapshot.phase == Phase.SPACING && next != null && next > now) {
            setExact(next, nextIntent)
        }

        val milestoneIntent = pending(AlarmReceiver.ACTION_MILESTONE, REQ_MILESTONE)
        alarmManager.cancel(milestoneIntent)
        if (config.onboardingDone && snapshot.phase == Phase.QUIT) {
            val start = listOfNotNull(snapshot.lastSmokeAt, config.quitStartedAt).maxOrNull()
            if (start != null) {
                val minutes = (now - start) / MINUTE_MS
                HealthMilestones.next(minutes)?.let { milestone ->
                    val at = start + milestone.minutes * MINUTE_MS
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, milestoneIntent)
                }
            }
        }

        // Rafraîchit l'anneau de progression du widget tous les huitièmes de l'intervalle.
        val tickIntent = pending(AlarmReceiver.ACTION_WIDGET_TICK, REQ_TICK)
        alarmManager.cancel(tickIntent)
        val last = snapshot.lastSmokeAt
        if (snapshot.phase == Phase.SPACING && next != null && last != null && next > now) {
            val step = ((next - last) / 8).coerceAtLeast(5 * MINUTE_MS)
            val tick = generateSequence(last + step) { it + step }.first { it > now + MINUTE_MS }
            if (tick < next) alarmManager.set(AlarmManager.RTC, tick, tickIntent)
        }

        // Bilan de la semaine : dimanche à 18 h.
        if (config.onboardingDone) {
            var sunday = PlanEngine.day(now, zone).with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
            var at = sunday.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
            if (at <= now) {
                sunday = sunday.plusWeeks(1)
                at = sunday.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
            }
            alarmManager.set(AlarmManager.RTC_WAKEUP, at, pending(AlarmReceiver.ACTION_WEEKLY, REQ_WEEKLY))
        }

        val midnight = PlanEngine.startOfDay(PlanEngine.day(now, zone).plusDays(1), zone) + MINUTE_MS
        alarmManager.set(AlarmManager.RTC, midnight, pending(AlarmReceiver.ACTION_MIDNIGHT, REQ_MIDNIGHT))
    }

    private fun setExact(at: Long, intent: PendingIntent) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            // Sans l'autorisation : fenêtre de 2 minutes, au plus près de l'heure.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, at, 2 * MINUTE_MS, intent)
        }
    }

    companion object {
        private const val REQ_NEXT = 100
        private const val REQ_MILESTONE = 101
        private const val REQ_MIDNIGHT = 102
        private const val REQ_TICK = 103
        private const val REQ_WEEKLY = 104

        /** Heure locale (minutes depuis minuit) d'un instant. */
        fun minuteOfDay(at: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
            val t = Instant.ofEpochMilli(at).atZone(zone)
            return t.hour * 60 + t.minute
        }
    }
}
