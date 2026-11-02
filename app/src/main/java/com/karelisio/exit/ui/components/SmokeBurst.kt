package com.karelisio.exit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.karelisio.exit.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private class Particle(
    val t: Float,
    val spread: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val accent: Boolean,
)

private fun particles(seed: Int): List<Particle> {
    val rnd = Random(seed * 7919 + 17)
    return List(110) {
        val angle = rnd.nextFloat() * 2 * PI.toFloat()
        val speed = 0.25f + rnd.nextFloat() * 0.75f
        Particle(
            t = rnd.nextFloat(),
            spread = rnd.nextFloat() * 2 - 1,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed - 0.35f,
            radius = 0.018f + rnd.nextFloat() * 0.035f,
            accent = rnd.nextFloat() < 0.22f,
        )
    }
}

/**
 * Animation signature : une volute de fumée se forme puis se dissipe en particules
 * (envie surmontée, jalon atteint). Déclenchée à chaque changement de [trigger] (> 0).
 */
@Composable
fun SmokeBurst(
    trigger: Int,
    modifier: Modifier = Modifier,
    smoke: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accent: Color = MaterialTheme.colorScheme.primary,
    onFinished: () -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val finished by rememberUpdatedState(onFinished)
    val progress = remember { Animatable(1f) }
    val list = remember(trigger) { particles(trigger) }
    LaunchedEffect(trigger, reduce) {
        if (trigger <= 0) return@LaunchedEffect
        if (reduce) {
            finished()
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2600, easing = LinearEasing))
        finished()
    }
    if (reduce) return
    Canvas(modifier) {
        val q = progress.value
        if (q >= 1f) return@Canvas
        val w = size.width
        val h = size.height
        val formed = (q / 0.28f).coerceAtMost(1f)
        val d = FastOutSlowInEasing.transform(((q - 0.28f) / 0.72f).coerceIn(0f, 1f))
        val fade = (1f - d).pow(1.4f)
        val base = h * 0.82f
        val top = h * 0.22f
        list.forEach { p ->
            if (p.t > formed) return@forEach
            val y0 = base - (base - top) * p.t
            val x0 = w / 2 + sin(p.t * 2.6f * PI.toFloat()) * w * 0.12f * (0.4f + p.t) + p.spread * w * 0.03f * (0.3f + p.t)
            val push = if (p.accent) 0.75f else 0.45f
            val x = x0 + p.vx * d * w * push
            val y = y0 + p.vy * d * h * push - d * h * 0.08f
            val appear = ((formed - p.t) * 6f).coerceIn(0f, 1f)
            if (p.accent) {
                val r = w * p.radius * 0.35f * (1f + d)
                drawCircle(accent.copy(alpha = appear * fade), r, Offset(x, y))
            } else {
                val r = w * p.radius * (1f + 1.8f * d)
                val alpha = 0.5f * appear * fade
                drawCircle(
                    brush = Brush.radialGradient(listOf(smoke.copy(alpha = alpha), smoke.copy(alpha = 0f)), Offset(x, y), r),
                    radius = r,
                    center = Offset(x, y),
                )
            }
        }
    }
}
