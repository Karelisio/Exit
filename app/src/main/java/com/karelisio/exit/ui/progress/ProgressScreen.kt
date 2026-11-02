package com.karelisio.exit.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmokeFree
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.exit.domain.Badge
import com.karelisio.exit.domain.BadgeIcon
import com.karelisio.exit.domain.BadgeInputs
import com.karelisio.exit.domain.Badges
import com.karelisio.exit.domain.GainsCalculator
import com.karelisio.exit.domain.HealthMilestone
import com.karelisio.exit.domain.HealthMilestones
import com.karelisio.exit.domain.MINUTE_MS
import com.karelisio.exit.domain.Phase
import com.karelisio.exit.domain.PlanEngine
import com.karelisio.exit.domain.PlanEventType
import com.karelisio.exit.domain.StatsCalculator
import com.karelisio.exit.ui.AppViewModel
import com.karelisio.exit.ui.components.AnimatedCounter
import com.karelisio.exit.ui.components.CountUpText
import com.karelisio.exit.ui.components.ExitCard
import com.karelisio.exit.ui.components.ExitTopBar
import com.karelisio.exit.ui.components.Fmt
import com.karelisio.exit.ui.components.SectionTitle
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.components.SosFab
import com.karelisio.exit.ui.components.cascade
import com.karelisio.exit.ui.components.rememberNow
import com.karelisio.exit.ui.navigation.Navigator
import java.time.LocalDate
import java.time.ZoneId

private val BADGE_SHAPES: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie7Sided, MaterialShapes.Clover8Leaf, MaterialShapes.Sunny, MaterialShapes.Gem,
    MaterialShapes.Pentagon, MaterialShapes.SoftBurst, MaterialShapes.Flower, MaterialShapes.Puffy,
    MaterialShapes.Cookie9Sided, MaterialShapes.Bun, MaterialShapes.Clover4Leaf, MaterialShapes.VerySunny,
    MaterialShapes.Cookie6Sided, MaterialShapes.PuffyDiamond, MaterialShapes.Burst, MaterialShapes.Heart,
)

@Composable
fun ProgressScreen(vm: AppViewModel, nav: Navigator) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val c = d.config
    val zone = ZoneId.systemDefault()
    val now = rememberNow(60_000)
    val gains = remember(c, d.smokes, now) { GainsCalculator.compute(c, d.smokes, now, zone) }
    val streakStart = listOfNotNull(d.lastSmokeAt, c.quitStartedAt.takeIf { PlanEngine.phase(c) == Phase.QUIT }).maxOrNull()
    val streakMinutes = streakStart?.let { (now - it) / MINUTE_MS } ?: 0L
    val longest = remember(d.smokes, c.startedAt) {
        StatsCalculator.longestSmokeFreeDays(d.smokes.mapTo(HashSet()) { PlanEngine.day(it.timestamp, zone) }, PlanEngine.day(c.startedAt, zone), LocalDate.now())
    }
    val badges = remember(gains, d.cravings, d.events, streakMinutes, longest) {
        Badges.compute(
            BadgeInputs(
                cravingsOvercome = d.cravings.count { it.overcome },
                currentStreakMinutes = if (PlanEngine.phase(c) == Phase.QUIT) streakMinutes else 0,
                longestSmokeFreeDays = longest,
                money = gains.money,
                avoided = gains.avoided,
                quitEver = d.events.any { it.type == PlanEventType.QUIT.name },
                reboundEver = d.events.any { it.type == PlanEventType.RESUME_PLAN.name } ||
                    d.events.any { it.type == PlanEventType.SLIP.name } && PlanEngine.phase(c) == Phase.QUIT && streakMinutes > 24 * 60,
                onTimeCount = d.smokes.count { !it.early && !it.slip },
                bonusMinutes = d.smokes.sumOf { it.bonusMinutes },
            ),
        )
    }
    var openBadge by remember { mutableStateOf<Pair<Badge, RoundedPolygon>?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { ExitTopBar("Progrès", scroll) },
        floatingActionButton = { SosFab(nav::sos) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (PlanEngine.phase(c) == Phase.QUIT && streakStart != null) {
                item(key = "streak") {
                    ExitCard(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.cascade(0)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                AnimatedCounter(
                                    (streakMinutes / (24 * 60)).toString(),
                                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text("jours sans fumer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            ShapeIcon(Icons.Rounded.SmokeFree, MaterialShapes.Sunny, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, size = 72.dp)
                        }
                    }
                }
            }
            item(key = "gains") {
                Column(Modifier.cascade(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GainTile(Icons.Rounded.Savings, MaterialShapes.Cookie4Sided, "économisés", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f)) {
                            CountUpText(gains.money, Fmt::money, MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        GainTile(Icons.Rounded.SmokeFree, MaterialShapes.Clover4Leaf, "cigarettes évitées", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f)) {
                            CountUpText(gains.avoided.toDouble(), { Fmt.number(it.toInt()) }, MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GainTile(Icons.Rounded.Favorite, MaterialShapes.Heart, "de vie regagnés", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Modifier.weight(1f)) {
                            Text(Fmt.lifeTime(gains.lifeMinutes), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        GainTile(Icons.Rounded.EventAvailable, MaterialShapes.Sunny, "jours sans fumer", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)) {
                            CountUpText(gains.smokeFreeDays.toDouble(), { Fmt.number(it.toInt()) }, MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
                        }
                    }
                    Text(
                        "Depuis le début du suivi, par rapport à ${c.cigsPerDay} cigarettes par jour. Temps de vie : environ 20 minutes par cigarette évitée. " +
                            "Les jours sans fumer ne repartent jamais à zéro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            item(key = "piggy") { PiggyBank(c, gains.money, onSave = { label, amount -> vm.updateConfig { it.copy(savingsGoalLabel = label, savingsGoalAmount = amount) } }, modifier = Modifier.cascade(2)) }
            item(key = "healthTitle") {
                Column {
                    SectionTitle("Jalons santé")
                    Text(
                        if (streakStart == null) "Ils démarrent à ta dernière cigarette." else "Depuis ta dernière cigarette, il y a ${Fmt.duration(now - streakStart)}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            itemsIndexed(HealthMilestones.all, key = { _, m -> "m-${m.minutes}" }) { i, m ->
                MilestoneRow(m, streakMinutes, Modifier.cascade(i + 2))
            }
            item(key = "sources") {
                TextButton(onClick = nav::sources) {
                    Icon(Icons.Rounded.Info, contentDescription = null)
                    Text("  Sources : Tabac info service, OMS")
                }
            }
            item(key = "badgesTitle") {
                SectionTitle("Badges · ${badges.count { it.achieved }} / ${badges.size}")
            }
            item(key = "badges") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    badges.forEachIndexed { i, badge ->
                        val shape = BADGE_SHAPES[i % BADGE_SHAPES.size]
                        BadgeTile(badge, shape, Modifier.cascade(i).clickable { openBadge = badge to shape })
                    }
                }
            }
        }
    }

    openBadge?.let { (badge, shape) ->
        AlertDialog(
            onDismissRequest = { openBadge = null },
            icon = {
                ShapeIcon(
                    badgeIcon(badge.icon),
                    shape,
                    if (badge.achieved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    if (badge.achieved) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 72.dp,
                )
            },
            title = { Text(badge.title) },
            text = {
                Column {
                    Text(badge.description)
                    if (!badge.achieved) {
                        Spacer(Modifier.height(12.dp))
                        LinearWavyProgressIndicator(progress = { badge.progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(badge.progress * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { openBadge = null }) { Text("OK") } },
        )
    }
}

/** Objectif tirelire : ce que les économies vont offrir. */
@Composable
private fun PiggyBank(c: com.karelisio.exit.data.ConfigEntity, money: Double, onSave: (String, Double) -> Unit, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf(false) }
    val goal = c.savingsGoalAmount
    ExitCard(color = MaterialTheme.colorScheme.secondaryContainer, onClick = { editing = true }, modifier = modifier) {
        val on = MaterialTheme.colorScheme.onSecondaryContainer
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(Icons.Rounded.Savings, MaterialShapes.Bun, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (goal <= 0) {
                    Text("Un objectif tirelire ?", style = MaterialTheme.typography.titleMedium, color = on)
                    Text("Un week-end, un vélo, un cadeau… Touche pour le choisir.", style = MaterialTheme.typography.bodyMedium, color = on)
                } else {
                    Text(c.savingsGoalLabel.ifBlank { "Mon objectif" }, style = MaterialTheme.typography.titleMedium, color = on)
                    Text(
                        if (money >= goal) "Objectif atteint 🎉 Fais-toi plaisir !" else "${Fmt.money(money)} sur ${Fmt.money(goal)} · encore ${Fmt.money(goal - money)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = on,
                    )
                }
            }
        }
        if (goal > 0) {
            Spacer(Modifier.height(12.dp))
            LinearWavyProgressIndicator(progress = { (money / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (editing) {
        var label by remember { mutableStateOf(c.savingsGoalLabel) }
        var amount by remember { mutableStateOf(if (goal > 0) "%.0f".format(goal) else "") }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Objectif tirelire") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(label, { label = it.take(40) }, label = { Text("Pour quoi ?") }, singleLine = true)
                    androidx.compose.material3.OutlinedTextField(
                        amount,
                        { amount = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(7) },
                        label = { Text("Montant (€)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(label.trim(), amount.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0)
                    editing = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onSave("", 0.0)
                    editing = false
                }) { Text("Retirer") }
            },
        )
    }
}

@Composable
private fun GainTile(
    icon: ImageVector,
    shape: RoundedPolygon,
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    ExitCard(color = container, modifier = modifier) {
        ShapeIcon(icon, shape, content.copy(alpha = 0.12f), content, size = 44.dp)
        Spacer(Modifier.height(12.dp))
        value()
        Text(label, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.8f))
    }
}

@Composable
private fun MilestoneRow(m: HealthMilestone, minutes: Long, modifier: Modifier = Modifier) {
    val reached = minutes >= m.minutes
    val isNext = HealthMilestones.next(minutes) == m
    ExitCard(
        color = if (reached) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                when {
                    reached -> ShapeIcon(Icons.Rounded.Check, MaterialShapes.Cookie9Sided, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, size = 52.dp)
                    isNext -> {
                        CircularWavyProgressIndicator(progress = { HealthMilestones.progressToNext(minutes) }, modifier = Modifier.size(52.dp))
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                    else -> ShapeIcon(Icons.Rounded.Lock, MaterialShapes.Circle, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, size = 44.dp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("${m.label} · ${m.title}", style = MaterialTheme.typography.titleMedium)
                Text(m.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Source : ${m.source.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, shape: RoundedPolygon, modifier: Modifier = Modifier) {
    Column(modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ShapeIcon(
            badgeIcon(badge.icon),
            shape,
            if (badge.achieved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            if (badge.achieved) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
            size = 72.dp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            badge.title,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (badge.achieved) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        if (!badge.achieved && badge.progress > 0f) {
            Text("${(badge.progress * 100).toInt()} %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun badgeIcon(icon: BadgeIcon): ImageVector = when (icon) {
    BadgeIcon.SOS -> Icons.Rounded.Spa
    BadgeIcon.SHIELD -> Icons.Rounded.Shield
    BadgeIcon.STREAK -> Icons.Rounded.Whatshot
    BadgeIcon.MONEY -> Icons.Rounded.Savings
    BadgeIcon.LEAF -> Icons.Rounded.AutoAwesome
    BadgeIcon.FLAG -> Icons.Rounded.Flag
    BadgeIcon.REBOUND -> Icons.Rounded.Undo
    BadgeIcon.TIMER -> Icons.Rounded.Timer
    BadgeIcon.HEART -> Icons.Rounded.Favorite
}
