package org.mesos.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.mesos.core.MesOSApps

/**
 * Chooses the apps on the home screen and in the dock.
 *
 * MesOS apps are pinned by component. When Android apps are shown, Phone, Messages
 * and Browser are added by role (whatever app Android reports for that role).
 */
internal object Pins {

    private val dock = listOf(MesOSApps.CAMERA, MesOSApps.PHOTOS, MesOSApps.PLAY_STORE_PACKAGE, MesOSApps.SETTINGS)
    private val home = listOf(MesOSApps.FILES, MesOSApps.DOWNLOADS, MesOSApps.CALCULATOR, MesOSApps.NOTES)

    /** Returns (home screen pins, dock) as subsets of [apps] without duplicates. */
    fun resolve(context: Context, apps: List<AppEntry>, showAndroidApps: Boolean): Pair<List<AppEntry>, List<AppEntry>> {
        val used = mutableSetOf<String>()
        fun take(entry: AppEntry?): AppEntry? = entry?.takeIf { used.add(it.key) }

        fun pick(id: String): AppEntry? = take(
            if (id == MesOSApps.PLAY_STORE_PACKAGE) {
                apps.firstOrNull { it.packageName == id }
            } else {
                apps.firstOrNull { it.packageName == context.packageName && it.component.className == id }
            },
        )

        val dockEntries = dock.mapNotNull(::pick)
        val homeEntries = home.mapNotNull(::pick).toMutableList()
        if (showAndroidApps) {
            val pm = context.packageManager
            roleIntents().forEach { intent ->
                take(findByPackage(defaultPackage(pm, intent), apps))?.let(homeEntries::add)
            }
        }
        return homeEntries to dockEntries
    }

    private fun roleIntents(): List<Intent> = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE),
    )

    /** The package Android would use for [intent]: the user's default, else the first match. */
    private fun defaultPackage(pm: PackageManager, intent: Intent): String? {
        val resolved = pm.resolveActivity(intent, 0)?.activityInfo?.packageName
        // "android" means the system chooser: no default app is set for this role.
        if (resolved != null && resolved != "android") return resolved
        return pm.queryIntentActivities(intent, 0).firstOrNull()?.activityInfo?.packageName
    }

    private fun findByPackage(packageName: String?, apps: List<AppEntry>): AppEntry? =
        packageName?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
}
