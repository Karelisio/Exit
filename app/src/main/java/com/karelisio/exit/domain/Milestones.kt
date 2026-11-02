package com.karelisio.exit.domain

enum class HealthSource(val label: String, val url: String) {
    TIS("Tabac info service", "https://www.tabac-info-service.fr/je-me-prepare-a-arreter/les-benefices-de-l-arret"),
    OMS("OMS", "https://www.who.int/news-room/questions-and-answers/item/tobacco-health-benefits-of-smoking-cessation"),
}

/** Jalon santé, atteint après [minutes] sans fumer. */
data class HealthMilestone(
    val minutes: Long,
    val label: String,
    val title: String,
    val description: String,
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
        HealthMilestone(20, "20 min", "Cœur apaisé", "La pression sanguine et les pulsations du cœur redeviennent normales.", HealthSource.TIS),
        HealthMilestone(8 * H, "8 h", "Oxygène retrouvé", "La quantité de monoxyde de carbone dans le sang diminue de moitié. L'oxygénation des cellules redevient normale.", HealthSource.TIS),
        HealthMilestone(24 * H, "24 h", "Zéro nicotine", "Le risque d'infarctus diminue déjà. Les poumons commencent à éliminer le mucus et les résidus de fumée. Le corps ne contient plus de nicotine.", HealthSource.TIS),
        HealthMilestone(48 * H, "48 h", "Goût et odorat", "Le goût et l'odorat s'améliorent. Les terminaisons nerveuses gustatives commencent à repousser.", HealthSource.TIS),
        HealthMilestone(72 * H, "72 h", "Respirer mieux", "Respirer devient plus facile. Les bronches commencent à se relâcher et on se sent plus énergique.", HealthSource.TIS),
        HealthMilestone(14 * D, "2 semaines", "Circulation", "La circulation sanguine s'améliore et la fonction pulmonaire augmente (2 semaines à 3 mois).", HealthSource.OMS),
        HealthMilestone(30 * D, "1 mois", "Moins de toux", "La toux et la fatigue diminuent. On récupère du souffle et on marche plus facilement.", HealthSource.TIS),
        HealthMilestone(90 * D, "3 mois", "Poumons plus forts", "La fonction pulmonaire s'améliore nettement. Les cils bronchiques repoussent et l'essoufflement diminue (1 à 9 mois).", HealthSource.OMS),
        HealthMilestone(365 * D, "1 an", "Cœur protégé", "Le risque de maladie coronarienne est réduit de moitié par rapport à celui d'un fumeur.", HealthSource.OMS),
        HealthMilestone(5 * 365 * D, "5 ans", "AVC", "Le risque d'accident vasculaire cérébral rejoint celui d'un non-fumeur (5 à 15 ans). Le risque de cancer de la bouche, de la gorge et de l'œsophage est divisé par deux.", HealthSource.OMS),
        HealthMilestone(10 * 365 * D, "10 ans", "Cancer du poumon", "Le risque de cancer du poumon est environ moitié moindre que celui d'un fumeur.", HealthSource.OMS),
        HealthMilestone(15 * 365 * D, "15 ans", "Comme un non-fumeur", "Le risque de maladie coronarienne est celui d'un non-fumeur.", HealthSource.OMS),
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
