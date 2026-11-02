package com.karelisio.exit.domain

import com.karelisio.exit.data.CravingEntity
import com.karelisio.exit.data.SmokeEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class StatsSummary(
    val daily: List<Pair<LocalDate, Int>>,
    val weekly: List<Pair<LocalDate, Int>>,
    /** Intervalle moyen sur 7 jours (écarts de plus de 12 h ignorés : nuits). */
    val averageIntervalMinutes: Int?,
    val longestGapMinutes: Long,
    val longestSmokeFreeDays: Int,
    val hours: IntArray,
    val cravingHours: IntArray,
    val smokeTriggers: List<Pair<Trigger, Int>>,
    val cravingTriggers: List<Pair<Trigger, Int>>,
    val cravingsOvercome: Int,
    val cravingsTotal: Int,
    val earlyCount: Int,
    val total: Int,
    /** Total du temps tenu au-delà du minuteur, en minutes. */
    val bonusMinutes: Int = 0,
    val intensityStartAvg: Double? = null,
    val intensityEndAvg: Double? = null,
    /** Délai moyen réveil → première cigarette (7 derniers jours, puis les 7 d'avant). */
    val morningDelay: Int? = null,
    val previousMorningDelay: Int? = null,
) {
    val riskHour: Int? get() = hours.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
    val riskTrigger: Trigger? get() = (smokeTriggers + cravingTriggers)
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        .maxByOrNull { it.value }?.key
}

object StatsCalculator {

    fun compute(
        smokes: List<SmokeEntity>,
        cravings: List<CravingEntity>,
        startedAt: Long,
        now: Long,
        zone: ZoneId,
        days: Int = 14,
        weeks: Int = 8,
        wakeMinutes: Int = 7 * 60,
    ): StatsSummary {
        val today = PlanEngine.day(now, zone)
        val perDay = smokes.groupingBy { PlanEngine.day(it.timestamp, zone) }.eachCount()
        val daily = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (perDay[it] ?: 0) }
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekly = (weeks - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }.map { start ->
            start to (0..6).sumOf { perDay[start.plusDays(it.toLong())] ?: 0 }
        }

        val sorted = smokes.map { it.timestamp }.sorted()
        val gaps = sorted.zipWithNext { a, b -> b - a }
        val recentGaps = sorted.filter { it >= now - 7 * DAY_MS }.zipWithNext { a, b -> b - a }
            .filter { it in 1..12 * HOUR_MS }
        val sinceLast = sorted.lastOrNull()?.let { now - it } ?: 0L

        return StatsSummary(
            daily = daily,
            weekly = weekly,
            averageIntervalMinutes = recentGaps.takeIf { it.isNotEmpty() }?.average()?.let { (it / MINUTE_MS).toInt() },
            longestGapMinutes = ((gaps + sinceLast).maxOrNull() ?: 0L) / MINUTE_MS,
            longestSmokeFreeDays = longestSmokeFreeDays(perDay.keys, PlanEngine.day(startedAt, zone), today),
            hours = hourDistribution(smokes.map { it.timestamp }, zone),
            cravingHours = hourDistribution(cravings.map { it.timestamp }, zone),
            smokeTriggers = triggerCounts(smokes.map { it.trigger }),
            cravingTriggers = triggerCounts(cravings.map { it.trigger }),
            cravingsOvercome = cravings.count { it.overcome },
            cravingsTotal = cravings.size,
            earlyCount = smokes.count { it.early },
            total = smokes.size,
            bonusMinutes = smokes.sumOf { it.bonusMinutes },
            intensityStartAvg = cravings.mapNotNull { it.intensityStart }.takeIf { it.isNotEmpty() }?.average(),
            intensityEndAvg = cravings.mapNotNull { it.intensityEnd }.takeIf { it.isNotEmpty() }?.average(),
            morningDelay = morningDelay(smokes, today.minusDays(6), today, wakeMinutes, zone),
            previousMorningDelay = morningDelay(smokes, today.minusDays(13), today.minusDays(7), wakeMinutes, zone),
        )
    }

    /** Délai moyen (minutes) entre le réveil et la première cigarette, sur les jours [from, to] fumés. */
    fun morningDelay(smokes: List<SmokeEntity>, from: LocalDate, to: LocalDate, wakeMinutes: Int, zone: ZoneId): Int? {
        val firsts = smokes.groupBy { PlanEngine.day(it.timestamp, zone) }
            .filterKeys { it in from..to }
            .map { (day, list) ->
                val first = list.minOf { it.timestamp }
                ((first - PlanEngine.startOfDay(day, zone)) / MINUTE_MS - wakeMinutes).coerceAtLeast(0)
            }
        return firsts.takeIf { it.isNotEmpty() }?.average()?.toInt()
    }

    fun hourDistribution(timestamps: List<Long>, zone: ZoneId): IntArray {
        val hours = IntArray(24)
        timestamps.forEach { hours[java.time.Instant.ofEpochMilli(it).atZone(zone).hour]++ }
        return hours
    }

    fun triggerCounts(keys: List<String?>): List<Pair<Trigger, Int>> =
        keys.mapNotNull(Trigger::of).groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

    /** Plus longue série de jours complets sans cigarette (aujourd'hui exclu), depuis [start]. */
    fun longestSmokeFreeDays(smokeDays: Set<LocalDate>, start: LocalDate, today: LocalDate): Int {
        var best = 0
        var current = 0
        var d = start.plusDays(1)
        while (d.isBefore(today)) {
            if (d in smokeDays) current = 0 else { current++; best = maxOf(best, current) }
            d = d.plusDays(1)
        }
        return best
    }

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
}
