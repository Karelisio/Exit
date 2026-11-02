package com.karelisio.exit.data

import com.karelisio.exit.plural
import androidx.room.withTransaction
import com.karelisio.exit.R
import com.karelisio.exit.str
import com.karelisio.exit.domain.DAY_MS
import com.karelisio.exit.domain.HOUR_MS
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

    suspend fun allSmokes(): List<SmokeEntity> = db.smokes().all()

    suspend fun allCravings(): List<CravingEntity> = db.cravings().all()

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
        reasons: String = "",
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
                reasons = reasons.trim(),
            ),
        )
        if (!current.onboardingDone) event(PlanEventType.START, plural(R.plurals.plan_start_message, cigsPerDay, cigsPerDay))
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
        val day = PlanEngine.day(at, zone)
        val before = db.smokes().all().filter { it.timestamp <= at }
        val previous = before.firstOrNull()?.timestamp
        val countToday = before.count { PlanEngine.day(it.timestamp, zone) == day }
        val next = PlanEngine.snapshot(c, previous, countToday, at, zone).nextAllowedAt
        val quit = PlanEngine.phase(c) == Phase.QUIT
        val early = !quit && next != null && at < next
        // Temps tenu au-delà de la fin du minuteur (les nuits ne comptent pas).
        val bonus = if (!quit && next != null && at > next && at - next < 6 * HOUR_MS) ((at - next) / MINUTE_MS).toInt() else 0
        val smoke = SmokeEntity(timestamp = at, early = early, trigger = trigger, note = note, slip = quit, bonusMinutes = bonus)
        val id = db.smokes().insert(smoke)
        if (quit) event(PlanEventType.SLIP, str(R.string.data_un_ecart_note_sans_jugement), at)
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

    suspend fun logCraving(overcome: Boolean, trigger: String?, note: String?, durationSeconds: Int, intensityStart: Int? = null): Long {
        val id = db.cravings().insert(
            CravingEntity(
                timestamp = clock(), overcome = overcome, trigger = trigger, note = note,
                durationSeconds = durationSeconds, intensityStart = intensityStart,
            ),
        )
        changed()
        return id
    }

    /** Intensité ressentie à la fin du SOS, enregistrée sur la dernière envie. */
    suspend fun setLastCravingEndIntensity(value: Int) {
        val last = db.cravings().last() ?: return
        db.cravings().update(last.copy(intensityEnd = value.coerceIn(1, 5)))
        changed()
    }

    suspend fun deleteCraving(craving: CravingEntity) {
        db.cravings().delete(craving)
        changed()
    }

    // --- Plan ---------------------------------------------------------------------------------

    /** Vérifie un éventuel dérapage et réajuste le plan en douceur. Renvoie l'ajustement appliqué. */
    suspend fun runDriftCheck(now: Long = clock()): String? {
        val c = config()
        if (!c.onboardingDone) return null
        val zone = zone()
        val perDay = db.smokes().since(now - 5 * DAY_MS).groupingBy { PlanEngine.day(it.timestamp, zone) }.eachCount()
        val adjustment = PlanEngine.driftAdjustment(c, { perDay[it] ?: 0 }, now, zone) ?: return null
        val message = buildString {
            append(str(R.string.adjust_message, adjustment.anchorPerDay.roundToInt(), PlanEngine.formatDuration(adjustment.intervalMinutes)))
            adjustment.pushedTo?.let {
                append(' ')
                append(str(R.string.adjust_message_pushed, it.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM"))))
            }
        }
        db.config().upsert(
            c.copy(
                planAnchorAt = adjustment.anchorAt,
                planAnchorPerDay = adjustment.anchorPerDay,
                quitEpochDay = adjustment.quitEpochDay,
                manualOffsetMinutes = 0,
                lastAutoAdjustAt = now,
                adjustmentMessage = message,
            ),
        )
        event(PlanEventType.AUTO_ADJUST, message, now)
        changed()
        return message
    }

    suspend fun dismissAdjustmentMessage() {
        db.config().upsert(config().copy(adjustmentMessage = null))
    }

    suspend fun adjustOffset(deltaMinutes: Int) {
        val c = config()
        val offset = (c.manualOffsetMinutes + deltaMinutes).coerceIn(-240, 600)
        db.config().upsert(c.copy(manualOffsetMinutes = offset))
        val sign = if (deltaMinutes > 0) "+" else "−"
        event(PlanEventType.MANUAL_ADJUST, str(R.string.data_intervalle_ajuste_a_la_main, sign, kotlin.math.abs(deltaMinutes)))
        changed()
    }

    suspend fun setQuitDay(day: LocalDate?) {
        val c = config()
        db.config().upsert(c.copy(quitEpochDay = day?.toEpochDay()))
        event(PlanEventType.MANUAL_ADJUST, if (day == null) str(R.string.data_plus_de_date_cible_on) else str(R.string.data_nouvelle_date_d_arret_choisie))
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
        event(PlanEventType.MANUAL_ADJUST, str(R.string.data_plan_recalcule_depuis_aujourd_hui, perDay.roundToInt()))
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
        event(PlanEventType.QUIT, str(R.string.data_arret_complet_c_est_parti))
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
        event(PlanEventType.RESUME_PLAN, str(R.string.data_on_repart_sur_le_plan))
        changed()
    }

    /** Enregistre la date de la dernière sauvegarde automatique (sans replanifier quoi que ce soit). */
    suspend fun markAutoBackup(at: Long) {
        db.config().upsert(config().copy(lastAutoBackupAt = at))
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
        require(backup.format == Backup.FORMAT) { str(R.string.data_format_inconnu) }
        db.withTransaction {
            db.smokes().clear()
            db.cravings().clear()
            db.planEvents().clear()
            db.config().upsert(backup.config.copy(id = 0))
            db.smokes().insertAll(backup.smokes)
            db.cravings().insertAll(backup.cravings)
            db.planEvents().insertAll(backup.planEvents)
        }
        event(PlanEventType.IMPORT, str(R.string.data_sauvegarde_restauree))
        changed()
        return backup
    }
}
