package org.mesos.messages

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import org.mesos.core.log.MesOSLog

/**
 * Sends SMS through Android's SmsManager. As the default SMS app MesOS files the
 * message in the outbox and moves it to sent (or failed); otherwise Android files it.
 */
object SmsSender {

    const val ACTION_SENT = "org.mesos.messages.action.SENT"
    private const val EXTRA_URI = "uri"

    fun send(context: Context, address: String, body: String, subscriptionId: Int? = null): Boolean {
        val text = body.trim()
        if (address.isBlank() || text.isEmpty()) return false
        val manager = smsManager(context, subscriptionId) ?: return false
        val default = SmsRepository.isDefaultSmsApp(context)
        val uri = if (default) insertOutbox(context, address, text) else null
        return try {
            val parts = manager.divideMessage(text)
            val sent = ArrayList<PendingIntent>(parts.size)
            repeat(parts.size) { index ->
                sent += PendingIntent.getBroadcast(
                    context,
                    (uri?.hashCode() ?: text.hashCode()) * 31 + index,
                    Intent(context, SmsSentReceiver::class.java)
                        .setAction(ACTION_SENT)
                        .putExtra(EXTRA_URI, uri?.toString()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            if (parts.size == 1) {
                manager.sendTextMessage(address, null, text, sent.first(), null)
            } else {
                manager.sendMultipartTextMessage(address, null, parts, sent, null)
            }
            MesOSLog.i(MesOSLog.SYSTEM, "SMS sent to the radio (${parts.size} part(s))")
            true
        } catch (e: IllegalArgumentException) {
            MesOSLog.w(MesOSLog.SYSTEM, "SMS not sent", e)
            uri?.let { markFailed(context, it) }
            false
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "SMS not allowed", e)
            uri?.let { markFailed(context, it) }
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context, subscriptionId: Int?): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(SmsManager::class.java)
            if (subscriptionId != null && subscriptionId >= 0) manager?.createForSubscriptionId(subscriptionId) else manager
        } else if (subscriptionId != null && subscriptionId >= 0) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getDefault()
        }

    private fun insertOutbox(context: Context, address: String, body: String): Uri? =
        try {
            context.contentResolver.insert(
                Telephony.Sms.Outbox.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                },
            )
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Could not file the outgoing SMS", e)
            null
        }

    internal fun markSent(context: Context, uri: Uri) {
        try {
            val values = ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) }
            // A failed part keeps the whole message failed.
            context.contentResolver.update(uri, values, "${Telephony.Sms.TYPE} = ?", arrayOf(Telephony.Sms.MESSAGE_TYPE_OUTBOX.toString()))
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Could not update the sent SMS", e)
        }
    }

    internal fun markFailed(context: Context, uri: Uri) {
        try {
            val values = ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED) }
            context.contentResolver.update(uri, values, null, null)
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Could not update the failed SMS", e)
        }
    }

    internal fun uriOf(intent: Intent): Uri? = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
}

/** Result of each sent SMS part. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsSender.ACTION_SENT) return
        val ok = resultCode == Activity.RESULT_OK
        val uri = SmsSender.uriOf(intent)
        if (uri != null) {
            if (ok) SmsSender.markSent(context, uri) else SmsSender.markFailed(context, uri)
        }
        if (!ok) {
            MesOSLog.w(MesOSLog.SYSTEM, "SMS part failed with result $resultCode")
            MessageNotifications.sendFailed(context)
        }
    }
}
