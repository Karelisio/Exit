package com.karelisio.exit.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** Autorisations demandées par Exit, une page chacune dans l'onboarding et dans les réglages. */
enum class ExitPermission(val title: String, val rationale: String, val detail: String) {
    NOTIFICATIONS(
        "Notifications",
        "Pour te prévenir quand la prochaine cigarette est « autorisée » et fêter chaque jalon santé.",
        "Aucune publicité, aucun rappel culpabilisant : seulement l'essentiel, au bon moment.",
    ),
    EXACT_ALARMS(
        "Alarmes exactes",
        "Pour que la notification « c'est OK » arrive à la minute près, pas un quart d'heure plus tard.",
        "Android limite les alarmes précises : Exit en programme au plus une à la fois.",
    ),
    BATTERY(
        "Batterie sans restriction",
        "Ton téléphone peut mettre Exit en veille forcée et faire sauter les rappels.",
        "Sur Honor (MagicOS), autorise aussi Exit dans « Lancement d'applis » → Gérer manuellement.",
    ),
    INSTALL(
        "Mises à jour",
        "Pour installer les nouvelles versions d'Exit directement depuis l'appli.",
        "Exit n'est pas sur le Play Store : les mises à jour viennent des releases GitHub, signées avec la même clé.",
    ),
}

object Permissions {

    val isHonorOrHuawei: Boolean
        get() = Build.MANUFACTURER.lowercase() in setOf("honor", "huawei") ||
            Build.BRAND.lowercase() in setOf("honor", "huawei")

    fun isGranted(context: Context, permission: ExitPermission): Boolean = when (permission) {
        ExitPermission.NOTIFICATIONS ->
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
        ExitPermission.EXACT_ALARMS -> context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ExitPermission.BATTERY ->
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        ExitPermission.INSTALL -> context.packageManager.canRequestPackageInstalls()
    }

    fun openSettings(context: Context, permission: ExitPermission): Boolean = when (permission) {
        ExitPermission.NOTIFICATIONS -> context.startFirst(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            appDetails(context),
        )
        ExitPermission.EXACT_ALARMS -> context.startFirst(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
            appDetails(context),
        )
        ExitPermission.BATTERY -> requestIgnoreBattery(context)
        ExitPermission.INSTALL -> context.startFirst(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            appDetails(context),
        )
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBattery(context: Context) = context.startFirst(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetails(context),
    )

    /** Gestionnaire de lancement d'Honor / Huawei (« Gérer manuellement »). */
    fun openLaunchManager(context: Context) = context.startFirst(
        *LAUNCH_MANAGERS.map { Intent().setComponent(it) }.toTypedArray(),
        appDetails(context),
    )

    fun openAppDetails(context: Context) = context.startFirst(appDetails(context))

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    private fun Context.startFirst(vararg intents: Intent): Boolean {
        for (intent in intents) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    private val LAUNCH_MANAGERS = listOf(
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    )
}
