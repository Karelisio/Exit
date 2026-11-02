package com.karelisio.exit.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.karelisio.exit.appContainer

/** Bouton +1 du widget : note une cigarette à l'instant (sans contexte, on pourra l'ajouter dans l'historique). */
class LogSmokeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = context.appContainer
        container.notifications.cancelNextAllowed()
        container.repository.logSmoke()
    }
}
