package id.personal.campus.schedule

import id.personal.campus.data.CourseEntity
import id.personal.campus.data.SettingsEntity
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

data class Occurrence(
    val course: CourseEntity,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
) {
    val key: String get() = "${course.id}:${start.toInstant().toEpochMilli()}"
}

enum class ScheduleMode { PREVIEW, SCHEDULED, EMPTY, FINISHED }

data class ScheduleSnapshot(
    val mode: ScheduleMode,
    val now: ZonedDateTime,
    val active: List<Occurrence> = emptyList(),
    val upcoming: List<Occurrence> = emptyList(),
) {
    val primary: Occurrence? get() = active.firstOrNull() ?: upcoming.firstOrNull()
}

class OccurrenceResolver(private val clock: Clock = Clock.systemUTC()) {
    fun resolve(courses: List<CourseEntity>, settings: SettingsEntity): ScheduleSnapshot {
        val now = clock.instant().atZone(settings.zone)
        val today = now.toLocalDate()
        val enabled = courses.filter { it.enabled }
        if (enabled.isEmpty()) return ScheduleSnapshot(ScheduleMode.EMPTY, now)
        val confirmed = settings.semesterConfirmed
        val semesterEnd = settings.endDate.takeIf { confirmed }
        if (semesterEnd != null && today > semesterEnd) {
            return ScheduleSnapshot(ScheduleMode.FINISHED, now)
        }
        val firstDate = maxOf(today, settings.startDate.takeIf { confirmed } ?: today)
        val occurrences = enabled.flatMap { course ->
            val first = firstDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(course.weekday)))
            (0L..1L).mapNotNull { week ->
                val date = first.plusWeeks(week)
                if (semesterEnd != null && date > semesterEnd) null
                else occurrence(course, date, settings).takeIf { it.end.isAfter(now) }
            }
        }.sortedWith(compareBy<Occurrence> { it.start.toInstant() }.thenBy { it.course.id })
        return ScheduleSnapshot(
            mode = if (confirmed) ScheduleMode.SCHEDULED else ScheduleMode.PREVIEW,
            now = now,
            active = occurrences.filter { !it.start.isAfter(now) && it.end.isAfter(now) },
            upcoming = occurrences.filter { it.start.isAfter(now) },
        )
    }

    fun nextWidgetBoundary(snapshot: ScheduleSnapshot): Instant {
        val midnight = snapshot.now.toLocalDate().plusDays(1).atStartOfDay(snapshot.now.zone)
        return (snapshot.active.map { it.end } + snapshot.upcoming.map { it.start } + midnight)
            .filter { it.isAfter(snapshot.now) }
            .minBy { it.toInstant() }.toInstant()
    }

    private fun occurrence(course: CourseEntity, date: LocalDate, settings: SettingsEntity) = Occurrence(
        course,
        date.atTime(LocalTime.ofSecondOfDay(course.startMinute * 60L)).atZone(settings.zone),
        date.atTime(LocalTime.ofSecondOfDay(course.endMinute * 60L)).atZone(settings.zone),
    )
}

object ReminderPolicy {
    fun due(
        snapshot: ScheduleSnapshot,
        settings: SettingsEntity,
        delivered: Set<String>,
    ): List<Occurrence> {
        if (!eligible(snapshot, settings)) return emptyList()
        val now = snapshot.now.toInstant()
        return snapshot.upcoming.filter {
            it.key !in delivered && now >= trigger(it, settings) && now < it.start.toInstant()
        }
    }

    fun nextTrigger(
        snapshot: ScheduleSnapshot,
        settings: SettingsEntity,
        delivered: Set<String>,
    ): Instant? {
        if (!eligible(snapshot, settings)) return null
        val now = snapshot.now.toInstant()
        return snapshot.upcoming.filter { it.key !in delivered }
            .map { maxOf(now, trigger(it, settings)) }.minOrNull()
    }

    private fun eligible(snapshot: ScheduleSnapshot, settings: SettingsEntity): Boolean =
        settings.remindersEnabled && settings.semesterConfirmed && snapshot.mode == ScheduleMode.SCHEDULED

    private fun trigger(occurrence: Occurrence, settings: SettingsEntity): Instant =
        occurrence.start.toInstant().minusSeconds(settings.reminderMinutes * 60L)
}
