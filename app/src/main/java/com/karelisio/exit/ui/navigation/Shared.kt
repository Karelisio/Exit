package com.karelisio.exit.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Transition « container transform » entre deux écrans (FAB SOS → écran SOS, carte plan → plan). */
fun Modifier.sharedContainer(key: String, shape: Shape = RoundedCornerShape(28.dp)): Modifier = composed {
    val shared = LocalSharedScope.current
    val anim = LocalNavAnimScope.current
    if (shared == null || anim == null) {
        this
    } else {
        with(shared) {
            this@composed.sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = anim,
                clipInOverlayDuringTransition = OverlayClip(shape),
            )
        }
    }
}
