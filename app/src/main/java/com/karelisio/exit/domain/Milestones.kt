package com.karelisio.exit.domain

import androidx.annotation.StringRes
import com.karelisio.exit.R

enum class HealthSource(@StringRes val labelRes: Int, val url: String) {
    TIS(R.string.source_tis, "https://www.tabac-info-service.fr/je-me-prepare-a-arreter/les-benefices-de-l-arret"),
    OMS(R.string.source_oms, "https://www.who.int/news-room/questions-and-answers/item/tobacco-health-benefits-of-smoking-cessation"),
}

/** Jalon santé, atteint après [minutes] sans fumer. */
data class HealthMilestone(
    val minutes: Long,
    @StringRes val labelRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val source: HealthSource,
)

/**
 * Bénéfices de l'arrêt d'après Tabac info service (Santé publique France / Assurance Maladie)
 * et l'Organisation mondiale de la santé.
 */
object HealthMilestones {
    private const val H = 60L
    private const val D = 24 * H

    val all: List<HealthMilestone> = listOf(
        HealthMilestone(20, R.string.milestone_0_label, R.string.milestone_0_title, R.string.milestone_0_text, HealthSource.TIS),
        HealthMilestone(8 * H, R.string.milestone_1_label, R.string.milestone_1_title, R.string.milestone_1_text, HealthSource.TIS),
        HealthMilestone(24 * H, R.string.milestone_2_label, R.string.milestone_2_title, R.string.milestone_2_text, HealthSource.TIS),
        HealthMilestone(48 * H, R.string.milestone_3_label, R.string.milestone_3_title, R.string.milestone_3_text, HealthSource.TIS),
        HealthMilestone(72 * H, R.string.milestone_4_label, R.string.milestone_4_title, R.string.milestone_4_text, HealthSource.TIS),
        HealthMilestone(14 * D, R.string.milestone_5_label, R.string.milestone_5_title, R.string.milestone_5_text, HealthSource.OMS),
        HealthMilestone(30 * D, R.string.milestone_6_label, R.string.milestone_6_title, R.string.milestone_6_text, HealthSource.TIS),
        HealthMilestone(90 * D, R.string.milestone_7_label, R.string.milestone_7_title, R.string.milestone_7_text, HealthSource.OMS),
        HealthMilestone(365 * D, R.string.milestone_8_label, R.string.milestone_8_title, R.string.milestone_8_text, HealthSource.OMS),
        HealthMilestone(5 * 365 * D, R.string.milestone_9_label, R.string.milestone_9_title, R.string.milestone_9_text, HealthSource.OMS),
        HealthMilestone(10 * 365 * D, R.string.milestone_10_label, R.string.milestone_10_title, R.string.milestone_10_text, HealthSource.OMS),
        HealthMilestone(15 * 365 * D, R.string.milestone_11_label, R.string.milestone_11_title, R.string.milestone_11_text, HealthSource.OMS),
    )

    fun reached(minutesSmokeFree: Long): List<HealthMilestone> = all.filter { it.minutes <= minutesSmokeFree }

    fun next(minutesSmokeFree: Long): HealthMilestone? = all.firstOrNull { it.minutes > minutesSmokeFree }

    /** Avancement vers le prochain jalon (0..1). */
    fun progressToNext(minutesSmokeFree: Long): Float {
        val next = next(minutesSmokeFree) ?: return 1f
        val prev = all.lastOrNull { it.minutes <= minutesSmokeFree }?.minutes ?: 0L
        return ((minutesSmokeFree - prev).toFloat() / (next.minutes - prev)).coerceIn(0f, 1f)
    }
}
