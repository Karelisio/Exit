package com.karelisio.exit.domain

import com.karelisio.exit.data.ConfigEntity
import com.karelisio.exit.data.SmokeEntity
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.floor

data class Gains(
    val avoided: Int,
    val money: Double,
    val lifeMinutes: Long,
    val smokedSinceStart: Int,
    /** Jours complets sans aucune cigarette depuis le début du suivi (jamais remis à zéro). */
    val smokeFreeDays: Int,
)

object GainsCalculator {

    /**
     * Temps de vie perdu par cigarette : environ 20 minutes (Jackson et al., Addiction, 2025,
     * University College London), estimation moyenne hommes / femmes.
     */
    const val LIFE_MINUTES_PER_CIGARETTE = 20

    fun pricePerCigarette(c: ConfigEntity): Double = if (c.cigsPerPack > 0) c.packPrice / c.cigsPerPack else 0.0

    fun compute(c: ConfigEntity, smokes: List<SmokeEntity>, now: Long, zone: ZoneId): Gains {
        val since = smokes.filter { it.timestamp >= c.startedAt }
        val elapsedDays = (now - c.startedAt).coerceAtLeast(0) / DAY_MS.toDouble()
        val expected = c.cigsPerDay * elapsedDays
        val avoided = floor(expected - since.size).toInt().coerceAtLeast(0)
        return Gains(
            avoided = avoided,
            money = avoided * pricePerCigarette(c),
            lifeMinutes = avoided.toLong() * LIFE_MINUTES_PER_CIGARETTE,
            smokedSinceStart = since.size,
            smokeFreeDays = smokeFreeDays(c, since, now, zone),
        )
    }

    private fun smokeFreeDays(c: ConfigEntity, smokes: List<SmokeEntity>, now: Long, zone: ZoneId): Int {
        val start = PlanEngine.day(c.startedAt, zone)
        val today = PlanEngine.day(now, zone)
        val smokeDays = smokes.mapTo(HashSet()) { PlanEngine.day(it.timestamp, zone) }
        // Jours complets après le jour de début ; aujourd'hui, pas encore terminé, est exclu.
        val first = start.plusDays(1)
        if (!first.isBefore(today)) return 0
        val total = ChronoUnit.DAYS.between(first, today).toInt()
        return (0 until total).count { first.plusDays(it.toLong()) !in smokeDays }
    }
}
