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

    fun setThemeMode(mode: ThemeMode) {
        if (mode == _themeMode.value) return
        prefs.edit().putString(KEY_THEME_MODE, mode.id).apply()
        _themeMode.value = mode
        MesOSLog.i(MesOSLog.SYSTEM, "Theme mode set to ${mode.id}")
    }

    companion object {
        private const val FILE_NAME = "mesos_preferences"
        private const val KEY_THEME_MODE = "theme_mode"

        @Volatile
        private var instance: MesOSPreferences? = null

        fun get(context: Context): MesOSPreferences =
            instance ?: synchronized(this) {
                instance ?: MesOSPreferences(context.applicationContext).also { instance = it }
            }
    }
}
