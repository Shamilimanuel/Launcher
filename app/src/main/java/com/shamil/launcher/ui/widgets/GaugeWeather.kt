package com.shamil.launcher.ui.widgets

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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

/** What the gauge tiles need from the weather: humidity in % and the temperature in °C, plus when we got it. */
data class GaugeWeather(val humidity: Int, val temp: Int, val fetchedAt: Long)

/**
 * A small, separate weather fetch just for the gauge tiles (the glance's WeatherSource doesn't ask for humidity).
 * Same idea as WeatherSource: Open-Meteo (free, no key), only a ROUGH location (rounded to ~10 km),
 * and the last answer is saved, so we ask at most every 30 minutes.
 */
object GaugeWeatherSource {
    private const val PREFS = "gauge_weather"
    private const val KEY = "last"
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    /** True when Seelie may use the rough location AND the phone's Location switch is on. Otherwise the tiles show "—". */
    fun canUseLocation(context: Context): Boolean {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val on = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            context.getSystemService(LocationManager::class.java).isLocationEnabled
        return allowed && on
    }

    /** The saved answer from last time (may be old), or null if there is none. */
    fun cached(context: Context): GaugeWeather? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let { parse(it) }

    /** Fresh values if the saved ones are older than 30 minutes; otherwise the saved ones. Null without location. */
    suspend fun load(context: Context): GaugeWeather? {
        if (!canUseLocation(context)) return null
        val saved = cached(context)
        if (saved != null && System.currentTimeMillis() - saved.fetchedAt < MAX_AGE_MS) return saved
        val location = roughLocation(context) ?: return saved
        return withContext(Dispatchers.IO) {
            try {
                val lat = String.format(Locale.US, "%.1f", location.latitude)
                val lon = String.format(Locale.US, "%.1f", location.longitude)
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=relative_humidity_2m,temperature_2m&timezone=auto"
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val body = try {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
                // Save it with the time we got it, so next time we know how old it is.
                val stamped = JSONObject(body).put("fetchedAt", System.currentTimeMillis()).toString()
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, stamped) }
                parse(stamped)
            } catch (e: Exception) {
                saved   // offline or the service is down: keep the last values
            }
        }
    }

    private fun parse(json: String): GaugeWeather? = try {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        GaugeWeather(
            humidity = current.getDouble("relative_humidity_2m").roundToInt(),
            temp = current.getDouble("temperature_2m").roundToInt(),
            fetchedAt = root.optLong("fetchedAt", 0L),
        )
    } catch (e: Exception) {
        null
    }

    /**
     * The phone's last known rough location; if it has none, asks for a fresh one (max 15 s).
     * A copy of WeatherSource's way: Google's "fused" source first, then network, then GPS.
     */
    @SuppressLint("MissingPermission")   // checked in load() (canUseLocation) before calling this
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
