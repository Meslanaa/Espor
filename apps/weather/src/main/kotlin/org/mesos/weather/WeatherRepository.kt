package org.mesos.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.hasPermission
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume

/** A place with its forecast, as shown on one page of MesOS Weather. */
data class PlaceWeather(
    val place: Place,
    val forecast: Forecast?,
    val fetchedAt: Long,
    val isCurrentLocation: Boolean,
    val failed: Boolean = false,
)

data class WeatherState(
    val pages: List<PlaceWeather> = emptyList(),
    val useLocation: Boolean = false,
    val fahrenheit: Boolean = false,
    val loading: Boolean = false,
)

/**
 * Forecasts from Open-Meteo (HTTPS, no account or key), for the device location
 * and the places the user saved. Results are cached on the device so Weather and
 * the Home widget show something immediately and offline.
 */
class WeatherRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("mesos_weather", Context.MODE_PRIVATE)
    private val cacheDir = File(appContext.filesDir, "weather")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    /** The first page: what the Home widget shows. */
    val primary: StateFlow<PlaceWeather?> get() = _primary
    private val _primary = MutableStateFlow(_state.value.pages.firstOrNull())

    fun setUseLocation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_LOCATION, enabled).apply()
        publish(_state.value.copy(useLocation = enabled, pages = pagesFor(enabled, _state.value.pages)))
        refreshInBackground(force = true)
    }

    fun setFahrenheit(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FAHRENHEIT, enabled).apply()
        publish(_state.value.copy(fahrenheit = enabled))
        refreshInBackground(force = true)
    }

    fun addPlace(place: Place) {
        val places = savedPlaces()
        if (places.any { it.id == place.id }) return
        savePlaces(places + place)
        publish(_state.value.copy(pages = pagesFor(_state.value.useLocation, _state.value.pages)))
        refreshInBackground(force = false)
    }

    fun removePlace(place: Place) {
        savePlaces(savedPlaces().filter { it.id != place.id })
        File(cacheDir, cacheName(place)).delete()
        publish(_state.value.copy(pages = pagesFor(_state.value.useLocation, _state.value.pages)))
    }

    fun refreshInBackground(force: Boolean) {
        scope.launch { refresh(force) }
    }

    /** Refreshes pages older than [MAX_AGE_MS] (or all with [force]). */
    suspend fun refresh(force: Boolean) = withContext(Dispatchers.IO) { refreshMutex.withLock { refreshLocked(force) } }

    private suspend fun refreshLocked(force: Boolean) {
        val current = _state.value
        publish(current.copy(loading = true))
        val now = System.currentTimeMillis()
        val updated = mutableListOf<PlaceWeather>()
        if (current.useLocation) {
            val here = locate()
            val previous = current.pages.firstOrNull { it.isCurrentLocation }
            val page = when {
                here == null -> previous?.copy(failed = previous.forecast == null)
                !force && previous != null && previous.forecast != null && now - previous.fetchedAt < MAX_AGE_MS &&
                    distanceKm(previous.place, here) < 5 -> previous
                else -> fetch(here, isCurrentLocation = true) ?: previous?.copy(failed = true)
            }
            page?.let(updated::add)
        }
        for (place in savedPlaces()) {
            val previous = current.pages.firstOrNull { !it.isCurrentLocation && it.place.id == place.id }
            val fresh = previous != null && previous.forecast != null && now - previous.fetchedAt < MAX_AGE_MS
            updated += if (!force && fresh) previous!! else fetch(place, isCurrentLocation = false) ?: previous?.copy(failed = true)
                ?: PlaceWeather(place, null, 0L, isCurrentLocation = false, failed = true)
        }
        publish(_state.value.copy(pages = updated, loading = false))
    }

    suspend fun searchPlaces(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val language = if (Locale.getDefault().language == "tr") "tr" else "en"
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=10&format=json&language=$language&name=" +
            URLEncoder.encode(q, "UTF-8")
        try {
            WeatherParser.places(httpsGet(url))
        } catch (e: IOException) {
            MesOSLog.w(TAG, "Place search failed", e)
            emptyList()
        } catch (e: WeatherFormatException) {
            MesOSLog.w(TAG, "Place search failed", e)
            emptyList()
        }
    }

    private fun publish(state: WeatherState) {
        _state.value = state
        _primary.value = state.pages.firstOrNull()
    }

    private fun fetch(place: Place, isCurrentLocation: Boolean): PlaceWeather? {
        val unit = if (_state.value.fahrenheit) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else ""
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,uv_index_max" +
                "&timezone=auto&forecast_days=10&forecast_hours=30%s",
            place.latitude,
            place.longitude,
            unit,
        )
        return try {
            val text = httpsGet(url)
            val forecast = WeatherParser.forecast(text)
            val now = System.currentTimeMillis()
            writeCache(place, text, now, isCurrentLocation)
            PlaceWeather(place, forecast, now, isCurrentLocation)
        } catch (e: IOException) {
            MesOSLog.w(TAG, "Forecast download failed for ${place.name}", e)
            null
        } catch (e: WeatherFormatException) {
            MesOSLog.w(TAG, "Forecast unreadable for ${place.name}", e)
            null
        }
    }

    private fun httpsGet(url: String): String {
        val connection = URL(url).openConnection() as? HttpsURLConnection ?: throw IOException("HTTPS required")
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { input ->
                val bytes = input.readBytes(MAX_RESPONSE_BYTES)
                String(bytes, Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readBytes(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) throw IOException("Response too large")
        }
        return out.toByteArray()
    }

    @SuppressLint("MissingPermission")
    private suspend fun locate(): Place? {
        if (!appContext.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return null
        val location = try {
            currentLocation(manager) ?: lastKnown(manager)
        } catch (e: SecurityException) {
            null
        } ?: return null
        val name = placeName(location) ?: appContext.getString(R.string.weather_my_location)
        return Place(name, "", location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(manager: LocationManager): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        for (provider in providers) {
            val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    try {
                        manager.getCurrentLocation(provider, signal, appContext.mainExecutor) { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                    } catch (e: IllegalArgumentException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
            if (location != null) return location
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        manager.getProviders(true).mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (e: IllegalArgumentException) {
                null
            }
        }.maxByOrNull { it.time }

    @Suppress("DEPRECATION")
    private fun placeName(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            Geocoder(appContext, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun loadState(): WeatherState {
        val useLocation = prefs.getBoolean(KEY_USE_LOCATION, false)
        val fahrenheit = prefs.getBoolean(KEY_FAHRENHEIT, false)
        val pages = mutableListOf<PlaceWeather>()
        if (useLocation) readCache(LOCATION_CACHE)?.let(pages::add)
        savedPlaces().forEach { place ->
            pages += readCache(cacheName(place)) ?: PlaceWeather(place, null, 0L, isCurrentLocation = false)
        }
        return WeatherState(pages = pages, useLocation = useLocation, fahrenheit = fahrenheit)
    }

    private fun pagesFor(useLocation: Boolean, previous: List<PlaceWeather>): List<PlaceWeather> {
        val pages = mutableListOf<PlaceWeather>()
        if (useLocation) previous.firstOrNull { it.isCurrentLocation }?.let(pages::add)
        savedPlaces().forEach { place ->
            pages += previous.firstOrNull { !it.isCurrentLocation && it.place.id == place.id }
                ?: PlaceWeather(place, null, 0L, isCurrentLocation = false)
        }
        return pages
    }

    private fun savedPlaces(): List<Place> = WeatherParser.placesFromJson(prefs.getString(KEY_PLACES, null))

    private fun savePlaces(places: List<Place>) {
        prefs.edit().putString(KEY_PLACES, WeatherParser.placesToJson(places)).apply()
    }

    private fun cacheName(place: Place) = "place_" + place.id.replace(Regex("[^0-9.,-]"), "_") + ".json"

    private fun writeCache(place: Place, text: String, fetchedAt: Long, isCurrentLocation: Boolean) {
        try {
            cacheDir.mkdirs()
            val name = if (isCurrentLocation) LOCATION_CACHE else cacheName(place)
            val json = JSONObject()
                .put("fetchedAt", fetchedAt)
                .put("name", place.name)
                .put("region", place.region)
                .put("lat", place.latitude)
                .put("lon", place.longitude)
                .put("data", text)
            File(cacheDir, name).writeText(json.toString())
        } catch (e: IOException) {
            MesOSLog.w(TAG, "Could not cache the forecast", e)
        }
    }

    private fun readCache(name: String): PlaceWeather? =
        try {
            val file = File(cacheDir, name)
            if (!file.exists()) {
                null
            } else {
                val json = JSONObject(file.readText())
                val place = Place(json.optString("name"), json.optString("region"), json.optDouble("lat"), json.optDouble("lon"))
                PlaceWeather(place, WeatherParser.forecast(json.optString("data")), json.optLong("fetchedAt"), name == LOCATION_CACHE)
            }
        } catch (e: IOException) {
            null
        } catch (e: JSONException) {
            null
        } catch (e: WeatherFormatException) {
            null
        }

    private fun distanceKm(a: Place, b: Place): Float {
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        return result[0] / 1000f
    }

    companion object {
        private const val TAG = "MesOSWeather"
        private const val KEY_PLACES = "places"
        private const val KEY_USE_LOCATION = "use_location"
        private const val KEY_FAHRENHEIT = "fahrenheit"
        private const val LOCATION_CACHE = "location.json"
        private const val MAX_AGE_MS = 30 * 60 * 1000L
        private const val LOCATION_TIMEOUT_MS = 8_000L
        private const val MAX_RESPONSE_BYTES = 512 * 1024

        @Volatile
        private var instance: WeatherRepository? = null

        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) {
                instance ?: WeatherRepository(context).also { instance = it }
            }
    }
}
