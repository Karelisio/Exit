package com.karelisio.exit.ui.progress

import com.karelisio.exit.domain.label
import com.karelisio.exit.domain.title
import com.karelisio.exit.domain.description
import com.karelisio.exit.domain.rationale
import com.karelisio.exit.domain.detail
import androidx.compose.ui.res.stringResource
import com.karelisio.exit.R
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
        topBar = { ExitTopBar(stringResource(R.string.progress_sources), scroll, onBack = { nav.back() }) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ExitCard(modifier = Modifier.cascade(0)) {
                    Text(stringResource(R.string.progress_jalons_sante), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.progress_les_benefices_de_l_arret) +
                            stringResource(R.string.progress_sante_publique_france_et_assurance),
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
                    Text(stringResource(R.string.progress_temps_de_vie_regagne), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.progress_estimation_moyenne_d_environ_20) +
                            stringResource(R.string.progress_university_college_london_c_est),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                ExitCard(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.cascade(2)) {
                    Text(stringResource(R.string.progress_besoin_d_aide), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.progress_exit_ne_remplace_pas_un) +
                            stringResource(R.string.progress_et_ton_medecin_ou_ton),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = { uri.openUri("tel:3989") }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Call, contentDescription = null)
                        Text(stringResource(R.string.progress_appeler_le_39_89))
                    }
                }
            }
        }
    }
}
