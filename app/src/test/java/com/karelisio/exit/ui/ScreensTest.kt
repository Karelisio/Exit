package com.karelisio.exit.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karelisio.exit.ExitApp
import com.karelisio.exit.domain.DAY_MS
import com.karelisio.exit.domain.HOUR_MS
import com.karelisio.exit.domain.MINUTE_MS
import com.karelisio.exit.domain.StepPeriod
import com.karelisio.exit.ui.theme.ExitTheme
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendu réel des écrans principaux (Robolectric, graphismes natifs) : vérifie qu'aucun écran ne plante
 * et, avec -PexitScreenshots=true, enregistre des captures dans app/build/screenshots.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<ExitApp>()

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        if (System.getProperty("exit.screenshots") != "true") return
        val dir = File(System.getProperty("exit.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        // Attend que l'écran ait une taille (données encore en cours de chargement au tout premier rendu).
        repeat(100) {
            if (compose.onRoot().fetchSemanticsNode().size.width > 0) return@repeat
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(50)
        }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seed(quit: Boolean = false) = runBlocking {
        val repo = app.container.repository
        val now = System.currentTimeMillis()
        repo.completeOnboarding(16, 12.5, 20, LocalDate.now().plusDays(30), StepPeriod.DAY, 5)
        repo.updateConfig { it.copy(startedAt = now - 9 * DAY_MS, planAnchorAt = now - 9 * DAY_MS) }
        for (day in 9 downTo 1) {
            repeat(14 - day / 2) { i -> repo.logSmoke(now - day * DAY_MS + i * 70 * MINUTE_MS, listOf("CAFE", "STRESS", "REPAS", null)[i % 4]) }
        }
        repo.logSmoke(now - 3 * HOUR_MS, "CAFE")
        repo.logSmoke(now - 40 * MINUTE_MS, "STRESS")
        repo.logCraving(true, "STRESS", null, 240)
        repo.logCraving(true, "CAFE", null, 300)
        if (quit) {
            repo.startQuit()
            repo.updateConfig { it.copy(quitStartedAt = now - 3 * DAY_MS - 5 * HOUR_MS) }
        }
    }

    private fun setApp() {
        compose.mainClock.autoAdvance = false
        val vm = AppViewModel(app.container)
        compose.setContent { ExitTheme(dynamicColor = false) { ExitRoot(vm, null) {} } }
        repeat(200) {
            if (vm.data.value != null) return@repeat
            Thread.sleep(50)
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test
    fun onboarding() {
        setApp()
        shot("01-onboarding-welcome")
        repeat(3) { compose.onNodeWithText("Suivant").performClick(); compose.mainClock.advanceTimeBy(800) }
        shot("02-onboarding-consumption")
        compose.onNodeWithText("Suivant").performClick()
        shot("03-onboarding-goal")
        compose.onNodeWithText("Suivant").performClick()
        shot("04-onboarding-notifications")
    }

    @Test
    fun mainScreens() {
        seed()
        setApp()
        shot("10-home")
        compose.onAllNodesWithText("Historique")[0].performClick()
        shot("11-history")
        compose.onAllNodesWithText("Progrès")[0].performClick()
        shot("12-progress")
        compose.onAllNodesWithText("Stats")[0].performClick()
        shot("13-stats")
        compose.onAllNodesWithText("Accueil")[0].performClick()
        compose.mainClock.advanceTimeBy(1000)
        clickWhenReady(androidx.compose.ui.test.hasContentDescription("Réglages"))
        compose.mainClock.advanceTimeBy(800)
        clickWhenReady(androidx.compose.ui.test.hasText("Mon plan"))
        shot("14-plan")
    }

    @Test
    fun sos() {
        seed()
        setApp()
        clickWhenReady(androidx.compose.ui.test.hasText("SOS envie"))
        shot("20-sos")
        compose.onNodeWithText("L'envie est passée 💪").performClick()
        compose.mainClock.advanceTimeBy(700)
        shot("21-sos-done")
    }

    @Test
    fun settings() {
        seed()
        setApp()
        compose.onNodeWithContentDescriptionSafe("Réglages")
        shot("30-settings")
    }

    @Test
    fun quitPhase() {
        seed(quit = true)
        setApp()
        shot("40-home-quit")
    }

    /** Police système agrandie (×1,6) : rien ne doit déborder ni planter. */
    @Test
    @Config(fontScale = 1.6f)
    fun largeFont() {
        seed()
        setApp()
        shot("60-home-large-font")
        compose.onNode(androidx.compose.ui.test.hasText("SOS envie"), useUnmergedTree = true).performClick()
        shot("61-sos-large-font")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun darkOnboarding() {
        setApp()
        shot("51-onboarding-dark")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun darkHome() {
        seed()
        setApp()
        shot("50-home-dark")
    }

    /** Attend que le nœud apparaisse (chargement des données) puis clique dessus. */
    private fun clickWhenReady(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        repeat(50) {
            if (compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) return@repeat
            compose.mainClock.advanceTimeBy(200)
            Thread.sleep(20)
        }
        compose.onAllNodes(matcher, useUnmergedTree = true)[0].performClick()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onNodeWithContentDescriptionSafe(desc: String) {
        val matcher = androidx.compose.ui.test.hasContentDescription(desc)
        repeat(50) {
            if (onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return@repeat
            mainClock.advanceTimeBy(200)
            Thread.sleep(20)
        }
        onNode(matcher).performClick()
    }
}
