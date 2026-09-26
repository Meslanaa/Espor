package org.mesos.core.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mesos.core.log.MesOSLog

/** Appearance chosen in MesOS Settings → Appearance. */
enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** MesOS accent colour. [base] is used on dark surfaces, [deep] on light ones. */
enum class Accent(val id: String, val base: Long, val deep: Long) {
    INDIGO("indigo", 0xFF6D7CFF, 0xFF4F5BD5),
    TEAL("teal", 0xFF2DD4BF, 0xFF0D9488),
    GREEN("green", 0xFF34D399, 0xFF059669),
    ORANGE("orange", 0xFFFB923C, 0xFFEA580C),
    ROSE("rose", 0xFFF43F5E, 0xFFE11D48),
    VIOLET("violet", 0xFFA78BFA, 0xFF7C3AED);

    companion object {
        fun fromId(id: String?): Accent = entries.firstOrNull { it.id == id } ?: INDIGO
    }
}

/** Home screen wallpaper. [SYSTEM] shows Android's wallpaper behind MesOS Home. */
enum class Wallpaper(val id: String) {
    AURORA("aurora"),
    AURORA_STILL("aurora_still"),
    DAWN("dawn"),
    OCEAN("ocean"),
    GRAPHITE("graphite"),
    SYSTEM("system");

    companion object {
        fun fromId(id: String?): Wallpaper = entries.firstOrNull { it.id == id } ?: AURORA
    }
}

/** Shape MesOS Home gives every app icon. */
enum class IconShape(val id: String) {
    SQUIRCLE("squircle"),
    CIRCLE("circle"),
    ROUNDED("rounded");

    companion object {
        fun fromId(id: String?): IconShape = entries.firstOrNull { it.id == id } ?: SQUIRCLE
    }
}

/**
 * Persistent MesOS preferences. Values survive reboots and MesOS updates.
 * Only settings MesOS actually implements are stored here.
 */
class MesOSPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.fromId(prefs.getString(KEY_THEME_MODE, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accent = MutableStateFlow(Accent.fromId(prefs.getString(KEY_ACCENT, null)))
    val accent: StateFlow<Accent> = _accent.asStateFlow()

    private val _wallpaper = MutableStateFlow(Wallpaper.fromId(prefs.getString(KEY_WALLPAPER, null)))
    val wallpaper: StateFlow<Wallpaper> = _wallpaper.asStateFlow()

    private val _iconShape = MutableStateFlow(IconShape.fromId(prefs.getString(KEY_ICON_SHAPE, null)))
    val iconShape: StateFlow<IconShape> = _iconShape.asStateFlow()

    private val _themedIcons = MutableStateFlow(prefs.getBoolean(KEY_THEMED_ICONS, false))

    /** Draw app icons in the accent colour (monochrome layer, Android 13+). */
    val themedIcons: StateFlow<Boolean> = _themedIcons.asStateFlow()

    private val _notificationBadges = MutableStateFlow(prefs.getBoolean(KEY_BADGES, true))
    val notificationBadges: StateFlow<Boolean> = _notificationBadges.asStateFlow()

    private val _autoUpdateCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, true))

    /** Check for MesOS updates in the background once a day (installing still asks). */
    val autoUpdateCheck: StateFlow<Boolean> = _autoUpdateCheck.asStateFlow()

    private val _showAndroidApps = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ANDROID_APPS, false))

    /**
     * false (default, "MesOS mode"): MesOS Home lists only MesOS apps, Google Play and
     * apps the user installed. true: preinstalled Android apps are listed too.
     */
    val showAndroidApps: StateFlow<Boolean> = _showAndroidApps.asStateFlow()

    private val _setupDone = MutableStateFlow(prefs.getBoolean(KEY_SETUP_DONE, false))

    /** The MesOS setup wizard was completed (or skipped) on this device. */
    val setupDone: StateFlow<Boolean> = _setupDone.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = update(_themeMode, mode, "Theme mode") {
        putString(KEY_THEME_MODE, mode.id)
    }

    fun setAccent(accent: Accent) = update(_accent, accent, "Accent") {
        putString(KEY_ACCENT, accent.id)
    }

    fun setWallpaper(wallpaper: Wallpaper) = update(_wallpaper, wallpaper, "Wallpaper") {
        putString(KEY_WALLPAPER, wallpaper.id)
    }

    fun setIconShape(shape: IconShape) = update(_iconShape, shape, "Icon shape") {
        putString(KEY_ICON_SHAPE, shape.id)
    }

    fun setThemedIcons(enabled: Boolean) = update(_themedIcons, enabled, "Themed icons") {
        putBoolean(KEY_THEMED_ICONS, enabled)
    }

    fun setNotificationBadges(enabled: Boolean) = update(_notificationBadges, enabled, "Notification badges") {
        putBoolean(KEY_BADGES, enabled)
    }

    fun setAutoUpdateCheck(enabled: Boolean) = update(_autoUpdateCheck, enabled, "Automatic update check") {
        putBoolean(KEY_AUTO_UPDATE_CHECK, enabled)
    }

    fun setShowAndroidApps(show: Boolean) = update(_showAndroidApps, show, "Show Android apps") {
        putBoolean(KEY_SHOW_ANDROID_APPS, show)
    }

    fun setSetupDone(done: Boolean) = update(_setupDone, done, "Setup done") {
        putBoolean(KEY_SETUP_DONE, done)
    }

    private inline fun <T> update(
        flow: MutableStateFlow<T>,
        value: T,
        name: String,
        write: SharedPreferences.Editor.() -> Unit,
    ) {
        if (flow.value == value) return
        prefs.edit().apply(write).apply()
        flow.value = value
        MesOSLog.i(MesOSLog.SYSTEM, "$name set to $value")
    }

    companion object {
        private const val FILE_NAME = "mesos_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT = "accent"
        private const val KEY_WALLPAPER = "wallpaper"
        private const val KEY_ICON_SHAPE = "icon_shape"
        private const val KEY_THEMED_ICONS = "themed_icons"
        private const val KEY_BADGES = "notification_badges"
        private const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
        private const val KEY_SHOW_ANDROID_APPS = "show_android_apps"
        private const val KEY_SETUP_DONE = "setup_done"

        @Volatile
        private var instance: MesOSPreferences? = null

        fun get(context: Context): MesOSPreferences =
            instance ?: synchronized(this) {
                instance ?: MesOSPreferences(context.applicationContext).also { instance = it }
            }
    }
}
