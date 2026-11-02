package com.karelisio.exit.data

import androidx.room.withTransaction
import com.karelisio.exit.domain.DAY_MS
import com.karelisio.exit.domain.HealthMilestones
import com.karelisio.exit.domain.MINUTE_MS
import com.karelisio.exit.domain.Phase
import com.karelisio.exit.domain.PlanAdjustment
import com.karelisio.exit.domain.PlanEngine
import com.karelisio.exit.domain.PlanEventType
import com.karelisio.exit.domain.PlanSnapshot
import com.karelisio.exit.domain.StepPeriod
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Résultat d'un « J'en fume une » : sert à l'affichage (en avance ? écart pendant l'arrêt ?). */
data class SmokeLogged(val smoke: SmokeEntity, val minutesEarly: Int)

/**
 * Accès unique aux données locales. Après chaque changement, [onChanged] replanifie
 * les alarmes et rafraîchit le widget (branché par l'AppContainer).
 */
class ExitRepository(
    private val db: ExitDatabase,
    private val json: Json,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    var onChanged: (suspend () -> Unit)? = null

    val config: Flow<ConfigEntity> = db.config().observe().map { it ?: ConfigEntity() }
    val configOrNull: Flow<ConfigEntity?> = db.config().observe()
    val smokes: Flow<List<SmokeEntity>> = db.smokes().observeAll()
    val cravings: Flow<List<CravingEntity>> = db.cravings().observeAll()
    val planEvents: Flow<List<PlanEventEntity>> = db.planEvents().observeAll()

    suspend fun config(): ConfigEntity = db.config().get() ?: ConfigEntity()

    private suspend fun changed() {
        onChanged?.invoke()
    }

    private suspend fun event(type: PlanEventType, message: String, at: Long = clock()) {
        db.planEvents().insert(PlanEventEntity(timestamp = at, type = type.name, message = message))
    }

    suspend fun snapshot(now: Long = clock()): PlanSnapshot {
        val c = config()
        val zone = zone()
        val today = PlanEngine.day(now, zone)
        val last = db.smokes().last()?.timestamp
        val count = db.smokes().countBetween(PlanEngine.startOfDay(today, zone), PlanEngine.startOfDay(today.plusDays(1), zone))
        return PlanEngine.snapshot(c, last, count, now, zone)
    }

    // --- Configuration initiale -------------------------------------------------------------

    suspend fun completeOnboarding(
        cigsPerDay: Int,
        packPrice: Double,
        cigsPerPack: Int,
        quitDay: LocalDate?,
        stepPeriod: StepPeriod,
        stepMinutes: Int,
    ) {
        val now = clock()
        val current = config()
        db.config().upsert(
            current.copy(
                onboardingDone = true,
                cigsPerDay = cigsPerDay.coerceIn(1, 80),
                packPrice = packPrice.coerceAtLeast(0.0),
                cigsPerPack = cigsPerPack.coerceIn(1, 50),
                quitEpochDay = quitDay?.toEpochDay(),
                stepPeriod = stepPeriod.name,
                stepMinutes = stepMinutes.coerceIn(1, 120),
                startedAt = if (current.onboardingDone) current.startedAt else now,
                planAnchorAt = now,
                planAnchorPerDay = cigsPerDay.coerceIn(1, 80).toDouble(),
                manualOffsetMinutes = 0,
                phase = Phase.SPACING.name,
            ),
        )
        if (!current.onboardingDone) event(PlanEventType.START, "Début du plan : $cigsPerDay cigarettes par jour au départ.")
        changed()
    }

    suspend fun updateConfig(transform: (ConfigEntity) -> ConfigEntity) {
        db.config().upsert(transform(config()))
        changed()
    }

    // --- Cigarettes ---------------------------------------------------------------------------

    suspend fun logSmoke(at: Long = clock(), trigger: String? = null, note: String? = null): SmokeLogged {
        val c = config()
        val zone = zone()
        val previous = db.smokes().all().firstOrNull { it.timestamp <= at }?.timestamp
        val interval = PlanEngine.intervalMinutes(c, PlanEngine.day(at, zone), zone)
        val next = previous?.plus(interval * MINUTE_MS)
        val quit = PlanEngine.phase(c) == Phase.QUIT
        val early = !quit && next != null && at < next
        val smoke = SmokeEntity(timestamp = at, early = early, trigger = trigger, note = note, slip = quit)
        val id = db.smokes().insert(smoke)
        if (quit) event(PlanEventType.SLIP, "Un écart, noté sans jugement. Tout ce qui a été gagné reste acquis.", at)
        changed()
        val minutesEarly = if (early) (((next ?: at) - at) / MINUTE_MS).toInt() else 0
        return SmokeLogged(smoke.copy(id = id), minutesEarly)
    }

    suspend fun setSmokeContext(id: Long, trigger: String?, note: String?) {
        val smoke = db.smokes().all().firstOrNull { it.id == id } ?: return
        db.smokes().update(smoke.copy(trigger = trigger, note = note))
        changed()
    }

    suspend fun updateSmoke(smoke: SmokeEntity) {
        db.smokes().update(smoke)
        changed()
    }

    suspend fun deleteSmoke(smoke: SmokeEntity) {
        db.smokes().delete(smoke)
        changed()
    }

    suspend fun restoreSmoke(smoke: SmokeEntity) {
        db.smokes().insertAll(listOf(smoke))
        changed()
    }

    // --- Envies -------------------------------------------------------------------------------

    suspend fun logCraving(overcome: Boolean, trigger: String?, note: String?, durationSeconds: Int): Long {
        val id = db.cravings().insert(
            CravingEntity(timestamp = clock(), overcome = overcome, trigger = trigger, note = note, durationSeconds = durationSeconds),
        )
        changed()
        return id
    }

    suspend fun deleteCraving(craving: CravingEntity) {
        db.cravings().delete(craving)
        changed()
    }

    // --- Plan ---------------------------------------------------------------------------------

    /** Vérifie un éventuel dérapage et réajuste le plan en douceur. Renvoie l'ajustement appliqué. */
    suspend fun runDriftCheck(now: Long = clock()): PlanAdjustment? {
        val c = config()
        if (!c.onboardingDone) return null
        val zone = zone()
        val perDay = db.smokes().since(now - 5 * DAY_MS).groupingBy { PlanEngine.day(it.timestamp, zone) }.eachCount()
        val adjustment = PlanEngine.driftAdjustment(c, { perDay[it] ?: 0 }, now, zone) ?: return null
        db.config().upsert(
            c.copy(
                planAnchorAt = adjustment.anchorAt,
                planAnchorPerDay = adjustment.anchorPerDay,
                quitEpochDay = adjustment.quitEpochDay,
                manualOffsetMinutes = 0,
                lastAutoAdjustAt = now,
                adjustmentMessage = adjustment.message,
            ),
        )
        event(PlanEventType.AUTO_ADJUST, adjustment.message, now)
        changed()
        return adjustment
    }

    suspend fun dismissAdjustmentMessage() {
        db.config().upsert(config().copy(adjustmentMessage = null))
    }

    suspend fun adjustOffset(deltaMinutes: Int) {
        val c = config()
        val offset = (c.manualOffsetMinutes + deltaMinutes).coerceIn(-240, 600)
        db.config().upsert(c.copy(manualOffsetMinutes = offset))
        val sign = if (deltaMinutes > 0) "+" else "−"
        event(PlanEventType.MANUAL_ADJUST, "Intervalle ajusté à la main ($sign${kotlin.math.abs(deltaMinutes)} min).")
        changed()
    }

    suspend fun setQuitDay(day: LocalDate?) {
        val c = config()
        db.config().upsert(c.copy(quitEpochDay = day?.toEpochDay()))
        event(PlanEventType.MANUAL_ADJUST, if (day == null) "Plus de date cible : on espace d'abord." else "Nouvelle date d'arrêt choisie.")
        changed()
    }

    suspend fun setStep(period: StepPeriod, minutes: Int) {
        db.config().upsert(config().copy(stepPeriod = period.name, stepMinutes = minutes.coerceIn(1, 120)))
        changed()
    }

    /** Repart d'aujourd'hui avec la consommation réelle récente (moyenne sur 3 jours). */
    suspend fun recalculateFromToday() {
        val now = clock()
        val c = config()
        val zone = zone()
        val avg = recentDailyAverage(now, zone, 3)
        val perDay = avg.coerceIn(1.0, c.cigsPerDay.toDouble().coerceAtLeast(1.0))
        db.config().upsert(
            c.copy(
                planAnchorAt = PlanEngine.startOfDay(PlanEngine.day(now, zone), zone),
                planAnchorPerDay = perDay,
                manualOffsetMinutes = 0,
            ),
        )
        event(PlanEventType.MANUAL_ADJUST, "Plan recalculé depuis aujourd'hui : environ ${perDay.roundToInt()} par jour.")
        changed()
    }

    private suspend fun recentDailyAverage(now: Long, zone: ZoneId, days: Int): Double {
        val today = PlanEngine.day(now, zone)
        val from = PlanEngine.startOfDay(today.minusDays(days.toLong()), zone)
        val to = PlanEngine.startOfDay(today, zone)
        return db.smokes().countBetween(from, to) / days.toDouble()
    }

    // --- Arrêt, écarts, reprise ---------------------------------------------------------------

    suspend fun startQuit() {
        val now = clock()
        val c = config()
        val last = db.smokes().last()?.timestamp
        val streakMinutes = ((now - (last ?: now)) / MINUTE_MS).coerceAtLeast(0)
        db.config().upsert(
            c.copy(
                phase = Phase.QUIT.name,
                quitStartedAt = now,
                lastCelebratedMilestoneMinutes = HealthMilestones.reached(streakMinutes).lastOrNull()?.minutes ?: 0,
            ),
        )
        event(PlanEventType.QUIT, "Arrêt complet : c'est parti !")
        changed()
    }

    /** Après un écart : on continue l'arrêt, la série repart de cette cigarette, rien d'autre ne change. */
    suspend fun continueQuitAfterSlip() {
        db.config().upsert(config().copy(lastCelebratedMilestoneMinutes = 0))
        changed()
    }

    /** Après un écart : retour au plan d'espacement, en repartant doucement, sans rien effacer. */
    suspend fun resumePlanAfterSlip() {
        val now = clock()
        val c = config()
        val zone = zone()
        val recent = db.smokes().countBetween(now - 3 * DAY_MS, now + 1) / 3.0
        val perDay = recent.coerceIn(2.0, c.cigsPerDay.toDouble().coerceAtLeast(2.0))
        val today = PlanEngine.day(now, zone)
        db.config().upsert(
            c.copy(
                phase = Phase.SPACING.name,
                planAnchorAt = PlanEngine.startOfDay(today, zone),
                planAnchorPerDay = perDay,
                manualOffsetMinutes = 0,
                quitEpochDay = c.quitEpochDay?.let { today.plusDays(maxOf(7L, ceil(perDay * 2).toLong())).toEpochDay() },
                lastCelebratedMilestoneMinutes = 0,
            ),
        )
        event(PlanEventType.RESUME_PLAN, "On repart sur le plan, en douceur. L'historique et les gains sont conservés.")
        changed()
    }

    suspend fun markMilestoneCelebrated(minutes: Long) {
        db.config().upsert(config().copy(lastCelebratedMilestoneMinutes = minutes))
    }

    // --- Sauvegarde ---------------------------------------------------------------------------

    suspend fun exportJson(): String = json.encodeToString(
        Backup.serializer(),
        Backup(
            exportedAt = clock(),
            config = config(),
            smokes = db.smokes().all(),
            cravings = db.cravings().all(),
            planEvents = db.planEvents().all(),
        ),
    )

    /** Remplace toutes les données par celles de la sauvegarde. Lève une exception si le fichier est invalide. */
    suspend fun importJson(text: String): Backup {
        val backup = json.decodeFromString(Backup.serializer(), text)
        require(backup.format == Backup.FORMAT) { "Format inconnu" }
        db.withTransaction {
            db.smokes().clear()
            db.cravings().clear()
            db.planEvents().clear()
            db.config().upsert(backup.config.copy(id = 0))
            db.smokes().insertAll(backup.smokes)
            db.cravings().insertAll(backup.cravings)
            db.planEvents().insertAll(backup.planEvents)
        }
        event(PlanEventType.IMPORT, "Sauvegarde restaurée.")
        changed()
        return backup
    }
}
