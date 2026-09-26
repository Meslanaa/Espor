package org.mesos.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.ShortcutInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mesos.core.MesOSIntents
import org.mesos.core.prefs.MesOSPreferences
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSMotion
import org.mesos.core.ui.glass
import org.mesos.core.ui.rememberHaptics
import org.mesos.launcher.AppEntry
import org.mesos.launcher.AppRepository
import org.mesos.launcher.LauncherModel
import org.mesos.launcher.R
import org.mesos.launcher.control.ControlCenter
import org.mesos.launcher.control.NotificationCenter
import org.mesos.launcher.home.AppUsage
import org.mesos.launcher.home.HomeModel
import org.mesos.launcher.layout.HomeItem
import org.mesos.launcher.layout.HomeLayout
import org.mesos.launcher.layout.WidgetKinds
import org.mesos.launcher.search.SearchEngine
import org.mesos.launcher.search.SearchResults
import org.mesos.launcher.wallpaper.WallpaperLayer
import org.mesos.launcher.widgets.AndroidWidgets
import org.mesos.launcher.widgets.MesOSWidget
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What MesOS Home needs from its activity. */
internal interface HomeActions {
    fun launch(entry: AppEntry, bounds: Rect?)
    fun launchShortcut(shortcut: ShortcutInfo, bounds: Rect?)
    fun open(intent: Intent)
    fun openAppInfo(entry: AppEntry, bounds: Rect?)
    fun uninstall(entry: AppEntry)
    fun addAndroidWidget(info: AppWidgetProviderInfo, page: Int, w: Int, h: Int)
    fun deleteAndroidWidget(appWidgetId: Int)
    fun copy(text: String)
}

/** Which long-press menu is open. */
internal data class MenuTarget(
    val anchor: Rect,
    val entry: AppEntry?,
    val itemId: Long?,
    val source: Source,
    val folderId: Long? = null,
) {
    enum class Source { HOME, DOCK, DRAWER, FOLDER }
}

/** UI state that the activity also touches (Home button, back). */
@Stable
internal class HomeUiState {
    val drawer = Animatable(0f)
    val control = Animatable(0f)
    var query by mutableStateOf("")
    var focusSearch by mutableStateOf(false)
    var editMode by mutableStateOf(false)
    var menu by mutableStateOf<MenuTarget?>(null)
    var openFolderId by mutableStateOf<Long?>(null)
    var widgetPicker by mutableStateOf(false)

    /** Incremented each time the Home button is pressed while Home is shown. */
    var homeRequests by mutableIntStateOf(0)
}

private enum class Sheet { DRAWER, CONTROL }

@Composable
internal fun HomeScreen(
    ui: HomeUiState,
    model: LauncherModel,
    layout: HomeLayout?,
    nowMillis: Long,
    actions: HomeActions,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val preferences = remember { MesOSPreferences.get(context) }
    val homeModel = remember { HomeModel.get(context) }
    val repository = remember { AppRepository.get(context) }
    val usage = remember { AppUsage.get(context) }
    val wallpaper by preferences.wallpaper.collectAsState()
    val accent by preferences.accent.collectAsState()
    val showBadges by preferences.notificationBadges.collectAsState()
    val badgesRaw by NotificationCenter.badges.collectAsState()
    val badges = if (showBadges) badgesRaw else emptyMap()
    val recentKeys by usage.recent.collectAsState()
    val counts by usage.counts.collectAsState()

    val registry = remember { HitRegistry() }
    val drag = remember { DragState() }
    var hoverId by remember { mutableStateOf<Long?>(null) }
    var cellSizeDp by remember { mutableStateOf(0f to 0f) }

    val current = layout ?: HomeLayout()
    val extraPage = if (ui.editMode || drag.isDragging) 1 else 0
    val pager = rememberPagerState { current.pageCount + extraPage }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF050914))) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val screen = Rect(0f, 0f, widthPx, heightPx)
        val drawerP = ui.drawer.value
        val controlP = ui.control.value
        val overlay = max(drawerP, controlP)

        // ---- Sheets (drawer from below, control center from above) ----
        fun settle(sheet: Sheet, open: Boolean) {
            val anim = if (sheet == Sheet.DRAWER) ui.drawer else ui.control
            scope.launch {
                anim.animateTo(if (open) 1f else 0f, MesOSMotion.sheet())
                if (!open && sheet == Sheet.DRAWER) {
                    ui.query = ""
                    ui.focusSearch = false
                }
            }
        }

        fun openDrawer(search: Boolean) {
            ui.focusSearch = search
            settle(Sheet.DRAWER, true)
        }

        val drawerConnection = remember(heightPx) {
            sheetConnection(scope, ui.drawer, heightPx, closeWhenPulled = +1) { open -> settle(Sheet.DRAWER, open) }
        }
        val controlConnection = remember(heightPx) {
            sheetConnection(scope, ui.control, heightPx * 0.7f, closeWhenPulled = -1) { open -> settle(Sheet.CONTROL, open) }
        }

        fun closeEverything(goToFirstPage: Boolean) {
            ui.menu = null
            ui.openFolderId = null
            ui.widgetPicker = false
            val anythingOpen = ui.drawer.value > 0f || ui.control.value > 0f || ui.editMode
            ui.editMode = false
            if (ui.drawer.value > 0f) settle(Sheet.DRAWER, false)
            if (ui.control.value > 0f) settle(Sheet.CONTROL, false)
            homeModel.update { it.compactPages() }
            if (goToFirstPage && !anythingOpen) scope.launch { pager.animateScrollToPage(0) }
        }

        LaunchedEffect(ui.homeRequests) {
            if (ui.homeRequests > 0) closeEverything(goToFirstPage = true)
        }

        BackHandler(enabled = true) {
            when {
                ui.menu != null -> ui.menu = null
                ui.widgetPicker -> ui.widgetPicker = false
                ui.openFolderId != null -> ui.openFolderId = null
                ui.drawer.value > 0f -> settle(Sheet.DRAWER, false)
                ui.control.value > 0f -> settle(Sheet.CONTROL, false)
                ui.editMode -> closeEverything(goToFirstPage = false)
            }
        }

        // ---- Drag and drop ----
        var edgeSince by remember { mutableLongStateOf(0L) }
        val edgePx = with(density) { 28.dp.toPx() }

        fun itemById(id: Long): HomeItem? =
            current.placement(id)?.item ?: current.dock.firstOrNull { it.id == id }

        fun dropAt(pointer: Offset) {
            val item = drag.item ?: return
            val id = item.id
            val placement = current.placement(id)
            val ok = when {
                registry.removeZone != Rect.Zero && registry.removeZone.contains(pointer) -> {
                    (item as? HomeItem.Widget)?.takeIf { it.kind == WidgetKinds.ANDROID }?.let { actions.deleteAndroidWidget(it.appWidgetId) }
                    homeModel.update { it.remove(id) }
                }
                registry.dock.contains(pointer) -> {
                    val others = current.dock.filter { it.id != id }
                    val slot = (((pointer.x - registry.dock.left) / registry.dock.width) * (others.size + 1)).toInt()
                        .coerceIn(0, others.size)
                    homeModel.update { it.moveToDock(id, slot) }
                }
                registry.grid.contains(pointer) -> {
                    val grid = registry.grid
                    val cw = grid.width / current.columns
                    val ch = grid.height / current.rows
                    val w = placement?.w ?: 1
                    val h = placement?.h ?: 1
                    val (x, y) = if (w > 1 || h > 1) {
                        ((drag.topLeft.x - grid.left) / cw).roundToInt().coerceIn(0, current.columns - w) to
                            ((drag.topLeft.y - grid.top) / ch).roundToInt().coerceIn(0, current.rows - h)
                    } else {
                        ((pointer.x - grid.left) / cw).toInt().coerceIn(0, current.columns - 1) to
                            ((pointer.y - grid.top) / ch).toInt().coerceIn(0, current.rows - 1)
                    }
                    homeModel.update { it.moveTo(id, pager.currentPage, x, y) }
                }
                else -> false
            }
            if (ok) haptics.confirm()
            drag.end()
            hoverId = null
            edgeSince = 0L
            if (!ui.editMode) homeModel.update { it.compactPages() }
        }

        val sheetDrag = remember(heightPx) { SheetDrag(scope, ui, heightPx) { sheet, open -> settle(sheet, open) } }
        val homeInteractive by rememberUpdatedState(
            layout != null && overlay == 0f && ui.menu == null && ui.openFolderId == null && !ui.widgetPicker,
        )

        // ---- Home layer: wallpaper, pages, dock ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f - 0.06f * overlay
                    scaleX = s
                    scaleY = s
                }
                .then(if (overlay > 0f) Modifier.blur((28 * overlay).dp) else Modifier),
        ) {
            WallpaperLayer(wallpaper = wallpaper, accent = accent)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - 0.8f * overlay }
                    .homeLongPressDrag(
                        enabled = { homeInteractive },
                        sheetsEnabled = { position ->
                            // Android widgets may scroll vertically themselves.
                            val id = registry.hit(position, screen)
                            val item = id?.let(::itemById)
                            !ui.editMode && !(item is HomeItem.Widget && item.kind == WidgetKinds.ANDROID)
                        },
                        hitTest = { position -> registry.hit(position, screen) },
                        onSheetDrag = { dy -> sheetDrag.drag(dy) },
                        onSheetEnd = { velocity -> sheetDrag.end(velocity) },
                        onLongPressItem = { id ->
                            haptics.longPress()
                            val item = itemById(id)
                            val bounds = registry.bounds(id) ?: Rect.Zero
                            val entry = (item as? HomeItem.App)?.let { model.byKey[it.app] }
                            val inDock = current.dock.any { it.id == id }
                            ui.menu = MenuTarget(bounds, entry, id, if (inDock) MenuTarget.Source.DOCK else MenuTarget.Source.HOME)
                        },
                        onLongPressEmpty = {
                            haptics.longPress()
                            ui.editMode = true
                        },
                        onDragStart = { id, pointer ->
                            val item = itemById(id)
                            val bounds = registry.bounds(id)
                            if (item == null || bounds == null) {
                                false
                            } else {
                                ui.menu = null
                                drag.start(item, bounds, pointer)
                                true
                            }
                        },
                        onDragMove = { pointer ->
                            drag.move(pointer)
                            hoverId = registry.hit(pointer, screen)?.takeIf { it != drag.item?.id }
                            val direction = when {
                                pointer.x < edgePx -> -1
                                pointer.x > widthPx - edgePx -> 1
                                else -> 0
                            }
                            val now = System.currentTimeMillis()
                            if (direction == 0 || !registry.grid.contains(Offset(widthPx / 2, pointer.y))) {
                                edgeSince = 0L
                            } else if (edgeSince == 0L) {
                                edgeSince = now
                            } else if (now - edgeSince > PAGE_FLIP_DELAY_MS) {
                                edgeSince = now
                                val target = (pager.currentPage + direction).coerceIn(0, pager.pageCount - 1)
                                if (target != pager.currentPage) {
                                    haptics.tick()
                                    scope.launch { pager.animateScrollToPage(target) }
                                }
                            }
                        },
                        onDrop = ::dropAt,
                        onDragCancel = {
                            drag.end()
                            hoverId = null
                        },
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                TopArea(editMode = ui.editMode, dragging = drag.isDragging, registry = registry, onDone = { closeEverything(false) })
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !drag.isDragging,
                ) { page ->
                    PageGrid(
                        page = page,
                        isCurrent = page == pager.currentPage,
                        layout = current,
                        model = model,
                        badges = badges,
                        nowMillis = nowMillis,
                        editMode = ui.editMode,
                        draggingId = drag.item?.id,
                        hoverId = hoverId,
                        registry = registry,
                        onCellSize = { w, h -> cellSizeDp = w to h },
                        onLaunch = { entry, bounds -> if (!ui.editMode) actions.launch(entry, bounds) },
                        onOpenFolder = { id -> if (!ui.editMode) ui.openFolderId = id },
                    )
                }
                PageDots(pager = pager, count = current.pageCount + extraPage)
                AnimatedVisibility(visible = !ui.editMode, enter = fadeIn(), exit = fadeOut()) {
                    SearchPill(onClick = { openDrawer(search = true) })
                }
                AnimatedVisibility(visible = ui.editMode, enter = fadeIn(), exit = fadeOut()) {
                    EditActions(
                        onWidgets = { ui.widgetPicker = true },
                        onWallpaper = { actions.open(MesOSIntents.settings(context, MesOSIntents.PAGE_APPEARANCE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onSettings = { actions.open(MesOSIntents.settings(context, MesOSIntents.PAGE_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    )
                }
                Dock(
                    items = current.dock,
                    model = model,
                    badges = badges,
                    draggingId = drag.item?.id,
                    registry = registry,
                    onLaunch = { entry, bounds -> if (!ui.editMode) actions.launch(entry, bounds) },
                    onOpenFolder = { id -> if (!ui.editMode) ui.openFolderId = id },
                )
            }
        }

        // ---- Floating copy of the dragged item ----
        drag.item?.let { item ->
            val sizeDp = with(density) { drag.size.width.toDp() to drag.size.height.toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset(drag.topLeft.x.roundToInt(), drag.topLeft.y.roundToInt()) }
                    .size(sizeDp.first, sizeDp.second)
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        alpha = 0.92f
                    },
            ) {
                if (item is HomeItem.Widget && item.kind == WidgetKinds.ANDROID) {
                    // An app widget view can only be shown once; drag a stand-in.
                    Box(Modifier.fillMaxSize().padding(6.dp).glass(RoundedCornerShape(22.dp), fill = 0.24f), contentAlignment = Alignment.Center) {
                        Icon(MesOSGlyphs.Widgets, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                } else {
                    ItemContent(item, model, badges, nowMillis, onLaunch = { _, _ -> }, onOpenFolder = {}, showLabel = true, cellWidthDp = sizeDp.first, cellHeightDp = sizeDp.second)
                }
            }
        }

        // ---- Scrim and sheets ----
        if (overlay > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050914).copy(alpha = 0.55f * overlay)),
            )
        }
        if (drawerP > 0f) {
            val recent = remember(recentKeys, model) { recentKeys.mapNotNull { model.byKey[it] }.filter { it in model.apps } }
            var results by remember { mutableStateOf(SearchResults()) }
            val engine = remember { SearchEngine(context) }
            LaunchedEffect(ui.query, model) {
                if (ui.query.isBlank()) {
                    results = SearchResults()
                } else {
                    delay(SEARCH_DEBOUNCE_MS)
                    results = engine.search(ui.query, model.apps, counts)
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = (1f - drawerP) * heightPx * 0.4f
                        alpha = min(1f, drawerP * 1.4f)
                    },
            ) {
                AppDrawer(
                    apps = model.apps,
                    recent = recent,
                    badges = badges,
                    query = ui.query,
                    onQueryChange = { ui.query = it },
                    focusSearch = ui.focusSearch,
                    results = results,
                    onLaunch = { entry, bounds -> actions.launch(entry, bounds) },
                    onLongPress = { entry, bounds ->
                        haptics.longPress()
                        ui.menu = MenuTarget(bounds, entry, null, MenuTarget.Source.DRAWER)
                    },
                    onOpen = actions::open,
                    onCopy = actions::copy,
                    onClose = { settle(Sheet.DRAWER, false) },
                    pullToClose = drawerConnection,
                )
            }
        }
        if (controlP > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = -(1f - controlP) * heightPx * 0.25f
                        alpha = min(1f, controlP * 1.4f)
                    },
            ) {
                ControlCenter(
                    nowMillis = nowMillis,
                    appIcon = { pkg -> model.apps.firstOrNull { it.packageName == pkg }?.icon ?: model.byKey.values.firstOrNull { it.packageName == pkg }?.icon },
                    appLabel = { pkg -> appLabel(context, model, pkg) },
                    onOpen = actions::open,
                    onClose = { settle(Sheet.CONTROL, false) },
                    closeConnection = controlConnection,
                )
            }
        }

        // ---- Folder, widget picker, menu ----
        val folder = ui.openFolderId?.let { id -> itemById(id) as? HomeItem.Folder }
        if (folder != null) {
            FolderOverlay(
                name = folder.name,
                apps = folder.apps.mapNotNull { model.byKey[it] },
                badges = badges,
                onRename = { name -> homeModel.update { it.renameFolder(folder.id, name) } },
                onLaunch = { entry, bounds ->
                    ui.openFolderId = null
                    actions.launch(entry, bounds)
                },
                onLongPress = { entry, bounds ->
                    haptics.longPress()
                    ui.menu = MenuTarget(bounds, entry, null, MenuTarget.Source.FOLDER, folderId = folder.id)
                },
                onDismiss = { ui.openFolderId = null },
            )
        } else if (ui.openFolderId != null) {
            ui.openFolderId = null
        }

        if (ui.widgetPicker) {
            WidgetPicker(
                nowMillis = nowMillis,
                widgets = AndroidWidgets.get(context),
                cellWidthDp = cellSizeDp.first.takeIf { it > 0f } ?: 90f,
                cellHeightDp = cellSizeDp.second.takeIf { it > 0f } ?: 100f,
                onPick = { choice ->
                    ui.widgetPicker = false
                    val page = pager.currentPage.coerceAtMost(current.pageCount - 1)
                    when (choice) {
                        is WidgetChoice.MesOS -> homeModel.update { it.addWidget(choice.kind, choice.w, choice.h, startPage = page) }
                        is WidgetChoice.Android -> {
                            val (w, h) = AndroidWidgets.span(
                                choice.info,
                                density.density,
                                cellSizeDp.first.takeIf { it > 0f } ?: 90f,
                                cellSizeDp.second.takeIf { it > 0f } ?: 100f,
                                current.columns,
                                current.rows,
                            )
                            actions.addAndroidWidget(choice.info, page, w, h)
                        }
                    }
                },
                onDismiss = { ui.widgetPicker = false },
            )
        }

        ui.menu?.let { target ->
            LongPressMenu(
                anchor = target.anchor,
                entry = target.entry,
                actions = menuActions(target, current, ui, homeModel, actions, pager.currentPage),
                onShortcut = { shortcut, bounds ->
                    ui.menu = null
                    actions.launchShortcut(shortcut, bounds)
                },
                onDismiss = { ui.menu = null },
                repository = repository,
            )
        }
    }
}

private const val PAGE_FLIP_DELAY_MS = 550L
private const val SEARCH_DEBOUNCE_MS = 120L

private fun appLabel(context: android.content.Context, model: LauncherModel, pkg: String): String =
    model.byKey.values.firstOrNull { it.packageName == pkg }?.label ?: try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        pkg
    }

/** Swipes on Home: up opens the drawer, down the control center, following the finger. */
private class SheetDrag(
    private val scope: CoroutineScope,
    private val ui: HomeUiState,
    private val heightPx: Float,
    private val settle: (Sheet, Boolean) -> Unit,
) {
    private var target: Sheet? = null
    private var drawer = 0f
    private var control = 0f

    fun drag(dy: Float) {
        if (target == null) {
            target = if (dy < 0) Sheet.DRAWER else Sheet.CONTROL
            drawer = ui.drawer.value
            control = ui.control.value
        }
        when (target) {
            Sheet.DRAWER -> {
                drawer = (drawer - dy / (heightPx * 0.6f)).coerceIn(0f, 1f)
                val value = drawer
                scope.launch { ui.drawer.snapTo(value) }
            }
            Sheet.CONTROL -> {
                control = (control + dy / (heightPx * 0.5f)).coerceIn(0f, 1f)
                val value = control
                scope.launch { ui.control.snapTo(value) }
            }
            null -> Unit
        }
    }

    fun end(velocity: Float) {
        when (target) {
            Sheet.DRAWER -> settle(Sheet.DRAWER, velocity < -FLING_VELOCITY || (drawer > 0.3f && velocity < FLING_VELOCITY))
            Sheet.CONTROL -> settle(Sheet.CONTROL, velocity > FLING_VELOCITY || (control > 0.3f && velocity > -FLING_VELOCITY))
            null -> Unit
        }
        target = null
    }
}

private const val FLING_VELOCITY = 1200f

/**
 * Lets a sheet's list close the sheet: pulling past the list's edge in the closing
 * direction ([closeWhenPulled] +1 = down, -1 = up) moves the sheet with the finger.
 */
private fun sheetConnection(
    scope: CoroutineScope,
    progress: Animatable<Float, *>,
    distance: Float,
    closeWhenPulled: Int,
    settle: (Boolean) -> Unit,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // While partly closed, scrolling the other way reopens the sheet first.
        val opening = available.y * closeWhenPulled < 0
        if (opening && progress.value < 1f && source == NestedScrollSource.UserInput) {
            scope.launch { progress.snapTo((progress.value + kotlin.math.abs(available.y) / distance).coerceIn(0f, 1f)) }
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val closing = available.y * closeWhenPulled > 0
        if (closing && source == NestedScrollSource.UserInput) {
            scope.launch { progress.snapTo((progress.value - kotlin.math.abs(available.y) / distance).coerceIn(0f, 1f)) }
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (progress.value >= 1f) return Velocity.Zero
        val closingFling = available.y * closeWhenPulled > FLING_VELOCITY
        settle(!closingFling && progress.value > 0.65f)
        return available
    }
}

@Composable
private fun TopArea(editMode: Boolean, dragging: Boolean, registry: HitRegistry, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (dragging) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF43F5E).copy(alpha = 0.3f))
                    .border(1.dp, Color(0xFFFB7185).copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                    .onGloballyPositioned { registry.removeZone = it.boundsInRoot() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MesOSGlyphs.Trash, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.edit_remove_zone), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        } else {
            DisposableEffect(Unit) {
                registry.removeZone = Rect.Zero
                onDispose { }
            }
            if (editMode) {
                Text(
                    stringResource(R.string.edit_done),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .glass(CircleShape, fill = 0.2f)
                        .clickable(onClick = onDone)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PageGrid(
    page: Int,
    isCurrent: Boolean,
    layout: HomeLayout,
    model: LauncherModel,
    badges: Map<String, Int>,
    nowMillis: Long,
    editMode: Boolean,
    draggingId: Long?,
    hoverId: Long?,
    registry: HitRegistry,
    onCellSize: (Float, Float) -> Unit,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onOpenFolder: (Long) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .then(
                if (editMode) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                } else {
                    Modifier
                },
            )
            .onGloballyPositioned { if (isCurrent) registry.grid = it.boundsInRoot() },
    ) {
        val cellW = maxWidth / layout.columns
        val cellH = maxHeight / layout.rows
        LaunchedEffect(cellW, cellH) { onCellSize(cellW.value, cellH.value) }
        val density = LocalDensity.current
        val cellWpx = with(density) { cellW.roundToPx() }
        val cellHpx = with(density) { cellH.roundToPx() }
        layout.onPage(page).forEach { placement ->
            key(placement.item.id) {
                val id = placement.item.id
                val target = IntOffset(placement.x * cellWpx, placement.y * cellHpx)
                val offset by animateIntOffsetAsState(target, MesOSMotion.gridMove, label = "cell")
                val hoverScale by animateFloatAsState(if (hoverId == id) 1.12f else 1f, label = "hover")
                Box(
                    modifier = Modifier
                        .offset { offset }
                        .size(cellW * placement.w, cellH * placement.h)
                        .onGloballyPositioned { registry.register(id, it.boundsInRoot()) }
                        .graphicsLayer {
                            alpha = if (draggingId == id) 0f else 1f
                            scaleX = hoverScale
                            scaleY = hoverScale
                        },
                ) {
                    ItemContent(
                        item = placement.item,
                        model = model,
                        badges = badges,
                        nowMillis = nowMillis,
                        onLaunch = onLaunch,
                        onOpenFolder = onOpenFolder,
                        showLabel = true,
                        cellWidthDp = cellW * placement.w,
                        cellHeightDp = cellH * placement.h,
                    )
                }
                DisposableEffect(id) { onDispose { registry.unregister(id) } }
            }
        }
    }
}

@Composable
private fun ItemContent(
    item: HomeItem,
    model: LauncherModel,
    badges: Map<String, Int>,
    nowMillis: Long,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onOpenFolder: (Long) -> Unit,
    showLabel: Boolean,
    cellWidthDp: Dp,
    cellHeightDp: Dp,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val positioned = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }
    when (item) {
        is HomeItem.App -> {
            val entry = model.byKey[item.app] ?: return
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppTile(
                    entry = entry,
                    onClick = { onLaunch(entry, bounds) },
                    badge = badges[entry.packageName] ?: 0,
                    showLabel = showLabel,
                    modifier = positioned.fillMaxWidth(),
                )
            }
        }
        is HomeItem.Folder -> {
            val apps = item.apps.mapNotNull { model.byKey[it] }
            if (apps.isEmpty()) return
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FolderTile(
                    name = item.name.ifBlank { stringResource(R.string.launcher_folder_unnamed) },
                    apps = apps,
                    onClick = { onOpenFolder(item.id) },
                    showLabel = showLabel,
                    badge = apps.sumOf { badges[it.packageName] ?: 0 },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        is HomeItem.Widget -> Box(Modifier.fillMaxSize().padding(6.dp)) {
            if (item.kind == WidgetKinds.ANDROID) {
                AndroidWidgetView(item, cellWidthDp.value - 12f, cellHeightDp.value - 12f)
            } else {
                MesOSWidget(item.kind, nowMillis, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun AndroidWidgetView(item: HomeItem.Widget, widthDp: Float, heightDp: Float) {
    val context = LocalContext.current
    val widgets = remember { AndroidWidgets.get(context) }
    val info = remember(item.appWidgetId) { widgets.info(item.appWidgetId) }
    if (info == null) {
        Box(
            Modifier
                .fillMaxSize()
                .glass(RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.launcher_widget_unavailable), style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
        return
    }
    AndroidView(
        factory = { ctx -> widgets.createView(ctx, item.appWidgetId, info) },
        update = { view -> AndroidWidgets.updateSize(view, widthDp, heightDp) },
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp)),
    )
}

@Composable
private fun PageDots(pager: PagerState, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == pager.currentPage
            val width by animateFloatAsState(if (selected) 18f else 6f, label = "dot")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .width(width.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (selected) 1f else 0.45f)),
            )
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .height(48.dp)
            .glass(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(MesOSGlyphs.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.launcher_search_pill), style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun EditActions(onWidgets: () -> Unit, onWallpaper: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EditButton(stringResource(R.string.edit_widgets), MesOSGlyphs.Widgets, onWidgets, Modifier.weight(1f))
        EditButton(stringResource(R.string.edit_wallpaper), MesOSGlyphs.Image, onWallpaper, Modifier.weight(1f))
        EditButton(stringResource(R.string.edit_settings), MesOSGlyphs.Settings, onSettings, Modifier.weight(1f))
    }
}

@Composable
private fun EditButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .height(64.dp)
            .glass(RoundedCornerShape(22.dp), fill = 0.18f)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun Dock(
    items: List<HomeItem>,
    model: LauncherModel,
    badges: Map<String, Int>,
    draggingId: Long?,
    registry: HitRegistry,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onOpenFolder: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp)
            .height(88.dp)
            .glass(RoundedCornerShape(34.dp), fill = 0.14f, edge = 0.2f)
            .onGloballyPositioned { registry.dock = it.boundsInRoot() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            key(item.id) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { registry.register(item.id, it.boundsInRoot()) }
                        .graphicsLayer { alpha = if (draggingId == item.id) 0f else 1f },
                    contentAlignment = Alignment.Center,
                ) {
                    var bounds by remember { mutableStateOf(Rect.Zero) }
                    when (item) {
                        is HomeItem.App -> model.byKey[item.app]?.let { entry ->
                            AppTile(
                                entry = entry,
                                onClick = { onLaunch(entry, bounds) },
                                showLabel = false,
                                badge = badges[entry.packageName] ?: 0,
                                modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
                            )
                        }
                        is HomeItem.Folder -> {
                            val apps = item.apps.mapNotNull { model.byKey[it] }
                            FolderTile(item.name, apps, onClick = { onOpenFolder(item.id) }, showLabel = false)
                        }
                        is HomeItem.Widget -> Unit
                    }
                }
                DisposableEffect(item.id) { onDispose { registry.unregister(item.id) } }
            }
        }
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.launcher_dock_empty),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun menuActions(
    target: MenuTarget,
    layout: HomeLayout,
    ui: HomeUiState,
    homeModel: HomeModel,
    actions: HomeActions,
    page: Int,
): List<MenuAction> {
    val entry = target.entry
    val item = target.itemId?.let { id -> layout.placement(id)?.item ?: layout.dock.firstOrNull { it.id == id } }
    val result = mutableListOf<MenuAction>()
    val close = { ui.menu = null }
    val info = stringResource(R.string.menu_app_info)
    val uninstall = stringResource(R.string.menu_uninstall)
    val removeHome = stringResource(R.string.menu_remove)
    val addHome = stringResource(R.string.menu_add_home)
    val editHome = stringResource(R.string.menu_edit_home)
    val rename = stringResource(R.string.menu_rename)
    val removeFolder = stringResource(R.string.menu_remove_from_folder)

    if (entry != null) {
        result += MenuAction(info, MesOSGlyphs.Info) {
            close()
            actions.openAppInfo(entry, target.anchor)
        }
        if (!entry.isSystemApp) {
            result += MenuAction(uninstall, MesOSGlyphs.Trash) {
                close()
                actions.uninstall(entry)
            }
        }
    }
    when (target.source) {
        MenuTarget.Source.HOME, MenuTarget.Source.DOCK -> {
            if (item is HomeItem.Folder) {
                result += MenuAction(rename, MesOSGlyphs.Edit) {
                    close()
                    ui.openFolderId = item.id
                }
            }
            if (item != null) {
                result += MenuAction(removeHome, MesOSGlyphs.Close) {
                    close()
                    (item as? HomeItem.Widget)?.takeIf { it.kind == WidgetKinds.ANDROID }?.let { actions.deleteAndroidWidget(it.appWidgetId) }
                    homeModel.update { it.remove(item.id).compactPages() }
                }
            }
            result += MenuAction(editHome, MesOSGlyphs.Grid) {
                close()
                ui.editMode = true
            }
        }
        MenuTarget.Source.DRAWER -> if (entry != null) {
            result += MenuAction(addHome, MesOSGlyphs.Plus) {
                close()
                homeModel.update { it.addApp(entry.key, startPage = page) }
            }
        }
        MenuTarget.Source.FOLDER -> if (entry != null && target.folderId != null) {
            result += MenuAction(removeFolder, MesOSGlyphs.Upload) {
                close()
                homeModel.update { it.removeFromFolder(target.folderId, entry.key, page) }
            }
        }
    }
    return result
}
