package com.karelisio.exit.ui.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.exit.system.ExitPermission
import com.karelisio.exit.system.Permissions
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.theme.LocalReduceMotion
import com.karelisio.exit.update.UpdateManager
import com.karelisio.exit.update.UpdateState
import com.karelisio.exit.update.offer

/** Feuille « nouvelle version » : changelog, téléchargement avec progression animée, installation. */
@Composable
fun UpdateSheetHost(updates: UpdateManager) {
    val state by updates.state.collectAsStateWithLifecycle()
    val offer = state.offer ?: return
    var hidden by remember { mutableStateOf<String?>(null) }
    if (offer.version == hidden || (offer.version == updates.dismissedVersion && state is UpdateState.Available)) return
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(updates.canInstall()) }
    LifecycleResumeEffect(Unit) {
        canInstall = updates.canInstall()
        onPauseOrDispose { }
    }
    val busy = state is UpdateState.Downloading || state is UpdateState.Installing
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })
    val spin by rememberInfiniteTransition(label = "upd").animateFloat(0f, 360f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "spin")

    ModalBottomSheet(
        onDismissRequest = {
            if (!busy) {
                updates.dismiss(offer)
                hidden = offer.version
            }
        },
        sheetState = sheet,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(
                    Icons.Rounded.SystemUpdate,
                    MaterialShapes.Cookie12Sided,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 56.dp,
                    rotationDeg = if (LocalReduceMotion.current) 0f else spin,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Exit ${offer.version}", style = MaterialTheme.typography.headlineSmall)
                    Text("Nouvelle version disponible", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                Changelog(offer.changelog.ifBlank { "Améliorations et corrections." })
            }
            Spacer(Modifier.height(20.dp))
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "updateAction",
            ) { s ->
                when (s) {
                    is UpdateState.Downloading -> {
                        val p by animateFloatAsState(s.progress, label = "dl")
                        Column {
                            LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Téléchargement… ${(p * 100).toInt()} %", style = MaterialTheme.typography.bodyLarge)
                                TextButton(onClick = updates::cancelDownload) { Text("Annuler") }
                            }
                        }
                    }
                    is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Installation… confirme dans la fenêtre d'Android.", style = MaterialTheme.typography.bodyLarge)
                    }
                    is UpdateState.Failed -> Column {
                        Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { updates.install(s.info) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Réessayer") }
                    }
                    else -> Column {
                        if (canInstall) {
                            Button(onClick = { updates.install(offer) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                Text("Mettre à jour")
                            }
                        } else {
                            Text(
                                "Autorise Exit à installer des applis pour faire la mise à jour en un geste.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { Permissions.openSettings(context, ExitPermission.INSTALL) },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { Text("Autoriser l'installation") }
                        }
                        TextButton(
                            onClick = {
                                updates.dismiss(offer)
                                hidden = offer.version
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Plus tard") }
                    }
                }
            }
        }
    }
}

/** Rendu léger du Markdown des releases GitHub (titres, puces). */
@Composable
private fun Changelog(text: String) {
    text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.forEach { raw ->
        val line = raw.replace("**", "").replace("`", "")
        when {
            line.startsWith("#") -> Text(
                line.trimStart('#', ' '),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row(Modifier.padding(vertical = 2.dp)) {
                Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(line.trimStart().drop(2), style = MaterialTheme.typography.bodyMedium)
            }
            else -> Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}
