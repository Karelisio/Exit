package com.karelisio.exit.domain

import androidx.annotation.StringRes
import com.karelisio.exit.R

enum class Phase { SPACING, QUIT }

enum class StepPeriod { DAY, WEEK }

/** Contextes à risque, proposés en tag optionnel à chaque cigarette ou envie. */
enum class Trigger(@StringRes val labelRes: Int, val emoji: String) {
    CAFE(R.string.trigger_cafe, "☕"),
    STRESS(R.string.trigger_stress, "😮‍💨"),
    REPAS(R.string.trigger_repas, "🍽️"),
    VOITURE(R.string.trigger_voiture, "🚗"),
    ALCOOL(R.string.trigger_alcool, "🍷"),
    ENNUI(R.string.trigger_ennui, "🥱"),
    PERSO(R.string.trigger_perso, "✏️"),
    ;

    companion object {
        fun of(key: String?): Trigger? = entries.firstOrNull { it.name == key }
    }
}

enum class PlanEventType { START, AUTO_ADJUST, MANUAL_ADJUST, QUIT, SLIP, RESUME_PLAN, IMPORT }

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
const val DAY_MS = 24 * HOUR_MS
