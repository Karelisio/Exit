package com.karelisio.exit.ui.settings

import com.karelisio.exit.str
import androidx.compose.ui.res.stringResource
import com.karelisio.exit.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SmokingRooms
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.exit.BuildConfig
import com.karelisio.exit.data.ConfigEntity
import com.karelisio.exit.ui.AppViewModel
import com.karelisio.exit.ui.components.ExitTopBar
import com.karelisio.exit.ui.components.SectionTitle
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.components.cascade
import com.karelisio.exit.ui.navigation.Navigator
import com.karelisio.exit.update.UpdateState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Edit {
    data class Number(val title: String, val value: String, val decimal: Boolean, val apply: (ConfigEntity, String) -> ConfigEntity?) : Edit
    data class Time(val title: String, val minutes: Int, val apply: (ConfigEntity, Int) -> ConfigEntity) : Edit
}

@Composable
fun SettingsScreen(vm: AppViewModel, nav: Navigator) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val c = d.config
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update by vm.updates.state.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<Edit?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = vm.exportJson()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            }.isSuccess
            vm.message(if (ok) str(R.string.settings_sauvegarde_exportee) else str(R.string.settings_export_impossible))
        }
    }
    var reasonsEdit by remember { mutableStateOf(false) }
    var backupMenu by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            vm.setAutoBackupFolder(uri.toString())
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            pendingImport = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
            }.getOrNull()
            if (pendingImport == null) vm.message(str(R.string.settings_lecture_du_fichier_impossible))
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = { ExitTopBar(stringResource(R.string.settings_reglages), scroll, onBack = { nav.back() }) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SectionTitle(stringResource(R.string.settings_ma_consommation_de_depart)) }
            item {
                Row(Icons.Rounded.SmokingRooms, MaterialShapes.Cookie4Sided, stringResource(R.string.settings_cigarettes_par_jour), "${c.cigsPerDay}", Modifier.cascade(0)) {
                    edit = Edit.Number(str(R.string.settings_cigarettes_par_jour), c.cigsPerDay.toString(), false) { cfg, v -> v.toIntOrNull()?.takeIf { it in 1..80 }?.let { cfg.copy(cigsPerDay = it) } }
                }
            }
            item {
                Row(Icons.Rounded.Euro, MaterialShapes.Clover4Leaf, stringResource(R.string.settings_prix_du_paquet), com.karelisio.exit.ui.components.Fmt.money(c.packPrice), Modifier.cascade(1)) {
                    edit = Edit.Number(str(R.string.settings_prix_du_paquet_2), "%.2f".format(c.packPrice).replace(',', '.'), true) { cfg, v ->
                        v.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }?.let { cfg.copy(packPrice = it) }
                    }
                }
            }
            item {
                Row(Icons.Rounded.Inventory2, MaterialShapes.Square, stringResource(R.string.settings_cigarettes_par_paquet), "${c.cigsPerPack}", Modifier.cascade(2)) {
                    edit = Edit.Number(str(R.string.settings_cigarettes_par_paquet), c.cigsPerPack.toString(), false) { cfg, v -> v.toIntOrNull()?.takeIf { it in 1..50 }?.let { cfg.copy(cigsPerPack = it) } }
                }
            }
            item { Row(Icons.Rounded.Route, MaterialShapes.Pill, stringResource(R.string.settings_mon_plan), stringResource(R.string.settings_date_paliers_ajustements), Modifier.cascade(3), onClick = nav::plan) }
            item {
                val count = c.reasons.lines().count { it.isNotBlank() }
                Row(Icons.Rounded.Favorite, MaterialShapes.Heart, stringResource(R.string.settings_mes_raisons_d_arreter), if (count == 0) stringResource(R.string.settings_rappelees_dans_le_sos_et) else stringResource(R.string.settings_raison, count, if (count > 1) "s" else "")) { reasonsEdit = true }
            }

            item { SectionTitle(stringResource(R.string.settings_ma_journee)) }
            item {
                Row(Icons.Rounded.WbSunny, MaterialShapes.Sunny, stringResource(R.string.settings_reveil), hm(c.wakeMinutes), Modifier.cascade(4)) {
                    edit = Edit.Time(str(R.string.settings_heure_de_reveil), c.wakeMinutes) { cfg, m -> cfg.copy(wakeMinutes = m) }
                }
            }
            item {
                Row(Icons.Rounded.Bedtime, MaterialShapes.Arch, stringResource(R.string.settings_coucher), stringResource(R.string.settings_pas_de_notification_la_nuit, hm(c.sleepMinutes)), Modifier.cascade(5)) {
                    edit = Edit.Time(str(R.string.settings_heure_de_coucher), c.sleepMinutes) { cfg, m -> cfg.copy(sleepMinutes = m) }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    var delay by remember(c.morningDelayMinutes) { mutableFloatStateOf(c.morningDelayMinutes.toFloat()) }
                    Text(
                        if (delay < 1f) stringResource(R.string.settings_premiere_du_matin_pas_d) else stringResource(R.string.settings_premiere_du_matin_au_moins, com.karelisio.exit.domain.PlanEngine.formatDuration(delay.roundToInt())),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(delay, { delay = (it / 15f).roundToInt() * 15f }, valueRange = 0f..240f, steps = 15, onValueChangeFinished = {
                        vm.updateConfig { it.copy(morningDelayMinutes = delay.roundToInt()) }
                    })
                    Text(
                        stringResource(R.string.settings_retarder_la_premiere_cigarette_est),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.settings_respiration_sos)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).cascade(6)) {
                    var inhale by remember(c.breathInhaleSeconds) { mutableFloatStateOf(c.breathInhaleSeconds) }
                    var exhale by remember(c.breathExhaleSeconds) { mutableFloatStateOf(c.breathExhaleSeconds) }
                    Text(stringResource(R.string.settings_inspiration_s, inhale.roundToInt()), style = MaterialTheme.typography.bodyLarge)
                    Slider(inhale, { inhale = it.roundToInt().toFloat() }, valueRange = 3f..8f, steps = 4, onValueChangeFinished = {
                        vm.updateConfig { it.copy(breathInhaleSeconds = inhale) }
                    })
                    Text(stringResource(R.string.settings_expiration_s, exhale.roundToInt()), style = MaterialTheme.typography.bodyLarge)
                    Slider(exhale, { exhale = it.roundToInt().toFloat() }, valueRange = 3f..10f, steps = 6, onValueChangeFinished = {
                        vm.updateConfig { it.copy(breathExhaleSeconds = exhale) }
                    })
                    Text(
                        stringResource(R.string.settings_5_s_5_s_coherence),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Toggle(Icons.Rounded.Vibration, MaterialShapes.Flower, stringResource(R.string.settings_vibrations_douces), stringResource(R.string.settings_a_chaque_inspiration_et_expiration), c.breathHaptics) { on ->
                    vm.updateConfig { it.copy(breathHaptics = on) }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_notifications)) }
            item {
                Toggle(Icons.Rounded.Notifications, MaterialShapes.Cookie9Sided, stringResource(R.string.settings_prochaine_autorisee), stringResource(R.string.settings_a_l_heure_exacte_ou), c.notifyNextAllowed) { on ->
                    vm.updateConfig { it.copy(notifyNextAllowed = on) }
                }
            }
            item {
                Toggle(Icons.Rounded.EmojiEvents, MaterialShapes.SoftBurst, stringResource(R.string.progress_jalons_sante), stringResource(R.string.settings_pendant_l_arret_complet), c.notifyMilestones) { on ->
                    vm.updateConfig { it.copy(notifyMilestones = on) }
                }
            }
            item { Row(Icons.Rounded.Security, MaterialShapes.Gem, stringResource(R.string.settings_autorisations), stringResource(R.string.settings_notifications_alarmes_batterie_mises_a), onClick = nav::permissions) }

            item { SectionTitle(stringResource(R.string.settings_apparence)) }
            item {
                Toggle(Icons.Rounded.Palette, MaterialShapes.Puffy, stringResource(R.string.settings_couleurs_du_fond_d_ecran), stringResource(R.string.settings_sinon_vert_menthe_et_bleu), c.dynamicColor) { on ->
                    vm.updateConfig { it.copy(dynamicColor = on) }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_sauvegarde_locale)) }
            item {
                val last = c.lastAutoBackupAt
                Row(
                    Icons.Rounded.Backup,
                    MaterialShapes.Cookie6Sided,
                    stringResource(R.string.settings_sauvegarde_automatique),
                    when {
                        c.autoBackupUri == null -> stringResource(R.string.settings_desactivee_choisis_un_dossier_ex)
                        last == null -> stringResource(R.string.settings_quotidienne_pas_encore_faite)
                        else -> stringResource(R.string.settings_quotidienne_derniere_le_a, com.karelisio.exit.ui.components.Fmt.dayLabel(com.karelisio.exit.domain.PlanEngine.day(last, java.time.ZoneId.systemDefault())), com.karelisio.exit.ui.components.Fmt.time(last))
                    },
                ) { if (c.autoBackupUri == null) folderPicker.launch(null) else backupMenu = true }
            }
            item {
                Row(Icons.Rounded.Download, MaterialShapes.Bun, stringResource(R.string.settings_exporter_en_json), stringResource(R.string.settings_toutes_tes_donnees_dans_un)) {
                    exporter.launch("exit-sauvegarde-${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}.json")
                }
            }
            item {
                Row(Icons.Rounded.Upload, MaterialShapes.Bun, stringResource(R.string.settings_importer_un_json), stringResource(R.string.settings_remplace_les_donnees_actuelles)) {
                    importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }

            item { SectionTitle(stringResource(R.string.settings_application)) }
            item {
                val status = when (update) {
                    UpdateState.Checking -> stringResource(R.string.settings_recherche)
                    UpdateState.UpToDate -> stringResource(R.string.settings_a_jour)
                    UpdateState.CheckFailed -> stringResource(R.string.settings_verification_impossible_reseau)
                    is UpdateState.Available -> stringResource(R.string.update_nouvelle_version_disponible)
                    is UpdateState.Downloading -> stringResource(R.string.settings_telechargement)
                    is UpdateState.Installing -> stringResource(R.string.settings_installation)
                    is UpdateState.Failed -> stringResource(R.string.settings_echec_de_la_mise_a)
                    UpdateState.Idle -> stringResource(R.string.settings_touche_pour_verifier)
                }
                Row(Icons.Rounded.SystemUpdate, MaterialShapes.Cookie7Sided, stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), status) { vm.updates.checkNow() }
            }
            item { Row(Icons.Rounded.Info, MaterialShapes.Circle, stringResource(R.string.settings_sources_et_aide), stringResource(R.string.settings_tabac_info_service_oms_39), onClick = nav::sources) }
            item { Row(Icons.Rounded.Air, MaterialShapes.Clover8Leaf, stringResource(R.string.settings_exit), "100 % local : aucun compte, aucun serveur, aucune publicité") {} }
        }
    }

    when (val e = edit) {
        is Edit.Number -> NumberDialog(e, onDismiss = { edit = null }) { value ->
            val next = e.apply(c, value)
            if (next != null) {
                vm.updateConfig { e.apply(it, value) ?: it }
                edit = null
            }
        }
        is Edit.Time -> TimeDialog(e.title, e.minutes, onDismiss = { edit = null }) { m ->
            vm.updateConfig { e.apply(it, m) }
            edit = null
        }
        null -> Unit
    }
    if (reasonsEdit) {
        var text by remember { mutableStateOf(c.reasons) }
        AlertDialog(
            onDismissRequest = { reasonsEdit = false },
            title = { Text(stringResource(R.string.settings_mes_raisons_d_arreter)) },
            text = {
                OutlinedTextField(text, { text = it.take(500) }, label = { Text(stringResource(R.string.settings_une_raison_par_ligne)) }, minLines = 4)
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateConfig { it.copy(reasons = text.trim()) }
                    reasonsEdit = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { reasonsEdit = false }) { Text(stringResource(R.string.history_annuler)) } },
        )
    }
    if (backupMenu) {
        AlertDialog(
            onDismissRequest = { backupMenu = false },
            title = { Text(stringResource(R.string.settings_sauvegarde_automatique)) },
            text = { Text(stringResource(R.string.settings_chaque_jour_exit_enregistre_dans, com.karelisio.exit.data.AutoBackup.FILE_NAME)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.backupNow()
                    backupMenu = false
                }) { Text(stringResource(R.string.settings_sauvegarder_maintenant)) }
            },
            dismissButton = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    TextButton(onClick = {
                        backupMenu = false
                        folderPicker.launch(null)
                    }) { Text(stringResource(R.string.settings_changer_de_dossier)) }
                    TextButton(onClick = {
                        vm.setAutoBackupFolder(null)
                        backupMenu = false
                    }) { Text(stringResource(R.string.settings_desactiver)) }
                }
            },
        )
    }
    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.settings_restaurer_cette_sauvegarde)) },
            text = { Text(stringResource(R.string.settings_les_donnees_actuelles_historique_envies)) },
            confirmButton = {
                Button(onClick = {
                    vm.importJson(text)
                    pendingImport = null
                }) { Text(stringResource(R.string.settings_restaurer)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.history_annuler)) } },
        )
    }
}

private fun hm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
private fun Row(
    icon: ImageVector,
    shape: RoundedPolygon,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { ShapeIcon(icon, shape, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, size = 40.dp) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    )
}

@Composable
private fun Toggle(icon: ImageVector, shape: RoundedPolygon, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { ShapeIcon(icon, shape, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, size = 40.dp) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onChange(!checked) },
    )
}

@Composable
private fun NumberDialog(edit: Edit.Number, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(edit.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(edit.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(6) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (edit.decimal) KeyboardType.Decimal else KeyboardType.Number),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_annuler)) } },
    )
}

@Composable
private fun TimeDialog(title: String, minutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_annuler)) } },
    )
}
