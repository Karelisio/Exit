package com.karelisio.exit.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Une cigarette fumée. `early` : avant la fin du minuteur (simplement noté, jamais bloqué). */
@Serializable
@Entity(tableName = "smokes", indices = [Index("timestamp")])
data class SmokeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val early: Boolean = false,
    /** Déclencheur optionnel (voir [com.karelisio.exit.domain.Trigger]). */
    val trigger: String? = null,
    /** Libellé libre quand le déclencheur est « perso ». */
    val note: String? = null,
    /** Fumée pendant la phase d'arrêt : c'est un écart, pas un échec. */
    @ColumnInfo(defaultValue = "0") val slip: Boolean = false,
)

/** Une envie, surmontée ou non (mode SOS). */
@Serializable
@Entity(tableName = "cravings", indices = [Index("timestamp")])
data class CravingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val overcome: Boolean,
    val trigger: String? = null,
    val note: String? = null,
    val durationSeconds: Int = 0,
)

/** Journal du plan : ajustements automatiques ou manuels, arrêt, écarts, reprise. */
@Serializable
@Entity(tableName = "plan_events", indices = [Index("timestamp")])
data class PlanEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val message: String,
)

/** Configuration et état du plan (une seule ligne). */
@Serializable
@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey val id: Int = 0,
    val onboardingDone: Boolean = false,
    /** Consommation moyenne de départ (cigarettes par jour). */
    val cigsPerDay: Int = 15,
    val packPrice: Double = 13.0,
    val cigsPerPack: Int = 20,
    /** Date cible d'arrêt (jour epoch, LocalDate.toEpochDay) ; null = « on espace d'abord ». */
    val quitEpochDay: Long? = null,
    /** Début du suivi (base du calcul des gains). */
    val startedAt: Long = System.currentTimeMillis(),
    /** DAY ou WEEK : rythme d'augmentation de l'intervalle. */
    val stepPeriod: String = "DAY",
    /** Sans date cible : minutes ajoutées à l'intervalle à chaque palier. */
    val stepMinutes: Int = 5,
    /** Fenêtre d'éveil (minutes depuis minuit) : sert à répartir les cigarettes et à éviter les notifs nocturnes. */
    val wakeMinutes: Int = 7 * 60,
    val sleepMinutes: Int = 23 * 60,
    /** Ancrage du plan : il repart d'ici (début, ou dernier ajustement). */
    val planAnchorAt: Long = System.currentTimeMillis(),
    val planAnchorPerDay: Double = 15.0,
    /** Ajustement manuel de l'intervalle, en minutes (peut être négatif). */
    val manualOffsetMinutes: Int = 0,
    /** SPACING (on espace) ou QUIT (arrêt complet). */
    val phase: String = "SPACING",
    val quitStartedAt: Long? = null,
    val lastAutoAdjustAt: Long? = null,
    /** Message bienveillant du dernier ajustement, affiché sur l'accueil jusqu'à lecture. */
    val adjustmentMessage: String? = null,
    val breathInhaleSeconds: Float = 5f,
    val breathExhaleSeconds: Float = 5f,
    val breathHaptics: Boolean = true,
    val notifyNextAllowed: Boolean = true,
    val notifyMilestones: Boolean = true,
    val dynamicColor: Boolean = true,
    /** Dernier jalon santé célébré (en minutes sans fumer), pour ne fêter chaque jalon qu'une fois. */
    val lastCelebratedMilestoneMinutes: Long = 0,
)
