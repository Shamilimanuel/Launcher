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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
import com.shamil.launcher.ui.home.GlanceLook
import com.shamil.launcher.ui.home.StatsCard
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.home.glanceLook
import com.shamil.launcher.ui.home.homeChip
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
    // Its look comes from his home style (Look → Home style): plain, pill or glow, size and colour.
    val gl = glanceLook(HomeStyleStore.get(context).style, LocalLauncherLook.current)
    // What the line shows: both can be switched off in the "Seelie widget" sheet (long-press beside the clock).
    val home = remember { HomePrefs.get(context) }
    Row(
        modifier
            .then(if (gl.chip) Modifier.homeChip(LocalLauncherLook.current) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.open = !state.open }
            .padding(vertical = if (gl.chip) 0.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      CompositionLocalProvider(LocalGlanceLook provides gl) {
        val w = state.weather
        if (home.showWeather) when {
            w != null -> {
                GlanceIcon(w.icon)
                GlanceText("${w.temp}° ${w.description}")
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
}

/** The stats card (weather, next hours, battery / memory / storage rings), unfolding when [GlanceState.open]. */
@Composable
fun GlanceCard(state: GlanceState, modifier: Modifier = Modifier) {
    AnimatedVisibility(state.open, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        // Swallow taps on the card itself, so only a tap AWAY from it closes it.
        Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            // The customisable stats card (Seelie settings → Home screen → Stats card); its card matches his home style.
            val context = LocalContext.current
            StatsCard(state.weather, state.stats, Modifier.padding(top = 6.dp, end = 8.dp, bottom = 10.dp), onWeatherTap = { openWeatherApp(context) })
        }
    }
}

/** Opens the first installed app with "weather" (or Dutch "weer") in its name or package. Does nothing if there's none. */
internal fun openWeatherApp(context: Context) {
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

@Composable
private fun GlanceIcon(icon: Int) {
    val gl = LocalGlanceLook.current
    Icon(painterResource(icon), contentDescription = null, tint = gl.color, modifier = Modifier.size((gl.size.value * 1.2f).dp))
    Spacer(Modifier.width(6.dp))
}

/** How the glance line looks right now (from his home style), handed to its pieces. */
private val LocalGlanceLook = staticCompositionLocalOf { GlanceLook(15.sp, Color.White, FontWeight.SemiBold, null, false) }

@Composable
private fun GlanceText(text: String, modifier: Modifier = Modifier) {
    val look = LocalLauncherLook.current
    val gl = LocalGlanceLook.current
    Text(text, modifier = modifier, style = TextStyle(fontFamily = look.font, color = gl.color, fontSize = gl.size, fontWeight = gl.weight, shadow = gl.shadow))
}

@Composable
private fun GlanceDot() {
    val look = LocalLauncherLook.current
    val gl = LocalGlanceLook.current
    Text("  ·  ", style = TextStyle(fontFamily = look.font, color = gl.color, fontSize = gl.size, shadow = gl.shadow))
}

@Composable
private fun CardText(text: String, size: TextUnit, color: androidx.compose.ui.graphics.Color, weight: FontWeight = FontWeight.Normal) {
    val look = LocalLauncherLook.current
    Text(text, style = TextStyle(fontFamily = look.font, color = color, fontSize = size, fontWeight = weight))
}
