package com.shamil.launcher.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.ui.openSafely
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.LookStore
import com.shamil.launcher.ui.theme.SeelieWallpaper
import com.shamil.launcher.ui.theme.SeelieWallpapers
import com.shamil.launcher.ui.theme.WallpaperFolder
import com.shamil.launcher.ui.theme.WallpaperStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Seelie wallpapers (25 Oct): the "SOON" card in Look → Wallpaper became four real folders of real art.
 * Folder covers (three pictures fanned out) → a grid of the folder → a preview behind his own home, with the credit
 * line, and "Home screen / Lock screen / Both", which go through the same path as "Choose from gallery".
 * Plus a Credits page (also under About): every picture's maker, museum, licence and its exact credit line.
 * The pages sit on the settings stack like any other page (Spot.wall), so Back steps out one at a time.
 */

/** The names of the wallpaper pages on the settings stack. */
internal object WallPages {
    const val CREDITS = "credits"
    fun folder(id: String) = "folder:$id"
    fun pick(id: String) = "pick:$id"
}

/** The section in Look → Wallpaper: the four folders as covers, two by two, and the way to the credits. */
@Composable
internal fun SeelieWallpaperSection(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val folders = remember { SeelieWallpapers.folders(context) }
    if (folders.isEmpty()) {
        Hint("Seelie's own wallpapers couldn't be read. Reinstalling Seelie brings them back.")
        return
    }
    Hint("Real night skies, prints and paintings, picked for Seelie. Tap a folder to look inside.")
    folders.chunked(2).forEach { pair ->
        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { f -> FolderCover(f) { onOpen(WallPages.folder(f.id)) } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    CreditsCard(onOpen)
}

/** The way to the credits page, from the wallpaper section, every folder, the preview and About. */
@Composable
internal fun CreditsCard(onOpen: (String) -> Unit) =
    GroupCard(R.drawable.ic_info, Geo, "Wallpaper credits", "Who made each one, where it's kept, its licence") { onOpen(WallPages.CREDITS) }

/** Opens the right wallpaper page for its name on the stack ([WallPages]). */
@Composable
internal fun SeelieWallpaperPage(page: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    when {
        page == WallPages.CREDITS -> CreditsPage(onBack, onOpen)
        page.startsWith("folder:") -> {
            val folder = remember(page) { SeelieWallpapers.folder(context, page.removePrefix("folder:")) }
            if (folder != null) FolderPage(folder, onBack, onOpen) else NotFound(onBack)
        }
        page.startsWith("pick:") -> {
            val wallpaper = remember(page) { SeelieWallpapers.find(context, page.removePrefix("pick:")) }
            val folder = remember(page) { wallpaper?.let { SeelieWallpapers.folder(context, it.folder) } }
            if (wallpaper != null && folder != null) PreviewPage(folder, folder.items.indexOf(wallpaper).coerceAtLeast(0), onBack, onOpen)
            else NotFound(onBack)
        }
        else -> NotFound(onBack)
    }
}

@Composable
private fun NotFound(onBack: () -> Unit) = PageFrame("Seelie wallpapers", "Not found", onBack = onBack) {
    Hint("This wallpaper isn't in Seelie any more.")
}

/**
 * A folder's cover: its first three pictures fanned out like cards in a hand (the first one on top), its name and
 * how many are inside. Under the finger the fan opens a little wider, with the usual sink and glow.
 */
@Composable
private fun RowScope.FolderCover(folder: WallpaperFolder, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val store = remember { WallpaperStore.get(context) }
    val shape = RoundedCornerShape(22.dp)
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val spread by animateFloatAsState(if (pressed) 1.3f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "fan")
    // The folder holding the wallpaper that's on his home now wears a gold edge (a 0.4 s glide when it changes).
    val inUse = folder.items.any { it.id == store.seelieId }
    val edge by animateColorAsState(if (inUse) look.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.07f), tween(400), label = "edge")
    val covers = folder.cover.mapNotNull { id -> folder.items.firstOrNull { it.id == id } }
    Column(
        Modifier
            .weight(1f)
            .staggerIn()
            .pressFeel(press, look.accent, 22.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(look.accent.copy(alpha = 0.10f), look.card)))
            .border(1.dp, edge, shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
            // Back to front: left, right, then the first picture in the middle on top.
            listOf(1 to -1f, 2 to 1f, 0 to 0f).forEach { (i, side) ->
                covers.getOrNull(i)?.let { w ->
                    WallThumb(
                        w,
                        Modifier
                            .width(56.dp)
                            .aspectRatio(9f / 19.5f)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                rotationZ = side * 10f * spread
                                translationX = side * 26.dp.toPx() * spread
                                translationY = if (side == 0f) -4.dp.toPx() * (spread - 1f) * 3f else 0f
                            },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 14.5.sp, lineHeight = 18.sp))
        Text(if (inUse) "${folder.items.size} wallpapers · 1 on" else "${folder.items.size} wallpapers",
            style = TextStyle(fontFamily = look.font, color = if (inUse) look.accent else look.textDim, fontSize = 12.sp))
    }
}

/** A wallpaper's small copy, phone-shaped, fading in (0.4 s) once it's read. */
@Composable
private fun WallThumb(wallpaper: SeelieWallpaper, modifier: Modifier, corner: Dp = 10.dp) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val picture by produceState(SeelieWallpapers.cachedThumb(wallpaper.id), wallpaper.id) {
        if (value == null) value = withContext(Dispatchers.IO) { SeelieWallpapers.thumb(context, wallpaper) }
    }
    val shown by animateFloatAsState(if (picture != null) 1f else 0f, tween(400), label = "thumb")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(look.card)
    ) {
        picture?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().graphicsLayer { alpha = shown })
        }
        Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.14f), shape))
    }
}

/** A folder: its wallpapers three across, each with its title under it; the one on his home has a gold ✓. */
@Composable
private fun FolderPage(folder: WallpaperFolder, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { WallpaperStore.get(context) }
    PageFrame("Seelie wallpapers", folder.shortName, onBack = onBack) {
        Hint("${folder.name} · ${folder.items.size} wallpapers. Tap one to see it behind your home.")
        folder.items.chunked(3).forEach { row ->
            Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { w -> GridTile(w, onHome = store.seelieId == w.id) { onOpen(WallPages.pick(w.id)) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        CreditsCard(onOpen)
    }
}

@Composable
private fun RowScope.GridTile(wallpaper: SeelieWallpaper, onHome: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    val ring by animateColorAsState(if (onHome) look.accent else Color.Transparent, tween(400), label = "ring")
    Column(
        Modifier
            .weight(1f)
            .staggerIn()
            .pressFeel(press, look.accent, 14.dp)
            .clickable(interactionSource = press, indication = null, onClick = onClick),
    ) {
        Box {
            WallThumb(wallpaper, Modifier.fillMaxWidth().aspectRatio(9f / 19.5f), corner = 14.dp)
            Box(Modifier.matchParentSize().border(2.dp, ring, shape))
            if (onHome) Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(look.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", style = TextStyle(color = look.sheet.copy(alpha = 1f), fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        }
        Text(wallpaper.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 12.sp, lineHeight = 15.sp))
    }
}

/**
 * The preview: the wallpaper behind a copy of his own home (his clock, widgets, favourites, rail), or the picture on
 * its own. Swipe for the next one in the folder. Under it: what it is, the credit line, and where to put it.
 */
@Composable
private fun PreviewPage(folder: WallpaperFolder, start: Int, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val scope = rememberCoroutineScope()
    val store = remember { WallpaperStore.get(context) }
    val looks = remember { LookStore.get(context) }
    val parts = rememberCurrentParts()
    val favourites = rememberFavouriteApps()
    val pager = rememberPagerState(initialPage = start) { folder.items.size }
    val wallpaper = folder.items[pager.currentPage.coerceIn(0, folder.items.size - 1)]
    var bare by remember { mutableStateOf(false) }
    // Which button is busy setting it ("home", "lock", "both"), or null.
    var busy by remember { mutableStateOf<String?>(null) }

    fun putOn(where: String) {
        if (busy != null) return
        busy = where
        val picked = wallpaper
        scope.launch {
            val ok = withContext(Dispatchers.IO) { store.setFromAsset(picked, where) }
            busy = null
            // The same as a gallery pick: the "follow" theme picks up the new colours by itself.
            looks.refreshWallpaper()
            val on = when (where) { "home" -> "on your home screen"; "lock" -> "on your lock screen"; else -> "on both screens" }
            Toast.makeText(context, if (ok) "Wallpaper set $on" else "Couldn't set that wallpaper", Toast.LENGTH_SHORT).show()
        }
    }

    PageFrame(folder.shortName, "Preview", onBack = onBack) {
        Box(Modifier.staggerIn().fillMaxWidth().height(440.dp)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 40.dp), pageSpacing = 14.dp) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PreviewPhone(folder.items[page], parts, favourites, bare)
                }
            }
        }
        // Where in the folder: a dot per wallpaper, the current one long and gold.
        Row(Modifier.staggerIn().fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center) {
            folder.items.indices.forEach { i ->
                val on = i == pager.currentPage
                val w by animateFloatAsState(if (on) 18f else 6f, tween(400), label = "dot")
                val c by animateColorAsState(if (on) look.accent else look.textDim.copy(alpha = 0.4f), tween(400), label = "dot colour")
                Box(Modifier.padding(horizontal = 3.dp).width(w.dp).height(6.dp).background(c, CircleShape))
            }
        }
        Choice("Show", listOf("home" to "With your home", "bare" to "Just the picture"), if (bare) "bare" else "home") { bare = it == "bare" }

        // What it is and who made it; glides to the next one as he swipes.
        Crossfade(wallpaper, animationSpec = tween(400), label = "about") { w -> WallpaperAbout(w) }

        SectionLabel("Put it on")
        Row(Modifier.staggerIn().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // His "Put it on" choice from the Wallpaper page is the gold one; the other two are one tap away.
            listOf("home" to "Home screen", "lock" to "Lock screen", "both" to "Both").forEach { (where, label) ->
                PutButton(label, primary = store.target == where, working = busy == where, enabled = busy == null) { putOn(where) }
            }
        }
        Hint("Seelie keeps a copy, like a gallery pick: your previews and new saves show it, and with \"Follow wallpaper\" the colours change with it.")
        CreditsCard(onOpen)
    }
}

/** The wallpaper behind his home (or on its own), phone-shaped like the save previews. */
@Composable
private fun PreviewPhone(wallpaper: SeelieWallpaper, parts: SaveParts, favourites: List<com.shamil.launcher.apps.AppEntry>, bare: Boolean) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val picture by produceState<ImageBitmap?>(null, wallpaper.id) {
        value = withContext(Dispatchers.IO) { SeelieWallpapers.picture(context, wallpaper, metrics.widthPixels / 2) }
    }
    val shape = RoundedCornerShape(24.dp)
    Crossfade(bare, animationSpec = tween(400), label = "bare") { justPicture ->
        if (justPicture) Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(metrics.widthPixels.toFloat() / metrics.heightPixels)
                .clip(shape)
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
        ) {
            picture?.let { Image(it, contentDescription = wallpaper.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        } else SavePhone(parts, PhoneWalls(picture), favourites)
    }
}

/** Title, maker and date, then the credit line exactly as the source gives it, with the licence. */
@Composable
private fun WallpaperAbout(w: SeelieWallpaper) {
    val look = LocalLauncherLook.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(w.title, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 17.sp, lineHeight = 22.sp))
        Text("${w.artist} · ${w.date}", modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 10.dp),
            style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp, lineHeight = 18.sp))
        CreditBox(w)
    }
}

/** The credit line in a card (CC BY pictures must keep it visible and unaltered), with the licence as a small pill. */
@Composable
private fun CreditBox(w: SeelieWallpaper) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(look.card)
            .border(1.dp, look.accent.copy(alpha = if (w.attribution) 0.35f else 0.12f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        LicencePill(w.licence)
        Text(w.credit, modifier = Modifier.padding(top = 8.dp),
            style = TextStyle(fontFamily = look.font, color = look.text.copy(alpha = 0.9f), fontSize = 12.5.sp, lineHeight = 18.sp))
    }
}

@Composable
private fun LicencePill(licence: String) {
    val look = LocalLauncherLook.current
    Text(licence.uppercase(), modifier = Modifier.border(1.dp, look.accent.copy(alpha = 0.5f), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 9.sp, letterSpacing = 1.2.sp))
}

/** "Home screen", "Lock screen" or "Both": gold when it's his usual pick, the colours gliding over 0.4 s. */
@Composable
private fun RowScope.PutButton(label: String, primary: Boolean, working: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    val fill by animateColorAsState(if (primary) look.accent else look.card, tween(400), label = "fill")
    val ink by animateColorAsState(if (primary) look.sheet.copy(alpha = 1f) else look.text, tween(400), label = "ink")
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 52.dp)
            .pressFeel(press, look.accent, 16.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, look.accent.copy(alpha = if (primary) 0f else 0.35f), shape)
            .clickable(enabled = enabled, interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (working) "Setting…" else label, maxLines = 2, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = ink.copy(alpha = if (enabled || working) 1f else 0.5f), fontSize = 14.sp))
    }
}

/**
 * Credits: every wallpaper that comes with Seelie, by folder: title, maker, date, museum or agency, licence, the exact
 * credit line, and a link to the source's own page. Tapping the picture opens its preview.
 */
@Composable
private fun CreditsPage(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val folders = remember { SeelieWallpapers.folders(context) }
    PageFrame("Seelie wallpapers", "Credits", onBack = onBack) {
        Hint("Every wallpaper that comes with Seelie: who made it, where it's kept and its licence. The space pictures from ESO, ESA/Webb and ESA/Hubble are shared under CC BY 4.0, so their credit lines are shown exactly as the source gives them. Seelie only cropped the pictures to phone shape.")
        folders.forEach { f ->
            SectionLabel(f.name)
            f.items.forEach { w -> CreditEntry(w) { onOpen(WallPages.pick(w.id)) } }
        }
    }
}

@Composable
private fun CreditEntry(w: SeelieWallpaper, onPreview: () -> Unit) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(22.dp)
    val thumbPress = remember { MutableInteractionSource() }
    val linkPress = remember { MutableInteractionSource() }
    Column(
        Modifier
            .staggerIn()
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)
    ) {
        Row {
            WallThumb(
                w,
                Modifier
                    .width(52.dp)
                    .aspectRatio(9f / 19.5f)
                    .pressFeel(thumbPress, look.accent, 10.dp)
                    .clickable(interactionSource = thumbPress, indication = null, onClick = onPreview),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(w.title, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 15.sp, lineHeight = 20.sp))
                Text(w.artist, modifier = Modifier.padding(top = 2.dp),
                    style = TextStyle(fontFamily = look.font, color = look.text.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp))
                Text("${w.date} · ${w.institution}", modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.5.sp, lineHeight = 17.sp))
                LicencePill(w.licence)
            }
        }
        Text(w.credit, modifier = Modifier.padding(top = 10.dp),
            style = TextStyle(fontFamily = look.font, color = look.text.copy(alpha = 0.9f), fontSize = 12.5.sp, lineHeight = 18.sp))
        // The source's own page for this picture (opens in the browser).
        val host = remember(w.record) { Uri.parse(w.record).host?.removePrefix("www.") ?: w.record }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .pressFeel(linkPress, look.accent, 12.dp)
                .clickable(interactionSource = linkPress, indication = null) {
                    openSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(w.record)))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Open the record", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 13.5.sp))
            Spacer(Modifier.width(8.dp))
            Text(host, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = look.accent)
        }
    }
}
