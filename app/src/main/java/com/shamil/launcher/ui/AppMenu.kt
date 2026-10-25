package com.shamil.launcher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.DefaultHome
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.Apps
import com.shamil.launcher.apps.GestureStore
import com.shamil.launcher.settings.SeelieSettings
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Soft red for Uninstall, the one action that can't be undone. */
private val Warn = Color(0xFFFF9A9A)

/**
 * The app menu: long-press any app and this sheet slides up from the bottom.
 * Same actions as Niagara's (plus Rename), laid out as grouped rounded cards: the "a bit dry" fix
 * he approved in look review 1 (23 Oct). Only actions that really work are shown; the prototype's
 * gestures, vault and Serenitea cards join when those features are built.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenuSheet(
    app: AppEntry,
    originalName: String,
    isFavourite: Boolean,
    isHidden: Boolean,
    onToggleFavourite: () -> Unit,
    onEditFavourites: () -> Unit,
    onToggleHidden: () -> Unit,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Opens the "swipe left" chooser for this app. */
    onSwipeLeft: () -> Unit = {},
    /** Puts the app in the PIN vault. */
    onMoveToVault: () -> Unit = {},
    /** Locks or unlocks the app (stays visible, asks the code to open). */
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
    /** Uninstall; null = Android's "Uninstall this app?" straight away. The home asks a locked app's code first (25 Oct). */
    onUninstall: (() -> Unit)? = null,
) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val isSystem = remember(app) { Apps.isSystemApp(context, app) }
    var renaming by remember { mutableStateOf(false) }

    /** Slide the sheet away, then do [action] (so e.g. App info opens after the sheet is gone). */
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = look.sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(0f to look.sheetTop, 0.4f to look.sheet))
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
        ) {
            // The grabber: the little bar that says "drag me down to close".
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 4.dp)
                    .background(look.text.copy(alpha = 0.28f), CircleShape)
            )
            Spacer(Modifier.height(16.dp))
            Header(app, isSystem)

            GroupLabel("Quick")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(R.drawable.ic_star, if (isFavourite) "In favourites" else "Favourite",
                    if (isFavourite) "Tap to remove" else "Add to home", on = isFavourite) { closeThen(onToggleFavourite) }
                Tile(R.drawable.ic_info, "App info", "System page") { closeThen { Apps.openInfo(context, app) } }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(R.drawable.ic_hourglass, "Screen time", "Digital Wellbeing") { closeThen { Apps.openScreenTime(context, app) } }
                Tile(R.drawable.ic_edit, "Rename", "Only here") { renaming = true }
            }

            GroupLabel("Gestures")
            val left = GestureStore.get(context).actionFor(app.component.packageName)
            Card(R.drawable.ic_touch_app, "Swipe left", (left?.label ?: "Not set") + " · tap to change (swipe right = shortcuts)") { closeThen(onSwipeLeft) }

            GroupLabel("Manage")
            Card(R.drawable.ic_drag_indicator, "Edit favourites", "Reorder or remove") { closeThen(onEditFavourites) }
            Card(
                if (isHidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
                if (isHidden) "Show in app list" else "Hide from app list",
                if (isHidden) "Back in the A–Z" else "Find it again under the •",
            ) { closeThen(onToggleHidden) }
            Card(R.drawable.ic_lock, if (isLocked) "Unlock" else "Lock with code",
                if (isLocked) "Opens without the code again" else "Stays in your list · asks your code or fingerprint to open") { closeThen(onToggleLock) }
            Card(R.drawable.ic_lock, "Move to vault", "PIN-locked, gone from the list and search · hold • to open") { closeThen(onMoveToVault) }
            if (app.component.packageName == context.packageName) {
                // "Seelie settings" is part of Seelie: uninstalling it would remove the whole launcher.
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_info), null, tint = look.textDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Part of Seelie, so it can't be uninstalled on its own.",
                        style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
                }
            } else if (isSystem) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_info), null, tint = look.textDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("This app came with the phone and can't be uninstalled.",
                        style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
                }
            } else {
                Card(R.drawable.ic_delete, "Uninstall", null, color = Warn) { closeThen { onUninstall?.invoke() ?: Apps.uninstall(context, app) } }
            }

            GroupLabel("Seelie")
            Card(R.drawable.ic_settings, "Seelie settings", "Themes, fonts and more") { closeThen { SeelieSettings.open(context) } }
            Card(R.drawable.ic_home, "Switch Home app", "Pick Seelie or another launcher") {
                closeThen { openSafely(context, DefaultHome.settingsIntent()) }
            }
        }
    }

    if (renaming) {
        RenameDialog(
            current = app.label,
            original = originalName,
            onDone = { newName ->
                renaming = false
                if (newName != null) closeThen { onRename(newName) }
            },
        )
    }
}

/** The app's icon with a soft glow in the accent colour, its name, and when it was installed. */
@Composable
private fun Header(app: AppEntry, isSystem: Boolean) {
    val look = LocalLauncherLook.current
    val sub = remember(app.installedAt, isSystem) {
        if (isSystem) "Came with the phone"
        else "Installed " + DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
            .format(Instant.ofEpochMilli(app.installedAt).atZone(ZoneId.systemDefault())).replace(".", "")
    }
    Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(60.dp)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(listOf(look.accent.copy(alpha = 0.38f), Color.Transparent), radius = size.minDimension),
                        radius = size.minDimension,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = 25.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(look.accent, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(sub, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp))
            }
        }
    }
}

/** A small accent-coloured heading over a group of cards, with a hairline running to the right. */
@Composable
internal fun GroupLabel(text: String) {
    val look = LocalLauncherLook.current
    Row(Modifier.padding(start = 6.dp, top = 20.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent,
            fontSize = 11.sp, letterSpacing = 2.4.sp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(look.accent.copy(alpha = 0.3f), Color.Transparent))))
    }
}

/** A half-width square-ish tile for the quick actions: icon on top, title, small line. [on] tints it with the accent. */
@Composable
private fun RowScope.Tile(@DrawableRes icon: Int, title: String, sub: String, on: Boolean = false, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(18.dp)
    val fill = if (on) Brush.linearGradient(listOf(look.accent.copy(alpha = 0.26f), look.card)) else Brush.linearGradient(listOf(look.card, look.card))
    Column(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(fill)
            .border(1.dp, Color.White.copy(alpha = if (on) 0.14f else 0.06f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = look.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 16.sp))
        Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
    }
}

/** A full-width rounded card: icon, title and an optional small line under it. */
@Composable
internal fun Card(@DrawableRes icon: Int, title: String, sub: String?, color: Color? = null, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .padding(bottom = 7.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color ?: look.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = TextStyle(fontFamily = look.font, color = color ?: look.text, fontSize = 17.sp))
            if (sub != null) Text(sub, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.5.sp))
        }
    }
}

/** Type a new name. "Original name" puts the app's own name back. [onDone] gets null on Cancel. */
@Composable
private fun RenameDialog(current: String, original: String, onDone: (String?) -> Unit) {
    val look = LocalLauncherLook.current
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = look.sheet,
        title = { Text("Rename", color = look.text) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = look.font, color = look.text, fontSize = 18.sp),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text("Save", color = look.accent) } },
        dismissButton = {
            Row {
                if (current != original) {
                    TextButton(onClick = { onDone("") }) { Text("Original name", color = look.textDim) }
                }
                TextButton(onClick = { onDone(null) }) { Text("Cancel", color = look.textDim) }
            }
        },
        // Keep the default rounded corners; just make sure nothing renders on a white card.
        titleContentColor = look.text,
        textContentColor = Color.Unspecified,
    )
}
