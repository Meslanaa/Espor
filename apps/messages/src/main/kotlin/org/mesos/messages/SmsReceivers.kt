package org.mesos.messages

import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Telephony
import org.mesos.core.log.MesOSLog

/**
 * Incoming SMS while MesOS is the default SMS app: Android hands it only to MesOS,
 * which stores it in the inbox and notifies.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val address = parts.first().displayOriginatingAddress ?: parts.first().originatingAddress.orEmpty()
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val sent = parts.first().timestampMillis
        val subscription = intent.getIntExtra("subscription", -1)
        val pending = goAsync()
        Thread {
            try {
                val uri = context.contentResolver.insert(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, body)
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                        put(Telephony.Sms.DATE_SENT, sent)
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.SEEN, 0)
                        if (subscription >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscription)
                    },
                )
                val threadId = uri?.let { threadOf(context, it) } ?: SmsRepository.threadFor(context, address) ?: 0L
                MessageNotifications.incoming(context, threadId, address, body)
                MesOSLog.i(MesOSLog.SYSTEM, "SMS received and stored")
            } catch (e: RuntimeException) {
                MesOSLog.e(MesOSLog.SYSTEM, "Incoming SMS could not be stored", e)
                MessageNotifications.incoming(context, 0L, address, body)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun threadOf(context: Context, uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(Telephony.Sms.THREAD_ID), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }
}

/**
 * MMS announcements while MesOS is the default SMS app. MesOS 0.3 cannot download
 * MMS yet, so it files the announcement the standard way (a notification-ind row),
 * which a later MMS-capable app can still download, and tells the user.
 */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val pdu = intent.getByteArrayExtra("data") ?: return
        val notification = MmsNotificationParser.parse(pdu) ?: return
        val pending = goAsync()
        Thread {
            try {
                val from = notification.from.orEmpty()
                val threadId = SmsRepository.threadFor(context, from.ifBlank { "insert-address-token" }) ?: 0L
                val now = System.currentTimeMillis() / 1000
                val expiry = notification.expiry?.let { if (notification.expiryRelative) now + it else it }
                val uri = context.contentResolver.insert(
                    Telephony.Mms.Inbox.CONTENT_URI,
                    ContentValues().apply {
                        put(Telephony.Mms.THREAD_ID, threadId)
                        put(Telephony.Mms.DATE, now)
                        put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
                        put(Telephony.Mms.MESSAGE_TYPE, 130)
                        put(Telephony.Mms.READ, 0)
                        put(Telephony.Mms.SEEN, 0)
                        put(Telephony.Mms.CONTENT_LOCATION, notification.contentLocation)
                        put(Telephony.Mms.TRANSACTION_ID, notification.transactionId)
                        notification.subject?.let { put(Telephony.Mms.SUBJECT, it) }
                        notification.messageSize?.let { put(Telephony.Mms.MESSAGE_SIZE, it) }
                        expiry?.let { put(Telephony.Mms.EXPIRY, it) }
                        notification.version?.let { put(Telephony.Mms.MMS_VERSION, it) }
                    },
                )
                if (uri != null && from.isNotBlank()) {
                    context.contentResolver.insert(
                        Uri.parse("$uri/addr"),
                        ContentValues().apply {
                            put(Telephony.Mms.Addr.ADDRESS, from)
                            put(Telephony.Mms.Addr.TYPE, 137)
                            put(Telephony.Mms.Addr.CHARSET, 106)
                        },
                    )
                }
                MessageNotifications.mmsWaiting(context, threadId, from)
                MesOSLog.i(MesOSLog.SYSTEM, "MMS announcement filed")
            } catch (e: RuntimeException) {
                MesOSLog.e(MesOSLog.SYSTEM, "MMS announcement could not be filed", e)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** "Reply" and "Mark as read" from message notifications. */
class MessageActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(MessageNotifications.EXTRA_THREAD, 0L)
        val address = intent.getStringExtra(MessageNotifications.EXTRA_ADDRESS).orEmpty()
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    MessageNotifications.ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(MessageNotifications.KEY_REPLY)?.toString()
                        if (!text.isNullOrBlank() && address.isNotBlank()) {
                            SmsSender.send(context, address, text)
                            SmsRepository.markRead(context, threadId)
                        }
                        MessageNotifications.cancel(context, threadId)
                    }
                    MessageNotifications.ACTION_MARK_READ -> {
                        SmsRepository.markRead(context, threadId)
                        MessageNotifications.cancel(context, threadId)
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/**
 * "Respond via message" for incoming calls (required for the SMS role): sends the
 * chosen quick reply as an SMS.
 */
class QuickReplyService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart?.substringBefore('?').orEmpty()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (intent?.action == "android.intent.action.RESPOND_VIA_MESSAGE" && address.isNotBlank() && text.isNotBlank()) {
            Thread {
                SmsSender.send(this, address, text)
                stopSelf(startId)
            }.start()
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}
