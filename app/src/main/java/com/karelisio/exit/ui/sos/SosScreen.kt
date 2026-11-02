package com.karelisio.exit.ui.sos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.exit.domain.Trigger
import com.karelisio.exit.ui.AppViewModel
import com.karelisio.exit.ui.components.Fmt
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.components.SmokeBurst
import com.karelisio.exit.ui.components.TriggerChips
import com.karelisio.exit.ui.components.composePath
import com.karelisio.exit.ui.components.rememberHaptics
import com.karelisio.exit.ui.components.rememberNow
import com.karelisio.exit.ui.navigation.Navigator
import com.karelisio.exit.ui.navigation.sharedContainer
import com.karelisio.exit.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

private const val CRAVING_MS = 5 * 60 * 1000L

private enum class SosStep { RUNNING, DONE }

private data class Rhythm(val inhale: Float, val exhale: Float, val label: String)

private val RHYTHMS = listOf(
    Rhythm(5f, 5f, "Cohérence 5·5"),
    Rhythm(4f, 6f, "Apaisant 4·6"),
    Rhythm(4f, 4f, "Court 4·4"),
    Rhythm(6f, 6f, "Lent 6·6"),
)

private val IDEAS = listOf(
    "Bois un grand verre d'eau, lentement.",
    "Passe-toi de l'eau froide sur le visage et les mains.",
    "Marche deux minutes, même dans la pièce.",
    "Écris en une phrase pourquoi tu veux arrêter.",
    "Envoie un message à quelqu'un que tu aimes.",
    "Mâche un chewing-gum ou croque une pomme.",
    "Fais 10 squats ou 10 pompes contre un mur.",
    "Range un tiroir ou ta table pendant 3 minutes.",
    "Brosse-toi les dents : le goût change tout.",
    "Mets une chanson que tu adores et écoute-la en entier.",
    "Compte à rebours de 100 de 7 en 7.",
    "Regarde par la fenêtre et nomme 5 choses que tu vois.",
)

/**
 * Mode SOS envie : respiration guidée (forme M3 qui gonfle, dégonfle et morphe au rythme),
 * minuteur de 5 minutes, petite distraction. Chaque envie surmontée est comptée et célébrée.
 */
@Composable
fun SosScreen(vm: AppViewModel, nav: Navigator) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val c = d.config
    val haptics = rememberHaptics()
    var step by rememberSaveable { mutableStateOf(SosStep.RUNNING) }
    var startedAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var trigger by rememberSaveable { mutableStateOf<Trigger?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var burst by remember { mutableIntStateOf(0) }
    var victories by rememberSaveable { mutableIntStateOf(0) }

    // L'écran reste allumé pendant l'exercice.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val seconds = { ((System.currentTimeMillis() - startedAt) / 1000).toInt() }
    val overcome = {
        victories = d.cravings.count { it.overcome } + 1
        vm.logCraving(true, trigger?.name, note.takeIf { trigger == Trigger.PERSO && it.isNotBlank() }, seconds())
        haptics.success()
        burst++
        step = SosStep.DONE
    }

    Surface(
        modifier = Modifier.fillMaxSize().sharedContainer("sos", MaterialTheme.shapes.large),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.Rounded.Close, contentDescription = "Fermer") }
                    Text("SOS envie", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut() + scaleOut(targetScale = 1.05f)) },
                    label = "sos",
                    modifier = Modifier.weight(1f),
                ) { s ->
                    when (s) {
                        SosStep.RUNNING -> Running(
                            startedAt = startedAt,
                            inhale = c.breathInhaleSeconds,
                            exhale = c.breathExhaleSeconds,
                            hapticsOn = c.breathHaptics,
                            trigger = trigger,
                            note = note,
                            onTrigger = { trigger = it },
                            onNote = { note = it },
                            onRhythm = { r -> vm.updateConfig { it.copy(breathInhaleSeconds = r.inhale, breathExhaleSeconds = r.exhale) } },
                            onOvercome = overcome,
                            onGaveIn = {
                                vm.logCraving(false, trigger?.name, note.takeIf { trigger == Trigger.PERSO && it.isNotBlank() }, seconds(), alsoSmoke = true)
                                nav.back()
                            },
                        )
                        SosStep.DONE -> Done(
                            victories = victories,
                            onBack = { nav.back() },
                            onAgain = {
                                startedAt = System.currentTimeMillis()
                                step = SosStep.RUNNING
                            },
                        )
                    }
                }
            }
            SmokeBurst(burst, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Running(
    startedAt: Long,
    inhale: Float,
    exhale: Float,
    hapticsOn: Boolean,
    trigger: Trigger?,
    note: String,
    onTrigger: (Trigger?) -> Unit,
    onNote: (String) -> Unit,
    onRhythm: (Rhythm) -> Unit,
    onOvercome: () -> Unit,
    onGaveIn: () -> Unit,
) {
    val now = rememberNow(250)
    val remaining = (CRAVING_MS - (now - startedAt)).coerceAtLeast(0)
    val haptics = rememberHaptics()
    var finishedNotified by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(remaining == 0L) {
        if (remaining == 0L && !finishedNotified) {
            finishedNotified = true
            haptics.success()
        }
    }
    var showContext by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (remaining > 0) "Ça va passer." else "5 minutes. Tu as tenu.",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            if (remaining > 0) "Une envie dure 3 à 5 minutes. Respire avec la forme." else "L'envie a sûrement baissé. Comment ça va ?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Breathing(inhale, exhale, hapticsOn, Modifier.fillMaxWidth(0.82f))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RHYTHMS.forEach { r ->
                FilterChip(
                    selected = r.inhale == inhale && r.exhale == exhale,
                    onClick = { onRhythm(r) },
                    label = { Text(r.label) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearWavyProgressIndicator(
                progress = { 1f - remaining / CRAVING_MS.toFloat() },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(Fmt.countdown(remaining), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onOvercome,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { Text("L'envie est passée 💪", style = MaterialTheme.typography.titleMedium) }
        TextButton(onClick = onGaveIn) { Text("J'ai craqué, ce n'est pas grave") }

        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Petit jeu", "Idées").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tab == i,
                    onClick = { tab = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(tab, label = "distraction") { t ->
            if (t == 0) BubbleGame(Modifier.fillMaxWidth().height(240.dp)) else Ideas()
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { showContext = !showContext }) {
            Text("Qu'est-ce qui l'a déclenchée ? (facultatif)")
            Icon(if (showContext) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(showContext, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            TriggerChips(trigger, note, onTrigger, onNote, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Animation signature n° 3 : la forme gonfle à l'inspiration et morphe d'un cookie vers une fleur,
 * puis se dégonfle à l'expiration. Haptique doux à chaque changement de phase.
 */
@Composable
private fun Breathing(inhale: Float, exhale: Float, hapticsOn: Boolean, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val morph = remember { Morph(MaterialShapes.Cookie6Sided, MaterialShapes.Flower) }
    val halo = remember { Morph(MaterialShapes.Circle, MaterialShapes.SoftBurst) }
    val breath = remember { Animatable(0f) }
    var inhaling by remember { mutableStateOf(true) }
    var phaseStart by remember { mutableStateOf(System.currentTimeMillis()) }
    val inMs = (inhale * 1000).toInt()
    val outMs = (exhale * 1000).toInt()
    LaunchedEffect(inMs, outMs, reduce) {
        while (true) {
            inhaling = true
            phaseStart = System.currentTimeMillis()
            if (hapticsOn) haptics.breatheIn()
            if (reduce) {
                breath.snapTo(1f)
                delay(inMs.toLong())
            } else {
                breath.animateTo(1f, tween(inMs, easing = EaseInOutSine))
            }
            inhaling = false
            phaseStart = System.currentTimeMillis()
            if (hapticsOn) haptics.breatheOut()
            if (reduce) {
                breath.snapTo(0f)
                delay(outMs.toLong())
            } else {
                breath.animateTo(0f, tween(outMs, easing = EaseInOutSine))
            }
        }
    }
    val spin by rememberInfiniteTransition(label = "breathSpin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "spin",
    )
    val now = rememberNow(200)
    val phaseLeft = ((if (inhaling) inMs else outMs) - (now - phaseStart)).coerceAtLeast(0) / 1000 + 1

    val scheme = MaterialTheme.colorScheme
    Box(modifier.height(300.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val b = breath.value
            val side = minOf(size.width, size.height)
            val square = androidx.compose.ui.geometry.Size(side, side)
            val rotation = if (reduce) 0f else spin + b * 40f
            translate((size.width - side) / 2, (size.height - side) / 2) {
                val haloPath = halo.composePath(b, square, scale = 0.62f + 0.38f * b, rotationDeg = -rotation / 2)
                drawPath(haloPath, scheme.tertiaryContainer.copy(alpha = 0.35f + 0.25f * b))
                val path = morph.composePath(b, square, scale = 0.5f + 0.4f * b, rotationDeg = rotation)
                drawPath(
                    path,
                    Brush.radialGradient(
                        listOf(scheme.primaryContainer, scheme.primary.copy(alpha = 0.85f)),
                        center = center,
                        radius = side * (0.3f + 0.3f * b),
                    ),
                )
                drawPath(path, scheme.primary, style = Stroke(2.dp.toPx()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = inhaling,
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                label = "breathLabel",
            ) { i ->
                Text(if (i) "Inspire" else "Expire", style = MaterialTheme.typography.headlineMedium, color = scheme.onPrimary)
            }
            Text("$phaseLeft", style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = scheme.onPrimary)
        }
    }
}

@Composable
private fun Ideas() {
    var index by rememberSaveable { mutableIntStateOf((0 until IDEAS.size).random()) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(
                targetState = index,
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "idea",
            ) { i ->
                Text(
                    IDEAS[i],
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { index = (index + 1 + (0 until IDEAS.size - 1).random()) % IDEAS.size }, shapes = ButtonDefaults.shapes()) {
            Text("Une autre idée")
        }
    }
}

@Composable
private fun Done(victories: Int, onBack: () -> Unit, onAgain: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val spin by rememberInfiniteTransition(label = "trophy").animateFloat(0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "spin")
        ShapeIcon(
            Icons.Rounded.EmojiEvents,
            MaterialShapes.Sunny,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            size = 140.dp,
            rotationDeg = if (LocalReduceMotion.current) 0f else spin,
        )
        Spacer(Modifier.height(24.dp))
        Text("Envie surmontée !", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (victories <= 1) "C'est ta première victoire. Elle compte énormément." else "C'est ta ${victories}e victoire. L'envie est passée, toi tu es resté·e.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onBack, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Retour") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAgain) { Text("Encore un peu de respiration") }
        Spacer(Modifier.size(1.dp))
    }
}
