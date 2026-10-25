package com.shamil.launcher.settings

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppCache
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.favouriteKey
import com.shamil.launcher.apps.rememberFavourites
import com.shamil.launcher.ui.clock.rememberClockTime
import com.shamil.launcher.ui.home.BLOCK_FAVS
import com.shamil.launcher.ui.home.BLOCK_MUSIC
import com.shamil.launcher.ui.home.BLOCK_TOP
import com.shamil.launcher.ui.home.BLOCK_WIDGETS
import com.shamil.launcher.ui.home.HomeLayoutStore
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.home.HomeTop
import com.shamil.launcher.ui.media.MusicCardBody
import com.shamil.launcher.ui.media.MusicPrefs
import androidx.compose.ui.graphics.graphicsLayer
import com.shamil.launcher.ui.rememberGlanceState
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.WallpaperStore
import com.shamil.launcher.ui.widgets.SeelieWidgetHost
import com.shamil.launcher.ui.widgets.StackEntry
import com.shamil.launcher.ui.widgets.WidgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * "Tap the thing" (his pick A from the settings mockup, 24 Oct: "the settings currently is a bit everywhere, sometimes
 * you have to scroll all the way down just to reach a setting, we need to order it more").
 * The top of settings is a small live copy of his home: tap the clock and you land on the clock's page, tap the
 * background, the widgets, the music card, the favourites, the rail or the search button. Every page is split into
 * short tabs, so nothing is a long scroll. What isn't a part of the home sits in four tiles under it.
 */

/** A part of Seelie with its own settings page: its name, one line of what's inside, its icon, and its tabs (empty = one page). */
internal enum class Node(val title: String, val sub: String, @DrawableRes val icon: Int, val tint: Color, val tabs: List<String>) {
    Background("Background", "Wallpaper, living background, scene, fade", R.drawable.ic_wallpaper, Anemo, listOf("Wallpaper", "Living", "Scene", "Fade")),
    Clock("Clock & date", "Clock, date, the line under it, spacing", R.drawable.ic_schedule, Geo, listOf("Clock", "Date", "Glance line", "Spacing")),
    Widgets("Widgets", "Stack, card look, stats", R.drawable.ic_widgets, Pyro, listOf("Stack", "Card", "Stats")),
    Music("Music card", "Card, cover art, controls", R.drawable.ic_music_note, Electro, listOf("Card", "Cover", "Controls")),
    Favourites("Favourites", "List, apps, notifications, quick toggles", R.drawable.ic_star, Hydro, listOf("List", "Apps", "Notifications", "Toggles")),
    Rail("A–Z rail", "Style, letter colour, left thumb", R.drawable.ic_touch_app, Anemo, listOf("Style", "Feel")),
    Search("Search", "What it searches, engine, commands", R.drawable.ic_search, Cryo, emptyList()),
    Style("Theme & style", "Theme, colours, fonts, icons, bundles", R.drawable.ic_palette, Pyro, listOf("Theme", "Colours", "Fonts", "Icons", "Bundles & layouts")),
    Privacy("Privacy & vault", "PIN look, what lives in the vault", R.drawable.ic_lock, Electro, emptyList()),
    Saves("Saves & backup", "Your saved looks, backup, share", R.drawable.ic_history, Dendro, listOf("Saves", "Backup & share")),
    About("About", "Default home app, rescue screen, version", R.drawable.ic_info, Geo, emptyList());

    /** The tab's number from its name (0 when the name isn't one of this page's tabs). */
    fun tabOf(name: String?): Int = tabs.indexOf(name).coerceAtLeast(0)
}

/** A place in settings: the top page (no node), a node (opened on [tab]), or the widget picker. */
internal data class Spot(val node: Node? = null, val tab: Int = 0, val picker: Boolean = false)

/** The mini home is laid out at his phone's real width (384dp), then shrunk, so it shows the real clock, cards and list. */
private val MINI_W = 384.dp
private val MINI_H = 640.dp
private const val MINI_SCALE = 0.625f
private const val RAIL_LETTERS = "☆ABCDEFGHIJKLMNOPQRSTUVWXYZ•"

/**
 * "Tap a part of your home": his real clock, widget stack, music card and favourites on his wallpaper, each with a
 * dashed gold outline and a name tag that softly pulses; a tap opens that part's page. Every part is always drawn
 * (a switched-off one as a faint placeholder), so nothing is ever unreachable.
 */
@Composable
internal fun MiniHome(onPick: (Node) -> Unit) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val layout = remember { HomeLayoutStore.get(context) }.layout
    val hs = remember { HomeStyleStore.get(context) }.style
    val widgets = remember { WidgetStore.get(context) }
    val glance = rememberGlanceState()
    val time = rememberClockTime()
    val wallpapers = remember { WallpaperStore.get(context) }
    val wallpaper by produceState<ImageBitmap?>(null, wallpapers.version) {
        value = withContext(Dispatchers.IO) {
            runCatching { wallpapers.copyFile()?.let { BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() } }.getOrNull()
        }
    }
    // His favourites with the names and icons the home shows (the saved app list, read once, never written).
    val favourites = rememberFavourites()
    // Vault apps stay out of the little phone, like on the real home (25 Oct, audit S5).
    val vault = remember { com.shamil.launcher.apps.VaultStore.get(context) }
    val vaultApps = vault.apps
    val apps by produceState(emptyList<AppEntry>(), favourites.packages, vaultApps) {
        value = withContext(Dispatchers.IO) {
            val saved = runCatching { AppCache(context.applicationContext).readIndex() }.getOrDefault(emptyList())
            val byKey = saved.associateBy { it.favouriteKey }
            favourites.packages.mapNotNull { byKey[it] }.filter { it.component.packageName !in vaultApps }.take(8)
        }
    }
    // One slow clock for every hotspot's pulse, each a little later than the one before.
    val pulse by rememberInfiniteTransition(label = "hotspots")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "pulse")
    val screen = RoundedCornerShape(44.dp)

    Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .staggerIn()
                .shrunk(MINI_SCALE)
                .size(MINI_W, MINI_H)
                .clip(screen)
                .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 1f), look.sheet.copy(alpha = 1f))))
                .border(2.dp, look.accent.copy(alpha = 0.35f), screen)
        ) {
            // The wallpaper Seelie keeps a copy of, a little dimmed so the parts read; without one, a night sky in his theme.
            val picture = wallpaper
            if (picture != null) {
                Image(picture, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.28f)))
            } else {
                Box(
                    Modifier.matchParentSize()
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(listOf(look.accent.copy(alpha = 0.3f), Color.Transparent), center = Offset(size.width * 0.25f, 0f), radius = size.width * 0.8f),
                                radius = size.width * 0.8f, center = Offset(size.width * 0.25f, 0f),
                            )
                        }
                        .sparkles(look.accent.copy(alpha = 1f), count = 14)
                )
            }

            // The background itself: first, so every part drawn on top gets its own taps. Its tag sits in the empty
            // strip above the clock, so it never looks like it belongs to the favourites.
            Hotspot("Background", index = 0, pulse = { pulse }, dotted = true, labelOnTop = true, corner = 40.dp,
                modifier = Modifier.matchParentSize().padding(4.dp), onClick = { onPick(Node.Background) })

            // His block order, plus any block an old save left out, so every part always has its tap-zone.
            val order = layout.order + listOf(BLOCK_TOP, BLOCK_WIDGETS, BLOCK_MUSIC, BLOCK_FAVS).filter { it !in layout.order }
            val musicOn = remember { MusicPrefs.get(context) }.style.show
            // Budget (in his phone's dp): 44 top + 192 clock + 132 widgets + 108 music leaves ~140 for the favourites.
            Column(Modifier.fillMaxSize().padding(top = 44.dp, end = 50.dp, bottom = 20.dp)) {
                order.forEach { block ->
                    when (block) {
                        BLOCK_TOP -> Part("Clock & date", 1, { pulse }, maxHeight = 180.dp, onClick = { onPick(Node.Clock) }) {
                            HomeTop(time = time, glance = glance)
                        }
                        BLOCK_WIDGETS -> Part("Widgets", 2, { pulse }, maxHeight = 120.dp, onClick = { onPick(Node.Widgets) }) {
                            if (layout.widgets && widgets.stack.isNotEmpty()) {
                                CompositionLocalProvider(LocalLauncherLook provides LocalLauncherLook.current.forPart("widgets")) {
                                    MiniWidgets(widgets, hs, Modifier.padding(start = hs.edge.dp))
                                }
                            } else Placeholder(if (layout.widgets) "No widgets yet" else "Widgets · off", hs.edge.dp)
                        }
                        BLOCK_MUSIC -> Part(if (musicOn) "Music" else "Music · off", 3, { pulse }, maxHeight = 96.dp, onClick = { onPick(Node.Music) }) {
                            // Switched off on the home: drawn faintly, so he can still tap it to switch it on.
                            CompositionLocalProvider(LocalLauncherLook provides LocalLauncherLook.current.forPart("widgets")) {
                                MusicCardBody(Modifier.padding(start = hs.edge.dp).graphicsLayer { alpha = if (musicOn) 1f else 0.4f }, inSettings = true)
                            }
                        }
                        BLOCK_FAVS -> Part("Favourites", 4, { pulse }, modifier = Modifier.weight(1f), minHeight = 100.dp, onClick = { onPick(Node.Favourites) }) {
                            if (layout.favs) MiniFavourites(apps) else Placeholder("Favourites · off", 40.dp)
                        }
                    }
                }
            }

            // The A–Z rail down the right edge (on the left when he moved it there).
            val railLeft = com.shamil.launcher.ui.rail.RailSide.left
            Hotspot("A–Z", index = 5, pulse = { pulse },
                modifier = Modifier.align(if (railLeft) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 6.dp).size(40.dp, 470.dp),
                onClick = { onPick(Node.Rail) }) {
                Column(Modifier.fillMaxSize().padding(top = 18.dp, bottom = 8.dp), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                    RAIL_LETTERS.forEach { c ->
                        Text(c.toString(), style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text.copy(alpha = 0.75f), fontSize = 11.sp, shadow = look.shadow))
                    }
                }
            }

            // The round search button where it sits on the home (58dp from the rail side, near the bottom).
            Hotspot(null, index = 6, pulse = { pulse }, circle = true,
                modifier = Modifier.align(if (railLeft) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(start = if (railLeft) 58.dp else 0.dp, end = if (railLeft) 0.dp else 58.dp, bottom = 30.dp).size(60.dp),
                onClick = { onPick(Node.Search) }) {
                Box(Modifier.fillMaxSize().padding(4.dp).clip(CircleShape).background(look.accent.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = "Search", tint = look.sheet.copy(alpha = 1f), modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

/** Lays the content out at full size, then shrinks it to [scale]; taps still land where they look (the layer maps them). */
private fun Modifier.shrunk(scale: Float) = layout { measurable, _ ->
    val full = measurable.measure(Constraints())
    layout((full.width * scale).roundToInt(), (full.height * scale).roundToInt()) {
        full.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
    }
}

/**
 * One block of the mini home: its real content at its natural height (cut off at [maxHeight] so the favourites always
 * keep room), with a tap-zone on top that takes every tap (the widget pager or the music buttons never see them).
 */
@Composable
private fun ColumnScope.Part(
    label: String, index: Int, pulse: () -> Float,
    modifier: Modifier = Modifier, maxHeight: Dp = Dp.Unspecified, minHeight: Dp = 64.dp,
    onClick: () -> Unit, content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, bottom = 6.dp).heightIn(min = minHeight, max = maxHeight).clipToBounds()) {
        Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).padding(top = 16.dp, bottom = 6.dp)) { content() }
        Hotspot(label, index, pulse, modifier = Modifier.matchParentSize(), onClick = onClick)
    }
}

/**
 * A tap-zone: a dashed gold outline that breathes (each one a little after the one before), the part's name in a
 * small gold tag, and the press feel he approved: it sinks a touch and glows.
 */
@Composable
private fun Hotspot(
    label: String?, index: Int, pulse: () -> Float, modifier: Modifier = Modifier,
    dotted: Boolean = false, circle: Boolean = false, labelOnTop: Boolean = false, corner: Dp = if (circle) 30.dp else 20.dp,
    onClick: () -> Unit, content: @Composable BoxScope.() -> Unit = {},
) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressFeel(press, look.accent, corner)
            .drawWithContent {
                drawContent()
                val breathe = if (dotted) 0.55f else 0.5f + 0.5f * sin(2 * PI.toFloat() * (pulse() - index * 0.14f))
                val dash = if (dotted) floatArrayOf(4.dp.toPx(), 9.dp.toPx()) else floatArrayOf(12.dp.toPx(), 8.dp.toPx())
                val inset = 1.5.dp.toPx()
                val r = corner.toPx()
                if (!dotted) drawRoundRect(look.accent.copy(alpha = 0.06f * breathe), cornerRadius = CornerRadius(r))
                drawRoundRect(
                    look.accent.copy(alpha = 0.45f + 0.45f * breathe),
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(r),
                    style = Stroke(width = 2.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(dash)),
                )
            }
            .clickable(interactionSource = press, indication = null, onClick = onClick)
    ) {
        content()
        if (label != null) {
            Text(
                label,
                maxLines = 1,
                modifier = Modifier
                    .align(if (labelOnTop) Alignment.TopCenter else Alignment.TopStart)
                    .padding(start = if (labelOnTop) 0.dp else 12.dp, top = if (labelOnTop) 14.dp else 0.dp)
                    .offset(y = if (labelOnTop) 0.dp else (-2).dp)
                    .background(Brush.verticalGradient(listOf(lerp(look.accent, Color.White, 0.3f), look.accent)), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, color = look.sheet.copy(alpha = 1f), fontSize = 16.sp, letterSpacing = 0.5.sp),
            )
        }
    }
}

/**
 * The widget area, drawn rather than the real stack: Android gives each app widget ONE live view, so a second copy
 * here would freeze the one on his home. It shows the widget he's on, in his card look, with a dot per widget.
 */
@Composable
internal fun MiniWidgets(store: WidgetStore, hs: com.shamil.launcher.ui.home.HomeStyle, modifier: Modifier) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val stack = store.stack
    val current = store.lastPage.coerceIn(0, stack.lastIndex)
    val entry = stack[current]
    val (title, icon, tint) = when (entry) {
        is StackEntry.BuiltIn -> Triple(entry.kind.title, entry.kind.icon, entry.kind.tint)
        is StackEntry.App -> Triple(
            remember(entry.appWidgetId) {
                runCatching { SeelieWidgetHost.manager(context).getAppWidgetInfo(entry.appWidgetId)?.loadLabel(context.packageManager) }.getOrNull() ?: "App widget"
            },
            R.drawable.ic_widgets, look.accent,
        )
    }
    val shape = RoundedCornerShape(hs.cardRadius.dp)
    val fill = when (hs.cardStyle) {
        "glass" -> look.panel.copy(alpha = hs.cardOpacity / 100f)
        "solid" -> look.sheetTop.copy(alpha = hs.cardOpacity / 100f)
        "outline" -> Color(0xFF0E1126).copy(alpha = hs.cardOpacity / 100f)
        else -> Color.Transparent
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(fill, shape)
            .then(if (hs.cardBorder && hs.cardStyle != "none") Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), shape) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 18.sp, shadow = look.shadow))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stack.indices.take(8).forEach { i ->
                    Box(Modifier.size(7.dp).background(if (i == current) look.accent else look.text.copy(alpha = 0.35f), CircleShape))
                }
            }
        }
    }
}

/** His first favourites as the home lists them: icon and name (the icons follow his icon pack and size). */
@Composable
private fun MiniFavourites(apps: List<AppEntry>) {
    val look = LocalLauncherLook.current.forPart("apps")
    Column(Modifier.padding(start = 30.dp, top = 4.dp)) {
        if (apps.isEmpty()) Placeholder("Your favourites", 10.dp)
        apps.forEach { app ->
            Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp * look.iconScale))
                Spacer(Modifier.width(22.dp))
                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 18.sp, shadow = look.shadow))
            }
        }
    }
}

/** A faint stand-in for a part that's switched off or empty, so its tap-zone still has something to sit on. */
@Composable
private fun Placeholder(text: String, start: Dp) {
    val look = LocalLauncherLook.current
    Text(text, modifier = Modifier.padding(start = start, top = 10.dp, bottom = 10.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.textDim.copy(alpha = 0.7f), fontSize = 17.sp))
}

/**
 * The short tabs under a page's header (like the mockup): pills that scroll sideways, the picked one filled with a
 * gold glow that glides over in 0.4 s. Opening a page on a far tab (from search) scrolls that tab into view.
 */
@Composable
internal fun TabPills(tabs: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val look = LocalLauncherLook.current
    val scroll = rememberScrollState()
    val starts = remember { mutableStateMapOf<Int, Float>() }
    val margin = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(selected, starts[selected]) {
        starts[selected]?.let { scroll.animateScrollTo((it - margin).roundToInt().coerceAtLeast(0)) }
    }
    Row(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { i, name ->
            val on = i == selected
            val top by animateColorAsState(if (on) lerp(look.accent, Color.White, 0.3f) else look.card, tween(400), label = "tab top")
            val bottom by animateColorAsState(if (on) look.accent else look.card, tween(400), label = "tab bottom")
            val ink by animateColorAsState(if (on) look.sheet.copy(alpha = 1f) else look.textDim, tween(400), label = "tab text")
            val press = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .onPlaced { starts[i] = it.positionInParent().x }
                    .height(40.dp)
                    .pressFeel(press, look.accent, 20.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(top, bottom)))
                    .border(1.dp, if (on) Color.Transparent else Color.White.copy(alpha = 0.08f), CircleShape)
                    .clickable(interactionSource = press, indication = null) { onPick(i) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(name, maxLines = 1, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = ink, fontSize = 13.5.sp))
            }
        }
    }
}

/** One of the four tiles under the mini home: icon, name, what's inside. Two per row. */
@Composable
internal fun RowScope.NodeTile(node: Node, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(22.dp)
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier
            .weight(1f)
            .staggerIn()
            .heightIn(min = 132.dp)
            .pressFeel(press, node.tint, 22.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(node.tint.copy(alpha = 0.13f), look.card)))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(14.dp)
    ) {
        IconTile(node.icon, node.tint)
        Spacer(Modifier.height(10.dp))
        Text(node.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 16.sp))
        Text(node.sub, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp, lineHeight = 15.sp))
    }
}
