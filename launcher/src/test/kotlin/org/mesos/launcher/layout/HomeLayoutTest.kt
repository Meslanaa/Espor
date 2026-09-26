package org.mesos.launcher.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    private fun apps(vararg keys: String): HomeLayout =
        keys.fold(HomeLayout()) { layout, key -> layout.addApp(key) }

    private fun HomeLayout.idOf(app: String): Long =
        (items.map { it.item } + dock).first { it is HomeItem.App && it.app == app }.id

    @Test
    fun appsFillRowsLeftToRight() {
        val layout = apps("a", "b", "c", "d", "e")
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 0 to 1), layout.items.map { it.x to it.y })
        assertEquals(1, layout.pageCount)
    }

    @Test
    fun fullPageSpillsToNewPage() {
        val keys = (1..25).map { "app$it" }.toTypedArray()
        val layout = apps(*keys)
        assertEquals(2, layout.pageCount)
        val last = layout.items.last()
        assertEquals(1, last.page)
        assertEquals(0 to 0, last.x to last.y)
    }

    @Test
    fun widgetNeedsFreeArea() {
        var layout = HomeLayout().addWidget(WidgetKinds.CLOCK, 4, 2)!!
        layout = layout.addApp("a")
        // The app goes below the clock, not under it.
        assertEquals(0 to 2, layout.items.last().let { it.x to it.y })
        assertNull(layout.moveTo(layout.idOf("a"), 0, 1, 1))
    }

    @Test
    fun moveToFreeCell() {
        val layout = apps("a", "b")
        val moved = layout.moveTo(layout.idOf("a"), 0, 3, 5)!!
        val placement = moved.placement(layout.idOf("a"))!!
        assertEquals(Triple(0, 3, 5), Triple(placement.page, placement.x, placement.y))
    }

    @Test
    fun movingOntoItselfKeepsPlace() {
        val layout = apps("a")
        val moved = layout.moveTo(layout.idOf("a"), 0, 0, 0)
        assertEquals(layout.items, moved!!.items)
    }

    @Test
    fun appOnAppCreatesFolder() {
        val layout = apps("a", "b")
        val result = layout.moveTo(layout.idOf("a"), 0, 1, 0)!!
        assertEquals(1, result.items.size)
        val folder = result.items.single().item as HomeItem.Folder
        assertEquals(listOf("b", "a"), folder.apps)
        assertEquals(1 to 0, result.items.single().let { it.x to it.y })
    }

    @Test
    fun appOnFolderJoinsFolder() {
        var layout = apps("a", "b", "c")
        layout = layout.moveTo(layout.idOf("a"), 0, 1, 0)!!
        val result = layout.moveTo(layout.idOf("c"), 0, 1, 0)!!
        val folder = result.items.single().item as HomeItem.Folder
        assertEquals(listOf("b", "a", "c"), folder.apps)
    }

    @Test
    fun folderCannotBeDroppedOnApp() {
        var layout = apps("a", "b", "c")
        layout = layout.moveTo(layout.idOf("a"), 0, 1, 0)!!
        val folderId = layout.items.first { it.item is HomeItem.Folder }.item.id
        assertNull(layout.moveTo(folderId, 0, 2, 0))
    }

    @Test
    fun dockTakesAppsNotWidgets() {
        var layout = apps("a").addWidget(WidgetKinds.NOTES, 2, 2)!!
        val widgetId = layout.items.first { it.item is HomeItem.Widget }.item.id
        assertNull(layout.moveToDock(widgetId, 0))
        layout = layout.moveToDock(layout.idOf("a"), 0)!!
        assertEquals(listOf("a"), layout.dock.map { (it as HomeItem.App).app })
        assertTrue(layout.items.none { it.item is HomeItem.App })
    }

    @Test
    fun dockIsLimited() {
        var layout = HomeLayout()
        repeat(HomeLayout.MAX_DOCK + 1) { layout = layout.addApp("app$it") }
        repeat(HomeLayout.MAX_DOCK) { layout = layout.moveToDock(layout.idOf("app$it"), it)!! }
        assertNull(layout.moveToDock(layout.idOf("app${HomeLayout.MAX_DOCK}"), 0))
    }

    @Test
    fun dockAppMovesBackToPage() {
        var layout = apps("a")
        layout = layout.moveToDock(layout.idOf("a"), 0)!!
        layout = layout.moveTo(layout.idOf("a"), 0, 2, 3)!!
        assertTrue(layout.dock.isEmpty())
        assertEquals(2 to 3, layout.placement(layout.idOf("a"))!!.let { it.x to it.y })
    }

    @Test
    fun dockAppOnPageAppCreatesFolder() {
        var layout = apps("a", "b")
        layout = layout.moveToDock(layout.idOf("a"), 0)!!
        val bPlace = layout.placement(layout.idOf("b"))!!
        layout = layout.moveTo(layout.idOf("a"), 0, bPlace.x, bPlace.y)!!
        assertTrue(layout.dock.isEmpty())
        assertEquals(listOf("b", "a"), (layout.items.single().item as HomeItem.Folder).apps)
    }

    @Test
    fun movingToNewPageGrowsPages() {
        val layout = apps("a")
        val moved = layout.moveTo(layout.idOf("a"), 1, 0, 0)!!
        assertEquals(2, moved.pageCount)
    }

    @Test
    fun compactRemovesEmptyPages() {
        var layout = apps("a", "b")
        layout = layout.moveTo(layout.idOf("b"), 3, 0, 0)!!
        assertEquals(4, layout.pageCount)
        val compact = layout.compactPages()
        assertEquals(2, compact.pageCount)
        assertEquals(1, compact.placement(layout.idOf("b"))!!.page)
    }

    @Test
    fun compactKeepsFirstPageEvenWhenEmpty() {
        var layout = apps("a")
        layout = layout.moveTo(layout.idOf("a"), 1, 0, 0)!!
        val compact = layout.compactPages()
        assertEquals(2, compact.pageCount)
        assertEquals(1, compact.items.single().page)
    }

    @Test
    fun pruneRemovesUninstalledAppsAndCollapsesFolders() {
        var layout = apps("a", "b", "c")
        layout = layout.moveTo(layout.idOf("a"), 0, 1, 0)!! // folder [b, a]
        layout = layout.moveToDock(layout.idOf("c"), 0)!!
        val pruned = layout.prune { it != "a" && it != "c" }
        assertTrue(pruned.dock.isEmpty())
        val remaining = pruned.items.single().item
        assertTrue(remaining is HomeItem.App && remaining.app == "b")
    }

    @Test
    fun removeFromFolderPlacesAppOnPage() {
        var layout = apps("a", "b", "c")
        layout = layout.moveTo(layout.idOf("a"), 0, 1, 0)!!
        layout = layout.moveTo(layout.idOf("c"), 0, 1, 0)!! // folder [b, a, c]
        val folderId = layout.items.first { it.item is HomeItem.Folder }.item.id
        val result = layout.removeFromFolder(folderId, "a", 0)
        val folder = result.items.first { it.item is HomeItem.Folder }.item as HomeItem.Folder
        assertEquals(listOf("b", "c"), folder.apps)
        assertTrue(result.items.any { val item = it.item; item is HomeItem.App && item.app == "a" })
    }

    @Test
    fun removingLastButOneFromFolderLeavesApp() {
        var layout = apps("a", "b")
        layout = layout.moveTo(layout.idOf("a"), 0, 1, 0)!!
        val folderId = layout.items.single().item.id
        val result = layout.removeFromFolder(folderId, "a", 0)
        assertEquals(setOf("a", "b"), result.appKeys())
        assertTrue(result.items.all { it.item is HomeItem.App })
    }

    @Test
    fun renameFolderTrims() {
        var layout = apps("a", "b")
        layout = layout.moveTo(layout.idOf("a"), 0, 1, 0)!!
        val folderId = layout.items.single().item.id
        val renamed = layout.renameFolder(folderId, "  Oyunlar ")
        assertEquals("Oyunlar", (renamed.items.single().item as HomeItem.Folder).name)
    }

    @Test
    fun idsStayUnique() {
        var layout = apps("a", "b", "c", "d")
        layout = layout.moveTo(layout.idOf("a"), 0, 1, 0)!!
        layout = layout.addWidget(WidgetKinds.CLOCK, 4, 2)!!
        val ids = layout.items.map { it.item.id } + layout.dock.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun jsonRoundTrip() {
        var layout = apps("a", "b", "c").addWidget(WidgetKinds.CLOCK, 4, 2)!!
        layout = layout.moveTo(layout.idOf("a"), 0, 1, 0)!!
        layout = layout.moveToDock(layout.idOf("c"), 0)!!
        layout = layout.addWidget(WidgetKinds.ANDROID, 2, 1, appWidgetId = 42, provider = "com.example/.Widget")!!
        val decoded = HomeLayoutJson.decode(HomeLayoutJson.encode(layout))
        assertEquals(layout, decoded)
    }

    @Test
    fun jsonSkipsBrokenAndOverlappingItems() {
        val text = """
            {"v":1,"columns":4,"rows":6,"pages":1,"nextId":3,
             "items":[
               {"id":1,"type":"app","app":"a","page":0,"x":0,"y":0,"w":1,"h":1},
               {"id":2,"type":"app","app":"b","page":0,"x":0,"y":0,"w":1,"h":1},
               {"id":3,"type":"mystery","page":0,"x":1,"y":0},
               {"id":4,"type":"app","app":"c","page":5,"x":0,"y":0},
               {"id":5,"type":"widget","kind":"mesos.clock","page":0,"x":2,"y":0,"w":4,"h":2}
             ],
             "dock":[{"id":1,"type":"app","app":"dup"},{"id":6,"type":"app","app":"d"}]}
        """.trimIndent()
        val layout = HomeLayoutJson.decode(text)
        assertNotNull(layout)
        assertEquals(listOf(1L), layout!!.items.map { it.item.id })
        assertEquals(listOf(6L), layout.dock.map { it.id })
        assertEquals(7L, layout.nextId)
    }

    @Test
    fun jsonRejectsGarbage() {
        assertNull(HomeLayoutJson.decode("not json"))
        assertNull(HomeLayoutJson.decode("""{"v":99}"""))
    }
}
