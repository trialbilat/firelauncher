package com.custom.firelauncher

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

data class Place(val name: String, val lat: Double, val lon: Double)

data class DayForecast(
    val label: String,
    val kind: WxKind,
    val hi: Int,
    val lo: Int,
    val rainChance: Int
)

data class Weather(
    val place: String,
    val temp: Int,
    val feelsLike: Int,
    val humidity: Int,
    val windMph: Int,
    val windDir: String,
    val pressure: Int,
    val kind: WxKind,
    val description: String,
    val hi: Int,
    val lo: Int,
    val rainChance: Int,
    val sunrise: String,
    val sunset: String,
    val days: List<DayForecast>
)

/** Weather from Open-Meteo (free, no API key). Temperatures in °C, wind in mph. */
object WeatherApi {

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "FireLauncher/1.0")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Rough location from the public IP address (a Fire TV has no GPS). */
    fun locateByIp(): Place? {
        try {
            val j = JSONObject(httpGet("https://ipwho.is/"))
            if (j.optBoolean("success", false)) {
                val name = j.optString("city").ifEmpty { j.optString("region") }
                    .ifEmpty { j.optString("country") }
                return Place(name, j.getDouble("latitude"), j.getDouble("longitude"))
            }
        } catch (e: Exception) {
            // fall through to the backup service
        }
        try {
            val j = JSONObject(httpGet("https://ipapi.co/json/"))
            val lat = j.optDouble("latitude", Double.NaN)
            val lon = j.optDouble("longitude", Double.NaN)
            if (!lat.isNaN() && !lon.isNaN()) {
                return Place(j.optString("city").ifEmpty { "Your location" }, lat, lon)
            }
        } catch (e: Exception) {
            // give up
        }
        return null
    }

    fun geocode(query: String): Place? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(query, "UTF-8") + "&count=1&language=en&format=json"
        val results = JSONObject(httpGet(url)).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val r = results.getJSONObject(0)
        val country = r.optString("country_code")
        val name = r.optString("name") + (if (country.isNotEmpty()) ", $country" else "")
        return Place(name, r.getDouble("latitude"), r.getDouble("longitude"))
    }

    fun fetch(place: Place): Weather {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code," +
            "pressure_msl,wind_speed_10m,wind_direction_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max" +
            "&wind_speed_unit=mph&timezone=auto&forecast_days=6"
        val root = JSONObject(httpGet(url))
        val cur = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")

        val code = cur.getInt("weather_code")
        val isDay = cur.optInt("is_day", 1) == 1

        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxes = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")
        val rains = daily.getJSONArray("precipitation_probability_max")
        val sunrises = daily.getJSONArray("sunrise")
        val sunsets = daily.getJSONArray("sunset")

        val days = ArrayList<DayForecast>()
        for (i in 1 until dates.length()) {
            days.add(
                DayForecast(
                    label = dayLabel(dates.getString(i)),
                    kind = kindFor(codes.optInt(i, 3), true),
                    hi = maxes.optDouble(i, 0.0).roundToInt(),
                    lo = mins.optDouble(i, 0.0).roundToInt(),
                    rainChance = rains.optInt(i, 0)
                )
            )
        }

        return Weather(
            place = place.name,
            temp = cur.getDouble("temperature_2m").roundToInt(),
            feelsLike = cur.getDouble("apparent_temperature").roundToInt(),
            humidity = cur.getInt("relative_humidity_2m"),
            windMph = cur.getDouble("wind_speed_10m").roundToInt(),
            windDir = compass(cur.optDouble("wind_direction_10m", 0.0)),
            pressure = cur.getDouble("pressure_msl").roundToInt(),
            kind = kindFor(code, isDay),
            description = describe(code),
            hi = maxes.optDouble(0, 0.0).roundToInt(),
            lo = mins.optDouble(0, 0.0).roundToInt(),
            rainChance = rains.optInt(0, 0),
            sunrise = sunrises.optString(0).substringAfter('T'),
            sunset = sunsets.optString(0).substringAfter('T'),
            days = days
        )
    }

    private fun dayLabel(iso: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)
            if (date == null) iso else SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            iso
        }
    }

    private fun compass(deg: Double): String {
        val points = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((deg % 360.0) + 360.0 + 22.5) / 45.0).toInt() % 8
        return points[idx]
    }

    fun kindFor(code: Int, isDay: Boolean): WxKind = when (code) {
        0 -> if (isDay) WxKind.SUN else WxKind.MOON
        1, 2 -> if (isDay) WxKind.PARTLY_DAY else WxKind.PARTLY_NIGHT
        3 -> WxKind.CLOUD
        45, 48 -> WxKind.FOG
        in 51..67, in 80..82 -> WxKind.RAIN
        in 71..77, 85, 86 -> WxKind.SNOW
        95, 96, 99 -> WxKind.STORM
        else -> WxKind.CLOUD
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65, 66, 67 -> "Heavy rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Cloudy"
    }
}
