package com.karelisio.exit.ui.plan

import com.karelisio.exit.ui.components.chartScrub
import androidx.compose.ui.res.pluralStringResource
import com.karelisio.exit.str
import androidx.compose.ui.res.stringResource
import com.karelisio.exit.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.exit.data.ConfigEntity
import com.karelisio.exit.data.PlanEventEntity
import com.karelisio.exit.data.SmokeEntity
import com.karelisio.exit.domain.Phase
import com.karelisio.exit.domain.PlanEngine
import com.karelisio.exit.domain.PlanEventType
import com.karelisio.exit.domain.StepPeriod
import com.karelisio.exit.ui.AppViewModel
import com.karelisio.exit.ui.components.ExitCard
import com.karelisio.exit.ui.components.ExitTopBar
import com.karelisio.exit.ui.components.Fmt
import com.karelisio.exit.ui.components.SectionTitle
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.components.StatLine
import com.karelisio.exit.ui.components.cascade
import com.karelisio.exit.ui.components.rememberHaptics
import com.karelisio.exit.ui.navigation.Navigator
import com.karelisio.exit.ui.navigation.sharedContainer
import com.karelisio.exit.ui.theme.LocalReduceMotion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun PlanScreen(vm: AppViewModel, nav: Navigator) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val c = d.config
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val snapshot = remember(c, d.smokes) {
        val today = LocalDate.now()
        PlanEngine.snapshot(c, d.lastSmokeAt, d.smokes.count { PlanEngine.day(it.timestamp, zone) == today }, now, zone)
    }
    val haptics = rememberHaptics()
    var pickDate by remember { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    var confirmResume by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection).sharedContainer("plan"),
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = { ExitTopBar(stringResource(R.string.plan_ton_plan), scroll, onBack = { nav.back() }) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "summary") {
                ExitCard(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.cascade(0)) {
                    val onColor = MaterialTheme.colorScheme.onPrimaryContainer
                    val title = when {
                        snapshot.phase == Phase.QUIT -> stringResource(R.string.plan_arret_complet)
                        c.quitEpochDay != null -> stringResource(R.string.plan_objectif_arret_le, Fmt.dayFull(LocalDate.ofEpochDay(c.quitEpochDay)))
                        else -> stringResource(R.string.plan_on_espace_d_abord)
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = onColor)
                    Spacer(Modifier.height(8.dp))
                    val lines = buildList {
                        if (snapshot.phase == Phase.SPACING) {
                            add(stringResource(R.string.plan_intervalle_aujourd_hui, PlanEngine.formatDuration(snapshot.intervalMinutes)))
                            add(ceil(snapshot.targetToday).toInt().let { pluralStringResource(R.plurals.plan_target_today, it, it) })
                            if (c.quitEpochDay == null) {
                                add(stringResource(R.string.plan_min, c.stepMinutes, if (PlanEngine.stepPeriod(c) == StepPeriod.WEEK) "chaque semaine" else "chaque jour"))
                                snapshot.projectedQuitDay?.let { add(stringResource(R.string.plan_zero_estime_vers_le, Fmt.dayFull(it))) }
                            } else {
                                val left = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.ofEpochDay(c.quitEpochDay))
                                add(if (left > 0) pluralStringResource(R.plurals.plan_days_left, left.toInt(), left.toInt()) else stringResource(R.string.plan_c_est_le_moment_tu))
                            }
                        } else {
                            c.quitStartedAt?.let { add(stringResource(R.string.plan_depuis_le, Fmt.dayFull(PlanEngine.day(it, zone)))) }
                            add(stringResource(R.string.plan_le_plan_reste_la_si))
                        }
                    }
                    lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = onColor) }
                }
            }
            if (snapshot.phase == Phase.SPACING) {
                item(key = "chart") {
                    ExitCard(modifier = Modifier.cascade(1)) {
                        Text(stringResource(R.string.plan_courbe_de_reduction), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Legend()
                        Spacer(Modifier.height(12.dp))
                        PlanChart(c, d.smokes, Modifier.fillMaxWidth().height(250.dp))
                    }
                }
                item(key = "adjust") {
                    ExitCard(modifier = Modifier.cascade(2)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShapeIcon(Icons.Rounded.Tune, MaterialShapes.Cookie6Sided, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.plan_ajuster_a_ta_facon), style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        StatLine(stringResource(R.string.plan_intervalle), PlanEngine.formatDuration(snapshot.intervalMinutes))
                        if (c.manualOffsetMinutes != 0) {
                            Text(
                                stringResource(R.string.plan_dont_ajuste_a_la_main, if (c.manualOffsetMinutes > 0) "+" else "−", PlanEngine.formatDuration(kotlin.math.abs(c.manualOffsetMinutes))),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { haptics.tick(); vm.adjustOffset(-15) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.plan_15_min)) }
                            FilledTonalButton(onClick = { haptics.tick(); vm.adjustOffset(15) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.plan_15_min_2)) }
                        }

                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.plan_date_d_arret), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    c.quitEpochDay?.let { Fmt.dayFull(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.plan_pas_de_date_on_espace),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = c.quitEpochDay != null, onCheckedChange = { on ->
                                if (on) pickDate = true else vm.setQuitDay(null)
                            })
                        }
                        if (c.quitEpochDay != null) {
                            TextButton(onClick = { pickDate = true }) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                                Text(stringResource(R.string.plan_changer_la_date))
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.plan_paliers), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        val period = PlanEngine.stepPeriod(c)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            StepPeriod.entries.forEachIndexed { i, p ->
                                SegmentedButton(
                                    selected = period == p,
                                    onClick = { vm.setStep(p, c.stepMinutes) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                ) { Text(if (p == StepPeriod.DAY) stringResource(R.string.plan_chaque_jour) else stringResource(R.string.plan_chaque_semaine)) }
                            }
                        }
                        if (c.quitEpochDay == null) {
                            Spacer(Modifier.height(12.dp))
                            var step by remember(c.stepMinutes) { mutableFloatStateOf(c.stepMinutes.toFloat()) }
                            Text(stringResource(R.string.plan_min_par_palier, step.roundToInt()), style = MaterialTheme.typography.bodyLarge)
                            Slider(
                                value = step,
                                onValueChange = { step = it },
                                onValueChangeFinished = { vm.setStep(period, step.roundToInt()) },
                                valueRange = 1f..60f,
                                steps = 58,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { haptics.confirm(); vm.recalculateFromToday() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Text(stringResource(R.string.plan_recalculer_depuis_aujourd_hui))
                        }
                        Text(
                            stringResource(R.string.plan_repart_de_ta_consommation_reelle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                item(key = "quit") {
                    Button(onClick = { confirmQuit = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(60.dp).cascade(3)) {
                        Icon(Icons.Rounded.Flag, contentDescription = null)
                        Text(stringResource(R.string.plan_passer_a_l_arret_complet), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                item(key = "resume") {
                    OutlinedButton(onClick = { confirmResume = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().cascade(1)) {
                        Icon(Icons.Rounded.Restore, contentDescription = null)
                        Text(stringResource(R.string.plan_revenir_au_plan_d_espacement))
                    }
                }
            }
            if (d.events.isNotEmpty()) {
                item(key = str(R.string.plan_journaltitle)) { SectionTitle(stringResource(R.string.plan_journal_du_plan)) }
                items(d.events.take(40), key = { "e-${it.id}" }) { event -> EventRow(event, Modifier.animateItem()) }
            }
        }
    }

    if (pickDate) {
        val initial = (c.quitEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusDays(28))
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() > LocalDate.now()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.setQuitDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDate = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.history_annuler)) } },
        ) { DatePicker(state, title = { Text(stringResource(R.string.plan_date_d_arret), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }) }
    }
    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            icon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
            title = { Text(stringResource(R.string.plan_passer_a_l_arret_complet_2)) },
            text = { Text(stringResource(R.string.plan_le_minuteur_laisse_place_au)) },
            confirmButton = {
                Button(onClick = {
                    confirmQuit = false
                    haptics.success()
                    vm.startQuit()
                }) { Text(stringResource(R.string.plan_c_est_parti)) }
            },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text(stringResource(R.string.plan_pas_encore)) } },
        )
    }
    if (confirmResume) {
        AlertDialog(
            onDismissRequest = { confirmResume = false },
            title = { Text(stringResource(R.string.plan_revenir_a_l_espacement)) },
            text = { Text(stringResource(R.string.plan_on_repart_en_douceur_avec)) },
            confirmButton = {
                Button(onClick = {
                    confirmResume = false
                    vm.resumePlanAfterSlip()
                }) { Text(stringResource(R.string.plan_revenir_au_plan)) }
            },
            dismissButton = { TextButton(onClick = { confirmResume = false }) { Text(stringResource(R.string.history_annuler)) } },
        )
    }
}

@Composable
private fun Legend() {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp, 10.dp)) { drawLine(primary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx(), StrokeCap.Round) }
        Text(stringResource(R.string.plan_prevu), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.size(10.dp)) { drawCircle(secondary) }
        Text(stringResource(R.string.plan_reel), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Courbe prévue (aire dégradée) et consommation réelle (points), dessinée progressivement. */
@Composable
private fun PlanChart(c: ConfigEntity, smokes: List<SmokeEntity>, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val reduce = LocalReduceMotion.current
    val curve = remember(c) { PlanEngine.curve(c, zone, 180) }
    val today = LocalDate.now()
    val start = remember(c) { minOf(PlanEngine.day(c.startedAt, zone), curve.firstOrNull()?.first ?: today).let { s -> maxOf(s, today.minusDays(60)) } }
    val end = curve.lastOrNull()?.first?.let { maxOf(it, today.plusDays(3)) } ?: today.plusDays(14)
    val actual = remember(smokes, start) {
        smokes.groupingBy { PlanEngine.day(it.timestamp, zone) }.eachCount().filterKeys { it >= start && it <= today }
    }
    val reveal = remember { Animatable(if (reduce) 1f else 0f) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val dayCount = ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(1) + 1
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val todayColor = MaterialTheme.colorScheme.tertiary
    val selectedDay = selected?.let { start.plusDays(it.toLong()) }
    Column(modifier) {
    androidx.compose.animation.AnimatedContent(selectedDay, label = "planDetail") { day ->
        Text(
            day?.let {
                stringResource(
                    R.string.chart_plan_detail,
                    Fmt.dayLabel(it),
                    "%.1f".format(Fmt.locale, PlanEngine.targetPerDay(c, it, zone)),
                    if (it <= today) (actual[it] ?: 0).toString() else "—",
                )
            } ?: stringResource(R.string.chart_tap_hint),
            style = if (day != null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            color = if (day != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().weight(1f).chartScrub(dayCount, selected) { selected = it }.semantics {
        contentDescription = str(R.string.plan_courbe_de_reduction_objectif_de, (curve.firstOrNull()?.second ?: 0.0).roundToInt()) +
            str(R.string.plan_zero_le, curve.lastOrNull()?.first?.let { Fmt.dayFull(it) } ?: "—")
    }) {
        Canvas(Modifier.matchParentSize()) {
            val days = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1).toFloat()
            val max = maxOf(curve.maxOfOrNull { it.second } ?: 1.0, (actual.values.maxOrNull() ?: 0).toDouble(), 1.0).toFloat() * 1.1f
            fun x(day: LocalDate) = ChronoUnit.DAYS.between(start, day) / days * size.width
            fun y(v: Float) = size.height - v / max * size.height
            repeat(4) { i ->
                val gy = size.height * i / 4f
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            val tx = x(today)
            drawLine(todayColor, Offset(tx, 0f), Offset(tx, size.height), 2.dp.toPx())
            selectedDay?.let { drawLine(grid, Offset(x(it), 0f), Offset(x(it), size.height), 2.dp.toPx()) }
            clipRect(right = size.width * reveal.value) {
                val visible = curve.filter { it.first >= start }
                if (visible.size > 1) {
                    val line = Path()
                    visible.forEachIndexed { i, (day, v) ->
                        if (i == 0) line.moveTo(x(day), y(v.toFloat())) else line.lineTo(x(day), y(v.toFloat()))
                    }
                    val area = Path().apply {
                        addPath(line)
                        lineTo(x(visible.last().first), size.height)
                        lineTo(x(visible.first().first), size.height)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(primary.copy(alpha = 0.35f), primary.copy(alpha = 0.02f))))
                    drawPath(line, primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                actual.forEach { (day, count) -> drawCircle(secondary, 4.5.dp.toPx(), Offset(x(day), y(count.toFloat()))) }
            }
        }
        Text(
            stringResource(R.string.plan_jour, (curve.maxOfOrNull { it.second } ?: 0.0).roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}
}

@Composable
private fun EventRow(event: PlanEventEntity, modifier: Modifier = Modifier) {
    val type = runCatching { PlanEventType.valueOf(event.type) }.getOrNull()
    val (icon, shape) = when (type) {
        PlanEventType.START -> Icons.Rounded.PlayArrow to MaterialShapes.Circle
        PlanEventType.AUTO_ADJUST -> Icons.Rounded.AutoFixHigh to MaterialShapes.Cookie4Sided
        PlanEventType.MANUAL_ADJUST -> Icons.Rounded.Tune to MaterialShapes.Square
        PlanEventType.QUIT -> Icons.Rounded.Flag to MaterialShapes.Sunny
        PlanEventType.SLIP -> Icons.Rounded.Favorite to MaterialShapes.Heart
        PlanEventType.RESUME_PLAN -> Icons.Rounded.Restore to MaterialShapes.Clover4Leaf
        PlanEventType.IMPORT, null -> Icons.Rounded.Upload to MaterialShapes.Pill
    }
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        ShapeIcon(icon, shape, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${Fmt.dayLabel(PlanEngine.day(event.timestamp, ZoneId.systemDefault()))} · ${Fmt.time(event.timestamp)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(event.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
