package com.shamil.launcher.settings

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.SavedLook
import com.shamil.launcher.ui.theme.SavesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Seelie settings → Look → My saves (his idea, 23 Oct): save the whole look under a name, give each save its own
 * wallpaper, and switch between them in one tap.
 */

@Composable
fun ColumnScope.SavesSettingsContent(
    /** false on the Themes page ("Your themes" section): no intro text, no own heading. */
    intro: Boolean = true,
) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val store = remember { SavesStore.get(context) }
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf<SavedLook?>(null) }      // renaming this save
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedLook?>(null) }
    var pickingFor by remember { mutableStateOf<SavedLook?>(null) }
    // The photo picker: no permission needed, he picks one picture.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val save = pickingFor ?: return@rememberLauncherForActivityResult
        pickingFor = null
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { store.setWallpaper(save, uri) }
            Toast.makeText(context, if (ok) "Wallpaper added to \"${save.name}\"" else "Couldn't read that picture", Toast.LENGTH_SHORT).show()
        }
    }

    if (intro) Hint("Save your whole look (theme, font, text colours, icons, home style, clock, stats, music card and toggles) under a name. Give a save its own wallpaper, and switch between them in one tap.")
    SaveButton("+ Save current look as a theme") { creating = true }

    if (intro) SectionLabel(if (store.saves.isEmpty()) "No saves yet" else "Your themes · ${store.saves.size}")
    else if (store.saves.isEmpty()) Hint("Nothing saved yet. Your saved looks show up here, the way Niagara's \"your themes\" do.")
    store.saves.forEach { save ->
        SaveCard(
            save = save,
            store = store,
            onApply = {
                scope.launch {
                    withContext(Dispatchers.IO) { store.apply(save) }
                    store.reloadStores()
                    Toast.makeText(context, "\"${save.name}\" applied", Toast.LENGTH_SHORT).show()
                }
            },
            onUpdate = {
                store.update(save)
                Toast.makeText(context, "\"${save.name}\" now holds your current look", Toast.LENGTH_SHORT).show()
            },
            onWallpaper = {
                pickingFor = save
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemoveWallpaper = { store.removeWallpaper(save) },
            onRename = { naming = save },
            onDelete = { deleting = save },
        )
    }

    if (creating) NameDialog("Name this look", "My look ${store.saves.size + 1}", onDone = { name ->
        creating = false
        if (name != null) {
            store.saveCurrent(name)
            Toast.makeText(context, "Saved \"$name\"", Toast.LENGTH_SHORT).show()
        }
    })
    naming?.let { save ->
        NameDialog("Rename", save.name, onDone = { name ->
            naming = null
            if (name != null) store.rename(save, name)
        })
    }
    deleting?.let { save ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = look.sheet,
            title = { Text("Delete \"${save.name}\"?", color = look.text) },
            text = { Text("Your current look stays as it is; only this save (and its wallpaper copy) goes.", color = look.textDim) },
            confirmButton = { TextButton({ store.delete(save); deleting = null }) { Text("Delete", color = Color(0xFFFF9A9A)) } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel", color = look.textDim) } },
        )
    }
}

/** One save: its wallpaper thumbnail (or its colours), name, theme, and the actions. */
@Composable
private fun SaveCard(
    save: SavedLook, store: SavesStore,
    onApply: () -> Unit, onUpdate: () -> Unit, onWallpaper: () -> Unit, onRemoveWallpaper: () -> Unit,
    onRename: () -> Unit, onDelete: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(20.dp)
    val hasWallpaper = store.hasWallpaper(save)
    // The thumbnail, decoded small and off the main thread; redone when the saves list changes (a new picture).
    val thumb by produceState<ImageBitmap?>(null, store.saves, save.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = store.wallpaperFile(save)
                if (!f.exists()) null else BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Column(
        Modifier.staggerIn().padding(bottom = 12.dp).fillMaxWidth().clip(shape).background(look.card)
            .border(1.dp, Color(save.accent).copy(alpha = 0.5f), shape).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 56.dp, height = 96.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Color(save.sheet), Color(save.accent).copy(alpha = 0.6f)))),
                contentAlignment = Alignment.Center,
            ) {
                thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 56.dp, height = 96.dp)) }
                if (thumb == null) Box(Modifier.size(18.dp).background(Color(save.accent), CircleShape))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(save.name, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = 18.sp))
                Text(save.themeName + if (hasWallpaper) " · with wallpaper" else "", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp))
                Spacer(Modifier.height(8.dp))
                SaveButton("Apply", filled = true, onClick = onApply)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallChip(if (hasWallpaper) "Change wallpaper" else "Add wallpaper", onWallpaper)
            if (hasWallpaper) SmallChip("No wallpaper", onRemoveWallpaper)
            SmallChip("Update to current", onUpdate)
            SmallChip("Rename", onRename)
            SmallChip("Delete", onDelete, colour = Color(0xFFFF9A9A))
        }
    }
}

@Composable
private fun SaveButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Text(
        text,
        modifier = Modifier.staggerIn().padding(bottom = 4.dp).pressFeel(press, look.accent, 18.dp).clip(CircleShape)
            .background(if (filled) look.accent else Color.Transparent)
            .border(1.dp, look.accent.copy(alpha = if (filled) 0f else 0.7f), CircleShape)
            .clickable(press, null, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            color = if (filled) look.sheet.copy(alpha = 1f) else look.accent),
    )
}

@Composable
private fun SmallChip(text: String, onClick: () -> Unit, colour: Color? = null) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Text(
        text,
        modifier = Modifier.pressFeel(press, look.accent, 14.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .clickable(press, null, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colour ?: look.text),
    )
}

/** A small dialog to type a name; [onDone] gets null on Cancel. */
@Composable
private fun NameDialog(title: String, start: String, onDone: (String?) -> Unit) {
    val look = LocalLauncherLook.current
    var text by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = look.sheet,
        title = { Text(title, color = look.text) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                textStyle = TextStyle(fontFamily = look.font, color = look.text, fontSize = 18.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = look.accent, cursorColor = look.accent,
                    focusedTextColor = look.text, unfocusedTextColor = look.text),
            )
        },
        confirmButton = { TextButton({ onDone(text.trim()) }) { Text("Save", color = look.accent) } },
        dismissButton = { TextButton({ onDone(null) }) { Text("Cancel", color = look.textDim) } },
    )
}
