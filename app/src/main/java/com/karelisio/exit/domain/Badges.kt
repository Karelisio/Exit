package com.karelisio.exit.domain

enum class BadgeIcon { SOS, SHIELD, STREAK, MONEY, LEAF, FLAG, REBOUND, TIMER, HEART }

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val icon: BadgeIcon,
    val progress: Float,
) {
    val achieved: Boolean get() = progress >= 1f
}

data class BadgeInputs(
    val cravingsOvercome: Int,
    val currentStreakMinutes: Long,
    val longestSmokeFreeDays: Int,
    val money: Double,
    val avoided: Int,
    val quitEver: Boolean,
    val reboundEver: Boolean,
    val onTimeCount: Int,
)

/** Badges calculés à partir de l'historique : rien n'est perdu après un écart. */
object Badges {
    private fun ratio(value: Double, goal: Double) = (value / goal).toFloat().coerceIn(0f, 1f)

    fun compute(i: BadgeInputs): List<Badge> {
        val streakDays = maxOf(i.currentStreakMinutes / (24 * 60), i.longestSmokeFreeDays.toLong()).toDouble()
        return listOf(
            Badge("sos1", "Première victoire", "Une envie surmontée avec le mode SOS", BadgeIcon.SOS, ratio(i.cravingsOvercome.toDouble(), 1.0)),
            Badge("sos10", "Bouclier", "10 envies surmontées", BadgeIcon.SHIELD, ratio(i.cravingsOvercome.toDouble(), 10.0)),
            Badge("sos50", "Forteresse", "50 envies surmontées", BadgeIcon.SHIELD, ratio(i.cravingsOvercome.toDouble(), 50.0)),
            Badge("ontime10", "Patience", "10 cigarettes après la fin du minuteur", BadgeIcon.TIMER, ratio(i.onTimeCount.toDouble(), 10.0)),
            Badge("quit", "Le grand saut", "Passer en phase d'arrêt complet", BadgeIcon.FLAG, if (i.quitEver) 1f else 0f),
            Badge("d1", "24 heures", "Une journée entière sans fumer", BadgeIcon.STREAK, ratio(streakDays, 1.0)),
            Badge("d3", "3 jours", "La nicotine a quitté ton corps", BadgeIcon.STREAK, ratio(streakDays, 3.0)),
            Badge("d7", "Une semaine", "7 jours sans fumer", BadgeIcon.STREAK, ratio(streakDays, 7.0)),
            Badge("d30", "Un mois", "30 jours sans fumer", BadgeIcon.LEAF, ratio(streakDays, 30.0)),
            Badge("d90", "Trois mois", "90 jours sans fumer", BadgeIcon.LEAF, ratio(streakDays, 90.0)),
            Badge("d365", "Un an", "365 jours sans fumer", BadgeIcon.HEART, ratio(streakDays, 365.0)),
            Badge("m50", "Tirelire", "50 € économisés", BadgeIcon.MONEY, ratio(i.money, 50.0)),
            Badge("m500", "Coffre-fort", "500 € économisés", BadgeIcon.MONEY, ratio(i.money, 500.0)),
            Badge("a100", "Cent de moins", "100 cigarettes évitées", BadgeIcon.LEAF, ratio(i.avoided.toDouble(), 100.0)),
            Badge("a1000", "Mille de moins", "1 000 cigarettes évitées", BadgeIcon.HEART, ratio(i.avoided.toDouble(), 1000.0)),
            Badge("rebound", "Rebond", "Repartir après un écart : c'est ça, avancer", BadgeIcon.REBOUND, if (i.reboundEver) 1f else 0f),
        )
    }
}
