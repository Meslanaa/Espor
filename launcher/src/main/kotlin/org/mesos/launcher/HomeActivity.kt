package org.mesos.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import org.mesos.core.MesOSRelease
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.theme.MesOSUserTheme

/** MesOS Home: the HOME activity Android shows when the user presses Home. */
class HomeActivity : ComponentActivity() {

    private val now = MutableStateFlow(System.currentTimeMillis())
    private var drawerOpen by mutableStateOf(false)
    private var timeReceiverRegistered = false

    // Minute ticks and clock/time-zone changes; registered only while Home is visible.
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            now.value = System.currentTimeMillis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MesOSLog.i(MesOSLog.LAUNCHER, "MesOS Home started (${MesOSRelease.current.displayName})")

        val repository = AppRepository.get(this)
        repository.start()

        setContent {
            MesOSUserTheme {
                val model by repository.model.collectAsState()
                val time by now.collectAsState()
                HomeScreen(
                    model = model,
                    nowMillis = time,
                    drawerOpen = drawerOpen,
                    onOpenDrawer = { drawerOpen = true },
                    onCloseDrawer = { drawerOpen = false },
                    onLaunch = { entry ->
                        if (repository.launch(entry)) {
                            drawerOpen = false
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.launcher_launch_failed, entry.label),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timeReceiver, filter)
        }
        timeReceiverRegistered = true
        now.value = System.currentTimeMillis()
    }

    override fun onStop() {
        if (timeReceiverRegistered) {
            unregisterReceiver(timeReceiver)
            timeReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while already home returns to the home page.
        drawerOpen = false
    }
}
