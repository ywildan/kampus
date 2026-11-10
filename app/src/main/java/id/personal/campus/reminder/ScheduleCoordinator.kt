package id.personal.campus.reminder

import android.content.Context
import id.personal.campus.CampusApplication
import id.personal.campus.schedule.OccurrenceResolver
import id.personal.campus.schedule.ReminderPolicy
import id.personal.campus.widget.CampusWidgetProvider
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One reconciliation path for startup, UI mutations/resume, alarms, widgets and system broadcasts. */
object ScheduleCoordinator {
    private val mutex = Mutex()
    private val resolver = OccurrenceResolver()

    suspend fun refresh(context: Context, deliverDue: Boolean = false) = withContext(Dispatchers.IO) {
        val app = CampusApplication.from(context)
        mutex.withLock {
            val repository = app.repository
            ReminderNotifications.ensureChannel(app)
            // An old alarm carries no schedule payload. Always replace it from current persisted data.
            ScheduleAlarms.cancelReminder(app)
            val initial = repository.snapshot()
            val state = resolver.resolve(initial.courses, initial.settings)
            // Keep recent claims across clock adjustments and process death, without unbounded growth.
            repository.pruneReminders(state.now.toInstant().minus(35, ChronoUnit.DAYS))
            val delivered = repository.deliveredKeys()

            if (deliverDue && ReminderNotifications.canPost(app)) {
                val due = ReminderPolicy.due(state, initial.settings, delivered)
                for (candidate in due) {
                    // Re-read after each suspension: edits, offsets or permissions may have changed.
                    val current = repository.snapshot()
                    val currentState = resolver.resolve(current.courses, current.settings)
                    val occurrence = ReminderPolicy.due(currentState, current.settings, delivered)
                        .firstOrNull { it.key == candidate.key } ?: continue
                    if (!ReminderNotifications.canPost(app)) break
                    val notification = ReminderNotifications.build(app, occurrence)
                    if (repository.claimReminder(occurrence.key, occurrence.start.toInstant())) {
                        // Room I/O can cross the start boundary. Reapply the shared late-delivery rule.
                        val atDelivery = resolver.resolve(current.courses, current.settings)
                        if (ReminderPolicy.due(atDelivery, current.settings, emptySet()).any { it.key == occurrence.key }) {
                            ReminderNotifications.post(app, occurrence, notification)
                        }
                    }
                }
            }

            val latest = repository.snapshot()
            val latestState = resolver.resolve(latest.courses, latest.settings)
            // Permission/channel denial cancels the alarm entirely; no immediate-alarm wake loop.
            // UI/startup refresh never posts: an already-due batch gets an immediate one-shot alarm.
            val next = if (ReminderNotifications.canPost(app)) {
                ReminderPolicy.nextTrigger(latestState, latest.settings, repository.deliveredKeys())
            } else null
            ScheduleAlarms.replaceReminder(app, next)

            val hasWidgets = CampusWidgetProvider.updateAll(app, latestState, latest.settings)
            ScheduleAlarms.replaceWidgetBoundary(
                app, if (hasWidgets) resolver.nextWidgetBoundary(latestState) else null,
            )
        }
    }
}
