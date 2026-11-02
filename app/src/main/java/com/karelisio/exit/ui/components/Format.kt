package com.karelisio.exit.ui.components

import com.karelisio.exit.domain.DAY_MS
import com.karelisio.exit.domain.HOUR_MS
import com.karelisio.exit.domain.MINUTE_MS
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

object Fmt {
    private val locale = Locale.FRANCE
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayLong = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val dayShort = DateTimeFormatter.ofPattern("d MMM", locale)
    private val dayFull = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)

    fun money(value: Double): String = NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("EUR")
        maximumFractionDigits = if (value >= 100) 0 else 2
        minimumFractionDigits = if (value >= 100) 0 else 2
    }.format(value)

    fun number(value: Int): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun time(millis: Long): String = time.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> dayLong.format(day).replaceFirstChar { it.uppercase() }
    }

    fun dayShort(day: LocalDate): String = dayShort.format(day)

    fun dayFull(day: LocalDate): String = dayFull.format(day)

    /** Compte à rebours : « 1:05:09 » ou « 42:17 ». */
    fun countdown(millis: Long): String {
        val total = (millis + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** Durée lisible : « 3 j 4 h », « 2 h 15 », « 12 min ». */
    fun duration(millis: Long): String {
        val d = millis / DAY_MS
        val h = (millis % DAY_MS) / HOUR_MS
        val m = (millis % HOUR_MS) / MINUTE_MS
        return when {
            d > 0 -> if (h > 0) "$d j $h h" else "$d j"
            h > 0 -> if (m > 0) "$h h ${m.toString().padStart(2, '0')}" else "$h h"
            else -> "$m min"
        }
    }

    /** Temps de vie regagné à partir de minutes. */
    fun lifeTime(minutes: Long): String {
        val days = minutes / (24 * 60)
        val hours = (minutes % (24 * 60)) / 60
        val mins = minutes % 60
        return when {
            days > 0 -> "$days j $hours h"
            hours > 0 -> "$hours h ${mins.toString().padStart(2, '0')}"
            else -> "$mins min"
        }
    }

    fun greeting(hour: Int): String = when (hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }
}
