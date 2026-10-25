package com.shamil.launcher.settings

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.ui.theme.LocalLauncherLook
import androidx.compose.ui.text.style.TextOverflow
import com.shamil.launcher.ui.theme.LookStore
import com.shamil.launcher.ui.theme.SeelieWallpapers
import com.shamil.launcher.ui.theme.WallpaperStore
import com.shamil.launcher.ui.theme.WallpaperTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Look → Wallpaper (24 Oct): pick his own picture (home, lock screen or both), and let Seelie's colours follow it.
 * Seelie keeps a copy, so the theme previews and new saves show the real wallpaper.
 * 25 Oct: under the gallery button, Seelie's own wallpapers (four folders of real art, [SeelieWallpaperSection]);
 * [onWall] opens their pages (a folder, a preview, the credits).
 */
@Composable
internal fun WallpaperSettingsContent(onWall: (String) -> Unit) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val scope = rememberCoroutineScope()
    val store = remember { WallpaperStore.get(context) }
    val looks = remember { LookStore.get(context) }
    var busy by remember { mutableStateOf(false) }
    val following = looks.themeId == WallpaperTheme.ID

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { store.setFromUri(uri) }
            busy = false
            // New colours arrive from Android a moment later; the "follow" theme picks them up by itself.
            looks.refreshWallpaper()
            Toast.makeText(context, if (ok) "Wallpaper set" else "Couldn't use that picture", Toast.LENGTH_SHORT).show()
        }
    }

    Hint("Pick a picture from your gallery. Seelie keeps a copy, so your theme previews and saves show it too.")

    // The current copy, phone-shaped.
    val picture by produceState<ImageBitmap?>(null, store.version) {
        value = withContext(Dispatchers.IO) {
            runCatching { store.copyFile()?.let { BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() } }.getOrNull()
        }
    }
    Row(Modifier.staggerIn().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier.width(96.dp).aspectRatio(9f / 19.5f).clip(shape).background(look.card)
                .border(1.dp, look.accent.copy(alpha = 0.4f), shape),
            contentAlignment = Alignment.Center,
        ) {
            picture?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
                ?: Text("?", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 22.sp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            // One of Seelie's own: its name (25 Oct).
            val seelie = remember(store.seelieId) { SeelieWallpapers.find(context, store.seelieId) }
            Text(if (picture != null) seelie?.title ?: "Your wallpaper" else "Not picked in Seelie yet", maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 16.sp))
            Text(if (picture != null) (if (seelie != null) "A Seelie wallpaper · " else "") + "Shown in your previews and new saves"
                else "Android doesn't let Seelie see a wallpaper set elsewhere. Pick it here once and it shows everywhere.",
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.5.sp))
        }
    }

    Choice("Put it on", listOf("home" to "Home", "lock" to "Lock screen", "both" to "Both"), store.target) { store.useTarget(it) }
    GroupCard(R.drawable.ic_wallpaper, Anemo, if (busy) "Setting it…" else "Choose from gallery", "Photos, screenshots, anything you saved") {
        if (!busy) pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Seelie's own wallpapers sit right under the gallery: the other place a picture can come from (25 Oct).
    SectionLabel("Seelie wallpapers")
    SeelieWallpaperSection(onWall)

    SectionLabel("Colours")
    Choice("Seelie's colours", listOf("follow" to "Follow wallpaper", "theme" to "Keep my theme"), if (following) "follow" else "theme") {
        if (it == "follow") {
            if (!following) store.rememberTheme(looks.themeId)
            looks.setTheme(WallpaperTheme.ID)
        } else if (following) looks.setTheme(store.themeBefore ?: "fuji")
    }
    // What "follow" gives right now: the colours Android took from the wallpaper.
    looks.wallpaperLook?.let { w ->
        Row(Modifier.staggerIn().padding(start = 4.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(w.accent, w.panel.copy(alpha = 1f), w.sheetTop.copy(alpha = 1f), w.sheet.copy(alpha = 1f)).forEach { c ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(c).border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape))
            }
            Text("from your wallpaper", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.5.sp))
        }
    } ?: Hint("Your phone doesn't share the wallpaper's colours, so \"Follow wallpaper\" uses the Fuji colours.")
    Spacer(Modifier.height(8.dp))
}
