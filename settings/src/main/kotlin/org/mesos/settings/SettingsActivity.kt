package org.mesos.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mesos.core.MesOSIntents
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.updater.UpdateController

/** MesOS Settings: MesOS pages plus clean hand-offs to Android's own settings screens. */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UpdateController.init(this)
        val page = Page.fromId(intent.getStringExtra(MesOSIntents.EXTRA_SETTINGS_PAGE))
        MesOSLog.i(MesOSLog.SETTINGS, "MesOS Settings opened" + (page?.let { " at ${it.id}" } ?: ""))

        setContent {
            MesOSUserTheme {
                SettingsApp(initialPage = page, openExternal = ::openExternal, finish = ::finish)
            }
        }
    }

    /** Opens the first of [intents] that works, or tells the user this device has none. */
    private fun openExternal(intents: List<Intent>) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                MesOSLog.w(MesOSLog.SETTINGS, "No activity for ${intent.action}")
            } catch (e: SecurityException) {
                MesOSLog.w(MesOSLog.SETTINGS, "Not allowed to open ${intent.action}", e)
            }
        }
        Toast.makeText(this, R.string.settings_not_available, Toast.LENGTH_SHORT).show()
    }
}
