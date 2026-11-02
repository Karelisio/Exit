package com.karelisio.exit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.exit.AppContainer
import com.karelisio.exit.data.ConfigEntity
import com.karelisio.exit.data.CravingEntity
import com.karelisio.exit.data.PlanEventEntity
import com.karelisio.exit.data.SmokeEntity
import com.karelisio.exit.data.SmokeLogged
import com.karelisio.exit.domain.StepPeriod
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Données de l'appli, chargées une fois et partagées par tous les écrans. */
data class AppData(
    val config: ConfigEntity,
    val smokes: List<SmokeEntity>,
    val cravings: List<CravingEntity>,
    val events: List<PlanEventEntity>,
) {
    val lastSmokeAt: Long? get() = smokes.firstOrNull()?.timestamp
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
}

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository

    /** null tant que la base n'a pas répondu (le splash reste affiché). */
    val data: StateFlow<AppData?> = combine(repo.configOrNull, repo.smokes, repo.cravings, repo.planEvents) { c, s, cr, e ->
        AppData(c ?: ConfigEntity(), s, cr, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _lastLogged = MutableStateFlow<SmokeLogged?>(null)

    /** Dernière cigarette notée depuis l'appli (carte de confirmation et contexte sur l'accueil). */
    val lastLogged: StateFlow<SmokeLogged?> = _lastLogged.asStateFlow()

    private val _pendingSlip = MutableStateFlow<SmokeLogged?>(null)

    /** Écart pendant l'arrêt, en attente de la feuille bienveillante sur l'accueil. */
    val pendingSlip: StateFlow<SmokeLogged?> = _pendingSlip.asStateFlow()

    fun clearLogged() {
        _lastLogged.value = null
    }

    fun clearSlip() {
        _pendingSlip.value = null
    }

    private fun onLogged(logged: SmokeLogged) {
        if (logged.smoke.slip) _pendingSlip.value = logged else _lastLogged.value = logged
    }

    val updates get() = container.updates

    fun completeOnboarding(cigsPerDay: Int, packPrice: Double, cigsPerPack: Int, quitDay: LocalDate?, step: StepPeriod, stepMinutes: Int) =
        viewModelScope.launch { repo.completeOnboarding(cigsPerDay, packPrice, cigsPerPack, quitDay, step, stepMinutes) }

    fun logSmoke(at: Long? = null, trigger: String? = null, note: String? = null) = viewModelScope.launch {
        container.notifications.cancelNextAllowed()
        val logged = if (at == null) repo.logSmoke(trigger = trigger, note = note) else repo.logSmoke(at, trigger, note)
        if (at == null) onLogged(logged)
    }

    fun setSmokeContext(id: Long, trigger: String?, note: String?) = viewModelScope.launch { repo.setSmokeContext(id, trigger, note) }
    fun updateSmoke(smoke: SmokeEntity) = viewModelScope.launch { repo.updateSmoke(smoke) }
    fun deleteSmoke(smoke: SmokeEntity) = viewModelScope.launch { repo.deleteSmoke(smoke) }
    fun restoreSmoke(smoke: SmokeEntity) = viewModelScope.launch { repo.restoreSmoke(smoke) }

    fun logCraving(overcome: Boolean, trigger: String?, note: String?, seconds: Int, alsoSmoke: Boolean = false) =
        viewModelScope.launch {
            repo.logCraving(overcome, trigger, note, seconds)
            if (alsoSmoke) onLogged(repo.logSmoke(trigger = trigger, note = note))
        }

    fun deleteCraving(craving: CravingEntity) = viewModelScope.launch { repo.deleteCraving(craving) }

    fun startQuit() = viewModelScope.launch { repo.startQuit() }
    fun continueQuitAfterSlip() = viewModelScope.launch { repo.continueQuitAfterSlip() }
    fun resumePlanAfterSlip() = viewModelScope.launch { repo.resumePlanAfterSlip() }
    fun adjustOffset(delta: Int) = viewModelScope.launch { repo.adjustOffset(delta) }
    fun setQuitDay(day: LocalDate?) = viewModelScope.launch { repo.setQuitDay(day) }
    fun setStep(period: StepPeriod, minutes: Int) = viewModelScope.launch { repo.setStep(period, minutes) }
    fun recalculateFromToday() = viewModelScope.launch { repo.recalculateFromToday() }
    fun dismissAdjustment() = viewModelScope.launch { repo.dismissAdjustmentMessage() }
    fun markMilestoneCelebrated(minutes: Long) = viewModelScope.launch { repo.markMilestoneCelebrated(minutes) }
    fun updateConfig(transform: (ConfigEntity) -> ConfigEntity) = viewModelScope.launch { repo.updateConfig(transform) }

    suspend fun exportJson(): String = repo.exportJson()

    fun importJson(text: String) = viewModelScope.launch {
        val message = runCatching { repo.importJson(text) }.fold(
            onSuccess = { "Sauvegarde restaurée : ${it.smokes.size} cigarettes, ${it.cravings.size} envies." },
            onFailure = { "Fichier invalide : ce n'est pas une sauvegarde Exit." },
        )
        _events.emit(UiEvent.Message(message))
    }

    fun message(text: String) = viewModelScope.launch { _events.emit(UiEvent.Message(text)) }
}
