package com.karelisio.exit.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Sélection dans un graphique : toucher choisit une colonne (re-toucher la désélectionne),
 * glisser horizontalement parcourt les colonnes avec un léger retour haptique.
 */
fun Modifier.chartScrub(count: Int, selected: Int?, onSelect: (Int?) -> Unit): Modifier = composed {
    val haptics = rememberHaptics()
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    fun index(x: Float, width: Int) = (x / width.coerceAtLeast(1) * count).toInt().coerceIn(0, (count - 1).coerceAtLeast(0))
    this
        .pointerInput(count) {
            detectTapGestures { o ->
                val i = index(o.x, size.width)
                haptics.tick()
                select(if (i == current) null else i)
            }
        }
        .pointerInput(count) {
            detectHorizontalDragGestures(onDragStart = { o -> select(index(o.x, size.width)) }) { change, _ ->
                val i = index(change.position.x, size.width)
                if (i != current) {
                    haptics.tick()
                    select(i)
                }
            }
        }
}
