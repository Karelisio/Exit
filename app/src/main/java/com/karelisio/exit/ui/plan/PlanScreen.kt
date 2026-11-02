package com.karelisio.exit.ui.plan

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
        topBar = { ExitTopBar("Ton plan", scroll, onBack = { nav.back() }) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "summary") {
                ExitCard(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.cascade(0)) {
                    val onColor = MaterialTheme.colorScheme.onPrimaryContainer
                    val title = when {
                        snapshot.phase == Phase.QUIT -> "Arrêt complet"
                        c.quitEpochDay != null -> "Objectif : arrêt le ${Fmt.dayFull(LocalDate.ofEpochDay(c.quitEpochDay))}"
                        else -> "On espace d'abord"
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = onColor)
                    Spacer(Modifier.height(8.dp))
                    val lines = buildList {
                        if (snapshot.phase == Phase.SPACING) {
                            add("Intervalle aujourd'hui : ${PlanEngine.formatDuration(snapshot.intervalMinutes)}")
                            add("Objectif du jour : ≈ ${ceil(snapshot.targetToday).toInt()} cigarette${if (snapshot.targetToday > 1) "s" else ""}")
                            if (c.quitEpochDay == null) {
                                add("+${c.stepMinutes} min ${if (PlanEngine.stepPeriod(c) == StepPeriod.WEEK) "chaque semaine" else "chaque jour"}")
                                snapshot.projectedQuitDay?.let { add("Zéro estimé vers le ${Fmt.dayFull(it)}") }
                            } else {
                                val left = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.ofEpochDay(c.quitEpochDay))
                                add(if (left > 0) "Encore $left jour${if (left > 1) "s" else ""}, à ton rythme" else "C'est le moment : tu peux passer à l'arrêt complet")
                            }
                        } else {
                            c.quitStartedAt?.let { add("Depuis le ${Fmt.dayFull(PlanEngine.day(it, zone))}") }
                            add("Le plan reste là si tu as besoin de revenir à l'espacement.")
                        }
                    }
                    lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = onColor) }
                }
            }
            if (snapshot.phase == Phase.SPACING) {
                item(key = "chart") {
                    ExitCard(modifier = Modifier.cascade(1)) {
                        Text("Courbe de réduction", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Legend()
                        Spacer(Modifier.height(12.dp))
                        PlanChart(c, d.smokes, Modifier.fillMaxWidth().height(220.dp))
                    }
                }
                item(key = "adjust") {
                    ExitCard(modifier = Modifier.cascade(2)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShapeIcon(Icons.Rounded.Tune, MaterialShapes.Cookie6Sided, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Ajuster à ta façon", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        StatLine("Intervalle", PlanEngine.formatDuration(snapshot.intervalMinutes))
                        if (c.manualOffsetMinutes != 0) {
                            Text(
                                "dont ${if (c.manualOffsetMinutes > 0) "+" else "−"}${PlanEngine.formatDuration(kotlin.math.abs(c.manualOffsetMinutes))} ajusté à la main",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { haptics.tick(); vm.adjustOffset(-15) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("− 15 min") }
                            FilledTonalButton(onClick = { haptics.tick(); vm.adjustOffset(15) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("+ 15 min") }
                        }

                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Date d'arrêt", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    c.quitEpochDay?.let { Fmt.dayFull(LocalDate.ofEpochDay(it)) } ?: "Pas de date : on espace d'abord",
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
                                Text("  Changer la date")
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("Paliers", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        val period = PlanEngine.stepPeriod(c)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            StepPeriod.entries.forEachIndexed { i, p ->
                                SegmentedButton(
                                    selected = period == p,
                                    onClick = { vm.setStep(p, c.stepMinutes) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                ) { Text(if (p == StepPeriod.DAY) "Chaque jour" else "Chaque semaine") }
                            }
                        }
                        if (c.quitEpochDay == null) {
                            Spacer(Modifier.height(12.dp))
                            var step by remember(c.stepMinutes) { mutableFloatStateOf(c.stepMinutes.toFloat()) }
                            Text("+${step.roundToInt()} min par palier", style = MaterialTheme.typography.bodyLarge)
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
                            Text("  Recalculer depuis aujourd'hui")
                        }
                        Text(
                            "Repart de ta consommation réelle des 3 derniers jours, sans rien effacer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                item(key = "quit") {
                    Button(onClick = { confirmQuit = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(60.dp).cascade(3)) {
                        Icon(Icons.Rounded.Flag, contentDescription = null)
                        Text("  Passer à l'arrêt complet", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                item(key = "resume") {
                    OutlinedButton(onClick = { confirmResume = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().cascade(1)) {
                        Icon(Icons.Rounded.Restore, contentDescription = null)
                        Text("  Revenir au plan d'espacement")
                    }
                }
            }
            if (d.events.isNotEmpty()) {
                item(key = "journalTitle") { SectionTitle("Journal du plan") }
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
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Annuler") } },
        ) { DatePicker(state, title = { Text("Date d'arrêt", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }) }
    }
    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            icon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
            title = { Text("Passer à l'arrêt complet ?") },
            text = { Text("Le minuteur laisse place au compteur de jours sans fumer. Si un écart arrive, rien n'est perdu.") },
            confirmButton = {
                Button(onClick = {
                    confirmQuit = false
                    haptics.success()
                    vm.startQuit()
                }) { Text("C'est parti") }
            },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Pas encore") } },
        )
    }
    if (confirmResume) {
        AlertDialog(
            onDismissRequest = { confirmResume = false },
            title = { Text("Revenir à l'espacement ?") },
            text = { Text("On repart en douceur avec un minuteur. Tes jours sans fumer, tes gains et tes badges restent acquis.") },
            confirmButton = {
                Button(onClick = {
                    confirmResume = false
                    vm.resumePlanAfterSlip()
                }) { Text("Revenir au plan") }
            },
            dismissButton = { TextButton(onClick = { confirmResume = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Legend() {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp, 10.dp)) { drawLine(primary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx(), StrokeCap.Round) }
        Text("  prévu   ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.size(10.dp)) { drawCircle(secondary) }
        Text("  réel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val todayColor = MaterialTheme.colorScheme.tertiary
    Box(modifier.semantics {
        contentDescription = "Courbe de réduction : objectif de ${(curve.firstOrNull()?.second ?: 0.0).roundToInt()} par jour au départ, " +
            "zéro le ${curve.lastOrNull()?.first?.let { Fmt.dayFull(it) } ?: "—"}"
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
            "${(curve.maxOfOrNull { it.second } ?: 0.0).roundToInt()} / jour",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopStart),
        )
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
