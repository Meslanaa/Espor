package org.mesos.updater

import android.content.Context

/** Updater state that must survive restarts and reboots. */
internal class UpdatePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("mesos_updater", Context.MODE_PRIVATE)

    var lastCheckedAt: Long?
        get() = prefs.getLong(KEY_LAST_CHECKED_AT, -1L).takeIf { it > 0 }
        set(value) {
            prefs.edit().putLong(KEY_LAST_CHECKED_AT, value ?: -1L).apply()
        }

    val lastSeenVersionCode: Int
        get() = prefs.getInt(KEY_LAST_SEEN_CODE, -1)

    val lastSeenVersionName: String?
        get() = prefs.getString(KEY_LAST_SEEN_NAME, null)

    /**
     * Release notes of an update handed to the installer, keyed by its version code.
     * Written synchronously (call off the main thread): installing the update kills this process.
     */
    fun savePendingNotes(versionCode: Int, notes: String) {
        prefs.edit()
            .putInt(KEY_PENDING_NOTES_CODE, versionCode)
            .putString(KEY_PENDING_NOTES, notes)
            .commit()
    }

    fun pendingNotesFor(versionCode: Int): String? =
        if (prefs.getInt(KEY_PENDING_NOTES_CODE, -1) == versionCode) prefs.getString(KEY_PENDING_NOTES, null) else null

    /** Highest version the background check already told the user about. */
    var lastNotifiedVersionCode: Int
        get() = prefs.getInt(KEY_LAST_NOTIFIED_CODE, -1)
        set(value) {
            prefs.edit().putInt(KEY_LAST_NOTIFIED_CODE, value).apply()
        }

    fun recordSeen(versionCode: Int, versionName: String) {
        prefs.edit()
            .putInt(KEY_LAST_SEEN_CODE, versionCode)
            .putString(KEY_LAST_SEEN_NAME, versionName)
            .apply()
    }

    private companion object {
        const val KEY_LAST_CHECKED_AT = "last_checked_at"
        const val KEY_LAST_SEEN_CODE = "last_seen_version_code"
        const val KEY_LAST_SEEN_NAME = "last_seen_version_name"
        const val KEY_PENDING_NOTES_CODE = "pending_notes_version_code"
        const val KEY_PENDING_NOTES = "pending_notes"
        const val KEY_LAST_NOTIFIED_CODE = "last_notified_version_code"
    }
}
