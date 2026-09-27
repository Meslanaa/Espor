package org.mesos.messages

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.mesos.contacts.ContactsRepository

/** Notifications for incoming messages, with reply and mark-as-read. */
internal object MessageNotifications {
    private const val CHANNEL = "mesos_messages"
    private const val CHANNEL_ALERTS = "mesos_messages_alerts"
    private const val FAILED_ID = 4500
    const val ACTION_REPLY = "org.mesos.messages.action.REPLY"
    const val ACTION_MARK_READ = "org.mesos.messages.action.MARK_READ"
    const val EXTRA_THREAD = "thread"
    const val EXTRA_ADDRESS = "address"
    const val KEY_REPLY = "reply"

    fun incoming(context: Context, threadId: Long, address: String, body: String) {
        val manager = manager(context) ?: return
        val name = ContactsRepository.lookupByNumber(context, address)?.name?.takeIf { it.isNotBlank() } ?: address
        val open = PendingIntent.getActivity(
            context,
            threadId.toInt(),
            MessagesActivity.conversationIntent(context, address),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel(context.getString(R.string.messages_reply)).build()
        val reply = PendingIntent.getBroadcast(
            context,
            threadId.toInt(),
            actionIntent(context, ACTION_REPLY, threadId, address),
            // Must be mutable so Android can add the typed reply (explicit intent).
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0) or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val markRead = PendingIntent.getBroadcast(
            context,
            threadId.toInt() + 1,
            actionIntent(context, ACTION_MARK_READ, threadId, address),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sender = Person.Builder().setName(name).setKey(address).build()
            val me = Person.Builder().setName(context.getString(R.string.messages_me)).build()
            Notification.MessagingStyle(me).addMessage(body, System.currentTimeMillis(), sender)
        } else {
            Notification.BigTextStyle().bigText(body)
        }
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_messages_notification)
            .setStyle(style)
            .setContentTitle(name)
            .setContentText(body)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(null, context.getString(R.string.messages_reply), reply)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(true)
                    .build(),
            )
            .addAction(Notification.Action.Builder(null, context.getString(R.string.messages_mark_read), markRead).build())
            .build()
        manager.notify(tag(threadId), 0, notification)
    }

    fun mmsWaiting(context: Context, threadId: Long, address: String) {
        val manager = manager(context) ?: return
        val name = ContactsRepository.lookupByNumber(context, address)?.name?.takeIf { it.isNotBlank() } ?: address
        val open = PendingIntent.getActivity(
            context,
            threadId.toInt(),
            MessagesActivity.conversationIntent(context, address),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            tag(threadId),
            0,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_messages_notification)
                .setContentTitle(name.ifBlank { context.getString(R.string.messages_unknown) })
                .setContentText(context.getString(R.string.messages_mms_waiting))
                .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.messages_mms_waiting_long)))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun sendFailed(context: Context) {
        val manager = manager(context) ?: return
        manager.notify(
            FAILED_ID,
            Notification.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_messages_notification)
                .setContentTitle(context.getString(R.string.messages_send_failed))
                .setContentText(context.getString(R.string.messages_send_failed_text))
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        FAILED_ID,
                        Intent(context, MessagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build(),
        )
    }

    fun cancel(context: Context, threadId: Long) {
        context.getSystemService(NotificationManager::class.java)?.cancel(tag(threadId), 0)
    }

    private fun tag(threadId: Long) = "thread-$threadId"

    private fun actionIntent(context: Context, action: String, threadId: Long, address: String): Intent =
        Intent(context, MessageActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_THREAD, threadId)
            .putExtra(EXTRA_ADDRESS, address)

    private fun manager(context: Context): NotificationManager? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.messages_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.messages_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
        )
        return manager
    }
}
