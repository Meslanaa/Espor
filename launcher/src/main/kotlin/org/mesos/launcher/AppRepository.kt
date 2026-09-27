package org.mesos.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.mesos.core.log.MesOSLog
import org.mesos.core.prefs.MesOSPreferences
import java.text.Collator
import java.util.Locale

/** A launchable activity as shown on the home screen and in the app drawer. */
data class AppEntry(
    /** Stable key used by the home layout: component and user. */
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** Preinstalled with the system image (including updated system apps). */
    val isSystemApp: Boolean,
)

/** Everything the launcher knows about apps. */
data class LauncherModel(
    /** Apps MesOS shows (see [AppVisibility]), sorted by label. */
    val apps: List<AppEntry> = emptyList(),
    /** Every launchable app by key, including hidden Android apps. */
    val byKey: Map<String, AppEntry> = emptyMap(),
    val loaded: Boolean = false,
)

/** A package change the home layout reacts to. */
sealed interface PackageEvent {
    data class Installed(val packageName: String, val user: UserHandle) : PackageEvent
    data class Removed(val packageName: String, val user: UserHandle) : PackageEvent
}

/**
 * Source of launchable apps, backed by Android's [LauncherApps] service.
 *
 * Apps load once off the main thread and reload when Android reports a package
 * change or when the icon style changes; "Show Android apps" only re-filters.
 */
class AppRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = MesOSPreferences.get(appContext)
    private val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null
    private var started = false

    @Volatile
    private var loadedApps: List<AppEntry> = emptyList()

    private val _model = MutableStateFlow(LauncherModel())
    val model: StateFlow<LauncherModel> = _model.asStateFlow()

    private val _events = MutableSharedFlow<PackageEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PackageEvent> = _events.asSharedFlow()

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            reload("added $packageName")
            _events.tryEmit(PackageEvent.Installed(packageName, user))
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            reload("removed $packageName")
            _events.tryEmit(PackageEvent.Removed(packageName, user))
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) = reload("changed $packageName")
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            reload("available")
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            reload("unavailable")
    }

    /** Starts watching packages and preferences. Safe to call more than once. */
    fun start() {
        if (started) return
        started = true
        launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
        reload("start")
        scope.launch {
            // Skip the current value: reload() above already uses it.
            preferences.showAndroidApps.drop(1).collect { publish() }
        }
        scope.launch {
            combine(preferences.iconShape, preferences.themedIcons, preferences.accent) { _, _, _ -> }
                .drop(1)
                .collect { reload("icon style") }
        }
    }

    fun launch(entry: AppEntry, sourceBounds: Rect?, options: Bundle?): Boolean =
        try {
            launcherApps.startMainActivity(entry.component, entry.user, sourceBounds, options)
            true
        } catch (e: RuntimeException) {
            // ActivityNotFoundException / SecurityException when an app vanished mid-tap.
            MesOSLog.w(MesOSLog.LAUNCHER, "Could not launch ${entry.component}", e)
            false
        }

    fun openAppInfo(entry: AppEntry, sourceBounds: Rect?) {
        try {
            launcherApps.startAppDetailsActivity(entry.component, entry.user, sourceBounds, null)
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Could not open app info for ${entry.packageName}", e)
        }
    }

    /** Android's uninstall confirmation for [entry]'s package (the user confirms there). */
    fun uninstallIntent(entry: AppEntry): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null))
            .putExtra(Intent.EXTRA_USER, entry.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** App shortcuts (long-press actions) of [entry]; empty unless MesOS is the home app. */
    fun shortcuts(entry: AppEntry): List<ShortcutInfo> {
        if (!hasShortcutAccess()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(entry.packageName)
            .setActivity(entry.component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        return try {
            launcherApps.getShortcuts(query, entry.user).orEmpty()
                .filter { it.isEnabled }
                .sortedWith(compareBy<ShortcutInfo> { !it.isDynamic }.thenBy { it.rank })
                .take(MAX_SHORTCUTS)
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Could not load shortcuts for ${entry.packageName}", e)
            emptyList()
        }
    }

    fun shortcutIcon(shortcut: ShortcutInfo, sizePx: Int): ImageBitmap? =
        try {
            launcherApps.getShortcutIconDrawable(shortcut, appContext.resources.displayMetrics.densityDpi)
                ?.let { drawable -> iconRenderer().render(drawable, sizePx).asImageBitmap() }
        } catch (e: RuntimeException) {
            null
        }

    fun startShortcut(shortcut: ShortcutInfo, sourceBounds: Rect?, options: Bundle?) {
        try {
            launcherApps.startShortcut(shortcut, sourceBounds, options)
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Could not start shortcut ${shortcut.id}", e)
        }
    }

    private fun hasShortcutAccess(): Boolean =
        try {
            launcherApps.hasShortcutHostPermission()
        } catch (e: RuntimeException) {
            false
        }

    /** Whether [packageName] is still installed for [user] (false after uninstall). */
    fun isInstalled(packageName: String, user: UserHandle): Boolean =
        try {
            launcherApps.getApplicationInfo(packageName, 0, user)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: RuntimeException) {
            true
        }

    private fun reload(reason: String) {
        MesOSLog.d(MesOSLog.LAUNCHER, "Reloading apps ($reason)")
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) {
            loadedApps = loadApps()
            MesOSLog.i(MesOSLog.LAUNCHER, "Loaded ${loadedApps.size} apps")
            publish()
        }
    }

    /** Applies the visibility rules to the loaded apps and publishes the model. */
    private fun publish() {
        val all = loadedApps
        val showAndroidApps = preferences.showAndroidApps.value
        val visible = all.filter {
            AppVisibility.isVisible(it.packageName, it.isSystemApp, appContext.packageName, showAndroidApps)
        }
        _model.value = LauncherModel(apps = visible, byKey = all.associateBy { it.key }, loaded = true)
    }

    private fun iconRenderer() = IconRenderer(
        shape = preferences.iconShape.value,
        themed = preferences.themedIcons.value,
        accent = preferences.accent.value.base.toInt(),
    )

    private fun loadApps(): List<AppEntry> {
        val iconSizePx = (ICON_SIZE_DP * appContext.resources.displayMetrics.density).toInt()
        val renderer = iconRenderer()
        val collator = Collator.getInstance(Locale.getDefault())
        return launcherApps.profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .mapNotNull { info ->
                try {
                    info.toEntry(renderer, iconSizePx)
                } catch (e: RuntimeException) {
                    MesOSLog.w(MesOSLog.LAUNCHER, "Skipping ${info.componentName}", e)
                    null
                }
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun LauncherActivityInfo.toEntry(renderer: IconRenderer, iconSizePx: Int): AppEntry {
        val density = appContext.resources.displayMetrics.densityDpi
        var bitmap = renderer.render(getIcon(density), iconSizePx)
        if (user != Process.myUserHandle()) {
            // Work profile apps keep Android's briefcase badge.
            val badged = appContext.packageManager.getUserBadgedIcon(BitmapDrawable(appContext.resources, bitmap), user)
            bitmap = Bitmap.createBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888).also {
                badged.setBounds(0, 0, iconSizePx, iconSizePx)
                badged.draw(Canvas(it))
            }
        }
        return AppEntry(
            key = keyOf(componentName, user),
            label = label.toString(),
            packageName = componentName.packageName,
            component = componentName,
            user = user,
            icon = bitmap.asImageBitmap(),
            isSystemApp = applicationInfo.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
        )
    }

    companion object {
        const val ICON_SIZE_DP = 60
        private const val MAX_SHORTCUTS = 4

        fun keyOf(component: ComponentName, user: UserHandle): String =
            "${component.flattenToShortString()}#${user.hashCode()}"

        /** Package part of a home-layout key. */
        fun packageOf(key: String): String = key.substringBefore('/')

        @Volatile
        private var instance: AppRepository? = null

        fun get(context: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(context).also { instance = it }
            }
    }
}
