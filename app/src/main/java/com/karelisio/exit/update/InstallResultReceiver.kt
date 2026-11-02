package com.karelisio.exit.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.karelisio.exit.appContainer

/** Statut des installations lancées par [UpdateManager] (non exporté). */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        context.appContainer.updates.onInstallStatus(status, confirmation, message)
    }

    companion object {
        fun statusReceiver(context: Context, sessionId: Int): IntentSender {
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            return PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ).intentSender
        }
    }
}
