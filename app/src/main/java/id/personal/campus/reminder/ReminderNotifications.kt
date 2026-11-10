package id.personal.campus.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import id.personal.campus.R
import id.personal.campus.schedule.Occurrence
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReminderNotifications {
    const val CHANNEL_ID = "class_reminders"
    private const val NOTIFICATION_ID = 1
    private val locale = Locale.forLanguageTag("id-ID")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Pengingat kuliah", NotificationManager.IMPORTANCE_DEFAULT)
            .apply {
                description = "Pengingat sebelum kuliah dimulai, sesuai zona waktu jadwal."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        // Recreating an existing channel preserves the user's sound and importance preferences.
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return false
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val groupId = channel.group
            if (groupId != null && manager.getNotificationChannelGroup(groupId)?.isBlocked == true) return false
        }
        return true
    }

    internal fun build(context: Context, occurrence: Occurrence): Notification {
        val course = occurrence.course
        val range = "${occurrence.start.format(timeFormat)}–${occurrence.end.format(timeFormat)}"
        val section = course.section.takeIf { it.isNotBlank() }?.let { "Kelas $it" }
        val description = listOfNotNull(course.name, section, course.roomNote.takeIf { it.isNotBlank() })
            .joinToString(" · ")
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_schedule)
            .setContentTitle("$range · ${course.room}")
            .setContentText(description)
            .setStyle(Notification.BigTextStyle().bigText(
                "$description\n${occurrence.start.format(dateFormat)} · ${occurrence.start.zone.id}\n" +
                    "$range · ${course.room}",
            ))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(ScheduleIntents.openCourse(context, course.id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setTimeoutAfter(Duration.between(Instant.now(), occurrence.start.toInstant()).toMillis().coerceAtLeast(1L))
            .build()
    }

    internal fun post(context: Context, occurrence: Occurrence, notification: Notification) {
        // Recheck at the notification boundary as permission may change during the database claim.
        if (!canPost(context)) return
        try {
            // A fixed numeric ID plus the full occurrence key is collision-free; no hash-based IDs.
            context.getSystemService(NotificationManager::class.java)
                .notify(occurrence.key, NOTIFICATION_ID, notification)
        } catch (revoked: SecurityException) {
            // There is no atomic transaction spanning Room and NotificationManager. Keep the claim
            // (at-most-once) if permission is revoked after claiming, rather than risking duplicates.
            Log.w("CampusNotifications", "Notification permission changed during delivery", revoked)
        }
    }
}
