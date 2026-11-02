package com.karelisio.exit.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.exit.data.ConfigEntity
import com.karelisio.exit.data.SmokeLogged
import com.karelisio.exit.domain.DAY_MS
import com.karelisio.exit.domain.GainsCalculator
import com.karelisio.exit.domain.HealthMilestones
import com.karelisio.exit.domain.MINUTE_MS
import com.karelisio.exit.domain.Phase
import com.karelisio.exit.domain.PlanEngine
import com.karelisio.exit.domain.PlanSnapshot
import com.karelisio.exit.domain.Trigger
import com.karelisio.exit.ui.AppData
import com.karelisio.exit.ui.AppViewModel
import com.karelisio.exit.ui.components.AnimatedCounter
import com.karelisio.exit.ui.components.CountUpText
import com.karelisio.exit.ui.components.ExitCard
import com.karelisio.exit.ui.components.ExitTopBar
import com.karelisio.exit.ui.components.Fmt
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.components.SmokeBurst
import com.karelisio.exit.ui.components.SosFab
import com.karelisio.exit.ui.components.TimerRing
import com.karelisio.exit.ui.components.TriggerChips
import com.karelisio.exit.ui.components.cascade
import com.karelisio.exit.ui.components.rememberHaptics
import com.karelisio.exit.ui.components.rememberNow
import com.karelisio.exit.ui.navigation.Navigator
import com.karelisio.exit.ui.navigation.TopLevel
import com.karelisio.exit.ui.navigation.sharedContainer
import com.karelisio.exit.ui.theme.LocalReduceMotion
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlinx.coroutines.delay


@Composable
fun HomeScreen(vm: AppViewModel, nav: Navigator) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val c = d.config
    val zone = ZoneId.systemDefault()
    val now = rememberNow()
    val minute = now / MINUTE_MS
    val today = remember(minute) { LocalDate.now() }
    val todayCount = remember(d.smokes, today) { d.smokes.count { PlanEngine.day(it.timestamp, zone) == today } }
    val snapshot = remember(c, d.lastSmokeAt, todayCount, minute) { PlanEngine.snapshot(c, d.lastSmokeAt, todayCount, now, zone) }
    val gains = remember(c, d.smokes, minute) { GainsCalculator.compute(c, d.smokes, now, zone) }
    val haptics = rememberHaptics()

    val logged by vm.lastLogged.collectAsStateWithLifecycle()
    val slip by vm.pendingSlip.collectAsStateWithLifecycle()
    var confirmQuit by remember { mutableStateOf(false) }
    var burst by remember { mutableIntStateOf(0) }
    var celebration by remember { mutableStateOf<String?>(null) }

    // Jalon santé atteint pendant l'arrêt : volute qui se dissipe + haptique, une seule fois par jalon.
    val streakStart = listOfNotNull(d.lastSmokeAt, c.quitStartedAt).maxOrNull()
    val streakMinutes = streakStart?.let { (now - it) / MINUTE_MS } ?: 0
    val reached = if (snapshot.phase == Phase.QUIT) HealthMilestones.reached(streakMinutes).lastOrNull() else null
    LaunchedEffect(reached?.minutes, c.lastCelebratedMilestoneMinutes) {
        if (reached != null && reached.minutes > c.lastCelebratedMilestoneMinutes) {
            vm.markMilestoneCelebrated(reached.minutes)
            celebration = "${reached.label} sans fumer · ${reached.title}"
            burst++
            haptics.success()
        }
    }

    // Petite vibration quand le minuteur arrive à zéro, écran ouvert.
    val ok = snapshot.isOk(now)
    var wasOk by remember { mutableStateOf(ok) }
    LaunchedEffect(ok) {
        if (ok && !wasOk) haptics.success()
        wasOk = ok
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hour = remember(minute) { LocalTime.now().hour }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0),
            topBar = {
                ExitTopBar(Fmt.greeting(hour), scroll, actions = {
                    IconButton(onClick = nav::settings) { Icon(Icons.Rounded.Settings, contentDescription = "Réglages") }
                })
            },
            floatingActionButton = { SosFab(nav::sos) },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                c.adjustmentMessage?.let { message ->
                    item(key = "adjust") {
                        AdjustmentCard(message, onDismiss = vm::dismissAdjustment, onOpen = nav::plan, modifier = Modifier.cascade(0).animateItem())
                    }
                }
                if (snapshot.phase == Phase.SPACING) {
                    item(key = "hero") {
                        Column(Modifier.cascade(1).animateItem(), horizontalAlignment = Alignment.CenterHorizontally) {
                            TimerHero(snapshot, now)
                            Spacer(Modifier.height(12.dp))
                            InfoPills(snapshot, todayCount)
                            snapshot.morningGoalAt?.takeIf { it > now }?.let {
                                Spacer(Modifier.height(8.dp))
                                Pill("☀️ Première du matin : pas avant ${Fmt.time(it)}")
                            }
                            Spacer(Modifier.height(16.dp))
                            SmokeButton {
                                haptics.confirm()
                                vm.logSmoke()
                            }
                        }
                    }
                    item(key = "logged") {
                        LoggedCard(logged, vm, onClose = vm::clearLogged, modifier = Modifier.animateItem())
                    }
                    if (snapshot.reachedZero) {
                        item(key = "ready") { ReadyToQuitCard(onQuit = { confirmQuit = true }, modifier = Modifier.cascade(2).animateItem()) }
                    }
                } else {
                    item(key = "quitHero") {
                        QuitHero(streakStart, now, onSlip = {
                            haptics.tick()
                            vm.logSmoke()
                        }, modifier = Modifier.cascade(1).animateItem())
                    }
                }
                val dow = LocalDate.now().dayOfWeek
                if (dow == java.time.DayOfWeek.SUNDAY || dow == java.time.DayOfWeek.MONDAY) {
                    item(key = "review") {
                        ExitCard(color = MaterialTheme.colorScheme.tertiaryContainer, onClick = nav::review, modifier = Modifier.cascade(2).animateItem()) {
                            Text("Ton bilan de la semaine est prêt", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Envies surmontées, bonus tenu, économies : viens voir.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
                item(key = "plan") { PlanCard(c, snapshot, d, onClick = nav::plan, modifier = Modifier.cascade(3).animateItem()) }
                item(key = "gains") {
                    ExitCard(onClick = { nav.tab(TopLevel.PROGRESS) }, modifier = Modifier.cascade(4).animateItem()) {
                        Text("Tes gains", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            GainColumn("économisés") {
                                CountUpText(gains.money, Fmt::money, MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.primary)
                            }
                            GainColumn("évitées") {
                                CountUpText(gains.avoided.toDouble(), { Fmt.number(it.toInt()) }, MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.secondary)
                            }
                            GainColumn("de vie") {
                                Text(Fmt.lifeTime(gains.lifeMinutes), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
                if (snapshot.phase == Phase.SPACING && d.lastSmokeAt != null) {
                    item(key = "milestone") {
                        NextMilestoneCard((now - d.lastSmokeAt!!) / MINUTE_MS, title = "Depuis ta dernière cigarette", modifier = Modifier.cascade(5).animateItem())
                    }
                }
                item(key = "cravings") {
                    val week = d.cravings.count { it.overcome && it.timestamp > now - 7 * DAY_MS }
                    val total = d.cravings.count { it.overcome }
                    ExitCard(color = MaterialTheme.colorScheme.secondaryContainer, onClick = nav::sos, modifier = Modifier.cascade(6).animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShapeIcon(Icons.Rounded.Spa, MaterialShapes.Clover4Leaf, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (total == 0) "Une envie ? Le mode SOS t'aide à la laisser passer" else "$total envie${if (total > 1) "s" else ""} surmontée${if (total > 1) "s" else ""}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                if (total > 0) {
                                    Text(
                                        "dont $week cette semaine. Chacune compte.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SmokeBurst(burst, Modifier.fillMaxSize(), onFinished = { })
        AnimatedVisibility(
            visible = celebration != null,
            enter = scaleIn(initialScale = 0.6f) + fadeIn(),
            exit = scaleOut(targetScale = 1.1f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
        ) {
            ExitCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Text("🎉", style = MaterialTheme.typography.displaySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(
                    celebration.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LaunchedEffect(celebration) {
                delay(3500)
                celebration = null
            }
        }
    }

    slip?.let { s -> SlipSheet(s, d, onDismiss = vm::clearSlip, vm = vm) }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            icon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
            title = { Text("Passer à l'arrêt complet ?") },
            text = { Text("Le minuteur laisse place au compteur de jours sans fumer. Si un écart arrive, rien n'est perdu : on en parle, sans jugement.") },
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
}

@Composable
private fun TimerHero(snapshot: PlanSnapshot, now: Long) {
    val ok = snapshot.isOk(now)
    TimerRing(
        progress = snapshot.progress(now),
        ok = ok,
        modifier = Modifier.fillMaxWidth(0.82f).clearAndSetSemantics {
            contentDescription = if (ok) "C'est OK si tu en as besoin" else "Prochaine cigarette autorisée à ${snapshot.nextAllowedAt?.let { Fmt.time(it) }}"
        },
    ) {
        AnimatedContent(
            targetState = ok,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut() + scaleOut(targetScale = 1.1f)) },
            label = "heroText",
        ) { isOk ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isOk) {
                    Text(
                        if (snapshot.lastSmokeAt == null) "Prêt·e" else "C'est OK",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        if (snapshot.lastSmokeAt == null) "note ta prochaine cigarette" else "si tu en as besoin",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                } else {
                    Text("prochaine dans", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    AnimatedCounter(
                        Fmt.countdown(snapshot.remainingMillis(now)),
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    snapshot.nextAllowedAt?.let {
                        Text("à ${Fmt.time(it)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPills(snapshot: PlanSnapshot, todayCount: Int) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill("Intervalle ${PlanEngine.formatDuration(snapshot.intervalMinutes)}")
        val target = ceil(snapshot.targetToday).toInt()
        Pill("Aujourd'hui $todayCount" + if (target > 0) " / $target" else "")
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun SmokeButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().height(76.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text("J'en fume une", style = MaterialTheme.typography.titleLarge)
    }
}

/** Confirmation après « J'en fume une » : heure, en avance ou non, et contexte optionnel. */
@Composable
private fun LoggedCard(logged: SmokeLogged?, vm: AppViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = logged != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val current = logged ?: return@AnimatedVisibility
        var trigger by remember(current.smoke.id) { mutableStateOf<Trigger?>(null) }
        var note by remember(current.smoke.id) { mutableStateOf("") }
        var touched by remember(current.smoke.id) { mutableStateOf(false) }
        LaunchedEffect(current.smoke.id, touched) {
            delay(if (touched) 20_000 else 12_000)
            onClose()
        }
        ExitCard(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notée à ${Fmt.time(current.smoke.timestamp)}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (current.minutesEarly > 0) {
                            "${PlanEngine.formatDuration(current.minutesEarly)} en avance. Pas grave, on continue ensemble."
                        } else if (current.smoke.bonusMinutes > 0) {
                            "Tu as tenu ${PlanEngine.formatDuration(current.smoke.bonusMinutes)} de plus que prévu. Bravo 💪"
                        } else {
                            "Merci de l'avoir notée. Le minuteur repart."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Fermer") }
            }
            Spacer(Modifier.height(10.dp))
            Text("Un contexte ? (facultatif)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            TriggerChips(
                selected = trigger,
                note = note,
                onSelect = {
                    touched = true
                    trigger = it
                    vm.setSmokeContext(current.smoke.id, it?.name, note.takeIf { n -> n.isNotBlank() && it == Trigger.PERSO })
                },
                onNote = {
                    touched = true
                    note = it
                    vm.setSmokeContext(current.smoke.id, trigger?.name, it.takeIf { n -> n.isNotBlank() })
                },
            )
        }
    }
}

@Composable
private fun AdjustmentCard(message: String, onDismiss: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    ExitCard(color = MaterialTheme.colorScheme.tertiaryContainer, onClick = onOpen, modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            ShapeIcon(Icons.Rounded.Favorite, MaterialShapes.Heart, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Plan réajusté en douceur", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Compris") }
        }
    }
}

@Composable
private fun ReadyToQuitCard(onQuit: () -> Unit, modifier: Modifier = Modifier) {
    ExitCard(color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier) {
        Text("Tu y es presque 🌱", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(6.dp))
        Text(
            "Ton plan arrive à zéro. Quand tu te sens prêt·e, passe à l'arrêt complet : le compteur de jours sans fumer prend le relais.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onQuit, shapes = ButtonDefaults.shapes()) { Text("Passer à l'arrêt complet") }
    }
}

@Composable
private fun QuitHero(streakStart: Long?, now: Long, onSlip: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val elapsed = streakStart?.let { now - it } ?: 0L
    val days = elapsed / DAY_MS
    val rest = elapsed % DAY_MS
    val spin by rememberInfiniteTransition(label = "quit").animateFloat(0f, 360f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "spin")
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(0.78f).padding(8.dp), contentAlignment = Alignment.Center) {
            val primaryContainer = MaterialTheme.colorScheme.primaryContainer
            Canvas(Modifier.fillMaxWidth().height(260.dp)) {
                val s = minOf(size.width, size.height)
                val path = com.karelisio.exit.ui.components.composePathOf(MaterialShapes.Cookie9Sided, androidx.compose.ui.geometry.Size(s, s), if (reduce) 0f else spin)
                translate((size.width - s) / 2, (size.height - s) / 2) { drawPath(path, primaryContainer) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedCounter(
                    days.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(if (days <= 1) "jour sans fumer" else "jours sans fumer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "+ ${Fmt.clock(rest)}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        NextMilestoneCard(elapsed / MINUTE_MS, title = "Ton corps se répare")
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onSlip, shapes = ButtonDefaults.shapes()) { Text("J'ai fumé une cigarette") }
    }
}

@Composable
private fun NextMilestoneCard(minutes: Long, title: String, modifier: Modifier = Modifier) {
    val next = HealthMilestones.next(minutes)
    ExitCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        if (next == null) {
            Text("Tous les jalons sont atteints. Respect.", style = MaterialTheme.typography.titleMedium)
        } else {
            Text("Prochain jalon : ${next.label} · ${next.title}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            LinearWavyProgressIndicator(progress = { HealthMilestones.progressToNext(minutes) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(next.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanCard(c: ConfigEntity, snapshot: PlanSnapshot, d: AppData, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val curve = remember(c) { PlanEngine.curve(c, zone, 120) }
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    ExitCard(onClick = onClick, modifier = modifier.sharedContainer("plan")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(Icons.Rounded.Route, MaterialShapes.Pill, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, size = 40.dp, rotationDeg = -30f)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Ton plan", style = MaterialTheme.typography.titleMedium)
                val today = LocalDate.now()
                val subtitle = when {
                    snapshot.phase == Phase.QUIT -> "Arrêt complet depuis le ${c.quitStartedAt?.let { Fmt.dayShort(PlanEngine.day(it, zone)) } ?: "—"}"
                    snapshot.quitDay != null -> {
                        val left = ChronoUnit.DAYS.between(today, snapshot.quitDay)
                        "Arrêt le ${Fmt.dayShort(snapshot.quitDay)}" + if (left > 0) " · J-$left" else " · c'est le jour"
                    }
                    snapshot.projectedQuitDay != null -> "On espace d'abord · zéro vers le ${Fmt.dayShort(snapshot.projectedQuitDay)}"
                    else -> "On espace d'abord"
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (snapshot.phase == Phase.SPACING && curve.size > 1) {
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                val max = curve.maxOf { it.second }.coerceAtLeast(1.0)
                val step = size.width / (curve.size - 1)
                val path = Path()
                curve.forEachIndexed { i, (_, v) ->
                    val x = i * step
                    val y = size.height - (v / max * size.height).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, track, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                val idx = ChronoUnit.DAYS.between(curve.first().first, LocalDate.now()).toInt().coerceIn(0, curve.size - 1)
                val x = idx * step
                val y = size.height - (curve[idx].second / max * size.height).toFloat()
                drawCircle(primary, 6.dp.toPx(), Offset(x, y))
            }
        }
    }
}

@Composable
private fun GainColumn(label: String, value: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.Start) {
        value()
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Écart pendant l'arrêt : bienveillance, rien n'est perdu, et on choisit la suite. */
@Composable
private fun SlipSheet(slip: SmokeLogged, d: AppData, onDismiss: () -> Unit, vm: AppViewModel) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = System.currentTimeMillis()
    val gains = remember(d) { GainsCalculator.compute(d.config, d.smokes, now, ZoneId.systemDefault()) }
    ModalBottomSheet(onDismissRequest = {
        vm.continueQuitAfterSlip()
        onDismiss()
    }, sheetState = state) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ShapeIcon(Icons.Rounded.Favorite, MaterialShapes.Heart, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text("Ça arrive. Ça ne t'enlève rien.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tes ${gains.smokeFreeDays} jours sans fumer, tes ${Fmt.money(gains.money)} économisés et tes badges restent acquis. " +
                    "Une cigarette n'efface pas le chemin parcouru. Comment veux-tu continuer ?",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                vm.continueQuitAfterSlip()
                onDismiss()
            }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Je continue mon arrêt") }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = {
                vm.resumePlanAfterSlip()
                onDismiss()
            }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Revenir au plan d'espacement") }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = {
                vm.deleteSmoke(slip.smoke)
                onDismiss()
            }) { Text("Je me suis trompé·e, annuler") }
        }
    }
}
