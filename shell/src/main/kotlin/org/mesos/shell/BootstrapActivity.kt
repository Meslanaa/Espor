package org.mesos.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mesos.core.MesOSRelease
import org.mesos.core.ReleaseChannel
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.MesOSWordmark
import org.mesos.core.ui.theme.MesOSTheme

/**
 * Phase 1 bootstrap screen: shows the MesOS identity compiled from mesos.properties.
 * Replaced by the MesOS Home experience in Phase 2.
 */
class BootstrapActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val release = MesOSRelease.current
        MesOSLog.i(MesOSLog.SYSTEM, "Starting ${release.displayName} (build ${release.buildNumber})")

        setContent {
            MesOSTheme {
                BootstrapScreen(release)
            }
        }
    }
}

@Composable
private fun BootstrapScreen(release: MesOSRelease) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MesOSWordmark()
            Spacer(Modifier.height(8.dp))
            Text(
                text = release.displayVersion,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(
                    R.string.bootstrap_build_info,
                    release.buildNumber,
                    release.channel.displayName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BootstrapScreenPreview() {
    MesOSTheme {
        BootstrapScreen(
            MesOSRelease(
                versionName = "0.1",
                versionCode = 1,
                label = "Developer Preview",
                channel = ReleaseChannel.DEVELOPER,
                buildNumber = "local",
            ),
        )
    }
}
