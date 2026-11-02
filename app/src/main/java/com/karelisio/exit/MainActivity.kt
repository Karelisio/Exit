package com.karelisio.exit

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.animation.doOnEnd
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.getValue
import com.karelisio.exit.ui.AppViewModel
import com.karelisio.exit.ui.ExitRoot
import com.karelisio.exit.ui.theme.ExitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var vm: AppViewModel
    private val destination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = appContainer
        vm = ViewModelProvider(this, viewModelFactory { initializer { AppViewModel(container) } })[AppViewModel::class.java]
        splash.setKeepOnScreenCondition { vm.data.value == null }
        splash.setOnExitAnimationListener { provider ->
            val icon: View = provider.iconView
            val scale = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.6f)
            val scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.6f)
            val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
            AnimatorSet().apply {
                playTogether(scale, scaleY, fade)
                interpolator = AnticipateInterpolator(1.2f)
                duration = 420
                doOnEnd { provider.remove() }
                start()
            }
        }

        if (savedInstanceState == null) destination.value = intent.getStringExtra(EXTRA_DESTINATION)
        pushShortcuts()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.updates.confirmation.filterNotNull().collect { confirm ->
                    container.updates.onConfirmationShown()
                    runCatching { startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }

        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val pending by destination.collectAsStateWithLifecycle()
            ExitTheme(dynamicColor = data?.config?.dynamicColor ?: true) {
                ExitRoot(vm, pending) { destination.value = null }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appContainer.updates.checkIfStale()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_DESTINATION)?.let { destination.value = it }
    }

    /** Raccourci « SOS envie » (appui long sur l'icône). */
    private fun pushShortcuts() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_DESTINATION, DEST_SOS)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val shortcut = ShortcutInfoCompat.Builder(this, "sos")
            .setShortLabel(getString(R.string.shortcut_sos))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_sos))
            .setIntent(intent)
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(this, shortcut) }
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
        const val DEST_SOS = "sos"
        const val DEST_PLAN = "plan"
        const val DEST_PROGRESS = "progress"
    }
}
