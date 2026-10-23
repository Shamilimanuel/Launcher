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
 * A live preview of the real row on top (tap it, it works), then: show it or not, which toggles in which order,
 * where between the favourites, round icons or pills, and an honest list of what each button really does.
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

    // The live preview: the very same row as on the home, a little less indented. Faded while the row is switched off.
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(shape)
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(vertical = 6.dp)
            .graphicsLayer { alpha = if (s.show) 1f else 0.4f },
    ) {
        if (s.toggles.isEmpty()) EmptyTogglesHint("Pick a toggle below to see it here", startPadding = 16.dp)
        else QuickTogglesRow(startPadding = 14.dp)
    }
    Hint("This is the real row: tap to try it. Long-press a toggle for its full settings page.")

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
        unit = if (s.after == 1) "favourite" else "favourites", enabled = s.show,
    ) { v -> prefs.change { it.copy(after = v.roundToInt()) } }
    Hint("0 puts the row above your first favourite. With fewer favourites than this, it sits after the last one.")
    Choice("Style", listOf(TogglePrefs.STYLE_ICONS to "Round icons", TogglePrefs.STYLE_PILLS to "Icon + name"), s.style, enabled = s.show) { v ->
        prefs.change { it.copy(style = v) }
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
