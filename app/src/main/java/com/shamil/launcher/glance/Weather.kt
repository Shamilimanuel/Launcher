package com.shamil.launcher.glance

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.shamil.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** One hour of the forecast: "23" (o'clock), the weather code, day or night, and the temperature. */
data class HourForecast(val hour: String, val code: Int, val isDay: Boolean, val temp: Int) {
    @get:DrawableRes val icon: Int get() = iconFor(code, isDay)
}

/** The weather right now plus the next few hours. [code] is the standard "WMO" weather code Open-Meteo uses. */
data class Weather(val temp: Int, val code: Int, val isDay: Boolean, val hours: List<HourForecast>, val fetchedAt: Long) {
    val description: String get() = describe(code)
    @get:DrawableRes val icon: Int get() = iconFor(code, isDay)
}

/**
 * Weather from Open-Meteo (open-meteo.com): free, no account, no API key.
 * Only a ROUGH location is sent: rounded to 1 decimal, which is about 10 km.
 * The last answer is saved, so the glance shows it straight away and we ask at most every 30 minutes.
 */
object WeatherSource {
    private const val PREFS = "weather"
    private const val KEY = "last"
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Whether the phone's Location switch (quick settings) is on. Without it there's no weather. */
    fun locationOn(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            context.getSystemService(LocationManager::class.java).isLocationEnabled

    /** The saved weather from last time (may be old), or null if there is none. */
    fun cached(context: Context): Weather? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let { parse(it) }

    /** Fresh weather if the saved one is older than 30 minutes; otherwise the saved one. Null if it can't get any. */
    suspend fun load(context: Context): Weather? {
        val saved = cached(context)
        if (saved != null && System.currentTimeMillis() - saved.fetchedAt < MAX_AGE_MS) return saved
        if (!hasPermission(context)) return saved
        val location = roughLocation(context) ?: return saved
        return withContext(Dispatchers.IO) {
            try {
                val lat = String.format(Locale.US, "%.1f", location.latitude)
                val lon = String.format(Locale.US, "%.1f", location.longitude)
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,is_day" +
                        "&hourly=temperature_2m,weather_code,is_day&forecast_hours=4&timezone=auto"
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val body = try {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
                // Save the answer with the time we got it, so next time we know how old it is.
                val stamped = JSONObject(body).put("fetchedAt", System.currentTimeMillis()).toString()
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, stamped) }
                parse(stamped)
            } catch (e: Exception) {
                saved   // offline or the service is down: keep showing the last weather
            }
        }
    }

    private fun parse(json: String): Weather? = try {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val hourly = root.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val days = hourly.getJSONArray("is_day")
        // The first hourly entry is the current hour; show the 3 after it.
        val hours = (1 until times.length()).map { i ->
            HourForecast(
                hour = times.getString(i).substring(11, 13),
                code = codes.getInt(i),
                isDay = days.getInt(i) == 1,
                temp = temps.getDouble(i).roundToInt(),
            )
        }
        Weather(
            temp = current.getDouble("temperature_2m").roundToInt(),
            code = current.getInt("weather_code"),
            isDay = current.getInt("is_day") == 1,
            hours = hours,
            fetchedAt = root.optLong("fetchedAt", 0L),
        )
    } catch (e: Exception) {
        null
    }

    /**
     * The phone's last known rough location; if it has none, asks for a fresh one (max 15 s).
     * 23 Oct: on his Samsung only asking "network" gave nothing, so the weather never showed.
     * Now it asks Google's "fused" source first (it mixes Wi-Fi, cell towers and GPS), then network, then GPS.
     */
    @SuppressLint("MissingPermission")   // checked in load() before calling this
    private suspend fun roughLocation(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java)
        val enabled = manager.getProviders(true)
        val known = enabled
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (known != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return known
        val preferred = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        val provider = preferred.firstOrNull { it in enabled } ?: return null
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                manager.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) }
            }
        }
    }
}

/** Open-Meteo's weather codes, in words. */
private fun describe(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67, in 80..82 -> "Rain"
    in 71..77, 85, 86 -> "Snow"
    in 95..99 -> "Thunderstorm"
    else -> "—"
}

@DrawableRes
private fun iconFor(code: Int, isDay: Boolean): Int = when (code) {
    0, 1 -> if (isDay) R.drawable.ic_sunny else R.drawable.ic_clear_night
    2 -> if (isDay) R.drawable.ic_partly_cloudy_day else R.drawable.ic_partly_cloudy_night
    3 -> R.drawable.ic_cloud
    45, 48 -> R.drawable.ic_foggy
    in 71..77, 85, 86 -> R.drawable.ic_weather_snowy
    in 95..99 -> R.drawable.ic_thunderstorm
    in 51..67, in 80..82 -> R.drawable.ic_rainy
    else -> R.drawable.ic_cloud
}
