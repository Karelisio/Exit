package com.karelisio.exit.ui.components

import android.graphics.Matrix
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

private fun android.graphics.Path.fit(size: Size, scale: Float, rotationDeg: Float): Path {
    val m = Matrix()
    m.setScale(size.width * scale, size.height * scale)
    m.postTranslate(size.width * (1 - scale) / 2f, size.height * (1 - scale) / 2f)
    if (rotationDeg != 0f) m.postRotate(rotationDeg, size.width / 2f, size.height / 2f)
    transform(m)
    return asComposePath()
}

/** Chemin d'un morphing entre deux formes M3 (normalisées dans [0, 1]), mis à l'échelle de [size]. */
fun Morph.composePath(progress: Float, size: Size, scale: Float = 1f, rotationDeg: Float = 0f): Path =
    toPath(progress.coerceIn(0f, 1f)).fit(size, scale, rotationDeg)

fun RoundedPolygon.composePath(size: Size, scale: Float = 1f, rotationDeg: Float = 0f): Path =
    toPath().fit(size, scale, rotationDeg)

/** Forme Compose issue d'un morphing M3 Expressive, à un instant donné. */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDeg: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(morph.composePath(progress, size, rotationDeg = rotationDeg))
}

class PolygonShape(private val polygon: RoundedPolygon, private val rotationDeg: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(polygon.composePath(size, rotationDeg = rotationDeg))
}

/** Icône posée sur une forme M3 Expressive (cookie, trèfle, soleil…). */
@Composable
fun ShapeIcon(
    icon: ImageVector,
    shape: RoundedPolygon,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    rotationDeg: Float = 0f,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(PolygonShape(shape, rotationDeg))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

/** Chemin d'une forme M3 tournée de [rotationDeg] autour de son centre. */
fun composePathOf(polygon: RoundedPolygon, size: Size, rotationDeg: Float = 0f): Path =
    polygon.composePath(size, rotationDeg = rotationDeg)
