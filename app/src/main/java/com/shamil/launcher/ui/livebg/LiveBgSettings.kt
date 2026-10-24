package com.shamil.launcher.ui.livebg

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.glance.WeatherSource
import com.shamil.launcher.settings.Choice
import com.shamil.launcher.settings.Hint
import com.shamil.launcher.settings.ON_OFF
import com.shamil.launcher.settings.SectionLabel
import com.shamil.launcher.settings.Slide
import com.shamil.launcher.settings.onOff
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.search.MiniSwitch
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.roundToInt

/*
 * Seelie settings → Living background (24 Oct): the live preview at the top, the main switch, presets, the scene
 * (time of day, weather, colour mood, calm mode, frame rate), then every effect in its group with a switch and a
 * density slider. Wrapped in PageFrame by the settings screen, like the music card's page.
 */

/**
 * The page content. [showPreview] = false when the settings screen pins [LiveBgPreview] at the top instead
 * (recommended, like Music card: PageFrame(…, pinned = { LiveBgPreview() }) { LiveBgSettingsContent(showPreview = false) }).
 */
@Composable
fun ColumnScope.LiveBgSettingsContent(showPreview: Boolean = true) {
    val context = LocalContext.current
    val store = remember { LiveBgStore.get(context) }
    val s = store.style
    val gate = rememberSystemGate()
    val weather = remember { WeatherSource.cached(context) }

    if (showPreview) {
        LiveBgPreview()
        Spacer(Modifier.height(6.dp))
    }
    Hint("A living layer over your own wallpaper and behind your home: petals, stars, clouds and more. Tap the preview to try Touch sparkle.")
    if (gate.powerSave) Hint(
        if (s.inSaver) "Battery saver is on, so your home runs the light version: calm, 30 fps, and no clouds, mist, light rays or aurora."
        else "Battery saver is on, so your home shows just your wallpaper for now (\"In battery saver\" is off below)."
    )
    if (!gate.animationsOn) Hint("Android's animations are off (Developer options → Animator duration scale), so the living background rests as well.")

    SectionLabel("Living background")
    Choice("Living background", ON_OFF, s.enabled.onOff()) { v -> store.change { it.copy(enabled = v == "on") } }
    Choice("In battery saver", listOf("on" to "Light version", "off" to "Pause"), if (s.inSaver) "on" else "off") { v ->
        store.change { it.copy(inSaver = v == "on") }
    }

    SectionLabel("Presets")
    LIVE_BG_PRESETS.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            row.forEach { p -> PresetChip(p, selected = s.preset == p.id) { store.usePreset(p) } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    val picked = LIVE_BG_PRESETS.firstOrNull { it.id == s.preset }
    Hint(
        if (picked != null) "${picked.desc} It also fixes the time to ${picked.time} and the weather to ${picked.weather}: set them back to Auto and Real below to follow your day."
        else "Pick one to start from, then change anything below."
    )

    SectionLabel("Scene")
    Choice("Time of day", listOf("auto" to "Auto", "dawn" to "Dawn", "day" to "Day", "dusk" to "Dusk", "night" to "Night"), s.time) { v ->
        store.change { it.copy(time = v) }
    }
    Hint(
        if (s.time == "auto") "Auto follows your clock: dawn 5–8, day 8–17, dusk 17–21, night after that. Some effects only come out at certain times (it says under each one)."
        else "Fixed, whatever the clock says. Auto follows your clock."
    )
    Choice(
        "Weather",
        listOf("real" to "Real", "clear" to "Clear", "rain" to "Rain", "storm" to "Storm", "snow" to "Snow", "mist" to "Mist"),
        s.weather,
    ) { v -> store.change { it.copy(weather = v) } }
    Hint(
        if (s.weather == "real") {
            val now = weather?.description?.let { " (now: $it)" } ?: " (no weather yet: it counts as clear)"
            "Follows the weather in your glance line$now. Switch on Rain, Snow and Storm under Weather below to see them when it really happens."
        } else "Fixed weather, so you can see its effects any time. Real follows the actual weather."
    )
    Choice("Colour mood", listOf("gold" to "Gold", "pink" to "Pink", "violet" to "Violet", "green" to "Green"), s.mood) { v ->
        store.change { it.copy(mood = v) }
    }
    Hint("Colours the motes, butterflies, sparkles and lightning, and the glow in the sky.")
    Choice("Calm mode", ON_OFF, s.calm.onOff()) { v -> store.change { it.copy(calm = v == "on") } }
    Hint(
        if (s.calm) "Slower (0.4× speed), about 60% as many things, and no lightning flashes (a storm glows softly instead)."
        else "Full speed and density, with lightning flashes in a storm."
    )
    Choice("Frame rate", listOf("30" to "30 fps", "60" to "60 fps"), if (s.fps60) "60" else "30") { v ->
        store.change { it.copy(fps60 = v == "60") }
    }
    Hint("30 frames a second is smooth for slow things like these and uses about half the battery of 60.")
    if (!skyEffectsSupported) Hint("Cloud drift, Mist, Light rays and Aurora need Android 13 or newer, so they're off on this phone.")

    FX_GROUPS.forEach { g ->
        SectionLabel(g.name)
        Hint(g.sub)
        LiveFx.ALL.filter { it.group == g.id }.forEach { info ->
            EffectCard(
                info = info,
                f = s.fx(info.id),
                available = !info.shader || skyEffectsSupported,
                onToggle = { store.useFx(info.id) { it.copy(on = !it.on) } },
                onDensity = { v -> store.useFx(info.id) { it.copy(density = v) } },
                onHeight = { v ->
                    store.glintGuideAt = SystemClock.uptimeMillis()
                    store.useFx(info.id) { it.copy(height = v) }
                },
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    ResetChip("Back to the starting mix") { store.reset() }
}

/**
 * The live preview: the real effects on a little painted dusk sky, running while the page is open (even in battery
 * saver, which only pauses the home). Tap it to try Touch sparkle. Shows the scene it is drawing, e.g. "Dusk · Clear".
 */
@Composable
fun LiveBgPreview() {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val store = remember { LiveBgStore.get(context) }
    val weatherCode = remember { WeatherSource.cached(context)?.code }
    val controller = rememberLiveBgController()
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .height(240.dp)
            .clip(shape)
            .background(Brush.verticalGradient(*PREVIEW_SKY))
            .border(1.dp, look.accent.copy(alpha = 0.3f), shape)
            .pointerInput(controller) { detectTapGestures { controller.onTap(it.x, it.y) } }
    ) {
        LiveBgLayer(Modifier.matchParentSize(), weatherCode, active = true, controller = controller, preview = true)
        Badge("LIVE PREVIEW", Modifier.align(Alignment.TopEnd).padding(10.dp))
        Badge(
            if (store.style.anythingOn()) sceneLabel(store.style, weatherCode).uppercase() else "OFF",
            Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}

/** A stand-in wallpaper for the preview: night blue at the top, a pink dusk glow low down, dark ground at the bottom. */
private val PREVIEW_SKY = arrayOf(
    0f to Color(0xFF12142E),
    .35f to Color(0xFF282256),
    .62f to Color(0xFF6A3F78),
    .74f to Color(0xFFB8708E),
    .80f to Color(0xFF3A2446),
    1f to Color(0xFF120D22),
)

@Composable
private fun Badge(text: String, modifier: Modifier) {
    val look = LocalLauncherLook.current
    Text(
        text,
        modifier = modifier
            .background(Color(0x99000000), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp, letterSpacing = 1.5.sp),
    )
}

/** A preset to pick: a coloured dot and its name; filled with the accent when it's the one in use. */
@Composable
private fun RowScope.PresetChip(p: LiveBgPreset, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .weight(1f)
            .staggerIn()
            .height(44.dp)
            .pressFeel(press, look.accent, 14.dp)
            .clip(shape)
            .background(if (selected) look.accent.copy(alpha = 0.16f) else look.card)
            .border(1.dp, if (selected) look.accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.06f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(p.dot, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(p.name, maxLines = 1,
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = if (selected) look.text else look.textDim, fontSize = 13.sp))
    }
}

/**
 * One effect: its name (and an "Undecided" tag on the four he hasn't judged yet), what it does, when it shows,
 * and a switch. While it's on: its density slider (and Height for Glints on water).
 */
@Composable
private fun EffectCard(info: FxInfo, f: FxSetting, available: Boolean, onToggle: () -> Unit, onDensity: (Int) -> Unit, onHeight: (Int) -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(20.dp)
    val press = remember { MutableInteractionSource() }
    val on = f.on && available
    Column(
        Modifier
            .staggerIn()
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(if (on) Brush.horizontalGradient(listOf(look.accent.copy(alpha = 0.10f), look.card)) else Brush.linearGradient(listOf(look.card, look.card)))
            .border(1.dp, if (on) look.accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.07f), shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressFeel(press, look.accent, 20.dp)
                .clickable(enabled = available, interactionSource = press, indication = null, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(info.name, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text.copy(alpha = if (available) 1f else 0.5f), fontSize = 16.sp))
                    if (!info.decided) {
                        Spacer(Modifier.width(8.dp))
                        Text("UNDECIDED", modifier = Modifier.border(1.dp, look.accent.copy(alpha = 0.7f), CircleShape).padding(horizontal = 6.dp, vertical = 1.dp),
                            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 8.sp, letterSpacing = 1.2.sp))
                    }
                }
                Text(info.text, style = TextStyle(fontFamily = look.font, color = look.textDim.copy(alpha = if (available) 1f else 0.5f), fontSize = 13.sp, lineHeight = 18.sp))
                val shows = if (!available) "Needs Android 13" else info.needs
                if (shows != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(shows, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent.copy(alpha = if (available) 0.9f else 0.5f), fontSize = 12.sp))
                }
            }
            Spacer(Modifier.width(12.dp))
            MiniSwitch(on)
        }
        if (on && info.hasAmount) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp)) {
                Slide(info.amountLabel, f.density.toFloat(), 0f..100f, 19, "%") { onDensity(it.roundToInt()) }
                if (info.index == LiveFx.GLINTS) {
                    Slide("Height (where your water is)", f.height.toFloat(), 0f..100f, 19, "%") { onHeight(it.roundToInt()) }
                }
            }
        }
    }
}

@Composable
private fun ResetChip(text: String, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Text(
        text,
        modifier = Modifier
            .staggerIn()
            .padding(bottom = 12.dp)
            .pressFeel(press, look.accent, 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, look.accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 14.sp),
    )
}
