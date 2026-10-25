package com.shamil.launcher.ui.toggles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.notify.NotifyHub
import com.shamil.launcher.settings.Choice
import com.shamil.launcher.settings.Hint
import com.shamil.launcher.settings.ON_OFF
import com.shamil.launcher.settings.SectionLabel
import com.shamil.launcher.settings.Slide
import com.shamil.launcher.settings.onOff
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.roundToInt

/*
 * Seelie settings → Home screen → Quick toggles.
 * A live preview of the real row on top (tap it, it works; shown here even while the row is hidden on the home),
 * then: show it, which toggles in which order, where, the buttons (size, spacing, shape, names), colours,
 * the card behind it, and an honest list of what each button really does.
 */

@Composable
fun ColumnScope.TogglesSettingsContent() {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val prefs = remember { TogglePrefs.get(context) }
    val s = prefs.setup
    // Listening to "connected" makes this page notice when he comes back from switching Notification access on.
    val connected by NotifyHub.connected.collectAsState()
    val quietCanSwitch = connected || NotifyHub.isEnabled(context)

    // The live preview: the very same row as on the home (a little less indented), always shown here.
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(shape)
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(vertical = 6.dp),
    ) {
        if (s.toggles.isEmpty()) EmptyTogglesHint("Pick a toggle below to see it here", startPadding = 16.dp)
        else QuickTogglesRow(startPadding = 14.dp)
    }
    Hint(
        if (s.show) "This is the real row: tap to try it. Long-press a toggle for its full settings page."
        else "Hidden on your home for now: only this preview shows it. Tap to try it, then switch it on below when you like it."
    )

    Choice("Show between your favourites", ON_OFF, s.show.onOff()) { v -> prefs.change { it.copy(show = v == "on") } }

    SectionLabel("Toggles · in the order you pick them")
    QuickToggle.entries.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            pair.forEach { t ->
                val index = s.toggles.indexOf(t)
                val available = remember(t) { ToggleActions.isAvailable(context, t) }
                val sub = when {
                    !available -> "Not on this phone"
                    t == QuickToggle.QUIET && !quietCanSwitch -> "Needs notification access"
                    else -> t.how.tag
                }
                ToggleChip(t, sub, number = if (index >= 0) index + 1 else null, enabled = available) { prefs.flip(t) }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))   // keep the last one half-width
        }
    }

    SectionLabel("Place")
    Slide(
        "Sits after", s.after.toFloat(), 0f..TogglePrefs.MAX_AFTER.toFloat(), steps = TogglePrefs.MAX_AFTER - 1,
        unit = if (s.after == 1) "favourite" else "favourites",
    ) { v -> prefs.change { it.copy(after = v.roundToInt()) } }
    Hint("0 puts the row above your first favourite. With fewer favourites than this, it sits after the last one.")
    Choice("Line up", listOf("start" to "With the icons", "center" to "Centred", "spread" to "Spread out"), s.align) { v ->
        prefs.change { it.copy(align = v) }
    }

    SectionLabel("Buttons")
    Choice("Size", listOf("small" to "Small", "medium" to "Medium", "large" to "Large"), s.size) { v -> prefs.change { it.copy(size = v) } }
    Slide(
        "Space between", s.gap.toFloat(), TogglePrefs.MIN_GAP.toFloat()..TogglePrefs.MAX_GAP.toFloat(),
        steps = (TogglePrefs.MAX_GAP - TogglePrefs.MIN_GAP) / 2 - 1, unit = "dp",
    ) { v -> prefs.change { it.copy(gap = (v / 2f).roundToInt() * 2) } }   // in steps of 2dp
    Choice("Shape", listOf("circle" to "Circle", "squircle" to "Squircle", "pill" to "Pill"), s.shape) { v -> prefs.change { it.copy(shape = v) } }
    Choice("Inside the button", listOf(TogglePrefs.STYLE_ICONS to "Icon only", TogglePrefs.STYLE_PILLS to "Icon + name"), s.style) { v ->
        prefs.change { it.copy(style = v) }
    }
    Choice("Name under each button", ON_OFF, s.labels.onOff(), enabled = s.style == TogglePrefs.STYLE_ICONS) { v ->
        prefs.change { it.copy(labels = v == "on") }
    }
    Choice("On / Off line", ON_OFF, s.stateLine.onOff()) { v -> prefs.change { it.copy(stateLine = v == "on") } }

    SectionLabel("Colours")
    Choice("When on", listOf("accent" to "Accent", "gold" to "Gold", "white" to "White", "home" to "Home style"), s.onColour) { v ->
        prefs.change { it.copy(onColour = v) }
    }
    Choice("Glow when on", ON_OFF, s.glow.onOff()) { v -> prefs.change { it.copy(glow = v == "on") } }
    Choice("When off", listOf("glass" to "Glass", "soft" to "Soft fill", "bare" to "Icon only"), s.offLook) { v ->
        prefs.change { it.copy(offLook = v) }
    }
    Hint("\"Home style\" uses the same accent as your widget card (Widgets → Card): the theme's colour or gold.")

    SectionLabel("Behind the row")
    Choice("Backing", listOf("none" to "Nothing", "card" to "A card"), s.backing) { v -> prefs.change { it.copy(backing = v) } }
    Choice("\"Quick toggles\" heading", ON_OFF, s.cardLabel.onOff(), enabled = s.backing == "card") { v ->
        prefs.change { it.copy(cardLabel = v == "on") }
    }
    Hint("The card matches your widget card: its style, see-through level, corners and border all follow Widgets → Card.")

    SectionLabel("Volume")
    Hint("Seelie's side bar slides in from the edge when you press the phone's volume buttons, in any app, or tap Volume here.")
    if (!com.shamil.launcher.volume.SeelieVolumeService.running) {
        Hint("For the volume buttons, Android wants Seelie switched on under Accessibility → Installed apps → Seelie. " +
            "Seelie only listens to the two volume buttons there, nothing else. With the screen off, during calls and while " +
            "the phone rings, the phone's own volume stays.")
        LinkChip("Turn on in Android settings") { ToggleActions.open(context, android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    } else if (s.volumeLook == VolumeLook.SYSTEM.id) {
        Hint("Look is \"Phone's own\", so the volume buttons show the phone's own bar. Pick another look to use Seelie's.")
    } else {
        Choice("Volume buttons", listOf("seelie" to "Seelie's bar", "phone" to "Phone's own"), if (s.volumeKeys) "seelie" else "phone") { v -> prefs.change { it.copy(volumeKeys = v == "seelie") } }
        Hint("The camera and a ringing alarm keep the buttons for themselves (shutter, snooze).")
    }
    Choice("Look", listOf(VolumeLook.GOLD, VolumeLook.STARS, VolumeLook.THREAD).map { it.id to it.title }, s.volumeLook) { v -> prefs.change { it.copy(volumeLook = v) } }
    Choice("", listOf(VolumeLook.PETALS, VolumeLook.TRAIL, VolumeLook.LANTERN).map { it.id to it.title }, s.volumeLook) { v -> prefs.change { it.copy(volumeLook = v) } }
    Choice("More", listOf(VolumeLook.GLASS.id to "Glass", VolumeLook.ELEMENT.id to "Elements", VolumeLook.WALLPAPER.id to "Wallpaper", VolumeLook.SYSTEM.id to "Phone's own"), s.volumeLook) { v -> prefs.change { it.copy(volumeLook = v) } }
    if (s.volumeLook != VolumeLook.SYSTEM.id) {
        Choice("Edge", listOf("right" to "Right", "left" to "Left"), s.volumeEdge) { v -> prefs.change { it.copy(volumeEdge = v) } }
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = if (s.volumeEdge == "left") Alignment.CenterStart else Alignment.CenterEnd) {
            VolumeSideBar(9, 15, VolumeLook.byId(s.volumeLook), "Ring", s.volumeEdge == "left", onChange = {}, onRinger = {})
        }
        Choice("Soft click on each step", ON_OFF, s.volumeClick.onOff()) { v -> prefs.change { it.copy(volumeClick = v == "on") } }
    }

    SectionLabel("What each one really does")
    Hint(
        "Android only lets apps switch a few things themselves. Since Android 10 no app may flip Wi-Fi, Bluetooth, " +
            "mobile data or airplane mode, so those buttons take you there in one tap instead. " +
            "Where Android lets Seelie read it, the button lights up when it's on."
    )
    ToggleHow.entries.forEach { how ->
        val names = QuickToggle.entries.filter { it.how == how }.joinToString(", ") { it.short }
        HowLine(how.what, names)
    }
    if (!quietCanSwitch) {
        Hint("Quiet switches right here once Notification access is on for Seelie. Until then a tap explains and opens that page.")
        LinkChip("Turn on Notification access") { ToggleActions.open(context, NotifyHub.accessIntent(context)) }
    }
}

/** A toggle to add or take out; when picked it shows its place in the order (1, 2, 3…) in a springy accent circle. */
@Composable
private fun RowScope.ToggleChip(t: QuickToggle, sub: String, number: Int?, enabled: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val on = number != null
    val pop by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "toggle picked")
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .weight(1f)
            .staggerIn()
            .height(56.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .pressFeel(press, look.accent, 16.dp)
            .clip(shape)
            .background(if (on) look.accent.copy(alpha = 0.16f) else look.card)
            .border(1.dp, if (on) look.accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f), shape)
            .clickable(enabled = enabled, interactionSource = press, indication = null, onClick = onClick)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(t.icon), contentDescription = null, tint = if (on) look.accent else look.textDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.short, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = if (on) look.text else look.textDim, fontSize = 15.sp))
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, color = look.textDim.copy(alpha = 0.8f), fontSize = 11.sp))
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(24.dp).graphicsLayer { scaleX = pop; scaleY = pop }.background(look.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (number != null) Text("$number", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, color = look.sheet.copy(alpha = 1f), fontSize = 12.sp))
        }
    }
}

/** "Switches right here: Torch, Quiet" as a small two-line card. */
@Composable
private fun HowLine(what: String, names: String) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(what, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 13.sp))
        Text(names, modifier = Modifier.padding(top = 2.dp), style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 14.sp, lineHeight = 19.sp))
    }
}

/** An outlined accent button, for "go and switch this on". */
@Composable
private fun LinkChip(text: String, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Text(
        text,
        modifier = Modifier
            .staggerIn()
            .padding(start = 4.dp, bottom = 12.dp)
            .pressFeel(press, look.accent, 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, look.accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 14.sp),
    )
}
