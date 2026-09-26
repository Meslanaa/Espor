package org.mesos.contacts

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.OperationApplicationException
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.PhoneLookup
import android.provider.ContactsContract.RawContacts
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.mesos.core.log.MesOSLog

/** A contact as shown in lists. */
data class ContactSummary(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photo: Uri?,
    val starred: Boolean,
    val hasPhone: Boolean,
)

enum class PhoneKind { MOBILE, HOME, WORK, OTHER }

data class ContactPhone(val number: String, val kind: PhoneKind)

data class ContactEmail(val address: String)

/** Everything MesOS shows and edits for one contact. */
data class ContactDetail(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val givenName: String,
    val familyName: String,
    val photo: Uri?,
    val starred: Boolean,
    val phones: List<ContactPhone>,
    val emails: List<ContactEmail>,
    val company: String?,
    val rawContactId: Long?,
)

/** A contact being created or edited. */
data class ContactDraft(
    val givenName: String = "",
    val familyName: String = "",
    val phones: List<ContactPhone> = emptyList(),
    val emails: List<String> = emptyList(),
    val company: String = "",
) {
    val isEmpty: Boolean
        get() = givenName.isBlank() && familyName.isBlank() && phones.none { it.number.isNotBlank() } &&
            emails.none { it.isNotBlank() } && company.isBlank()
}

/**
 * Android's contacts database, read and written through ContactsContract. Shared
 * by MesOS Contacts, Phone and Messages. Call the query functions off the main thread.
 */
object ContactsRepository {

    fun all(context: Context): List<ContactSummary> {
        val projection = arrayOf(
            Contacts._ID,
            Contacts.LOOKUP_KEY,
            Contacts.DISPLAY_NAME_PRIMARY,
            Contacts.PHOTO_THUMBNAIL_URI,
            Contacts.STARRED,
            Contacts.HAS_PHONE_NUMBER,
        )
        val result = mutableListOf<ContactSummary>()
        try {
            context.contentResolver.query(
                Contacts.CONTENT_URI,
                projection,
                "${Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL",
                null,
                "${Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    result += ContactSummary(
                        id = c.getLong(0),
                        lookupKey = c.getString(1).orEmpty(),
                        name = c.getString(2).orEmpty(),
                        photo = c.getString(3)?.let(Uri::parse),
                        starred = c.getInt(4) == 1,
                        hasPhone = c.getInt(5) == 1,
                    )
                }
            }
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Contacts not readable", e)
        }
        return result
    }

    fun detail(context: Context, id: Long): ContactDetail? {
        val resolver = context.contentResolver
        var summary: ContactSummary? = null
        try {
            resolver.query(
                ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
                arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_URI, Contacts.STARRED, Contacts.HAS_PHONE_NUMBER),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    summary = ContactSummary(
                        id = c.getLong(0),
                        lookupKey = c.getString(1).orEmpty(),
                        name = c.getString(2).orEmpty(),
                        photo = c.getString(3)?.let(Uri::parse),
                        starred = c.getInt(4) == 1,
                        hasPhone = c.getInt(5) == 1,
                    )
                }
            }
            val found = summary ?: return null
            val phones = mutableListOf<ContactPhone>()
            val emails = mutableListOf<ContactEmail>()
            var given = ""
            var family = ""
            var company: String? = null
            var rawContactId: Long? = null
            resolver.query(
                Data.CONTENT_URI,
                arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.RAW_CONTACT_ID),
                "${Data.CONTACT_ID} = ?",
                arrayOf(id.toString()),
                "${Data.IS_PRIMARY} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    if (rawContactId == null) rawContactId = c.getLong(4)
                    when (c.getString(0)) {
                        Phone.CONTENT_ITEM_TYPE -> c.getString(1)?.takeIf { it.isNotBlank() }?.let { number ->
                            if (phones.none { same(it.number, number) }) phones += ContactPhone(number, phoneKind(c.getInt(2)))
                        }
                        Email.CONTENT_ITEM_TYPE -> c.getString(1)?.takeIf { it.isNotBlank() }?.let { address ->
                            if (emails.none { it.address.equals(address, ignoreCase = true) }) emails += ContactEmail(address)
                        }
                        StructuredName.CONTENT_ITEM_TYPE -> {
                            given = c.getString(2).orEmpty()
                            family = c.getString(3).orEmpty()
                        }
                        Organization.CONTENT_ITEM_TYPE -> company = c.getString(1)?.takeIf { it.isNotBlank() }
                    }
                }
            }
            return ContactDetail(
                id = found.id,
                lookupKey = found.lookupKey,
                name = found.name,
                givenName = given,
                familyName = family,
                photo = found.photo,
                starred = found.starred,
                phones = phones,
                emails = emails,
                company = company,
                rawContactId = rawContactId,
            )
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Contact not readable", e)
            return null
        }
    }

    /** The contact a phone number belongs to, for call logs and conversations. */
    fun lookupByNumber(context: Context, number: String): ContactSummary? {
        if (number.isBlank()) return null
        return try {
            context.contentResolver.query(
                Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(PhoneLookup._ID, PhoneLookup.LOOKUP_KEY, PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_THUMBNAIL_URI, PhoneLookup.STARRED),
                null,
                null,
                null,
            )?.use { c ->
                if (!c.moveToFirst()) {
                    null
                } else {
                    ContactSummary(
                        id = c.getLong(0),
                        lookupKey = c.getString(1).orEmpty(),
                        name = c.getString(2).orEmpty(),
                        photo = c.getString(3)?.let(Uri::parse),
                        starred = c.getInt(4) == 1,
                        hasPhone = true,
                    )
                }
            }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Phone numbers of all contacts, for search in Phone and Messages. */
    fun phones(context: Context): List<Pair<ContactSummary, ContactPhone>> {
        val result = mutableListOf<Pair<ContactSummary, ContactPhone>>()
        try {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.CONTACT_ID, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME_PRIMARY, Phone.PHOTO_THUMBNAIL_URI, Phone.STARRED, Phone.NUMBER, Phone.TYPE),
                null,
                null,
                "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(5)?.takeIf { it.isNotBlank() } ?: continue
                    val contact = ContactSummary(
                        id = c.getLong(0),
                        lookupKey = c.getString(1).orEmpty(),
                        name = c.getString(2).orEmpty(),
                        photo = c.getString(3)?.let(Uri::parse),
                        starred = c.getInt(4) == 1,
                        hasPhone = true,
                    )
                    result += contact to ContactPhone(number, phoneKind(c.getInt(6)))
                }
            }
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Phone numbers not readable", e)
        }
        return result
    }

    fun setStarred(context: Context, id: Long, starred: Boolean): Boolean =
        try {
            val values = ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }
            context.contentResolver.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), values, null, null) > 0
        } catch (e: SecurityException) {
            false
        }

    fun delete(context: Context, detail: ContactDetail): Boolean =
        try {
            val uri = Contacts.getLookupUri(detail.id, detail.lookupKey)
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: SecurityException) {
            false
        }

    /** Creates a device contact, or rewrites the fields MesOS edits on [existing]. Returns the contact id. */
    fun save(context: Context, draft: ContactDraft, existing: ContactDetail? = null): Long? {
        val ops = ArrayList<ContentProviderOperation>()
        val rawId = existing?.rawContactId
        if (rawId == null) {
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null)
                .withValue(RawContacts.ACCOUNT_NAME, null)
                .build()
        } else {
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} IN (?, ?, ?, ?)",
                    arrayOf(rawId.toString(), StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE),
                )
                .build()
        }
        fun insertData(): ContentProviderOperation.Builder =
            ContentProviderOperation.newInsert(Data.CONTENT_URI).let { builder ->
                if (rawId == null) builder.withValueBackReference(Data.RAW_CONTACT_ID, 0) else builder.withValue(Data.RAW_CONTACT_ID, rawId)
            }

        if (draft.givenName.isNotBlank() || draft.familyName.isNotBlank()) {
            ops += insertData()
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.GIVEN_NAME, draft.givenName.trim())
                .withValue(StructuredName.FAMILY_NAME, draft.familyName.trim())
                .build()
        }
        draft.phones.filter { it.number.isNotBlank() }.forEach { phone ->
            ops += insertData()
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, phone.number.trim())
                .withValue(Phone.TYPE, phoneType(phone.kind))
                .build()
        }
        draft.emails.filter { it.isNotBlank() }.forEach { email ->
            ops += insertData()
                .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, email.trim())
                .withValue(Email.TYPE, Email.TYPE_OTHER)
                .build()
        }
        if (draft.company.isNotBlank()) {
            ops += insertData()
                .withValue(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                .withValue(Organization.COMPANY, draft.company.trim())
                .build()
        }
        return try {
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            if (existing != null) {
                existing.id
            } else {
                val rawUri = results.firstOrNull()?.uri ?: return null
                val newRawId = ContentUris.parseId(rawUri)
                context.contentResolver.query(
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, newRawId),
                    arrayOf(RawContacts.CONTACT_ID),
                    null,
                    null,
                    null,
                )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            }
        } catch (e: RemoteException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Contact not saved", e)
            null
        } catch (e: OperationApplicationException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Contact not saved", e)
            null
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Contact not saved", e)
            null
        }
    }

    /** Resolves a contact URI another app handed over (VIEW intents) to its id. */
    fun idOf(context: Context, uri: Uri): Long? =
        try {
            Contacts.lookupContact(context.contentResolver, uri)?.let(ContentUris::parseId)
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    /** vCard of a contact, for sharing. */
    fun vcardUri(detail: ContactDetail): Uri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, detail.lookupKey)

    /** Emits whenever Android's contacts change. */
    fun changes(context: Context): Flow<Int> = callbackFlow {
        // A counter, not Unit: Compose only reacts to values that differ.
        var version = 0
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(++version)
            }
        }
        context.contentResolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)
        trySend(version)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun phoneKind(type: Int): PhoneKind = when (type) {
        Phone.TYPE_MOBILE -> PhoneKind.MOBILE
        Phone.TYPE_HOME -> PhoneKind.HOME
        Phone.TYPE_WORK, Phone.TYPE_WORK_MOBILE -> PhoneKind.WORK
        else -> PhoneKind.OTHER
    }

    private fun phoneType(kind: PhoneKind): Int = when (kind) {
        PhoneKind.MOBILE -> Phone.TYPE_MOBILE
        PhoneKind.HOME -> Phone.TYPE_HOME
        PhoneKind.WORK -> Phone.TYPE_WORK
        PhoneKind.OTHER -> Phone.TYPE_OTHER
    }

    /** Same number written differently ("+90 555 111" vs "+90555111"). */
    fun same(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }
        val y = b.filter { it.isDigit() }
        if (x.isEmpty() || y.isEmpty()) return a == b
        val tail = minOf(x.length, y.length, 10)
        return x.takeLast(tail) == y.takeLast(tail)
    }
}
