package com.karelisio.exit.system

import com.karelisio.exit.str
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
import androidx.annotation.StringRes
import com.karelisio.exit.R

/** Autorisations demandées par Exit, une page chacune dans l'onboarding et dans les réglages. */
enum class ExitPermission(@StringRes val titleRes: Int, @StringRes val rationaleRes: Int, @StringRes val detailRes: Int) {
    NOTIFICATIONS(R.string.perm_notifications_title, R.string.perm_notifications_why, R.string.perm_notifications_detail),
    EXACT_ALARMS(R.string.perm_exact_alarms_title, R.string.perm_exact_alarms_why, R.string.perm_exact_alarms_detail),
    BATTERY(R.string.perm_battery_title, R.string.perm_battery_why, R.string.perm_battery_detail),
    INSTALL(R.string.perm_install_title, R.string.perm_install_why, R.string.perm_install_detail),
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
        ComponentName("com.hihonor.systemmanager", str(R.string.system_com_hihonor_systemmanager_startupmgr_ui)),
        ComponentName("com.huawei.systemmanager", str(R.string.system_com_huawei_systemmanager_startupmgr_ui)),
        ComponentName("com.hihonor.systemmanager", str(R.string.system_com_hihonor_systemmanager_optimize_process)),
        ComponentName("com.huawei.systemmanager", str(R.string.system_com_huawei_systemmanager_optimize_process)),
    )
}
