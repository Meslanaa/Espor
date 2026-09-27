package org.mesos.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import org.mesos.launcher.layout.HomeItem
import kotlin.math.abs

/** Where things are on screen (root coordinates), for hit testing and drops. */
internal class HitRegistry {
    private val items = LinkedHashMap<Long, Rect>()
    var grid: Rect = Rect.Zero
    var dock: Rect = Rect.Zero
    var removeZone: Rect = Rect.Zero

    fun register(id: Long, bounds: Rect) {
        items[id] = bounds
    }

    fun unregister(id: Long) {
        items.remove(id)
    }

    fun bounds(id: Long): Rect? = items[id]

    /** The item under [position] that is fully inside the screen area [visible]. */
    fun hit(position: Offset, visible: Rect): Long? =
        items.entries.lastOrNull { (_, rect) ->
            rect.contains(position) && rect.left >= visible.left - 1f && rect.right <= visible.right + 1f
        }?.key
}

/** The item being dragged and where its floating copy is. */
@Stable
internal class DragState {
    var item by mutableStateOf<HomeItem?>(null)
        private set

    /** Size of the floating copy in px. */
    var size by mutableStateOf(IntSize.Zero)
        private set

    /** Top-left of the floating copy in root px. */
    var topLeft by mutableStateOf(Offset.Zero)
        private set

    var pointer by mutableStateOf(Offset.Zero)
        private set

    private var grab = Offset.Zero

    val isDragging: Boolean get() = item != null

    fun start(item: HomeItem, bounds: Rect, pointer: Offset) {
        this.item = item
        size = IntSize(bounds.width.toInt(), bounds.height.toInt())
        grab = pointer - bounds.topLeft
        move(pointer)
    }

    fun move(pointer: Offset) {
        this.pointer = pointer
        topLeft = pointer - grab
    }

    fun end() {
        item = null
    }
}

/** How a touch on Home ended before the long-press timeout. */
private enum class EarlyEnd { LIFTED, HORIZONTAL, VERTICAL }

/**
 * All gestures of MesOS Home, observed before the icons see the touch (initial
 * pass), so taps still reach the icons and sideways swipes still reach the pager:
 *
 * - vertical swipe → [onSheetDrag] with the finger's movement, then [onSheetEnd]
 *   with its velocity (drawer up, control center down);
 * - sideways swipe → the pager; when the finger lifts, [onPageSwipeEnd] with the
 *   horizontal distance, so Home can turn the page if the pager never started
 *   (a swipe with too few moves, e.g. on a busy UI thread). Returns true if it did;
 * - long press on an item → [onLongPressItem]; moving afterwards → [onDragStart],
 *   [onDragMove] and finally [onDrop] (or [onDragCancel]);
 * - long press on empty space → [onLongPressEmpty].
 */
internal fun Modifier.homeLongPressDrag(
    enabled: () -> Boolean,
    sheetsEnabled: (Offset) -> Boolean,
    hitTest: (Offset) -> Long?,
    onSheetDrag: (dy: Float) -> Unit,
    onSheetEnd: (velocityY: Float) -> Unit,
    onPageSwipeEnd: (dx: Float) -> Boolean,
    onLongPressItem: (id: Long) -> Unit,
    onLongPressEmpty: (Offset) -> Unit,
    onDragStart: (id: Long, pointer: Offset) -> Boolean,
    onDragMove: (Offset) -> Unit,
    onDrop: (Offset) -> Unit,
    onDragCancel: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled()) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        val velocity = VelocityTracker()
        velocity.addPosition(down.uptimeMillis, down.position)
        var last = down.position
        // A swipe can arrive as just down and up when the UI thread was busy.
        var upAlready: PointerInputChange? = null

        // Until the long-press timeout: a lift is a tap, a move is a swipe.
        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull EarlyEnd.LIFTED
                if (change.isConsumed || event.changes.size > 1) return@withTimeoutOrNull EarlyEnd.LIFTED
                velocity.addPosition(change.uptimeMillis, change.position)
                last = change.position
                val moved = change.position - down.position
                if (moved.getDistance() > slop) {
                    if (!change.pressed) upAlready = change
                    return@withTimeoutOrNull if (abs(moved.y) > abs(moved.x)) EarlyEnd.VERTICAL else EarlyEnd.HORIZONTAL
                }
                if (!change.pressed) return@withTimeoutOrNull EarlyEnd.LIFTED
            }
            @Suppress("UNREACHABLE_CODE")
            EarlyEnd.LIFTED
        }
        when (early) {
            EarlyEnd.LIFTED -> return@awaitEachGesture
            EarlyEnd.HORIZONTAL -> {
                // Watch (without consuming) until the finger lifts; the pager has the swipe.
                val up = upAlready ?: awaitUp(down.id)
                if (up != null && onPageSwipeEnd(up.position.x - down.position.x)) up.consume()
                return@awaitEachGesture
            }
            EarlyEnd.VERTICAL -> {
                if (!sheetsEnabled(down.position)) return@awaitEachGesture
                // Own the swipe from here on: the pager and the icons do not see it.
                onSheetDrag(last.y - down.position.y)
                upAlready?.let { up ->
                    up.consume()
                    onSheetEnd(velocity.calculateVelocity().y)
                    return@awaitEachGesture
                }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        onSheetEnd(0f)
                        return@awaitEachGesture
                    }
                    change.consume()
                    if (!change.pressed) {
                        onSheetEnd(velocity.calculateVelocity().y)
                        return@awaitEachGesture
                    }
                    velocity.addPosition(change.uptimeMillis, change.position)
                    onSheetDrag(change.position.y - last.y)
                    last = change.position
                }
            }
            null -> Unit // Long press.
        }

        val id = hitTest(down.position)
        if (id == null) {
            onLongPressEmpty(down.position)
            consumeUntilUp(down.id)
            return@awaitEachGesture
        }

        onLongPressItem(id)
        var dragging = false
        val origin = down.position
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                if (dragging) onDragCancel()
                return@awaitEachGesture
            }
            change.consume()
            if (!change.pressed) {
                if (dragging) onDrop(change.position)
                return@awaitEachGesture
            }
            if (!dragging && (change.position - origin).getDistance() > slop) {
                dragging = onDragStart(id, change.position)
                if (!dragging) {
                    consumeUntilUp(down.id)
                    return@awaitEachGesture
                }
            }
            if (dragging) onDragMove(change.position)
        }
    }
}

/** The up event of [pointerId], seen before the children and not consumed; null if cancelled. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitUp(
    pointerId: androidx.compose.ui.input.pointer.PointerId,
): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return null
        if (!change.pressed) return change
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.consumeUntilUp(pointerId: androidx.compose.ui.input.pointer.PointerId) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        change.consume()
        if (!change.pressed) return
    }
}
