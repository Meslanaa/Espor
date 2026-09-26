package org.mesos.clock

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.core.ui.theme.Sora
import java.util.Date

/** Full-screen ringing alarm or finished timer, shown over the lock screen. */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getIntExtra(AlarmEngine.EXTRA_ALARM_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val time = DateFormat.getTimeFormat(this).format(Date())
        setContent {
            MesOSUserTheme(forceDark = true) {
                RingScreen(
                    title = title,
                    time = time,
                    canSnooze = id != AlarmEngine.TIMER_ID,
                    onSnooze = {
                        AlarmEngine.get(this).snooze(id)
                        finish()
                    },
                    onDismiss = {
                        AlarmEngine.get(this).dismiss(id)
                        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "org.mesos.clock.extra.TITLE"
    }
}

@Composable
private fun RingScreen(title: String, time: String, canSnooze: Boolean, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val accent = MesOSTheme.colors.accentBright
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050914), Color(0xFF0A1834), accent.copy(alpha = 0.55f)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            Box(
                Modifier
                    .size(96.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MesOSGlyphs.Alarm, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(time, style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 84.sp, color = Color.White))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (canSnooze) RingButton(stringResource(R.string.clock_snooze), MesOSGlyphs.Moon, Color.White.copy(alpha = 0.18f), onSnooze)
                RingButton(stringResource(R.string.clock_dismiss), MesOSGlyphs.Close, accent, onDismiss)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RingButton(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}
