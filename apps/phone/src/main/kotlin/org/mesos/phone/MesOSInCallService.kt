package org.mesos.phone

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mesos.core.log.MesOSLog

/**
 * MesOS's in-call UI, bound by Android's Telecom while MesOS is the default
 * phone app. Android plays the ringtone; MesOS shows the calls.
 */
class MesOSInCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
        scope.launch {
            CallManager.state.collect { calls -> CallNotifications.update(this@MesOSInCallService, calls) }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(this, call)
        MesOSLog.i(MesOSLog.SYSTEM, "Call added")
        // Incoming calls ring through a full-screen notification; others open the call screen now.
        val ringing = CallManager.state.value.any { it.status == CallStatus.RINGING }
        if (!ringing) startActivity(InCallActivity.intent(this))
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(call)
        MesOSLog.i(MesOSLog.SYSTEM, "Call removed")
    }

    @Deprecated("Replaced by call endpoints on Android 14; still delivered for audio state")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        @Suppress("DEPRECATION")
        super.onCallAudioStateChanged(audioState)
        CallManager.audioChanged(audioState)
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        CallNotifications.cancel(this)
        scope.cancel()
        super.onDestroy()
    }
}

/** Decline and hang up from notifications (answering opens the call screen instead). */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_CALL, 0)
        when (intent.action) {
            ACTION_DECLINE -> CallManager.decline(id)
            ACTION_HANG_UP -> CallManager.hangUp(id)
        }
    }

    companion object {
        const val ACTION_DECLINE = "org.mesos.phone.action.DECLINE"
        const val ACTION_HANG_UP = "org.mesos.phone.action.HANG_UP"
        const val EXTRA_CALL = "call"

        fun pending(context: Context, action: String, id: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                action.hashCode() xor id,
                Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(EXTRA_CALL, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

internal object CallNotifications {
    private const val CHANNEL_INCOMING = "mesos_calls_incoming"
    private const val CHANNEL_ONGOING = "mesos_calls_ongoing"
    private const val NOTIFICATION_ID = 4400

    fun update(context: Context, calls: List<CallInfo>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val call = calls.firstOrNull { it.status == CallStatus.RINGING } ?: calls.firstOrNull { it.status != CallStatus.ENDED }
        if (call == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        ensureChannels(context, manager)
        val screen = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            InCallActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val incoming = call.status == CallStatus.RINGING
        val builder = Notification.Builder(context, if (incoming) CHANNEL_INCOMING else CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_phone_notification)
            .setContentTitle(call.displayName)
            .setContentText(context.getString(if (incoming) R.string.phone_incoming else R.string.phone_ongoing))
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(screen)
        if (incoming) builder.setFullScreenIntent(screen, true)
        val decline = CallActionReceiver.pending(context, CallActionReceiver.ACTION_DECLINE, call.id)
        // Answering opens the call screen directly (activities may not be started from receivers).
        val answer = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 1,
            InCallActivity.intent(context).putExtra(InCallActivity.EXTRA_ANSWER, call.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hangUp = CallActionReceiver.pending(context, CallActionReceiver.ACTION_HANG_UP, call.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = Person.Builder().setName(call.displayName).setImportant(true).build()
            builder.setStyle(
                if (incoming) {
                    Notification.CallStyle.forIncomingCall(person, decline, answer)
                } else {
                    Notification.CallStyle.forOngoingCall(person, hangUp)
                },
            )
        } else if (incoming) {
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.phone_decline), decline).build())
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.phone_answer),
                    answer,
                ).build(),
            )
        } else {
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.phone_hang_up), hangUp).build())
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: IllegalArgumentException) {
            // CallStyle needs a full-screen intent or foreground service; fall back to a plain notification.
            builder.setStyle(null)
            manager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannels(context: Context, manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, context.getString(R.string.phone_channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                // Telecom plays the ringtone; the notification itself stays silent.
                setSound(null, null)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.phone_channel_ongoing), NotificationManager.IMPORTANCE_LOW),
        )
    }
}
