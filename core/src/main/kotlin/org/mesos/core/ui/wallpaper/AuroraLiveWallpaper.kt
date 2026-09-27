package org.mesos.core.ui.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * The Aurora live wallpaper (implemented by the launcher module), for pages that
 * offer it as Android's wallpaper so it also shows on the lock screen.
 */
object AuroraLiveWallpaper {
    private const val SERVICE = "org.mesos.launcher.wallpaper.AuroraWallpaperService"

    fun component(context: Context): ComponentName = ComponentName(context.packageName, SERVICE)

    /** Android's own preview screen for setting Aurora; the user confirms there. */
    fun chooserIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))

    /** Whether Android's current wallpaper is MesOS Aurora. */
    fun isActive(context: Context): Boolean =
        try {
            WallpaperManager.getInstance(context).wallpaperInfo?.component == component(context)
        } catch (e: RuntimeException) {
            false
        }
}
