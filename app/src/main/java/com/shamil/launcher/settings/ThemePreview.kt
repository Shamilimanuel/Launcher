package com.shamil.launcher.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppCache
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.Favourites
import com.shamil.launcher.ui.home.Gold
import com.shamil.launcher.ui.home.HomeBundle
import com.shamil.launcher.ui.home.HomeStyle
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LauncherLook
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.LookStore
import com.shamil.launcher.ui.theme.SavedLook
import com.shamil.launcher.ui.theme.SavesStore
import com.shamil.launcher.ui.theme.WallpaperTheme
import com.shamil.launcher.ui.theme.fontById
import com.shamil.launcher.ui.theme.themeById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Theme preview, like Niagara's (his ask, 23 Oct): each saved theme as a full phone-shaped preview (its wallpaper,
 * clock, date, his real favourites, the A–Z), swipe sideways between them, "Apply theme" at the bottom.
 */

/** The full-screen preview of his saved themes, starting at [startIndex]. [onApply] applies the one he's on. */
@Composable
fun ThemePreviewDialog(saves: List<SavedLook>, startIndex: Int, onApply: (SavedLook) -> Unit, onDismiss: () -> Unit) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (saves.size - 1).coerceAtLeast(0))) { saves.size }
    // His favourites as the home shows them (from Seelie's saved list, so nothing has to load again).
    val favourites by produceState(emptyList<AppEntry>()) {
        value = withContext(Dispatchers.IO) {
            val all = AppCache(context).readIndex()
            Favourites(context).packages.mapNotNull { pkg -> all.firstOrNull { it.component.packageName == pkg } }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF0101218))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // ← back, like Niagara.
                val press = remember { MutableInteractionSource() }
                Box(
                    Modifier.padding(start = 18.dp, top = 10.dp).size(48.dp).pressFeel(press, look.accent, 24.dp).clip(CircleShape)
                        .background(look.accent.copy(alpha = 0.85f)).clickable(press, null, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = look.sheet.copy(alpha = 1f)) }
                // The phones: the next one peeks in at the side.
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp),
                    contentPadding = PaddingValues(horizontal = 46.dp),
                    pageSpacing = 16.dp,
                ) { page ->
                    val save = saves[page]
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            PhonePreview(save, favourites)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(save.name, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp))
                    }
                }
                // ✓ Apply theme.
                val apply = remember { MutableInteractionSource() }
                Row(
                    Modifier.align(Alignment.CenterHorizontally).padding(bottom = 18.dp).pressFeel(apply, look.accent, 24.dp).clip(RoundedCornerShape(24.dp))
                        .background(look.accent.copy(alpha = 0.9f))
                        .clickable(apply, null) { saves.getOrNull(pager.currentPage)?.let(onApply) }
                        .padding(horizontal = 30.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("✓", style = TextStyle(color = look.sheet.copy(alpha = 1f), fontSize = 20.sp, fontWeight = FontWeight.Bold))
                    Spacer(Modifier.width(12.dp))
                    Text("Apply theme", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.sheet.copy(alpha = 1f), fontSize = 18.sp))
                }
            }
        }
    }
}

/** One saved theme drawn as a little phone: its wallpaper (or colours), its clock and date, his favourites, the A–Z. */
@Composable
private fun PhonePreview(save: SavedLook, favourites: List<AppEntry>) {
    val context = LocalContext.current
    val store = remember { SavesStore.get(context) }
    // What the save says about its look (without applying it).
    val parsed = remember(save.id, save.snapshot.toString()) { parseLook(context, save) }
    val (look, hs) = parsed
    val wallpaper by produceState<ImageBitmap?>(null, save.id, store.saves) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = store.wallpaperFile(save)
                if (f.exists()) BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() else null
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(24.dp)
    val now = remember { LocalDateTime.now() }
    Box(
        Modifier
            .fillMaxHeight()
            .aspectRatio(9f / 19.5f)
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
        val textShadow = Shadow(Color(0x99000000), blurRadius = 8f)
        val clockStyle = TextStyle(
            fontFamily = if (hs.clockFont == "cinzel") Cinzel else look.font,
            fontWeight = FontWeight(hs.clockWeight.coerceIn(300, 800)),
            fontSize = (hs.clockSize * 0.62f).sp, shadow = textShadow,
        ).let {
            when (hs.clockColor) {
                "gold" -> it.copy(brush = Brush.verticalGradient(listOf(Color(0xFFFFF0C8), Gold, Color(0xFFB8995F))))
                "accent" -> it.copy(color = look.accent)
                "glass" -> it.copy(brush = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.6f))))
                else -> it.copy(color = look.text)
            }
        }
        Column(Modifier.padding(start = 18.dp, top = 40.dp, end = 30.dp)) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = clockStyle)
            Text(now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())).replace(".", ""),
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = (hs.dateSize * 0.62f).sp, shadow = textShadow))
            Spacer(Modifier.height(14.dp))
            favourites.take(12).forEach { app ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 11.sp, shadow = textShadow))
                }
            }
        }
        // The A–Z rail on the right.
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp, top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ("☆#" + ('A'..'Z').joinToString("") + "•").forEach { c ->
                Text(c.toString(), style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = 7.sp, lineHeight = 9.sp, shadow = textShadow))
            }
        }
    }
}

/** The look (colours, font, text colour) and home style a save describes, read from its snapshot. */
private fun parseLook(context: android.content.Context, save: SavedLook): Pair<LauncherLook, HomeStyle> {
    fun str(file: String, key: String): String? = save.snapshot.optJSONObject(file)?.optJSONObject(key)?.optString("v")?.takeIf { it.isNotEmpty() }
    val themeId = str("look", "theme")
    var look = if (themeId == WallpaperTheme.ID) LookStore.get(context).wallpaperLook ?: themeById(null) else themeById(themeId)
    str("look", "font")?.let { look = look.copy(font = fontById(it)) }
    LookStore.TEXT_COLOURS[str("look", "text_colour")]?.let { c ->
        val text = if (c == Color.Unspecified) look.accent else c
        look = look.copy(text = text, textDim = text.copy(alpha = 0.82f))
    }
    val bundle = HomeBundle.byId(str("home_style", "bundle"))
    val hs = HomeStyle.fromJson(str("home_style", "style"), bundle.style)
    return look to hs
}
