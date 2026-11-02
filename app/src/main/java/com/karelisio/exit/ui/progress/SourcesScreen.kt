package com.karelisio.exit.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.karelisio.exit.domain.HealthSource
import com.karelisio.exit.ui.components.ExitCard
import com.karelisio.exit.ui.components.ExitTopBar
import com.karelisio.exit.ui.components.cascade
import com.karelisio.exit.ui.navigation.Navigator

@Composable
fun SourcesScreen(nav: Navigator) {
    val uri = LocalUriHandler.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = { ExitTopBar("Sources", scroll, onBack = { nav.back() }) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ExitCard(modifier = Modifier.cascade(0)) {
                    Text("Jalons santé", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Les bénéfices de l'arrêt affichés dans Exit reprennent les informations publiées par Tabac info service " +
                            "(Santé publique France et Assurance Maladie) et par l'Organisation mondiale de la santé.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HealthSource.entries.forEach { source ->
                        TextButton(onClick = { uri.openUri(source.url) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                            Text("  ${source.label}")
                        }
                    }
                }
            }
            item {
                ExitCard(modifier = Modifier.cascade(1)) {
                    Text("Temps de vie regagné", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Estimation moyenne d'environ 20 minutes d'espérance de vie par cigarette (Jackson S. et al., Addiction, 2025, " +
                            "University College London). C'est un ordre de grandeur, pas une promesse individuelle.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                ExitCard(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.cascade(2)) {
                    Text("Besoin d'aide ?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Exit ne remplace pas un avis médical. Les tabacologues de Tabac info service répondent au 39 89 (service gratuit), " +
                            "et ton médecin ou ton pharmacien peut t'accompagner, notamment avec des substituts nicotiniques remboursés.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = { uri.openUri("tel:3989") }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Call, contentDescription = null)
                        Text("  Appeler le 39 89")
                    }
                }
            }
        }
    }
}
