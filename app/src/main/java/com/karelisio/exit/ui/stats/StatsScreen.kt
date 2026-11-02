package com.karelisio.exit.ui.stats

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
        topBar = { ExitTopBar("Stats", scroll) },
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
                ) { Text("Bilan de la semaine") }
            }
            item(key = "chart") {
                ExitCard(modifier = Modifier.cascade(0)) {
                    Text("Consommation", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("14 jours", "8 semaines").forEachIndexed { i, label ->
                            SegmentedButton(selected = range == i, onClick = { range = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (range == 0) {
                        BarChart(
                            values = stats.daily.map { it.second },
                            labels = stats.daily.map { it.first.dayOfMonth.toString() },
                            target = target,
                            key = "d",
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                        )
                    } else {
                        BarChart(
                            values = stats.weekly.map { it.second },
                            labels = stats.weekly.map { Fmt.dayShort(it.first).substringBefore(' ') },
                            target = emptyList(),
                            key = "w",
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                        )
                    }
                }
            }
            item(key = "figures") {
                Column(Modifier.cascade(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Figure("Intervalle moyen (7 j)", stats.averageIntervalMinutes?.let { PlanEngine.formatDuration(it) } ?: "—", Modifier.weight(1f))
                        Figure("Plus long intervalle", if (stats.longestGapMinutes > 0) Fmt.duration(stats.longestGapMinutes * 60_000) else "—", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Figure("Meilleure série sans fumer", "${stats.longestSmokeFreeDays} j", Modifier.weight(1f))
                        Figure("Envies surmontées", "${stats.cravingsOvercome} / ${stats.cravingsTotal}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Figure("Bonus tenu (au-delà du minuteur)", if (stats.bonusMinutes > 0) Fmt.duration(stats.bonusMinutes * 60_000L) else "—", Modifier.weight(1f))
                        Figure(
                            "Intensité des envies (début → fin)",
                            if (stats.intensityStartAvg != null) "%.1f → %s".format(stats.intensityStartAvg, stats.intensityEndAvg?.let { "%.1f".format(it) } ?: "?") else "—",
                            Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val delay = stats.morningDelay
                        val prev = stats.previousMorningDelay
                        Figure(
                            "Première du matin après le réveil (7 j)",
                            delay?.let { PlanEngine.formatDuration(it) + (prev?.let { p -> (it - p).takeIf { d -> d != 0 }?.let { d -> if (d > 0) " (+$d)" else " ($d)" } } ?: "") } ?: "—",
                            Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Figure("Cigarettes notées", Fmt.number(stats.total), Modifier.weight(1f))
                        Figure("En avance", if (stats.total > 0) "${stats.earlyCount * 100 / stats.total} %" else "—", Modifier.weight(1f))
                    }
                }
            }
            item(key = "insight") { Insight(stats, Modifier.cascade(2)) }
            item(key = "hours") {
                ExitCard(modifier = Modifier.cascade(3)) {
                    Text("Heures les plus fréquentes", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    HourChart(stats.hours, stats.cravingHours, Modifier.fillMaxWidth().height(140.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("0 h", "6 h", "12 h", "18 h", "23 h").forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Barres : cigarettes · points : envies", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            item(key = "triggers") {
                ExitCard(modifier = Modifier.cascade(4)) {
                    Text("Contextes à risque", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    if (stats.smokeTriggers.isEmpty() && stats.cravingTriggers.isEmpty()) {
                        Text(
                            "Ajoute un contexte quand tu notes une cigarette ou une envie : Exit te montrera ici tes moments à risque.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        if (stats.smokeTriggers.isNotEmpty()) {
                            SectionTitle("Cigarettes")
                            TriggerBars(stats.smokeTriggers, MaterialTheme.colorScheme.primary)
                        }
                        if (stats.cravingTriggers.isNotEmpty()) {
                            SectionTitle("Envies")
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
    Trigger.CAFE to "Change un détail du rituel : autre tasse, autre endroit, un verre d'eau avant.",
    Trigger.STRESS to "Le mode SOS et ses 5 minutes de respiration sont faits pour ces moments-là.",
    Trigger.REPAS to "Après le repas, lève-toi tout de suite : vaisselle, petite marche, brossage de dents.",
    Trigger.VOITURE to "Garde des chewing-gums dans la voiture et mets un podcast dès le démarrage.",
    Trigger.ALCOOL to "L'alcool baisse la garde : prévois une boisson sans alcool sur deux.",
    Trigger.ENNUI to "Prépare une liste de petites occupations de 5 minutes, à piocher.",
    Trigger.PERSO to "Tu connais tes moments : anticipe-les avec une alternative prête.",
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
                Text("Ton moment à risque", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                val parts = listOfNotNull(hour?.let { "vers $it h" }, trigger?.let { "souvent avec « ${it.label.lowercase()} » ${it.emoji}" })
                Text(parts.joinToString(", ").replaceFirstChar { it.uppercase() } + ".", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                trigger?.let { TIPS[it] }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}

/** Barres qui poussent une à une avec un ressort ; ligne pointillée = objectif du plan. */
@Composable
private fun BarChart(values: List<Int>, labels: List<String>, target: List<Float?>, key: String, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val anims = remember(key, values.size) { values.map { Animatable(if (reduce) 1f else 0f) } }
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
    Column(modifier.semantics { contentDescription = "Cigarettes par période. $summary" }) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val n = values.size.coerceAtLeast(1)
                val slot = size.width / n
                val barW = slot * 0.62f
                values.forEachIndexed { i, v ->
                    val h = v / max.toFloat() * size.height * anims.getOrNull(i)?.value.orZero()
                    val x = i * slot + (slot - barW) / 2
                    drawRoundRect(
                        if (i == n - 1) today else primary.copy(alpha = 0.55f + 0.45f * (i + 1) / n),
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
                    if (labels.size > 10 && i % 2 == 1) "" else label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
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
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 120f)) }
    val primary = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.error
    val tertiary = MaterialTheme.colorScheme.tertiary
    val maxH = maxOf(hours.maxOrNull() ?: 0, cravings.maxOrNull() ?: 0, 1)
    val top = hours.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
    Canvas(modifier.semantics { contentDescription = top?.let { "Heure la plus fréquente : $it h, ${hours[it]} cigarettes" } ?: "Pas encore de données" }) {
        val slot = size.width / 24
        val barW = slot * 0.7f
        hours.forEachIndexed { i, v ->
            val h = v / maxH.toFloat() * size.height * grow.value
            drawRoundRect(
                if (i == top) highlight else primary.copy(alpha = 0.75f),
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
