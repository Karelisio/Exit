package com.karelisio.exit.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.karelisio.exit.R
import com.karelisio.exit.data.CheckInEntity
import com.karelisio.exit.ui.components.rememberHaptics
import java.time.LocalDate

val MOODS = listOf("😣", "😕", "😐", "🙂", "😄")

/** Point du jour : humeur (1 à 5), une ligne de journal, signes de sevrage de 0 à 3. */
@Composable
fun CheckInSheet(existing: CheckInEntity?, onDismiss: () -> Unit, onSave: (CheckInEntity) -> Unit) {
    val haptics = rememberHaptics()
    var mood by remember { mutableIntStateOf(existing?.mood ?: 0) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var irritability by remember { mutableIntStateOf(existing?.irritability ?: 0) }
    var sleep by remember { mutableIntStateOf(existing?.sleep ?: 0) }
    var hunger by remember { mutableIntStateOf(existing?.hunger ?: 0) }
    var anxiety by remember { mutableIntStateOf(existing?.anxiety ?: 0) }
    var focus by remember { mutableIntStateOf(existing?.focus ?: 0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.checkin_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.checkin_mood), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MOODS.forEachIndexed { i, emoji ->
                    val selected = mood == i + 1
                    val scale by animateFloatAsState(if (selected) 1.25f else 1f, spring(dampingRatio = 0.4f, stiffness = 400f), label = "mood")
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .size(52.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .clickable(role = Role.RadioButton) {
                                haptics.tick()
                                mood = i + 1
                            }
                            .semantics {
                                this.selected = selected
                                contentDescription = "${i + 1} / 5"
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(note, { note = it.take(200) }, label = { Text(stringResource(R.string.checkin_note)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.checkin_withdrawal), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.checkin_withdrawal_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Symptom(stringResource(R.string.checkin_irritability), irritability) { irritability = it }
            Symptom(stringResource(R.string.checkin_sleep), sleep) { sleep = it }
            Symptom(stringResource(R.string.checkin_hunger), hunger) { hunger = it }
            Symptom(stringResource(R.string.checkin_anxiety), anxiety) { anxiety = it }
            Symptom(stringResource(R.string.checkin_focus), focus) { focus = it }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    haptics.confirm()
                    onSave(
                        CheckInEntity(
                            epochDay = LocalDate.now().toEpochDay(),
                            timestamp = System.currentTimeMillis(),
                            mood = mood,
                            note = note.trim(),
                            irritability = irritability,
                            sleep = sleep,
                            hunger = hunger,
                            anxiety = anxiety,
                            focus = focus,
                        ),
                    )
                },
                enabled = mood > 0,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.checkin_save)) }
        }
    }
}

@Composable
private fun Symptom(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.width(184.dp)) {
            (0..3).forEach { i ->
                SegmentedButton(selected = value == i, onClick = { onChange(i) }, shape = SegmentedButtonDefaults.itemShape(i, 4), icon = {}) {
                    Text("$i")
                }
            }
        }
    }
}
