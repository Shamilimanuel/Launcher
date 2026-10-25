package com.shamil.launcher.settings

import com.shamil.launcher.ui.rail.RailStyle
import com.shamil.launcher.ui.rail.RailStore
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
import com.shamil.launcher.ui.home.HomeMode
import com.shamil.launcher.ui.home.HomeModeStore
import com.shamil.launcher.ui.home.LayoutPreset
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.roundToInt

/*
 * Seelie settings → Look → Layout (the layout editor, 24 Oct): whole layouts in one tap, the top's parts
 * (on/off, left/centre/right, how far down, how big), the blocks' order, the favourites' icons, the background
 * fade and the Seelie touches. Moving the clock freely by hand comes next.
 *
 * Settings sorted by "tap the thing" (his pick A, 24 Oct: "the settings is a bit everywhere… we need to order it more"):
 * each slice now lives on the tab of the part it changes, so [part] picks one slice and null still shows the lot.
 * Slices: "layouts", "order", "clock", "date", "glance", "widgets", "favs", "fade", "rail", "thumb", "touches".
 */

private val ALIGN3 = listOf("left" to "Left", "centre" to "Centre", "right" to "Right")
private val BLOCK_NAMES = mapOf(
    BLOCK_TOP to "Clock, date & glance line",
    BLOCK_WIDGETS to "Widgets",
    BLOCK_MUSIC to "Music card",
    BLOCK_FAVS to "Favourites",
)

@Composable
fun ColumnScope.LayoutSettingsContent(part: String? = null, onClockFaces: (() -> Unit)? = null) {
    val context = LocalContext.current
    val store = remember { HomeLayoutStore.get(context) }
    val clocks = remember { ClockStore.get(context) }
    val lay = store.layout
    fun show(p: String) = part == null || part == p
    // On a tab the slice sits next to Home style's slice of the same part, so its heading says what it does there.
    @Composable fun label(whole: String, onTab: String) = SectionLabel(if (part == null) whole else onTab)

    if (part == null) Hint("Your home is a set of blocks. Switch parts on or off, line them up, change their order, or pick a whole layout in one tap.")

    if (show("layouts")) {
        SectionLabel("Layouts")
        LayoutPreset.entries.forEach { preset ->
            PresetRow(preset.title, preset.about, selected = lay.preset == preset.id) {
                store.use(preset.apply(lay))
                preset.face?.let { clocks.useClockStyle(ClockStyle.byId(it)) }
            }
        }
    }

    if (show("clock")) {
        label("Clock", "Where it sits")
        Choice("Clock", ON_OFF, lay.clock.onOff()) { v -> store.change { it.copy(clock = v == "on") } }
        if (onClockFaces != null) GroupCard(R.drawable.ic_schedule, Electro, "Clock face", clocks.clockStyle.label + " · tap to change", onClick = onClockFaces)
        Choice("Clock position", ALIGN3, lay.alignClock) { v -> store.change { it.copy(alignClock = v) } }
        Slide("How far down", lay.clockTop * 100f, 0f..45f, 0, "%") { v -> store.change { it.copy(clockTop = v / 100f) } }
        Slide("Size", lay.clockScale * 100f, 70f..115f, 0, "%") { v -> store.change { it.copy(clockScale = v / 100f) } }
    }

    if (part == null) SectionLabel("Date & glance line")
    if (show("date")) {
        if (part != null) SectionLabel("Where it sits")
        if (clocks.clockStyle == ClockStyle.BigDay) Hint("The Big day clock shows the date itself, so the date line rests.")
        Choice("Date", ON_OFF, lay.date.onOff()) { v -> store.change { it.copy(date = v == "on") } }
        Choice("Date position", ALIGN3, lay.alignDate) { v -> store.change { it.copy(alignDate = v) } }
    }
    if (show("glance")) {
        if (part != null) SectionLabel("Where it sits")
        Choice("Glance line", ON_OFF, lay.glance.onOff()) { v -> store.change { it.copy(glance = v == "on") } }
        Choice("Glance position", ALIGN3, lay.alignGlance) { v -> store.change { it.copy(alignGlance = v) } }
    }

    if (show("order")) {
        SectionLabel("Blocks, top to bottom")
        SeelieOnlyHint()
        Hint(if (part == null) "Move a block up or down. Widgets and favourites can be switched off too."
            else "Move a block up or down. Switch widgets off in Widgets → Stack, favourites in Favourites → List.")
        lay.order.forEachIndexed { i, block ->
            OrderRow(
                name = BLOCK_NAMES[block] ?: block,
                canUp = i > 0, canDown = i < lay.order.lastIndex,
                onUp = { store.change { it.copy(order = it.order.toMutableList().apply { add(i - 1, removeAt(i)) }) } },
                onDown = { store.change { it.copy(order = it.order.toMutableList().apply { add(i + 1, removeAt(i)) }) } },
            )
        }
        Spacer(Modifier.padding(top = 6.dp))
    }
    if (show("widgets")) {
        if (part != null) SectionLabel("On the home")
        Choice("Widgets", ON_OFF, lay.widgets.onOff()) { v -> store.change { it.copy(widgets = v == "on") } }
    }
    if (show("favs")) {
        if (part != null) SectionLabel("On the home")
        Choice("Favourites", ON_OFF, lay.favs.onOff()) { v -> store.change { it.copy(favs = v == "on") } }
        Choice("Group favourites under headings", ON_OFF, lay.group.onOff()) { v -> store.change { it.copy(group = v == "on") } }
        if (lay.group) Hint("Social, Media, Games, Tools…: Seelie sorts them from what each app says it is. Your own order stays inside each group.")
        Choice("Favourites icons", listOf("normal" to "Normal", "small" to "Small", "text" to "Text only"), lay.icons) { v ->
            store.change { it.copy(icons = v) }
        }
    }

    if (show("fade")) {
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
    }

    val rails = remember { RailStore.get(context) }
    if (show("rail")) {
        SectionLabel(if (part == null) "A–Z rail" else "Rail style")
        Hint("Seelie's own rails, in the order you picked them. The arc ones sit where your thumb rests: drag along the curve to pick a letter (a tap still opens what's under it).")
        // 25 Oct (audit P4): the wheel's hot corner sits where the arc would, so the home swaps it; say so.
        Hint("In the Radial wheel home style an arc rail becomes the edge Guide rail, so it stays clear of the wheel's corner.")
        if (part == null) LeftThumb(rails)
        RailStyle.entries.filter { it.built }.forEach { style ->
            PresetRow(style.title, style.about, selected = rails.style == style) { rails.use(style) }
        }
    }
    if (part == "thumb") {
        SectionLabel("Left thumb")
        LeftThumb(rails)
    }

    if (show("touches")) {
        SectionLabel("Seelie touches")
        Hint("Little things only Seelie has, from the spirit in its icon.")
        Choice("Sparkles by the clock", ON_OFF, lay.sparkles.onOff()) { v -> store.change { it.copy(sparkles = v == "on") } }
        Hint("Coming next: move the clock freely with your finger, turn and tilt it.")
    }
}

/** Left thumb: the empty left edge moves the rail too, the rail on the left, or off. */
@Composable
private fun LeftThumb(rails: RailStore) {
    val side = com.shamil.launcher.ui.rail.RailSide
    Choice("Left thumb", listOf("edge" to "Left edge works too", "rail" to "Rail on the left", "off" to "Off"),
        when { side.left -> "rail"; side.edge -> "edge"; else -> "off" }) { rails.useLeftThumb(it) }
    Hint("Left edge works too: slide your thumb up and down the empty left side and the A–Z on the right follows, as if your thumb were there. A tap there still opens what's under it.")
}

/** One layout in the Layouts list: its name and what it does; a gold ring when it's the one in use. */
@Composable
internal fun PresetRow(title: String, about: String, selected: Boolean, onClick: () -> Unit) {
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

/** Home style: today's tall page, Poster or the Radial wheel (his 25 Oct picks; more styles come later). */
@Composable
internal fun HomeModeChoice() {
    val context = LocalContext.current
    val modes = remember { HomeModeStore.get(context) }
    SectionLabel("Home style")
    Hint("How your home works. Your saves remember it, so each save can have its own.")
    HomeMode.entries.forEach { m ->
        PresetRow(m.title, m.about, selected = modes.mode == m) { modes.use(m) }
    }
    // 25 Oct (audit P1): say plainly what the two new styles leave out, so the pages for those don't seem broken.
    Hint("Poster and Radial wheel leave out the widgets, music card, quick toggles, notification lines, stats card and event line. Those show in the Seelie style.")
}

/**
 * "Only used in the Seelie home style" (25 Oct, audit P1): for settings pages that change nothing in Poster or the
 * Radial wheel. Shows only while one of those is the home style.
 */
@Composable
internal fun SeelieOnlyHint() {
    val context = LocalContext.current
    val mode = remember { HomeModeStore.get(context) }.mode
    if (mode != HomeMode.SEELIE) Hint("Only used in the Seelie home style. Your home is ${mode.title} now (Theme & style → Bundles & layouts).")
}
