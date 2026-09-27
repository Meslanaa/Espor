package org.mesos.messages

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.mesos.core.log.MesOSLog

/** A conversation in the list. */
data class Conversation(
    val threadId: Long,
    val addresses: List<String>,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
    val count: Int,
)

enum class MessageBox { INBOX, SENT, OUTBOX, FAILED, DRAFT }

/** One SMS, or the text and pictures of one MMS. */
data class Message(
    val key: String,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val box: MessageBox,
    val read: Boolean,
    val images: List<Uri> = emptyList(),
    /** An MMS that was announced but not downloaded. */
    val pendingMms: Boolean = false,
) {
    val outgoing: Boolean get() = box != MessageBox.INBOX
}

/**
 * Android's SMS/MMS provider. Anyone with READ_SMS can read; only the default
 * SMS app may write (mark read, delete, store received messages).
 */
object SmsRepository {

    private val conversationsUri: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
    private val canonicalUri: Uri = Uri.parse("content://mms-sms/canonical-addresses")

    fun conversations(context: Context): List<Conversation> {
        val resolver = context.contentResolver
        val addressById = mutableMapOf<Long, String>()
        val result = mutableListOf<Conversation>()
        try {
            resolver.query(canonicalUri, arrayOf("_id", "address"), null, null, null)?.use { c ->
                while (c.moveToNext()) addressById[c.getLong(0)] = c.getString(1).orEmpty()
            }
            resolver.query(
                conversationsUri,
                arrayOf("_id", "date", "message_count", "recipient_ids", "snippet", "read"),
                null,
                null,
                "date DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val count = c.getInt(2)
                    if (count == 0) continue
                    val addresses = c.getString(3).orEmpty().split(' ').mapNotNull { it.toLongOrNull()?.let(addressById::get) }
                    result += Conversation(
                        threadId = c.getLong(0),
                        addresses = addresses,
                        snippet = c.getString(4).orEmpty(),
                        date = c.getLong(1),
                        unread = c.getInt(5) == 0,
                        count = count,
                    )
                }
            }
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Messages not readable", e)
        } catch (e: IllegalArgumentException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Conversations not available", e)
        }
        return result
    }

    /** The thread a set of addresses belongs to (created if needed, which needs the SMS role). */
    fun threadFor(context: Context, address: String): Long? =
        try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (e: RuntimeException) {
            null
        }

    fun messages(context: Context, threadId: Long): List<Message> {
        val resolver = context.contentResolver
        val result = mutableListOf<Message>()
        try {
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    result += Message(
                        key = "sms-" + c.getLong(0),
                        threadId = threadId,
                        address = c.getString(1).orEmpty(),
                        body = c.getString(2).orEmpty(),
                        date = c.getLong(3),
                        box = smsBox(c.getInt(4)),
                        read = c.getInt(5) == 1,
                    )
                }
            }
            resolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ, Telephony.Mms.MESSAGE_TYPE),
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val pending = c.getInt(4) == 130 // m-notification-ind: not downloaded.
                    val (text, images) = if (pending) "" to emptyList() else mmsParts(context, id)
                    result += Message(
                        key = "mms-$id",
                        threadId = threadId,
                        address = mmsAddress(context, id).orEmpty(),
                        body = text,
                        date = c.getLong(1) * 1000,
                        box = mmsBox(c.getInt(2)),
                        read = c.getInt(3) == 1,
                        images = images,
                        pendingMms = pending,
                    )
                }
            }
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Messages not readable", e)
        }
        return result.sortedBy { it.date }
    }

    private fun mmsParts(context: Context, id: Long): Pair<String, List<Uri>> {
        val text = StringBuilder()
        val images = mutableListOf<Uri>()
        context.contentResolver.query(
            Uri.parse("content://mms/part"),
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT),
            "${Telephony.Mms.Part.MSG_ID} = ?",
            arrayOf(id.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getString(1).orEmpty()
                when {
                    type == "text/plain" -> c.getString(2)?.let { if (text.isNotEmpty()) text.append('\n'); text.append(it) }
                    type.startsWith("image/") -> images += Uri.parse("content://mms/part/" + c.getLong(0))
                }
            }
        }
        return text.toString() to images
    }

    private fun mmsAddress(context: Context, id: Long): String? =
        context.contentResolver.query(
            Uri.parse("content://mms/$id/addr"),
            arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
            null,
            null,
            null,
        )?.use { c ->
            var from: String? = null
            var to: String? = null
            while (c.moveToNext()) {
                val address = c.getString(0)?.takeIf { it.isNotBlank() && it != "insert-address-token" } ?: continue
                when (c.getInt(1)) {
                    137 -> from = address
                    151 -> if (to == null) to = address
                }
            }
            from ?: to
        }

    /** Marks a conversation read (default SMS app only; otherwise nothing happens). */
    fun markRead(context: Context, threadId: Long) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        try {
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
            context.contentResolver.update(Telephony.Mms.CONTENT_URI, values, "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(threadId.toString()))
        } catch (e: SecurityException) {
            // Not the default SMS app.
        } catch (e: IllegalArgumentException) {
            // Provider refused the update.
        }
    }

    fun deleteConversation(context: Context, threadId: Long): Boolean =
        try {
            context.contentResolver.delete(Uri.parse("content://mms-sms/conversations/$threadId"), null, null) > 0
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }

    fun deleteMessage(context: Context, message: Message): Boolean {
        val uri = when {
            message.key.startsWith("sms-") -> Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, message.key.removePrefix("sms-"))
            else -> Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, message.key.removePrefix("mms-"))
        }
        return try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: SecurityException) {
            false
        }
    }

    /** Emits whenever messages change. */
    fun changes(context: Context): Flow<Int> = callbackFlow {
        // A counter, not Unit: Compose only reacts to values that differ.
        var version = 0
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(++version)
            }
        }
        val resolver = context.contentResolver
        try {
            resolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer)
            resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (e: SecurityException) {
            // No permission yet.
        }
        trySend(version)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    fun isDefaultSmsApp(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }

    private fun smsBox(type: Int): MessageBox = when (type) {
        Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageBox.INBOX
        Telephony.Sms.MESSAGE_TYPE_SENT -> MessageBox.SENT
        Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> MessageBox.OUTBOX
        Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageBox.FAILED
        Telephony.Sms.MESSAGE_TYPE_DRAFT -> MessageBox.DRAFT
        else -> MessageBox.INBOX
    }

    private fun mmsBox(box: Int): MessageBox = when (box) {
        Telephony.Mms.MESSAGE_BOX_INBOX -> MessageBox.INBOX
        Telephony.Mms.MESSAGE_BOX_SENT -> MessageBox.SENT
        Telephony.Mms.MESSAGE_BOX_OUTBOX -> MessageBox.OUTBOX
        Telephony.Mms.MESSAGE_BOX_FAILED -> MessageBox.FAILED
        Telephony.Mms.MESSAGE_BOX_DRAFTS -> MessageBox.DRAFT
        else -> MessageBox.INBOX
    }
}
