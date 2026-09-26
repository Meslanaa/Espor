package org.mesos.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import org.mesos.core.MesOSIntents

/**
 * Chooses which installed apps appear on the home screen and in the dock.
 *
 * Nothing is hard-coded to a specific vendor app: each slot is a role (dialer, browser,
 * camera, …) resolved to whatever app Android reports for that role. Roles with no
 * installed app are simply left out.
 */
internal object Pins {

    private enum class Role {
        PHONE, MESSAGES, BROWSER, MESOS_SETTINGS,
        FILES, PLAY_STORE, CAMERA, GALLERY, CLOCK, CALENDAR, CONTACTS, EMAIL,
    }

    private val dockRoles = listOf(Role.PHONE, Role.MESSAGES, Role.BROWSER, Role.MESOS_SETTINGS)

    private val homeRoles = listOf(
        Role.FILES, Role.PLAY_STORE, Role.CAMERA, Role.GALLERY,
        Role.CLOCK, Role.CALENDAR, Role.CONTACTS, Role.EMAIL,
    )

    private val filesPackages = listOf(
        "com.google.android.apps.nbu.files",
        "com.google.android.documentsui",
        "com.android.documentsui",
    )

    /** Returns (home screen pins, dock) as subsets of [apps] without duplicates. */
    fun resolve(context: Context, apps: List<AppEntry>): Pair<List<AppEntry>, List<AppEntry>> {
        val pm = context.packageManager
        val used = mutableSetOf<String>()

        fun pick(role: Role): AppEntry? {
            val entry = when (role) {
                Role.MESOS_SETTINGS -> findByComponentOf(pm, MesOSIntents.settings(context), apps)
                Role.PLAY_STORE -> apps.firstOrNull { it.packageName == "com.android.vending" }
                Role.FILES -> findByPackage(defaultPackage(pm, selector("android.intent.category.APP_FILES")), apps)
                    ?: filesPackages.firstNotNullOfOrNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
                else -> findByPackage(defaultPackage(pm, intentFor(role)), apps)
            } ?: return null
            return if (used.add(entry.key)) entry else null
        }

        val dock = dockRoles.mapNotNull(::pick)
        val home = homeRoles.mapNotNull(::pick)
        return home to dock
    }

    private fun intentFor(role: Role): Intent = when (role) {
        Role.PHONE -> Intent(Intent.ACTION_DIAL)
        Role.MESSAGES -> selector(Intent.CATEGORY_APP_MESSAGING)
        Role.BROWSER -> Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE)
        Role.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        Role.GALLERY -> selector(Intent.CATEGORY_APP_GALLERY)
        Role.CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
        Role.CALENDAR -> selector(Intent.CATEGORY_APP_CALENDAR)
        Role.CONTACTS -> selector(Intent.CATEGORY_APP_CONTACTS)
        Role.EMAIL -> selector(Intent.CATEGORY_APP_EMAIL)
        Role.MESOS_SETTINGS, Role.PLAY_STORE, Role.FILES -> error("$role is resolved separately")
    }

    private fun selector(category: String): Intent =
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

    /** The package Android would use for [intent]: the user's default, else the first match. */
    private fun defaultPackage(pm: PackageManager, intent: Intent): String? {
        val resolved = pm.resolveActivity(intent, 0)?.activityInfo?.packageName
        // "android" means the system chooser: no default app is set for this role.
        if (resolved != null && resolved != "android") return resolved
        return pm.queryIntentActivities(intent, 0).firstOrNull()?.activityInfo?.packageName
    }

    private fun findByPackage(packageName: String?, apps: List<AppEntry>): AppEntry? =
        packageName?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }

    private fun findByComponentOf(pm: PackageManager, intent: Intent, apps: List<AppEntry>): AppEntry? {
        val activity = pm.resolveActivity(intent, 0)?.activityInfo ?: return null
        return apps.firstOrNull {
            it.component.packageName == activity.packageName && it.component.className == activity.name
        }
    }
}
