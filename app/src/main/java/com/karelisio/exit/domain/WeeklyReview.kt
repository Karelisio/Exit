package com.karelisio.exit.domain

import com.karelisio.exit.data.ConfigEntity
import com.karelisio.exit.data.CravingEntity
import com.karelisio.exit.data.SmokeEntity
import java.time.LocalDate
import java.time.ZoneId

/** Bilan des 7 derniers jours (aujourd'hui inclus), comparé aux 7 jours précédents. */
data class WeeklyReview(
    val start: LocalDate,
    val end: LocalDate,
    val smokes: Int,
    val previousSmokes: Int,
    val cravingsOvercome: Int,
    val cravingsTotal: Int,
    val bonusMinutes: Int,
    val money: Double,
    val smokeFreeDays: Int,
    val bestDay: Pair<LocalDate, Int>?,
    val morningDelay: Int?,
    val previousMorningDelay: Int?,
) {
    /** Variation en % du nombre de cigarettes (négatif = baisse). */
    val change: Int? get() = if (previousSmokes > 0) ((smokes - previousSmokes) * 100 / previousSmokes) else null
}

object WeeklyReviewCalculator {
    fun compute(c: ConfigEntity, smokes: List<SmokeEntity>, cravings: List<CravingEntity>, today: LocalDate, zone: ZoneId): WeeklyReview {
        val start = today.minusDays(6)
        val prevStart = today.minusDays(13)
        val prevEnd = today.minusDays(7)
        fun inRange(t: Long, a: LocalDate, b: LocalDate) = PlanEngine.day(t, zone).let { it in a..b }
        val week = smokes.filter { inRange(it.timestamp, start, today) }
        val perDay = (0L..6L).map { start.plusDays(it) }.filter { it >= PlanEngine.day(c.startedAt, zone) }
            .map { d -> d to week.count { PlanEngine.day(it.timestamp, zone) == d } }
        val weekCravings = cravings.filter { inRange(it.timestamp, start, today) }
        val trackedDays = perDay.size
        val avoided = (c.cigsPerDay * trackedDays - week.size).coerceAtLeast(0)
        return WeeklyReview(
            start = start,
            end = today,
            smokes = week.size,
            previousSmokes = smokes.count { inRange(it.timestamp, prevStart, prevEnd) },
            cravingsOvercome = weekCravings.count { it.overcome },
            cravingsTotal = weekCravings.size,
            bonusMinutes = week.sumOf { it.bonusMinutes },
            money = avoided * GainsCalculator.pricePerCigarette(c),
            smokeFreeDays = perDay.count { it.second == 0 && it.first != today },
            bestDay = perDay.filter { it.first != today }.minByOrNull { it.second },
            morningDelay = StatsCalculator.morningDelay(smokes, start, today, c.wakeMinutes, zone),
            previousMorningDelay = StatsCalculator.morningDelay(smokes, prevStart, prevEnd, c.wakeMinutes, zone),
        )
    }
}
