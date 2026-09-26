package org.mesos.weather

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/** Weather in plain values (Open-Meteo, metric or imperial as requested). Pure Kotlin. */
data class CurrentWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val apparent: Double,
    val humidity: Int,
    val windSpeed: Double,
    val code: Int,
    val isDay: Boolean,
)

data class HourWeather(val time: LocalDateTime, val temperature: Double, val code: Int, val precipitation: Int?, val isDay: Boolean)

data class DayWeather(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val precipitation: Int?,
    val uvIndex: Double?,
)

data class Forecast(
    val timezone: String,
    val current: CurrentWeather,
    val hours: List<HourWeather>,
    val days: List<DayWeather>,
)

/** A place the user can pick; from geocoding or the device location. */
data class Place(
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
) {
    val id: String get() = "%.3f,%.3f".format(java.util.Locale.US, latitude, longitude)
}

/** What a weather code (WMO 4677, as used by Open-Meteo) means. */
enum class Condition {
    CLEAR, MAINLY_CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, DRIZZLE, RAIN, FREEZING_RAIN, SNOW, SHOWERS, SNOW_SHOWERS, THUNDERSTORM;

    val isRainy: Boolean get() = this in setOf(DRIZZLE, RAIN, FREEZING_RAIN, SHOWERS, THUNDERSTORM)
    val isSnowy: Boolean get() = this == SNOW || this == SNOW_SHOWERS
    val isCloudy: Boolean get() = this != CLEAR && this != MAINLY_CLEAR

    companion object {
        fun fromCode(code: Int): Condition = when (code) {
            0 -> CLEAR
            1 -> MAINLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57, 66, 67 -> FREEZING_RAIN
            61, 63, 65 -> RAIN
            71, 73, 75, 77 -> SNOW
            80, 81, 82 -> SHOWERS
            85, 86 -> SNOW_SHOWERS
            95, 96, 99 -> THUNDERSTORM
            else -> if (code < 0) CLEAR else OVERCAST
        }
    }
}

/** Reads Open-Meteo JSON. Throws [WeatherFormatException] when a required part is missing. */
object WeatherParser {

    fun forecast(text: String): Forecast {
        val root = obj(text)
        val current = root.optJSONObject("current") ?: throw WeatherFormatException("current")
        val hourly = root.optJSONObject("hourly") ?: throw WeatherFormatException("hourly")
        val daily = root.optJSONObject("daily") ?: throw WeatherFormatException("daily")

        val now = CurrentWeather(
            time = dateTime(current.optString("time")) ?: throw WeatherFormatException("current.time"),
            temperature = number(current, "temperature_2m"),
            apparent = current.optDouble("apparent_temperature", number(current, "temperature_2m")),
            humidity = current.optInt("relative_humidity_2m", 0),
            windSpeed = current.optDouble("wind_speed_10m", 0.0),
            code = current.optInt("weather_code", 0),
            isDay = current.optInt("is_day", 1) == 1,
        )

        val hourTimes = hourly.optJSONArray("time") ?: JSONArray()
        val hourTemps = hourly.optJSONArray("temperature_2m") ?: JSONArray()
        val hourCodes = hourly.optJSONArray("weather_code") ?: JSONArray()
        val hourRain = hourly.optJSONArray("precipitation_probability")
        val hourDay = hourly.optJSONArray("is_day")
        val currentHour = now.time.withMinute(0).withSecond(0).withNano(0)
        val hours = (0 until hourTimes.length()).mapNotNull { i ->
            val time = dateTime(hourTimes.optString(i)) ?: return@mapNotNull null
            val temperature = hourTemps.optDouble(i, Double.NaN)
            if (time.isBefore(currentHour) || temperature.isNaN()) return@mapNotNull null
            HourWeather(
                time = time,
                temperature = temperature,
                code = hourCodes.optInt(i, 0),
                precipitation = hourRain?.let { optionalInt(it, i) },
                isDay = hourDay?.optInt(i, 1) != 0,
            )
        }.take(24)

        val dayDates = daily.optJSONArray("time") ?: JSONArray()
        val days = (0 until dayDates.length()).mapNotNull { i ->
            val date = date(dayDates.optString(i)) ?: return@mapNotNull null
            val max = daily.optJSONArray("temperature_2m_max")?.optDouble(i, Double.NaN) ?: Double.NaN
            val min = daily.optJSONArray("temperature_2m_min")?.optDouble(i, Double.NaN) ?: Double.NaN
            if (max.isNaN() || min.isNaN()) return@mapNotNull null
            DayWeather(
                date = date,
                code = daily.optJSONArray("weather_code")?.optInt(i, 0) ?: 0,
                max = max,
                min = min,
                sunrise = daily.optJSONArray("sunrise")?.optString(i)?.let(::dateTime),
                sunset = daily.optJSONArray("sunset")?.optString(i)?.let(::dateTime),
                precipitation = daily.optJSONArray("precipitation_probability_max")?.let { optionalInt(it, i) },
                uvIndex = daily.optJSONArray("uv_index_max")?.optDouble(i, Double.NaN)?.takeIf { !it.isNaN() },
            )
        }
        if (days.isEmpty()) throw WeatherFormatException("daily.time")
        return Forecast(root.optString("timezone", "UTC"), now, hours, days)
    }

    fun places(text: String): List<Place> {
        val results = obj(text).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!o.has("latitude") || !o.has("longitude")) return@mapNotNull null
            Place(
                name = name,
                region = listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() && it != name }.joinToString(", "),
                latitude = o.optDouble("latitude"),
                longitude = o.optDouble("longitude"),
            )
        }
    }

    fun placesToJson(places: List<Place>): String = JSONArray().apply {
        places.forEach { place ->
            put(JSONObject().put("name", place.name).put("region", place.region).put("lat", place.latitude).put("lon", place.longitude))
        }
    }.toString()

    fun placesFromJson(text: String?): List<Place> =
        try {
            val array = JSONArray(text ?: "[]")
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Place(o.optString("name"), o.optString("region"), o.optDouble("lat"), o.optDouble("lon"))
                    .takeIf { it.name.isNotBlank() && !it.latitude.isNaN() && !it.longitude.isNaN() }
            }
        } catch (e: JSONException) {
            emptyList()
        }

    private fun obj(text: String): JSONObject =
        try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw WeatherFormatException("not JSON")
        }

    private fun number(o: JSONObject, key: String): Double {
        val value = o.optDouble(key, Double.NaN)
        if (value.isNaN()) throw WeatherFormatException(key)
        return value
    }

    private fun optionalInt(array: JSONArray, i: Int): Int? =
        if (array.isNull(i)) null else array.optDouble(i, Double.NaN).takeIf { !it.isNaN() }?.toInt()

    private fun dateTime(text: String?): LocalDateTime? =
        try {
            if (text.isNullOrBlank()) null else LocalDateTime.parse(text)
        } catch (e: DateTimeParseException) {
            null
        }

    private fun date(text: String?): LocalDate? =
        try {
            if (text.isNullOrBlank()) null else LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            null
        }
}

class WeatherFormatException(part: String) : Exception("Unexpected weather data: $part")
