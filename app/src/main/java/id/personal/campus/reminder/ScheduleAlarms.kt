package id.personal.campus.reminder

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.time.Instant

internal object ScheduleAlarms {
    private const val TAG = "CampusAlarms"
    private const val WIDGET_WINDOW_MILLIS = 10 * 60 * 1_000L

    fun cancelReminder(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(ScheduleIntents.reminder(context))
    }

    fun replaceReminder(context: Context, trigger: Instant?) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = ScheduleIntents.reminder(context)
        manager.cancel(operation)
        if (trigger == null) return
        val at = trigger.toEpochMilli()
        if (canUseExact(manager)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                return
            } catch (revoked: SecurityException) {
                // Access can be revoked between the check and binder call. Only this boundary falls back.
                Log.w(TAG, "Exact reminder access changed; using an approximate alarm", revoked)
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
    }

    fun replaceWidgetBoundary(context: Context, trigger: Instant?) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = ScheduleIntents.widgetBoundary(context)
        manager.cancel(operation)
        if (trigger == null) return
        val at = trigger.toEpochMilli()
        if (canUseExact(manager)) {
            try {
                // Deliberately non-wakeup and NOT allow-while-idle: the widget is not an alert.
                manager.setExact(AlarmManager.RTC, at, operation)
                return
            } catch (revoked: SecurityException) {
                Log.w(TAG, "Exact widget access changed; using an approximate boundary", revoked)
            }
        }
        manager.setWindow(AlarmManager.RTC, at, WIDGET_WINDOW_MILLIS, operation)
    }

    private fun canUseExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
}
