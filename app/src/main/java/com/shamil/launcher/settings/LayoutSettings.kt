package com.shamil.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.ui.clock.ClockStore
import com.shamil.launcher.ui.clock.ClockStyle
import com.shamil.launcher.ui.home.BLOCK_FAVS
import com.shamil.launcher.ui.home.BLOCK_MUSIC
import com.shamil.launcher.ui.home.BLOCK_TOP
import com.shamil.launcher.ui.home.BLOCK_WIDGETS
import com.shamil.launcher.ui.home.HomeLayoutStore
import com.shamil.launcher.ui.home.LayoutPreset
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.roundToInt

/*
 * Seelie settings → Look → Layout (the layout editor, 24 Oct): whole layouts in one tap, the top's parts
 * (on/off, left/centre/right, how far down, how big), the blocks' order, the favourites' icons, the background
 * fade and the Seelie touches. Moving the clock freely by hand comes next.
 */

private val ALIGN3 = listOf("left" to "Left", "centre" to "Centre", "right" to "Right")
private val BLOCK_NAMES = mapOf(
    BLOCK_TOP to "Clock, date & glance line",
    BLOCK_WIDGETS to "Widgets",
    BLOCK_MUSIC to "Music card",
    BLOCK_FAVS to "Favourites",
)

@Composable
fun ColumnScope.LayoutSettingsContent(onClockFaces: () -> Unit) {
    val context = LocalContext.current
    val store = remember { HomeLayoutStore.get(context) }
    val clocks = remember { ClockStore.get(context) }
    val lay = store.layout

    Hint("Your home is a set of blocks. Switch parts on or off, line them up, change their order, or pick a whole layout in one tap.")

    SectionLabel("Layouts")
    LayoutPreset.entries.forEach { preset ->
        PresetRow(preset.title, preset.about, selected = lay.preset == preset.id) {
            store.use(preset.apply(lay))
            preset.face?.let { clocks.useClockStyle(ClockStyle.byId(it)) }
        }
    }

    SectionLabel("Clock")
    Choice("Clock", ON_OFF, lay.clock.onOff()) { v -> store.change { it.copy(clock = v == "on") } }
    GroupCard(R.drawable.ic_schedule, Electro, "Clock face", clocks.clockStyle.label + " · tap to change", onClick = onClockFaces)
    Choice("Clock position", ALIGN3, lay.alignClock) { v -> store.change { it.copy(alignClock = v) } }
    Slide("How far down", lay.clockTop * 100f, 0f..45f, 0, "%") { v -> store.change { it.copy(clockTop = v / 100f) } }
    Slide("Size", lay.clockScale * 100f, 70f..115f, 0, "%") { v -> store.change { it.copy(clockScale = v / 100f) } }

    SectionLabel("Date & glance line")
    if (clocks.clockStyle == ClockStyle.BigDay) Hint("The Big day clock shows the date itself, so the date line rests.")
    Choice("Date", ON_OFF, lay.date.onOff()) { v -> store.change { it.copy(date = v == "on") } }
    Choice("Date position", ALIGN3, lay.alignDate) { v -> store.change { it.copy(alignDate = v) } }
    Choice("Glance line", ON_OFF, lay.glance.onOff()) { v -> store.change { it.copy(glance = v == "on") } }
    Choice("Glance position", ALIGN3, lay.alignGlance) { v -> store.change { it.copy(alignGlance = v) } }

    SectionLabel("Blocks, top to bottom")
    Hint("Move a block up or down. Widgets and favourites can be switched off too.")
    lay.order.forEachIndexed { i, block ->
        OrderRow(
            name = BLOCK_NAMES[block] ?: block,
            canUp = i > 0, canDown = i < lay.order.lastIndex,
            onUp = { store.change { it.copy(order = it.order.toMutableList().apply { add(i - 1, removeAt(i)) }) } },
            onDown = { store.change { it.copy(order = it.order.toMutableList().apply { add(i + 1, removeAt(i)) }) } },
        )
    }
    Spacer(Modifier.padding(top = 6.dp))
    Choice("Widgets", ON_OFF, lay.widgets.onOff()) { v -> store.change { it.copy(widgets = v == "on") } }
    Choice("Favourites", ON_OFF, lay.favs.onOff()) { v -> store.change { it.copy(favs = v == "on") } }
    Choice("Favourites icons", listOf("normal" to "Normal", "small" to "Small", "text" to "Text only"), lay.icons) { v ->
        store.change { it.copy(icons = v) }
    }

    SectionLabel("Background fade")
    Hint("Show your wallpaper only at the top and let it melt into a colour below. Nice for a calm, readable list.")
    Choice("Fade", ON_OFF, lay.fade.onOff()) { v -> store.change { it.copy(fade = v == "on") } }
    Slide("Wallpaper shows down to", lay.fadeReach.toFloat(), 0f..100f, 0, "%", enabled = lay.fade) { v ->
        store.change { it.copy(fadeReach = v.roundToInt()) }
    }
    Slide("Softness", lay.fadeSoft.toFloat(), 0f..100f, 0, "%", enabled = lay.fade) { v -> store.change { it.copy(fadeSoft = v.roundToInt()) } }
    Choice("Fade colour", listOf("black" to "Black", "theme" to "Theme", "navy" to "Navy"), lay.fadeColour, enabled = lay.fade) { v ->
        store.change { it.copy(fadeColour = v) }
    }

    SectionLabel("Seelie touches")
    Hint("Little things only Seelie has, from the spirit and the light trail in its icon.")
    Choice("Light trail", ON_OFF, lay.trail.onOff()) { v -> store.change { it.copy(trail = v == "on") } }
    Choice("Sparkles by the clock", ON_OFF, lay.sparkles.onOff()) { v -> store.change { it.copy(sparkles = v == "on") } }
    Hint("Coming next: move the clock freely with your finger, turn and tilt it.")
}

/** One layout in the Layouts list: its name and what it does; a gold ring when it's the one in use. */
@Composable
private fun PresetRow(title: String, about: String, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.staggerIn().padding(bottom = 10.dp).fillMaxWidth().pressFeel(press, look.accent, 20.dp).clip(shape)
            .background(look.card).border(if (selected) 2.dp else 1.dp, if (selected) look.accent else Color.White.copy(alpha = 0.08f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = 17.sp))
            if (selected) Text("✓", style = TextStyle(color = look.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }
        Text(about, modifier = Modifier.padding(top = 4.dp), style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
    }
}

/** One block in the order list, with ↑ and ↓. */
@Composable
private fun OrderRow(name: String, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.staggerIn().padding(bottom = 8.dp).fillMaxWidth().clip(shape).background(look.card).padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 16.sp))
        ArrowButton("↑", canUp, onUp)
        Spacer(Modifier.width(4.dp))
        ArrowButton("↓", canDown, onDown)
    }
}

@Composable
private fun ArrowButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(44.dp).pressFeel(press, look.accent, 22.dp).clip(CircleShape)
            .clickable(interactionSource = press, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TextStyle(color = if (enabled) look.accent else look.textDim.copy(alpha = 0.3f), fontSize = 20.sp, fontWeight = FontWeight.Bold))
    }
}
