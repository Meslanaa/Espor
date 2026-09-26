package org.mesos.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WeatherParserTest {

    private val sample = """
        {"latitude":41.0,"longitude":28.98,"utc_offset_seconds":10800,"timezone":"Europe/Istanbul",
         "current":{"time":"2026-09-26T21:15","interval":900,"temperature_2m":21.3,"relative_humidity_2m":70,
                    "apparent_temperature":20.1,"is_day":0,"weather_code":2,"wind_speed_10m":12.4},
         "hourly":{"time":["2026-09-26T20:00","2026-09-26T21:00","2026-09-26T22:00","2026-09-26T23:00"],
                   "temperature_2m":[22.0,21.0,20.5,null],
                   "weather_code":[1,2,3,61],
                   "precipitation_probability":[0,5,null,80],
                   "is_day":[0,0,0,0]},
         "daily":{"time":["2026-09-26","2026-09-27"],
                  "weather_code":[2,61],
                  "temperature_2m_max":[24.0,19.5],
                  "temperature_2m_min":[16.2,14.0],
                  "sunrise":["2026-09-26T06:52","2026-09-27T06:53"],
                  "sunset":["2026-09-26T18:55","2026-09-27T18:53"],
                  "precipitation_probability_max":[10,90],
                  "uv_index_max":[4.5,null]}}
    """.trimIndent()

    @Test
    fun parsesCurrentHoursAndDays() {
        val forecast = WeatherParser.forecast(sample)
        assertEquals("Europe/Istanbul", forecast.timezone)
        assertEquals(21.3, forecast.current.temperature, 0.001)
        assertEquals(20.1, forecast.current.apparent, 0.001)
        assertEquals(70, forecast.current.humidity)
        assertEquals(false, forecast.current.isDay)
        assertEquals(Condition.PARTLY_CLOUDY, Condition.fromCode(forecast.current.code))

        // Hours before the current hour and hours without a temperature are dropped.
        assertEquals(listOf(LocalDateTime.of(2026, 9, 26, 21, 0), LocalDateTime.of(2026, 9, 26, 22, 0)), forecast.hours.map { it.time })
        assertNull(forecast.hours[1].precipitation)
        assertEquals(5, forecast.hours[0].precipitation)

        assertEquals(2, forecast.days.size)
        assertEquals(LocalDate.of(2026, 9, 27), forecast.days[1].date)
        assertEquals(90, forecast.days[1].precipitation)
        assertNull(forecast.days[1].uvIndex)
        assertEquals(LocalDateTime.of(2026, 9, 26, 18, 55), forecast.days[0].sunset)
    }

    @Test(expected = WeatherFormatException::class)
    fun rejectsMissingCurrent() {
        WeatherParser.forecast("""{"hourly":{},"daily":{"time":["2026-09-26"]}}""")
    }

    @Test(expected = WeatherFormatException::class)
    fun rejectsGarbage() {
        WeatherParser.forecast("<html>")
    }

    @Test
    fun parsesPlaces() {
        val text = """
            {"results":[
              {"id":745044,"name":"İstanbul","latitude":41.01384,"longitude":28.94966,"country":"Türkiye","admin1":"İstanbul"},
              {"id":1,"name":"Istanbul","latitude":40.0,"longitude":30.0,"country":"Türkiye","admin1":"Bursa"},
              {"id":2,"name":"","latitude":1.0,"longitude":1.0},
              {"id":3,"name":"NoCoordinates"}
            ],"generationtime_ms":0.5}
        """.trimIndent()
        val places = WeatherParser.places(text)
        assertEquals(2, places.size)
        assertEquals("Türkiye", places[0].region)
        assertEquals("Bursa, Türkiye", places[1].region)
        assertTrue(WeatherParser.places("""{"generationtime_ms":0.2}""").isEmpty())
    }

    @Test
    fun placesRoundTrip() {
        val places = listOf(Place("Ankara", "Türkiye", 39.93, 32.85), Place("Berlin", "Germany", 52.52, 13.41))
        assertEquals(places, WeatherParser.placesFromJson(WeatherParser.placesToJson(places)))
        assertTrue(WeatherParser.placesFromJson("broken").isEmpty())
        assertTrue(WeatherParser.placesFromJson(null).isEmpty())
    }

    @Test
    fun mapsWeatherCodes() {
        assertEquals(Condition.CLEAR, Condition.fromCode(0))
        assertEquals(Condition.FOG, Condition.fromCode(48))
        assertEquals(Condition.THUNDERSTORM, Condition.fromCode(99))
        assertEquals(Condition.SNOW_SHOWERS, Condition.fromCode(86))
        assertTrue(Condition.fromCode(63).isRainy)
        assertTrue(Condition.fromCode(75).isSnowy)
    }
}
