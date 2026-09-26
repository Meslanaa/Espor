package org.mesos.core.log

import android.util.Log
import org.mesos.core.BuildConfig

/**
 * Logcat tags and helpers shared by all MesOS components.
 *
 * Show only MesOS output with:
 * `adb logcat -s MesOS MesOSLauncher MesOSSettings MesOSUpdater`
 *
 * Log state changes and failures, not per-frame or polling events.
 */
object MesOSLog {
    const val SYSTEM = "MesOS"
    const val LAUNCHER = "MesOSLauncher"
    const val SETTINGS = "MesOSSettings"
    const val UPDATER = "MesOSUpdater"

    /** Debug-only diagnostics; suppressed in release builds. */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
    }
}
