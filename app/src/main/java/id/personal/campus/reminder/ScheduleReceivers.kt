package id.personal.campus.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import id.personal.campus.CampusApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ScheduleIntents.REMINDER_ACTION) {
            refreshScheduleAsync(context, deliverDue = true)
        }
    }
}

class ScheduleSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> refreshScheduleAsync(context)
        }
    }
}

/** Keep the CPU available for bounded asynchronous Room I/O, independent of the sender's wake lock. */
internal fun BroadcastReceiver.refreshScheduleAsync(context: Context, deliverDue: Boolean = false) {
    val result = goAsync()
    var wakeLock: PowerManager.WakeLock? = null
    try {
        val app = CampusApplication.from(context)
        val heldLock = app.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "campus:schedule-receiver")
        wakeLock = heldLock
        heldLock.setReferenceCounted(false)
        heldLock.acquire(9_000L)
        val job = app.applicationScope.launch {
            try {
                // Also bounds waiting for the coordinator mutex. Never strand a PendingResult.
                withTimeout(7_500L) { ScheduleCoordinator.refresh(app, deliverDue) }
            } catch (timeout: TimeoutCancellationException) {
                Log.w("CampusReceiver", "Schedule refresh exceeded the receiver time budget", timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Receiver boundary only: surface failures, but always finish the broadcast.
                Log.e("CampusReceiver", "Unable to reconcile the schedule", failure)
            }
        }
        // Completion handlers also run if the application scope was cancelled before launch started.
        job.invokeOnCompletion {
            try {
                if (heldLock.isHeld) heldLock.release()
            } finally {
                result.finish()
            }
        }
    } catch (failure: Exception) {
        try {
            if (wakeLock?.isHeld == true) wakeLock.release()
        } finally {
            result.finish()
        }
        Log.e("CampusReceiver", "Unable to start asynchronous schedule refresh", failure)
    }
}
