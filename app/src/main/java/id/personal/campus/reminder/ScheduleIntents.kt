package id.personal.campus.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import id.personal.campus.MainActivity
import id.personal.campus.widget.WidgetBoundaryReceiver

/** Extras never determine PendingIntent identity. Alarms each have one fixed identity. */
internal object ScheduleIntents {
    const val REMINDER_ACTION = "id.personal.campus.action.REMINDER"
    const val WIDGET_BOUNDARY_ACTION = "id.personal.campus.action.WIDGET_BOUNDARY"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun reminder(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 100, Intent(context, ReminderReceiver::class.java).setAction(REMINDER_ACTION), FLAGS,
    )

    fun widgetBoundary(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 101,
        Intent(context, WidgetBoundaryReceiver::class.java).setAction(WIDGET_BOUNDARY_ACTION), FLAGS,
    )

    fun openCourse(context: Context, courseId: Long): PendingIntent = PendingIntent.getActivity(
        context, 0,
        activity(context).setAction("id.personal.campus.action.OPEN_COURSE")
            .setData(Uri.parse("campus://course/$courseId"))
            .putExtra("course_id", courseId),
        FLAGS,
    )

    fun openSettings(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        activity(context).setAction("id.personal.campus.action.OPEN_SETTINGS")
            .setData(Uri.parse("campus://settings"))
            .putExtra("open_settings", true),
        FLAGS,
    )

    private fun activity(context: Context) = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
