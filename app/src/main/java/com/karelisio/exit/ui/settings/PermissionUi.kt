package com.karelisio.exit.ui.settings

import androidx.compose.ui.res.stringResource
import com.karelisio.exit.R
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.exit.system.ExitPermission
import com.karelisio.exit.system.Permissions
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.components.rememberHaptics

val ExitPermission.icon: ImageVector
    get() = when (this) {
        ExitPermission.NOTIFICATIONS -> Icons.Rounded.NotificationsActive
        ExitPermission.EXACT_ALARMS -> Icons.Rounded.Alarm
        ExitPermission.BATTERY -> Icons.Rounded.BatteryChargingFull
        ExitPermission.INSTALL -> Icons.Rounded.SystemUpdate
    }

/** État des autorisations, revérifié à chaque retour des réglages système. */
class PermissionController(
    private val check: (ExitPermission) -> Boolean,
    private val requestImpl: (ExitPermission) -> Unit,
) {
    var granted by mutableStateOf(ExitPermission.entries.associateWith(check))
        private set

    fun refresh() {
        granted = ExitPermission.entries.associateWith(check)
    }

    fun request(permission: ExitPermission) = requestImpl(permission)

    fun isGranted(permission: ExitPermission) = granted[permission] == true
}

@Composable
fun rememberPermissionController(): PermissionController {
    val context = LocalContext.current
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    var controller: PermissionController? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) deniedOnce = true
        controller?.refresh()
    }
    val created = remember {
        PermissionController(
            check = { Permissions.isGranted(context, it) },
            requestImpl = { p ->
                if (p == ExitPermission.NOTIFICATIONS && !deniedOnce && android.os.Build.VERSION.SDK_INT >= 33) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Permissions.openSettings(context, p)
                }
            },
        )
    }
    controller = created
    LifecycleResumeEffect(Unit) {
        created.refresh()
        onPauseOrDispose { }
    }
    return created
}

/** Bouton « Autoriser » qui se transforme en coche animée une fois l'autorisation accordée. */
@Composable
fun GrantButton(granted: Boolean, onRequest: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    var wasGranted by remember { mutableStateOf(granted) }
    LaunchedEffect(granted) {
        if (granted && !wasGranted) haptics.success()
        wasGranted = granted
    }
    AnimatedContent(
        targetState = granted,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), initialScale = 0.3f) + fadeIn())
                .togetherWith(scaleOut(targetScale = 0.6f) + fadeOut())
        },
        label = "grant",
        modifier = modifier,
    ) { ok ->
        if (ok) {
            ShapeIcon(Icons.Rounded.Check, MaterialShapes.Cookie9Sided, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, size = 56.dp)
        } else {
            Button(onClick = onRequest, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.settings_autoriser)) }
        }
    }
}
