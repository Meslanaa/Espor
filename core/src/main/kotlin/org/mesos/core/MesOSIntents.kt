package org.mesos.core

import android.content.Context
import android.content.Intent

/** Intents MesOS components use to reach each other without compile-time coupling. */
object MesOSIntents {
    /** Opens MesOS Settings. Declared by the settings module. */
    const val ACTION_SETTINGS = "org.mesos.intent.action.SETTINGS"

    /** Settings page to open: one of the PAGE_ values. */
    const val EXTRA_SETTINGS_PAGE = "org.mesos.intent.extra.SETTINGS_PAGE"
    const val PAGE_UPDATE = "update"
    const val PAGE_APPEARANCE = "appearance"
    const val PAGE_HOME = "home"
    const val PAGE_NOTIFICATIONS = "notifications"
    const val PAGE_PRIVACY = "privacy"

    /** Starts or stops MesOS screen recording (handled by the recorder module). */
    const val ACTION_TOGGLE_SCREEN_RECORDING = "org.mesos.intent.action.TOGGLE_SCREEN_RECORDING"

    fun settings(context: Context, page: String? = null): Intent =
        Intent(ACTION_SETTINGS)
            .setPackage(context.packageName)
            .apply { if (page != null) putExtra(EXTRA_SETTINGS_PAGE, page) }
}
