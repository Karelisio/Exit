package com.karelisio.exit.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WidgetUpdater {
    private val _tick = MutableStateFlow(System.currentTimeMillis())

    /** Change à chaque événement qui modifie l'affichage (alarme, minuit, nouvelle cigarette…). */
    val tick: StateFlow<Long> = _tick.asStateFlow()

    suspend fun update(context: Context) {
        _tick.value = System.currentTimeMillis()
        runCatching { ExitWidget().updateAll(context) }
    }
}
