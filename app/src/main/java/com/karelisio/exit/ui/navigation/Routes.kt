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
@Serializable data object ReviewRoute

enum class TopLevel(val route: Any, @androidx.annotation.StringRes val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(HomeRoute, com.karelisio.exit.R.string.nav_home, Icons.Outlined.Home, Icons.Rounded.Home),
    HISTORY(HistoryRoute, com.karelisio.exit.R.string.nav_history, Icons.Outlined.History, Icons.Rounded.History),
    PROGRESS(ProgressRoute, com.karelisio.exit.R.string.nav_progress, Icons.Outlined.EmojiEvents, Icons.Rounded.EmojiEvents),
    STATS(StatsRoute, com.karelisio.exit.R.string.nav_stats, Icons.Outlined.BarChart, Icons.Rounded.BarChart),
}
