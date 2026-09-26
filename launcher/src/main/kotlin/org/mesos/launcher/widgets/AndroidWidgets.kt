package org.mesos.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.SizeF
import org.mesos.core.log.MesOSLog
import kotlin.math.ceil

/**
 * Hosts Android app widgets (from Google Play apps and others) on MesOS Home.
 * Binding a new widget goes through Android's own permission dialog when needed.
 */
internal class AndroidWidgets private constructor(context: Context) {

    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)
    private var listening = false

    fun startListening() {
        if (listening) return
        try {
            host.startListening()
            listening = true
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Widget host could not start", e)
        }
    }

    fun stopListening() {
        if (!listening) return
        try {
            host.stopListening()
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Widget host could not stop", e)
        }
        listening = false
    }

    /** Widgets offered by installed apps, for every profile, sorted by app and label. */
    fun providers(): List<AppWidgetProviderInfo> {
        val users = appContext.getSystemService(UserManager::class.java)?.userProfiles.orEmpty()
        val all = users.flatMap { user ->
            try {
                manager.getInstalledProvidersForProfile(user)
            } catch (e: RuntimeException) {
                emptyList()
            }
        }
        val pm = appContext.packageManager
        return all.sortedWith(compareBy({ it.provider.packageName }, { it.loadLabel(pm) }))
    }

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        try {
            manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        } catch (e: RuntimeException) {
            false
        }

    /** Android's "allow MesOS to create widgets" dialog for [info]. */
    fun bindIntent(id: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)

    fun info(id: Int): AppWidgetProviderInfo? =
        try {
            manager.getAppWidgetInfo(id)
        } catch (e: RuntimeException) {
            null
        }

    fun delete(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Could not delete widget $id", e)
        }
    }

    /** Frees widget ids MesOS allocated but no longer shows (e.g. after a cancelled add). */
    fun deleteUnused(inUse: Set<Int>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ids = try {
            host.appWidgetIds
        } catch (e: RuntimeException) {
            return
        }
        ids.filter { it !in inUse }.forEach(::delete)
    }

    fun startConfigure(activity: android.app.Activity, id: Int, requestCode: Int): Boolean =
        try {
            host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
            true
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Widget configuration failed for $id", e)
            false
        }

    fun createView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        host.createView(context, id, info)

    companion object {
        private const val HOST_ID = 0x4D65

        @Volatile
        private var instance: AndroidWidgets? = null

        fun get(context: Context): AndroidWidgets =
            instance ?: synchronized(this) {
                instance ?: AndroidWidgets(context).also { instance = it }
            }

        /** Grid cells a widget wants, for cells of [cellWidthDp] × [cellHeightDp]. */
        fun span(info: AppWidgetProviderInfo, density: Float, cellWidthDp: Float, cellHeightDp: Float, columns: Int, rows: Int): Pair<Int, Int> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
                return info.targetCellWidth.coerceIn(1, columns) to info.targetCellHeight.coerceIn(1, rows)
            }
            val w = ceil((info.minWidth / density) / cellWidthDp).toInt().coerceIn(1, columns)
            val h = ceil((info.minHeight / density) / cellHeightDp).toInt().coerceIn(1, rows)
            return w to h
        }

        /** Tells the widget how big it is drawn, so it can pick a matching layout. */
        fun updateSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
                } else {
                    @Suppress("DEPRECATION")
                    view.updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
                }
            } catch (e: RuntimeException) {
                MesOSLog.w(MesOSLog.LAUNCHER, "Widget size update failed", e)
            }
        }
    }
}
