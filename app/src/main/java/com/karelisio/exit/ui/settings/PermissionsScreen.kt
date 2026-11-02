package com.karelisio.exit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.exit.system.ExitPermission
import com.karelisio.exit.system.Permissions
import com.karelisio.exit.ui.components.ExitCard
import com.karelisio.exit.ui.components.ExitTopBar
import com.karelisio.exit.ui.components.ShapeIcon
import com.karelisio.exit.ui.components.cascade
import com.karelisio.exit.ui.navigation.Navigator

@Composable
fun PermissionsScreen(nav: Navigator) {
    val context = LocalContext.current
    val permissions = rememberPermissionController()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val shapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Gem)
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = { ExitTopBar("Autorisations", scroll, onBack = { nav.back() }) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(ExitPermission.entries) { i, p ->
                val granted = permissions.isGranted(p)
                ExitCard(modifier = Modifier.cascade(i)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShapeIcon(p.icon, shapes[i], MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (granted) "Accordée" else "Non accordée",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                        GrantButton(granted, onRequest = { permissions.request(p) })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(p.rationale, style = MaterialTheme.typography.bodyMedium)
                    Text(p.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (p == ExitPermission.BATTERY && Permissions.isHonorOrHuawei) {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { Permissions.openLaunchManager(context) }, shapes = ButtonDefaults.shapes()) {
                            Text("Ouvrir « Lancement d'applis »")
                        }
                    }
                }
            }
        }
    }
}
