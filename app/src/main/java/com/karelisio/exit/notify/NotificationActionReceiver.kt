package com.karelisio.exit.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karelisio.exit.appContainer
import kotlinx.coroutines.launch

/** Bouton « J'en fume une » de la notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMOKE) return
        val container = context.appContainer
        val pending = goAsync()
        container.scope.launch {
            try {
                container.notifications.cancelNextAllowed()
                container.repository.logSmoke()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SMOKE = "com.karelisio.exit.ACTION_SMOKE"
    }
}
