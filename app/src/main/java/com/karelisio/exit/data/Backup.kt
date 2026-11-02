package com.karelisio.exit.data

import kotlinx.serialization.Serializable

/** Sauvegarde locale complète (export / import JSON). */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long,
    val config: ConfigEntity,
    val smokes: List<SmokeEntity>,
    val cravings: List<CravingEntity>,
    val planEvents: List<PlanEventEntity> = emptyList(),
) {
    companion object {
        const val FORMAT = "exit-backup"
    }
}
