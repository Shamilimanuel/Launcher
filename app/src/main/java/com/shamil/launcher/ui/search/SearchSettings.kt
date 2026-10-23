package com.shamil.launcher.ui.search

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.settings.Anemo
import com.shamil.launcher.settings.Cryo
import com.shamil.launcher.settings.Electro
import com.shamil.launcher.settings.Geo
import com.shamil.launcher.settings.Hint
import com.shamil.launcher.settings.Hydro
import com.shamil.launcher.settings.Pyro
import com.shamil.launcher.settings.SectionLabel
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.theme.LocalLauncherLook

/**
 * The "Search & commands" settings page (wrap it in PageFrame): a switch per result group,
 * the web search engine, and a short list of the > commands.
 */
@Composable
fun ColumnScope.SearchSettingsContent() {
    val context = LocalContext.current
    val store = remember { SearchStore.get(context) }

    Hint("Tap the round search button in the list and type. The best match sits nearest your thumb; the keyboard's Search key opens it.")

    SectionLabel("Search in")
    SearchGroup.entries.forEach { group ->
        val (glyph, icon, tint) = groupIcon(group)
        ToggleRow(glyph, icon, tint, group.title, group.sub, on = store.isOn(group)) { store.turn(group, it) }
    }

    SectionLabel("Web search engine")
    WebEngine.entries.chunked(2).forEach { pair ->
        Row(Modifier.staggerIn().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { engine ->
                EngineChip(engine.label, selected = store.engineId == engine.id) { store.useEngine(engine) }
            }
        }
    }

    SectionLabel("Commands")
    Hint("Type > in the search box, then one of these. Tap the card or press Search on the keyboard to run it.")
    SearchCommand.entries.forEach { CommandHelpRow(it) }
    Hint("Android no longer lets apps switch Wi-Fi or Bluetooth themselves, so > wifi opens the quick Wi-Fi panel and > bluetooth its settings. Timers and alarms are set in your clock app. Notes stay on this phone.")
}

/** Which icon and element colour each group gets on the settings page. */
private fun groupIcon(group: SearchGroup): Triple<Glyph?, Int?, Color> = when (group) {
    SearchGroup.Calc -> Triple(Glyph.Calc, null, Geo)
    SearchGroup.Apps -> Triple(null, R.drawable.ic_apps, Hydro)
    SearchGroup.Settings -> Triple(null, R.drawable.ic_settings, Cryo)
    SearchGroup.Contacts -> Triple(Glyph.Person, null, Electro)
    SearchGroup.Web -> Triple(Glyph.Globe, null, Anemo)
    SearchGroup.Play -> Triple(Glyph.Store, null, Pyro)
}

/** A setting with an on/off switch: coloured icon tile, title, one line of explanation. Tap anywhere to flip it. */
@Composable
private fun ToggleRow(glyph: Glyph?, @DrawableRes icon: Int?, tint: Color, title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(20.dp)
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .staggerIn()
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .pressFeel(press, tint, 20.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = if (on) 0.12f else 0.04f), look.card)))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .clickable(interactionSource = press, indication = null) { onChange(!on) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The same glowing tile as the other settings pages, but it can also hold a glyph drawn in code.
        val tileShape = RoundedCornerShape(15.dp)
        Box(
            Modifier
                .size(46.dp)
                .clip(tileShape)
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.32f), tint.copy(alpha = 0.12f))))
                .border(1.dp, tint.copy(alpha = 0.55f), tileShape),
            contentAlignment = Alignment.Center,
        ) {
            if (glyph != null) GlyphIcon(glyph, tint, Modifier.size(24.dp))
            else if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 17.sp))
            Text(sub, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp, lineHeight = 16.sp))
        }
        Spacer(Modifier.width(10.dp))
        MiniSwitch(on)
    }
}

/** One search engine to pick, half the row wide; the picked one glows in the accent. */
@Composable
private fun RowScope.EngineChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(18.dp)
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 52.dp)
            .pressFeel(press, look.accent, 18.dp)
            .clip(shape)
            .background(if (selected) look.accent.copy(alpha = 0.18f) else look.card)
            .border(1.dp, if (selected) look.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (selected) "✓  $label" else label,
            style = TextStyle(fontFamily = look.font, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) look.accent else look.text, fontSize = 15.sp),
        )
    }
}

/** A > command explained: its icon, the example in typewriter letters, and what it does. */
@Composable
private fun CommandHelpRow(command: SearchCommand) {
    val look = LocalLauncherLook.current
    Row(
        Modifier
            .staggerIn()
            .padding(bottom = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(look.card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResultBadge(glyph = command.glyph, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("> ${command.example}", style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 14.sp))
            val also = command.aliases.filter { it.all(Char::isLetter) }.take(2)
            val extra = if (also.isEmpty()) "" else "  (also: ${also.joinToString(", ")})"
            Text(command.what + extra, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
        }
    }
}
