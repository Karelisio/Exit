package com.karelisio.exit.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karelisio.exit.appContainer
import kotlinx.coroutines.launch

/** Redémarrage, mise à jour de l'appli, changement d'heure ou d'autorisation d'alarme : on replanifie. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val pending = goAsync()
        container.scope.launch {
            try {
                container.refreshSchedules()
            } finally {
                pending.finish()
            }
        }
    }
}
