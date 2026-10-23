package com.shamil.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shamil.launcher.R
import com.shamil.launcher.ui.clock.ClockStore
import com.shamil.launcher.ui.clock.glanceFrame
import com.shamil.launcher.glance.DeviceStats
import com.shamil.launcher.glance.Weather
import com.shamil.launcher.glance.WeatherSource
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * What the glance line and the stats card show: the weather, the phone's stats, and whether the card is open.
 * Shared, because the line sits under the date while the card lives at the top of the scrolling favourites
 * (23 Oct: with the card under the line, his favourites scrolled up behind it).
 */
class GlanceState(context: Context) {
    var weather by mutableStateOf(WeatherSource.cached(context))
    var hasPermission by mutableStateOf(WeatherSource.hasPermission(context))
    var locationOn by mutableStateOf(WeatherSource.locationOn(context))
    var stats by mutableStateOf(DeviceStats.read(context))
    var open by mutableStateOf(false)
}

/** Keeps the weather and stats fresh, but only while home is on screen ("STARTED"). */
@Composable
fun rememberGlanceState(): GlanceState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { GlanceState(context) }
    // Check the weather every 5 minutes (it only really downloads when the saved one is over 30 minutes old;
    // every minute while there's none yet), and the stats every second while the card is open, so "this phone" is live.
    LaunchedEffect(lifecycleOwner, state.hasPermission) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state.locationOn = WeatherSource.locationOn(context)
                state.weather = WeatherSource.load(context) ?: state.weather
                delay(if (state.weather == null) 60_000L else 5 * 60_000L)
            }
        }
    }
    LaunchedEffect(lifecycleOwner, state.open) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state.stats = withContext(Dispatchers.Default) { DeviceStats.read(context) }
                delay(if (state.open) 1_000L else 60_000L)
            }
        }
    }
    return state
}

/** The glance line under the date: "☁ 17° Partly cloudy · 🔋 82%". Tap it to open or close the stats card. */
@Composable
fun Glance(state: GlanceState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.hasPermission = granted
    }
    // The glance style he picked (Seelie settings → Look → Clock & date): plain, pill, glow or icons only.
    val glanceStyle = remember { ClockStore.get(context) }.glanceStyle
    // What the line shows: both can be switched off in the "Seelie widget" sheet (long-press beside the clock).
    val home = remember { HomePrefs.get(context) }
    Row(
        modifier
            .glanceFrame(glanceStyle)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.open = !state.open }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val w = state.weather
        if (home.showWeather) when {
            w != null -> {
                GlanceIcon(w.icon)
                GlanceText(if (glanceStyle.showsWords) "${w.temp}° ${w.description}" else "${w.temp}°")
                if (home.showBattery) GlanceDot()
            }
            !state.hasPermission -> {
                GlanceIcon(R.drawable.ic_cloud)
                GlanceText(
                    "Tap for weather",
                    Modifier.clickable { askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                )
                if (home.showBattery) GlanceDot()
            }
            // Allowed, but no weather yet: say why instead of showing nothing (his 23 Oct bug).
            !state.locationOn -> {
                GlanceIcon(R.drawable.ic_cloud)
                GlanceText(
                    "Location off",
                    Modifier.clickable { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                )
                if (home.showBattery) GlanceDot()
            }
            else -> {
                GlanceIcon(R.drawable.ic_cloud)
                GlanceText("Weather…")
                if (home.showBattery) GlanceDot()
            }
        }
        if (home.showBattery) {
            GlanceIcon(if (state.stats.charging) R.drawable.ic_bolt else R.drawable.ic_battery_full)
            GlanceText("${state.stats.batteryPercent}%")
        }
    }
}

/** The stats card (weather, next hours, battery / memory / storage rings), unfolding when [GlanceState.open]. */
@Composable
fun GlanceCard(state: GlanceState, modifier: Modifier = Modifier) {
    AnimatedVisibility(state.open, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        // Swallow taps on the card itself, so only a tap AWAY from it closes it.
        Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            StatsCard(state.weather, state.stats)
        }
    }
}

@Composable
private fun StatsCard(weather: Weather?, stats: DeviceStats) {
    val look = LocalLauncherLook.current
    Column(
        Modifier
            .padding(top = 6.dp, end = 8.dp, bottom = 10.dp)
            .fillMaxWidth()
            .background(look.panel, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (weather != null) {
            val context = LocalContext.current
            Row(
                // Tap the weather = open his own weather app (Android keeps its data private, so we can't read it).
                Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    openWeatherApp(context)
                },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    CardText("${weather.temp}°", 32.sp, look.text, FontWeight.Medium)
                    CardText(weather.description, 13.sp, look.textDim)
                }
                weather.hours.forEach { hour ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CardText(hour.hour, 12.sp, look.textDim)
                        Icon(painterResource(hour.icon), contentDescription = null, tint = look.text, modifier = Modifier.size(22.dp))
                        CardText("${hour.temp}°", 14.sp, look.text)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Ring("Battery", stats.batteryPercent, if (stats.charging) "Charging" else null)
            Ring("Memory", stats.memoryUsedPercent, "in use")
            Ring("Storage", stats.storageUsedPercent, "${stats.storageFreeGb} GB free")
        }
    }
}

/** Opens the first installed app with "weather" (or Dutch "weer") in its name or package. Does nothing if there's none. */
private fun openWeatherApp(context: Context) {
    val pm = context.packageManager
    val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    val app = launchable.firstOrNull { info ->
        val name = "${info.loadLabel(pm)} ${info.activityInfo.packageName}".lowercase()
        "weather" in name || Regex("\\bweer\\b").containsMatchIn(name)
    } ?: return
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.activityInfo.packageName, app.activityInfo.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** A round gauge: a faint full circle with the filled part in the accent colour, the % in the middle. */
@Composable
private fun Ring(label: String, percent: Int, sub: String?) {
    val look = LocalLauncherLook.current
    // The ring glides to its new value (and fills up from empty when the card opens), so the live updates feel alive.
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(percent) { shown = percent }
    val fill by animateFloatAsState(shown.coerceIn(0, 100) / 100f, tween(700), label = "ring")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(58.dp)
                .drawBehind {
                    val stroke = 5.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(look.text.copy(alpha = 0.18f), -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    drawArc(look.accent, -90f, 360f * fill, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                },
            contentAlignment = Alignment.Center,
        ) {
            CardText("$percent%", 14.sp, look.text, FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        CardText(label, 13.sp, look.text)
        if (sub != null) CardText(sub, 11.sp, look.textDim)
    }
}

@Composable
private fun GlanceIcon(icon: Int) {
    val look = LocalLauncherLook.current
    Icon(painterResource(icon), contentDescription = null, tint = look.text, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun GlanceText(text: String, modifier: Modifier = Modifier) {
    val look = LocalLauncherLook.current
    Text(text, modifier = modifier, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 15.sp, shadow = look.shadow))
}

@Composable
private fun GlanceDot() {
    val look = LocalLauncherLook.current
    Text("  ·  ", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 15.sp, shadow = look.shadow))
}

@Composable
private fun CardText(text: String, size: TextUnit, color: androidx.compose.ui.graphics.Color, weight: FontWeight = FontWeight.Normal) {
    val look = LocalLauncherLook.current
    Text(text, style = TextStyle(fontFamily = look.font, color = color, fontSize = size, fontWeight = weight))
}
