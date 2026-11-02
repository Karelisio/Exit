package com.karelisio.exit.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.karelisio.exit.MainActivity
import com.karelisio.exit.ui.AppViewModel
import com.karelisio.exit.ui.UiEvent
import com.karelisio.exit.ui.components.rememberHaptics
import com.karelisio.exit.ui.history.HistoryScreen
import com.karelisio.exit.ui.home.HomeScreen
import com.karelisio.exit.ui.plan.PlanScreen
import com.karelisio.exit.ui.progress.ProgressScreen
import com.karelisio.exit.ui.progress.SourcesScreen
import com.karelisio.exit.ui.settings.PermissionsScreen
import com.karelisio.exit.ui.settings.SettingsScreen
import com.karelisio.exit.ui.sos.SosScreen
import com.karelisio.exit.ui.stats.StatsScreen

/** Actions de navigation partagées par les écrans. */
class Navigator(private val nav: NavHostController) {
    fun back() = nav.popBackStack()
    fun sos() = nav.navigate(SosRoute) { launchSingleTop = true }
    fun plan() = nav.navigate(PlanRoute) { launchSingleTop = true }
    fun settings() = nav.navigate(SettingsRoute) { launchSingleTop = true }
    fun permissions() = nav.navigate(PermissionsRoute) { launchSingleTop = true }
    fun sources() = nav.navigate(SourcesRoute) { launchSingleTop = true }
    fun review() = nav.navigate(ReviewRoute) { launchSingleTop = true }
    fun tab(item: TopLevel) = nav.navigate(item.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavDestination?.isTopLevel() = TopLevel.entries.any { this?.hasRoute(it.route::class) == true }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() =
    initialState.destination.isTopLevel() && targetState.destination.isTopLevel()

@Composable
fun ExitNavHost(vm: AppViewModel, pendingDestination: String?, onDestinationHandled: () -> Unit) {
    val nav = rememberNavController()
    val navigator = remember(nav) { Navigator(nav) }
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    val entry by nav.currentBackStackEntryAsState()
    val topLevel = entry?.destination.isTopLevel()

    LaunchedEffect(pendingDestination) {
        when (pendingDestination) {
            MainActivity.DEST_SOS -> navigator.sos()
            MainActivity.DEST_PLAN -> navigator.plan()
            MainActivity.DEST_PROGRESS -> navigator.tab(TopLevel.PROGRESS)
            MainActivity.DEST_REVIEW -> navigator.review()
            else -> Unit
        }
        if (pendingDestination != null) onDestinationHandled()
    }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            if (event is UiEvent.Message) snackbar.showSnackbar(event.text)
        }
    }

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (betweenTabs()) fadeIn(tween(220, delayMillis = 70)) + scaleIn(tween(260, delayMillis = 70), initialScale = 0.96f)
        else slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(240))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (betweenTabs()) fadeOut(tween(90))
        else slideOutHorizontally(tween(320)) { -it / 6 } + fadeOut(tween(200))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(tween(320)) { -it / 6 } + fadeIn(tween(240))
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(200)) + scaleOut(tween(320), targetScale = 0.92f)
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    AnimatedVisibility(
                        visible = topLevel,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        NavigationBar {
                            TopLevel.entries.forEach { item ->
                                val selected = entry?.destination?.hasRoute(item.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) haptics.tick()
                                        navigator.tab(item)
                                    },
                                    icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                                    label = { Text(item.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                    NavHost(
                        navController = nav,
                        startDestination = HomeRoute,
                        enterTransition = enter,
                        exitTransition = exit,
                        popEnterTransition = popEnter,
                        popExitTransition = popExit,
                    ) {
                        composable<HomeRoute> { Scoped(this) { HomeScreen(vm, navigator) } }
                        composable<HistoryRoute> { Scoped(this) { HistoryScreen(vm, navigator) } }
                        composable<ProgressRoute> { Scoped(this) { ProgressScreen(vm, navigator) } }
                        composable<StatsRoute> { Scoped(this) { StatsScreen(vm, navigator) } }
                        composable<PlanRoute> { Scoped(this) { PlanScreen(vm, navigator) } }
                        composable<SosRoute>(
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(200)) },
                            popEnterTransition = { fadeIn(tween(300)) },
                            popExitTransition = { fadeOut(tween(250)) + scaleOut(tween(300), targetScale = 0.9f) },
                        ) { Scoped(this) { SosScreen(vm, navigator) } }
                        composable<SettingsRoute> { Scoped(this) { SettingsScreen(vm, navigator) } }
                        composable<PermissionsRoute> { Scoped(this) { PermissionsScreen(navigator) } }
                        composable<SourcesRoute> { Scoped(this) { SourcesScreen(navigator) } }
                        composable<ReviewRoute> { Scoped(this) { com.karelisio.exit.ui.review.ReviewScreen(vm, navigator) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Scoped(scope: androidx.compose.animation.AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimScope provides scope, content = content)
}
