package org.mesos.phone

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.mesos.core.ui.Avatar
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.Sora

/** The call screen: incoming call, dialling and ongoing call. Also shows over the lock screen. */
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        // Android 8.1+ reads showWhenLocked/turnScreenOn from the manifest.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        answerFrom(intent)
        setContent {
            MesOSTheme(darkTheme = true) {
                CallScreen(onDone = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        answerFrom(intent)
    }

    /** "Answer" in the incoming call notification opens this screen with the call to answer. */
    private fun answerFrom(intent: Intent?) {
        val id = intent?.getIntExtra(EXTRA_ANSWER, 0) ?: 0
        if (id != 0) {
            CallManager.answer(id)
            intent?.removeExtra(EXTRA_ANSWER)
        }
    }

    companion object {
        const val EXTRA_ANSWER = "org.mesos.phone.extra.ANSWER"

        fun intent(context: Context): Intent =
            Intent(context, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

private val Green = Color(0xFF22C55E)
private val Red = Color(0xFFEF4444)

@Composable
private fun CallScreen(onDone: () -> Unit) {
    val calls by CallManager.state.collectAsState()
    val audio by CallManager.audio.collectAsState()
    // The call on screen: a ringing one first, otherwise the first live one.
    val call = calls.firstOrNull { it.status == CallStatus.RINGING } ?: calls.firstOrNull { it.status != CallStatus.ENDED }
    var lastCall by remember { mutableStateOf<CallInfo?>(null) }
    LaunchedEffect(call) { if (call != null) lastCall = call }
    val shown = call ?: lastCall
    var keypad by remember { mutableStateOf(false) }
    var dialed by remember { mutableStateOf("") }

    // Close shortly after the last call ended, so "Call ended" can be read.
    LaunchedEffect(call == null) {
        if (call == null) {
            delay(if (lastCall == null) 0L else 1_200L)
            onDone()
        }
    }
    ProximityLock(active = call != null && call.status != CallStatus.RINGING && !audio.speaker)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF111A3A), Color(0xFF070C1A)))),
    ) {
        if (shown == null) return@Box
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(statusText(call, shown), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(24.dp))
            if (!keypad) {
                Avatar(shown.displayName, photo = shown.contact?.photo, size = 128.dp)
                Spacer(Modifier.height(20.dp))
            }
            Text(
                shown.displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (shown.contact != null && shown.number.isNotEmpty()) {
                Text(shown.number, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
            }
            if (keypad) {
                Spacer(Modifier.height(12.dp))
                Text(dialed, style = TextStyle(fontFamily = Sora, fontSize = 28.sp, color = Color.White), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            when {
                call == null -> Unit
                call.status == CallStatus.RINGING -> IncomingActions(call)
                keypad -> {
                    DtmfPad { digit ->
                        dialed += digit
                        CallManager.tone(call.id, digit)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { keypad = false }) { Text(stringResource(R.string.phone_hide_keypad), color = Color.White) }
                        Spacer(Modifier.size(24.dp))
                        RoundButton(MesOSGlyphs.CallEnd, stringResource(R.string.phone_hang_up), Red, 72.dp) { CallManager.hangUp(call.id) }
                    }
                }
                else -> OngoingActions(call, audio, onKeypad = { keypad = true })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun statusText(call: CallInfo?, shown: CallInfo): String {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(call?.status) {
        while (call?.status == CallStatus.ACTIVE) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    return when (call?.status) {
        null, CallStatus.ENDED, CallStatus.DISCONNECTING -> stringResource(R.string.phone_call_ended)
        CallStatus.RINGING -> stringResource(R.string.phone_incoming)
        CallStatus.DIALING, CallStatus.CONNECTING -> stringResource(R.string.phone_calling)
        CallStatus.HOLDING -> stringResource(R.string.phone_on_hold)
        CallStatus.ACTIVE -> shown.connectedAt?.let { formatDuration(now - it) } ?: stringResource(R.string.phone_ongoing)
    }
}

internal fun formatDuration(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total / 60) % 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun IncomingActions(call: CallInfo) {
    val pulse = rememberInfiniteTransition(label = "answer")
    val scale by pulse.animateFloat(1f, 1.12f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "answerScale")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LabeledButton(MesOSGlyphs.CallEnd, stringResource(R.string.phone_decline), Red) { CallManager.decline(call.id) }
        LabeledButton(MesOSGlyphs.Phone, stringResource(R.string.phone_answer), Green, Modifier.scale(scale)) { CallManager.answer(call.id) }
    }
}

@Composable
private fun OngoingActions(call: CallInfo, audio: AudioInfo, onKeypad: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToggleButton(if (audio.muted) MesOSGlyphs.MicOff else MesOSGlyphs.Mic, stringResource(R.string.phone_mute), audio.muted) {
                CallManager.setMuted(!audio.muted)
            }
            ToggleButton(MesOSGlyphs.Keypad, stringResource(R.string.phone_keypad), false, onKeypad)
            ToggleButton(MesOSGlyphs.Volume, stringResource(R.string.phone_speaker), audio.speaker) {
                CallManager.setSpeaker(!audio.speaker)
            }
            if (call.canHold) {
                ToggleButton(MesOSGlyphs.Pause, stringResource(R.string.phone_hold), call.status == CallStatus.HOLDING) {
                    CallManager.hold(call.id, call.status != CallStatus.HOLDING)
                }
            }
        }
        RoundButton(MesOSGlyphs.CallEnd, stringResource(R.string.phone_hang_up), Red, 76.dp) { CallManager.hangUp(call.id) }
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.14f))
                .clickable(onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (active) Color(0xFF0B1224) else Color.White)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LabeledButton(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundButton(icon, label, color, 76.dp, modifier, onClick)
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
private fun DtmfPad(onDigit: (Char) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Dialpad.keypad.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { (digit, letters) ->
                    Column(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onDigit(digit) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(digit.toString(), style = TextStyle(fontFamily = Sora, fontSize = 28.sp, fontWeight = FontWeight.Normal, color = Color.White))
                        if (letters.isNotEmpty()) Text(letters, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

/** Turns the screen off while the phone is at the ear (earpiece calls only). */
@Composable
private fun ProximityLock(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active) {
        val power = context.getSystemService(PowerManager::class.java)
        val lock = if (active && power?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
            power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "MesOS:call").apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L)
            }
        } else {
            null
        }
        onDispose { lock?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
    }
}
