package org.mesos.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.mesos.core.log.MesOSLog
import org.mesos.core.prefs.MesOSPreferences
import java.text.Collator
import java.util.Locale

/** A launchable activity as shown on the home screen and in the app drawer. */
data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** Preinstalled with the system image (including updated system apps). */
    val isSystemApp: Boolean,
)

/** Everything the home screen shows. */
data class LauncherModel(
    val allApps: List<AppEntry> = emptyList(),
    val pinned: List<AppEntry> = emptyList(),
    val dock: List<AppEntry> = emptyList(),
)

/**
 * Source of launchable apps, backed by Android's [LauncherApps] service.
 *
 * Apps are loaded once off the main thread and reloaded only when Android reports a
 * package change, so apps installed later appear automatically without polling.
 * Changing "Show Android apps" only re-filters the loaded list.
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

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload("added $packageName")
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload("removed $packageName")
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload("changed $packageName")
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            reload("available")
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            reload("unavailable")
    }

    /** Starts watching packages. Safe to call more than once. */
    fun start() {
        if (started) return
        started = true
        launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
        reload("start")
        scope.launch {
            // Skip the current value: reload() above already uses it.
            preferences.showAndroidApps.drop(1).collect { publish() }
        }
    }

    fun launch(entry: AppEntry): Boolean =
        try {
            launcherApps.startMainActivity(entry.component, entry.user, null, null)
            true
        } catch (e: RuntimeException) {
            // ActivityNotFoundException / SecurityException when an app vanished mid-tap.
            MesOSLog.w(MesOSLog.LAUNCHER, "Could not launch ${entry.component}", e)
            false
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

    /** Applies the visibility rules to the loaded apps and publishes the home model. */
    private fun publish() {
        scope.launch(Dispatchers.IO) {
            val showAndroidApps = preferences.showAndroidApps.value
            val visible = loadedApps.filter {
                AppVisibility.isVisible(it.packageName, it.isSystemApp, appContext.packageName, showAndroidApps)
            }
            val (pinned, dock) = Pins.resolve(appContext, visible, showAndroidApps)
            _model.value = LauncherModel(allApps = visible, pinned = pinned, dock = dock)
        }
    }

    private fun loadApps(): List<AppEntry> {
        val iconSizePx = (ICON_SIZE_DP * appContext.resources.displayMetrics.density).toInt()
        val collator = Collator.getInstance(Locale.getDefault())
        return launcherApps.profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .map { it.toEntry(iconSizePx) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun LauncherActivityInfo.toEntry(iconSizePx: Int): AppEntry =
        AppEntry(
            key = "${componentName.flattenToShortString()}#${user.hashCode()}",
            label = label.toString(),
            packageName = componentName.packageName,
            component = componentName,
            user = user,
            icon = getBadgedIcon(0).toImageBitmap(iconSizePx),
            isSystemApp = applicationInfo.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
        )

    companion object {
        private const val ICON_SIZE_DP = 56

        @Volatile
        private var instance: AppRepository? = null

        fun get(context: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(context).also { instance = it }
            }
    }
}

private fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}
