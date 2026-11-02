package com.karelisio.exit.domain

import com.karelisio.exit.data.ConfigEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEngineTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val start = LocalDate.of(2026, 1, 5)
    private val startMs = start.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun config(quitInDays: Long? = 30, step: StepPeriod = StepPeriod.DAY, stepMinutes: Int = 5) = ConfigEntity(
        onboardingDone = true,
        cigsPerDay = 16,
        startedAt = startMs,
        planAnchorAt = startMs,
        planAnchorPerDay = 16.0,
        quitEpochDay = quitInDays?.let { start.plusDays(it).toEpochDay() },
        stepPeriod = step.name,
        stepMinutes = stepMinutes,
    )

    @Test
    fun `avec date, l'objectif décroît jusqu'à zéro à la date d'arrêt`() {
        val c = config(quitInDays = 32)
        assertEquals(16.0, PlanEngine.targetPerDay(c, start, zone), 0.001)
        assertEquals(8.0, PlanEngine.targetPerDay(c, start.plusDays(16), zone), 0.001)
        assertEquals(0.0, PlanEngine.targetPerDay(c, start.plusDays(32), zone), 0.001)
    }

    @Test
    fun `l'intervalle découle de la durée d'éveil et de l'objectif`() {
        val c = config(quitInDays = 32)
        // 16 h d'éveil / 16 cigarettes = 60 min ; puis 16 h / 8 = 120 min.
        assertEquals(60, PlanEngine.intervalMinutes(c, start, zone))
        assertEquals(120, PlanEngine.intervalMinutes(c, start.plusDays(16), zone))
    }

    @Test
    fun `paliers hebdomadaires gardent le même intervalle toute la semaine`() {
        val c = config(quitInDays = 56, step = StepPeriod.WEEK)
        val monday = PlanEngine.intervalMinutes(c, start.plusDays(7), zone)
        assertEquals(monday, PlanEngine.intervalMinutes(c, start.plusDays(13), zone))
        assertTrue(PlanEngine.intervalMinutes(c, start.plusDays(14), zone) > monday)
    }

    @Test
    fun `sans date, l'intervalle augmente du pas à chaque jour`() {
        val c = config(quitInDays = null, stepMinutes = 5)
        assertEquals(60, PlanEngine.intervalMinutes(c, start, zone))
        assertEquals(110, PlanEngine.intervalMinutes(c, start.plusDays(10), zone))
        val projected = PlanEngine.projectedQuitDay(c, zone)
        assertNotNull(projected)
        // (960 - 60) / 5 = 180 paliers.
        assertEquals(start.plusDays(180), projected)
    }

    @Test
    fun `ajustement manuel appliqué et borné`() {
        val c = config(quitInDays = null).copy(manualOffsetMinutes = 15)
        assertEquals(75, PlanEngine.intervalMinutes(c, start, zone))
        val low = config(quitInDays = null).copy(manualOffsetMinutes = -200)
        assertEquals(PlanEngine.MIN_INTERVAL_MINUTES, PlanEngine.intervalMinutes(low, start, zone))
    }

    @Test
    fun `prochaine autorisée et progression du minuteur`() {
        val c = config(quitInDays = 32)
        val last = startMs
        val s = PlanEngine.snapshot(c, last, 1, last + 30 * MINUTE_MS, zone)
        assertEquals(last + 60 * MINUTE_MS, s.nextAllowedAt)
        assertFalse(s.isOk(last + 30 * MINUTE_MS))
        assertEquals(0.5f, s.progress(last + 30 * MINUTE_MS), 0.001f)
        assertTrue(s.isOk(last + 61 * MINUTE_MS))
        assertNull(PlanEngine.snapshot(c, null, 0, last, zone).nextAllowedAt)
    }

    @Test
    fun `dérapage détecté, réajustement à mi-chemin, date repoussée si trop raide`() {
        val c = config(quitInDays = 10)
        val now = start.plusDays(8).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        // Objectif ~3-4 par jour à J5-J7, réel 12 par jour.
        val adj = PlanEngine.driftAdjustment(c, { 12 }, now, zone)
        assertNotNull(adj)
        adj!!
        assertTrue(adj.anchorPerDay > 3.0 && adj.anchorPerDay < 12.0)
        assertTrue(adj.quitEpochDay!! > c.quitEpochDay!!)
        assertTrue(adj.message.contains("douceur"))
    }

    @Test
    fun `pas de réajustement si la consommation suit le plan`() {
        val c = config(quitInDays = 32)
        val now = start.plusDays(8).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        assertNull(PlanEngine.driftAdjustment(c, { day -> PlanEngine.targetPerDay(c, day, zone).toInt() }, now, zone))
    }

    @Test
    fun `pas de réajustement pendant l'arrêt`() {
        val c = config(quitInDays = 10).copy(phase = Phase.QUIT.name)
        val now = start.plusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()
        assertNull(PlanEngine.driftAdjustment(c, { 20 }, now, zone))
    }
}
