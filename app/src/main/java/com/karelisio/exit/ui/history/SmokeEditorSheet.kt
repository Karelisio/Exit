package com.karelisio.exit.ui.history

import androidx.compose.ui.res.stringResource
import com.karelisio.exit.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.exit.data.SmokeEntity
import com.karelisio.exit.domain.Trigger
import com.karelisio.exit.ui.components.Fmt
import com.karelisio.exit.ui.components.TriggerChips
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Ajout d'une cigarette oubliée, ou modification (heure, date, contexte). */
@Composable
fun SmokeEditorSheet(
    initial: SmokeEntity?,
    onDismiss: () -> Unit,
    onSave: (at: Long, trigger: String?, note: String?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val zone = ZoneId.systemDefault()
    val start = remember { initial?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestamp), zone) } ?: LocalDateTime.now().minusMinutes(30) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime().withSecond(0).withNano(0)) }
    var trigger by remember { mutableStateOf(Trigger.of(initial?.trigger)) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    val inFuture = at > System.currentTimeMillis()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(if (initial == null) stringResource(R.string.history_ajouter_un_oubli) else stringResource(R.string.history_modifier), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { pickDate = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                    Text("  ${Fmt.dayLabel(date)}")
                }
                FilledTonalButton(onClick = { pickTime = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                    Text("  %02d:%02d".format(time.hour, time.minute))
                }
            }
            if (inFuture) {
                Text(stringResource(R.string.history_cette_heure_est_dans_le), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.history_contexte_facultatif), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            TriggerChips(trigger, note, { trigger = it }, { note = it })
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSave(at, trigger?.name, note.takeIf { trigger == Trigger.PERSO && it.isNotBlank() }) },
                enabled = !inFuture,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.history_enregistrer)) }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.history_supprimer), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= LocalDate.now()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.history_annuler)) } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    pickTime = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.history_annuler)) } },
            text = { TimePicker(state) },
        )
    }
}
