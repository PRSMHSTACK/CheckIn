package com.parsa.checkin

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleTest {
    private fun at(s: String) = ZonedDateTime.parse(s)
    private fun task(date: String = "2026-09-07", done: Boolean = false, time: String = "") = Task(title = "Study", date = date, completed = done, time = time)
    @Test fun completedListStopsReminders() {
        assertNull(Schedule.next(at("2026-09-07T08:00:00Z"), listOf(task(done = true)), Settings(), 0))
    }
    @Test fun defaultNextCheckIn() {
        assertEquals(at("2026-09-07T09:00:00Z"), Schedule.next(at("2026-09-07T08:00:00Z"), listOf(task()), Settings(), 0))
    }
    @Test fun futureTasksScheduleWithoutOpeningApp() {
        assertEquals(at("2026-09-08T09:00:00Z"), Schedule.next(at("2026-09-07T22:00:00Z"), listOf(task("2026-09-08")), Settings(), 0))
    }
    @Test fun snoozeOverridesEarlierRegularReminder() {
        val now = at("2026-09-07T08:55:00Z")
        assertEquals(now.plusMinutes(30), Schedule.next(now,listOf(task()),Settings(),now.plusMinutes(30).toInstant().toEpochMilli()))
    }
    @Test fun snoozeDefersAcrossMidnightQuietPeriod() {
        val now = at("2026-09-07T22:50:00Z")
        assertEquals(at("2026-09-08T08:00:00Z"), Schedule.next(now,listOf(task()),Settings(),now.plusMinutes(30).toInstant().toEpochMilli()))
    }
    @Test fun overnightQuietBoundaries() {
        assertTrue(Schedule.quiet(LocalTime.of(23,0),Settings()))
        assertTrue(Schedule.quiet(LocalTime.of(7,59),Settings()))
        assertFalse(Schedule.quiet(LocalTime.of(8,0),Settings()))
    }
    @Test fun daytimeQuietHours() {
        val s = Settings(quietStart = "12:00",quietEnd = "14:00")
        assertTrue(Schedule.quiet(LocalTime.of(13,0),s))
        assertFalse(Schedule.quiet(LocalTime.of(23,0),s))
    }
    @Test fun explicitTaskTimeIsCandidate() {
        assertEquals(at("2026-09-07T08:30:00Z"), Schedule.next(at("2026-09-07T08:00:00Z"), listOf(task(time = "08:30")), Settings(),0))
    }
    @Test fun disabledStopsEverything() { assertNull(Schedule.next(at("2026-09-07T08:00:00Z"),listOf(task()),Settings(enabled = false),0)) }
    @Test fun oldUnfinishedTasksAreNotSilentlyMoved() { assertNull(Schedule.next(at("2026-09-08T08:00:00Z"),listOf(task()),Settings(),0)) }
    @Test fun dstUsesLocalWallClock() {
        val now = at("2026-03-29T00:00:00+01:00[Europe/Berlin]")
        assertEquals(9, Schedule.next(now,listOf(task("2026-03-29")),Settings(),0)!!.hour)
        assertEquals(ZoneOffset.ofHours(2), Schedule.next(now,listOf(task("2026-03-29")),Settings(),0)!!.offset)
    }
}
