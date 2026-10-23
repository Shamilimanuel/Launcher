package com.shamil.launcher.settings

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppCache
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.Favourites
import com.shamil.launcher.ui.ROW_START
import com.shamil.launcher.ui.clock.ClockStore
import com.shamil.launcher.ui.clock.ClockStyle
import com.shamil.launcher.ui.clock.LocalClockStyleOverride
import com.shamil.launcher.ui.clock.rememberClockTime
import com.shamil.launcher.ui.home.HomeBundle
import com.shamil.launcher.ui.home.HomeStyle
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.home.HomeTop
import com.shamil.launcher.ui.home.LocalHomeStyleOverride
import com.shamil.launcher.ui.rememberGlanceState
import com.shamil.launcher.ui.theme.LauncherLook
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.LookStore
import com.shamil.launcher.ui.theme.SavedLook
import com.shamil.launcher.ui.theme.SavesStore
import com.shamil.launcher.ui.theme.WallpaperTheme
import com.shamil.launcher.ui.theme.themeById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/*
 * Theme previews, like Niagara's (his ask, 23 Oct): each theme drawn as a little phone with the REAL home parts
 * (his clock face, date look, glance line, app rows and the A–Z) in that theme's styles, without applying it.
 * The first page is what he has right now; swipe sideways for his saved themes.
 */

/** What a preview needs to draw a look: its colours/font, home style and clock face. */
private class PreviewLook(val look: LauncherLook, val home: HomeStyle, val clock: ClockStyle)

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
    val look = LocalLauncherLook.current
    val pageCount = saves.size + 1
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, pageCount - 1)) { pageCount }
    val favourites = rememberFavouriteApps()
    var confirmDelete by remember { mutableStateOf<SavedLook?>(null) }
    val save = saves.getOrNull(pager.currentPage - 1)   // null = the current look

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF0101218))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // ← back, like Niagara.
                    RoundIcon(R.drawable.ic_arrow_back, "Back", filled = true, onClick = onDismiss)
                    Spacer(Modifier.weight(1f))
                    // 🗑 quick delete of the theme he's looking at (not for "Current look").
                    if (save != null) RoundIcon(R.drawable.ic_delete, "Delete theme", filled = false) { confirmDelete = save }
                }
                // The phones: the next one peeks in at the side.
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp),
                    contentPadding = PaddingValues(horizontal = 46.dp),
                    pageSpacing = 16.dp,
                ) { page ->
                    val pageSave = saves.getOrNull(page - 1)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (pageSave == null) CurrentLookPhone(favourites) else SavedLookPhone(pageSave, favourites)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(pageSave?.name ?: "Current look",
                            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp))
                    }
                }
                // ✓ Apply theme (or, on "Current look", save it as a theme).
                val press = remember { MutableInteractionSource() }
                Row(
                    Modifier.align(Alignment.CenterHorizontally).padding(bottom = 18.dp).pressFeel(press, look.accent, 24.dp).clip(RoundedCornerShape(24.dp))
                        .background(look.accent.copy(alpha = 0.9f))
                        .clickable(press, null) { if (save != null) onApply(save) else onSaveCurrent() }
                        .padding(horizontal = 30.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (save != null) "✓" else "+", style = TextStyle(color = look.sheet.copy(alpha = 1f), fontSize = 20.sp, fontWeight = FontWeight.Bold))
                    Spacer(Modifier.width(12.dp))
                    Text(if (save != null) "Apply theme" else "Save as a theme",
                        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.sheet.copy(alpha = 1f), fontSize = 18.sp))
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

/** His look right now, as a phone. */
@Composable
internal fun CurrentLookPhone(favourites: List<AppEntry>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val p = PreviewLook(
        LookStore.get(context).look,
        HomeStyleStore.get(context).style,
        remember { ClockStore.get(context) }.clockStyle,
    )
    PhonePreview(p, wallpaper = null, favourites, modifier)
}

/** A saved theme as a phone, with its own wallpaper picture if it has one. */
@Composable
internal fun SavedLookPhone(save: SavedLook, favourites: List<AppEntry>, modifier: Modifier = Modifier, small: Boolean = false) {
    val context = LocalContext.current
    val store = remember { SavesStore.get(context) }
    val p = remember(save.id, save.snapshot.toString(), LookStore.get(context).wallpaperLook) { readSave(context, save.snapshot) }
    val wallpaper by produceState<ImageBitmap?>(null, save.id, store.saves) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = store.wallpaperFile(save)
                if (!f.exists()) null
                else BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = if (small) 8 else 2 })?.asImageBitmap()
            }.getOrNull()
        }
    }
    PhonePreview(p, wallpaper, favourites, modifier)
}

/**
 * The phone: the home laid out at the real screen size, then shrunk to fit (so the proportions are exactly the home's).
 * Without a wallpaper picture it shows the theme's colours (newer Android doesn't let Seelie read the wallpaper itself).
 */
@Composable
private fun PhonePreview(p: PreviewLook, wallpaper: ImageBitmap?, favourites: List<AppEntry>, modifier: Modifier) {
    val metrics = LocalContext.current.resources.displayMetrics
    val density = LocalDensity.current
    val screenW = with(density) { metrics.widthPixels.toDp() }
    val screenH = with(density) { metrics.heightPixels.toDp() }
    val shape = RoundedCornerShape(24.dp)
    val look = p.look
    val time = rememberClockTime()
    val glance = rememberGlanceState()
    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .aspectRatio(screenW / screenH)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 1f), look.sheet.copy(alpha = 1f))))
            .drawBehind {
                if (wallpaper == null) drawCircle(
                    Brush.radialGradient(listOf(look.accent.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.3f, 0f), radius = size.width),
                    radius = size.width, center = Offset(size.width * 0.3f, 0f),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
    ) {
        wallpaper?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        val scale = maxWidth / screenW
        CompositionLocalProvider(
            LocalLauncherLook provides look,
            LocalHomeStyleOverride provides p.home,
            LocalClockStyleOverride provides p.clock,
        ) {
            Box(
                Modifier.layout { measurable, constraints ->
                    val full = measurable.measure(Constraints.fixed(screenW.roundToPx(), screenH.roundToPx()))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        full.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
                    }
                }
            ) {
                Column(Modifier.fillMaxSize().padding(end = 56.dp)) {
                    Spacer(Modifier.height(screenH * 0.105f))
                    HomeTop(time = time, glance = glance)
                    Spacer(Modifier.height(12.dp + p.home.gap3.dp))
                    CompositionLocalProvider(LocalLauncherLook provides look.forPart("apps")) {
                        val rowLook = LocalLauncherLook.current
                        favourites.take(10).forEach { app ->
                            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = ROW_START), verticalAlignment = Alignment.CenterVertically) {
                                Image(app.icon, contentDescription = null, modifier = Modifier.size(44.dp * rowLook.iconScale))
                                Spacer(Modifier.width(20.dp))
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(fontFamily = rowLook.font, color = rowLook.text, fontSize = 17.sp, shadow = rowLook.shadow))
                            }
                        }
                    }
                }
                // The A–Z rail on the right, in the theme's rail colour.
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ("☆" + ('A'..'Z').joinToString("") + "•").forEach { c ->
                        Text(c.toString(), style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.rail,
                            fontSize = 12.sp, lineHeight = 19.sp, shadow = look.shadow))
                    }
                }
            }
        }
        // Taps on the little phone do nothing (it's a picture); swipes still reach the pager.
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {})
    }
}

/** The look, home style and clock face a save describes, read from its snapshot (without applying it). */
private fun readSave(context: Context, snapshot: JSONObject): PreviewLook {
    fun entry(file: String, key: String) = snapshot.optJSONObject(file)?.optJSONObject(key)
    fun str(file: String, key: String): String? = entry(file, key)?.optString("v")?.takeIf { it.isNotEmpty() }
    fun set(file: String, key: String): Set<String>? = entry(file, key)?.optJSONArray("v")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
    val themeId = str("look", "theme")
    val theme = if (themeId == WallpaperTheme.ID) LookStore.get(context).wallpaperLook ?: themeById(null) else themeById(themeId)
    val look = LookStore.build(
        theme = theme,
        fontId = str("look", "font"),
        iconScale = entry("look", "icon_scale")?.optDouble("v")?.toFloat()?.takeIf { !it.isNaN() } ?: 1f,
        textColour = str("look", "text_colour"),
        colourParts = set("look", "colour_parts") ?: LookStore.COLOUR_PARTS.map { it.first }.toSet(),
        railColour = str("look", "rail_colour") ?: "text",
    )
    val bundle = HomeBundle.byId(str("home_style", "bundle"))
    return PreviewLook(look, HomeStyle.fromJson(str("home_style", "style"), bundle.style), ClockStyle.byId(str("clock", "clock_style")))
}
