package org.mesos.launcher.layout

/*
 * MesOS Home layout: pages of a fixed grid plus the dock. Pure Kotlin (no Android
 * types) so every rule below is covered by JVM unit tests.
 *
 * Every operation returns a new layout, or null when the change is not possible
 * (for example a widget dropped where it does not fit); the caller then keeps the
 * old layout, so a failed drop never loses an item.
 */

/** Something on the home screen or in the dock. */
sealed interface HomeItem {
    val id: Long

    /** An app shortcut. [app] is the launcher key of the activity (component + user). */
    data class App(override val id: Long, val app: String) : HomeItem

    /** A folder of app keys, in order. */
    data class Folder(override val id: Long, val name: String, val apps: List<String>) : HomeItem

    /**
     * A widget. [kind] is a MesOS widget kind (see [WidgetKinds]) or [WidgetKinds.ANDROID]
     * for an Android app widget, which also has an [appWidgetId] and [provider].
     */
    data class Widget(
        override val id: Long,
        val kind: String,
        val appWidgetId: Int = NO_APP_WIDGET,
        val provider: String? = null,
    ) : HomeItem

    companion object {
        const val NO_APP_WIDGET = -1
    }
}

/** MesOS widget kinds and their default sizes. */
object WidgetKinds {
    const val ANDROID = "android"
    const val CLOCK = "mesos.clock"
    const val WEATHER = "mesos.weather"
    const val AGENDA = "mesos.agenda"
    const val NOTES = "mesos.notes"
    const val MUSIC = "mesos.music"
    const val BATTERY = "mesos.battery"

    val mesos: Map<String, Pair<Int, Int>> = linkedMapOf(
        CLOCK to (4 to 2),
        WEATHER to (2 to 2),
        AGENDA to (2 to 2),
        NOTES to (2 to 2),
        MUSIC to (4 to 2),
        BATTERY to (2 to 2),
    )
}

/** Where an item sits: page and top-left cell, and how many cells it covers. */
data class Placement(
    val item: HomeItem,
    val page: Int,
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
) {
    fun covers(cx: Int, cy: Int): Boolean = cx in x until x + w && cy in y until y + h

    fun overlaps(page: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
        this.page == page && x < this.x + this.w && this.x < x + w && y < this.y + this.h && this.y < y + h
}

data class HomeLayout(
    val columns: Int = DEFAULT_COLUMNS,
    val rows: Int = DEFAULT_ROWS,
    val pageCount: Int = 1,
    val items: List<Placement> = emptyList(),
    val dock: List<HomeItem> = emptyList(),
    val nextId: Long = 1,
) {

    fun placement(id: Long): Placement? = items.firstOrNull { it.item.id == id }

    fun onPage(page: Int): List<Placement> = items.filter { it.page == page }

    fun itemAt(page: Int, x: Int, y: Int): Placement? = items.firstOrNull { it.page == page && it.covers(x, y) }

    /** Every app key on the home screen, in the dock or in folders. */
    fun appKeys(): Set<String> = buildSet {
        (items.map { it.item } + dock).forEach { item ->
            when (item) {
                is HomeItem.App -> add(item.app)
                is HomeItem.Folder -> addAll(item.apps)
                is HomeItem.Widget -> Unit
            }
        }
    }

    fun fits(page: Int, x: Int, y: Int, w: Int, h: Int, ignoreId: Long? = null): Boolean {
        if (page < 0 || x < 0 || y < 0 || x + w > columns || y + h > rows) return false
        return items.none { it.item.id != ignoreId && it.overlaps(page, x, y, w, h) }
    }

    /** First free top-left cell for a [w] × [h] item on [page], scanning row by row. */
    fun findFree(page: Int, w: Int, h: Int): Pair<Int, Int>? {
        for (y in 0..rows - h) {
            for (x in 0..columns - w) {
                if (fits(page, x, y, w, h)) return x to y
            }
        }
        return null
    }

    /** Places [item] in the first free space from [startPage] on, adding a page if needed. */
    fun add(item: HomeItem, w: Int = 1, h: Int = 1, startPage: Int = 0): HomeLayout? {
        if (w > columns || h > rows) return null
        for (page in startPage.coerceAtLeast(0) until pageCount) {
            val free = findFree(page, w, h) ?: continue
            return copy(items = items + Placement(item, page, free.first, free.second, w, h))
        }
        return copy(
            pageCount = pageCount + 1,
            items = items + Placement(item, pageCount, 0, 0, w, h),
        )
    }

    fun addApp(app: String, startPage: Int = 0): HomeLayout =
        withNewId { id -> add(HomeItem.App(id, app), startPage = startPage) } ?: this

    fun addWidget(kind: String, w: Int, h: Int, startPage: Int = 0, appWidgetId: Int = HomeItem.NO_APP_WIDGET, provider: String? = null): HomeLayout? =
        withNewId { id -> add(HomeItem.Widget(id, kind, appWidgetId, provider), w, h, startPage) }

    /**
     * Drops item [id] (from the pages or the dock) on cell ([x], [y]) of [page]:
     * - a free area: the item moves there;
     * - an app onto another app: both become a new folder;
     * - an app onto a folder: the app joins the folder.
     * Anything else returns null.
     */
    fun moveTo(id: Long, page: Int, x: Int, y: Int): HomeLayout? {
        if (page < 0) return null
        val fromPage = placement(id)
        val item = fromPage?.item ?: dock.firstOrNull { it.id == id } ?: return null
        val w = fromPage?.w ?: 1
        val h = fromPage?.h ?: 1
        val grown = if (page >= pageCount) copy(pageCount = page + 1) else this

        if (grown.fits(page, x, y, w, h, ignoreId = id)) {
            val placed = Placement(item, page, x, y, w, h)
            return grown.copy(
                items = grown.items.filter { it.item.id != id } + placed,
                dock = grown.dock.filter { it.id != id },
            )
        }

        val target = grown.itemAt(page, x, y) ?: return null
        if (target.item.id == id || item !is HomeItem.App) return null
        return when (val targetItem = target.item) {
            is HomeItem.App -> grown.withoutItem(id).withNewId { folderId ->
                val folder = HomeItem.Folder(folderId, "", listOf(targetItem.app, item.app))
                replaceItem(targetItem.id, folder)
            }
            is HomeItem.Folder -> grown.withoutItem(id)
                .replaceItem(targetItem.id, targetItem.copy(apps = targetItem.apps + item.app))
            is HomeItem.Widget -> null
        }
    }

    /** Moves item [id] into the dock at [index]. Only apps and folders fit in the dock. */
    fun moveToDock(id: Long, index: Int): HomeLayout? {
        val item = placement(id)?.item ?: dock.firstOrNull { it.id == id } ?: return null
        if (item is HomeItem.Widget) return null
        val remaining = dock.filter { it.id != id }
        if (remaining.size >= MAX_DOCK) return null
        val newDock = remaining.toMutableList().apply { add(index.coerceIn(0, size), item) }
        return copy(items = items.filter { it.item.id != id }, dock = newDock)
    }

    /** Removes item [id] from the pages or the dock. */
    fun remove(id: Long): HomeLayout = withoutItem(id)

    fun renameFolder(id: Long, name: String): HomeLayout {
        val folder = findItem(id) as? HomeItem.Folder ?: return this
        return replaceItem(id, folder.copy(name = name.trim()))
    }

    /** Takes [app] out of folder [folderId] and puts it on the first free cell from [page] on. */
    fun removeFromFolder(folderId: Long, app: String, page: Int): HomeLayout {
        val folder = findItem(folderId) as? HomeItem.Folder ?: return this
        if (app !in folder.apps) return this
        val shrunk = replaceItem(folderId, folder.copy(apps = folder.apps - app)).normalizeFolders()
        return shrunk.addApp(app, startPage = page)
    }

    /**
     * Removes apps that no longer exist ([isInstalled] false) from pages, dock and
     * folders. Folders left with one app become that app; empty folders go away.
     */
    fun prune(isInstalled: (String) -> Boolean): HomeLayout {
        fun keep(item: HomeItem): HomeItem? = when (item) {
            is HomeItem.App -> item.takeIf { isInstalled(item.app) }
            is HomeItem.Folder -> item.copy(apps = item.apps.filter(isInstalled))
            is HomeItem.Widget -> item
        }
        return copy(
            items = items.mapNotNull { placement -> keep(placement.item)?.let { placement.copy(item = it) } },
            dock = dock.mapNotNull(::keep),
        ).normalizeFolders()
    }

    /** Keeps widgets only when [isValid] accepts them (unknown kinds, unbound Android widgets). */
    fun pruneWidgets(isValid: (HomeItem.Widget) -> Boolean): HomeLayout =
        copy(items = items.filter { val item = it.item; item !is HomeItem.Widget || isValid(item) })

    /** Removes empty pages after the first and renumbers the rest. */
    fun compactPages(): HomeLayout {
        val used = items.map { it.page }.toSortedSet()
        val keep = (listOf(0) + used.filter { it > 0 }).distinct()
        val renumber = keep.withIndex().associate { (index, page) -> page to index }
        return copy(
            pageCount = keep.size,
            items = items.map { it.copy(page = renumber.getValue(it.page)) },
        )
    }

    private fun findItem(id: Long): HomeItem? = placement(id)?.item ?: dock.firstOrNull { it.id == id }

    private fun withoutItem(id: Long): HomeLayout =
        copy(items = items.filter { it.item.id != id }, dock = dock.filter { it.id != id })

    private fun replaceItem(id: Long, item: HomeItem): HomeLayout = copy(
        items = items.map { if (it.item.id == id) it.copy(item = item) else it },
        dock = dock.map { if (it.id == id) item else it },
    )

    /** Folders with a single app become that app; empty folders are removed. */
    private fun normalizeFolders(): HomeLayout {
        var next = nextId
        fun normalize(item: HomeItem): HomeItem? =
            if (item is HomeItem.Folder && item.apps.size <= 1) {
                item.apps.firstOrNull()?.let { HomeItem.App(next++, it) }
            } else {
                item
            }
        val newItems = items.mapNotNull { placement -> normalize(placement.item)?.let { placement.copy(item = it) } }
        val newDock = dock.mapNotNull(::normalize)
        return copy(items = newItems, dock = newDock, nextId = next)
    }

    private inline fun withNewId(block: HomeLayout.(Long) -> HomeLayout?): HomeLayout? =
        copy(nextId = nextId + 1).block(nextId)

    companion object {
        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_ROWS = 6
        const val MAX_DOCK = 5
    }
}
