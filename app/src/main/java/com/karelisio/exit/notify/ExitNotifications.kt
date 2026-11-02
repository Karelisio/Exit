package com.karelisio.exit.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.karelisio.exit.MainActivity
import com.karelisio.exit.R
import com.karelisio.exit.domain.HealthMilestone
import com.karelisio.exit.domain.PlanEngine

class ExitNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_NEXT, "Prochaine autorisée", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Prévient à l'heure exacte où l'intervalle est écoulé"
                },
                NotificationChannel(CHANNEL_MILESTONES, "Jalons santé", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Célèbre chaque bénéfice santé atteint"
                },
                NotificationChannel(CHANNEL_PLAN, "Plan", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ajustements en douceur du plan de réduction"
                },
                NotificationChannel(CHANNEL_UPDATES, "Mises à jour", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Nouvelle version d'Exit disponible"
                },
            ),
        )
    }

    private fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            manager.areNotificationsEnabled()

    private fun openApp(destination: String? = null, requestCode: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (destination != null) intent.putExtra(MainActivity.EXTRA_DESTINATION, destination)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun showNextAllowed(intervalMinutes: Int) {
        if (!canPost()) return
        val smoke = PendingIntent.getBroadcast(
            context,
            REQ_ACTION_SMOKE,
            Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_SMOKE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_NEXT)
            .setSmallIcon(R.drawable.ic_stat_exit)
            .setContentTitle("C'est OK si tu en as besoin")
            .setContentText("${PlanEngine.formatDuration(intervalMinutes)} tenues. Tu peux aussi tenter de tenir encore un peu.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${PlanEngine.formatDuration(intervalMinutes)} tenues, bravo. Tu peux en fumer une si tu en as besoin… " +
                        "ou essayer de tenir encore un peu : chaque minute compte.",
                ),
            )
            .setContentIntent(openApp())
            .addAction(R.drawable.ic_stat_exit, "J'en fume une", smoke)
            .addAction(R.drawable.ic_stat_exit, "SOS envie", openApp(MainActivity.DEST_SOS, REQ_SOS))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        manager.notify(ID_NEXT, notification)
    }

    fun cancelNextAllowed() = manager.cancel(ID_NEXT)

    fun showMilestone(milestone: HealthMilestone) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_MILESTONES)
            .setSmallIcon(R.drawable.ic_stat_exit)
            .setContentTitle("${milestone.label} sans fumer · ${milestone.title}")
            .setContentText(milestone.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${milestone.description}\nSource : ${milestone.source.label}"))
            .setContentIntent(openApp(MainActivity.DEST_PROGRESS, REQ_PROGRESS))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_MILESTONE, notification)
    }

    fun showPlanAdjusted(message: String) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_PLAN)
            .setSmallIcon(R.drawable.ic_stat_exit)
            .setContentTitle("Plan réajusté en douceur")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp(MainActivity.DEST_PLAN, REQ_PLAN))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_PLAN, notification)
    }

    fun showUpdateAvailable(version: String) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_exit)
            .setContentTitle("Exit $version est disponible")
            .setContentText("Touche pour voir les nouveautés et installer.")
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager.notify(ID_UPDATE, notification)
    }

    companion object {
        const val CHANNEL_NEXT = "next_allowed"
        const val CHANNEL_MILESTONES = "milestones"
        const val CHANNEL_PLAN = "plan"
        const val CHANNEL_UPDATES = "updates"

        private const val ID_NEXT = 1
        private const val ID_MILESTONE = 2
        private const val ID_PLAN = 3
        private const val ID_UPDATE = 4

        private const val REQ_ACTION_SMOKE = 10
        private const val REQ_SOS = 11
        private const val REQ_PROGRESS = 12
        private const val REQ_PLAN = 13
    }
}
