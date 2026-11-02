package com.karelisio.exit.ui.components

import com.karelisio.exit.domain.label
import com.karelisio.exit.domain.title
import com.karelisio.exit.domain.description
import com.karelisio.exit.domain.rationale
import com.karelisio.exit.domain.detail
import androidx.compose.ui.res.stringResource
import com.karelisio.exit.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.exit.domain.Trigger

/** Tags de contexte optionnels (café, stress, après repas…) ; « Perso » ouvre un champ libre. */
@Composable
fun TriggerChips(
    selected: Trigger?,
    note: String,
    onSelect: (Trigger?) -> Unit,
    onNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Trigger.entries.forEach { t ->
                FilterChip(
                    selected = selected == t,
                    onClick = {
                        haptics.tick()
                        onSelect(if (selected == t) null else t)
                    },
                    label = { Text("${t.emoji}  ${t.label}") },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
        AnimatedVisibility(
            visible = selected == Trigger.PERSO,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            OutlinedTextField(
                value = note,
                onValueChange = { onNote(it.take(40)) },
                label = { Text(stringResource(R.string.components_ton_contexte)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
