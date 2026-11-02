package com.karelisio.exit.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.karelisio.exit.domain.StepPeriod
import com.karelisio.exit.system.ExitPermission
import com.karelisio.exit.system.Permissions
import com.karelisio.exit.ui.AppViewModel
import com.karelisio.exit.ui.components.AnimatedCounter
import com.karelisio.exit.ui.components.CountUpText
import com.karelisio.exit.ui.components.ExitLogo
import com.karelisio.exit.ui.components.Fmt
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.components.SmokeBurst
import com.karelisio.exit.ui.components.TimerRing
import com.karelisio.exit.ui.components.composePath
import com.karelisio.exit.ui.components.rememberHaptics
import com.karelisio.exit.ui.settings.GrantButton
import com.karelisio.exit.ui.settings.icon
import com.karelisio.exit.ui.settings.rememberPermissionController
import com.karelisio.exit.ui.theme.LocalReduceMotion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class Page { WELCOME, SPACING, SOS, CONSUMPTION, GOAL, REASONS, NOTIFICATIONS, ALARMS, BATTERY, INSTALL, READY }

private val Page.permission: ExitPermission?
    get() = when (this) {
        Page.NOTIFICATIONS -> ExitPermission.NOTIFICATIONS
        Page.ALARMS -> ExitPermission.EXACT_ALARMS
        Page.BATTERY -> ExitPermission.BATTERY
        Page.INSTALL -> ExitPermission.INSTALL
        else -> null
    }

/**
 * Premier lancement : présentation animée, configuration initiale, puis une page par autorisation
 * (icône animée, explication courte, « Autoriser », coche animée une fois accordée).
 */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val pages = Page.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val permissions = rememberPermissionController()

    var cigsPerDay by rememberSaveable { mutableIntStateOf(15) }
    var price by rememberSaveable { mutableStateOf("13,00") }
    var perPack by rememberSaveable { mutableIntStateOf(20) }
    var hasDate by rememberSaveable { mutableStateOf(true) }
    var quitEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(42).toEpochDay()) }
    var step by rememberSaveable { mutableStateOf(StepPeriod.DAY) }
    var stepMinutes by rememberSaveable { mutableIntStateOf(5) }
    var reasons by rememberSaveable { mutableStateOf("") }
    val priceValue = price.replace(',', '.').toDoubleOrNull()

    val current = pages[pager.currentPage]
    val canGoOn = current != Page.CONSUMPTION || (priceValue != null && priceValue in 0.0..100.0)
    val finish = {
        haptics.success()
        vm.completeOnboarding(cigsPerDay, priceValue ?: 13.0, perPack, if (hasDate) LocalDate.ofEpochDay(quitEpochDay) else null, step, stepMinutes, reasons)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(current.permission != null && !permissions.isGranted(current.permission!!), enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) { Text("Plus tard") }
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            userScrollEnabled = canGoOn,
        ) { index ->
            val page = pages[index]
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                        alpha = 1f - offset * 0.7f
                        val s = 1f - offset * 0.08f
                        scaleX = s
                        scaleY = s
                    },
            ) {
                when (page) {
                    Page.WELCOME -> Intro(
                        illustration = { ExitLogo(Modifier.size(240.dp)) },
                        title = "Exit",
                        body = "Espacer les cigarettes, puis arrêter.\nÀ ton rythme, sans culpabiliser.",
                    )
                    Page.SPACING -> Intro(
                        illustration = { DemoRing() },
                        title = "Un minuteur doux",
                        body = "Entre deux cigarettes, un intervalle minimum qui s'allonge petit à petit. Tu peux fumer avant : c'est simplement noté, jamais bloqué.",
                    )
                    Page.SOS -> Intro(
                        illustration = { DemoBreathing() },
                        title = "Une envie passe",
                        body = "Elle dure 3 à 5 minutes. Le mode SOS t'accompagne avec une respiration guidée et une petite distraction. Chaque victoire est célébrée.",
                    )
                    Page.CONSUMPTION -> Consumption(
                        cigsPerDay, { cigsPerDay = it.coerceIn(1, 80) },
                        price, { price = it },
                        perPack, { perPack = it.coerceIn(1, 50) },
                        priceValue,
                    )
                    Page.GOAL -> Goal(
                        hasDate, { hasDate = it },
                        quitEpochDay, { quitEpochDay = it },
                        step, { step = it },
                        stepMinutes, { stepMinutes = it },
                    )
                    Page.REASONS -> Reasons(reasons) { reasons = it }
                    Page.NOTIFICATIONS, Page.ALARMS, Page.BATTERY, Page.INSTALL -> {
                        val p = page.permission!!
                        PermissionPage(p, permissions.isGranted(p), onRequest = { permissions.request(p) })
                    }
                    Page.READY -> Ready(active = pager.currentPage == index, cigsPerDay = cigsPerDay, hasDate = hasDate, quitEpochDay = quitEpochDay)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Indicator(pages.size, pager.currentPage, Modifier.weight(1f))
            val last = pager.currentPage == pages.lastIndex
            Button(
                onClick = {
                    haptics.tick()
                    if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                enabled = canGoOn,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.height(56.dp),
            ) {
                AnimatedContent(last, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "next") { l ->
                    Text(if (l) "C'est parti" else "Suivant", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun Indicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics { contentDescription = "Page ${current + 1} sur $count" }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, spring(dampingRatio = 0.6f, stiffness = 400f), label = "dot")
            val color by animateColorAsState(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dotColor")
            Box(Modifier.size(width, 8.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun Intro(illustration: @Composable () -> Unit, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) { illustration() }
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DemoRing() {
    val reduce = LocalReduceMotion.current
    val t by rememberInfiniteTransition(label = "demo").animateFloat(
        0f, 1.25f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "t",
    )
    val p = if (reduce) 0.65f else t.coerceAtMost(1f)
    TimerRing(progress = p, ok = p >= 1f, modifier = Modifier.size(260.dp)) {
        Text(
            if (p >= 1f) "C'est OK" else Fmt.countdown(((1f - p) * 90 * 60_000).toLong()),
            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun DemoBreathing() {
    val reduce = LocalReduceMotion.current
    val morph = remember { Morph(MaterialShapes.Cookie6Sided, MaterialShapes.Flower) }
    val loop = rememberInfiniteTransition(label = "demoBreath")
    val b by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse), label = "b")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(50_000, easing = LinearEasing)), label = "spin")
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    val v = if (reduce) 0.6f else b
    Canvas(Modifier.size(260.dp)) {
        val path = morph.composePath(v, size, scale = 0.55f + 0.4f * v, rotationDeg = if (reduce) 0f else spin)
        drawPath(path, Brush.radialGradient(listOf(container, primary), center = center, radius = size.minDimension * 0.5f))
    }
}

@Composable
private fun PageTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Consumption(
    cigsPerDay: Int, onCigs: (Int) -> Unit,
    price: String, onPrice: (String) -> Unit,
    perPack: Int, onPerPack: (Int) -> Unit,
    priceValue: Double?,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        PageTitle("Ta consommation", "Une moyenne honnête suffit. Tu pourras tout changer plus tard.")
        Stepper("Cigarettes par jour", cigsPerDay, onCigs, MaterialShapes.Cookie9Sided)
        Spacer(Modifier.height(16.dp))
        Stepper("Cigarettes par paquet", perPack, onPerPack, MaterialShapes.Clover4Leaf)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = price,
            onValueChange = { onPrice(it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6)) },
            label = { Text("Prix du paquet (€)") },
            isError = priceValue == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        if (priceValue != null && perPack > 0) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Aujourd'hui, ça représente", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    CountUpText(
                        cigsPerDay * 365.0 * priceValue / perPack,
                        { "${Fmt.money(it)} par an" },
                        MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text("Chaque cigarette évitée t'en rend une partie.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, shape: RoundedPolygon) {
    val haptics = rememberHaptics()
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { haptics.tick(); onChange(value - 1) }) { Icon(Icons.Rounded.Remove, contentDescription = "$label : moins") }
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                ShapeIcon(Icons.Rounded.Add, shape, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer, size = 60.dp)
                AnimatedCounter(value.toString(), MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            FilledTonalIconButton(onClick = { haptics.tick(); onChange(value + 1) }) { Icon(Icons.Rounded.Add, contentDescription = "$label : plus") }
        }
    }
}

@Composable
private fun Goal(
    hasDate: Boolean, onHasDate: (Boolean) -> Unit,
    quitEpochDay: Long, onQuitDay: (Long) -> Unit,
    step: StepPeriod, onStep: (StepPeriod) -> Unit,
    stepMinutes: Int, onStepMinutes: (Int) -> Unit,
) {
    var pick by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        PageTitle("Ton objectif", "Une date motive certains, d'autres préfèrent d'abord espacer. Les deux marchent.")
        Choice(
            selected = hasDate,
            icon = Icons.Rounded.CalendarMonth,
            shape = MaterialShapes.Cookie12Sided,
            title = "Choisir une date d'arrêt",
            body = "Exit calcule la courbe de réduction jusqu'à ce jour et l'ajuste si besoin.",
            onClick = { onHasDate(true) },
        )
        AnimatedVisibility(hasDate, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            FilledTonalButton(onClick = { pick = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Arrêt le ${Fmt.dayFull(LocalDate.ofEpochDay(quitEpochDay))}")
            }
        }
        Spacer(Modifier.height(12.dp))
        Choice(
            selected = !hasDate,
            icon = Icons.Rounded.Timelapse,
            shape = MaterialShapes.Flower,
            title = "Pas de date, on espace d'abord",
            body = "L'intervalle entre deux cigarettes augmente un peu à chaque palier.",
            onClick = { onHasDate(false) },
        )
        Spacer(Modifier.height(20.dp))
        Text("Paliers", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StepPeriod.entries.forEachIndexed { i, p ->
                SegmentedButton(selected = step == p, onClick = { onStep(p) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                    Text(if (p == StepPeriod.DAY) "Chaque jour" else "Chaque semaine")
                }
            }
        }
        AnimatedVisibility(!hasDate, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 12.dp)) {
                Text("+$stepMinutes min par palier", style = MaterialTheme.typography.bodyLarge)
                Slider(stepMinutes.toFloat(), { onStepMinutes(it.roundToInt()) }, valueRange = 1f..60f, steps = 58)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (pick) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(quitEpochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() > LocalDate.now()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pick = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onQuitDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                    pick = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pick = false }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }
}

private val REASON_IDEAS = listOf(
    "Mieux respirer", "Ma santé", "Mes proches", "Économiser", "Retrouver le goût et l'odorat",
    "Me sentir libre", "Faire du sport", "Montrer l'exemple",
)

/** Mes raisons d'arrêter : rappelées dans le mode SOS et dans les notifications. */
@Composable
private fun Reasons(text: String, onChange: (String) -> Unit) {
    val haptics = rememberHaptics()
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        PageTitle("Tes raisons", "Pourquoi tu veux arrêter ? Exit te les rappellera au moment où une envie arrive. Facultatif, modifiable plus tard.")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REASON_IDEAS.forEach { idea ->
                androidx.compose.material3.FilterChip(
                    selected = idea in lines,
                    onClick = {
                        haptics.tick()
                        onChange((if (idea in lines) lines - idea else lines + idea).joinToString("\n"))
                    },
                    label = { Text(idea) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { onChange(it.take(500)) },
            label = { Text("Une raison par ligne") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Choice(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    shape: RoundedPolygon,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "choice")
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, label = "border")
    val haptics = rememberHaptics()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(2.dp, border, MaterialTheme.shapes.large)
            .selectable(selected = selected, role = Role.RadioButton) {
                haptics.tick()
                onClick()
            },
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(icon, shape, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PermissionPage(permission: ExitPermission, granted: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    val reduce = LocalReduceMotion.current
    val loop = rememberInfiniteTransition(label = "perm")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "spin")
    val pulse by loop.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val shape = when (permission) {
        ExitPermission.NOTIFICATIONS -> MaterialShapes.Cookie9Sided
        ExitPermission.EXACT_ALARMS -> MaterialShapes.Clover8Leaf
        ExitPermission.BATTERY -> MaterialShapes.Sunny
        ExitPermission.INSTALL -> MaterialShapes.SoftBurst
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(200.dp).graphicsLayer {
                val s = if (reduce || granted) 1f else pulse
                scaleX = s
                scaleY = s
            },
            contentAlignment = Alignment.Center,
        ) {
            ShapeIcon(
                permission.icon,
                shape,
                if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                if (granted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                size = 180.dp,
                rotationDeg = if (reduce) 0f else spin,
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(permission.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(permission.rationale, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(permission.detail, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        GrantButton(granted, onRequest)
        if (permission == ExitPermission.BATTERY && Permissions.isHonorOrHuawei) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { Permissions.openLaunchManager(context) }) { Text("Ouvrir « Lancement d'applis »") }
        }
    }
}

@Composable
private fun Ready(active: Boolean, cigsPerDay: Int, hasDate: Boolean, quitEpochDay: Long) {
    var burst by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) { if (active) burst++ }
    Box(Modifier.fillMaxSize()) {
        Intro(
            illustration = {
                val spin by rememberInfiniteTransition(label = "ready").animateFloat(0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "s")
                ShapeIcon(
                    Icons.Rounded.Add,
                    MaterialShapes.VerySunny,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                    size = 220.dp,
                    rotationDeg = if (LocalReduceMotion.current) 0f else spin,
                )
                ExitLogo(Modifier.size(150.dp))
            },
            title = "Tout est prêt",
            body = buildString {
                append("On part de $cigsPerDay cigarettes par jour")
                append(if (hasDate) ", jusqu'au ${Fmt.dayFull(LocalDate.ofEpochDay(quitEpochDay))}." else ", et on espace d'abord.")
                append("\nAjoute le widget Exit sur ton écran d'accueil pour noter une cigarette en un geste.")
            },
        )
        SmokeBurst(burst, Modifier.fillMaxSize())
    }
}
