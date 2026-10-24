package com.shamil.launcher.settings

import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppCategories
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.glance.WeatherSource
import com.shamil.launcher.ui.AppRow
import com.shamil.launcher.ui.LetterRail
import com.shamil.launcher.ui.LocalAppIcons
import com.shamil.launcher.ui.ROW_START
import com.shamil.launcher.ui.clock.LocalClockStyleOverride
import com.shamil.launcher.ui.clock.rememberClockTime
import com.shamil.launcher.ui.home.BLOCK_FAVS
import com.shamil.launcher.ui.home.BLOCK_MUSIC
import com.shamil.launcher.ui.home.BLOCK_TOP
import com.shamil.launcher.ui.home.BLOCK_WIDGETS
import com.shamil.launcher.ui.home.Gold
import com.shamil.launcher.ui.home.GroupHeading
import com.shamil.launcher.ui.home.HomeStyle
import com.shamil.launcher.ui.home.HomeTop
import com.shamil.launcher.ui.home.LocalHomeLayoutOverride
import com.shamil.launcher.ui.home.LocalHomeStyleOverride
import com.shamil.launcher.ui.home.WallpaperFade
import com.shamil.launcher.ui.home.homeCard
import com.shamil.launcher.ui.livebg.LiveBgStyle
import com.shamil.launcher.ui.livebg.LiveWorld
import com.shamil.launcher.ui.livebg.SkyShader
import com.shamil.launcher.ui.livebg.weatherKind
import com.shamil.launcher.ui.media.MusicStyle
import com.shamil.launcher.ui.notify.NotifPrefs
import com.shamil.launcher.ui.rail.ArcRailLayer
import com.shamil.launcher.ui.rail.ConstellationRail
import com.shamil.launcher.ui.rail.GliderRail
import com.shamil.launcher.ui.rail.LanternRail
import com.shamil.launcher.ui.rail.PetalFanRail
import com.shamil.launcher.ui.rail.RailStyle
import com.shamil.launcher.ui.rail.SeelieGuideRail
import com.shamil.launcher.ui.rail.ThumbArcLayer
import com.shamil.launcher.ui.rail.WheelRail
import com.shamil.launcher.ui.rail.railMirror
import com.shamil.launcher.ui.rail.rememberArcRailState
import com.shamil.launcher.ui.rememberGlanceState
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LauncherLook
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.vault.GlassPadLayout
import com.shamil.launcher.ui.vault.PinLook
import com.shamil.launcher.ui.vault.SeelieDotsPadLayout
import com.shamil.launcher.ui.vault.TrailPadLayout
import com.shamil.launcher.ui.widgets.widgetHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.min
import kotlin.math.sin

/*
 * The full save preview, part 2: the phone. The home is laid out at the real screen size and then shrunk to fit
 * (so the proportions are exactly the home's), with each part on its own layer, back to front: wallpaper, living
 * background, clock/date/glance, widgets, music card, favourites, the A–Z rail and the PIN screen.
 * Because every part is its own layer, the same phone can dim all but one part (the chips) or float the layers apart
 * in 3D ("Explode"). Everything that moves is read in the draw phase (graphicsLayer / drawBehind), so an animation
 * never rebuilds the phone.
 */

/** Which wallpaper pictures to draw, already decoded at preview size. */
internal class PhoneWalls(val home: ImageBitmap?)

/** How the phone is shown, besides the save itself. */
internal class PhoneShow(
    /** Run the living background (only the page he's looking at); false = one still frame. */
    val live: Boolean = false,
    /** 0 = flat, 1 = floated apart. Read only while drawing. */
    val explode: () -> Float = { 0f },
    /** Explode is on (or still settling back): no rounded clip, the PIN layer joins the stack. */
    val loose: Boolean = false,
    /** The chip he tapped: every other part dims. */
    val focus: String? = null,
    /** Layers switched off in Explode. */
    val hidden: Set<String> = emptySet(),
    /** Flip the phone over to the PIN screen. */
    val pinFace: Boolean = false,
)

@Composable
internal fun SavePhone(
    parts: SaveParts,
    walls: PhoneWalls,
    favourites: List<AppEntry>,
    modifier: Modifier = Modifier,
    show: PhoneShow = PhoneShow(),
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val density = LocalDensity.current
    val screenW = with(density) { metrics.widthPixels.toDp() }
    val screenH = with(density) { metrics.heightPixels.toDp() }
    val shape = RoundedCornerShape(24.dp)
    val look = parts.look

    // The flip to the PIN screen: the lock face is only built while it's (about to be) seen.
    val flip = remember { Animatable(0f) }
    var lockBuilt by remember { mutableStateOf(false) }
    LaunchedEffect(show.pinFace) {
        if (show.pinFace) lockBuilt = true
        flip.animateTo(if (show.pinFace) 180f else 0f, tween(700, easing = FastOutSlowInEasing))
        if (!show.pinFace) lockBuilt = false
    }

    // Each layer's see-through level: hidden in Explode, or dimmed while another part is lit (a 0.4 s glide).
    fun target(key: String): Float = when {
        key in show.hidden -> 0f
        key == "pin" -> if (show.loose) 1f else 0f
        show.focus == null || show.focus == "type" || show.focus == "pin" || show.focus == key -> 1f
        show.focus == "live" && key == "wall" -> 0.4f
        else -> 0.13f
    }
    val alphas = SAVE_LAYERS.associate { (key, _) -> key to animateFloatAsState(target(key), tween(400), label = key) }

    // The names floating beside each layer in Explode (measured once per look).
    val measurer = rememberTextMeasurer()
    val labels = remember(look.font) {
        val style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = Gold)
        SAVE_LAYERS.map { (_, name) -> measurer.measure(name, style) }
    }
    val panes = with(density) {
        Panes(show.explode, metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat(), 64.dp.toPx(), 28.dp.toPx(), labels, look.accent)
    }

    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .aspectRatio(screenW / screenH)
            .graphicsLayer { rotationY = flip.value; cameraDistance = 16f * density.density }
    ) {
        val scale = maxWidth / screenW
        // The front: the home, layer by layer.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (flip.value <= 90f) 1f else 0f }
                .then(if (show.loose) Modifier else Modifier.clip(shape).border(1.dp, Color.White.copy(alpha = 0.12f), shape))
        ) {
            CompositionLocalProvider(
                LocalLauncherLook provides look,
                LocalHomeStyleOverride provides parts.home,
                LocalClockStyleOverride provides parts.clock,
                LocalHomeLayoutOverride provides parts.layout,
            ) {
                Box(Modifier.fullSize(screenW, screenH, scale)) {
                    HomeLayers(parts, walls, favourites, show, alphas, panes, screenH)
                }
            }
        }
        // The back: the vault's PIN screen in the save's PIN look.
        if (lockBuilt) Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationY = 180f; alpha = if (flip.value > 90f) 1f else 0f }
                .clip(shape)
                .border(1.dp, look.accent.copy(alpha = 0.3f), shape)
        ) {
            CompositionLocalProvider(LocalLauncherLook provides look) {
                Box(Modifier.fullSize(screenW, screenH, scale)) { PinScreen(parts, solid = true) }
            }
        }
        // Taps on the little phone do nothing (it's a picture); swipes still reach the pager.
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {})
    }
}

/** Lays the content out at the real screen size, then shrinks it to fit (so it looks exactly like the home). */
private fun Modifier.fullSize(screenW: Dp, screenH: Dp, scale: Float): Modifier = layout { measurable, constraints ->
    val full = measurable.measure(Constraints.fixed(screenW.roundToPx(), screenH.roundToPx()))
    layout(constraints.maxWidth, constraints.maxHeight) {
        full.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
    }
}

/** The home's layers, in the order the home draws them, each with its own [pane]. */
@Composable
private fun HomeLayers(
    parts: SaveParts, walls: PhoneWalls, favourites: List<AppEntry>, show: PhoneShow,
    alphas: Map<String, State<Float>>, panes: Panes, screenH: Dp,
) {
    val look = parts.look
    val layout = parts.layout
    val hs = parts.home
    val time = rememberClockTime()
    // One shared glance for every phone on the page (each card checking the weather itself would add up).
    val glance = LocalPreviewGlance.current ?: rememberGlanceState()
    fun a(key: String) = alphas.getValue(key)

    Box(Modifier.fillMaxSize()) {
        // 0. Wallpaper: the save's own picture, else the theme's colours (Android won't let Seelie read the real one).
        Box(
            Modifier
                .fillMaxSize()
                .pane(0, a("wall"), panes)
                .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 1f), look.sheet.copy(alpha = 1f))))
                .drawBehind {
                    if (walls.home == null) drawCircle(
                        Brush.radialGradient(listOf(look.accent.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.3f, 0f), radius = size.width),
                        radius = size.width, center = Offset(size.width * 0.3f, 0f),
                    )
                }
        ) {
            walls.home?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (layout.fade) WallpaperFade(layout)
        }

        // 1. The living background, lighter than the home's (fewer particles), still on every page but the one in front.
        Box(Modifier.fillMaxSize().pane(1, a("live"), panes)) {
            SaveLiveBg(parts.live, show.live, Modifier.fillMaxSize())
        }

        // 2–5. The blocks in the save's order, like the home: clock, widgets, music, favourites.
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(screenH * layout.clockTop))
            layout.order.forEach { block ->
                when (block) {
                    BLOCK_TOP -> Box(Modifier.fillMaxWidth().pane(2, a("clock"), panes)) {
                        // Full width, like the home: a centred clock centres on the whole screen.
                        Column {
                            HomeTop(time = time, glance = glance)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    BLOCK_WIDGETS -> if (layout.widgets) Box(Modifier.fillMaxWidth().pane(3, a("widgets"), panes)) {
                        CompositionLocalProvider(LocalLauncherLook provides look.forPart("widgets")) {
                            SampleWidget(hs, Modifier.padding(start = hs.edge.dp, end = 56.dp, top = (hs.gap3 - 12).coerceAtLeast(0).dp, bottom = 8.dp))
                        }
                    }
                    BLOCK_MUSIC -> if (parts.music.show) Box(Modifier.fillMaxWidth().pane(4, a("music"), panes)) {
                        CompositionLocalProvider(LocalLauncherLook provides look.forPart("widgets")) {
                            SampleMusic(parts.music, hs, Modifier.padding(start = hs.edge.dp, end = 56.dp, bottom = 8.dp))
                        }
                    }
                    BLOCK_FAVS -> if (layout.favs) Box(Modifier.fillMaxWidth().weight(1f, fill = false).pane(5, a("favs"), panes)) {
                        // Clipped inside the layer, so the list stops at the phone's bottom edge (the pane outline doesn't).
                        Box(Modifier.clipToBounds()) { Favourites(parts, favourites) }
                    }
                }
            }
        }

        // 6. The save's own A–Z rail, where the home puts it.
        Box(Modifier.fillMaxSize().pane(6, a("rail"), panes)) { RailPicture(parts.rail, screenH) }

        // 7. The PIN screen joins the stack in Explode (the chips flip the phone to it instead).
        if (show.loose) Box(Modifier.fillMaxSize().pane(7, a("pin"), panes)) { PinScreen(parts, solid = false) }
    }
}

/* ---------------------------------------------------------------- the 3D layers */

/** What every layer needs to float apart: the shared explode amount, the phone's size, spacing and the labels. */
private class Panes(
    val explode: () -> Float,
    val w: Float,
    val h: Float,
    /** How far apart two layers float (px, before the phone is shrunk). */
    val gap: Float,
    val corner: Float,
    val labels: List<TextLayoutResult>,
    val accent: Color,
)

/**
 * One layer of the phone. Flat, it just fades ([alpha]). Exploded, it tilts back like a sheet of glass (rotationX,
 * rotationZ, a little smaller) around the phone's centre, not its own, so all layers line up into one stack, and
 * floats up by its place in the stack. A dashed outline and its name show which layer is which.
 */
@Composable
private fun Modifier.pane(slot: Int, alpha: State<Float>, p: Panes): Modifier {
    // Where this layer sits in the phone (set when placed; read only while drawing).
    val at = remember { floatArrayOf(0f, 0f) }
    return this
        .onPlaced { val o = it.positionInParent(); at[0] = o.x; at[1] = o.y }
        .graphicsLayer {
            this.alpha = alpha.value
            val e = p.explode()
            if (e > 0f && size.width > 0f && size.height > 0f) {
                transformOrigin = TransformOrigin((p.w / 2f - at[0]) / size.width, (p.h / 2f - at[1]) / size.height)
                rotationX = 56f * e
                rotationZ = -24f * e
                val k = 1f - 0.36f * e
                scaleX = k
                scaleY = k
                translationY = (p.h * 0.04f - (slot - 3.5f) * p.gap) * e
                cameraDistance = 18f * density
            }
        }
        .drawBehind {
            val e = p.explode()
            if (e < 0.01f) return@drawBehind
            // The layer's sheet of glass: the whole phone's size, wherever this block sits in it.
            val topLeft = Offset(-at[0], -at[1])
            val sheet = Size(p.w, p.h)
            drawRoundRect(Color(0xFF0B0F19).copy(alpha = 0.16f * e), topLeft, sheet, CornerRadius(p.corner))
            drawRoundRect(
                p.accent.copy(alpha = 0.55f * e), topLeft, sheet, CornerRadius(p.corner),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            )
            val label = p.labels[slot]
            val pad = 14.dp.toPx()
            val pill = Offset(p.w - label.size.width - pad * 3, p.h * 0.84f) + topLeft
            drawRoundRect(
                Color(0xFF0B0F19).copy(alpha = 0.85f * e), pill - Offset(pad, pad / 2),
                Size(label.size.width + pad * 2, label.size.height + pad), CornerRadius(label.size.height.toFloat()),
            )
            drawText(label, topLeft = pill, alpha = e)
        }
}

/* ---------------------------------------------------------------- the parts */

/** The favourites as the save lays them out: icon mode, grouping, the quick toggles row and notification lines. */
@Composable
private fun Favourites(parts: SaveParts, favourites: List<AppEntry>) {
    val context = LocalContext.current
    val layout = parts.layout
    val shown = favourites.take(12)
    // Grouped (Smart Launcher-ish): each favourite's heading, worked out off the main thread like the home does.
    val groupOf by produceState(emptyMap<String, String>(), shown, layout.group) {
        value = if (!layout.group) emptyMap()
        else withContext(Dispatchers.IO) { shown.associate { it.key to AppCategories.of(context, it.component.packageName) } }
    }
    // Notification lines aren't part of a save: a sample one shows when his phone has them on.
    val notifLines = remember { NotifPrefs.get(context) }.showRows
    val appsLook = LocalLauncherLook.current.forPart("apps").let { if (layout.icons == "small") it.copy(iconScale = it.iconScale * 0.7f) else it }
    val slot = if (parts.toggles.show && parts.toggles.toggles.isNotEmpty()) parts.toggles.after.coerceIn(0, shown.size) else null
    CompositionLocalProvider(LocalLauncherLook provides appsLook, LocalAppIcons provides (layout.icons != "text")) {
        Column(Modifier.padding(end = 56.dp)) {
            @Composable fun row(app: AppEntry, first: Boolean) {
                AppRow(app, onLongPress = {})
                if (first && notifLines) SampleNotifLine(icons = layout.icons != "text")
            }
            if (layout.group && groupOf.isNotEmpty()) {
                if (slot == 0) SampleToggles(parts.toggles, parts.home)
                var first = true
                shown.groupBy { groupOf[it.key] ?: AppCategories.OTHER }.forEach { (heading, apps) ->
                    GroupHeading(heading)
                    apps.forEach { row(it, first); first = false }
                }
                if (slot != null && slot != 0) SampleToggles(parts.toggles, parts.home)
            } else {
                shown.forEachIndexed { i, app ->
                    if (i == slot) SampleToggles(parts.toggles, parts.home)
                    row(app, i == 0)
                }
                if (slot == shown.size) SampleToggles(parts.toggles, parts.home)
            }
        }
    }
}

/** A notification line under a favourite, in the home's style ("now · …" in the accent). Sample words only. */
@Composable
private fun SampleNotifLine(icons: Boolean) {
    val look = LocalLauncherLook.current
    val start = ROW_START + if (icons) 44.dp * look.iconScale + 20.dp else 0.dp
    Row(Modifier.padding(start = start, bottom = 6.dp)) {
        Text("now · ", style = TextStyle(fontFamily = look.font, color = look.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, shadow = look.shadow))
        Text("2 new messages", maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.5.sp, shadow = look.shadow))
    }
}

/** The quick toggles row in the save's look (the first one shown "on"). Pictures only: nothing switches. */
@Composable
private fun SampleToggles(t: ToggleLook, hs: HomeStyle) {
    val look = LocalLauncherLook.current
    val side = when (t.size) { "small" -> 36.dp; "large" -> 52.dp; else -> 44.dp }
    val on = when (t.onColour) {
        "gold" -> Gold
        "white" -> Color.White
        "home" -> if (hs.cardAccent == "gold") Gold else look.accent
        else -> look.accent
    }
    Column(
        Modifier
            .padding(start = ROW_START - if (t.card) 12.dp else 0.dp, top = 4.dp, bottom = 8.dp)
            .then(if (t.card) Modifier.homeCard(hs, look).padding(12.dp) else Modifier)
    ) {
        if (t.card && t.cardLabel) {
            Text("QUICK TOGGLES", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp,
                letterSpacing = 0.16.em, color = if (hs.cardAccent == "gold") Gold else look.accent))
            Spacer(Modifier.height(8.dp))
        }
        Row(
            if (t.align == "start") Modifier else Modifier.fillMaxWidth(),
            horizontalArrangement = when (t.align) {
                "center" -> Arrangement.spacedBy(t.gap.dp, Alignment.CenterHorizontally)
                "spread" -> Arrangement.SpaceEvenly
                else -> Arrangement.spacedBy(t.gap.dp)
            },
        ) {
            t.toggles.take(6).forEachIndexed { i, q ->
                val lit = i == 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val shape = if (t.shape == "squircle") RoundedCornerShape(side * 0.32f) else CircleShape
                    val wide = t.pills || t.shape == "pill"
                    Row(
                        Modifier
                            .height(side)
                            .then(if (wide) Modifier else Modifier.width(side))
                            .drawBehind {
                                if (lit && t.glow) drawCircle(Brush.radialGradient(listOf(on.copy(alpha = 0.45f), Color.Transparent)), radius = size.maxDimension * 0.8f)
                            }
                            .clip(shape)
                            .background(
                                when {
                                    lit -> on.copy(alpha = 0.92f)
                                    t.offLook == "soft" -> Color.White.copy(alpha = 0.14f)
                                    t.offLook == "glass" -> Color.White.copy(alpha = 0.07f)
                                    else -> Color.Transparent
                                }
                            )
                            .then(if (!lit && t.offLook == "glass") Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), shape) else Modifier)
                            .padding(horizontal = if (wide) 14.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        val ink = if (lit) look.sheet.copy(alpha = 1f) else look.text
                        Icon(painterResource(q.icon), null, tint = ink, modifier = Modifier.size(side * 0.48f))
                        if (t.pills) {
                            Spacer(Modifier.width(8.dp))
                            Text(q.short, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ink))
                        }
                    }
                    if (t.labels && !t.pills) Text(q.short, modifier = Modifier.padding(top = 4.dp),
                        style = TextStyle(fontFamily = look.font, fontSize = 11.sp, color = look.textDim, shadow = look.shadow))
                }
            }
        }
    }
}

/**
 * The widget area: a month calendar in the save's widget card (its style, see-through, corners, size, accent and
 * "today" colour). Which widgets are in his stack isn't part of a save, so this is a stand-in, not his stack.
 */
@Composable
private fun SampleWidget(hs: HomeStyle, modifier: Modifier) {
    val look = LocalLauncherLook.current
    val accent = if (hs.cardAccent == "gold") Gold else look.accent
    val todayFill = when (hs.cardToday) { "gold" -> Gold; "accent" -> look.accent; else -> Color.White }
    val compact = hs.cardSize == "compact"
    val shadow = if (!hs.cardVisible || hs.cardOpacity < 50) look.shadow else null
    val today = remember { LocalDate.now() }
    val locale = Locale.getDefault()
    val weeks = remember(today, compact) {
        val first = WeekFields.of(locale).firstDayOfWeek
        var start = today.withDayOfMonth(1)
        while (start.dayOfWeek != first) start = start.minusDays(1)
        val all = (0 until 5).map { w -> (0 until 7).map { start.plusDays((w * 7 + it).toLong()) } }
        if (compact) all.filter { week -> week.any { it == today } } else all
    }
    Column(
        modifier
            .fillMaxWidth()
            .height(widgetHeight(hs))
            .then(if (hs.cardVisible) Modifier.homeCard(hs, look) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 12.dp)
    ) {
        Text(today.format(DateTimeFormatter.ofPattern("MMM yyyy", locale)).uppercase(locale).replace(".", ""),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.14.em,
                color = accent, shadow = shadow))
        Spacer(Modifier.height(if (compact) 6.dp else 4.dp))
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                week.forEach { d ->
                    val isToday = d == today
                    Box(Modifier.size(22.dp).then(if (isToday) Modifier.background(todayFill, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                        Text(d.dayOfMonth.toString(), style = TextStyle(
                            fontFamily = look.font, fontSize = 11.5.sp, fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = when {
                                isToday -> Color(0xFF171C29)
                                d.month != today.month -> look.text.copy(alpha = 0.35f)
                                else -> look.text
                            },
                            shadow = if (isToday) null else shadow,
                        ))
                    }
                }
            }
        }
    }
}

/**
 * The music card in the save's look: its card, cover size and shape, glow, colour, buttons, text and bar.
 * A sample song, drawn still (the real card follows what's playing).
 */
@Composable
private fun SampleMusic(ms: MusicStyle, hs: HomeStyle, modifier: Modifier) {
    val look = LocalLauncherLook.current
    val accent = when (ms.accent) {
        "gold" -> Gold
        else -> if (ms.followHome && hs.cardAccent == "gold") Gold else look.accent
    }
    val compact = ms.size == "compact"
    val art = when (ms.artSize) {
        "small" -> if (compact) 36.dp else 48.dp
        "large" -> if (compact) 56.dp else 96.dp
        else -> if (compact) 46.dp else 72.dp
    }
    val card = when {
        ms.artBackground -> Modifier.clip(RoundedCornerShape(ms.cardRadius.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), look.sheet.copy(alpha = 0.95f))))
        ms.followHome -> Modifier.homeCard(hs, look)
        ms.cardStyle == "none" -> Modifier
        else -> Modifier.homeCard(
            hs.copy(cardStyle = ms.cardStyle, cardOpacity = ms.cardOpacity, cardRadius = ms.cardRadius,
                cardBorder = ms.cardStyle == "glass" || ms.cardStyle == "outline"),
            look,
        )
    }
    val text = if (ms.artBackground) Color.White else look.text
    val dim = if (ms.artBackground) Color.White.copy(alpha = 0.78f) else look.textDim
    val title = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = ms.titleSize.sp, color = text, shadow = look.shadow)
    val small = TextStyle(fontFamily = look.font, fontSize = 13.sp, color = dim, shadow = look.shadow)
    val right = ms.artSide == "right"
    Column(modifier.fillMaxWidth().then(card).padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!right) { SampleCover(ms, accent, art); Spacer(Modifier.width(14.dp)) }
            Column(Modifier.weight(1f)) {
                if (ms.showApp && !compact) Text("♪ Music", style = small.copy(fontSize = 11.5.sp))
                Text("Liyue Harbor", maxLines = 1, overflow = TextOverflow.Ellipsis, style = title)
                if (ms.showArtist) Text("Yu-Peng Chen", maxLines = 1, style = small)
            }
            if (compact) PlayGlyph(ms, accent, 40.dp)
            if (right) { Spacer(Modifier.width(14.dp)); SampleCover(ms, accent, art) }
        }
        if (!compact) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (ms.skipButtons) { SkipGlyph(text, back = true); Spacer(Modifier.width(26.dp)) }
                PlayGlyph(ms, accent, 50.dp)
                if (ms.skipButtons) { Spacer(Modifier.width(26.dp)); SkipGlyph(text, back = false) }
            }
            if (ms.bar != "none") {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("1:12", style = small.copy(fontSize = 11.sp))
                    Spacer(Modifier.width(8.dp))
                    SampleBar(ms.bar, accent, text, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("3:40", style = small.copy(fontSize = 11.sp))
                }
            }
        }
    }
}

/** A stand-in cover in the accent's colours: rounded, round, or a vinyl record with its label. */
@Composable
private fun SampleCover(ms: MusicStyle, accent: Color, side: Dp) {
    val shape = if (ms.artShape == "rounded") RoundedCornerShape(side * 0.16f) else CircleShape
    Box(
        Modifier
            .size(side)
            .drawBehind {
                if (ms.artGlow) drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent)), radius = size.maxDimension * 0.85f)
            }
            .clip(shape)
            .drawBehind {
                if (ms.artShape == "vinyl") {
                    drawCircle(Color(0xFF111217))
                    for (k in 1..4) drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * (0.22f + k * 0.065f), style = Stroke(1f))
                    drawCircle(accent, radius = size.minDimension * 0.18f)
                    drawCircle(Color(0xFF111217), radius = size.minDimension * 0.03f)
                } else {
                    drawRect(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f), Color(0xFF1B2140))))
                    drawCircle(Color.White.copy(alpha = 0.22f), radius = size.minDimension * 0.18f, center = Offset(size.width * 0.68f, size.height * 0.32f))
                }
            }
    )
}

/** ⏯ as the save draws it: a filled circle, an outlined circle, or the bare icon. */
@Composable
private fun PlayGlyph(ms: MusicStyle, accent: Color, side: Dp) {
    val look = LocalLauncherLook.current
    val ink = if (ms.buttons == "filled") look.sheet.copy(alpha = 1f) else accent
    Box(
        Modifier
            .size(side)
            .then(
                when (ms.buttons) {
                    "filled" -> Modifier.background(accent, CircleShape)
                    "outline" -> Modifier.border(1.5.dp, accent, CircleShape)
                    else -> Modifier
                }
            )
            .drawBehind {
                // A pause sign (the sample is "playing").
                val w = size.width * 0.09f
                val h = size.height * 0.34f
                val gap = size.width * 0.07f
                drawRoundRect(ink, Offset(center.x - gap - w, center.y - h / 2), Size(w, h), CornerRadius(w / 2))
                drawRoundRect(ink, Offset(center.x + gap, center.y - h / 2), Size(w, h), CornerRadius(w / 2))
            }
    )
}

@Composable
private fun SkipGlyph(colour: Color, back: Boolean) {
    Box(
        Modifier.size(26.dp).drawBehind {
            val dir = if (back) -1f else 1f
            val cx = center.x
            val h = size.height * 0.5f
            val path = Path().apply {
                moveTo(cx - dir * size.width * 0.25f, center.y - h / 2)
                lineTo(cx + dir * size.width * 0.15f, center.y)
                lineTo(cx - dir * size.width * 0.25f, center.y + h / 2)
                close()
            }
            drawPath(path, colour)
            drawRect(colour, Offset(cx + dir * size.width * 0.18f - 1.5.dp.toPx() / 2, center.y - h / 2), Size(1.5.dp.toPx(), h))
        }
    )
}

/** The progress bar: thin, thick or wavy, about a third played. */
@Composable
private fun SampleBar(bar: String, accent: Color, track: Color, modifier: Modifier) {
    Box(
        modifier.height(12.dp).drawBehind {
            val y = center.y
            val played = size.width * 0.33f
            val thick = if (bar == "thick") 6.dp.toPx() else 2.dp.toPx()
            drawLine(track.copy(alpha = 0.25f), Offset(played, y), Offset(size.width, y), thick, StrokeCap.Round)
            if (bar == "wavy") {
                val wave = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    while (x <= played) { lineTo(x, y + sin(x / 7.dp.toPx()) * 3.dp.toPx()); x += 2f }
                }
                drawPath(wave, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            } else {
                drawLine(accent, Offset(0f, y), Offset(played, y), thick, StrokeCap.Round)
            }
            drawCircle(accent, radius = thick + 2.dp.toPx(), center = Offset(played, y))
        }
    )
}

/** The save's A–Z rail, the real drawing in the same strip the home uses (asleep: it only moves when touched). */
@Composable
private fun RailPicture(style: RailStyle, screenH: Dp) {
    val letters = remember { ('A'..'Z').map { it.toString() }.toSet() + "#" }
    Box(Modifier.fillMaxSize()) {
        when (style) {
            RailStyle.ARC_GUIDE -> ArcRailLayer(rememberArcRailState(), letters, Modifier.railMirror())
            RailStyle.THUMB_ARC -> ThumbArcLayer(rememberArcRailState(), letters, Modifier.railMirror())
            else -> {
                val strip = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = screenH * 0.27f)
                    .height(screenH * 0.64f)
                    .width(46.dp)
                    .railMirror()
                val none: (String) -> List<AppEntry> = { emptyList() }
                when (style) {
                    RailStyle.GUIDE -> SeelieGuideRail(letters, strip, onPick = {})
                    RailStyle.FAN -> PetalFanRail(letters, none, {}, strip, onPick = {})
                    RailStyle.CONSTELLATION -> ConstellationRail(letters, none, {}, strip, onPick = {})
                    RailStyle.LANTERN -> LanternRail(letters, strip, onPick = {})
                    RailStyle.GLIDER -> GliderRail(letters, strip, onPick = {})
                    RailStyle.WHEEL -> WheelRail(letters, strip, onPick = {})
                    else -> LetterRail(letters, strip, onPick = {})
                }
            }
        }
    }
}

/**
 * The vault's PIN screen in the save's PIN look, with two of four dots typed. [solid] = the vault's own dark backdrop
 * (the flipped phone); otherwise just the pad, as a glass layer in Explode. Nothing here can be typed into.
 */
@Composable
private fun PinScreen(parts: SaveParts, solid: Boolean) {
    val look = parts.look
    val noDigit: (String) -> Unit = {}
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (solid) Modifier.background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 0.97f), look.sheet.copy(alpha = 0.99f))))
                else Modifier.background(look.sheet.copy(alpha = 0.25f))
            )
    ) {
        when (parts.pin) {
            PinLook.GLASS -> GlassPadLayout("Vault", "Type your PIN", "12", 4, noDigit, {}, null, {}, onFinger = {})
            PinLook.SEELIE -> SeelieDotsPadLayout("Vault", "Type your PIN", "12", 4, noDigit, {}, null, {}, onFinger = {})
            PinLook.TRAIL -> TrailPadLayout("Vault", "Type your PIN", "12", 4, noDigit, {}, null, {}, onFinger = {})
            PinLook.ROUND -> RoundPadPicture(look)
        }
    }
}

/** The first pad ("Round"): big round keys under a Cinzel title. A picture of the vault's own, which is private there. */
@Composable
private fun RoundPadPicture(look: LauncherLook) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.6f))
        Icon(painterResource(R.drawable.ic_lock), null, tint = look.accent, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text("Vault", style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 30.sp))
        Spacer(Modifier.height(6.dp))
        Text("Type your PIN", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) { i -> Box(Modifier.size(14.dp).border(1.5.dp, look.accent.copy(alpha = 0.7f), CircleShape).then(if (i < 2) Modifier.background(look.accent, CircleShape) else Modifier)) }
        }
        Spacer(Modifier.weight(0.5f))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                row.forEach { k ->
                    Box(Modifier.size(72.dp).clip(CircleShape).background(if (k.isEmpty()) Color.Transparent else look.card), contentAlignment = Alignment.Center) {
                        Text(k, style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 28.sp))
                    }
                }
            }
        }
        Spacer(Modifier.weight(0.4f))
    }
}

/* ---------------------------------------------------------------- living background */

/**
 * The save's living background, on the same engine as the home but lighter: about half the particles, 30 fps, and
 * the sky shader (clouds, rays, mist, aurora) only on the page in front. [live] = false draws one still frame: the
 * scene runs for a moment out of sight, then is drawn once. The frame loop only runs while Seelie is in front.
 */
@Composable
private fun SaveLiveBg(style: LiveBgStyle, live: Boolean, modifier: Modifier) {
    if (!style.anythingOn()) return
    val context = LocalContext.current
    val world = remember { LiveWorld() }
    val tick = remember { mutableIntStateOf(0) }
    val light = remember(style) {
        style.copy(fps60 = false, fx = style.fx.mapValues { (id, s) -> if (id == "touch" || id == "tilt") s.copy(on = false) else s.copy(density = s.density / 2) })
    }
    val animationsOn = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val running = live && animationsOn
    val sky = remember(running) { if (running && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) SkyShader.create() else null }
    val skyLayer = rememberGraphicsLayer()
    val weatherCode = remember { WeatherSource.cached(context)?.code }
    SideEffect {
        world.style = light
        world.realWeather = weatherKind(weatherCode)
        world.skyOn = sky != null
    }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, running) {
        if (!running) {
            // A still frame: wait for the first draw (it sizes the scene), let it play ~3 s out of sight, draw once.
            withFrameNanos { }
            withFrameNanos { }
            repeat(20) { world.step(1f / 15f) }
            tick.intValue++
            return@LaunchedEffect
        }
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (true) {
                if (!world.busy) {
                    withTimeoutOrNull(15_000L) { world.wake.receive() }
                    last = 0L
                }
                withFrameNanos { now ->
                    val gap = now - last
                    if (last == 0L || gap >= 29_000_000L) {
                        world.step(if (last == 0L) 1f / 30f else min(.05f, gap / 1_000_000_000f))
                        last = now
                        tick.intValue++
                    }
                }
                delay(23L)
            }
        }
    }
    Box(
        modifier.drawBehind {
            tick.intValue   // read only to redraw on every frame
            world.draw(this, sky, skyLayer)
        }
    )
}

/* ---------------------------------------------------------------- pictures */

/**
 * Reads a wallpaper copy at about [width] px wide (a preview never needs the full picture), off the main thread.
 * Null if there's no file or it can't be read.
 */
internal suspend fun decodePreview(file: File?, width: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (file == null || !file.exists()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()
}

/** The glance (weather, battery) shared by every save phone on a page; null = each phone makes its own. */
internal val LocalPreviewGlance = androidx.compose.runtime.staticCompositionLocalOf<com.shamil.launcher.ui.GlanceState?> { null }
