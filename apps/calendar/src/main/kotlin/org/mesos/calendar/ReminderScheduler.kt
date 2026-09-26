package org.mesos.calendar

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mesos.core.MesOSApps
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.hasPermission
import java.time.ZoneId

/**
 * Event reminders: one alarm is set for the next reminder due; when it fires the
 * notification is posted and the following reminder is scheduled.
 */
object ReminderScheduler {

    private const val CHANNEL_ID = "mesos_calendar_reminders"
    private const val ACTION_REMIND = "org.mesos.calendar.action.REMIND"
    private const val EXTRA_EVENT_ID = "event_id"
    private const val EXTRA_START = "start"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun reschedule(context: Context, events: List<Event>) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, null)
        alarms.cancel(pending)
        val next = CalendarMath.nextReminder(events, System.currentTimeMillis(), ZoneId.systemDefault()) ?: return
        val (occurrence, at) = next
        val intent = pendingIntent(context, occurrence)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
            MesOSLog.i(TAG, "Next reminder at $at for event ${occurrence.event.id}")
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    fun rescheduleAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch { reschedule(appContext, EventStore.get(appContext).all()) }
    }

    private fun pendingIntent(context: Context, occurrence: Occurrence?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND)
        if (occurrence != null) {
            intent.putExtra(EXTRA_EVENT_ID, occurrence.event.id).putExtra(EXTRA_START, occurrence.start)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    internal fun remind(context: Context, eventId: Long, start: Long, done: () -> Unit) {
        scope.launch {
            try {
                val store = EventStore.get(context)
                store.get(eventId)?.let { event -> notify(context, event, start) }
                reschedule(context, store.all())
            } finally {
                done()
            }
        }
    }

    private fun notify(context: Context, event: Event, start: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.calendar_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context,
            event.id.toInt(),
            MesOSApps.launchIntent(context, MesOSApps.CALENDAR).putExtra(CalendarActivity.EXTRA_DAY, start),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val flags = if (event.allDay) DateUtils.FORMAT_SHOW_DATE else DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE
        val time = DateUtils.formatDateTime(context, start, flags)
        val text = if (event.location.isBlank()) time else "$time · ${event.location}"
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_calendar_notification)
            .setContentTitle(event.title)
            .setContentText(text)
            .setCategory(android.app.Notification.CATEGORY_EVENT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_BASE + event.id.toInt(), notification)
    }

    private const val NOTIFICATION_BASE = 4000
    private const val TAG = "MesOSCalendar"

    internal fun eventId(intent: Intent) = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
    internal fun start(intent: Intent) = intent.getLongExtra(EXTRA_START, 0L)
    internal fun isRemind(intent: Intent) = intent.action == ACTION_REMIND
}

/** Fires when an event reminder is due. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ReminderScheduler.isRemind(intent)) return
        val id = ReminderScheduler.eventId(intent)
        if (id <= 0) return
        val pending = goAsync()
        ReminderScheduler.remind(context.applicationContext, id, ReminderScheduler.start(intent)) { pending.finish() }
    }
}

/** Alarms do not survive reboots or clock changes; schedule the next reminder again. */
class CalendarBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> ReminderScheduler.rescheduleAll(context)
        }
    }
}
