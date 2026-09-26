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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import org.mesos.launcher.layout.HomeItem

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

/**
 * Long press and drag for MesOS Home, observed before the icons see the touch
 * (initial pass), so taps still reach the icons and swipes still reach the pager:
 *
 * - long press on an item → [onLongPressItem]; moving afterwards → [onDragStart],
 *   [onDragMove] and finally [onDrop] (or [onDragCancel]);
 * - long press on empty space → [onLongPressEmpty].
 */
internal fun Modifier.homeLongPressDrag(
    enabled: () -> Boolean,
    hitTest: (Offset) -> Long?,
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

        // Wait for the long-press timeout without the finger moving or lifting.
        val interrupted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!change.pressed || change.isConsumed) return@withTimeoutOrNull true
                if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
                if (event.changes.size > 1) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE")
            true
        }
        if (interrupted != null) return@awaitEachGesture

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

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.consumeUntilUp(pointerId: androidx.compose.ui.input.pointer.PointerId) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        change.consume()
        if (!change.pressed) return
    }
}
