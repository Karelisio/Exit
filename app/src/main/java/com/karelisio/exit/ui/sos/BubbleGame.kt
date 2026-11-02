package com.karelisio.exit.ui.sos

import androidx.compose.ui.res.pluralStringResource
import com.karelisio.exit.str
import androidx.compose.ui.res.stringResource
import com.karelisio.exit.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.karelisio.exit.ui.components.rememberHaptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private class Puff(val id: Long, val x: Float, val born: Long, val speed: Float, val radius: Float, val wobble: Float) {
    var poppedAt: Long = 0
}

/** Distraction : des bouffées de fumée montent, on les éclate du bout du doigt. */
@Composable
fun BubbleGame(modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val puffs = remember { mutableStateListOf<Puff>() }
    var frame by remember { mutableLongStateOf(0L) }
    var score by remember { mutableIntStateOf(0) }
    var nextId by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var lastSpawn = 0L
        while (true) {
            withFrameMillis { t ->
                frame = t
                if (t - lastSpawn > 700 && puffs.count { it.poppedAt == 0L } < 9) {
                    lastSpawn = t
                    puffs += Puff(nextId++, 0.1f + Random.nextFloat() * 0.8f, t, 0.00009f + Random.nextFloat() * 0.00008f, 0.07f + Random.nextFloat() * 0.06f, Random.nextFloat() * 6f)
                }
                puffs.removeAll { (it.poppedAt != 0L && t - it.poppedAt > 400) || (t - it.born) * it.speed > 1.3f }
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier.semantics { contentDescription = str(R.string.sos_mini_jeu_eclate_les_bouffees, score) },
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHigh,
    ) {
        Box {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val hit = puffs.lastOrNull { p ->
                                if (p.poppedAt != 0L) return@lastOrNull false
                                val c = position(p, frame, w, h)
                                hypot(tap.x - c.x, tap.y - c.y) < p.radius * w * 1.2f
                            }
                            if (hit != null) {
                                hit.poppedAt = frame
                                score++
                                haptics.tick()
                            }
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                puffs.forEach { p ->
                    val c = position(p, frame, w, h)
                    val r = p.radius * w
                    if (p.poppedAt == 0L) {
                        drawCircle(
                            Brush.radialGradient(listOf(scheme.onSurfaceVariant.copy(alpha = 0.35f), scheme.onSurfaceVariant.copy(alpha = 0.05f)), c, r),
                            r,
                            c,
                        )
                        drawCircle(scheme.outline.copy(alpha = 0.4f), r, c, style = Stroke(1.5.dp.toPx()))
                    } else {
                        val q = ((frame - p.poppedAt) / 400f).coerceIn(0f, 1f)
                        drawCircle(scheme.primary.copy(alpha = 1f - q), r * (1f + q), c, style = Stroke((1f - q) * 4.dp.toPx()))
                        repeat(8) { i ->
                            val a = i / 8f * 2 * PI.toFloat()
                            val o = Offset(c.x + cos(a) * r * (1f + q * 1.4f), c.y + sin(a) * r * (1f + q * 1.4f))
                            drawCircle(scheme.tertiary.copy(alpha = 1f - q), 3.dp.toPx() * (1f - q), o)
                        }
                    }
                }
            }
            Text(
                if (score == 0) stringResource(R.string.sos_eclate_les_bouffees_de_fumee) else pluralStringResource(R.plurals.sos_popped, score, score),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
            )
        }
    }
}

private fun position(p: Puff, now: Long, w: Float, h: Float): Offset {
    val age = (now - p.born).coerceAtLeast(0)
    val rise = age * p.speed
    val x = p.x * w + sin(age / 600f + p.wobble) * w * 0.04f
    val y = h * (1.1f - rise)
    return Offset(x, y)
}
