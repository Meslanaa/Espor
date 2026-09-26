package org.mesos.clock

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.hasPermission
import java.time.ZoneId
import java.util.Date

/**
 * Alarms, the timer and their notifications. Alarms use AlarmManager.setAlarmClock
 * (exact, shown by Android as the next alarm); ringing is an insistent full-screen
 * notification on the alarm sound stream, so it also works on the lock screen.
 */
class AlarmEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("mesos_clock", Context.MODE_PRIVATE)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    private val _alarms = MutableStateFlow(ClockMath.fromJson(prefs.getString(KEY_ALARMS, null)).sortedBy { it.hour * 60 + it.minute })
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _timer = MutableStateFlow(readTimer())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    /** Whether Android lets MesOS ring at an exact minute (always true below Android 12). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    fun exactAlarmSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${appContext.packageName}"))
        } else {
            Intent(Settings.ACTION_DATE_SETTINGS)
        }

    /** Saves [alarm] (new when id is 0), schedules it and returns the stored alarm. */
    fun save(alarm: Alarm): Alarm {
        val stored = if (alarm.id == 0) alarm.copy(id = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1) else alarm
        write(_alarms.value.filter { it.id != stored.id } + stored)
        schedule(stored)
        return stored
    }

    fun delete(alarm: Alarm) {
        cancel(alarm.id)
        write(_alarms.value.filter { it.id != alarm.id })
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean): Alarm = save(alarm.copy(enabled = enabled))

    /** Next ring time of [alarm], for "rings in 7 h 12 min". */
    fun nextTrigger(alarm: Alarm): Long = ClockMath.nextTrigger(alarm, System.currentTimeMillis(), ZoneId.systemDefault())

    /** Schedules every enabled alarm again (after boot, time change or update). */
    fun rescheduleAll() {
        _alarms.value.forEach { if (it.enabled) schedule(it) else cancel(it.id) }
        val timer = _timer.value
        if (timer.running) {
            if (timer.endAt <= System.currentTimeMillis()) timerFinished() else scheduleTimer(timer.endAt)
        }
    }

    private fun schedule(alarm: Alarm) {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return
        }
        val trigger = nextTrigger(alarm)
        setAlarmClock(trigger, ringIntent(alarm.id, snooze = false))
        MesOSLog.i(TAG, "Alarm ${alarm.id} set for $trigger")
    }

    private fun setAlarmClock(at: Long, operation: PendingIntent) {
        val manager = alarmManager ?: return
        val show = PendingIntent.getActivity(
            appContext,
            REQUEST_SHOW,
            Intent(appContext, ClockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            if (canScheduleExact()) {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } catch (e: SecurityException) {
            MesOSLog.w(TAG, "Exact alarm refused; using an inexact one", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun cancel(id: Int) {
        alarmManager?.cancel(ringIntent(id, snooze = false))
        alarmManager?.cancel(ringIntent(id, snooze = true))
    }

    private fun ringIntent(id: Int, snooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            if (snooze) SNOOZE_BASE + id else id,
            Intent(appContext, AlarmReceiver::class.java).setAction(ACTION_RING).putExtra(EXTRA_ALARM_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Called when an alarm fires: ring, then plan its next time (or switch a one-time alarm off). */
    internal fun ring(id: Int) {
        val alarm = _alarms.value.firstOrNull { it.id == id } ?: return
        if (alarm.repeats) schedule(alarm) else write(_alarms.value.map { if (it.id == id) it.copy(enabled = false) else it })
        showRinging(
            notificationId = RING_NOTIFICATION_BASE + id,
            title = alarm.label.ifBlank { appContext.getString(R.string.clock_alarm) },
            text = DateFormat.getTimeFormat(appContext).format(Date()),
            alarmId = id,
        )
    }

    internal fun snooze(id: Int) {
        dismiss(id)
        setAlarmClock(System.currentTimeMillis() + SNOOZE_MS, ringIntent(id, snooze = true))
    }

    internal fun dismiss(id: Int) {
        appContext.getSystemService(NotificationManager::class.java)?.cancel(
            if (id == TIMER_ID) TIMER_NOTIFICATION else RING_NOTIFICATION_BASE + id,
        )
    }

    // ---- Timer ----

    fun startTimer(durationMs: Long) {
        val end = System.currentTimeMillis() + durationMs
        writeTimer(TimerState(running = true, endAt = end, remaining = durationMs, duration = durationMs))
        scheduleTimer(end)
        showTimerProgress(end)
    }

    fun pauseTimer() {
        val t = _timer.value
        if (!t.running) return
        cancelTimerAlarm()
        writeTimer(t.copy(running = false, remaining = (t.endAt - System.currentTimeMillis()).coerceAtLeast(0)))
        appContext.getSystemService(NotificationManager::class.java)?.cancel(TIMER_PROGRESS_NOTIFICATION)
    }

    fun resumeTimer() {
        val t = _timer.value
        if (t.running || t.remaining <= 0) return
        val end = System.currentTimeMillis() + t.remaining
        writeTimer(t.copy(running = true, endAt = end))
        scheduleTimer(end)
        showTimerProgress(end)
    }

    fun resetTimer() {
        cancelTimerAlarm()
        writeTimer(TimerState())
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager?.cancel(TIMER_PROGRESS_NOTIFICATION)
        manager?.cancel(TIMER_NOTIFICATION)
    }

    internal fun timerFinished() {
        writeTimer(TimerState(duration = _timer.value.duration))
        appContext.getSystemService(NotificationManager::class.java)?.cancel(TIMER_PROGRESS_NOTIFICATION)
        showRinging(TIMER_NOTIFICATION, appContext.getString(R.string.clock_timer_done), appContext.getString(R.string.clock_timer), TIMER_ID)
    }

    private fun scheduleTimer(at: Long) {
        val manager = alarmManager ?: return
        try {
            if (canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, timerIntent())
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, timerIntent())
            }
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, timerIntent())
        }
    }

    private fun cancelTimerAlarm() {
        alarmManager?.cancel(timerIntent())
    }

    private fun timerIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            REQUEST_TIMER,
            Intent(appContext, AlarmReceiver::class.java).setAction(ACTION_TIMER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // ---- Notifications ----

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || appContext.hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    private fun ensureChannels(manager: NotificationManager) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarms = NotificationChannel(CHANNEL_ALARMS, appContext.getString(R.string.clock_channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val progress = NotificationChannel(CHANNEL_TIMER, appContext.getString(R.string.clock_channel_timer), NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(alarms)
        manager.createNotificationChannel(progress)
    }

    private fun showRinging(notificationId: Int, title: String, text: String, alarmId: Int) {
        if (!canNotify()) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(manager)
        val ring = Intent(appContext, AlarmRingActivity::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmRingActivity.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val fullScreen = PendingIntent.getActivity(appContext, notificationId, ring, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(appContext, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_clock_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(RING_TIMEOUT_MS)
            .addAction(Notification.Action.Builder(null, appContext.getString(R.string.clock_dismiss), actionIntent(ACTION_DISMISS, alarmId)).build())
        if (alarmId != TIMER_ID) {
            builder.addAction(Notification.Action.Builder(null, appContext.getString(R.string.clock_snooze), actionIntent(ACTION_SNOOZE, alarmId)).build())
        }
        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        manager.notify(notificationId, notification)
    }

    private fun showTimerProgress(end: Long) {
        if (!canNotify()) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(manager)
        val open = PendingIntent.getActivity(
            appContext,
            REQUEST_TIMER,
            Intent(appContext, ClockActivity::class.java).putExtra(ClockActivity.EXTRA_TAB, ClockTab.TIMER.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(appContext, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_clock_notification)
            .setContentTitle(appContext.getString(R.string.clock_timer))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(end)
            .setShowWhen(true)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        manager.notify(TIMER_PROGRESS_NOTIFICATION, notification)
    }

    private fun actionIntent(action: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            action.hashCode() + id,
            Intent(appContext, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_ALARM_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // ---- Storage ----

    private fun write(alarms: List<Alarm>) {
        val sorted = alarms.sortedBy { it.hour * 60 + it.minute }
        prefs.edit().putString(KEY_ALARMS, ClockMath.toJson(sorted)).apply()
        _alarms.value = sorted
    }

    private fun readTimer() = TimerState(
        running = prefs.getBoolean(KEY_TIMER_RUNNING, false),
        endAt = prefs.getLong(KEY_TIMER_END, 0L),
        remaining = prefs.getLong(KEY_TIMER_REMAINING, 0L),
        duration = prefs.getLong(KEY_TIMER_DURATION, 0L),
    )

    private fun writeTimer(state: TimerState) {
        prefs.edit()
            .putBoolean(KEY_TIMER_RUNNING, state.running)
            .putLong(KEY_TIMER_END, state.endAt)
            .putLong(KEY_TIMER_REMAINING, state.remaining)
            .putLong(KEY_TIMER_DURATION, state.duration)
            .apply()
        _timer.value = state
    }

    companion object {
        internal const val ACTION_RING = "org.mesos.clock.action.RING"
        internal const val ACTION_TIMER = "org.mesos.clock.action.TIMER"
        internal const val ACTION_DISMISS = "org.mesos.clock.action.DISMISS"
        internal const val ACTION_SNOOZE = "org.mesos.clock.action.SNOOZE"
        internal const val EXTRA_ALARM_ID = "alarm_id"
        internal const val TIMER_ID = -1
        private const val TAG = "MesOSClock"
        private const val KEY_ALARMS = "alarms"
        private const val KEY_TIMER_RUNNING = "timer_running"
        private const val KEY_TIMER_END = "timer_end"
        private const val KEY_TIMER_REMAINING = "timer_remaining"
        private const val KEY_TIMER_DURATION = "timer_duration"
        private const val CHANNEL_ALARMS = "mesos_alarms"
        private const val CHANNEL_TIMER = "mesos_timer"
        private const val SNOOZE_BASE = 50_000
        private const val SNOOZE_MS = 10 * 60 * 1000L
        private const val RING_TIMEOUT_MS = 10 * 60 * 1000L
        private const val RING_NOTIFICATION_BASE = 5000
        private const val TIMER_NOTIFICATION = 4990
        private const val TIMER_PROGRESS_NOTIFICATION = 4991
        private const val REQUEST_SHOW = 4980
        private const val REQUEST_TIMER = 4981

        @Volatile
        private var instance: AlarmEngine? = null

        fun get(context: Context): AlarmEngine =
            instance ?: synchronized(this) {
                instance ?: AlarmEngine(context).also { instance = it }
            }
    }
}

/** A countdown timer: running until [endAt], or paused with [remaining] left. */
data class TimerState(
    val running: Boolean = false,
    val endAt: Long = 0L,
    val remaining: Long = 0L,
    val duration: Long = 0L,
) {
    val active: Boolean get() = running || remaining > 0
}

/** Alarm, timer and notification-button broadcasts (only from MesOS's own PendingIntents). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = AlarmEngine.get(context)
        val id = intent.getIntExtra(AlarmEngine.EXTRA_ALARM_ID, 0)
        when (intent.action) {
            AlarmEngine.ACTION_RING -> engine.ring(id)
            AlarmEngine.ACTION_TIMER -> engine.timerFinished()
            AlarmEngine.ACTION_DISMISS -> engine.dismiss(id)
            AlarmEngine.ACTION_SNOOZE -> engine.snooze(id)
        }
    }
}

/** Alarms do not survive reboots or clock changes; schedule them again. */
class ClockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> AlarmEngine.get(context).rescheduleAll()
        }
    }
}
