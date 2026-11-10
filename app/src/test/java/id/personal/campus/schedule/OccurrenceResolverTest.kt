package id.personal.campus.schedule

import id.personal.campus.data.InitialSchedule
import id.personal.campus.data.SettingsEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceResolverTest {
    private val courses = InitialSchedule.courses.mapIndexed { index, course -> course.copy(id = index + 1L) }
    private val settings = SettingsEntity(
        startEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
        endEpochDay = LocalDate.of(2026, 12, 31).toEpochDay(),
        remindersEnabled = true,
    )

    private fun resolver(localDateTime: String, deviceZone: String = "UTC") = OccurrenceResolver(
        Clock.fixed(Instant.parse("${localDateTime}+07:00"), ZoneId.of(deviceZone)),
    )

    @Test fun endIsExclusiveAndGapShowsNextClass() {
        val state = resolver("2026-11-11T12:20:00").resolve(courses, settings)
        assertTrue(state.active.isEmpty())
        assertEquals("Pengantar Akuntansi", state.primary?.course?.name)
        assertEquals(12, state.primary?.start?.hour)
        assertEquals(30, state.primary?.start?.minute)
    }

    @Test fun startIsInclusive() {
        val state = resolver("2026-11-11T12:30:00").resolve(courses, settings)
        assertEquals(1, state.active.size)
        assertEquals("Pengantar Akuntansi", state.primary?.course?.name)
        assertEquals("A.3b.6", state.primary?.course?.room)
    }

    @Test fun classNoLongerActiveAtEnd() {
        val state = resolver("2026-11-11T15:00:00").resolve(courses, settings)
        assertTrue(state.active.isEmpty())
        assertEquals("Matematika Bisnis", state.primary?.course?.name)
        assertEquals(LocalDate.of(2026, 11, 12), state.primary?.start?.toLocalDate())
    }

    @Test fun thursdayEndRollsToMonday() {
        val state = resolver("2026-11-12T12:10:00").resolve(courses, settings)
        assertEquals("Pengantar Bisnis", state.primary?.course?.name)
        assertEquals(LocalDate.of(2026, 11, 16), state.primary?.start?.toLocalDate())
        assertEquals(15, state.primary?.start?.hour)
        assertEquals(10, state.primary?.start?.minute)
    }

    @Test fun doesNotExtendPastSemester() {
        val shortTerm = settings.copy(endEpochDay = LocalDate.of(2026, 11, 12).toEpochDay())
        val state = resolver("2026-11-12T12:10:00").resolve(courses, shortTerm)
        assertNull(state.primary)
        assertNull(ReminderPolicy.nextTrigger(state, shortTerm, emptySet()))
    }

    @Test fun semesterEndIsInclusive() {
        val shortTerm = settings.copy(endEpochDay = LocalDate.of(2026, 11, 12).toEpochDay())
        val state = resolver("2026-11-12T07:00:00").resolve(courses, shortTerm)
        assertEquals("Matematika Bisnis", state.active.single().course.name)
    }

    @Test fun semesterFinishedHasNoOccurrence() {
        val state = resolver("2027-01-01T00:00:00").resolve(courses, settings)
        assertEquals(ScheduleMode.FINISHED, state.mode)
        assertNull(state.primary)
    }

    @Test fun unconfiguredSemesterIsOnlyPreview() {
        val unconfigured = SettingsEntity(remindersEnabled = true)
        val state = resolver("2026-11-11T12:15:00").resolve(courses, unconfigured)
        assertEquals(ScheduleMode.PREVIEW, state.mode)
        assertNotNull(state.primary)
        assertTrue(ReminderPolicy.due(state, unconfigured, emptySet()).isEmpty())
        assertNull(ReminderPolicy.nextTrigger(state, unconfigured, emptySet()))
    }

    @Test fun emptyAndDisabledCoursesHaveNoAlarm() {
        for (input in listOf(emptyList(), courses.map { it.copy(enabled = false) })) {
            val state = resolver("2026-11-11T12:15:00").resolve(input, settings)
            assertEquals(ScheduleMode.EMPTY, state.mode)
            assertNull(ReminderPolicy.nextTrigger(state, settings, emptySet()))
        }
    }

    @Test fun deviceTimezoneDoesNotShiftWibSchedule() {
        val jakarta = resolver("2026-11-11T12:30:00", "Asia/Jakarta").resolve(courses, settings)
        val london = resolver("2026-11-11T12:30:00", "Europe/London").resolve(courses, settings)
        assertEquals(jakarta.primary, london.primary)
        assertEquals(ZoneId.of("Asia/Jakarta"), london.now.zone)
    }

    @Test fun futureSemesterDoesNotCreateEarlierClasses() {
        val future = settings.copy(startEpochDay = LocalDate.of(2026, 12, 1).toEpochDay())
        val state = resolver("2026-11-11T12:00:00").resolve(courses, future)
        assertEquals(LocalDate.of(2026, 12, 1), state.primary?.start?.toLocalDate())
        assertEquals("Bahasa Inggris", state.primary?.course?.name)
        assertTrue(state.active.isEmpty())
    }

    @Test fun overlappingClassesAreNotDroppedAndOrderIsStable() {
        val accounting = courses.first { it.name == "Pengantar Akuntansi" }
        val extra = accounting.copy(id = 99, name = "Kuliah bentrok", room = "B.1")
        val state = resolver("2026-11-11T12:30:00").resolve(courses + extra, settings)
        assertEquals(listOf(accounting.id, 99L), state.active.map { it.course.id })
    }

    @Test fun widgetTransitionsAtClassEndAndStartDuringGap() {
        val activeResolver = resolver("2026-11-11T12:19:00")
        assertEquals(Instant.parse("2026-11-11T05:20:00Z"),
            activeResolver.nextWidgetBoundary(activeResolver.resolve(courses, settings)))
        val gapResolver = resolver("2026-11-11T12:20:00")
        assertEquals(Instant.parse("2026-11-11T05:30:00Z"),
            gapResolver.nextWidgetBoundary(gapResolver.resolve(courses, settings)))
    }

    @Test fun widgetUpdatesAtMidnightBeforeNextDayClass() {
        val resolver = resolver("2026-11-12T13:00:00")
        val state = resolver.resolve(courses, settings)
        assertEquals(Instant.parse("2026-11-12T17:00:00Z"), resolver.nextWidgetBoundary(state))
    }

    @Test fun weeklyOccurrenceKeysAreDistinct() {
        val state = resolver("2026-11-13T00:00:00").resolve(courses, settings)
        val business = state.upcoming.filter { it.course.name == "Pengantar Bisnis" }
        assertEquals(2, business.size)
        assertFalse(business[0].key == business[1].key)
    }
}
