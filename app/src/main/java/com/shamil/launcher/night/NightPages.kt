package com.shamil.launcher.night

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.shamil.launcher.notify.NotifyHub
import com.shamil.launcher.ui.theme.Cinzel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The night screen's pages (25 Oct, his picks from the mock-up): StandBy, Seelie trail, Rainy window, Music, Photo and
 * Just the clock. Each page stands on its own scene and lays itself out sideways (the charger's usual way) or upright.
 * Everything drifts a few dp every minute against burn-in (see NightScreen).
 */

private val ClockGold = Color(0xFFFFE7B0)
private val DateGold = Color(0xFFD3BC8E)
private val NightText = Color(0xFFF1EBDD)

/** The burn-in drift, in pixels, read only while drawing (so moving never rebuilds the page). */
internal class Drift(val x: State<Float>, val y: State<Float>)
internal val LocalDrift = compositionLocalOf<Drift?> { null }

/** One page. [sky] is his chosen sky (StandBy, Music, Just the clock); the trail and the window have their own. */
@Composable
internal fun NightPageView(page: NightPage, sky: NightSky, slots: List<NightWidget>) {
    when (page) {
        NightPage.STANDBY -> StandByPage(sky, slots)
        NightPage.TRAIL -> TrailPage()
        NightPage.WINDOW -> WindowPage()
        NightPage.MUSIC -> MusicPage(sky)
        NightPage.PHOTO -> PhotoPage()
        NightPage.CLOCK -> ClockPage(sky)
    }
}

/**
 * The frame every page shares: works out the mock-up's unit u for this screen (1 % of the width, ×0.62 sideways),
 * draws the scene, then the content inside the drift with the page's padding and its three little twinkles.
 */
@Composable
private fun PageFrame(
    scene: SceneKind?,
    background: @Composable BoxScope.() -> Unit = {},
    sceneSetup: (Scene) -> Unit = {},
    twinkles: Boolean = true,
    content: @Composable BoxScope.(land: Boolean) -> Unit,
) {
    val data = LocalNight.current ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val land = maxWidth > maxHeight
        val unit = if (land) maxWidth * 0.0062f else maxWidth * 0.01f
        CompositionLocalProvider(LocalU provides unit) {
            if (scene != null) SceneLayer(scene, data.seconds, Modifier.fillMaxSize(), sceneSetup)
            background()
            val drift = LocalDrift.current
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = drift?.x?.value ?: 0f; translationY = drift?.y?.value ?: 0f }
                    .padding(horizontal = maxOf(unit * 6f, if (land) 44.dp else 0.dp), vertical = unit * (if (land) 4f else 6f)),
            ) {
                content(land)
                if (twinkles) Twinkles(Modifier.matchParentSize())
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// The pages

/** StandBy: clock + date + wisp, his three widget slots, music under the clock, battery stars along the top. */
@Composable
private fun StandByPage(sky: NightSky, slots: List<NightWidget>) {
    val data = LocalNight.current ?: return
    val music = LocalMusic.current
    val stars = remember(sky) { BatteryStars(shooting = sky !in setOf(NightSky.CONSTELLATION, NightSky.STARFALL, NightSky.RAIN)) }
    val showMusic = music != null && NightWidget.MUSIC !in slots
    PageFrame(
        scene = sky.scene(),
        background = {
            Box(Modifier.fillMaxSize().graphicsLayer { }.drawBehind {
                with(stars) { paint(data.seconds.floatValue, data.minute.battery / 100f, data.minute.charging) }
            })
        },
    ) { land ->
        if (land) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.05f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { ClockBlock(30f, wisp = true) }
                    if (showMusic) MusicStrip(3, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(u(5f)))
                Slots(slots, Modifier.weight(1f).widthIn(max = 420.dp))
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(u(8f)))
                ClockBlock(30f, wisp = true)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Slots(slots, Modifier.fillMaxWidth().widthIn(max = 460.dp)) }
                if (showMusic) MusicStrip(3, Modifier.fillMaxWidth().widthIn(max = 460.dp))
                Spacer(Modifier.height(u(4f)))
            }
        }
    }
}

/**
 * The three slots. A full card is about 19.7u tall; when three don't fit the height (a short screen sideways), the
 * cards shrink together instead of spilling over.
 */
@Composable
private fun Slots(slots: List<NightWidget>, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val unit = LocalU.current
        val need = unit * (19.7f * slots.size + 2.4f * (slots.size - 1))
        val fit = if (maxHeight < need) unit * (maxHeight / need) else unit
        CompositionLocalProvider(LocalU provides fit) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(u(2.4f), Alignment.CenterVertically)) {
                slots.forEachIndexed { i, w -> NightWidgetCard(w, i, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** The trail: the orb wanders, then sleeps just above the clock; four small chips along the bottom. */
@Composable
private fun TrailPage() {
    val spot = remember { RestSpot() }
    // The settings preview is shrunk, so screen positions don't match the scene's: it keeps the mock-up's resting spot.
    val live = LocalNight.current?.live == true
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The z letters: the mock-up's 9k and 6k, where k = the short side / 260.
        val kPx = with(density) { min(maxWidth.toPx(), maxHeight.toPx()) / 260f }
        val zBig = remember(kPx) { measurer.measure("z", TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = with(density) { (9 * kPx).toSp() })) }
        val zSmall = remember(kPx) { measurer.measure("z", TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = with(density) { (6 * kPx).toSp() })) }
        val gap = with(density) { 14 * kPx }
        PageFrame(
            scene = SceneKind.TRAIL,
            sceneSetup = { s ->
                (s as? TrailScene)?.apply {
                    rest = { if (!live) null else spot.clock?.let { c -> Offset(c.center.x - spot.scene.x, c.top - spot.scene.y - gap) } }
                    this.zBig = zBig; this.zSmall = zSmall
                }
            },
            background = { Box(Modifier.fillMaxSize().onGloballyPositioned { spot.scene = it.boundsInRoot().topLeft }) },
        ) { land ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ClockBlock(if (land) 40f else 34f, wisp = false, onClock = { spot.clock = it })
                }
                val chips = listOf(NightWidget.WEATHER, NightWidget.ALARM, NightWidget.EVENT, NightWidget.BATTERY)
                if (land) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u(2.4f))) {
                        chips.forEachIndexed { i, w -> NightWidgetCard(w, i, small = true, modifier = Modifier.weight(1f)) }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(u(2.4f))) {
                        chips.chunked(2).forEachIndexed { r, pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u(2.4f))) {
                                pair.forEachIndexed { i, w -> NightWidgetCard(w, r * 2 + i, small = true, modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(u(4f)))
                }
            }
        }
    }
}

/** Where the trail's orb rests: the scene's and the clock's places on screen (plain fields: the scene reads them each frame). */
private class RestSpot {
    var scene = Offset.Zero
    var clock: Rect? = null
}

/** Rainy window: rain on the glass over the city, a steaming mug; weather + alarm, and music like a little radio. */
@Composable
private fun WindowPage() {
    PageFrame(scene = SceneKind.WINDOW, twinkles = false) { land ->
        if (land) {
            // Above the window sill (9.5 % of the height).
            Row(Modifier.fillMaxSize().padding(bottom = u(5f)), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.05f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ClockBlock(28f, wisp = true, clockColor = Color(0xFFF4EBD6), dateColor = Color(0xFFC7D3E0))
                    }
                    MusicStrip(2, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(u(5f)))
                Column(Modifier.weight(1f).widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(u(2.4f))) {
                    NightWidgetCard(NightWidget.WEATHER, 0, modifier = Modifier.fillMaxWidth())
                    NightWidgetCard(NightWidget.ALARM, 1, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(bottom = u(19f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(u(3f))) {
                Spacer(Modifier.height(u(8f)))
                ClockBlock(28f, wisp = true, clockColor = Color(0xFFF4EBD6), dateColor = Color(0xFFC7D3E0))
                Spacer(Modifier.weight(1f))
                NightWidgetCard(NightWidget.WEATHER, 0, modifier = Modifier.fillMaxWidth())
                NightWidgetCard(NightWidget.ALARM, 1, modifier = Modifier.fillMaxWidth())
                MusicStrip(2, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Music: the big cover (sharp, from the player), title, artist, the controls and the progress. */
@Composable
private fun MusicPage(sky: NightSky) {
    val music = LocalMusic.current
    val context = LocalContext.current
    val latest by rememberUpdatedState(music)
    // The sharp cover, loaded off the main thread once per song (the shared one is shrunk for the home card).
    val big by produceState<ImageBitmap?>(null, music?.art) {
        value = latest?.let { m -> withContext(Dispatchers.Default) { m.bigArt(context, 1024) } } ?: latest?.art
    }
    val access = remember(music != null) { NotifyHub.isEnabled(context) }
    PageFrame(scene = sky.scene()) { land ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = if (land) minOf(u(54f), maxHeight * 0.86f) else minOf(u(54f), maxHeight * 0.45f)
            val text: @Composable (Boolean) -> Unit = { center ->
                Column(Modifier.widthIn(max = u(if (land) 70f else 80f)), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(u(2.4f))) {
                    Text(music?.title ?: "Nothing playing", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, color = Color(0xFFF6DFA8), fontSize = usp(8f)))
                    val sub = when {
                        music != null -> listOf(music.artist, music.appLabel).filter { it.isNotBlank() }.joinToString(" · ")
                        !access -> "Allow notification access in Seelie settings to see your music"
                        else -> "Press play to pick up where you left off"
                    }
                    Text(sub, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = LocalNight.current?.body, color = NightText.copy(alpha = 0.7f), fontSize = usp(4.4f)))
                    MediaButtons(music, GoldHi, GoldHi.copy(alpha = 0.8f), glyph = 10f, gap = u(3f), modifier = Modifier.padding(vertical = u(1f)))
                    ProgressBar(music, Color.White.copy(alpha = 0.16f), GoldHi, Modifier.widthIn(max = u(70f)))
                }
            }
            if (land) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u(9f), Alignment.CenterHorizontally)) {
                    BigArt(big, music?.artColor, side, Modifier.nightIn(0))
                    Box(Modifier.nightIn(1)) { text(false) }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(u(5f), Alignment.CenterVertically)) {
                    BigArt(big, music?.artColor, side, Modifier.nightIn(0))
                    Box(Modifier.nightIn(1)) { text(true) }
                }
            }
        }
    }
}

/** The big cover with a soft glow in its own colour (violet for the fallback tile, like the mock-up). */
@Composable
private fun BigArt(art: ImageBitmap?, glow: Color?, side: Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(12)
    val g = glow ?: Color(0xFFB47FDC)
    Box(modifier.size(side).drawBehind {
        drawCircle(Brush.radialGradient(listOf(g.copy(alpha = 0.45f), g.copy(alpha = 0f)), center, size.minDimension * 0.85f), size.minDimension * 0.85f)
    }) {
        if (art != null) Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(shape))
        else Box(Modifier.fillMaxSize().clip(shape).drawBehind { drawArtFallback() })
    }
}

/**
 * Photo: his one photo, filling the screen, slowly drifting, zooming and breathing (so no part stays put long enough
 * to burn in), with the clock and date at the bottom left over a soft shade.
 */
@Composable
private fun PhotoPage() {
    val context = LocalContext.current
    val store = remember { NightStore.get(context) }
    val data = LocalNight.current ?: return
    val photo by produceState<ImageBitmap?>(null, store.photoStamp) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = store.photoFile
                if (!file.exists()) return@runCatching null
                val metrics = context.resources.displayMetrics
                val target = max(metrics.widthPixels, metrics.heightPixels)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    PageFrame(
        scene = if (photo == null) SceneKind.CONSTEL else null,
        background = {
            photo?.let { img ->
                Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer {
                    val t = data.seconds.floatValue
                    val zoom = 1.12f + 0.05f * sin(2f * PI.toFloat() * t / 180f)
                    scaleX = zoom; scaleY = zoom
                    translationX = sin(2f * PI.toFloat() * t / 240f) * size.width * 0.03f
                    translationY = cos(2f * PI.toFloat() * t / 300f) * size.height * 0.02f
                    alpha = 0.84f + 0.16f * (0.5f + 0.5f * sin(2f * PI.toFloat() * t / 90f))
                })
            }
            // The shade under the clock (the mock-up's: dark at the bottom, gone by 45 %).
            Box(Modifier.fillMaxSize().drawWithCache {
                val shade = Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))
                onDrawBehind { drawRect(shade) }
            })
        },
    ) { land ->
        Box(Modifier.fillMaxSize().padding(bottom = u(if (land) 2f else 3f)), contentAlignment = Alignment.BottomStart) {
            Column(Modifier.nightIn(0)) { ClockBlock(15f, wisp = false, center = false) }
        }
    }
}

/** Just the clock: as big as it fits, a little softer. */
@Composable
private fun ClockPage(sky: NightSky) {
    PageFrame(scene = sky.scene()) { land ->
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.92f }, contentAlignment = Alignment.Center) {
            ClockBlock(if (land) 54f else 38f, wisp = false)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Clock, date, wisp, twinkles

/**
 * The big gold Cinzel clock (size [ck] in u, shrunk to fit its space) with the breathing colon, and the date in gold
 * small capitals under it, with the Seelie wisp beside it when [wisp]. [onClock] = where the clock is on screen.
 */
@Composable
private fun ClockBlock(
    ck: Float, wisp: Boolean, clockColor: Color = ClockGold, dateColor: Color = DateGold, center: Boolean = true,
    onClock: ((Rect) -> Unit)? = null,
) {
    val data = LocalNight.current ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val unit = LocalU.current
    val now = data.minute.now
    val h24 = android.text.format.DateFormat.is24HourFormat(context)
    val hour = if (h24) "%02d".format(now.hour) else (if (now.hour % 12 == 0) 12 else now.hour % 12).toString()
    val date = remember(now.toLocalDate()) {
        now.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.getDefault())).uppercase(Locale.getDefault())
    }
    BoxWithConstraints(contentAlignment = if (center) Alignment.Center else Alignment.BottomStart) {
        val boxWidth = maxWidth
        // A Cinzel "12:45" is about 3.3 times as wide as its size, and the date under it takes about 9u: the clock
        // never grows past its space, either way.
        val tall = if (maxHeight.value.isFinite()) (maxHeight - unit * 9.5f) / 1.05f else unit * ck
        val size = minOf(unit * ck, boxWidth / 3.3f, tall).coerceAtLeast(unit * 8f)
        val fs = with(density) { size.toSp() }
        val glow = with(density) { (unit * 5f).toPx() }
        val style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = clockColor, fontSize = fs, lineHeight = fs * 1.05f,
            fontFeatureSettings = "tnum", shadow = Shadow(GoldHi.copy(alpha = 0.55f), blurRadius = glow))
        Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
            Row(
                Modifier.nightIn(0, extraDelay = 0L).then(if (onClock != null) Modifier.onGloballyPositioned { onClock(it.boundsInRoot()) } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(hour, style = style, maxLines = 1)
                Text(":", style = style, maxLines = 1, modifier = Modifier.padding(horizontal = with(density) { (fs * 0.03f).toDp() }).graphicsLayer {
                    // The colon breathes: 0.22 → 1 → 0.22 every 2.6 s (the mock-up's "breathe").
                    alpha = 0.22f + 0.78f * (0.5f - 0.5f * cos(2f * PI.toFloat() * data.seconds.floatValue / 2.6f))
                    translationY = -0.05f * fs.toPx()
                })
                Text("%02d".format(now.minute), style = style, maxLines = 1)
            }
            // The date: as big as the mock-up's, smaller when a long one wouldn't fit.
            val wispSide = unit * 4.8f
            val room = boxWidth - (if (wisp) wispSide + unit * 3f else 0.dp)
            val dateSize = minOf(unit * 4.4f, room / (date.length * 0.98f))
            Row(Modifier.padding(top = unit * 3.4f).nightIn(1, extraDelay = 80L), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(unit * 3f)) {
                if (wisp) Wisp(wispSide)
                Text(date, maxLines = 1, style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, color = dateColor,
                    fontSize = with(density) { dateSize.toSp() }, letterSpacing = 0.2.em))
            }
        }
    }
}

/**
 * The little Seelie wisp by the date: a soft gold light that breathes slowly while it sleeps (a "z" floats up now and
 * then), and pulses faster, awake, while music plays.
 */
@Composable
private fun Wisp(side: Dp) {
    val data = LocalNight.current ?: return
    val playing = LocalMusic.current?.isPlaying == true
    val period = if (playing) 2.2f else 5f
    val density = LocalDensity.current
    Box(Modifier.size(side)) {
        Box(Modifier.size(side).graphicsLayer {
            val p = 0.5f - 0.5f * cos(2f * PI.toFloat() * data.seconds.floatValue / period)
            val s = 0.86f + 0.26f * p
            scaleX = s; scaleY = s
            alpha = 0.7f + 0.3f * p
        }.drawWithCache {
            val r = size.minDimension / 2
            val mid = Offset(size.width / 2, size.height / 2)
            val halo = Brush.radialGradient(listOf(GoldHi.copy(alpha = 0.55f), GoldHi.copy(alpha = 0f)), mid, r * 1.9f)
            val core = Brush.radialGradient(
                0f to Color.White, 0.12f to Color.White, 0.24f to Color(0xFFFFF0C0), 0.52f to GoldHi.copy(alpha = 0.55f), 0.72f to Color.Transparent,
                center = Offset(size.width * 0.4f, size.height * 0.38f), radius = r * 1.1f,
            )
            onDrawBehind { drawCircle(halo, r * 1.9f); drawCircle(core, r) }
        })
        if (!playing) {
            val zSize = with(density) { (side * 0.7f).toSp() }
            Text("z", style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, color = GoldHi, fontSize = zSize),
                modifier = Modifier.graphicsLayer {
                    // The mock-up's "zz": hidden for the first 55 % of the breath, then floats up-right and fades.
                    val f = (data.seconds.floatValue % period) / period
                    val q = ((f - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    alpha = if (f < 0.55f) 0f else if (q < 0.38f) 0.9f * q / 0.38f else 0.9f * (1f - (q - 0.38f) / 0.62f)
                    translationX = side.toPx() * 1.05f + q * size.width * 0.7f
                    translationY = -side.toPx() * 0.7f - q * size.height
                })
        }
    }
}

/** Three little gold twinkles at fixed spots (the mock-up's ".sk"), on their own layer. */
@Composable
private fun Twinkles(modifier: Modifier) {
    val data = LocalNight.current ?: return
    val unit = LocalU.current
    Box(modifier.graphicsLayer { }.drawBehind {
        val t = data.seconds.floatValue
        val dot = unit.toPx() * 1.7f
        TWINKLES.forEach { (x, y, delay) ->
            val q = (((t - delay) % 4.2f) + 4.2f) % 4.2f / 4.2f
            val v = 0.5f - 0.5f * cos(2f * PI.toFloat() * q)
            if (v > 0.02f) {
                val c = Offset(x * size.width, y * size.height)
                stamp(NightSprites.glow, c.x, c.y, dot * 5f * (0.4f + 0.6f * v), 0.9f * v)
                drawCircle(GoldHi.copy(alpha = 0.9f * v), dot / 2 * (0.4f + 0.6f * v), c)
            }
        }
    })
}

private val TWINKLES = listOf(Triple(0.05f, 0.12f, 0f), Triple(0.42f, 0.03f, 1.4f), Triple(0.22f, 0.5f, 2.7f))
