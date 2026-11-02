package com.karelisio.exit.notify

import com.karelisio.exit.str
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.karelisio.exit.MainActivity
import com.karelisio.exit.R
import com.karelisio.exit.appContainer
import com.karelisio.exit.domain.DAY_MS
import com.karelisio.exit.domain.Phase
import com.karelisio.exit.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ouvre l'appli depuis une tuile (API 34+ : PendingIntent obligatoire). */
@SuppressLint("StartActivityAndCollapseDeprecated")
private fun TileService.open(destination: String?) {
    val intent = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (destination != null) intent.putExtra(MainActivity.EXTRA_DESTINATION, destination)
    if (Build.VERSION.SDK_INT >= 34) {
        startActivityAndCollapse(PendingIntent.getActivity(this, destination.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }
}

/** Tuile « J'en fume une » : +1 en un geste depuis les réglages rapides (même écran verrouillé). */
class SmokeTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        val container = applicationContext.appContainer
        container.scope.launch {
            if (container.repository.snapshot().phase == Phase.QUIT) {
                // Pendant l'arrêt, un écart se gère dans l'appli, avec bienveillance.
                withContext(Dispatchers.Main) { open(null) }
                return@launch
            }
            container.notifications.cancelNextAllowed()
            container.repository.logSmoke()
            refresh()
        }
    }

    private fun refresh() {
        val container = applicationContext.appContainer
        container.scope.launch {
            val snapshot = container.repository.snapshot()
            val now = System.currentTimeMillis()
            val (label, subtitle) = if (snapshot.phase == Phase.QUIT) {
                val start = listOfNotNull(snapshot.lastSmokeAt, container.repository.config().quitStartedAt).maxOrNull() ?: now
                str(R.string.notify_sans_fumer) to str(R.string.components_j, (now - start) / DAY_MS)
            } else {
                str(R.string.home_j_en_fume_une) to (snapshot.nextAllowedAt?.takeIf { it > now }?.let { str(R.string.notify_prochaine_a, Fmt.time(it)) } ?: str(R.string.notify_c_est_ok_aujourd_hui, snapshot.todayCount))
            }
            withContext(Dispatchers.Main) {
                val tile = qsTile ?: return@withContext
                tile.label = label
                tile.subtitle = subtitle
                tile.icon = Icon.createWithResource(this@SmokeTileService, R.drawable.ic_stat_exit)
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()
            }
        }
    }
}

/** Tuile « SOS envie » : ouvre directement le mode SOS. */
class SosTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            label = getString(R.string.shortcut_sos)
            subtitle = str(R.string.notify_respire_5_min)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() = open(MainActivity.DEST_SOS)
}
