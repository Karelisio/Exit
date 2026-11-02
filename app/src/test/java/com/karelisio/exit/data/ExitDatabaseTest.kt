package com.karelisio.exit.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Données réelles : une base créée par la 1.0.0 (schéma exporté 1.json) passe en version 2 sans perte,
 * et une sauvegarde JSON exportée se réimporte à l'identique.
 */
@RunWith(AndroidJUnit4::class)
class ExitDatabaseTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @After
    fun cleanUp() {
        context.deleteDatabase(DB_V1)
        context.deleteDatabase(DB_A)
        context.deleteDatabase(DB_B)
    }

    private fun open(name: String) = Room.databaseBuilder(context, ExitDatabase::class.java, name).build()

    @Test
    fun migrationDe1Vers2SansPerteDHistorique() {
        createVersion1()
        val db = open(DB_V1)
        try {
            runBlocking {
                val smokes = db.smokes().all()
                assertEquals(2, smokes.size)
                assertEquals("CAFE", smokes.last().trigger)
                assertEquals(0, smokes.first().bonusMinutes)
                val craving = db.cravings().all().single()
                assertTrue(craving.overcome)
                assertNull(craving.intensityStart)
                val config = db.config().get()!!
                assertEquals(16, config.cigsPerDay)
                assertEquals("", config.reasons)
                assertEquals(0, config.morningDelayMinutes)
                assertNull(config.autoBackupUri)
                assertEquals("AUTO", config.themeMode)
                assertEquals("", config.triggerPlans)
                assertTrue(db.checkIns().all().isEmpty())
                // Les nouvelles colonnes s'écrivent normalement.
                db.config().upsert(config.copy(reasons = "Mieux respirer", morningDelayMinutes = 30))
                assertEquals("Mieux respirer", db.config().get()!!.reasons)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun sauvegardeExporteePuisReimporteeALIdentique() = runBlocking {
        val a = open(DB_A)
        val b = open(DB_B)
        try {
            val repoA = ExitRepository(a, json)
            repoA.completeOnboarding(12, 11.5, 20, null, com.karelisio.exit.domain.StepPeriod.WEEK, 10, "Ma santé\nMes proches")
            repoA.logSmoke(1_000_000L, "STRESS", null)
            repoA.logSmoke(5_000_000L, "PERSO", "Pause au bureau")
            repoA.logCraving(true, "CAFE", null, 240, intensityStart = 4)
            repoA.setLastCravingEndIntensity(2)
            repoA.updateConfig { it.copy(savingsGoalLabel = "Vélo", savingsGoalAmount = 400.0) }
            repoA.setTriggerPlan("CAFE", "Un verre d'eau")
            repoA.saveCheckIn(CheckInEntity(epochDay = 20000, timestamp = 1L, mood = 4, note = "Bonne journée", hunger = 2))
            val exported = repoA.exportJson()

            val repoB = ExitRepository(b, json)
            repoB.importJson(exported)
            assertEquals(a.smokes().all(), b.smokes().all())
            assertEquals(a.cravings().all(), b.cravings().all())
            assertEquals(a.config().get(), b.config().get())
            assertEquals(2, b.cravings().all().single().intensityEnd)
            assertEquals("Vélo", b.config().get()!!.savingsGoalLabel)
            assertEquals("Un verre d'eau", TriggerPlans.parse(b.config().get()!!.triggerPlans)["CAFE"])
            assertEquals(a.checkIns().all(), b.checkIns().all())
        } finally {
            a.close()
            b.close()
        }
    }

    @Test
    fun modeSoireeHorsMinuteurEtBilan() = runBlocking {
        val db = open(DB_A)
        try {
            val repo = ExitRepository(db, json)
            repo.completeOnboarding(10, 12.0, 20, null, com.karelisio.exit.domain.StepPeriod.DAY, 5, "")
            repo.logSmoke()
            repo.startParty(2)
            repeat(3) { repo.logSmoke() }
            val party = db.smokes().all().filter { it.party }
            assertEquals(3, party.size)
            assertTrue(party.none { it.early })
            repo.endParty()
            val c = db.config().get()!!
            assertNull(c.partyStartedAt)
            assertTrue(c.partySummary!!.contains("3"))
            assertEquals("PARTY", db.planEvents().all().first().type)
            // Après la soirée, les cigarettes ne sont plus marquées « soirée ».
            assertTrue(!repo.logSmoke().smoke.party)
        } finally {
            db.close()
        }
    }

    @Test(expected = Exception::class)
    fun importRefuseUnFichierQuiNEstPasUneSauvegarde() = runBlocking<Unit> {
        val db = open(DB_A)
        try {
            ExitRepository(db, json).importJson("""{"format":"autre","exportedAt":0}""")
        } finally {
            db.close()
        }
    }

    /** Recrée la base telle que la 1.0.0 l'écrivait, à partir du schéma exporté. */
    private fun createVersion1() {
        val schema = Json.parseToJsonElement(File(SCHEMA_V1).readText()).jsonObject["database"]!!.jsonObject
        context.deleteDatabase(DB_V1)
        val file = context.getDatabasePath(DB_V1).apply { parentFile?.mkdirs() }
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            schema["entities"]!!.jsonArray.forEach { e ->
                val entity = e.jsonObject
                val table = entity["tableName"]!!.jsonPrimitive.content
                sql.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach {
                    sql.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
            sql.execSQL("INSERT INTO smokes (timestamp, early, `trigger`, note, slip) VALUES (1000, 0, 'CAFE', NULL, 0)")
            sql.execSQL("INSERT INTO smokes (timestamp, early, `trigger`, note, slip) VALUES (9000, 1, NULL, NULL, 0)")
            sql.execSQL("INSERT INTO cravings (timestamp, overcome, `trigger`, note, durationSeconds) VALUES (5000, 1, 'STRESS', NULL, 300)")
            val columns = schema["entities"]!!.jsonArray.map { it.jsonObject }.single { it["tableName"]!!.jsonPrimitive.content == "config" }
                .getValue("fields").jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
            val values = columns.map { col ->
                when (col) {
                    "id" -> "0"; "onboardingDone" -> "1"; "cigsPerDay" -> "16"; "packPrice" -> "12.5"; "cigsPerPack" -> "20"
                    "quitEpochDay", "quitStartedAt", "lastAutoAdjustAt", "adjustmentMessage" -> "NULL"
                    "stepPeriod" -> "'DAY'"; "phase" -> "'SPACING'"
                    "breathInhaleSeconds", "breathExhaleSeconds" -> "5.0"
                    "planAnchorPerDay" -> "16.0"
                    else -> "0"
                }
            }
            sql.execSQL("INSERT INTO config (${columns.joinToString { "`$it`" }}) VALUES (${values.joinToString()})")
            sql.version = 1
        } finally {
            sql.close()
        }
    }

    private companion object {
        const val DB_V1 = "exit-test-v1.db"
        const val DB_A = "exit-test-a.db"
        const val DB_B = "exit-test-b.db"
        const val SCHEMA_V1 = "schemas/com.karelisio.exit.data.ExitDatabase/1.json"
    }
}
