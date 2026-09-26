package org.mesos.core

import android.content.Context
import android.content.Intent

/** Intents MesOS components use to reach each other without compile-time coupling. */
object MesOSIntents {
    /** Opens MesOS Settings. Declared by the settings module. */
    const val ACTION_SETTINGS = "org.mesos.intent.action.SETTINGS"

    fun settings(context: Context): Intent =
        Intent(ACTION_SETTINGS).setPackage(context.packageName)
}
