package com.karelisio.exit.domain

import androidx.annotation.StringRes
import com.karelisio.exit.R

enum class BadgeIcon { SOS, SHIELD, STREAK, MONEY, LEAF, FLAG, REBOUND, TIMER, HEART }

data class Badge(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
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
    val bonusMinutes: Int = 0,
)

/** Badges calculés à partir de l'historique : rien n'est perdu après un écart. */
object Badges {
    private fun ratio(value: Double, goal: Double) = (value / goal).toFloat().coerceIn(0f, 1f)

    fun compute(i: BadgeInputs): List<Badge> {
        val streakDays = maxOf(i.currentStreakMinutes / (24 * 60), i.longestSmokeFreeDays.toLong()).toDouble()
        return listOf(
            Badge("sos1", R.string.badge_sos1_title, R.string.badge_sos1_text, BadgeIcon.SOS, ratio(i.cravingsOvercome.toDouble(), 1.0)),
            Badge("sos10", R.string.badge_sos10_title, R.string.badge_sos10_text, BadgeIcon.SHIELD, ratio(i.cravingsOvercome.toDouble(), 10.0)),
            Badge("sos50", R.string.badge_sos50_title, R.string.badge_sos50_text, BadgeIcon.SHIELD, ratio(i.cravingsOvercome.toDouble(), 50.0)),
            Badge("ontime10", R.string.badge_ontime10_title, R.string.badge_ontime10_text, BadgeIcon.TIMER, ratio(i.onTimeCount.toDouble(), 10.0)),
            Badge("bonus", R.string.badge_bonus_title, R.string.badge_bonus_text, BadgeIcon.TIMER, ratio(i.bonusMinutes.toDouble(), 600.0)),
            Badge("quit", R.string.badge_quit_title, R.string.badge_quit_text, BadgeIcon.FLAG, if (i.quitEver) 1f else 0f),
            Badge("d1", R.string.badge_d1_title, R.string.badge_d1_text, BadgeIcon.STREAK, ratio(streakDays, 1.0)),
            Badge("d3", R.string.badge_d3_title, R.string.badge_d3_text, BadgeIcon.STREAK, ratio(streakDays, 3.0)),
            Badge("d7", R.string.badge_d7_title, R.string.badge_d7_text, BadgeIcon.STREAK, ratio(streakDays, 7.0)),
            Badge("d30", R.string.badge_d30_title, R.string.badge_d30_text, BadgeIcon.LEAF, ratio(streakDays, 30.0)),
            Badge("d90", R.string.badge_d90_title, R.string.badge_d90_text, BadgeIcon.LEAF, ratio(streakDays, 90.0)),
            Badge("d365", R.string.badge_d365_title, R.string.badge_d365_text, BadgeIcon.HEART, ratio(streakDays, 365.0)),
            Badge("m50", R.string.badge_m50_title, R.string.badge_m50_text, BadgeIcon.MONEY, ratio(i.money, 50.0)),
            Badge("m500", R.string.badge_m500_title, R.string.badge_m500_text, BadgeIcon.MONEY, ratio(i.money, 500.0)),
            Badge("a100", R.string.badge_a100_title, R.string.badge_a100_text, BadgeIcon.LEAF, ratio(i.avoided.toDouble(), 100.0)),
            Badge("a1000", R.string.badge_a1000_title, R.string.badge_a1000_text, BadgeIcon.HEART, ratio(i.avoided.toDouble(), 1000.0)),
            Badge("rebound", R.string.badge_rebound_title, R.string.badge_rebound_text, BadgeIcon.REBOUND, if (i.reboundEver) 1f else 0f),
        )
    }
}
