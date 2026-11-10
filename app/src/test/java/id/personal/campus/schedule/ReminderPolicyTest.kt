package id.personal.campus.schedule

import id.personal.campus.data.CourseEntity
import id.personal.campus.data.SettingsEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {
    private val course = CourseEntity(id = 7, name = "Kuliah", weekday = 3,
        startMinute = 12 * 60 + 30, endMinute = 15 * 60, room = "A.3b.6")
    private val settings = SettingsEntity(
        startEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
        endEpochDay = LocalDate.of(2026, 11, 30).toEpochDay(),
        remindersEnabled = true,
    )

    private fun resolve(localTime: String, courses: List<CourseEntity> = listOf(course),
        config: SettingsEntity = settings): ScheduleSnapshot = OccurrenceResolver(
        Clock.fixed(Instant.parse("2026-11-11T${localTime}+07:00"), ZoneOffset.UTC),
    ).resolve(courses, config)

    @Test fun notDeliveredBeforeLeadTime() {
        val state = resolve("12:14:59")
        assertTrue(ReminderPolicy.due(state, settings, emptySet()).isEmpty())
        assertEquals(Instant.parse("2026-11-11T05:15:00Z"),
            ReminderPolicy.nextTrigger(state, settings, emptySet()))
    }

    @Test fun deliveredAtLeadTimeAndBeforeStart() {
        for (time in listOf("12:15:00", "12:29:59")) {
            val state = resolve(time)
            assertEquals(1, ReminderPolicy.due(state, settings, emptySet()).size)
            assertEquals(state.now.toInstant(), ReminderPolicy.nextTrigger(state, settings, emptySet()))
        }
    }

    @Test fun noLateReminderAtOrAfterStart() {
        for (time in listOf("12:30:00", "14:00:00", "15:00:00")) {
            val state = resolve(time)
            assertTrue(ReminderPolicy.due(state, settings, emptySet()).isEmpty())
            assertEquals(Instant.parse("2026-11-18T05:15:00Z"),
                ReminderPolicy.nextTrigger(state, settings, emptySet()))
        }
    }

    @Test fun claimedOccurrenceIsNeverDeliveredTwice() {
        val state = resolve("12:15:00")
        val delivered = setOf(checkNotNull(state.primary).key)
        assertTrue(ReminderPolicy.due(state, settings, delivered).isEmpty())
        assertEquals(Instant.parse("2026-11-18T05:15:00Z"),
            ReminderPolicy.nextTrigger(state, settings, delivered))
    }

    @Test fun disabledReminderHasNoPendingAlarm() {
        val state = resolve("12:15:00")
        val disabled = settings.copy(remindersEnabled = false)
        assertTrue(ReminderPolicy.due(state, disabled, emptySet()).isEmpty())
        assertNull(ReminderPolicy.nextTrigger(state, disabled, emptySet()))
    }

    @Test fun sameTriggerReturnsAllClassesInOneBatch() {
        val state = resolve("12:15:00", listOf(course, course.copy(id = 8, room = "B.2")))
        assertEquals(2, ReminderPolicy.due(state, settings, emptySet()).size)
        assertEquals(state.now.toInstant(), ReminderPolicy.nextTrigger(state, settings, emptySet()))
    }

    @Test fun editsUseFreshDataAndDoNotDeliverOldTrigger() {
        val updated = course.copy(startMinute = 14 * 60, endMinute = 16 * 60)
        val state = resolve("12:15:00", listOf(updated))
        assertTrue(ReminderPolicy.due(state, settings, emptySet()).isEmpty())
        assertEquals(Instant.parse("2026-11-11T06:45:00Z"),
            ReminderPolicy.nextTrigger(state, settings, emptySet()))
    }

    @Test fun changingLeadTimeDoesNotDuplicateSameOccurrence() {
        val before = resolve("12:15:00")
        val claimed = setOf(checkNotNull(before.primary).key)
        val updated = settings.copy(reminderMinutes = 30)
        val after = resolve("12:16:00", config = updated)
        assertTrue(ReminderPolicy.due(after, updated, claimed).isEmpty())
    }

    @Test fun firstClassCanRemindOnPreviousDay() {
        val early = course.copy(startMinute = 5, endMinute = 60)
        val firstDay = settings.copy(startEpochDay = LocalDate.of(2026, 11, 11).toEpochDay())
        val clock = Clock.fixed(Instant.parse("2026-11-10T16:50:00Z"), ZoneOffset.UTC)
        val state = OccurrenceResolver(clock).resolve(listOf(early), firstDay)
        assertEquals(1, ReminderPolicy.due(state, firstDay, emptySet()).size)
        assertEquals(LocalDate.of(2026, 11, 11), state.primary?.start?.toLocalDate())
    }
}
