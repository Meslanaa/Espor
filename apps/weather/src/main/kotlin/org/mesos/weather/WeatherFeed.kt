package org.mesos.weather

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/** What other MesOS components (the Home widget) may read from MesOS Weather. */
object WeatherFeed {

    /** Weather for the first place in MesOS Weather (the device location when enabled). */
    fun primary(context: Context): StateFlow<PlaceWeather?> = WeatherRepository.get(context).primary

    /** Downloads a new forecast in the background when the cached one is older than 30 minutes. */
    fun refreshIfStale(context: Context) = WeatherRepository.get(context).refreshInBackground(force = false)

    fun temperatureText(value: Double): String = temperature(value)
}
