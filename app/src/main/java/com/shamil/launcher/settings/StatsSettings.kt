package com.shamil.launcher.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.home.StatKind
import com.shamil.launcher.ui.home.StatsCard
import com.shamil.launcher.ui.home.StatsStore
import com.shamil.launcher.ui.openWeatherApp
import com.shamil.launcher.ui.rememberGlanceState
import com.shamil.launcher.ui.theme.LocalLauncherLook

/*
 * Seelie settings → Home screen → Stats card (his ask, 23 Oct: "we should add we can customize stats").
 * A pinned live preview of the real card, then: which stats (in the order he taps them), the weather part, and the ring style.
 * The card's look itself follows Look → Home style, so it matches the widget card.
 */

/** The real stats card with his real numbers, pinned above the controls. */
@Composable
internal fun StatsPreview() {
    val glance = rememberGlanceState()
    val context = LocalContext.current
    StatsCard(glance.weather, glance.stats, onWeatherTap = { openWeatherApp(context) })
}

@Composable
fun ColumnScope.StatsSettingsContent() {
    val context = LocalContext.current
    val store = remember { StatsStore.get(context) }
    val st = store.style

    Hint("Tap the line under the date on your home to open this card. Its look (card, see-through, corners) follows Widgets → Card, so it matches your widget card.")

    SectionLabel("Stats · in the order you pick them")
    StatKind.entries.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            pair.forEach { kind ->
                val index = st.stats.indexOf(kind)
                StatChip(kind.title, number = if (index >= 0) index + 1 else null) { store.toggle(kind) }
            }
        }
    }

    SectionLabel("Weather")
    Choice("Show the weather", ON_OFF, st.showWeather.onOff()) { v -> store.change { it.copy(showWeather = v == "on") } }
    Choice("Hours ahead", listOf("3" to "3 hours", "5" to "5 hours", "8" to "8 hours"), st.hours.toString(), enabled = st.showWeather) { v ->
        store.change { it.copy(hours = v.toInt()) }
    }
    Choice("Temperature", listOf("c" to "°C", "f" to "°F"), if (st.fahrenheit) "f" else "c") { v -> store.change { it.copy(fahrenheit = v == "f") } }

    SectionLabel("Rings")
    Choice("Style", listOf("circle" to "Circle", "gauge" to "¾ gauge", "bars" to "Bars"), st.ringStyle) { v -> store.change { it.copy(ringStyle = v) } }
    Choice("Thickness", listOf("thin" to "Thin", "normal" to "Normal", "thick" to "Thick"), st.thickness) { v -> store.change { it.copy(thickness = v) } }
    Choice("Number", listOf("inside" to "Inside", "under" to "Under"), st.numberPlace, enabled = st.ringStyle != "bars") { v ->
        store.change { it.copy(numberPlace = v) }
    }
}

/** A stat to switch on or off; when on, it shows its place in the order (1, 2, 3…) in a springy accent circle. */
@Composable
private fun RowScope.StatChip(title: String, number: Int?, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val on = number != null
    val pop by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "stat on")
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .weight(1f)
            .staggerIn()
            .height(52.dp)
            .pressFeel(press, look.accent, 16.dp)
            .clip(shape)
            .background(if (on) look.accent.copy(alpha = 0.16f) else look.card)
            .border(1.dp, if (on) look.accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = if (on) look.text else look.textDim, fontSize = 15.sp))
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(24.dp).graphicsLayer { scaleX = pop; scaleY = pop }.background(look.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (number != null) Text("$number", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, color = look.sheet.copy(alpha = 1f), fontSize = 12.sp))
        }
    }
}
