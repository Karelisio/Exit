package com.karelisio.exit.domain

enum class Phase { SPACING, QUIT }

enum class StepPeriod { DAY, WEEK }

/** Contextes à risque, proposés en tag optionnel à chaque cigarette ou envie. */
enum class Trigger(val label: String, val emoji: String) {
    CAFE("Café", "☕"),
    STRESS("Stress", "😮‍💨"),
    REPAS("Après repas", "🍽️"),
    VOITURE("Voiture", "🚗"),
    ALCOOL("Alcool", "🍷"),
    ENNUI("Ennui", "🥱"),
    PERSO("Perso", "✏️"),
    ;

    companion object {
        fun of(key: String?): Trigger? = entries.firstOrNull { it.name == key }
    }
}

enum class PlanEventType { START, AUTO_ADJUST, MANUAL_ADJUST, QUIT, SLIP, RESUME_PLAN, IMPORT }

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
const val DAY_MS = 24 * HOUR_MS
