package com.shamil.launcher.ui.widgets

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.settings.Hint
import com.shamil.launcher.settings.IconTile
import com.shamil.launcher.settings.SectionLabel
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.theme.LocalLauncherLook

/** Soft red for "remove", like Uninstall in the long-press sheet. */
private val SoftRed = Color(0xFFFF9A9A)

/**
 * Seelie settings → Home screen → Widget stack (goes inside a PageFrame).
 * A live preview of the stack, his widgets in order (↑ / ↓ to move, ✕ to remove),
 * "Add a widget" cards for the ones not in the stack yet, and the card style.
 */
@Composable
fun ColumnScope.WidgetStackSettingsContent(onAddFromApp: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { WidgetStore.get(context) }
    val look = LocalLauncherLook.current
    val stack = store.stack

    Hint("Swipe sideways on the home screen to flip through your widgets. Add as many as you like and put them in any order.")

    // The real stack, so every change shows straight away.
    if (stack.isNotEmpty()) {
        WidgetStack(Modifier.staggerIn())
    } else {
        Text(
            "The stack is empty, so nothing shows on the home screen. Add a widget below.",
            modifier = Modifier
                .staggerIn()
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(look.card)
                .padding(16.dp),
            style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp, lineHeight = 20.sp),
        )
    }

    SectionLabel(if (stack.size == 1) "In your stack · 1 widget" else "In your stack · ${stack.size} widgets")
    stack.forEachIndexed { index, entry ->
        // key: each row stays tied to its widget when the order changes.
        key(entry.key) {
            val onUp = { store.move(index, index - 1) }
            val onDown = { store.move(index, index + 1) }
            when (entry) {
                is StackEntry.BuiltIn -> StackRow(
                    icon = { IconTile(entry.kind.icon, entry.kind.tint) },
                    title = entry.kind.title, about = entry.kind.about, tint = entry.kind.tint,
                    canMoveUp = index > 0, canMoveDown = index < stack.lastIndex,
                    onUp = onUp, onDown = onDown, onRemove = { store.remove(entry) },
                )
                is StackEntry.App -> AppStackRow(
                    entry = entry, store = store,
                    canMoveUp = index > 0, canMoveDown = index < stack.lastIndex,
                    onUp = onUp, onDown = onDown,
                    onRemove = {
                        store.remove(entry)
                        SeelieWidgetHost.delete(context, entry.appWidgetId)
                    },
                )
            }
        }
    }
    if (stack.isEmpty()) Hint("Nothing here yet.")

    SectionLabel("Add a widget")
    // Real widgets from his other apps (Spotify, Calendar, weather...).
    AddRow(R.drawable.ic_widgets, look.accent, "From an app…", "Any widget your apps offer, like on a normal home screen", onAddFromApp)
    store.notAdded.forEach { kind -> key(kind.id) { AddRow(kind.icon, kind.tint, kind.title, kind.about) { store.add(kind) } } }

    SectionLabel("Card look")
    Hint("How the widget card looks (no card, glass, solid or outline, how see-through, corners, border, compact) now lives in Look → Home style, so it always matches your clock and date.")
}

/** A widget from another app in his stack: its app's icon and name, ↑ ↓ ✕, and a Small / Medium / Large switch. */
@Composable
private fun AppStackRow(
    entry: StackEntry.App, store: WidgetStore,
    canMoveUp: Boolean, canMoveDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    // Android's info about the widget: its name, and its app's icon. Null if the app was uninstalled.
    val details = remember(entry.appWidgetId) {
        runCatching {
            val info = SeelieWidgetHost.manager(context).getAppWidgetInfo(entry.appWidgetId) ?: return@runCatching null
            val pm = context.packageManager
            val app = pm.getApplicationInfo(info.provider.packageName, 0)
            Triple(info.loadLabel(pm), pm.getApplicationLabel(app).toString(), pm.getApplicationIcon(app).toBitmap(96, 96).asImageBitmap())
        }.getOrNull()
    }
    Column(Modifier.padding(bottom = 8.dp)) {
        StackRow(
            icon = {
                if (details != null) Image(details.third, contentDescription = null, modifier = Modifier.size(50.dp))
                else IconTile(R.drawable.ic_widgets, look.textDim)
            },
            title = details?.first ?: "Widget (app removed)",
            about = details?.let { "From ${it.second}" } ?: "Its app is gone, remove it here",
            tint = look.accent,
            canMoveUp = canMoveUp, canMoveDown = canMoveDown, onUp = onUp, onDown = onDown, onRemove = onRemove,
            bottomGap = false,
        )
        // How tall it is on the home screen.
        Row(Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetSize.entries.forEach { size ->
                StyleChip(size.title, selected = store.sizeOf(entry) == size, modifier = Modifier.weight(1f)) { store.resize(entry, size) }
            }
        }
    }
}

/** A widget in his stack: its tile, name and one line, then ↑ ↓ ✕. */
@Composable
private fun StackRow(
    icon: @Composable () -> Unit, title: String, about: String, tint: Color,
    canMoveUp: Boolean, canMoveDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit,
    bottomGap: Boolean = true,
) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .staggerIn()
            .padding(bottom = if (bottomGap) 8.dp else 0.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.10f), look.card)))
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 16.sp))
            Text(about, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp, lineHeight = 16.sp))
        }
        Spacer(Modifier.width(6.dp))
        // The › arrow turned up and down makes the ↑ / ↓ buttons.
        RoundButton(R.drawable.ic_chevron_right, "Move up", look.text, enabled = canMoveUp, turn = -90f, onClick = onUp)
        RoundButton(R.drawable.ic_chevron_right, "Move down", look.text, enabled = canMoveDown, turn = 90f, onClick = onDown)
        RoundButton(R.drawable.ic_delete, "Remove", SoftRed, onClick = onRemove)
    }
}

/** A small round button with an icon; faded out and not tappable when [enabled] is false. */
@Composable
private fun RoundButton(@DrawableRes icon: Int, label: String, tint: Color, enabled: Boolean = true, turn: Float = 0f, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(36.dp)
            .pressFeel(press, look.accent, 18.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.07f else 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable(enabled = enabled, interactionSource = press, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon), contentDescription = label,
            tint = tint.copy(alpha = if (enabled) 1f else 0.28f),
            modifier = Modifier.size(20.dp).rotate(turn),
        )
    }
}

/** A widget he can add: tile, name, one line and a + . Tap anywhere on it to add it at the end of the stack. */
@Composable
private fun AddRow(@DrawableRes icon: Int, tint: Color, title: String, about: String, onAdd: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(20.dp)
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .staggerIn()
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .pressFeel(press, tint, 20.dp)
            .clip(shape)
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onAdd)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 16.sp))
            Text(about, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp, lineHeight = 16.sp))
        }
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_add_circle), contentDescription = "Add", tint = look.accent, modifier = Modifier.size(26.dp))
    }
}

/** One choice of the card style; the picked one glows in the accent. */
@Composable
private fun StyleChip(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(16.dp)
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(48.dp)
            .pressFeel(press, look.accent, 16.dp)
            .clip(shape)
            .background(if (selected) look.accent.copy(alpha = 0.18f) else look.card)
            .border(1.dp, if (selected) look.accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = TextStyle(
                fontFamily = look.font, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) look.accent else look.text, fontSize = 15.sp,
            ),
        )
    }
}
