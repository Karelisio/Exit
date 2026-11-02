package com.karelisio.exit.notify

import com.karelisio.exit.plural
import com.karelisio.exit.domain.label
import com.karelisio.exit.domain.title
import com.karelisio.exit.domain.description
import com.karelisio.exit.domain.rationale
import com.karelisio.exit.domain.detail
import com.karelisio.exit.str
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
                NotificationChannel(CHANNEL_NEXT, str(R.string.settings_prochaine_autorisee), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = str(R.string.notify_previent_a_l_heure_exacte)
                },
                NotificationChannel(CHANNEL_MILESTONES, str(R.string.progress_jalons_sante), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = str(R.string.notify_celebre_chaque_benefice_sante_atteint)
                },
                NotificationChannel(CHANNEL_PLAN, str(R.string.notify_plan), NotificationManager.IMPORTANCE_LOW).apply {
                    description = str(R.string.notify_ajustements_en_douceur_du_plan)
                },
                NotificationChannel(CHANNEL_UPDATES, str(R.string.notify_mises_a_jour), NotificationManager.IMPORTANCE_LOW).apply {
                    description = str(R.string.notify_nouvelle_version_d_exit_disponible)
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

    fun showNextAllowed(intervalMinutes: Int, reason: String? = null) {
        if (!canPost()) return
        val smoke = PendingIntent.getBroadcast(
            context,
            REQ_ACTION_SMOKE,
            Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_SMOKE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_NEXT)
            .setSmallIcon(R.drawable.ic_stat_exit)
            .setContentTitle(str(R.string.home_c_est_ok_si_tu))
            .setContentText(str(R.string.notify_tenues_tu_peux_aussi_tenter, PlanEngine.formatDuration(intervalMinutes)))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    str(R.string.notify_tenues_bravo_tu_peux_en, PlanEngine.formatDuration(intervalMinutes)) +
                        str(R.string.notify_ou_essayer_de_tenir_encore) +
                        (reason?.let { str(R.string.notify_nta_raison, it) } ?: ""),
                ),
            )
            .setContentIntent(openApp())
            .addAction(R.drawable.ic_stat_exit, str(R.string.home_j_en_fume_une), smoke)
            .addAction(R.drawable.ic_stat_exit, str(R.string.components_sos_envie), openApp(MainActivity.DEST_SOS, REQ_SOS))
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
            .setContentTitle(str(R.string.home_sans_fumer, milestone.label, milestone.title))
            .setContentText(milestone.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(str(R.string.notify_nsource, milestone.description, milestone.source.label)))
            .setContentIntent(openApp(MainActivity.DEST_PROGRESS, REQ_PROGRESS))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_MILESTONE, notification)
    }

    fun showPlanAdjusted(message: String) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_PLAN)
            .setSmallIcon(R.drawable.ic_stat_exit)
            .setContentTitle(str(R.string.home_plan_reajuste_en_douceur))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp(MainActivity.DEST_PLAN, REQ_PLAN))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_PLAN, notification)
    }

    fun showRiskReminder(hour: Int, trigger: com.karelisio.exit.domain.Trigger?, plan: String?) {
        if (!canPost()) return
        val text = buildString {
            append(str(R.string.risk_text, hour))
            trigger?.let { append(' '); append(str(R.string.risk_trigger, it.emoji, str(it.labelRes))) }
            plan?.let { append('\n'); append(str(R.string.risk_plan, it)) }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PLAN)
            .setSmallIcon(R.drawable.ic_stat_exit)
            .setContentTitle(str(R.string.risk_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .addAction(R.drawable.ic_sos, str(R.string.shortcut_sos), openApp(MainActivity.DEST_SOS, REQ_SOS))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_RISK, notification)
    }

    fun showWeeklyReview(review: com.karelisio.exit.domain.WeeklyReview) {
        if (!canPost()) return
        val text = buildString {
            append(plural(R.plurals.notify_week_cigarettes, review.smokes, review.smokes))
            review.change?.let { if (it < 0) append(str(R.string.notify_change_percent, it)) }
            if (review.cravingsOvercome > 0) append(plural(R.plurals.notify_week_cravings, review.cravingsOvercome, review.cravingsOvercome))
            append(str(R.string.notify_viens_voir_ton_bilan))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_MILESTONES)
            .setSmallIcon(R.drawable.ic_stat_exit)
            .setContentTitle(str(R.string.home_ton_bilan_de_la_semaine))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(MainActivity.DEST_REVIEW, REQ_REVIEW))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_REVIEW, notification)
    }

    fun showUpdateAvailable(version: String) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_exit)
            .setContentTitle(str(R.string.notify_exit_est_disponible, version))
            .setContentText(str(R.string.notify_touche_pour_voir_les_nouveautes))
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
        private const val ID_REVIEW = 5
        private const val ID_RISK = 6

        private const val REQ_ACTION_SMOKE = 10
        private const val REQ_SOS = 11
        private const val REQ_PROGRESS = 12
        private const val REQ_PLAN = 13
        private const val REQ_REVIEW = 14
    }
}
