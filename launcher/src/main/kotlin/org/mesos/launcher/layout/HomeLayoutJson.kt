package org.mesos.launcher.layout

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Stores a [HomeLayout] as JSON. Reading is forgiving: unknown or broken entries are
 * skipped instead of throwing, so a damaged file never leaves Home empty.
 */
object HomeLayoutJson {

    private const val VERSION = 1

    fun encode(layout: HomeLayout): String = JSONObject().apply {
        put("v", VERSION)
        put("columns", layout.columns)
        put("rows", layout.rows)
        put("pages", layout.pageCount)
        put("nextId", layout.nextId)
        put("items", JSONArray().apply {
            layout.items.forEach { placement ->
                put(itemToJson(placement.item).apply {
                    put("page", placement.page)
                    put("x", placement.x)
                    put("y", placement.y)
                    put("w", placement.w)
                    put("h", placement.h)
                })
            }
        })
        put("dock", JSONArray().apply { layout.dock.forEach { put(itemToJson(it)) } })
    }.toString()

    /** Returns null when [text] is not a MesOS layout at all. */
    fun decode(text: String): HomeLayout? {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return null
        }
        if (root.optInt("v", -1) != VERSION) return null
        val columns = root.optInt("columns", HomeLayout.DEFAULT_COLUMNS).coerceIn(3, 6)
        val rows = root.optInt("rows", HomeLayout.DEFAULT_ROWS).coerceIn(4, 8)
        val pageCount = root.optInt("pages", 1).coerceIn(1, MAX_PAGES)

        val items = mutableListOf<Placement>()
        val itemsJson = root.optJSONArray("items") ?: JSONArray()
        for (i in 0 until itemsJson.length()) {
            val json = itemsJson.optJSONObject(i) ?: continue
            val item = itemFromJson(json) ?: continue
            val placement = Placement(
                item = item,
                page = json.optInt("page", -1),
                x = json.optInt("x", -1),
                y = json.optInt("y", -1),
                w = json.optInt("w", 1),
                h = json.optInt("h", 1),
            )
            val valid = placement.page in 0 until pageCount &&
                placement.x >= 0 && placement.y >= 0 && placement.w >= 1 && placement.h >= 1 &&
                placement.x + placement.w <= columns && placement.y + placement.h <= rows &&
                items.none { it.item.id == item.id || it.overlaps(placement.page, placement.x, placement.y, placement.w, placement.h) }
            if (valid) items += placement
        }

        val dock = mutableListOf<HomeItem>()
        val dockJson = root.optJSONArray("dock") ?: JSONArray()
        for (i in 0 until dockJson.length()) {
            val item = dockJson.optJSONObject(i)?.let(::itemFromJson) ?: continue
            if (item !is HomeItem.Widget && dock.size < HomeLayout.MAX_DOCK && items.none { it.item.id == item.id } && dock.none { it.id == item.id }) {
                dock += item
            }
        }

        val maxId = (items.map { it.item.id } + dock.map { it.id }).maxOrNull() ?: 0
        return HomeLayout(
            columns = columns,
            rows = rows,
            pageCount = pageCount,
            items = items,
            dock = dock,
            nextId = maxOf(root.optLong("nextId", 1), maxId + 1),
        )
    }

    private fun itemToJson(item: HomeItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        when (item) {
            is HomeItem.App -> {
                put("type", "app")
                put("app", item.app)
            }
            is HomeItem.Folder -> {
                put("type", "folder")
                put("name", item.name)
                put("apps", JSONArray(item.apps))
            }
            is HomeItem.Widget -> {
                put("type", "widget")
                put("kind", item.kind)
                put("appWidgetId", item.appWidgetId)
                item.provider?.let { put("provider", it) }
            }
        }
    }

    private fun itemFromJson(json: JSONObject): HomeItem? {
        val id = json.optLong("id", -1)
        if (id <= 0) return null
        return when (json.optString("type")) {
            "app" -> json.optString("app").takeIf { it.isNotEmpty() }?.let { HomeItem.App(id, it) }
            "folder" -> {
                val appsJson = json.optJSONArray("apps") ?: return null
                val apps = (0 until appsJson.length()).mapNotNull { appsJson.optString(it).takeIf(String::isNotEmpty) }.distinct()
                if (apps.isEmpty()) null else HomeItem.Folder(id, json.optString("name"), apps)
            }
            "widget" -> json.optString("kind").takeIf { it.isNotEmpty() }?.let { kind ->
                HomeItem.Widget(
                    id = id,
                    kind = kind,
                    appWidgetId = json.optInt("appWidgetId", HomeItem.NO_APP_WIDGET),
                    provider = if (json.has("provider")) json.optString("provider") else null,
                )
            }
            else -> null
        }
    }

    private const val MAX_PAGES = 12
}
