package org.mesos.launcher.home

import android.content.Context
import org.mesos.core.MesOSApps
import org.mesos.launcher.AppEntry
import org.mesos.launcher.LauncherModel
import org.mesos.launcher.layout.HomeItem
import org.mesos.launcher.layout.HomeLayout
import org.mesos.launcher.layout.Placement
import org.mesos.launcher.layout.WidgetKinds

/** The MesOS Home a new user starts with (matches the Aurora design). */
internal object DefaultLayout {

    private val dockApps = listOf(MesOSApps.PHONE, MesOSApps.MESSAGES, MesOSApps.BROWSER, MesOSApps.CAMERA)

    // Page 1 below the widgets: two rows of four.
    private val firstPage = listOf(
        MesOSApps.PHOTOS, MesOSApps.FILES, MesOSApps.NOTES, MesOSApps.CLOCK,
        MesOSApps.CALENDAR, MesOSApps.MUSIC, MesOSApps.PLAY_STORE_PACKAGE, MesOSApps.SETTINGS,
    )

    private val secondPage = listOf(
        MesOSApps.CONTACTS, MesOSApps.CALCULATOR, MesOSApps.DOWNLOADS, MesOSApps.WEATHER,
        MesOSApps.RECORDER, MesOSApps.SCANNER, MesOSApps.CARE, MesOSApps.TIPS,
    )

    fun build(context: Context, model: LauncherModel): HomeLayout {
        fun find(id: String): AppEntry? =
            if (id == MesOSApps.PLAY_STORE_PACKAGE) {
                model.apps.firstOrNull { it.packageName == id }
            } else {
                model.apps.firstOrNull { it.packageName == context.packageName && it.component.className == id }
            }

        var nextId = 1L
        val items = mutableListOf<Placement>()
        items += Placement(HomeItem.Widget(nextId++, WidgetKinds.CLOCK), page = 0, x = 0, y = 0, w = 4, h = 2)
        items += Placement(HomeItem.Widget(nextId++, WidgetKinds.WEATHER), page = 0, x = 0, y = 2, w = 2, h = 2)
        items += Placement(HomeItem.Widget(nextId++, WidgetKinds.AGENDA), page = 0, x = 2, y = 2, w = 2, h = 2)
        // Missing apps (e.g. no Play Store on the image) leave no gaps.
        firstPage.mapNotNull(::find).forEachIndexed { index, app ->
            items += Placement(HomeItem.App(nextId++, app.key), 0, index % 4, 4 + index / 4)
        }
        secondPage.mapNotNull(::find).forEachIndexed { index, app ->
            items += Placement(HomeItem.App(nextId++, app.key), 1, index % 4, index / 4)
        }
        val dock = dockApps.mapNotNull { id -> find(id)?.let { HomeItem.App(nextId++, it.key) } }

        // Apps the user installed before (e.g. from Google Play) are not lost.
        val placed = items.mapNotNull { (it.item as? HomeItem.App)?.app }.toSet() + dock.map { (it as HomeItem.App).app }
        var layout = HomeLayout(pageCount = 2, items = items, dock = dock, nextId = nextId)
        model.apps
            .filter { !it.isSystemApp && it.packageName != context.packageName && it.key !in placed }
            .forEach { layout = layout.addApp(it.key, startPage = 1) }
        return layout
    }
}
