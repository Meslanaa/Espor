package org.mesos.core.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mesos.core.log.MesOSLog

/** Appearance chosen in MesOS Settings → Display. */
enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Persistent MesOS preferences. Values survive reboots and MesOS updates.
 * Only settings MesOS actually implements are stored here.
 */
class MesOSPreferences private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.fromId(prefs.getString(KEY_THEME_MODE, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _showAndroidApps = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ANDROID_APPS, false))

    /**
     * false (default, "MesOS mode"): MesOS Home lists only MesOS apps, Google Play and
     * apps the user installed. true: preinstalled Android apps are listed too.
     */
    val showAndroidApps: StateFlow<Boolean> = _showAndroidApps.asStateFlow()

    fun setShowAndroidApps(show: Boolean) {
        if (show == _showAndroidApps.value) return
        prefs.edit().putBoolean(KEY_SHOW_ANDROID_APPS, show).apply()
        _showAndroidApps.value = show
        MesOSLog.i(MesOSLog.SYSTEM, "Show Android apps set to $show")
    }

    fun setThemeMode(mode: ThemeMode) {
        if (mode == _themeMode.value) return
        prefs.edit().putString(KEY_THEME_MODE, mode.id).apply()
        _themeMode.value = mode
        MesOSLog.i(MesOSLog.SYSTEM, "Theme mode set to ${mode.id}")
    }

    companion object {
        private const val FILE_NAME = "mesos_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SHOW_ANDROID_APPS = "show_android_apps"

        @Volatile
        private var instance: MesOSPreferences? = null

        fun get(context: Context): MesOSPreferences =
            instance ?: synchronized(this) {
                instance ?: MesOSPreferences(context.applicationContext).also { instance = it }
            }
    }
}
