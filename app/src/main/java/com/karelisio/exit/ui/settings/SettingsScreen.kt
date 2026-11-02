package com.karelisio.exit.ui.settings

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
            vm.message(if (ok) "Sauvegarde exportée" else "Export impossible")
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
            if (pendingImport == null) vm.message("Lecture du fichier impossible")
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = { ExitTopBar("Réglages", scroll, onBack = { nav.back() }) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SectionTitle("Ma consommation de départ") }
            item {
                Row(Icons.Rounded.SmokingRooms, MaterialShapes.Cookie4Sided, "Cigarettes par jour", "${c.cigsPerDay}", Modifier.cascade(0)) {
                    edit = Edit.Number("Cigarettes par jour", c.cigsPerDay.toString(), false) { cfg, v -> v.toIntOrNull()?.takeIf { it in 1..80 }?.let { cfg.copy(cigsPerDay = it) } }
                }
            }
            item {
                Row(Icons.Rounded.Euro, MaterialShapes.Clover4Leaf, "Prix du paquet", "%.2f €".format(c.packPrice), Modifier.cascade(1)) {
                    edit = Edit.Number("Prix du paquet (€)", "%.2f".format(c.packPrice).replace(',', '.'), true) { cfg, v ->
                        v.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }?.let { cfg.copy(packPrice = it) }
                    }
                }
            }
            item {
                Row(Icons.Rounded.Inventory2, MaterialShapes.Square, "Cigarettes par paquet", "${c.cigsPerPack}", Modifier.cascade(2)) {
                    edit = Edit.Number("Cigarettes par paquet", c.cigsPerPack.toString(), false) { cfg, v -> v.toIntOrNull()?.takeIf { it in 1..50 }?.let { cfg.copy(cigsPerPack = it) } }
                }
            }
            item { Row(Icons.Rounded.Route, MaterialShapes.Pill, "Mon plan", "Date, paliers, ajustements", Modifier.cascade(3), onClick = nav::plan) }
            item {
                val count = c.reasons.lines().count { it.isNotBlank() }
                Row(Icons.Rounded.Favorite, MaterialShapes.Heart, "Mes raisons d'arrêter", if (count == 0) "Rappelées dans le SOS et les notifications" else "$count raison${if (count > 1) "s" else ""}") { reasonsEdit = true }
            }

            item { SectionTitle("Ma journée") }
            item {
                Row(Icons.Rounded.WbSunny, MaterialShapes.Sunny, "Réveil", hm(c.wakeMinutes), Modifier.cascade(4)) {
                    edit = Edit.Time("Heure de réveil", c.wakeMinutes) { cfg, m -> cfg.copy(wakeMinutes = m) }
                }
            }
            item {
                Row(Icons.Rounded.Bedtime, MaterialShapes.Arch, "Coucher", "${hm(c.sleepMinutes)} · pas de notification la nuit", Modifier.cascade(5)) {
                    edit = Edit.Time("Heure de coucher", c.sleepMinutes) { cfg, m -> cfg.copy(sleepMinutes = m) }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    var delay by remember(c.morningDelayMinutes) { mutableFloatStateOf(c.morningDelayMinutes.toFloat()) }
                    Text(
                        if (delay < 1f) "Première du matin : pas d'objectif" else "Première du matin : au moins ${com.karelisio.exit.domain.PlanEngine.formatDuration(delay.roundToInt())} après le réveil",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(delay, { delay = (it / 15f).roundToInt() * 15f }, valueRange = 0f..240f, steps = 15, onValueChangeFinished = {
                        vm.updateConfig { it.copy(morningDelayMinutes = delay.roundToInt()) }
                    })
                    Text(
                        "Retarder la première cigarette est l'un des meilleurs signes de baisse de la dépendance. Augmente petit à petit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionTitle("Respiration SOS") }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).cascade(6)) {
                    var inhale by remember(c.breathInhaleSeconds) { mutableFloatStateOf(c.breathInhaleSeconds) }
                    var exhale by remember(c.breathExhaleSeconds) { mutableFloatStateOf(c.breathExhaleSeconds) }
                    Text("Inspiration : ${inhale.roundToInt()} s", style = MaterialTheme.typography.bodyLarge)
                    Slider(inhale, { inhale = it.roundToInt().toFloat() }, valueRange = 3f..8f, steps = 4, onValueChangeFinished = {
                        vm.updateConfig { it.copy(breathInhaleSeconds = inhale) }
                    })
                    Text("Expiration : ${exhale.roundToInt()} s", style = MaterialTheme.typography.bodyLarge)
                    Slider(exhale, { exhale = it.roundToInt().toFloat() }, valueRange = 3f..10f, steps = 6, onValueChangeFinished = {
                        vm.updateConfig { it.copy(breathExhaleSeconds = exhale) }
                    })
                    Text(
                        "5 s / 5 s = cohérence cardiaque (6 respirations par minute).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Toggle(Icons.Rounded.Vibration, MaterialShapes.Flower, "Vibrations douces", "À chaque inspiration et expiration", c.breathHaptics) { on ->
                    vm.updateConfig { it.copy(breathHaptics = on) }
                }
            }

            item { SectionTitle("Notifications") }
            item {
                Toggle(Icons.Rounded.Notifications, MaterialShapes.Cookie9Sided, "Prochaine autorisée", "À l'heure exacte où l'intervalle est écoulé", c.notifyNextAllowed) { on ->
                    vm.updateConfig { it.copy(notifyNextAllowed = on) }
                }
            }
            item {
                Toggle(Icons.Rounded.EmojiEvents, MaterialShapes.SoftBurst, "Jalons santé", "Pendant l'arrêt complet", c.notifyMilestones) { on ->
                    vm.updateConfig { it.copy(notifyMilestones = on) }
                }
            }
            item { Row(Icons.Rounded.Security, MaterialShapes.Gem, "Autorisations", "Notifications, alarmes, batterie, mises à jour", onClick = nav::permissions) }

            item { SectionTitle("Apparence") }
            item {
                Toggle(Icons.Rounded.Palette, MaterialShapes.Puffy, "Couleurs du fond d'écran", "Sinon : vert menthe et bleu air frais", c.dynamicColor) { on ->
                    vm.updateConfig { it.copy(dynamicColor = on) }
                }
            }

            item { SectionTitle("Sauvegarde locale") }
            item {
                val last = c.lastAutoBackupAt
                Row(
                    Icons.Rounded.Backup,
                    MaterialShapes.Cookie6Sided,
                    "Sauvegarde automatique",
                    when {
                        c.autoBackupUri == null -> "Désactivée · choisis un dossier (ex. Drive, carte SD)"
                        last == null -> "Quotidienne · pas encore faite"
                        else -> "Quotidienne · dernière le ${com.karelisio.exit.ui.components.Fmt.dayLabel(com.karelisio.exit.domain.PlanEngine.day(last, java.time.ZoneId.systemDefault()))} à ${com.karelisio.exit.ui.components.Fmt.time(last)}"
                    },
                ) { if (c.autoBackupUri == null) folderPicker.launch(null) else backupMenu = true }
            }
            item {
                Row(Icons.Rounded.Download, MaterialShapes.Bun, "Exporter en JSON", "Toutes tes données dans un fichier") {
                    exporter.launch("exit-sauvegarde-${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}.json")
                }
            }
            item {
                Row(Icons.Rounded.Upload, MaterialShapes.Bun, "Importer un JSON", "Remplace les données actuelles") {
                    importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }

            item { SectionTitle("Application") }
            item {
                val status = when (update) {
                    UpdateState.Checking -> "Recherche…"
                    UpdateState.UpToDate -> "À jour"
                    UpdateState.CheckFailed -> "Vérification impossible (réseau ?)"
                    is UpdateState.Available -> "Nouvelle version disponible"
                    is UpdateState.Downloading -> "Téléchargement…"
                    is UpdateState.Installing -> "Installation…"
                    is UpdateState.Failed -> "Échec de la mise à jour"
                    UpdateState.Idle -> "Touche pour vérifier"
                }
                Row(Icons.Rounded.SystemUpdate, MaterialShapes.Cookie7Sided, "Version ${BuildConfig.VERSION_NAME}", status) { vm.updates.checkNow() }
            }
            item { Row(Icons.Rounded.Info, MaterialShapes.Circle, "Sources et aide", "Tabac info service, OMS, 39 89", onClick = nav::sources) }
            item { Row(Icons.Rounded.Air, MaterialShapes.Clover8Leaf, "Exit", "100 % local : aucun compte, aucun serveur, aucune publicité") {} }
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
            title = { Text("Mes raisons d'arrêter") },
            text = {
                OutlinedTextField(text, { text = it.take(500) }, label = { Text("Une raison par ligne") }, minLines = 4)
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateConfig { it.copy(reasons = text.trim()) }
                    reasonsEdit = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { reasonsEdit = false }) { Text("Annuler") } },
        )
    }
    if (backupMenu) {
        AlertDialog(
            onDismissRequest = { backupMenu = false },
            title = { Text("Sauvegarde automatique") },
            text = { Text("Chaque jour, Exit enregistre « ${com.karelisio.exit.data.AutoBackup.FILE_NAME} » dans le dossier choisi. Pour restaurer : Importer un JSON.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.backupNow()
                    backupMenu = false
                }) { Text("Sauvegarder maintenant") }
            },
            dismissButton = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    TextButton(onClick = {
                        backupMenu = false
                        folderPicker.launch(null)
                    }) { Text("Changer de dossier") }
                    TextButton(onClick = {
                        vm.setAutoBackupFolder(null)
                        backupMenu = false
                    }) { Text("Désactiver") }
                }
            },
        )
    }
    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restaurer cette sauvegarde ?") },
            text = { Text("Les données actuelles (historique, envies, plan) seront remplacées par celles du fichier.") },
            confirmButton = {
                Button(onClick = {
                    vm.importJson(text)
                    pendingImport = null
                }) { Text("Restaurer") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Annuler") } },
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
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun TimeDialog(title: String, minutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
