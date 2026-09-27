package org.mesos.launcher.home

import android.content.Context
import android.os.UserHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mesos.core.log.MesOSLog
import org.mesos.core.prefs.MesOSPreferences
import org.mesos.launcher.AppRepository
import org.mesos.launcher.AppVisibility
import org.mesos.launcher.LauncherModel
import org.mesos.launcher.PackageEvent
import org.mesos.launcher.layout.HomeItem
import org.mesos.launcher.layout.HomeLayout
import org.mesos.launcher.layout.HomeLayoutJson
import org.mesos.launcher.layout.WidgetKinds
import java.io.File
import java.io.IOException

/**
 * The home screen layout and the rules that keep it in sync with installed apps.
 *
 * The layout is loaded once (or built from [DefaultLayout] on first run), changed
 * only through [update] on the main thread, and saved atomically after each change.
 */
class HomeModel private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val repository = AppRepository.get(appContext)
    private val preferences = MesOSPreferences.get(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val file = File(appContext.filesDir, FILE_NAME)
    private val saveMutex = Mutex()
    private var started = false

    private val _layout = MutableStateFlow<HomeLayout?>(null)

    /** Null until loaded. */
    val layout: StateFlow<HomeLayout?> = _layout.asStateFlow()

    fun start() {
        if (started) return
        started = true
        repository.start()
        scope.launch {
            val stored = withContext(Dispatchers.IO) { read() }
            val apps = repository.model.first { it.loaded }
            val initial = stored?.let { cleanUp(it, apps) } ?: DefaultLayout.build(appContext, apps).also {
                MesOSLog.i(MesOSLog.LAUNCHER, "Created the default home layout")
            }
            _layout.value = initial
            save(initial)
            repository.events.collect(::onPackageEvent)
        }
    }

    /** Applies [change] to the current layout; a null result keeps the layout unchanged. */
    fun update(change: (HomeLayout) -> HomeLayout?): Boolean {
        val current = _layout.value ?: return false
        val next = change(current) ?: return false
        if (next == current) return true
        _layout.value = next
        scope.launch { save(next) }
        return true
    }

    private fun onPackageEvent(event: PackageEvent) {
        when (event) {
            is PackageEvent.Removed -> update { layout ->
                layout.prune { key -> AppRepository.packageOf(key) != event.packageName || !matchesUser(key, event.user) }
            }
            is PackageEvent.Installed -> scope.launch {
                // Wait for the reload that includes the new app, then put it on Home.
                val model = repository.model.first { model -> model.apps.any { it.packageName == event.packageName } }
                addNewApp(event.packageName, event.user, model)
            }
        }
    }

    private fun addNewApp(packageName: String, user: UserHandle, model: LauncherModel) {
        val entries = model.apps.filter { it.packageName == packageName && it.user == user }
        if (entries.isEmpty()) return
        update { layout ->
            val present = layout.appKeys()
            entries.filter { it.key !in present }
                .fold(layout) { acc, entry -> acc.addApp(entry.key) }
        }
    }

    /** Drops apps that were uninstalled while MesOS was not running and widgets MesOS cannot show. */
    private fun cleanUp(layout: HomeLayout, model: LauncherModel): HomeLayout =
        layout
            .prune { key -> model.byKey.containsKey(key) || stillInstalled(key) }
            .pruneWidgets { it.kind == WidgetKinds.ANDROID || it.kind in WidgetKinds.mesos }

    private fun stillInstalled(key: String): Boolean {
        val pkg = AppRepository.packageOf(key)
        return repository.isInstalled(pkg, android.os.Process.myUserHandle())
    }

    private fun matchesUser(key: String, user: UserHandle): Boolean =
        key.substringAfterLast('#', "") == user.hashCode().toString()

    private fun read(): HomeLayout? =
        try {
            if (file.exists()) HomeLayoutJson.decode(file.readText()) else null
        } catch (e: IOException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Could not read the home layout", e)
            null
        }

    private suspend fun save(layout: HomeLayout) {
        val text = HomeLayoutJson.encode(layout)
        withContext(Dispatchers.IO) {
            saveMutex.withLock {
                try {
                    val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                    tmp.writeText(text)
                    if (!tmp.renameTo(file)) {
                        file.writeText(text)
                        tmp.delete()
                    }
                } catch (e: IOException) {
                    MesOSLog.w(MesOSLog.LAUNCHER, "Could not save the home layout", e)
                }
            }
        }
    }

    /** Whether [entryPackage] may be placed on Home under the current visibility rules. */
    fun isVisible(entryPackage: String, isSystemApp: Boolean): Boolean =
        AppVisibility.isVisible(entryPackage, isSystemApp, appContext.packageName, preferences.showAndroidApps.value)

    companion object {
        private const val FILE_NAME = "home_layout.json"

        @Volatile
        private var instance: HomeModel? = null

        fun get(context: Context): HomeModel =
            instance ?: synchronized(this) {
                instance ?: HomeModel(context).also { instance = it }
            }
    }
}
