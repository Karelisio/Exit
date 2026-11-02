package com.karelisio.exit.domain

import com.karelisio.exit.data.ConfigEntity
import com.karelisio.exit.data.CravingEntity
import com.karelisio.exit.data.SmokeEntity
import com.karelisio.exit.update.UpdateManager
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GainsAndStatsTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val start = LocalDate.of(2026, 3, 2)
    private fun at(day: Long, h: Int, m: Int = 0) = start.plusDays(day).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `gains, cigarettes évitées, argent et temps de vie`() {
        val c = ConfigEntity(cigsPerDay = 10, packPrice = 12.0, cigsPerPack = 20, startedAt = at(0, 0))
        val smokes = (0 until 20).map { SmokeEntity(id = it.toLong() + 1, timestamp = at(it / 5L, 8 + it % 5)) }
        val gains = GainsCalculator.compute(c, smokes, at(4, 0), zone)
        // 4 jours × 10 = 40 attendues, 20 fumées.
        assertEquals(20, gains.avoided)
        assertEquals(12.0, gains.money, 0.001)
        assertEquals(400L, gains.lifeMinutes)
    }

    @Test
    fun `jours sans fumer comptés sans remise à zéro`() {
        val c = ConfigEntity(cigsPerDay = 10, startedAt = at(0, 9))
        // Jours 1, 2 sans fumer, écart jour 3, jours 4, 5 sans fumer.
        val smokes = listOf(SmokeEntity(id = 1, timestamp = at(0, 10)), SmokeEntity(id = 2, timestamp = at(3, 12)))
        val gains = GainsCalculator.compute(c, smokes, at(6, 12), zone)
        assertEquals(4, gains.smokeFreeDays)
        val longest = StatsCalculator.longestSmokeFreeDays(setOf(start, start.plusDays(3)), start, start.plusDays(6))
        assertEquals(2, longest)
    }

    @Test
    fun `stats, heures, déclencheurs et intervalle moyen`() {
        val smokes = listOf(
            SmokeEntity(id = 1, timestamp = at(0, 8), trigger = "CAFE"),
            SmokeEntity(id = 2, timestamp = at(0, 10), trigger = "CAFE"),
            SmokeEntity(id = 3, timestamp = at(0, 13), trigger = "REPAS", early = true),
        )
        val cravings = listOf(CravingEntity(id = 1, timestamp = at(0, 16), overcome = true, trigger = "STRESS"))
        val s = StatsCalculator.compute(smokes, cravings, at(0, 0), at(0, 20), zone)
        assertEquals(150, s.averageIntervalMinutes)
        assertEquals(Trigger.CAFE, s.smokeTriggers.first().first)
        assertEquals(1, s.hours[8])
        assertEquals(1, s.cravingsOvercome)
        assertEquals(1, s.earlyCount)
        assertEquals(Trigger.CAFE, s.riskTrigger)
    }

    @Test
    fun `heures à risque, au moins 4 occurrences sur 14 jours`() {
        val smokes = (0 until 5).map { SmokeEntity(id = it + 1L, timestamp = at(it.toLong(), 10, 15), trigger = "CAFE") } +
            (0 until 2).map { SmokeEntity(id = it + 10L, timestamp = at(it.toLong(), 18)) }
        val risk = StatsCalculator.riskHours(smokes, emptyList(), at(6, 12), zone)
        assertEquals(listOf(10 to Trigger.CAFE), risk)
    }

    @Test
    fun `jalons santé, progression vers le prochain`() {
        assertEquals(20L, HealthMilestones.next(0)!!.minutes)
        assertEquals(1, HealthMilestones.reached(25).size)
        assertTrue(HealthMilestones.progressToNext(10) in 0.49f..0.51f)
    }

    @Test
    fun `badges calculés depuis l'historique`() {
        val badges = Badges.compute(BadgeInputs(1, 3 * 24 * 60L, 0, 60.0, 50, quitEver = true, reboundEver = false, onTimeCount = 0))
        val achieved = badges.filter { it.achieved }.map { it.id }.toSet()
        assertTrue(achieved.containsAll(setOf("sos1", "quit", "d1", "d3", "m50")))
        assertTrue("d7" !in achieved)
    }

    @Test
    fun `comparaison de versions`() {
        assertTrue(UpdateManager.compareVersions("1.2.0", "1.1.9") > 0)
        assertTrue(UpdateManager.compareVersions("1.10.0", "1.9.0") > 0)
        assertEquals(0, UpdateManager.compareVersions("1.0.0", "1.0.0-debug"))
        assertTrue(UpdateManager.compareVersions("1.0.0", "1.0.1") < 0)
    }
}
