package com.karelisio.exit.domain

import com.karelisio.exit.data.ConfigEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** État du plan à un instant donné : intervalle, objectif du jour, prochaine cigarette « autorisée ». */
data class PlanSnapshot(
    val phase: Phase,
    val intervalMinutes: Int,
    val targetToday: Double,
    val lastSmokeAt: Long?,
    /** null : aucune cigarette encore notée, c'est OK tout de suite. */
    val nextAllowedAt: Long?,
    val todayCount: Int,
    val quitDay: LocalDate?,
    /** Jour où le plan arrive à zéro (date cible, ou projection en mode « on espace d'abord »). */
    val projectedQuitDay: LocalDate?,
    /** L'objectif est atteint : il ne reste qu'à arrêter. */
    val reachedZero: Boolean,
    /** Objectif « première du matin » du jour, si actif et pas encore de cigarette aujourd'hui. */
    val morningGoalAt: Long? = null,
) {
    fun isOk(now: Long): Boolean = nextAllowedAt == null || now >= nextAllowedAt

    fun remainingMillis(now: Long): Long = ((nextAllowedAt ?: now) - now).coerceAtLeast(0)

    /** Avancement du minuteur, de 0 (cigarette à l'instant) à 1 (prochaine autorisée). */
    fun progress(now: Long): Float {
        val last = lastSmokeAt ?: return 1f
        val next = nextAllowedAt ?: return 1f
        if (next <= last) return 1f
        return ((now - last).toFloat() / (next - last)).coerceIn(0f, 1f)
    }
}

/** Ajustement automatique proposé quand la consommation réelle dépasse nettement le plan. */
data class PlanAdjustment(
    val anchorAt: Long,
    val anchorPerDay: Double,
    val quitEpochDay: Long?,
    val message: String,
)

/**
 * Moteur d'espacement : fonctions pures, testées sur la JVM.
 *
 * Avec une date cible, l'objectif quotidien décroît linéairement depuis l'ancrage jusqu'à zéro
 * à la date d'arrêt ; l'intervalle minimum en découle (durée d'éveil / objectif).
 * Sans date, l'intervalle part de « durée d'éveil / consommation » et augmente de [ConfigEntity.stepMinutes]
 * à chaque palier (jour ou semaine). Les paliers hebdomadaires gardent le même objectif toute la semaine.
 */
object PlanEngine {

    const val MIN_INTERVAL_MINUTES = 15
    const val MAX_INTERVAL_MINUTES = 24 * 60

    /** Baisse maximale jugée tenable lors d'un réajustement : une cigarette de moins par jour. */
    private const val MAX_DAILY_DROP = 1.0
    private const val DRIFT_WINDOW_DAYS = 3
    private const val DRIFT_COOLDOWN_MS = 3 * DAY_MS

    fun phase(c: ConfigEntity): Phase = runCatching { Phase.valueOf(c.phase) }.getOrDefault(Phase.SPACING)

    fun stepPeriod(c: ConfigEntity): StepPeriod =
        runCatching { StepPeriod.valueOf(c.stepPeriod) }.getOrDefault(StepPeriod.DAY)

    fun awakeMinutes(c: ConfigEntity): Int {
        val span = Math.floorMod(c.sleepMinutes - c.wakeMinutes, 24 * 60)
        return if (span < 4 * 60) 16 * 60 else span
    }

    fun day(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOfDay(day: LocalDate, zone: ZoneId): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    fun anchorDay(c: ConfigEntity, zone: ZoneId): LocalDate = day(c.planAnchorAt, zone)

    fun quitDay(c: ConfigEntity): LocalDate? = c.quitEpochDay?.let(LocalDate::ofEpochDay)

    private fun anchorPerDay(c: ConfigEntity): Double = c.planAnchorPerDay.coerceIn(1.0, 80.0)

    fun baseIntervalMinutes(c: ConfigEntity): Double = awakeMinutes(c) / anchorPerDay(c)

    /** Jours écoulés depuis l'ancrage, arrondis au palier (jour ou semaine). */
    private fun steppedDays(c: ConfigEntity, day: LocalDate, zone: ZoneId): Long {
        val days = ChronoUnit.DAYS.between(anchorDay(c, zone), day).coerceAtLeast(0)
        return if (stepPeriod(c) == StepPeriod.WEEK) days / 7 * 7 else days
    }

    /** Objectif (cigarettes par jour) prévu par le plan pour [day]. */
    fun targetPerDay(c: ConfigEntity, day: LocalDate, zone: ZoneId): Double {
        val quit = quitDay(c)
        return if (quit != null) {
            val total = ChronoUnit.DAYS.between(anchorDay(c, zone), quit).coerceAtLeast(1)
            val fraction = (steppedDays(c, day, zone).toDouble() / total).coerceIn(0.0, 1.0)
            if (!day.isBefore(quit)) 0.0 else anchorPerDay(c) * (1 - fraction)
        } else {
            awakeMinutes(c) / rawSpacingInterval(c, day, zone)
        }
    }

    private fun rawSpacingInterval(c: ConfigEntity, day: LocalDate, zone: ZoneId): Double {
        val periods = steppedDays(c, day, zone) / if (stepPeriod(c) == StepPeriod.WEEK) 7 else 1
        return baseIntervalMinutes(c) + c.stepMinutes.coerceAtLeast(0) * periods
    }

    /** Intervalle minimum entre deux cigarettes, en minutes, pour [day]. */
    fun intervalMinutes(c: ConfigEntity, day: LocalDate, zone: ZoneId): Int {
        val raw = if (quitDay(c) != null) {
            awakeMinutes(c) / max(targetPerDay(c, day, zone), 1.0)
        } else {
            rawSpacingInterval(c, day, zone)
        }
        return (raw.roundToInt() + c.manualOffsetMinutes).coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
    }

    /** Jour où le plan atteint zéro. */
    fun projectedQuitDay(c: ConfigEntity, zone: ZoneId): LocalDate? {
        quitDay(c)?.let { return it }
        val step = c.stepMinutes
        if (step <= 0) return null
        val missing = awakeMinutes(c) - baseIntervalMinutes(c) - c.manualOffsetMinutes
        val periods = ceil(missing / step).toLong().coerceAtLeast(0)
        val days = periods * if (stepPeriod(c) == StepPeriod.WEEK) 7 else 1
        return anchorDay(c, zone).plusDays(days)
    }

    fun snapshot(c: ConfigEntity, lastSmokeAt: Long?, todayCount: Int, now: Long, zone: ZoneId): PlanSnapshot {
        val today = day(now, zone)
        val interval = intervalMinutes(c, today, zone)
        val projected = projectedQuitDay(c, zone)
        val morning = if (phase(c) == Phase.SPACING && c.morningDelayMinutes > 0 && todayCount == 0) {
            startOfDay(today, zone) + (c.wakeMinutes + c.morningDelayMinutes) * MINUTE_MS
        } else {
            null
        }
        return PlanSnapshot(
            phase = phase(c),
            intervalMinutes = interval,
            targetToday = targetPerDay(c, today, zone),
            lastSmokeAt = lastSmokeAt,
            nextAllowedAt = listOfNotNull(lastSmokeAt?.plus(interval * MINUTE_MS), morning).maxOrNull(),
            morningGoalAt = morning,
            todayCount = todayCount,
            quitDay = quitDay(c),
            projectedQuitDay = projected,
            reachedZero = projected != null && !today.isBefore(projected),
        )
    }

    /** Courbe de réduction prévue : objectif par jour, de l'ancrage jusqu'à zéro (au plus un an). */
    fun curve(c: ConfigEntity, zone: ZoneId, maxDays: Int = 365): List<Pair<LocalDate, Double>> {
        val start = anchorDay(c, zone)
        val end = (projectedQuitDay(c, zone) ?: start.plusDays(maxDays.toLong()))
            .coerceAtMost(start.plusDays(maxDays.toLong()))
        val points = mutableListOf<Pair<LocalDate, Double>>()
        var d = start
        while (!d.isAfter(end)) {
            points += d to targetPerDay(c, d, zone)
            d = d.plusDays(1)
        }
        return points
    }

    /**
     * Détecte un dérapage : sur les 3 derniers jours complets, une consommation nettement au-dessus
     * de l'objectif. Le plan repart alors d'aujourd'hui, à mi-chemin entre l'objectif et le réel,
     * et la date cible recule seulement si la baisse devenait trop raide.
     */
    fun driftAdjustment(
        c: ConfigEntity,
        dailyCount: (LocalDate) -> Int,
        now: Long,
        zone: ZoneId,
    ): PlanAdjustment? {
        if (phase(c) != Phase.SPACING) return null
        if (c.lastAutoAdjustAt != null && now - c.lastAutoAdjustAt < DRIFT_COOLDOWN_MS) return null
        val today = day(now, zone)
        val days = (1..DRIFT_WINDOW_DAYS).map { today.minusDays(it.toLong()) }
        if (days.last().isBefore(anchorDay(c, zone))) return null

        val actual = days.map(dailyCount).average()
        val target = days.map { targetPerDay(c, it, zone) }.average()
        if (actual <= target * 1.2 || actual - target < 1.0) return null

        val newPerDay = (target + (actual - target) * 0.6).coerceIn(1.0, c.cigsPerDay.toDouble().coerceAtLeast(1.0))
        val interval = (awakeMinutes(c) / newPerDay).roundToInt()
        var quitEpochDay = c.quitEpochDay
        var pushed: LocalDate? = null
        val quit = quitDay(c)
        if (quit != null) {
            val minDays = ceil(newPerDay / MAX_DAILY_DROP).toLong()
            if (ChronoUnit.DAYS.between(today, quit) < minDays) {
                pushed = today.plusDays(minDays)
                quitEpochDay = pushed.toEpochDay()
            }
        }
        val message = buildString {
            append("Les derniers jours ont été plus chargés que prévu, et c'est normal : une réduction n'est jamais une ligne droite. ")
            append("On réajuste en douceur, environ ${newPerDay.roundToInt()} par jour, une toutes les ${formatDuration(interval)}.")
            if (pushed != null) append(" La date d'arrêt passe au ${pushed.format(DATE_FORMAT)} pour garder un rythme tenable.")
        }
        return PlanAdjustment(startOfDay(today, zone), newPerDay, quitEpochDay, message)
    }

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)

    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h ${m.toString().padStart(2, '0')}"
        }
    }
}
