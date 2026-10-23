package com.shamil.launcher.ui.home

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.shamil.launcher.glance.DeviceStats
import com.shamil.launcher.glance.Weather
import com.shamil.launcher.ui.theme.LauncherLook
import com.shamil.launcher.ui.theme.LocalLauncherLook
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/*
 * The stats card (tap the line under the date), customisable (his ask, 23 Oct): which rings, the weather part,
 * the ring style, and its card follows his home style so it matches the widget card.
 */

/** One stat the card can show. [id] is saved, never rename it. */
enum class StatKind(val id: String, val title: String) {
    BATTERY("battery", "Battery"),
    MEMORY("memory", "Memory"),
    STORAGE("storage", "Storage"),
    TEMPERATURE("temp", "Battery temp"),
    UPTIME("uptime", "Uptime"),
    WIFI("wifi", "Wi-Fi");

    companion object {
        fun byId(id: String): StatKind? = entries.firstOrNull { it.id == id }
    }
}

/** His stats card choices. */
data class StatsStyle(
    val stats: List<StatKind> = listOf(StatKind.BATTERY, StatKind.MEMORY, StatKind.STORAGE),
    val showWeather: Boolean = true,
    /** How many hours of forecast: 3, 5 or 8. */
    val hours: Int = 3,
    val fahrenheit: Boolean = false,
    /** "circle", "gauge" (¾, like the widget) or "bars". */
    val ringStyle: String = "circle",
    /** "thin", "normal" or "thick". */
    val thickness: String = "normal",
    /** The number "inside" the ring or "under" it. */
    val numberPlace: String = "inside",
)

/** Remembers the stats card choices (saved on the phone, Compose state, one shared store). */
class StatsStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("stats_card", Context.MODE_PRIVATE)

    var style by mutableStateOf(load())
        private set

    fun change(edit: (StatsStyle) -> StatsStyle) {
        style = edit(style)
        prefs.edit {
            putString(KEY, JSONObject().apply {
                put("stats", JSONArray(style.stats.map { it.id }))
                put("showWeather", style.showWeather); put("hours", style.hours); put("fahrenheit", style.fahrenheit)
                put("ringStyle", style.ringStyle); put("thickness", style.thickness); put("numberPlace", style.numberPlace)
            }.toString())
        }
    }

    /** Turns a stat on (at the end) or off. */
    fun toggle(kind: StatKind) = change { s -> s.copy(stats = if (kind in s.stats) s.stats - kind else s.stats + kind) }

    private fun load(): StatsStyle = try {
        val o = JSONObject(prefs.getString(KEY, null) ?: return StatsStyle())
        val list = o.optJSONArray("stats")
        StatsStyle(
            stats = if (list == null) StatsStyle().stats else (0 until list.length()).mapNotNull { StatKind.byId(list.getString(it)) },
            showWeather = o.optBoolean("showWeather", true),
            hours = o.optInt("hours", 3),
            fahrenheit = o.optBoolean("fahrenheit", false),
            ringStyle = o.optString("ringStyle", "circle"),
            thickness = o.optString("thickness", "normal"),
            numberPlace = o.optString("numberPlace", "inside"),
        )
    } catch (e: Exception) {
        StatsStyle()
    }

    companion object {
        private const val KEY = "style"
        @Volatile private var instance: StatsStore? = null

        fun get(context: Context): StatsStore =
            instance ?: synchronized(this) { instance ?: StatsStore(context.applicationContext).also { instance = it } }
    }
}

/**
 * A card that looks like his widget card (his home style: none / glass / solid / outline, see-through level,
 * corners, border), so the stats card and the widgets match. With no card, a soft panel stays so it's readable.
 */
internal fun Modifier.homeCard(hs: HomeStyle, look: LauncherLook): Modifier {
    val shape = RoundedCornerShape(hs.cardRadius.dp)
    val opacity = if (hs.cardStyle == "none") 0.35f else (hs.cardOpacity / 100f).coerceAtLeast(0.2f)
    val fill = when (hs.cardStyle) {
        "solid" -> Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = opacity), look.sheet.copy(alpha = opacity)))
        "outline" -> Brush.verticalGradient(listOf(Color(0xFF0E1126).copy(alpha = opacity), Color(0xFF0E1126).copy(alpha = opacity)))
        else -> Brush.verticalGradient(listOf(lerp(look.panel, Color.White, 0.06f).copy(alpha = opacity), look.panel.copy(alpha = opacity)))
    }
    val border = when {
        hs.cardStyle == "outline" -> if (hs.cardAccent == "gold") Color(0xFFD3BC8E).copy(alpha = 0.8f) else look.accent.copy(alpha = 0.7f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    return this
        .clip(shape)
        .background(fill, shape)
        .then(if (hs.cardBorder || hs.cardStyle == "outline") Modifier.border(1.dp, border, shape) else Modifier)
}

/**
 * The stats card: the weather now and the next hours (optional), then his chosen stats as circles, ¾ gauges or bars.
 * [onWeatherTap] opens his own weather app.
 */
@Composable
fun StatsCard(weather: Weather?, stats: DeviceStats, modifier: Modifier = Modifier, onWeatherTap: () -> Unit = {}) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val hs = HomeStyleStore.get(context).style
    val st = StatsStore.get(context).style
    val ringColour = if (hs.cardAccent == "gold") Gold else look.accent
    fun temp(c: Int) = if (st.fahrenheit) "${(c * 9 / 5f + 32).roundToInt()}°" else "$c°"
    Column(
        modifier
            .fillMaxWidth()
            .homeCard(hs, look)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (st.showWeather && weather != null) {
            Row(
                Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onWeatherTap),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    CardText(temp(weather.temp), 32.sp, look.text, FontWeight.Medium)
                    CardText(weather.description, 13.sp, look.textDim)
                }
                weather.hours.take(st.hours).forEach { hour ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CardText(hour.hour, 12.sp, look.textDim)
                        Icon(painterResource(hour.icon), contentDescription = null, tint = look.text, modifier = Modifier.size(if (st.hours > 5) 18.dp else 22.dp))
                        CardText(temp(hour.temp), if (st.hours > 5) 12.sp else 14.sp, look.text)
                    }
                }
            }
            if (st.stats.isNotEmpty()) Spacer(Modifier.height(14.dp))
        }
        if (st.ringStyle == "bars") {
            st.stats.forEach { kind -> StatBar(kind, stats, ringColour, st) }
        } else {
            // Three per row, so up to six stats fit nicely.
            st.stats.chunked(3).forEachIndexed { i, row ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    row.forEach { kind -> StatRing(kind, stats, ringColour, st) }
                }
            }
        }
    }
}

/** What a stat shows: how full its ring is (0–1), its value, and a small line under it. */
private data class StatValue(val fill: Float, val value: String, val sub: String?)

private fun valueOf(kind: StatKind, s: DeviceStats, fahrenheit: Boolean): StatValue = when (kind) {
    StatKind.BATTERY -> StatValue(s.batteryPercent / 100f, "${s.batteryPercent}%", if (s.charging) "Charging" else null)
    StatKind.MEMORY -> StatValue(s.memoryUsedPercent / 100f, "${s.memoryUsedPercent}%", "in use")
    StatKind.STORAGE -> StatValue(s.storageUsedPercent / 100f, "${s.storageUsedPercent}%", "${s.storageFreeGb} GB free")
    // The ring runs from 20 °C (empty) to 50 °C (full): around 40 °C is warm for a phone.
    StatKind.TEMPERATURE -> StatValue(((s.batteryTempC - 20f) / 30f), if (fahrenheit) "${(s.batteryTempC * 9 / 5 + 32).roundToInt()}°" else "${s.batteryTempC.roundToInt()}°", "battery")
    // A day of uptime fills the ring.
    StatKind.UPTIME -> StatValue(s.uptimeHours / 24f, if (s.uptimeHours >= 48) "${s.uptimeHours / 24}d" else "${s.uptimeHours}h", "since restart")
    StatKind.WIFI -> s.wifiPercent?.let { StatValue(it / 100f, "$it%", "signal") } ?: StatValue(0f, "—", "no Wi-Fi")
}

private fun strokeOf(st: StatsStyle) = when (st.thickness) { "thin" -> 3.dp; "thick" -> 8.dp; else -> 5.dp }

/** One stat as a full circle or a ¾ gauge, the number inside or under it, gliding to new values. */
@Composable
private fun StatRing(kind: StatKind, s: DeviceStats, colour: Color, st: StatsStyle) {
    val look = LocalLauncherLook.current
    val v = valueOf(kind, s, st.fahrenheit)
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(v.fill) { target = v.fill.coerceIn(0f, 1f) }
    val fill by animateFloatAsState(target, tween(700), label = "stat")
    val gauge = st.ringStyle == "gauge"
    val start = if (gauge) 135f else -90f
    val sweep = if (gauge) 270f else 360f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(58.dp)
                .drawBehind {
                    val stroke = strokeOf(st).toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(look.text.copy(alpha = 0.18f), start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (fill > 0.005f) drawArc(colour, start, sweep * fill, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                },
            contentAlignment = Alignment.Center,
        ) {
            if (st.numberPlace == "inside") CardText(v.value, 14.sp, look.text, FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        if (st.numberPlace == "under") CardText(v.value, 15.sp, look.text, FontWeight.Bold)
        CardText(kind.title, 13.sp, look.text)
        v.sub?.let { CardText(it, 11.sp, look.textDim) }
    }
}

/** One stat as a bar: name and value on one line, a rounded bar under it. */
@Composable
private fun StatBar(kind: StatKind, s: DeviceStats, colour: Color, st: StatsStyle) {
    val look = LocalLauncherLook.current
    val v = valueOf(kind, s, st.fahrenheit)
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(v.fill) { target = v.fill.coerceIn(0f, 1f) }
    val fill by animateFloatAsState(target, tween(700), label = "bar")
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            CardText(kind.title, 13.sp, look.text, FontWeight.SemiBold)
            v.sub?.let { Spacer(Modifier.width(6.dp)); CardText(it, 11.sp, look.textDim) }
            Spacer(Modifier.weight(1f))
            CardText(v.value, 13.sp, look.text, FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        val h = strokeOf(st)
        Box(Modifier.fillMaxWidth().height(h).background(look.text.copy(alpha = 0.18f), CircleShape)) {
            Box(Modifier.fillMaxWidth(fill.coerceAtLeast(0.001f)).height(h).background(colour, CircleShape))
        }
    }
}

@Composable
private fun CardText(text: String, size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal) {
    val look = LocalLauncherLook.current
    Text(text, style = TextStyle(fontFamily = look.font, color = color, fontSize = size, fontWeight = weight))
}
