package org.mesos.launcher.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.mesos.core.MesOSApps
import org.mesos.core.ui.startActivitySafely
import org.mesos.launcher.R

@Composable
internal fun WeatherWidget(modifier: Modifier) {
    val context = LocalContext.current
    WidgetPlaceholder(
        title = stringResource(R.string.widget_weather),
        message = stringResource(R.string.widget_weather_setup),
        modifier = modifier,
        onClick = { context.startActivitySafely(MesOSApps.launchIntent(context, MesOSApps.WEATHER)) },
    )
}

@Composable
internal fun AgendaWidget(nowMillis: Long, modifier: Modifier) {
    val context = LocalContext.current
    WidgetPlaceholder(
        title = stringResource(R.string.widget_agenda),
        message = stringResource(R.string.widget_agenda_empty),
        modifier = modifier,
        onClick = { context.startActivitySafely(MesOSApps.launchIntent(context, MesOSApps.CALENDAR)) },
    )
}
