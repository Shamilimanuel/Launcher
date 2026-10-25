package com.shamil.launcher.night

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.shamil.launcher.R
import com.shamil.launcher.glance.CalEvent
import com.shamil.launcher.notify.NotifyHub
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.media.NowPlaying
import com.shamil.launcher.ui.media.resumeLastMedia
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/*
 * The night screen's cream cards (25 Oct, his house style from the mock-up: cream #F8EFD9 → #EFE1C0, dark ink #171C29,
 * a gold left edge #D3BC8E). Every size is in "u", the mock-up's unit (1 % of the screen's width, ×0.62 sideways), so the
 * cards keep the mock-up's proportions on any phone. Seelie draws them itself: a screen saver can't hold other apps'
 * widgets.
 */

internal val Ink = Color(0xFF171C29)
internal val CreamTop = Color(0xFFF8EFD9)
internal val CreamLow = Color(0xFFEFE1C0)
internal val GoldEdge = Color(0xFFD3BC8E)
internal val GoldHi = Color(0xFFF3D79C)
internal val LabelGold = Color(0xFF8A6D2E)
internal val SubInk = Color(0xFF4A4636)

/** The mock-up's unit for this page (set by the page from the screen's size). */
internal val LocalU = compositionLocalOf { 4.dp }

/** u × [times] as text size. */
@Composable
internal fun usp(times: Float): TextUnit = with(LocalDensity.current) { (LocalU.current * times).toSp() }

@Composable
internal fun u(times: Float): Dp = LocalU.current * times

/**
 * Stagger (his motion): the card floats up ~3u, grows from 96 % and fades in with a springy bounce, [index] × 90 ms after
 * the page appears (the mock-up's "wgin").
 */
@Composable
internal fun Modifier.nightIn(index: Int, extraDelay: Long = 200L): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(extraDelay + index * 90L)
        p.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
    }
    val lift = with(LocalDensity.current) { (LocalU.current * 3f).toPx() }
    return graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * lift
        val s = 0.96f + 0.04f * v
        scaleX = s; scaleY = s
    }
}

/** A cream card: gold left edge, soft drop shadow, sinks and glows under the finger (his press feel). */
@Composable
internal fun CreamCard(index: Int, modifier: Modifier = Modifier, small: Boolean = false, corner: Float = 4f, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(u(corner))
    val press = remember { MutableInteractionSource() }
    val edge = with(LocalDensity.current) { (LocalU.current * 1f).toPx() }
    Row(
        modifier
            .nightIn(index)
            .pressFeel(press, GoldHi, u(corner))
            .shadow(u(2.2f), shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CreamTop, CreamLow)))
            .drawWithContent { drawContent(); drawRect(GoldEdge, size = Size(edge, size.height)) }
            .clickable(press, indication = null) { }
            .padding(start = u(if (small) 2.4f else 3f) + u(1f), end = u(if (small) 2.4f else 3f), top = u(if (small) 2.2f else 2.6f), bottom = u(if (small) 2.2f else 2.6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(u(if (small) 2f else 2.6f)),
        content = content,
    )
}

/** The dark rounded tile with a gold icon (the mock-up's ".wi"). */
@Composable
internal fun InkTile(@DrawableRes icon: Int, side: Float = 9f) {
    Box(Modifier.size(u(side)).clip(RoundedCornerShape(28)).background(Ink), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = GoldHi, modifier = Modifier.size(u(side * 0.58f)))
    }
}

/** Label (small gold capitals), the value, and a line under it. [small] = the trail's chips: the value only. */
@Composable
internal fun RowScope.CardText(label: String, value: String, sub: String?, small: Boolean = false) {
    val body = LocalNight.current?.body
    Column(Modifier.weight(1f)) {
        if (!small) Text(label.uppercase(), maxLines = 1, overflow = TextOverflow.Clip,
            style = TextStyle(fontFamily = body, fontWeight = FontWeight.ExtraBold, color = LabelGold, fontSize = usp(3.4f), letterSpacing = 0.14.em, lineHeight = 1.15.em))
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = body, fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = usp(if (small) 5.2f else 5.4f), lineHeight = 1.15.em))
        if (!small && sub != null) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = body, color = SubInk, fontSize = usp(3.8f), lineHeight = 1.15.em))
    }
}

// ---------------------------------------------------------------------------------------------------------------
// The widget types

/**
 * One StandBy slot (or a trail chip when [small]). Sunrise without weather data shows nothing at all, so the other
 * cards close up.
 */
@Composable
internal fun NightWidgetCard(widget: NightWidget, index: Int, small: Boolean = false, modifier: Modifier = Modifier) {
    val data = LocalNight.current ?: return
    when (widget) {
        NightWidget.WEATHER -> {
            val w = data.weather
            val (value, sub) = when {
                w != null -> (if (small) "${w.temp}° ${w.description.substringAfterLast(' ').lowercase()}" else "${w.temp}° ${w.description.lowercase()}") to
                    (dayOf(data, 0)?.let { "Tonight low ${it.min}°" } ?: "Updated ${ago(w.fetchedAt)}")
                !data.weatherAllowed -> "No weather" to "Allow location in Seelie settings"
                else -> "No weather yet" to "Waiting for the forecast"
            }
            CreamCard(index, modifier, small) {
                InkTile(w?.icon ?: R.drawable.ic_cloud, if (small) 7f else 9f)
                CardText("Weather", value, sub, small)
            }
        }
        NightWidget.EVENT -> {
            val now = System.currentTimeMillis()
            val e = nextEvent(data.events, now)
            val (value, sub) = when {
                !data.calendarAllowed -> (if (small) "Calendar off" else "Calendar off") to "Allow it in Seelie settings"
                e == null -> (if (small) "No events" else "No events today") to "Tomorrow is free too"
                small -> chipTime(e, now) to null
                else -> eventTitle(e, data.hideTitles) to eventWhen(e, now, data.hideTitles)
            }
            CreamCard(index, modifier, small) {
                InkTile(R.drawable.ic_calendar_month, if (small) 7f else 9f)
                CardText("Next event", value, sub, small)
            }
        }
        NightWidget.ALARM -> {
            val alarm = data.minute.alarm
            val (value, sub) = if (alarm != null) hm(alarm) to "rings in ${duration(((alarm - System.currentTimeMillis()) / 60_000L).toInt())}"
            else (if (small) "No alarm" else "No alarm set") to "Nothing rings tomorrow"
            CreamCard(index, modifier, small) {
                InkTile(R.drawable.ic_schedule, if (small) 7f else 9f)
                CardText("Next alarm", value, sub, small)
            }
        }
        NightWidget.BATTERY -> {
            val m = data.minute
            val sub = when {
                !m.charging -> "Not charging"
                m.battery >= 100 -> "Full"
                m.fullIn != null -> "Full in ${duration(m.fullIn)}"
                else -> "Charging"
            }
            CreamCard(index, modifier, small) {
                BatteryRing(m.battery / 100f, m.charging, if (small) 7.6f else 10f)
                CardText(if (m.charging) "Charging" else "Battery", "${m.battery}%", sub, small)
            }
        }
        NightWidget.MUSIC -> MusicSlot(index, small, modifier)
        NightWidget.SUNRISE -> {
            val sun = nextSunrise(data) ?: return
            CreamCard(index, modifier, small) {
                InkTile(R.drawable.ic_sunny, if (small) 7f else 9f)
                CardText("Sunrise", sun.first, sun.second, small)
            }
        }
        NightWidget.TOMORROW -> {
            val day = dayOf(data, 1)
            val first = firstTomorrow(data.events)
            val firstText = first?.let {
                if (data.hideTitles) (if (it.allDay) "First: an all-day event" else "First event ${hm(it.begin)}")
                else "First: ${it.title}" + (if (it.allDay) "" else " ${hm(it.begin)}")
            }
            val value = day?.let { "${it.description}, ${it.max}°" } ?: firstText?.removePrefix("First: ") ?: "Nothing planned"
            val sub = if (day != null) firstText ?: "Nothing planned" else null
            CreamCard(index, modifier, small) {
                InkTile(day?.icon ?: R.drawable.ic_star, if (small) 7f else 9f)
                CardText("Tomorrow", value, sub, small)
            }
        }
    }
}

/** The music slot: the cover, "Now playing", the song, and ⏮ ⏯ ⏭ that really control the player. */
@Composable
private fun MusicSlot(index: Int, small: Boolean, modifier: Modifier) {
    val music = LocalMusic.current
    val context = LocalContext.current
    val access = remember(music != null) { NotifyHub.isEnabled(context) }
    CreamCard(index, modifier, small) {
        ArtTile(music, if (small) 7f else 9f)
        CardText(
            "Now playing",
            music?.title ?: "Nothing playing",
            music?.artist?.ifBlank { null } ?: if (music == null && !access) "Allow notification access" else if (music == null) "Press play to pick up again" else null,
            small,
        )
        if (!small) MediaButtons(music, LabelGold, Ink, glyph = 6f)
    }
}

/**
 * The cream music card under the clock (and the Rainy window's "radio"): cover, song, controls, and the song's progress
 * along the bottom. With nothing playing it says so and ▶ picks up the last app's music.
 */
@Composable
internal fun MusicStrip(index: Int, modifier: Modifier = Modifier) {
    val music = LocalMusic.current
    val shape = RoundedCornerShape(u(5.5f))
    val press = remember { MutableInteractionSource() }
    val edge = with(LocalDensity.current) { (LocalU.current * 1f).toPx() }
    val body = LocalNight.current?.body
    Column(
        modifier
            .nightIn(index, extraDelay = 550L)
            .pressFeel(press, GoldHi, u(5.5f))
            .shadow(u(2.2f), shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CreamTop, CreamLow)))
            .drawWithContent { drawContent(); drawRect(GoldEdge, size = Size(edge, size.height)) }
            .clickable(press, indication = null) { }
            .padding(start = u(4.4f), end = u(3.4f), top = u(3f), bottom = u(3f)),
        verticalArrangement = Arrangement.spacedBy(u(2.4f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u(3f))) {
            ArtTile(music, 11f, corner = 24)
            Column(Modifier.weight(1f)) {
                Text(music?.title ?: "Nothing playing", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = body, fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = usp(4.1f)))
                Text(music?.artist?.ifBlank { null } ?: if (music == null) "Press play to pick up again" else music.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = body, color = Ink.copy(alpha = 0.68f), fontSize = usp(3.7f)))
            }
            MediaButtons(music, LabelGold, Ink, glyph = 6f)
        }
        ProgressBar(music, Ink.copy(alpha = 0.14f), LabelGold)
    }
}

/** The song's progress: a thin rounded bar. */
@Composable
internal fun ProgressBar(music: NowPlaying?, track: Color, fill: Color, modifier: Modifier = Modifier) {
    val f = if (music != null && music.durationMs > 0) (music.positionMs.toFloat() / music.durationMs).coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(maxOf(2.dp, u(1f))).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(fill))
    }
}

/** The cover (shrunk), or the mock-up's gold-and-violet tile when there is none. */
@Composable
internal fun ArtTile(music: NowPlaying?, side: Float, corner: Int = 28) {
    val shape = RoundedCornerShape(corner)
    val art = music?.art
    if (art != null) {
        Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(u(side)).clip(shape))
    } else {
        Box(Modifier.size(u(side)).clip(shape).drawBehind { drawArtFallback() })
    }
}

/** The mock-up's ".art": a soft light top-left over gold → violet → night. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArtFallback() {
    drawRect(Brush.linearGradient(0f to GoldHi, .58f to Color(0xFFB47FDC), 1f to Color(0xFF15123F), start = Offset.Zero, end = Offset(size.width, size.height)))
    drawRect(Brush.radialGradient(listOf(Color(0xFFFFF3CF), Color(0x00FFF3CF)), Offset(size.width * .3f, size.height * .28f), size.minDimension * .6f))
}

/**
 * ⏮ ⏯ ⏭, each a 46dp target. Play/pause glides between ink and gold in 0.4 s (his colour glide). With nothing playing,
 * ▶ presses the phone's "play" key, so the last music app picks up where it stopped.
 */
@Composable
internal fun MediaButtons(music: NowPlaying?, accent: Color, idle: Color, glyph: Float, gap: Dp = 0.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val live = LocalNight.current?.live ?: false
    val playing = music?.isPlaying == true
    val tint by animateColorAsState(if (playing) accent else idle, tween(400), label = "play tint")
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        GlyphButton(Glyph.PREV, accent, glyph, enabled = live && music?.canSkipPrevious == true) { music?.previous() }
        GlyphButton(if (playing) Glyph.PAUSE else Glyph.PLAY, tint, glyph, enabled = live) {
            if (music != null) music.playPause() else resumeLastMedia(context)
        }
        GlyphButton(Glyph.NEXT, accent, glyph, enabled = live && music?.canSkipNext == true) { music?.next() }
    }
}

internal enum class Glyph { PREV, PLAY, PAUSE, NEXT }

/** One control, drawn like the mock-up's icons (24-unit shapes), in a round 46dp target that sinks and glows. */
@Composable
internal fun GlyphButton(glyph: Glyph, color: Color, size: Float, enabled: Boolean, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val side = maxOf(46.dp, u(size) + 12.dp)
    Box(
        Modifier.size(side).pressFeel(press, GoldHi, side / 2).clip(CircleShape)
            .clickable(press, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(u(size))) { drawGlyph(glyph, if (enabled || glyph == Glyph.PLAY || glyph == Glyph.PAUSE) color else color.copy(alpha = color.alpha * 0.45f)) }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val s = size.minDimension / 24f
    fun rect(x: Float, y: Float, w: Float, h: Float) = drawRect(color, Offset(x * s, y * s), Size(w * s, h * s))
    fun tri(vararg p: Float) = drawPath(Path().apply { moveTo(p[0] * s, p[1] * s); lineTo(p[2] * s, p[3] * s); lineTo(p[4] * s, p[5] * s); close() }, color)
    when (glyph) {
        Glyph.PREV -> { rect(7f, 5f, 2f, 14f); tri(20f, 5f, 20f, 19f, 10f, 12f) }
        Glyph.NEXT -> { rect(15f, 5f, 2f, 14f); tri(4f, 5f, 4f, 19f, 14f, 12f) }
        Glyph.PLAY -> tri(7f, 4f, 7f, 20f, 20f, 12f)
        Glyph.PAUSE -> { rect(6f, 4f, 4f, 16f); rect(14f, 4f, 4f, 16f) }
    }
}

/** The battery ring: the charge as a gold arc that slowly pulses while charging, a ⚡ in the middle. */
@Composable
internal fun BatteryRing(level: Float, charging: Boolean, side: Float) {
    val seconds = LocalNight.current?.seconds
    Box(Modifier.size(u(side)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(u(side))) {
            val sw = size.minDimension * 4.6f / 36f
            val r = size.minDimension * 15f / 36f
            val tl = Offset(center.x - r, center.y - r)
            drawCircle(Ink.copy(alpha = 0.16f), r, style = Stroke(sw))
            val t = seconds?.floatValue ?: 0f
            val f = if (charging) 0.5f + 0.5f * sin(2f * PI.toFloat() * t / 2.4f) else 0.5f
            val c = androidx.compose.ui.graphics.lerp(Color(0xFFB8892F), Color(0xFFF0B94A), f)
            drawArc(c, -90f, 360f * level.coerceIn(0f, 1f), false, tl, Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, tint = LabelGold, modifier = Modifier.size(u(side * 0.4f)))
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Words

private val HM = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val HMA = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/** "07:30" (or "7:30 AM" on a 12-hour phone). */
@Composable
internal fun hm(ms: Long): String {
    val h24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(if (h24) HM else HMA)
}

/** "7 h 43 min", "25 min". */
internal fun duration(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

/** "20 min ago". */
private fun ago(ms: Long): String {
    val m = ((System.currentTimeMillis() - ms) / 60_000L).toInt().coerceAtLeast(0)
    return if (m < 1) "just now" else "${duration(m)} ago"
}

/** The next event: a timed one first (an all-day one only if nothing else), from now until the end of tomorrow. */
internal fun nextEvent(events: List<CalEvent>, now: Long): CalEvent? =
    events.firstOrNull { !it.allDay && it.end > now } ?: events.firstOrNull { it.end > now }

private fun eventTitle(e: CalEvent, hide: Boolean) = if (hide) (if (e.allDay) "All-day event" else "Event") else e.title

/** "Today 18:00", "Tomorrow 09:00, room 2.14", "Now · until 20:45". The place only when titles show. */
@Composable
private fun eventWhen(e: CalEvent, now: Long, hide: Boolean): String {
    val today = LocalDate.now()
    val day = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).toLocalDate()
    val dayWord = if (!day.isAfter(today)) "Today" else "Tomorrow"
    val time = when {
        e.allDay -> "$dayWord, all day"
        e.begin <= now -> "Now · until ${hm(e.end)}"
        else -> "$dayWord ${hm(e.begin)}"
    }
    return time + (e.location?.takeIf { !hide }?.let { ", $it" } ?: "")
}

/** A chip's short time: "09:00", "Now", "All day". */
@Composable
private fun chipTime(e: CalEvent, now: Long): String = when {
    e.allDay -> "All day"
    e.begin <= now -> "Now"
    else -> hm(e.begin)
}

/** The first event tomorrow (timed first). */
private fun firstTomorrow(events: List<CalEvent>): CalEvent? {
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = LocalDate.now(zone).plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
    val tomorrow = events.filter { it.begin in start until end || (it.allDay && it.begin <= start && it.end > start) }
    return tomorrow.firstOrNull { !it.allDay } ?: tomorrow.firstOrNull()
}

/** The next sunrise and that day's sunset, from the weather's daily part; null when it has none (the card hides). */
private fun nextSunrise(data: NightData): Pair<String, String>? {
    val now = data.minute.now
    val nowText = "%02d:%02d".format(now.hour, now.minute)
    val today = dayOf(data, 0)?.takeIf { it.sunrise.isNotBlank() }
    val day = if (today != null && nowText < today.sunrise) today else dayOf(data, 1)?.takeIf { it.sunrise.isNotBlank() } ?: return null
    return day.sunrise to (if (day.sunset.isNotBlank()) "Sunset ${if (day === today) "today " else ""}${day.sunset}" else "")
}

/**
 * Today (0) or tomorrow (1) from the weather's daily part, found by its date, not its place in the list: weather saved
 * yesterday would otherwise show yesterday as "today" (review, 25 Oct). Null when the forecast doesn't have that day.
 */
private fun dayOf(data: NightData, plusDays: Long): com.shamil.launcher.glance.DayForecast? {
    val date = data.minute.now.toLocalDate().plusDays(plusDays).toString()   // "2026-10-25", Open-Meteo's own form
    return data.weather?.days?.firstOrNull { it.date == date }
}
