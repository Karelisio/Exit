package com.karelisio.exit.ui.components

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.karelisio.exit.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

/** Heure courante, rafraîchie toutes les [periodMs] (alignée sur la seconde). */
@Composable
fun rememberNow(periodMs: Long = 1000L): Long {
    val now by produceState(System.currentTimeMillis(), periodMs) {
        while (true) {
            val t = System.currentTimeMillis()
            delay(periodMs - t % periodMs)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** Entrée en cascade des éléments de liste : fondu + glissement avec ressort, décalé selon l'index. */
fun Modifier.cascade(index: Int): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    var played by rememberSaveable { mutableStateOf(false) }
    val anim = remember { Animatable(if (reduce || played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (anim.value < 1f) {
            delay(index.coerceAtMost(12) * 45L)
            anim.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 260f))
        }
        played = true
    }
    graphicsLayer {
        alpha = anim.value.coerceIn(0f, 1f)
        translationY = (1f - anim.value) * with(density) { 56.dp.toPx() }
        val s = 0.94f + 0.06f * anim.value
        scaleX = s
        scaleY = s
    }
}

/** Compteur « odomètre » : chaque caractère qui change glisse verticalement. */
@Composable
fun AnimatedCounter(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    Row(modifier) {
        val chars = text.toList()
        chars.forEachIndexed { index, char ->
            key(chars.size - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 400f)) { if (up) it else -it } + fadeIn())
                            .togetherWith(slideOutVertically { if (up) -it else it } + fadeOut())
                            .using(SizeTransform(clip = false))
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

/** Nombre qui « compte » jusqu'à sa valeur à l'affichage, puis suit ses changements. */
@Composable
fun CountUpText(
    value: Double,
    format: (Double) -> String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val reduce = LocalReduceMotion.current
    var target by remember { mutableStateOf(if (reduce) value else 0.0) }
    LaunchedEffect(value) { target = value }
    val animated by animateFloatAsState(
        target.toFloat(),
        tween(if (reduce) 0 else 1400, easing = FastOutSlowInEasing),
        label = "countUp",
    )
    Text(format(animated.toDouble()), style = style, color = color, modifier = modifier)
}

/** Retours haptiques subtils (primitives de composition quand le moteur les gère). */
class Haptics(context: Context) {
    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    private fun primitive(id: Int, scale: Float, fallback: () -> VibrationEffect) {
        if (!vibrator.hasVibrator()) return
        val effect = if (vibrator.areAllPrimitivesSupported(id)) {
            VibrationEffect.startComposition().addPrimitive(id, scale).compose()
        } else {
            fallback()
        }
        vibrator.vibrate(effect)
    }

    fun tick() = primitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    }

    fun confirm() = primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    }

    fun breatheIn() = primitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.35f) {
        VibrationEffect.createOneShot(40, 40)
    }

    fun breatheOut() = primitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.3f) {
        VibrationEffect.createOneShot(25, 30)
    }

    fun success() {
        if (!vibrator.hasVibrator()) return
        val ids = intArrayOf(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_CLICK)
        val effect = if (vibrator.areAllPrimitivesSupported(*ids)) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 60)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 90)
                .compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        }
        vibrator.vibrate(effect)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember { Haptics(context) }
}
