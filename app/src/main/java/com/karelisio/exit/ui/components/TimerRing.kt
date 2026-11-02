package com.karelisio.exit.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.karelisio.exit.ui.theme.LocalReduceMotion

/**
 * Anneau minuteur signature : il se remplit le long d'une forme M3 qui morphe d'un cookie
 * festonné vers un cercle à mesure que l'intervalle s'écoule, puis éclate en soleil doux
 * et pulse quand la prochaine cigarette est « autorisée ».
 */
@Composable
fun TimerRing(
    progress: Float,
    ok: Boolean,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val waiting = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Circle) }
    val burst = remember { Morph(MaterialShapes.Circle, MaterialShapes.SoftBurst) }
    val core = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }

    val p by animateFloatAsState(progress, spring(stiffness = Spring.StiffnessVeryLow), label = "ring")
    val okP by animateFloatAsState(if (ok) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 120f), label = "ok")

    val infinite = rememberInfiniteTransition(label = "ring-loop")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(48_000, easing = LinearEasing)), label = "spin")
    val beat by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beat")
    val rotation = if (reduce) 0f else spin
    val pulse = if (ok && !reduce) beat else 0f

    val scheme = MaterialTheme.colorScheme
    val ringColor by animateColorAsState(if (ok) scheme.tertiary else scheme.primary, label = "ringColor")
    val coreColor by animateColorAsState(if (ok) scheme.tertiaryContainer else scheme.primaryContainer, label = "coreColor")
    val track = scheme.surfaceContainerHighest

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f + pulse * 0.045f
                    scaleX = s
                    scaleY = s
                },
        ) {
            val stroke = strokeWidth.toPx()
            val inner = Size(size.width - stroke * 2.4f, size.height - stroke * 2.4f)
            val offset = Offset(stroke * 1.2f, stroke * 1.2f)

            // Cœur plein qui tourne lentement et s'épanouit en soleil.
            val corePath = core.composePath(okP * 0.85f + p * 0.15f, inner, scale = 0.8f, rotationDeg = rotation)
            translate(offset.x, offset.y) { drawPath(corePath, coreColor) }

            // Anneau : départ en haut, forme fixe (seule la progression avance).
            val ringPath: Path = if (okP > 0.001f) {
                burst.composePath(okP, inner, rotationDeg = -90f)
            } else {
                waiting.composePath(p, inner, rotationDeg = -90f)
            }
            translate(offset.x, offset.y) {
                drawPath(ringPath, track, style = Stroke(stroke, join = StrokeJoin.Round))
                if (ok) {
                    drawPath(ringPath, ringColor.copy(alpha = 0.18f + 0.22f * pulse), style = Stroke(stroke * 2.2f, join = StrokeJoin.Round))
                    drawPath(ringPath, ringColor, style = Stroke(stroke, join = StrokeJoin.Round))
                } else {
                    val measure = PathMeasure()
                    measure.setPath(ringPath, false)
                    val segment = Path()
                    measure.getSegment(0f, measure.length * p, segment, true)
                    drawPath(segment, ringColor, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        content()
    }
}
