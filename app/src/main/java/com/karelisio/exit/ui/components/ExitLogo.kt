package com.karelisio.exit.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.karelisio.exit.ui.theme.LocalReduceMotion

/** Le logo d'Exit animé : la fumée se dessine depuis la braise et devient une flèche de sortie. */
@Composable
fun ExitLogo(modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val loop = rememberInfiniteTransition(label = "logo")
    val t by loop.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                0f at 0 using FastOutSlowInEasing
                1f at 1600
                1f at 4200
            },
        ),
        label = "draw",
    )
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "spin")
    val breathe by loop.animateFloat(0.92f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val progress = if (reduce) 1f else t
    val bg = MaterialTheme.colorScheme.primaryContainer
    val smoke = MaterialTheme.colorScheme.primary
    val body = MaterialTheme.colorScheme.surfaceContainerLowest
    val filter = Color(0xFFF4B07A)
    val ember = Color(0xFFFF6F43)
    val smokePath = remember {
        Path().apply {
            moveTo(69f, 63f)
            cubicTo(75f, 57f, 63f, 52f, 67f, 45f)
            cubicTo(70f, 40f, 73f, 37.5f, 79f, 31.5f)
        }
    }
    val arrow = remember {
        Path().apply {
            moveTo(70f, 31f)
            lineTo(79.5f, 31f)
            lineTo(79.5f, 40.5f)
        }
    }
    Canvas(modifier) {
        val side = minOf(size.width, size.height)
        val bgPath = MaterialShapes.SoftBurst.composePath(Size(side, side), scale = if (reduce) 1f else breathe, rotationDeg = if (reduce) 0f else spin)
        translate((size.width - side) / 2, (size.height - side) / 2) {
            drawPath(bgPath, bg)
            val k = side / 108f
            scale(k, k, pivot = Offset.Zero) {
                translate(-1f, 2f) {
                    drawRoundRect(filter, Offset(28f, 67f), Size(12f, 9f), CornerRadius(2f))
                    drawRect(body, Offset(41.5f, 67f), Size(22f, 9f))
                    drawRoundRect(ember, Offset(65f, 67f), Size(5f, 9f), CornerRadius(3f))
                    val measure = PathMeasure()
                    measure.setPath(smokePath, false)
                    val seg = Path()
                    measure.getSegment(0f, measure.length * (progress / 0.8f).coerceAtMost(1f), seg, true)
                    val stroke = Stroke(5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawPath(seg, smoke, style = stroke)
                    val arrowAlpha = ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)
                    if (arrowAlpha > 0f) drawPath(arrow, smoke.copy(alpha = arrowAlpha), style = stroke)
                }
            }
        }
    }
}
