package org.mesos.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.updater.UpdateController

/** MesOS Settings: MesOS pages plus clean hand-offs to Android's own settings screens. */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UpdateController.init(this)
        MesOSLog.i(MesOSLog.SETTINGS, "MesOS Settings opened")

        setContent {
            MesOSUserTheme {
                SettingsApp(openExternal = ::openExternal)
            }
        }
    }

    /** Opens an Android settings screen, or tells the user this device has none. */
    private fun openExternal(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            MesOSLog.w(MesOSLog.SETTINGS, "No activity for ${intent.action}", e)
            Toast.makeText(this, R.string.settings_not_available, Toast.LENGTH_SHORT).show()
        }
    }
}
