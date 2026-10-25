package com.shamil.launcher.night

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.glance.AgendaSource
import com.shamil.launcher.glance.WeatherSource
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.HomePrefs
import com.shamil.launcher.ui.media.rememberNowPlaying
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/*
 * The night screen (25 Oct): his pages to swipe between, page dots, a thin ✕ to leave, and the night care around them:
 * dimming with the room's light (or after bedtime), the optional deep-red Late mode, and the burn-in drift.
 * Everything here only moves while the screen saver is really on screen ([running]).
 */

@Composable
internal fun NightScreen(running: Boolean, body: FontFamily, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NightStore.get(context) }
    val home = remember { HomePrefs.get(context) }
    val seconds = rememberSlowClock(running)
    val minute = rememberMinute(running)
    val weather = rememberNightWeather(running)
    // Asked again every minute: he may have allowed them in settings while it was off.
    val calendarOk = remember(minute.count) { AgendaSource.hasPermission(context) }
    val weatherOk = remember(minute.count) { WeatherSource.hasPermission(context) }
    val events = rememberNightEvents(running, calendarOk)
    val music = rememberNowPlaying()
    val roomLight = rememberRoomLight(running, store.dim == NightDim.ROOM)
    val data = NightData(seconds, minute, weather, weatherOk, events, calendarOk, home.hideEventTitles, body, live = true)

    // Night care: how bright the picture is, and Late mode's red.
    val nowMin = minute.now.hour * 60 + minute.now.minute
    val late = store.late && inWindow(nowMin, store.lateAt, NightStore.MORNING)
    val level = when {
        late -> 0.42f
        store.dim == NightDim.OFF -> 1f
        store.dim == NightDim.ROOM && roomLight != null -> roomLight
        // Bedtime (or Room light on a phone without a light sensor): dimmer from bedtime until 07:00, a little before.
        inWindow(nowMin, store.bedtime, NightStore.MORNING) -> 0.5f
        inWindow(nowMin, (store.bedtime - 60).mod(24 * 60), store.bedtime) -> 0.75f
        else -> 0.95f
    }
    val veil = animateFloatAsState(1f - level, tween(600), label = "night dim")
    val red = animateFloatAsState(if (late) 1f else 0f, tween(400), label = "late red")

    // Burn-in care: everything drifts a few dp to a new spot every minute, gliding there over 3 s.
    val density = LocalDensity.current
    val dx = animateFloatAsState(with(density) { (cos(minute.count * 0.9) * 7).toFloat().dp.toPx() }, tween(3000), label = "drift x")
    val dy = animateFloatAsState(with(density) { (sin(minute.count * 1.3) * 5).toFloat().dp.toPx() }, tween(3000), label = "drift y")
    val drift = remember(dx, dy) { Drift(dx, dy) }

    val pages = store.shownPages()
    val pager = rememberPagerState { pages.size }
    val bursts = remember { Bursts() }
    val layer = rememberGraphicsLayer()
    val filter = remember { LateFilter() }

    CompositionLocalProvider(LocalNight provides data, LocalMusic provides music, LocalDrift provides drift) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                // A tap anywhere lets out a little sparkle burst (his "press things" sparkles); it never blocks the tap.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (!change.pressed) break
                        }
                        if (!moved) bursts.add(down.position, seconds.floatValue)
                    }
                }
                .drawWithContent {
                    // Late mode: the whole picture turns deep red (gliding in over 0.4 s), then the dimming veil on top.
                    layer.colorFilter = filter.at(red.value)
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                    val v = veil.value
                    if (v > 0.003f) drawRect(Color.Black.copy(alpha = v.coerceIn(0f, 0.9f)))
                },
        ) {
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { pages.getOrNull(it)?.id ?: it.toString() }) { i ->
                NightPageView(pages.getOrElse(i) { NightPage.STANDBY }, store.sky, store.slots)
            }

            // Page dots (the picked one long and glowing), only when there's more than one page.
            if (pages.size > 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    repeat(pages.size) { i -> PageDot(i == pager.currentPage) }
                }
            }

            BurstLayer(bursts, seconds, Modifier.matchParentSize())

            // The way out: a touch doesn't close an interactive screen saver, so a thin ✕ (46dp to hit) does.
            val press = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(46.dp)
                    .pressFeel(press, GoldHi, 23.dp)
                    .clip(CircleShape)
                    .clickable(press, indication = null, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", style = TextStyle(color = GoldEdge.copy(alpha = 0.55f), fontSize = 18.sp))
            }
        }
    }
}

/** One page dot: long and glowing when it's the page on screen (the width springs, the colour glides in 0.4 s). */
@Composable
private fun PageDot(on: Boolean) {
    val w by animateDpAsState(if (on) 22.dp else 7.dp, spring(dampingRatio = 0.5f, stiffness = 400f), label = "dot width")
    val c by animateColorAsState(if (on) GoldHi else GoldHi.copy(alpha = 0.3f), tween(400), label = "dot colour")
    Box(Modifier.width(w).height(7.dp).drawBehind {
        if (on) drawRoundRect(GoldHi.copy(alpha = 0.25f), topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
    }.clip(CircleShape).background(c))
}

/** Late mode's colour: the picture's brightness, all in deep red. [at] glides from none (0) to full red (1). */
private class LateFilter {
    private var last = -1f
    private var filter: ColorFilter? = null

    fun at(f: Float): ColorFilter? {
        if (f <= 0.01f) return null
        if (f == last) return filter
        last = f
        val id = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        // Brightness (0.30 R + 0.59 G + 0.11 B) into red, a touch of it into green and blue for a warm deep red.
        val lum = floatArrayOf(.30f, .59f, .11f)
        val red = FloatArray(20).also { m ->
            for (c in 0..2) { m[c] = lum[c] * 1.2f; m[5 + c] = lum[c] * 0.12f; m[10 + c] = lum[c] * 0.06f }
            m[18] = 1f
        }
        filter = ColorFilter.colorMatrix(ColorMatrix(FloatArray(20) { id[it] + (red[it] - id[it]) * f }))
        return filter
    }
}

/** Little sparkle bursts where he taps: up to four at once, each 0.75 s. Only redraws while one is flying. */
private class Bursts {
    val x = FloatArray(4)
    val y = FloatArray(4)
    val t0 = FloatArray(4) { -10f }
    var version by mutableIntStateOf(0)
    /** True while a burst is flying; only then does the layer follow the clock. */
    var active by mutableStateOf(false)
    private var next = 0

    fun add(at: Offset, t: Float) {
        x[next] = at.x; y[next] = at.y; t0[next] = t
        next = (next + 1) % 4
        version++
        active = true
    }
}

@Composable
private fun BurstLayer(bursts: Bursts, seconds: androidx.compose.runtime.FloatState, modifier: Modifier) {
    // Switch it off again once the newest burst has flown (review, 25 Oct: one tap kept it redrawing all night).
    LaunchedEffect(bursts.version) {
        if (bursts.version == 0) return@LaunchedEffect
        delay(850)
        bursts.active = false
    }
    Box(modifier.graphicsLayer { }.drawBehind {
        if (!bursts.active) return@drawBehind
        // Only read the clock while a burst is flying, so this layer sits still the rest of the time.
        val newest = bursts.t0.max()
        val t = seconds.floatValue
        if (t - newest > 0.8f) return@drawBehind
        val reach = 46.dp.toPx()
        for (b in 0 until 4) {
            val u = (t - bursts.t0[b]) / 0.75f
            if (u < 0f || u > 1f) continue
            val ease = 1f - (1f - u) * (1f - u)
            for (i in 0 until 10) {
                val an = i / 10f * TAU + b
                val d = ease * reach * (0.65f + 0.35f * ((i * 7) % 5) / 4f)
                val px = bursts.x[b] + cos(an) * d
                val py = bursts.y[b] + sin(an) * d
                val s = 1f - 0.8f * u
                stamp(NightSprites.glow, px, py, 14.dp.toPx() * s, 1f - u)
                drawCircle(GoldHi.copy(alpha = 1f - u), 2.dp.toPx() * s, Offset(px, py))
            }
        }
    })
}

/**
 * Settings' little preview (25 Oct): one page as it looks sideways on the charger, shrunk into a card. It's the real
 * page (his sky, cards and music), drawn ~15 times a second instead of ~30 so it stays light; taps don't reach it.
 */
@Composable
internal fun NightPreview(page: NightPage, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { NightStore.get(context) }
    val look = LocalLauncherLook.current
    val home = remember { HomePrefs.get(context) }
    val seconds = rememberSlowClock(true, frameMs = 66L)
    val minute = rememberMinute(true)
    val weather = remember { WeatherSource.cached(context) }
    val calendarOk = remember { AgendaSource.hasPermission(context) }
    val events = rememberNightEvents(true, calendarOk)
    val music = rememberNowPlaying()
    val data = NightData(seconds, minute, weather, WeatherSource.hasPermission(context), events, calendarOk, home.hideEventTitles, look.font, live = false)
    val layer = rememberGraphicsLayer()
    val filter = remember { LateFilter() }
    val red by animateFloatAsState(if (store.late) 1f else 0f, tween(400), label = "preview red")
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(19.5f / 9f).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
        val full = DpSize(844.dp, 390.dp)   // a phone lying sideways
        val scale = maxWidth / full.width
        Box(Modifier.requiredSize(full).graphicsLayer { scaleX = scale; scaleY = scale }.drawWithContent {
            layer.colorFilter = filter.at(red)
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }) {
            CompositionLocalProvider(LocalNight provides data, LocalMusic provides music, LocalDrift provides null) {
                key(page, store.sky) { NightPageView(page, store.sky, store.slots) }
            }
        }
        // Taps stop here (the preview's buttons are only pictures); scrolling the settings page still works.
        Box(Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, indication = null) { })
    }
}
