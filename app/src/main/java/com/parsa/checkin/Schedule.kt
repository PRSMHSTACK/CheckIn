package com.parsa.checkin

import java.time.*

object Schedule {
    fun times(s: Settings): List<LocalTime> = when (s.intensity) {
        "Gentle" -> listOf("10:00", "18:00")
        "Persistent" -> (8..22 step 2).map { "%02d:00".format(it) }
        "Custom" -> s.custom.split(",").map { it.trim() }
        else -> listOf("09:00", "13:00", "17:00", "21:00")
    }.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }.distinct().sorted()
    fun quiet(now: LocalTime, s: Settings): Boolean {
        if (!s.quiet) return false
        val start = LocalTime.parse(s.quietStart); val end = LocalTime.parse(s.quietEnd)
        return if (start < end) now >= start && now < end else now >= start || now < end
    }
    fun allowed(at: ZonedDateTime, s: Settings): ZonedDateTime {
        if (!quiet(at.toLocalTime(), s)) return at
        val end = LocalTime.parse(s.quietEnd)
        var next = at.toLocalDate().atTime(end).atZone(at.zone)
        if (!next.isAfter(at)) next = at.toLocalDate().plusDays(1).atTime(end).atZone(at.zone)
        return next
    }
    fun next(now: ZonedDateTime, tasks: List<Task>, s: Settings, snooze: Long): ZonedDateTime? {
        if (!s.enabled) return null
        val pending = tasks.filter { !it.completed && LocalDate.parse(it.date) >= now.toLocalDate() }
        if (pending.isEmpty()) return null
        val candidates = mutableListOf<ZonedDateTime>()
        if (snooze > now.toInstant().toEpochMilli() && pending.any { it.date == now.toLocalDate().toString() })
            return allowed(Instant.ofEpochMilli(snooze).atZone(now.zone), s)
        for (date in pending.map { LocalDate.parse(it.date) }.distinct()) {
            val taskTimes = pending.filter { it.date == date.toString() && it.time.isNotBlank() }.map { LocalTime.parse(it.time) }
            (times(s) + taskTimes).forEach { time ->
                val raw = date.atTime(time).atZone(now.zone)
                val candidate = allowed(raw, s)
                if (candidate.isAfter(now) && candidate.toLocalDate() == date) candidates.add(candidate)
            }
        }
        return candidates.minOrNull()
    }
}
