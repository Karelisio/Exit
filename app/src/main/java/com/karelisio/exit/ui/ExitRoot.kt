package com.karelisio.exit.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.exit.ui.navigation.ExitNavHost
import com.karelisio.exit.ui.onboarding.OnboardingScreen
import com.karelisio.exit.ui.update.UpdateSheetHost

@Composable
fun ExitRoot(vm: AppViewModel, pendingDestination: String?, onDestinationHandled: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val current = data ?: return
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AnimatedContent(
            targetState = current.config.onboardingDone,
            transitionSpec = { (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.92f)) togetherWith fadeOut(tween(300)) },
            label = "root",
        ) { done ->
            if (done) ExitNavHost(vm, pendingDestination, onDestinationHandled) else OnboardingScreen(vm)
        }
        if (current.config.onboardingDone) UpdateSheetHost(vm.updates)
    }
}
