package com.karelisio.exit.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object ProgressRoute
@Serializable data object StatsRoute
@Serializable data object PlanRoute
@Serializable data object SosRoute
@Serializable data object SettingsRoute
@Serializable data object PermissionsRoute
@Serializable data object SourcesRoute

enum class TopLevel(val route: Any, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(HomeRoute, "Accueil", Icons.Outlined.Home, Icons.Rounded.Home),
    HISTORY(HistoryRoute, "Historique", Icons.Outlined.History, Icons.Rounded.History),
    PROGRESS(ProgressRoute, "Progrès", Icons.Outlined.EmojiEvents, Icons.Rounded.EmojiEvents),
    STATS(StatsRoute, "Stats", Icons.Outlined.BarChart, Icons.Rounded.BarChart),
}
