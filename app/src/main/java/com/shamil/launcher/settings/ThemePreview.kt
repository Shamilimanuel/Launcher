package com.shamil.launcher.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppCache
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.Favourites
import com.shamil.launcher.ui.clock.ClockStore
import com.shamil.launcher.ui.home.HomeLayoutStore
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.home.StatsStore
import com.shamil.launcher.ui.livebg.LiveBgStore
import com.shamil.launcher.ui.media.MusicPrefs
import com.shamil.launcher.ui.rail.RailSide
import com.shamil.launcher.ui.rail.RailStore
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.LookStore
import com.shamil.launcher.ui.theme.SavedLook
import com.shamil.launcher.ui.theme.SavesStore
import com.shamil.launcher.ui.theme.WallpaperStore
import com.shamil.launcher.ui.toggles.TogglePrefs
import com.shamil.launcher.ui.vault.PinLookStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Theme previews, like Niagara's (his ask, 23 Oct), made complete on 24 Oct ("it needs everything"): each save as a
 * phone with every part it holds (see SavePreview.kt / SavePhone.kt), without applying it. The first page is what he
 * has right now; swipe sideways for his saves. Both mockup variants he kept, as one screen:
 *  - under the phone, "What's in this save": one chip per part; tapping one dims the rest and lights that part up
 *    (Fonts & colours shows a type card, PIN look turns the phone over to the vault);
 *  - "Explode": the phone's layers float apart in 3D, each can be hidden, and a receipt lists every value in the save.
 */

/** The chips under the phone: (part, title, icon). */
private val CHIPS = listOf(
    Triple("wall", "Wallpaper", R.drawable.ic_wallpaper),
    Triple("live", "Living background", R.drawable.ic_partly_cloudy_night),
    Triple("clock", "Clock, date & glance", R.drawable.ic_schedule),
    Triple("widgets", "Widgets", R.drawable.ic_widgets),
    Triple("music", "Music card", R.drawable.ic_music_note),
    Triple("favs", "Favourites", R.drawable.ic_list),
    Triple("rail", "A–Z rail", R.drawable.ic_apps),
    Triple("type", "Fonts & colours", R.drawable.ic_text_fields),
    Triple("pin", "PIN look", R.drawable.ic_lock),
)

/** The full-screen preview. Page 0 = his current look, then his saves; starts at [startPage]. */
@Composable
fun ThemePreviewDialog(
    saves: List<SavedLook>,
    startPage: Int,
    onApply: (SavedLook) -> Unit,
    onDelete: (SavedLook) -> Unit,
    onSaveCurrent: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val store = remember { SavesStore.get(context) }
    val wallpapers = remember { WallpaperStore.get(context) }
    val pageCount = saves.size + 1
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, pageCount - 1)) { pageCount }
    val favourites = rememberFavouriteApps()
    var confirmDelete by remember { mutableStateOf<SavedLook?>(null) }
    val page = pager.currentPage.coerceIn(0, pageCount - 1)
    val save = saves.getOrNull(page - 1)   // null = the current look

    // Every page, read once: his settings right now ("Current look", and the parts an older save doesn't hold), then each save.
    val now = remember(saves) { store.currentSnapshot() }
    val allParts = remember(saves, now) { listOf(readSaveParts(context, now, now)) + saves.map { readSaveParts(context, it.snapshot, now) } }
    val allWalls = remember(saves, wallpapers.version) {
        listOf(WallInfo(home = wallpapers.copyFile() != null, lock = wallpapers.lockCopyFile() != null, current = true)) +
            saves.map { WallInfo(home = store.hasWallpaper(it), lock = store.lockWallpaperFile(it).exists(), current = false) }
    }
    val parts = allParts.getOrElse(page) { allParts.last() }
    val wall = allWalls.getOrElse(page) { allWalls.last() }

    // Variant 1: the chip he tapped. Variant 2: Explode, its hidden layers.
    var focus by remember { mutableStateOf<String?>(null) }
    var exploded by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(emptySet<String>()) }
    val explode = remember { Animatable(0f) }
    // Stays true until the layers have settled back, so the rounded clip only returns once they're home.
    var loose by remember { mutableStateOf(false) }
    LaunchedEffect(exploded) {
        if (exploded) loose = true
        explode.animateTo(if (exploded) 1f else 0f, tween(900, easing = FastOutSlowInEasing))
        if (!exploded) loose = false
    }
    LaunchedEffect(page) { focus = null }
    // The screen's own accent glides to each save's colour as he swipes (his 0.4 s colour glide).
    val accent by animateColorAsState(parts.look.accent, tween(400), label = "save accent")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(LocalStagger provides remember { Stagger() }) {
        Box(Modifier.fillMaxSize().background(Color(0xF0101218))) {
            // A few seelie sparkles drifting behind it all, in their own layer so they never redraw the phones.
            Box(Modifier.fillMaxSize().graphicsLayer().sparkles(accent.copy(alpha = 0.5f), count = 12))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // ← back, like Niagara.
                    RoundIcon(R.drawable.ic_arrow_back, "Back", filled = true, onClick = onDismiss)
                    Spacer(Modifier.weight(1f))
                    // 🗑 quick delete of the theme he's looking at (not for "Current look").
                    if (save != null) RoundIcon(R.drawable.ic_delete, "Delete theme", filled = false) { confirmDelete = save }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val phoneHeight = (maxHeight * 0.7f).coerceAtLeast(300.dp)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        // The phones: the next one peeks in at the side.
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxWidth().height(phoneHeight),
                            contentPadding = PaddingValues(horizontal = 46.dp),
                            pageSpacing = 16.dp,
                        ) { p ->
                            val front = p == pager.currentPage
                            PreviewPage(
                                parts = allParts.getOrElse(p) { allParts.last() },
                                save = saves.getOrNull(p - 1),
                                wall = allWalls.getOrElse(p) { allWalls.last() },
                                favourites = favourites,
                                show = if (!front) PhoneShow() else PhoneShow(
                                    live = true,
                                    explode = { explode.value },
                                    loose = loose,
                                    focus = focus,
                                    hidden = if (loose) hidden else emptySet(),
                                    pinFace = focus == "pin" && !loose,
                                ),
                                accent = accent,
                            )
                        }
                        PageTitle(save?.name ?: "Current look", parts, wall, pageCount, page, accent)
                        if (!exploded) {
                            Column(Modifier.padding(horizontal = 18.dp)) { SectionLabel("What's in this save") }
                            // Fresh stagger per save, so the chips float in again each time he lands on one.
                            CompositionLocalProvider(LocalStagger provides remember(page) { Stagger() }) {
                                val details = remember(parts, wall) { partDetails(parts, wall) }
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Max).padding(horizontal = 18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CHIPS.forEach { (key, title, icon) ->
                                        PartChip(title, icon, details[key].orEmpty(), selected = focus == key, accent = accent,
                                            swatches = if (key == "type") parts.swatches() else null) {
                                            focus = if (focus == key) null else key
                                        }
                                    }
                                }
                            }
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                                Hint("Tap a part to light it up on the phone. Your widget stack, your favourite apps and the vault's fun lock aren't part of a save: those always stay yours.")
                            }
                        } else {
                            Column(Modifier.padding(horizontal = 18.dp)) { SectionLabel("Layers · tap to hide") }
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SAVE_LAYERS.forEach { (key, name) ->
                                    LayerChip(name, visible = key !in hidden, accent = accent) { hidden = if (key in hidden) hidden - key else hidden + key }
                                }
                            }
                            Column(Modifier.padding(horizontal = 18.dp)) {
                                SectionLabel("The receipt · everything in this save")
                                Receipt(remember(parts, wall) { receiptRows(parts, wall) }, parts.swatches(), accent)
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ✦ Explode: the layers float apart (and back together).
                    val pressX = remember { MutableInteractionSource() }
                    Text(
                        if (exploded) "Put it back together" else "✦ Explode",
                        modifier = Modifier.pressFeel(pressX, accent, 24.dp).clip(RoundedCornerShape(24.dp))
                            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                            .background(if (exploded) accent.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable(pressX, null) { exploded = !exploded; focus = null }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        maxLines = 1,
                        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = accent, fontSize = 15.sp),
                    )
                    // ✓ Apply theme (or, on "Current look", save it as a theme).
                    val press = remember { MutableInteractionSource() }
                    Row(
                        Modifier.weight(1f).pressFeel(press, accent, 24.dp).clip(RoundedCornerShape(24.dp))
                            .background(accent.copy(alpha = 0.9f))
                            .clickable(press, null) { if (save != null) onApply(save) else onSaveCurrent() }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(if (save != null) "✓" else "+", style = TextStyle(color = look.sheet.copy(alpha = 1f), fontSize = 20.sp, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.width(10.dp))
                        Text(if (save != null) "Apply theme" else "Save as a theme", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.sheet.copy(alpha = 1f), fontSize = 17.sp))
                    }
                }
            }
            confirmDelete?.let { s ->
                AlertDialog(
                    onDismissRequest = { confirmDelete = null },
                    containerColor = look.sheet,
                    title = { Text("Delete \"${s.name}\"?", color = look.text) },
                    text = { Text("Your current look stays as it is; only this theme goes.", color = look.textDim) },
                    confirmButton = { TextButton({ confirmDelete = null; onDelete(s) }) { Text("Delete", color = Color(0xFFFF9A9A)) } },
                    dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel", color = look.textDim) } },
                )
            }
        }
        }
    }
}

/** One page of the pager: the phone, plus the type card (Fonts & colours) or the lock screen picture (Wallpaper). */
@Composable
private fun PreviewPage(parts: SaveParts, save: SavedLook?, wall: WallInfo, favourites: List<AppEntry>, show: PhoneShow, accent: Color) {
    val context = LocalContext.current
    val store = remember { SavesStore.get(context) }
    val wallpapers = remember { WallpaperStore.get(context) }
    val width = context.resources.displayMetrics.widthPixels / 2
    // The save's own pictures (or Seelie's copy of his wallpaper), decoded at preview size off the main thread.
    val home by produceState<ImageBitmap?>(null, save?.id, store.saves, wallpapers.version) {
        value = decodePreview(if (save == null) wallpapers.copyFile() else store.wallpaperFile(save), width)
    }
    val showLock = show.focus == "wall" && wall.lock
    val lock by produceState<ImageBitmap?>(null, showLock, save?.id) {
        value = if (!showLock) null else decodePreview(if (save == null) wallpapers.lockCopyFile() else store.lockWallpaperFile(save), 240)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SavePhone(parts, PhoneWalls(home), favourites, show = show)
        AnimatedVisibility(
            visible = show.focus == "type",
            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.92f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.96f),
        ) { TypeCard(parts, accent) }
        // The save's lock screen picture, beside the phone while Wallpaper is lit.
        lock?.let { picture ->
            Column(Modifier.align(Alignment.BottomStart).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(picture, "Lock screen picture", contentScale = ContentScale.Crop,
                    modifier = Modifier.width(62.dp).height(128.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp)))
                Text("LOCK SCREEN", modifier = Modifier.padding(top = 4.dp),
                    style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 9.5.sp, letterSpacing = 0.12.em, color = accent))
            }
        }
    }
}

/** The save's name in Cinzel, what it is in a few words, and a dot per page. */
@Composable
private fun PageTitle(name: String, parts: SaveParts, wall: WallInfo, pageCount: Int, page: Int, accent: Color) {
    val look = LocalLauncherLook.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = accent, fontSize = 20.sp, letterSpacing = 0.04.em))
        Text(
            parts.themeName + when { wall.home && wall.lock -> " · home + lock wallpaper"; wall.home -> " · with wallpaper"; else -> " · theme colours" },
            style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(pageCount) { i ->
                val w by animateDpAsState(if (i == page) 18.dp else 6.dp, tween(300), label = "dot")
                Box(Modifier.height(6.dp).width(w).clip(CircleShape).background(if (i == page) accent else Color.White.copy(alpha = 0.25f)))
            }
        }
    }
}

/** One "What's in this save" chip: the part, what the save says about it, and (for fonts) its colours. */
@Composable
private fun PartChip(title: String, icon: Int, text: String, selected: Boolean, accent: Color, swatches: List<Color>?, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    val edge by animateColorAsState(if (selected) accent else Color.White.copy(alpha = 0.12f), tween(400), label = "chip edge")
    val glow by animateFloatAsState(if (selected) 1f else 0f, tween(400), label = "chip glow")
    Column(
        Modifier
            .staggerIn()
            .width(176.dp)
            .fillMaxHeight()
            .pressFeel(press, accent, 14.dp)
            .drawBehind { if (glow > 0f) drawRoundRect(accent.copy(alpha = 0.18f * glow), cornerRadius = CornerRadius(14.dp.toPx())) }
            .clip(shape)
            .background(look.card)
            .border(1.dp, edge, shape)
            .clickable(press, null, onClick = onClick)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, tint = accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(title.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.06.em, color = accent))
        }
        Spacer(Modifier.height(6.dp))
        Text(text, maxLines = 6, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = look.font, fontSize = 12.5.sp, lineHeight = 16.sp, color = look.text))
        swatches?.let { Swatches(it, 14.dp, Modifier.padding(top = 8.dp)) }
    }
}

/** A layer switch in Explode: the eye open (shown) or shut (hidden). */
@Composable
private fun LayerChip(name: String, visible: Boolean, accent: Color, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val edge by animateColorAsState(if (visible) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.14f), tween(400), label = "layer edge")
    Row(
        Modifier.staggerIn().pressFeel(press, accent, 18.dp).clip(CircleShape).border(1.dp, edge, CircleShape)
            .background(if (visible) accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(press, null, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(if (visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off), null,
            tint = if (visible) accent else look.textDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(name, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (visible) look.text else look.textDim))
    }
}

/** The receipt: every value in the save, one row each, gold labels on the left. */
@Composable
private fun Receipt(rows: List<Pair<String, String>>, swatches: List<Color>, accent: Color) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.staggerIn().fillMaxWidth().clip(shape).background(look.card).border(1.dp, Color.White.copy(alpha = 0.08f), shape).padding(horizontal = 14.dp, vertical = 4.dp)) {
        rows.forEachIndexed { i, (what, value) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(accent.copy(alpha = 0.16f)))
            Row(Modifier.padding(vertical = 8.dp)) {
                Text(what.uppercase(), modifier = Modifier.width(104.dp).padding(top = 1.dp, end = 8.dp),
                    style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 0.08.em, color = accent))
                Column(Modifier.weight(1f)) {
                    Text(value, style = TextStyle(fontFamily = look.font, fontSize = 13.sp, lineHeight = 18.sp, color = look.text))
                    if (what == "Fonts & colours") Swatches(swatches, 13.dp, Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** "Fonts & colours" lit up: the save's font big, its names, and its colours. */
@Composable
private fun TypeCard(parts: SaveParts, accent: Color) {
    val shape = RoundedCornerShape(22.dp)
    val l = parts.look
    Column(
        Modifier.padding(horizontal = 18.dp).clip(shape).background(Color(0xF20E1224)).border(1.dp, accent.copy(alpha = 0.5f), shape).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Aa Bb 123", style = TextStyle(fontFamily = l.font, fontSize = 34.sp, color = l.text))
        Text("${parts.fontName} · text ${parts.textColourName.lowercase()}", textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = l.font, fontSize = 13.sp, color = l.textDim))
        Spacer(Modifier.height(12.dp))
        Swatches(parts.swatches(), 26.dp)
        Spacer(Modifier.height(10.dp))
        Text("${parts.clock.label} clock · ${parts.bundle.title} home style", textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = l.font, fontSize = 12.sp, color = l.textDim))
    }
}

@Composable
private fun Swatches(colours: List<Color>, side: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(side / 3)) {
        colours.forEach { c -> Box(Modifier.size(side).clip(CircleShape).background(c).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)) }
    }
}

@Composable
private fun RoundIcon(icon: Int, label: String, filled: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(48.dp).pressFeel(press, look.accent, 24.dp).clip(CircleShape)
            .background(if (filled) look.accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f))
            .clickable(press, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), label, tint = if (filled) look.sheet.copy(alpha = 1f) else Color(0xFFFF9A9A)) }
}

/** His favourites as the home shows them, read from Seelie's own app list (nothing loads again). */
@Composable
internal fun rememberFavouriteApps(): List<AppEntry> {
    val context = LocalContext.current
    val favs = remember { Favourites(context) }.packages
    val apps by produceState(emptyList<AppEntry>(), favs) {
        value = withContext(Dispatchers.IO) {
            val all = AppCache(context.applicationContext).readIndex()
            favs.mapNotNull { pkg -> all.firstOrNull { it.component.packageName == pkg } }
        }
    }
    return apps
}

/**
 * His look right now, read the same way as a save. Reading every store's state here keeps the card live: change
 * something in settings and the little phone follows at once.
 */
@Composable
private fun rememberCurrentParts(): SaveParts {
    val context = LocalContext.current
    val keys = listOf<Any?>(
        LookStore.get(context).look, LookStore.get(context).iconPack, HomeStyleStore.get(context).style,
        remember { ClockStore.get(context) }.clockStyle, HomeLayoutStore.get(context).layout, LiveBgStore.get(context).style,
        MusicPrefs.get(context).style, TogglePrefs.get(context).setup, StatsStore.get(context).style,
        RailStore.get(context).style, RailSide.left, PinLookStore.get(context).look,
    )
    return remember(keys) {
        val now = SavesStore.get(context).currentSnapshot()
        readSaveParts(context, now, now)
    }
}

/** His look right now, as a phone (a still picture: the living background shows one frame). */
@Composable
internal fun CurrentLookPhone(favourites: List<AppEntry>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val parts = rememberCurrentParts()
    // The wallpaper he picked through Seelie, if any (Android won't let us read it otherwise).
    val store = remember { WallpaperStore.get(context) }
    val wallpaper by produceState<ImageBitmap?>(null, store.version) { value = decodePreview(store.copyFile(), 240) }
    SavePhone(parts, PhoneWalls(wallpaper), favourites, modifier)
}

/** A saved theme as a phone, with its own wallpaper picture if it has one. [small] = a card thumbnail (still picture). */
@Composable
internal fun SavedLookPhone(save: SavedLook, favourites: List<AppEntry>, modifier: Modifier = Modifier, small: Boolean = false) {
    val context = LocalContext.current
    val store = remember { SavesStore.get(context) }
    val parts = remember(save.id, save.snapshot.toString(), LookStore.get(context).wallpaperLook) { readSaveParts(context, save.snapshot) }
    val width = if (small) 240 else context.resources.displayMetrics.widthPixels / 2
    val wallpaper by produceState<ImageBitmap?>(null, save.id, store.saves) { value = decodePreview(store.wallpaperFile(save), width) }
    SavePhone(parts, PhoneWalls(wallpaper), favourites, modifier, PhoneShow(live = !small))
}
