package org.mesos.launcher

import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mesos.core.MesOSApps
import org.mesos.core.MesOSRelease
import org.mesos.core.log.MesOSLog
import org.mesos.core.prefs.MesOSPreferences
import org.mesos.core.prefs.Wallpaper
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.launcher.home.AppUsage
import org.mesos.launcher.home.HomeModel
import org.mesos.launcher.layout.HomeItem
import org.mesos.launcher.layout.WidgetKinds
import org.mesos.launcher.ui.HomeActions
import org.mesos.launcher.ui.HomeScreen
import org.mesos.launcher.ui.HomeUiState
import org.mesos.launcher.widgets.AndroidWidgets
import org.mesos.updater.UpdateCheckScheduler

/** MesOS Home: the HOME activity Android shows when the user presses Home. */
class HomeActivity : ComponentActivity(), HomeActions {

    private val now = MutableStateFlow(System.currentTimeMillis())
    private val ui = HomeUiState()
    private var timeReceiverRegistered = false
    private lateinit var repository: AppRepository
    private lateinit var homeModel: HomeModel
    private lateinit var usage: AppUsage
    private lateinit var widgets: AndroidWidgets
    private lateinit var preferences: MesOSPreferences

    /** An Android widget being added while Android asks for permission or configuration. */
    private var pending: PendingWidget? = null

    private data class PendingWidget(val id: Int, val provider: ComponentName, val page: Int, val w: Int, val h: Int)

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val widget = pending ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) configureOrPlace(widget) else cancelPending()
    }

    // Minute ticks and clock/time-zone changes; registered only while Home is visible.
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            now.value = System.currentTimeMillis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MesOSLog.i(MesOSLog.LAUNCHER, "MesOS Home started (${MesOSRelease.current.displayName})")
        repository = AppRepository.get(this)
        homeModel = HomeModel.get(this)
        usage = AppUsage.get(this)
        widgets = AndroidWidgets.get(this)
        preferences = MesOSPreferences.get(this)
        homeModel.start()
        restorePending(savedInstanceState)
        UpdateCheckScheduler.sync(this)

        lifecycleScope.launch {
            preferences.wallpaper.collect(::applyWindowWallpaper)
        }
        lifecycleScope.launch {
            // Free widget ids from adds that never finished (e.g. MesOS was closed meanwhile).
            val layout = homeModel.layout.filterNotNull().first()
            val inUse = layout.items.mapNotNull { (it.item as? HomeItem.Widget)?.appWidgetId }.toMutableSet()
            pending?.let { inUse += it.id }
            widgets.deleteUnused(inUse)
        }

        setContent {
            MesOSUserTheme(forceDark = true) {
                val model by repository.model.collectAsState()
                val layout by homeModel.layout.collectAsState()
                val time by now.collectAsState()
                HomeScreen(ui = ui, model = model, layout = layout, nowMillis = time, actions = this)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timeReceiver, filter)
        }
        timeReceiverRegistered = true
        now.value = System.currentTimeMillis()

        if (!preferences.setupDone.value) {
            startActivitySafely(MesOSApps.launchIntent(this, MesOSApps.SETUP))
        }
    }

    override fun onStop() {
        if (timeReceiverRegistered) {
            unregisterReceiver(timeReceiver)
            timeReceiverRegistered = false
        }
        widgets.stopListening()
        // Coming back to Home shows Home, not the drawer or a stale menu.
        ui.menu = null
        lifecycleScope.launch {
            if (ui.drawer.value > 0f) ui.drawer.snapTo(0f)
            if (ui.control.value > 0f) ui.control.snapTo(0f)
            ui.query = ""
            ui.focusSearch = false
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while Home is shown closes everything and returns to the first page.
        ui.homeRequests++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pending?.let {
            outState.putInt(STATE_PENDING_ID, it.id)
            outState.putString(STATE_PENDING_PROVIDER, it.provider.flattenToString())
            outState.putIntArray(STATE_PENDING_PLACE, intArrayOf(it.page, it.w, it.h))
        }
    }

    private fun restorePending(state: Bundle?) {
        state ?: return
        val id = state.getInt(STATE_PENDING_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val provider = state.getString(STATE_PENDING_PROVIDER)?.let(ComponentName::unflattenFromString)
        val place = state.getIntArray(STATE_PENDING_PLACE)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && provider != null && place != null && place.size == 3) {
            pending = PendingWidget(id, provider, place[0], place[1], place[2])
        }
    }

    /** Shows Android's wallpaper behind Home only when the user chose it. */
    private fun applyWindowWallpaper(wallpaper: Wallpaper) {
        if (wallpaper == Wallpaper.SYSTEM) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(NIGHT))
        }
    }

    // ---- HomeActions ----

    override fun launch(entry: AppEntry, bounds: Rect?) {
        val rect = bounds?.toAndroidRect()
        val options = rect?.let {
            ActivityOptions.makeScaleUpAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
        }
        if (repository.launch(entry, rect, options)) {
            usage.recordLaunch(entry.key)
        } else {
            Toast.makeText(this, getString(R.string.launcher_launch_failed, entry.label), Toast.LENGTH_SHORT).show()
        }
    }

    override fun launchShortcut(shortcut: ShortcutInfo, bounds: Rect?) {
        val rect = bounds?.toAndroidRect()
        val options = rect?.let {
            ActivityOptions.makeScaleUpAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
        }
        repository.startShortcut(shortcut, rect, options)
    }

    override fun open(intent: Intent) {
        if (!startActivitySafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            Toast.makeText(this, org.mesos.core.R.string.mesos_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun openAppInfo(entry: AppEntry, bounds: Rect?) = repository.openAppInfo(entry, bounds?.toAndroidRect())

    override fun uninstall(entry: AppEntry) {
        open(repository.uninstallIntent(entry))
    }

    override fun addAndroidWidget(info: AppWidgetProviderInfo, page: Int, w: Int, h: Int) {
        val id = widgets.allocate()
        val widget = PendingWidget(id, info.provider, page, w, h)
        pending = widget
        if (widgets.bindIfAllowed(id, info)) {
            configureOrPlace(widget)
        } else {
            try {
                bindLauncher.launch(widgets.bindIntent(id, info))
            } catch (e: RuntimeException) {
                MesOSLog.w(MesOSLog.LAUNCHER, "Widget bind dialog unavailable", e)
                cancelPending()
            }
        }
    }

    override fun deleteAndroidWidget(appWidgetId: Int) = widgets.delete(appWidgetId)

    override fun copy(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("MesOS", text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, org.mesos.core.R.string.mesos_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureOrPlace(widget: PendingWidget) {
        val info = widgets.info(widget.id)
        if (info == null) {
            cancelPending()
            return
        }
        val optional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info.configure != null && !optional && widgets.startConfigure(this, widget.id, REQUEST_CONFIGURE)) return
        place(widget)
    }

    private fun place(widget: PendingWidget) {
        pending = null
        val added = homeModel.update {
            it.addWidget(WidgetKinds.ANDROID, widget.w, widget.h, startPage = widget.page, appWidgetId = widget.id, provider = widget.provider.flattenToString())
        }
        if (!added) widgets.delete(widget.id)
    }

    private fun cancelPending() {
        pending?.let { widgets.delete(it.id) }
        pending = null
    }

    @Deprecated("Widget configuration activities report back through the legacy result API.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CONFIGURE) {
            val widget = pending ?: return
            if (resultCode == RESULT_OK) place(widget) else cancelPending()
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun Rect.toAndroidRect() = android.graphics.Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

    private companion object {
        const val REQUEST_CONFIGURE = 0x31
        const val STATE_PENDING_ID = "pending_widget_id"
        const val STATE_PENDING_PROVIDER = "pending_widget_provider"
        const val STATE_PENDING_PLACE = "pending_widget_place"
        val NIGHT = Color.rgb(0x05, 0x09, 0x14)
    }
}
