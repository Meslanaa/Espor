package org.mesos.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.mesos.core.R

fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** Runs [onResume] every time the hosting activity resumes (e.g. back from Android settings). */
@Composable
fun OnResume(onResume: () -> Unit) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val callback by rememberUpdatedState(onResume)
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) callback()
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
}

/** Runs [onPause] every time the hosting activity pauses (e.g. to save unsaved edits). */
@Composable
fun OnPause(onPause: () -> Unit) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val callback by rememberUpdatedState(onPause)
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) callback()
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
}

/** Android's app details screen, where denied permissions can be granted later. */
fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

fun Context.startActivitySafely(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

/**
 * Shows [content] once [isGranted] holds; otherwise explains why the app needs
 * access and asks Android for [permissions]. Re-checks when the user returns
 * from Android's settings.
 */
@Composable
fun PermissionGate(
    permissions: List<String>,
    rationale: String,
    isGranted: (Context) -> Boolean = { context -> permissions.all(context::hasPermission) },
    icon: ImageVector = MesOSGlyphs.Lock,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(isGranted(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedOnce = true
        granted = isGranted(context)
    }
    OnResume { granted = isGranted(context) }

    if (granted) {
        content()
        return
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = icon,
            title = title ?: stringResource(R.string.mesos_permission_title),
            message = rationale,
            action = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { launcher.launch(permissions.toTypedArray()) }) {
                        Text(stringResource(R.string.mesos_permission_allow))
                    }
                    if (askedOnce) {
                        TextButton(onClick = { context.startActivitySafely(appDetailsIntent(context)) }) {
                            Text(stringResource(R.string.mesos_permission_open_settings))
                        }
                    }
                }
            },
        )
    }
}
