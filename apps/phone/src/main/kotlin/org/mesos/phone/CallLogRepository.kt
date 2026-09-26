package org.mesos.phone

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.mesos.contacts.ContactsRepository
import org.mesos.core.log.MesOSLog

enum class CallKind { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL }

/** Consecutive calls with the same number and kind, shown as one row. */
data class CallGroup(
    val ids: List<Long>,
    val number: String,
    val name: String?,
    val photo: Uri?,
    val kind: CallKind,
    val time: Long,
    val durationSeconds: Long,
) {
    val count: Int get() = ids.size
}

/** Android's call log. Reading needs READ_CALL_LOG, deleting WRITE_CALL_LOG. */
object CallLogRepository {

    fun recent(context: Context, limit: Int = 300): List<CallGroup> {
        val rows = mutableListOf<CallGroup>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.CACHED_PHOTO_URI,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                var read = 0
                while (c.moveToNext() && read < limit) {
                    read++
                    val kind = kindOf(c.getInt(4))
                    val number = c.getString(1).orEmpty()
                    val last = rows.lastOrNull()
                    if (last != null && last.kind == kind && ContactsRepository.same(last.number, number)) {
                        rows[rows.lastIndex] = last.copy(ids = last.ids + c.getLong(0))
                    } else {
                        rows += CallGroup(
                            ids = listOf(c.getLong(0)),
                            number = number,
                            name = c.getString(2)?.takeIf { it.isNotBlank() },
                            photo = c.getString(3)?.let(Uri::parse),
                            kind = kind,
                            time = c.getLong(5),
                            durationSeconds = c.getLong(6),
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Call log not readable", e)
        }
        return rows
    }

    fun delete(context: Context, group: CallGroup): Boolean =
        try {
            val placeholders = group.ids.joinToString(",") { "?" }
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} IN ($placeholders)",
                group.ids.map { it.toString() }.toTypedArray(),
            ) > 0
        } catch (e: SecurityException) {
            false
        }

    fun clear(context: Context): Boolean =
        try {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null) >= 0
        } catch (e: SecurityException) {
            false
        }

    fun changes(context: Context): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        try {
            context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        } catch (e: SecurityException) {
            // No permission yet; the screen reloads when it is granted.
        }
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun kindOf(type: Int): CallKind = when (type) {
        CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallKind.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallKind.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallKind.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallKind.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallKind.BLOCKED
        CallLog.Calls.VOICEMAIL_TYPE -> CallKind.VOICEMAIL
        else -> CallKind.INCOMING
    }
}
