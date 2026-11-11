package id.personal.campus.widget

import id.personal.campus.data.InitialSchedule
import id.personal.campus.data.SettingsEntity
import id.personal.campus.schedule.OccurrenceResolver
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetCountdownTest {
    private val courses = InitialSchedule.courses.mapIndexed { index, course -> course.copy(id = index + 1L) }
    private val settings = SettingsEntity(
        startEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
        endEpochDay = LocalDate.of(2026, 12, 31).toEpochDay(),
        remindersEnabled = true,
    )

    private fun countdownAt(localDateTime: String): WidgetCountdown? {
        val instant = Instant.parse("${localDateTime}+07:00")
        val state = OccurrenceResolver(Clock.fixed(instant, ZoneId.of("UTC"))).resolve(courses, settings)
        return widgetCountdown(state, instant.toEpochMilli())
    }

    @Test fun upcomingClassCountsDownToStart() {
        val countdown = countdownAt("2026-11-11T12:25:00")
        assertEquals("MULAI DALAM", countdown?.label)
        assertEquals(5 * 60_000L, countdown?.remainingMillis)
        assertEquals(12, countdown?.target?.hour)
        assertEquals(30, countdown?.target?.minute)
    }

    @Test fun activeClassCountsDownToEnd() {
        val countdown = countdownAt("2026-11-11T13:00:00")
        assertEquals("SISA", countdown?.label)
        assertEquals(2 * 60 * 60_000L, countdown?.remainingMillis)
        assertEquals(15, countdown?.target?.hour)
    }

    @Test fun classBeyondADayHasNoCountdown() {
        assertNull(countdownAt("2026-11-12T12:10:00"))
    }

    @Test fun semesterFinishedHasNoCountdown() {
        assertNull(countdownAt("2027-01-01T00:00:00"))
    }
}
