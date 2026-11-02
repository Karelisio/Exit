package com.karelisio.exit.ui.stats

import com.karelisio.exit.ui.components.chartScrub
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.AnimatedContent
import com.karelisio.exit.domain.label
import com.karelisio.exit.domain.title
import com.karelisio.exit.domain.description
import com.karelisio.exit.domain.rationale
import com.karelisio.exit.domain.detail
import com.karelisio.exit.str
import androidx.compose.ui.res.stringResource
import com.karelisio.exit.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.exit.domain.PlanEngine
import com.karelisio.exit.domain.StatsCalculator
import com.karelisio.exit.domain.StatsSummary
import com.karelisio.exit.domain.Trigger
import com.karelisio.exit.ui.AppViewModel
import com.karelisio.exit.ui.components.ExitCard
import com.karelisio.exit.ui.components.ExitTopBar
import com.karelisio.exit.ui.components.Fmt
import com.karelisio.exit.ui.components.SectionTitle
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.components.SosFab
import com.karelisio.exit.ui.components.cascade
import com.karelisio.exit.ui.navigation.Navigator
import com.karelisio.exit.ui.theme.LocalReduceMotion
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

@Composable
fun StatsScreen(vm: AppViewModel, nav: Navigator) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val zone = ZoneId.systemDefault()
    val stats = remember(d.smokes, d.cravings, d.config.wakeMinutes) {
        StatsCalculator.compute(d.smokes, d.cravings, d.config.startedAt, System.currentTimeMillis(), zone, wakeMinutes = d.config.wakeMinutes)
    }
    val target = remember(d.config) {
        stats.daily.map { (day, _) -> PlanEngine.targetPerDay(d.config, day, zone).toFloat().takeIf { day >= PlanEngine.anchorDay(d.config, zone) } }
    }
    var range by rememberSaveable { mutableIntStateOf(0) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { ExitTopBar(stringResource(R.string.stats_stats), scroll) },
        floatingActionButton = { SosFab(nav::sos) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "review") {
                androidx.compose.material3.FilledTonalButton(
                    onClick = nav::review,
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().cascade(0),
                ) { Text(stringResource(R.string.stats_bilan_de_la_semaine)) }
            }
            item(key = "chart") {
                ExitCard(modifier = Modifier.cascade(0)) {
                    Text(stringResource(R.string.stats_consommation), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(stringResource(R.string.stats_14_jours), stringResource(R.string.stats_8_semaines)).forEachIndexed { i, label ->
                            SegmentedButton(selected = range == i, onClick = { range = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (range == 0) {
                        BarChart(
                            values = stats.daily.map { it.second },
                            labels = stats.daily.map { it.first.dayOfMonth.toString() },
                            details = stats.daily.map { Fmt.dayLabel(it.first) },
                            target = target,
                            key = "d",
                            modifier = Modifier.fillMaxWidth().height(230.dp),
                        )
                    } else {
                        BarChart(
                            values = stats.weekly.map { it.second },
                            labels = stats.weekly.map { Fmt.dayShort(it.first).substringBefore(' ') },
                            details = stats.weekly.map { "${Fmt.dayShort(it.first)} → ${Fmt.dayShort(it.first.plusDays(6))}" },
                            target = emptyList(),
                            key = "w",
                            modifier = Modifier.fillMaxWidth().height(230.dp),
                        )
                    }
                }
            }
            item(key = "figures") {
                Column(Modifier.cascade(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Figure(stringResource(R.string.stats_intervalle_moyen_7_j), stats.averageIntervalMinutes?.let { PlanEngine.formatDuration(it) } ?: "—", Modifier.weight(1f))
                        Figure(stringResource(R.string.stats_plus_long_intervalle), if (stats.longestGapMinutes > 0) Fmt.duration(stats.longestGapMinutes * 60_000) else "—", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Figure(stringResource(R.string.stats_meilleure_serie_sans_fumer), stringResource(R.string.components_j, stats.longestSmokeFreeDays), Modifier.weight(1f))
                        Figure(stringResource(R.string.stats_envies_surmontees), "${stats.cravingsOvercome} / ${stats.cravingsTotal}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Figure(stringResource(R.string.stats_bonus_tenu_au_dela_du), if (stats.bonusMinutes > 0) Fmt.duration(stats.bonusMinutes * 60_000L) else "—", Modifier.weight(1f))
                        Figure(
                            stringResource(R.string.stats_intensite_des_envies_debut_fin),
                            if (stats.intensityStartAvg != null) "%.1f → %s".format(com.karelisio.exit.ui.components.Fmt.locale, stats.intensityStartAvg, stats.intensityEndAvg?.let { "%.1f".format(com.karelisio.exit.ui.components.Fmt.locale, it) } ?: "?") else "—",
                            Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val delay = stats.morningDelay
                        val prev = stats.previousMorningDelay
                        Figure(
                            stringResource(R.string.stats_premiere_du_matin_apres_le),
                            delay?.let { PlanEngine.formatDuration(it) + (prev?.let { p -> (it - p).takeIf { d -> d != 0 }?.let { d -> if (d > 0) " (+$d)" else " ($d)" } } ?: "") } ?: "—",
                            Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Figure(stringResource(R.string.stats_cigarettes_notees), Fmt.number(stats.total), Modifier.weight(1f))
                        Figure(stringResource(R.string.stats_en_avance), if (stats.total > 0) "${stats.earlyCount * 100 / stats.total} %" else "—", Modifier.weight(1f))
                    }
                }
            }
            item(key = "insight") { Insight(stats, Modifier.cascade(2)) }
            item(key = "hours") {
                ExitCard(modifier = Modifier.cascade(3)) {
                    Text(stringResource(R.string.stats_heures_les_plus_frequentes), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    HourChart(stats.hours, stats.cravingHours, Modifier.fillMaxWidth().height(170.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(stringResource(R.string.stats_0_h), stringResource(R.string.stats_6_h), stringResource(R.string.stats_12_h), stringResource(R.string.stats_18_h), stringResource(R.string.stats_23_h)).forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.stats_barres_cigarettes_points_envies), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            item(key = "mood") {
                ExitCard(modifier = Modifier.cascade(4)) {
                    Text(stringResource(R.string.mood_chart_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    if (d.checkIns.isEmpty()) {
                        Text(stringResource(R.string.mood_chart_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        MoodChart(d.checkIns, Modifier.fillMaxWidth().height(190.dp))
                    }
                }
            }
            item(key = "triggers") {
                ExitCard(modifier = Modifier.cascade(4)) {
                    Text(stringResource(R.string.stats_contextes_a_risque), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    if (stats.smokeTriggers.isEmpty() && stats.cravingTriggers.isEmpty()) {
                        Text(
                            stringResource(R.string.stats_ajoute_un_contexte_quand_tu),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        if (stats.smokeTriggers.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.stats_cigarettes))
                            TriggerBars(stats.smokeTriggers, MaterialTheme.colorScheme.primary)
                        }
                        if (stats.cravingTriggers.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.stats_envies))
                            TriggerBars(stats.cravingTriggers, MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    ExitCard(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val TIPS = mapOf(
    Trigger.CAFE to str(R.string.stats_change_un_detail_du_rituel),
    Trigger.STRESS to str(R.string.stats_le_mode_sos_et_ses),
    Trigger.REPAS to str(R.string.stats_apres_le_repas_leve_toi),
    Trigger.VOITURE to str(R.string.stats_garde_des_chewing_gums_dans),
    Trigger.ALCOOL to str(R.string.stats_l_alcool_baisse_la_garde),
    Trigger.ENNUI to str(R.string.stats_prepare_une_liste_de_petites),
    Trigger.PERSO to str(R.string.stats_tu_connais_tes_moments_anticipe),
)

@Composable
private fun Insight(stats: StatsSummary, modifier: Modifier = Modifier) {
    val hour = stats.riskHour
    val trigger = stats.riskTrigger
    if (hour == null && trigger == null) return
    ExitCard(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            ShapeIcon(Icons.Rounded.Lightbulb, MaterialShapes.Sunny, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.stats_ton_moment_a_risque), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                val parts = listOfNotNull(hour?.let { stringResource(R.string.stats_vers_h, it) }, trigger?.let { stringResource(R.string.stats_souvent_avec, it.label.lowercase(), it.emoji) })
                Text(parts.joinToString(", ").replaceFirstChar { it.uppercase() } + ".", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                trigger?.let { TIPS[it] }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}

/** Barres qui poussent une à une avec un ressort ; ligne pointillée = objectif du plan. Touche ou glisse pour le détail. */
@Composable
private fun BarChart(values: List<Int>, labels: List<String>, details: List<String>, target: List<Float?>, key: String, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val anims = remember(key, values.size) { values.map { Animatable(if (reduce) 1f else 0f) } }
    var selected by remember(key) { mutableStateOf<Int?>(null) }
    LaunchedEffect(key, values) {
        coroutineScope {
            anims.forEachIndexed { i, a ->
                launch {
                    delay(i * 35L)
                    a.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 220f))
                }
            }
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val today = MaterialTheme.colorScheme.tertiary
    val dash = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val max = maxOf(values.maxOrNull() ?: 0, target.filterNotNull().maxOrNull()?.toInt() ?: 0, 1)
    val summary = labels.zip(values).joinToString(", ") { (l, v) -> "$l : $v" }
    Column(modifier.semantics { contentDescription = summary }) {
        AnimatedContent(selected, label = "barDetail") { sel ->
            Text(
                sel?.let { stringResource(R.string.chart_bar_detail, details[it], pluralStringResource(R.plurals.cigarettes_count, values[it], values[it])) }
                    ?: stringResource(R.string.chart_tap_hint),
                style = if (sel != null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                color = if (sel != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().chartScrub(values.size, selected) { selected = it }) {
            Canvas(Modifier.matchParentSize()) {
                val n = values.size.coerceAtLeast(1)
                val slot = size.width / n
                val barW = slot * 0.62f
                values.forEachIndexed { i, v ->
                    val h = v / max.toFloat() * size.height * anims.getOrNull(i)?.value.orZero()
                    val x = i * slot + (slot - barW) / 2
                    val base = if (i == n - 1) today else primary.copy(alpha = 0.55f + 0.45f * (i + 1) / n)
                    val color = when (selected) {
                        null -> base
                        i -> today
                        else -> base.copy(alpha = 0.3f)
                    }
                    drawRoundRect(
                        color,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h.coerceAtLeast(0f)),
                        cornerRadius = CornerRadius(barW / 2, barW / 2),
                    )
                }
                target.forEachIndexed { i, t ->
                    if (t == null) return@forEachIndexed
                    val y = size.height - t / max * size.height
                    drawLine(dash, Offset(i * slot, y), Offset((i + 1) * slot, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    if (labels.size > 10 && i % 2 == 1 && i != selected) "" else label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == selected) MaterialTheme.colorScheme.tertiary else labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun Float?.orZero() = this ?: 0f

@Composable
private fun HourChart(hours: IntArray, cravings: IntArray, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val grow = remember { Animatable(if (reduce) 1f else 0f) }
    var selected by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 120f)) }
    val primary = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.error
    val tertiary = MaterialTheme.colorScheme.tertiary
    val maxH = maxOf(hours.maxOrNull() ?: 0, cravings.maxOrNull() ?: 0, 1)
    val top = hours.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
    Column(modifier) {
        AnimatedContent(selected, label = "hourDetail") { sel ->
            Text(
                sel?.let {
                    stringResource(
                        R.string.chart_hour_detail, it,
                        pluralStringResource(R.plurals.cigarettes_count, hours[it], hours[it]),
                        pluralStringResource(R.plurals.cravings_count, cravings[it], cravings[it]),
                    )
                } ?: stringResource(R.string.chart_tap_hint),
                style = if (sel != null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                color = if (sel != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().weight(1f)
                .chartScrub(24, selected) { selected = it }
                .semantics { contentDescription = top?.let { "${it} h : ${hours[it]}" } ?: "" },
        ) {
            val slot = size.width / 24
            val barW = slot * 0.7f
            hours.forEachIndexed { i, v ->
                val h = v / maxH.toFloat() * size.height * grow.value
                val color = when {
                    selected == i -> tertiary
                    selected != null -> primary.copy(alpha = 0.3f)
                    i == top -> highlight
                    else -> primary.copy(alpha = 0.75f)
                }
                drawRoundRect(
                    color,
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2, barW / 2),
                )
            }
            cravings.forEachIndexed { i, v ->
                if (v == 0) return@forEachIndexed
                val y = size.height - v / maxH.toFloat() * size.height * grow.value
                drawCircle(tertiary, 3.5.dp.toPx(), Offset(i * slot + slot / 2, y))
            }
        }
    }
}

/** Humeur (trait plein, 1 à 5) et sevrage (pointillés, 0 à 3) sur 30 jours ; touche ou glisse pour le détail. */
@Composable
private fun MoodChart(checkIns: List<com.karelisio.exit.data.CheckInEntity>, modifier: Modifier = Modifier) {
    val today = java.time.LocalDate.now()
    val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = remember(checkIns) { checkIns.associateBy { it.epochDay } }
    var selected by remember { mutableStateOf<Int?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        AnimatedContent(selected, label = "moodDetail") { sel ->
            val c = sel?.let { byDay[days[it].toEpochDay()] }
            Text(
                when {
                    sel == null -> stringResource(R.string.chart_tap_hint)
                    c == null -> Fmt.dayLabel(days[sel])
                    else -> stringResource(R.string.mood_detail, Fmt.dayLabel(days[sel]), com.karelisio.exit.ui.home.MOODS[c.mood - 1], "%.1f".format(Fmt.locale, c.withdrawal)) +
                        if (c.note.isNotBlank()) " · « ${c.note} »" else ""
                },
                style = if (sel != null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                color = if (sel != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f).chartScrub(days.size, selected) { selected = it }) {
            val slot = size.width / days.size
            fun x(i: Int) = i * slot + slot / 2
            repeat(3) { k -> val gy = size.height * (k + 1) / 4f; drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx()) }
            val mood = androidx.compose.ui.graphics.Path()
            val withdrawal = androidx.compose.ui.graphics.Path()
            var startedM = false
            var startedW = false
            days.forEachIndexed { i, d ->
                val c = byDay[d.toEpochDay()] ?: return@forEachIndexed
                val ym = size.height - (c.mood - 1) / 4f * size.height
                val yw = size.height - c.withdrawal / 3f * size.height
                if (startedM) mood.lineTo(x(i), ym) else { mood.moveTo(x(i), ym); startedM = true }
                if (startedW) withdrawal.lineTo(x(i), yw) else { withdrawal.moveTo(x(i), yw); startedW = true }
                drawCircle(primary, if (i == selected) 6.dp.toPx() else 3.5.dp.toPx(), Offset(x(i), ym))
            }
            drawPath(mood, primary, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            drawPath(withdrawal, tertiary, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            selected?.let { drawLine(grid, Offset(x(it), 0f), Offset(x(it), size.height), 2.dp.toPx()) }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.mood_chart_legend), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun TriggerBars(items: List<Pair<Trigger, Int>>, color: Color) {
    val max = items.maxOf { it.second }.coerceAtLeast(1)
    val reduce = LocalReduceMotion.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, (trigger, count) ->
            val anim = remember(trigger, count) { Animatable(if (reduce) 1f else 0f) }
            LaunchedEffect(trigger, count) {
                delay(index * 60L)
                anim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 200f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${trigger.emoji} ${trigger.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(132.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(18.dp)) {
                    Surface(
                        color = color,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.fillMaxWidth(count / max.toFloat() * anim.value).height(18.dp),
                    ) {}
                }
                Text("  $count", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
