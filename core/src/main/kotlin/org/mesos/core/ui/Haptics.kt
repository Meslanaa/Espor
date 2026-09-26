package org.mesos.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** MesOS haptic vocabulary, so the same kind of interaction feels the same everywhere. */
class MesOSHaptics internal constructor(private val feedback: HapticFeedback) {
    /** A long press that opens a menu or starts a drag. */
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Crossing a threshold or snapping to a position while dragging. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** A switch or toggle changing state. */
    fun toggle(on: Boolean) =
        feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** An action that completed (saved, sent, dropped). */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A key on a keypad. */
    fun key() = feedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
}

@Composable
fun rememberHaptics(): MesOSHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { MesOSHaptics(feedback) }
}
