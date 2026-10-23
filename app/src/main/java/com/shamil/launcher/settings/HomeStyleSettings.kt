package com.shamil.launcher.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.clock.rememberClockTime
import android.text.format.DateFormat
import com.shamil.launcher.ui.clock.ClockFace
import com.shamil.launcher.ui.clock.ClockStore
import com.shamil.launcher.ui.clock.ClockStyle
import com.shamil.launcher.ui.home.BundleDate
import com.shamil.launcher.ui.home.DATE_LOOKS
import com.shamil.launcher.ui.home.Gold
import com.shamil.launcher.ui.home.HomeBundle
import com.shamil.launcher.ui.home.HomeStyle
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.home.HomeTop
import com.shamil.launcher.ui.rememberGlanceState
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.widgets.WidgetStack
import kotlin.math.roundToInt

/*
 * Seelie settings → Look → Home style (23 Oct, he said "add every single one" of the bundles, and
 * "make it customizable to the very last detail"): a live preview of the home's top, the five bundles,
 * then every Fine-tune control from the bundles page. Changing anything marks it Custom; Reset goes back.
 */

/** What picking a card style sets the see-through level and border to (like the bundles page), so a change always shows. */
private val CARD_DEFAULTS = mapOf("none" to (0 to false), "glass" to (55 to true), "solid" to (90 to false), "outline" to (78 to true))

@Composable
fun ColumnScope.HomeStyleSettingsContent() {
    val context = LocalContext.current
    val store = remember { HomeStyleStore.get(context) }
    val s = store.style
    fun change(edit: (HomeStyle) -> HomeStyle) = store.change(edit)

    Hint("A bundle styles your clock, date, the line under it and the widget card as one matching set. Pick one, then change any detail below.")

    SectionLabel(if (store.isCustom) "Bundles · yours is custom" else "Bundles")
    HomeBundle.entries.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            pair.forEach { b -> BundleTile(b, selected = store.bundle == b, custom = store.bundle == b && store.isCustom) { store.useBundle(b) } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (store.isCustom) {
        ResetChip("Reset to ${store.bundle.title}") { store.reset() }
    }

    SectionLabel("Clock")
    // Every clock face as a tile (23 Oct: clock and date now live only here). "Home style" is the one the controls below shape.
    val clocks = remember { ClockStore.get(context) }
    val clockTime = rememberClockTime()
    val is24h = DateFormat.is24HourFormat(context)
    ClockStyle.entries.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            pair.forEach { face ->
                DateTile(face.label, selected = clocks.clockStyle == face, onClick = { clocks.useClockStyle(face) }) {
                    ClockFace(face, clockTime, is24h, scale = if (face == ClockStyle.Stacked || face == ClockStyle.Vertical || face == ClockStyle.Words) 0.32f else 0.42f)
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Hint(if (clocks.clockStyle == ClockStyle.Classic) "Shape the Home style clock below." else "Font, size, weight and colour below shape the \"Home style\" face; ${clocks.clockStyle.label} has its own look.")
    Choice("Font", listOf("theme" to "Theme font", "cinzel" to "Cinzel"), s.clockFont) { v -> change { it.copy(clockFont = v) } }
    Slide("Size", s.clockSize, 30f..64f, 33, "sp") { v -> change { it.copy(clockSize = v.roundToInt().toFloat()) } }
    Choice("Weight", listOf("300" to "Light", "600" to "Medium", "800" to "Bold"), nearestWeight(s.clockWeight)) { v -> change { it.copy(clockWeight = v.toInt()) } }
    Choice("Colour", listOf("white" to "White", "accent" to "Accent", "gold" to "Gold", "glass" to "Glass"), s.clockColor) { v -> change { it.copy(clockColor = v) } }

    SectionLabel("Date")
    // Every date look as a tile that draws today's date in that look (12 of them; he asked for more, 23 Oct).
    val time = rememberClockTime()
    DATE_LOOKS.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            pair.forEach { (id, title) ->
                DateTile(title, selected = s.dateStyle == id, onClick = { change { it.copy(dateStyle = id) } }) {
                    BundleDate(time, scale = 0.72f, lookOverride = id)
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Slide("Size", s.dateSize, 11f..20f, 17, "sp") { v -> change { it.copy(dateSize = (v * 2).roundToInt() / 2f) } }
    Choice("Colour", COLOURS, s.dateColor) { v -> change { it.copy(dateColor = v) } }
    Choice("Hairline under the date", ON_OFF, s.dateRule.onOff()) { v -> change { it.copy(dateRule = v == "on") } }

    SectionLabel("Line under the date")
    Choice("Style", listOf("plain" to "Plain", "pill" to "Pill", "glow" to "Glow"), s.glanceStyle) { v -> change { it.copy(glanceStyle = v) } }
    Slide("Size", s.glanceSize, 11f..18f, 13, "sp") { v -> change { it.copy(glanceSize = (v * 2).roundToInt() / 2f) } }
    Choice("Colour", COLOURS, s.glanceColor) { v -> change { it.copy(glanceColor = v) } }
    Choice("Date and this line on one line", ON_OFF, s.inline.onOff()) { v -> change { it.copy(inline = v == "on") } }

    SectionLabel("Widget card")
    Choice("Style", listOf("none" to "None", "glass" to "Glass", "solid" to "Solid", "outline" to "Outline"), s.cardStyle) { v ->
        val (opacity, border) = CARD_DEFAULTS.getValue(v)
        change { it.copy(cardStyle = v, cardOpacity = opacity, cardBorder = border) }
    }
    val noCard = s.cardStyle == "none"
    Slide("See-through (0% = fully transparent)", s.cardOpacity.toFloat(), 0f..100f, 19, "%", enabled = !noCard) { v -> change { it.copy(cardOpacity = v.roundToInt()) } }
    Slide("Corner rounding", s.cardRadius.toFloat(), 0f..36f, 35, "dp", enabled = !noCard) { v -> change { it.copy(cardRadius = v.roundToInt()) } }
    Choice("Border", ON_OFF, s.cardBorder.onOff(), enabled = !noCard) { v -> change { it.copy(cardBorder = v == "on") } }
    Choice("Widget size", listOf("full" to "Full", "compact" to "Compact"), s.cardSize) { v -> change { it.copy(cardSize = v) } }
    Choice("Headings and rings", listOf("theme" to "Theme accent", "gold" to "Gold"), s.cardAccent) { v -> change { it.copy(cardAccent = v) } }
    Choice("Today marker", COLOURS, s.cardToday) { v -> change { it.copy(cardToday = v) } }

    SectionLabel("Spacing and edge")
    Slide("Clock to date", s.gap1.toFloat(), 0f..16f, 15, "dp") { v -> change { it.copy(gap1 = v.roundToInt()) } }
    Slide("Date to the line", s.gap2.toFloat(), 0f..20f, 19, "dp") { v -> change { it.copy(gap2 = v.roundToInt()) } }
    Slide("Line to the widget", s.gap3.toFloat(), 6f..32f, 25, "dp") { v -> change { it.copy(gap3 = v.roundToInt()) } }
    Slide("Left edge (all four)", s.edge.toFloat(), 24f..64f, 39, "dp") { v -> change { it.copy(edge = v.roundToInt()) } }

    SectionLabel("The Seelie touch")
    Choice("Spirit, breathing colon and sparkle", ON_OFF, s.spirit.onOff()) { v -> change { it.copy(spirit = v == "on") } }
    if (store.isCustom) ResetChip("Reset to ${store.bundle.title}") { store.reset() }
}

private val COLOURS = listOf("white" to "White", "accent" to "Accent", "gold" to "Gold")
internal val ON_OFF = listOf("off" to "Off", "on" to "On")
internal fun Boolean.onOff() = if (this) "on" else "off"
private fun nearestWeight(w: Int) = listOf(300, 600, 800).minBy { kotlin.math.abs(it - w) }.toString()

/**
 * The real top of the home (clock, date, line, widget stack) on a little night sky, so every change shows at once.
 * It's pinned above the controls and drawn at [scale] (smaller), so it stays in view while he scrolls.
 */
@Composable
internal fun HomePreview(scale: Float = 0.7f) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(24.dp)
    val glance = rememberGlanceState()
    val time = rememberClockTime()
    Box(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 1f), look.sheet.copy(alpha = 1f))))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(look.accent.copy(alpha = 0.32f), Color.Transparent), center = Offset(size.width * 0.25f, 0f), radius = size.width * 0.7f),
                    radius = size.width * 0.7f, center = Offset(size.width * 0.25f, 0f),
                )
            }
            .sparkles(look.accent.copy(alpha = 1f), count = 12)
            .border(1.dp, look.accent.copy(alpha = 0.3f), shape)
            .padding(top = 12.dp, bottom = 10.dp, end = 12.dp)
    ) {
        val hs = HomeStyleStore.get(LocalContext.current).style
        // Laid out at full size, then shrunk: the layout itself takes only the smaller space.
        Column(Modifier.layout { measurable, constraints ->
            val full = measurable.measure(constraints.copy(minWidth = 0, maxWidth = (constraints.maxWidth / scale).toInt()))
            layout((full.width * scale).toInt(), (full.height * scale).toInt()) {
                full.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
            }
        }) {
            // The edge inside the preview is the real one minus the screen's own margin, so it fits on this card.
            HomeTop(time = time, glance = glance, modifier = Modifier.padding(start = 0.dp))
            Spacer(Modifier.height((hs.gap3).dp))
            WidgetStack(Modifier.padding(start = hs.edge.dp))
        }
        Text(
            "LIVE PREVIEW",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color(0x99000000), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp, letterSpacing = 1.5.sp),
        )
    }
}

/** One bundle to pick: a tiny sample of its clock and card in its own colours, its name and feel, a ✓ when it's yours. */
@Composable
private fun RowScope.BundleTile(b: HomeBundle, selected: Boolean, custom: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    val st = b.style
    Column(
        Modifier
            .weight(1f)
            .staggerIn()
            .heightIn(min = 170.dp)
            .pressFeel(press, look.accent, 20.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 1f), look.sheet.copy(alpha = 1f))))
            .border(if (selected) 2.dp else 1.dp, if (selected) look.accent else Color.White.copy(alpha = 0.08f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // The bundle's clock, small, in its own font and colour.
            val clockStyle = TextStyle(
                fontFamily = if (st.clockFont == "cinzel") Cinzel else look.font,
                fontWeight = FontWeight(st.clockWeight.coerceIn(300, 800)), fontSize = 24.sp,
            ).let {
                when (st.clockColor) {
                    "gold" -> it.copy(brush = Brush.verticalGradient(listOf(Color(0xFFFFF0C8), Gold, Color(0xFFB8995F))))
                    "glass" -> it.copy(brush = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.6f))))
                    "accent" -> it.copy(color = look.accent)
                    else -> it.copy(color = look.text)
                }
            }
            Text("21:09", modifier = Modifier.weight(1f), style = clockStyle)
            AnimatedVisibility(selected, enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                Box(Modifier.size(22.dp).background(look.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", style = TextStyle(color = look.sheet.copy(alpha = 1f), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        // The card swatch: how the widget card looks in this bundle.
        Spacer(Modifier.height(8.dp))
        val cardShape = RoundedCornerShape((st.cardRadius / 2).dp)
        val fill = when (st.cardStyle) {
            "glass" -> look.panel.copy(alpha = st.cardOpacity / 100f)
            "solid" -> look.sheetTop.copy(alpha = st.cardOpacity / 100f)
            "outline" -> Color(0xFF0E1126).copy(alpha = st.cardOpacity / 100f)
            else -> Color.Transparent
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(fill, cardShape)
                .then(if (st.cardBorder) Modifier.border(1.dp, if (st.cardAccent == "gold") Gold.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f), cardShape) else Modifier)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) { i ->
                    Box(Modifier.size(6.dp).background(if (i == 2) (if (st.cardAccent == "gold") Gold else look.accent) else look.text.copy(alpha = 0.5f), CircleShape))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.title, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = 15.sp))
            if (custom) {
                Spacer(Modifier.width(6.dp))
                Text("CUSTOM", modifier = Modifier.border(1.dp, look.accent, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp),
                    style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 8.sp, letterSpacing = 1.2.sp))
            }
        }
        Text(b.feel, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 11.sp, lineHeight = 14.sp))
    }
}

/** One date look to pick: the real date drawn in that look, its name under it, an accent border + ✓ when picked. */
@Composable
private fun RowScope.DateTile(title: String, selected: Boolean, onClick: () -> Unit, sample: @Composable () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .weight(1f)
            .staggerIn()
            .height(96.dp)
            .pressFeel(press, look.accent, 18.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 1f), look.sheet.copy(alpha = 1f))))
            .border(if (selected) 2.dp else 1.dp, if (selected) look.accent else Color.White.copy(alpha = 0.08f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.CenterStart) { sample() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = if (selected) look.accent else look.textDim, fontSize = 12.sp))
            AnimatedVisibility(selected, enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                Box(Modifier.size(18.dp).background(look.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", style = TextStyle(color = look.sheet.copy(alpha = 1f), fontSize = 10.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

/** A row of choices: the label, then pill buttons; the picked one is filled with the accent. */
@Composable
internal fun Choice(label: String, options: List<Pair<String, String>>, selected: String, enabled: Boolean = true, onPick: (String) -> Unit) {
    val look = LocalLauncherLook.current
    Column(Modifier.staggerIn().padding(bottom = 12.dp).then(if (enabled) Modifier else Modifier.background(Color.Transparent))) {
        Text(label, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.textDim.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 13.sp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(look.card)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEach { (value, title) ->
                val on = value == selected
                val press = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .pressFeel(press, look.accent, 11.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (on && enabled) look.accent else Color.Transparent)
                        .clickable(enabled = enabled, interactionSource = press, indication = null) { onPick(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title, maxLines = 1, style = TextStyle(
                        fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = when {
                            !enabled -> look.textDim.copy(alpha = 0.35f)
                            on -> look.sheet.copy(alpha = 1f)
                            else -> look.textDim
                        },
                    ))
                }
            }
        }
    }
}

/** A slider with its label and value ("47 sp"). */
@Composable
internal fun Slide(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, unit: String, enabled: Boolean = true, onChange: (Float) -> Unit) {
    val look = LocalLauncherLook.current
    Column(Modifier.staggerIn().padding(start = 4.dp, end = 4.dp, bottom = 6.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.textDim.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 13.sp))
            val shown = if (value % 1f == 0f) value.roundToInt().toString() else value.toString()
            Text(if (unit == "%") "$shown%" else "$shown $unit",
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = if (enabled) look.accent else look.textDim.copy(alpha = 0.4f), fontSize = 13.sp))
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = look.accent, activeTrackColor = look.accent, inactiveTrackColor = look.text.copy(alpha = 0.18f),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/** "Reset to <bundle>": throws away the tweaks. */
@Composable
private fun ResetChip(text: String, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Text(
        text,
        modifier = Modifier
            .padding(bottom = 12.dp)
            .pressFeel(press, look.accent, 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, look.accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 14.sp),
    )
}

/**
 * Look → Fonts: the text colour for all of Seelie (his ask, 23 Oct: "change the color of our letters") as round swatches,
 * and whether the A–Z letters follow it or use the accent.
 */
@Composable
internal fun TextColourSettings(looks: com.shamil.launcher.ui.theme.LookStore) {
    val look = LocalLauncherLook.current
    SectionLabel("Text colour")
    Row(Modifier.staggerIn().fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        // First: the theme's own text colour, then every colour he can pick.
        val options = listOf<Pair<String?, Color>>(null to com.shamil.launcher.ui.theme.themeById(looks.themeId).text) +
            com.shamil.launcher.ui.theme.LookStore.TEXT_COLOURS.map { (id, c) -> id to (if (c == Color.Unspecified) look.accent else c) }
        options.forEach { (id, colour) ->
            val on = looks.textColour == id
            val press = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(40.dp)
                    .pressFeel(press, colour, 20.dp)
                    .border(if (on) 2.dp else 1.dp, if (on) look.accent else Color.White.copy(alpha = 0.18f), CircleShape)
                    .padding(4.dp)
                    .background(colour, CircleShape)
                    .clickable(interactionSource = press, indication = null) { looks.useTextColour(id) },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    on -> Text("✓", style = TextStyle(color = Color(0xFF171C29), fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    id == null -> Text("T", style = TextStyle(color = Color(0xFF171C29), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
    Hint("T = your theme's own colour.")
    // Which parts get the colour; the others keep the theme's text colour.
    Text("Colour these", modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.textDim, fontSize = 13.sp))
    com.shamil.launcher.ui.theme.LookStore.COLOUR_PARTS.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            pair.forEach { (id, title) ->
                val on = id in looks.colourParts
                val press = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .pressFeel(press, look.accent, 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) look.accent.copy(alpha = 0.16f) else look.card)
                        .border(1.dp, if (on) look.accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                        .clickable(interactionSource = press, indication = null) { looks.toggleColourPart(id) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, modifier = Modifier.weight(1f), maxLines = 1,
                        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = if (on) look.text else look.textDim, fontSize = 13.sp))
                    if (on) Text("✓", style = TextStyle(color = look.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Choice("A–Z letters", listOf("text" to "Same as text", "accent" to "Accent colour"), looks.railColour) { looks.useRailColour(it) }
}
