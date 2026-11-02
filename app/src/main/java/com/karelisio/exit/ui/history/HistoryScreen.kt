package com.karelisio.exit.ui.history

import androidx.compose.ui.res.pluralStringResource
import com.karelisio.exit.domain.label
import com.karelisio.exit.domain.title
import com.karelisio.exit.domain.description
import com.karelisio.exit.domain.rationale
import com.karelisio.exit.domain.detail
import com.karelisio.exit.str
import androidx.compose.ui.res.stringResource
import com.karelisio.exit.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SmokingRooms
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.exit.data.CravingEntity
import com.karelisio.exit.data.SmokeEntity
import com.karelisio.exit.domain.PlanEngine
import com.karelisio.exit.domain.Trigger
import com.karelisio.exit.ui.AppViewModel
import com.karelisio.exit.ui.components.ExitTopBar
import com.karelisio.exit.ui.components.Fmt
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.components.SosFab
import com.karelisio.exit.ui.components.cascade
import com.karelisio.exit.ui.components.rememberHaptics
import com.karelisio.exit.ui.navigation.Navigator
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(vm: AppViewModel, nav: Navigator) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val zone = ZoneId.systemDefault()
    var tab by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf<SmokeEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val smokesByDay = remember(d.smokes) { d.smokes.groupBy { PlanEngine.day(it.timestamp, zone) } }
    val cravingsByDay = remember(d.cravings) { d.cravings.groupBy { PlanEngine.day(it.timestamp, zone) } }
    val today = LocalDate.now()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { ExitTopBar(stringResource(R.string.history_historique), scroll) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tab == 0) {
                    SmallFloatingActionButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.history_ajouter_un_oubli)) }
                }
                SosFab(nav::sos)
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "tabs") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    listOf(stringResource(R.string.history_cigarettes, d.smokes.size), stringResource(R.string.history_envies, d.cravings.size)).forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = tab == i,
                            onClick = {
                                haptics.tick()
                                tab = i
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            }
            if (tab == 0) {
                if (d.smokes.isEmpty()) item(key = "empty0") { Empty(stringResource(R.string.history_aucune_cigarette_notee_pour_l)) }
                smokesByDay.forEach { (day, smokes) ->
                    item(key = "h-s-$day") { DayHeader(Fmt.dayLabel(day, today), pluralStringResource(R.plurals.cigarettes_count, smokes.size, smokes.size), Modifier.animateItem()) }
                    items(smokes, key = { "s-${it.id}" }) { smoke ->
                        Dismissable(
                            onDelete = {
                                vm.deleteSmoke(smoke)
                                scope.launch {
                                    val r = snackbar.showSnackbar(str(R.string.history_cigarette_supprimee), actionLabel = str(R.string.history_annuler), withDismissAction = true)
                                    if (r == SnackbarResult.ActionPerformed) vm.restoreSmoke(smoke)
                                }
                            },
                            modifier = Modifier.animateItem(),
                        ) {
                            SmokeRow(smoke, onClick = { editing = smoke }, onDelete = {
                                vm.deleteSmoke(smoke)
                                scope.launch {
                                    val r = snackbar.showSnackbar(str(R.string.history_cigarette_supprimee), actionLabel = str(R.string.history_annuler), withDismissAction = true)
                                    if (r == SnackbarResult.ActionPerformed) vm.restoreSmoke(smoke)
                                }
                            })
                        }
                    }
                }
            } else {
                if (d.cravings.isEmpty()) item(key = "empty1") { Empty(stringResource(R.string.history_pas_encore_d_envie_notee)) }
                cravingsByDay.forEach { (day, cravings) ->
                    val won = cravings.count { it.overcome }
                    item(key = "h-c-$day") { DayHeader(Fmt.dayLabel(day, today), pluralStringResource(R.plurals.cravings_overcome_ratio, won, won, cravings.size), Modifier.animateItem()) }
                    items(cravings, key = { "c-${it.id}" }) { craving ->
                        Dismissable(
                            onDelete = {
                                vm.deleteCraving(craving)
                                scope.launch { snackbar.showSnackbar(str(R.string.history_envie_supprimee)) }
                            },
                            modifier = Modifier.animateItem(),
                        ) {
                            CravingRow(craving, onDelete = { vm.deleteCraving(craving) })
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        SmokeEditorSheet(
            initial = null,
            onDismiss = { adding = false },
            onSave = { at, trigger, note ->
                vm.logSmoke(at, trigger, note)
                adding = false
                vm.message(str(R.string.history_cigarette_ajoutee_a, Fmt.time(at)))
            },
        )
    }
    editing?.let { smoke ->
        SmokeEditorSheet(
            initial = smoke,
            onDismiss = { editing = null },
            onSave = { at, trigger, note ->
                vm.updateSmoke(smoke.copy(timestamp = at, trigger = trigger, note = note))
                editing = null
            },
            onDelete = {
                vm.deleteSmoke(smoke)
                editing = null
            },
        )
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
    )
}

@Composable
private fun DayHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Dismissable(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val haptics = rememberHaptics()
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier.clip(MaterialTheme.shapes.large),
        enableDismissFromStartToEnd = false,
        onDismiss = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                haptics.confirm()
                onDelete()
            }
        },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.history_supprimer), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

@Composable
private fun SmokeRow(smoke: SmokeEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val trigger = Trigger.of(smoke.trigger)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.history_modifier), onClick = onClick)
            // Alternative au glisser pour supprimer (TalkBack, accès par contacteur).
            .semantics(mergeDescendants = true) { customActions = listOf(CustomAccessibilityAction(str(R.string.history_supprimer)) { onDelete(); true }) },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(
                Icons.Rounded.SmokingRooms,
                if (smoke.early) MaterialShapes.Cookie4Sided else MaterialShapes.Circle,
                if (smoke.slip) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                if (smoke.slip) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                size = 40.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Fmt.time(smoke.timestamp), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                val context = when {
                    trigger == Trigger.PERSO && !smoke.note.isNullOrBlank() -> "${trigger.emoji}  ${smoke.note}"
                    trigger != null -> "${trigger.emoji}  ${trigger.label}"
                    else -> stringResource(R.string.history_sans_contexte_touche_pour_en)
                }
                Text(context, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                smoke.slip -> Tag(stringResource(R.string.history_ecart), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                smoke.party -> Tag(stringResource(R.string.party_tag), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                smoke.early -> Tag(stringResource(R.string.history_en_avance), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CravingRow(craving: CravingEntity, onDelete: () -> Unit) {
    val trigger = Trigger.of(craving.trigger)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            customActions = listOf(CustomAccessibilityAction(str(R.string.history_supprimer)) { onDelete(); true })
        },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(
                if (craving.overcome) Icons.Rounded.Spa else Icons.Rounded.Cloud,
                if (craving.overcome) MaterialShapes.Clover4Leaf else MaterialShapes.Puffy,
                if (craving.overcome) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                if (craving.overcome) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 40.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (craving.overcome) R.string.history_craving_overcome else R.string.history_craving_not, Fmt.time(craving.timestamp)),
                    style = MaterialTheme.typography.titleMedium,
                )
                val parts = listOfNotNull(
                    if (craving.durationSeconds > 0) stringResource(R.string.history_min_s, craving.durationSeconds / 60, craving.durationSeconds % 60) else null,
                    trigger?.let { if (it == Trigger.PERSO && !craving.note.isNullOrBlank()) "${it.emoji} ${craving.note}" else "${it.emoji} ${it.label}" },
                )
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Tag(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
