package com.karelisio.exit.ui.components

import androidx.compose.ui.res.stringResource
import com.karelisio.exit.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Support
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.karelisio.exit.ui.navigation.sharedContainer

/** Bouton SOS envie, présent sur tous les écrans principaux ; se transforme en écran SOS. */
@Composable
fun SosFab(onClick: () -> Unit, modifier: Modifier = Modifier, expanded: Boolean = true) {
    val haptics = rememberHaptics()
    ExtendedFloatingActionButton(
        onClick = {
            haptics.confirm()
            onClick()
        },
        expanded = expanded,
        icon = { Icon(Icons.Rounded.Support, contentDescription = null) },
        text = { Text(stringResource(R.string.components_sos_envie)) },
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
        modifier = modifier.sharedContainer("sos", MaterialTheme.shapes.large),
    )
}

@Composable
fun ExitTopBar(
    title: String,
    scroll: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    LargeTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.components_retour)) }
            }
        },
        actions = { actions() },
        scrollBehavior = scroll,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** Carte arrondie aux couleurs du conteneur de surface. */
@Composable
fun ExitCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = color)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors) {
            Column(Modifier.padding(20.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}

/** Petite statistique : valeur en gros, libellé dessous. */
@Composable
fun StatLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
