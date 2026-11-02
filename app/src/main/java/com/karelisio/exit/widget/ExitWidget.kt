package com.karelisio.exit.widget

import com.karelisio.exit.plural
import com.karelisio.exit.str
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.karelisio.exit.MainActivity
import com.karelisio.exit.R
import com.karelisio.exit.appContainer
import com.karelisio.exit.data.ConfigEntity
import com.karelisio.exit.data.SmokeEntity
import com.karelisio.exit.domain.DAY_MS
import com.karelisio.exit.domain.Phase
import com.karelisio.exit.domain.PlanEngine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Ce qu'affiche le widget, calculé à chaque recomposition. */
internal data class WidgetState(
    val ready: Boolean,
    val phase: Phase,
    val todayCount: Int,
    val target: Int,
    val nextAllowedAt: Long?,
    val ok: Boolean,
    val progress: Float,
    val streakStart: Long?,
    val now: Long,
)

internal fun widgetState(config: ConfigEntity?, smokes: List<SmokeEntity>, now: Long): WidgetState {
    val zone = ZoneId.systemDefault()
    if (config == null || !config.onboardingDone) {
        return WidgetState(false, Phase.SPACING, 0, 0, null, true, 1f, null, now)
    }
    val today = PlanEngine.day(now, zone)
    val todayCount = smokes.count { PlanEngine.day(it.timestamp, zone) == today }
    val last = smokes.maxOfOrNull { it.timestamp }
    val snapshot = PlanEngine.snapshot(config, last, todayCount, now, zone)
    return WidgetState(
        ready = true,
        phase = snapshot.phase,
        todayCount = todayCount,
        target = kotlin.math.ceil(snapshot.targetToday).toInt(),
        nextAllowedAt = snapshot.nextAllowedAt,
        ok = snapshot.isOk(now),
        progress = snapshot.progress(now),
        streakStart = listOfNotNull(last, config.quitStartedAt).maxOrNull(),
        now = now,
    )
}

/**
 * Widget d'écran d'accueil : minuteur « prochaine autorisée » (compte à rebours en direct),
 * bouton +1, compteur du jour ou jours sans fumer. Couleurs dynamiques (GlanceTheme),
 * coins arrondis du système, redimensionnable.
 */
class ExitWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, SQUARE, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.appContainer.repository
        provideContent {
            val config by repository.configOrNull.collectAsState(null)
            val smokes by repository.smokes.collectAsState(emptyList())
            val tick by WidgetUpdater.tick.collectAsState()
            val now = maxOf(tick, System.currentTimeMillis())
            GlanceTheme {
                WidgetContent(widgetState(config, smokes, now))
            }
        }
    }

    companion object {
        val SMALL = DpSize(57.dp, 57.dp)
        val SQUARE = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(200.dp, 57.dp)
        val LARGE = DpSize(250.dp, 110.dp)
    }
}

@Composable
private fun WidgetContent(state: WidgetState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val open = androidx.glance.action.actionStartActivity<MainActivity>()
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .clickable(open)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !state.ready -> Text(
                str(R.string.widget_ouvre_exit_pour_commencer),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, textAlign = TextAlign.Center),
            )
            // 1×1 : uniquement le bouton et son anneau, dimensionnés pour tenir dans la case.
            size.width < 100.dp -> ActionButton(state, (minOf(size.width, size.height).value - 20).toInt().coerceIn(32, 72))
            size.width >= 180.dp -> Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Status(state, big = size.height >= 100.dp)
                    Spacer(GlanceModifier.height(2.dp))
                    Counter(state)
                }
                Spacer(GlanceModifier.width(8.dp))
                ActionButton(state, if (size.height >= 100.dp) 76 else 52)
            }
            else -> Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Status(state, big = false, center = true)
                Counter(state, center = true)
                Spacer(GlanceModifier.height(6.dp))
                ActionButton(state, 56)
            }
        }
    }
}

private fun streakDays(state: WidgetState): Long = state.streakStart?.let { (state.now - it) / DAY_MS } ?: 0

@Composable
private fun Status(state: WidgetState, big: Boolean, center: Boolean = false) {
    val context = LocalContext.current
    val align = if (center) TextAlign.Center else TextAlign.Start
    val labelStyle = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = align)
    val valueSize = if (big) 30.sp else 22.sp
    val valueColor = GlanceTheme.colors.primary
    if (state.phase == Phase.QUIT) {
        Text(str(R.string.notify_sans_fumer), style = labelStyle, maxLines = 1)
        val start = state.streakStart
        if (start != null && state.now - start < DAY_MS) {
            Chronometer(base = start, countDown = false, color = valueColor, size = valueSize)
        } else {
            Text(
                streakDays(state).toInt().let { plural(R.plurals.days_count, it, it) },
                style = TextStyle(color = valueColor, fontSize = valueSize, fontWeight = FontWeight.Bold, textAlign = align),
                maxLines = 1,
            )
        }
        return
    }
    val next = state.nextAllowedAt
    if (state.ok || next == null) {
        Text(str(R.string.widget_prochaine), style = labelStyle, maxLines = 1)
        Text(
            str(R.string.widget_c_est_ok),
            style = TextStyle(color = valueColor, fontSize = valueSize, fontWeight = FontWeight.Bold, textAlign = align),
            maxLines = 1,
        )
    } else {
        val at = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()))
        Text(str(R.string.notify_prochaine_a, at), style = labelStyle, maxLines = 1)
        Chronometer(base = next, countDown = true, color = valueColor, size = valueSize)
    }
}

@Composable
private fun Counter(state: WidgetState, center: Boolean = false) {
    val text = if (state.phase == Phase.QUIT) {
        str(R.string.widget_tiens_bon_tu_avances)
    } else {
        str(R.string.widget_aujourd_hui, state.todayCount) + if (state.target > 0) " / ${state.target}" else ""
    }
    Text(
        text,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        ),
        maxLines = 1,
    )
}

/** +1 pendant la phase d'espacement ; SOS envie pendant l'arrêt. */
@Composable
private fun ActionButton(state: WidgetState, sizeDp: Int) {
    val context = LocalContext.current
    val quit = state.phase == Phase.QUIT
    val action: Action = if (quit) {
        actionStartActivity(
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DEST_SOS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    } else {
        actionRunCallback<LogSmokeAction>()
    }
    val description = if (quit) str(R.string.components_sos_envie) else str(R.string.home_j_en_fume_une)
    val showRing = !quit && !state.ok
    val ringColor = GlanceTheme.colors.primary.getColor(context).toArgb()
    val trackColor = GlanceTheme.colors.surfaceVariant.getColor(context).toArgb()
    Box(contentAlignment = Alignment.Center) {
    if (showRing) {
        Image(
            provider = ImageProvider(ringBitmap(context, sizeDp, state.progress, ringColor, trackColor)),
            contentDescription = null,
            modifier = GlanceModifier.size(sizeDp.dp),
        )
    }
    Box(
        modifier = GlanceModifier
            .size((sizeDp - 12).dp)
            .background(ImageProvider(R.drawable.widget_circle), colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer))
            .clickable(action)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (quit) {
            Image(
                provider = ImageProvider(R.drawable.ic_sos),
                contentDescription = null,
                modifier = GlanceModifier.size(((sizeDp - 12) * 0.5).dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
            )
        } else {
            Text(
                "+1",
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = ((sizeDp - 12) * 0.38).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
    }
}

/** Anneau de progression dessiné en bitmap (rafraîchi par les alarmes de tick du widget). */
private fun ringBitmap(context: Context, sizeDp: Int, progress: Float, color: Int, track: Int): android.graphics.Bitmap {
    val px = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val stroke = px * 0.07f
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    val rect = android.graphics.RectF(stroke, stroke, px - stroke, px - stroke)
    paint.color = track
    canvas.drawArc(rect, 0f, 360f, false, paint)
    paint.color = color
    canvas.drawArc(rect, -90f, 360f * progress.coerceIn(0.02f, 1f), false, paint)
    return bitmap
}

/** Chronomètre système (RemoteViews) : se met à jour seul chaque seconde, sans réveiller l'appli. */
@Composable
private fun Chronometer(base: Long, countDown: Boolean, color: ColorProvider, size: TextUnit) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
        val elapsedBase = SystemClock.elapsedRealtime() + (base - System.currentTimeMillis())
        setChronometer(R.id.chronometer, elapsedBase, null, true)
        setChronometerCountDown(R.id.chronometer, countDown)
        setTextColor(R.id.chronometer, color.getColor(context).toArgb())
        setTextViewTextSize(R.id.chronometer, TypedValue.COMPLEX_UNIT_SP, size.value)
    }
    AndroidRemoteViews(views, modifier = GlanceModifier.fillMaxWidth())
}
