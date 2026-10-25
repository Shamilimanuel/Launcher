package com.shamil.launcher.night

import android.Manifest
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shamil.launcher.R
import com.shamil.launcher.glance.AgendaSource
import com.shamil.launcher.glance.WeatherSource
import com.shamil.launcher.notify.NotifyHub
import com.shamil.launcher.settings.Anemo
import com.shamil.launcher.settings.Choice
import com.shamil.launcher.settings.Cryo
import com.shamil.launcher.settings.Dendro
import com.shamil.launcher.settings.Electro
import com.shamil.launcher.settings.Geo
import com.shamil.launcher.settings.GroupCard
import com.shamil.launcher.settings.GroupInfo
import com.shamil.launcher.settings.Hint
import com.shamil.launcher.settings.Hydro
import com.shamil.launcher.settings.ON_OFF
import com.shamil.launcher.settings.Pyro
import com.shamil.launcher.settings.SectionLabel
import com.shamil.launcher.settings.onOff
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.media.openNotificationAccess
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * Clock & date → Night screen (25 Oct, replacing the first test's hint): a small live preview, the button to the phone's
 * Screen saver page, his pages (on/off and order), the sky, the three StandBy widgets, the photo, and the night care
 * (dimming, bedtime, Late mode). Saved in the night screen's own store, so a saved look carries it.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NightScreenSettingsContent() {
    val context = LocalContext.current
    val store = remember { NightStore.get(context) }
    val scope = rememberCoroutineScope()

    // The preview: the page he tapped below it, else the first one.
    var previewPage by remember { mutableStateOf<NightPage?>(null) }
    val shown = store.shownPages()
    val page = previewPage?.takeIf { it in shown } ?: shown.first()
    Box(Modifier.staggerIn().padding(bottom = 10.dp)) { NightPreview(page) }
    FlowRow(Modifier.staggerIn().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { p -> Pill(p.title, selected = p == page) { previewPage = p } }
    }
    Hint("Shows by itself while the phone charges, once Seelie is your screen saver. Swipe sideways between your pages; leave with the ✕, Back or the power button.")
    GroupCard(R.drawable.ic_clear_night, Cryo, "Choose Seelie as screen saver", "Opens the phone's Screen saver page") {
        // Samsung keeps it under Display > Screen saver; if this phone has no such page, open Display instead.
        val dream = Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val display = Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(dream) }.isFailure) runCatching { context.startActivity(display) }
    }

    // ---- Pages
    val hasPhoto = store.photoStamp >= 0 && store.hasPhoto()   // reading the stamp: look again after a new pick
    SectionLabel("Pages")
    Hint("Turn pages on or off and put them in your order: the first one shows first. Photo shows once you pick a photo below.")
    store.order.forEachIndexed { i, p ->
        PageRow(
            page = p, on = p !in store.off, canUp = i > 0, canDown = i < store.order.lastIndex,
            note = if (p == NightPage.PHOTO && !hasPhoto) "Pick a photo below first" else null,
            onToggle = { store.setOn(p, it) }, onUp = { store.move(p, -1) }, onDown = { store.move(p, 1) },
        )
    }

    // ---- Sky
    SectionLabel("Sky")
    Hint("Behind StandBy, Music and Just the clock. The trail and the rainy window bring their own.")
    NightSky.entries.chunked(4).forEach { row ->
        Row(Modifier.staggerIn().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { s -> SkyTile(s, selected = store.sky == s, modifier = Modifier.weight(1f)) { store.useSky(s) } }
        }
    }

    // ---- StandBy widgets
    SectionLabel("StandBy widgets")
    Hint("The three cream cards beside the clock. Music also shows under the clock by itself while something plays.")
    store.slots.forEachIndexed { i, current ->
        val look = LocalLauncherLook.current
        Text("Slot ${i + 1}", modifier = Modifier.staggerIn().padding(start = 4.dp, bottom = 6.dp),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.textDim, fontSize = 13.sp))
        FlowRow(Modifier.staggerIn().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NightWidget.entries.forEach { w -> Pill(w.title, selected = w == current, icon = w.icon) { store.useSlot(i, w) } }
        }
    }
    Hint("Sunrise and Tomorrow use the weather (sunrise hides until Seelie has it). Next event follows \"Hide event titles\" in Glance line.")

    // ---- Photo
    SectionLabel("Photo page")
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { store.setPhoto(uri) }
            if (!ok) Toast.makeText(context, "Couldn't read that photo", Toast.LENGTH_SHORT).show()
            else previewPage = NightPage.PHOTO
        }
    }
    GroupCard(R.drawable.ic_wallpaper, Anemo, if (hasPhoto) "Change the photo" else "Pick a photo",
        "From your gallery, no storage permission. Seelie keeps its own copy.") {
        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    if (hasPhoto) GroupCard(R.drawable.ic_delete, Pyro, "Remove the photo", "The Photo page hides again") { store.removePhoto() }
    Hint("It slowly moves, zooms and fades, so no part of it stays put long enough to burn in.")

    // ---- Night care
    SectionLabel("At night")
    val hasSensor = remember { context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_LIGHT) != null }
    Choice("Dim with", NightDim.entries.map { it.id to it.title }, store.dim.id) { store.useDim(NightDim.byId(it)) }
    Hint(when (store.dim) {
        NightDim.ROOM -> if (hasSensor) "Follows the room's light (the phone's light sensor): the darker the room, the dimmer the night screen."
            else "This phone has no light sensor, so it dims from your bedtime instead."
        NightDim.BEDTIME -> "A little dimmer the hour before your bedtime, dimmer from bedtime until 07:00."
        NightDim.OFF -> "No extra dimming: the phone's own brightness only."
    })
    TimeSlide("Bedtime", store.bedtime, enabled = store.dim == NightDim.BEDTIME || (store.dim == NightDim.ROOM && !hasSensor)) { store.useBedtime(it) }
    Choice("Late mode: deep red, very dim", ON_OFF, store.late.onOff()) { store.useLate(it == "on") }
    TimeSlide("Late mode from", store.lateAt, enabled = store.late) { store.useLateAt(it) }
    Hint("Late mode lasts until 07:00. Everything also drifts a few dp every minute and moves at most 30 times a second, so nothing burns in.")

    // ---- What it can use
    SectionLabel("What it can show")
    Permissions()

    SectionLabel("Good to know")
    GroupInfo(R.drawable.ic_visibility, Geo, "To see it now", "Seelie can't start a screen saver itself (Android doesn't allow it). On the Screen saver page tap Preview, or plug in the charger and let the screen time out.")
    GroupInfo(R.drawable.ic_touch_app, Hydro, "Swipe, don't tap to leave", "Touching it doesn't close it, so you can swipe pages and use the music buttons. Leave with the ✕ in the corner, Back, or the power button.")
}

/** Weather, calendar and music: each says if it's on, and a tap asks for it when it isn't. */
@Composable
private fun Permissions() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var asked by remember { mutableIntStateOf(0) }
    val location = remember(resumes, asked) { WeatherSource.hasPermission(context) }
    val calendar = remember(resumes, asked) { AgendaSource.hasPermission(context) }
    val music = remember(resumes, asked) { NotifyHub.isEnabled(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked++ }

    if (location) GroupInfo(R.drawable.ic_location_on, Geo, "Weather: on", "From your rough location (about 10 km), like the home's weather.")
    else GroupCard(R.drawable.ic_location_on, Geo, "Weather: allow location", "Only a rough location, for the weather cards") { ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    if (calendar) GroupInfo(R.drawable.ic_calendar_month, Dendro, "Events: on", "Your next event and tomorrow's first, read on the phone only.")
    else GroupCard(R.drawable.ic_calendar_month, Dendro, "Events: allow the calendar", "For the Next event and Tomorrow cards") { ask.launch(Manifest.permission.READ_CALENDAR) }
    if (music) GroupInfo(R.drawable.ic_music_note, Electro, "Music: on", "What's playing, its cover, and the buttons that control it.")
    else GroupCard(R.drawable.ic_music_note, Electro, "Music: allow notification access", "So Seelie can see and control what's playing") { openNotificationAccess(context) }
}

/** A page in the order list: its name and what it is, an on/off switch, and ↑ ↓. */
@Composable
private fun PageRow(page: NightPage, on: Boolean, canUp: Boolean, canDown: Boolean, note: String?, onToggle: (Boolean) -> Unit, onUp: () -> Unit, onDown: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.staggerIn().padding(bottom = 8.dp).fillMaxWidth().clip(shape).background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(page.title, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text.copy(alpha = if (on) 1f else 0.55f), fontSize = 16.sp))
            Text(note ?: page.about, style = TextStyle(fontFamily = look.font, color = if (note != null) look.accent else look.textDim, fontSize = 12.5.sp, lineHeight = 17.sp))
        }
        Toggle(on, onToggle)
        Arrow("↑", canUp, onUp)
        Arrow("↓", canDown, onDown)
    }
}

/** An on/off switch like the mock-up's: a gold track when on, the knob springs across. 46dp to hit. */
@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val x by animateDpAsState(if (on) 20.dp else 0.dp, spring(dampingRatio = 0.5f, stiffness = 500f), label = "knob")
    val track by animateColorAsState(if (on) look.accent else Color(0xFF3A4157), tween(400), label = "track")
    Box(
        Modifier.size(width = 56.dp, height = 46.dp).pressFeel(press, look.accent, 23.dp).clip(RoundedCornerShape(23.dp))
            .clickable(press, indication = null) { onChange(!on) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 46.dp, height = 26.dp).clip(CircleShape).background(track).padding(3.dp)) {
            Box(Modifier.offset(x = x).size(20.dp).clip(CircleShape).background(Color(0xFFFFF8E6)))
        }
    }
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(46.dp).pressFeel(press, look.accent, 23.dp).clip(CircleShape)
            .clickable(press, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TextStyle(color = if (enabled) look.accent else look.textDim.copy(alpha = 0.3f), fontSize = 20.sp, fontWeight = FontWeight.Bold))
    }
}

/** A sky to pick: a little window of its colours with a few stars, its name under it, a gold ring when picked. */
@Composable
private fun SkyTile(sky: NightSky, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val ring by animateColorAsState(if (selected) look.accent else Color.White.copy(alpha = 0.08f), tween(400), label = "sky ring")
    Column(modifier.pressFeel(press, look.accent, 14.dp).clickable(press, indication = null, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier.fillMaxWidth().height(54.dp).clip(shape)
                .background(Brush.verticalGradient(sky.swatch.map { Color(it) }))
                .border(if (selected) 2.dp else 1.dp, ring, shape)
                .drawBehind {
                    // A few stars, the same spots on every tile.
                    listOf(.18f to .25f, .52f to .18f, .78f to .38f, .34f to .55f, .66f to .7f).forEach { (x, y) ->
                        drawCircle(Color(0xCCFFF4D6), 1.3.dp.toPx(), Offset(x * size.width, y * size.height))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Text("✓", style = TextStyle(color = look.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }
        Text(sky.title, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp),
            style = TextStyle(fontFamily = look.font, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) look.accent else look.textDim, fontSize = 12.sp))
    }
}

/** A choice pill (46dp tall): gold when picked; the colour glides in 0.4 s. */
@Composable
private fun Pill(text: String, selected: Boolean, icon: Int? = null, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) look.accent else look.card, tween(400), label = "pill")
    val fg by animateColorAsState(if (selected) look.sheet.copy(alpha = 1f) else look.text, tween(400), label = "pill text")
    Row(
        Modifier.heightIn(min = 46.dp).pressFeel(press, look.accent, 23.dp).clip(RoundedCornerShape(23.dp)).background(bg)
            .border(1.dp, if (selected) Color.Transparent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(23.dp))
            .clickable(press, indication = null, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = fg, fontSize = 13.5.sp))
    }
}

/** A time from 18:00 to 04:00 in quarter hours, with the time shown on the right. */
@Composable
private fun TimeSlide(label: String, minutes: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val look = LocalLauncherLook.current
    val start = 18 * 60
    // Minutes after 18:00 (past midnight counts on: 01:00 = 7 h after 18:00).
    val from18 = ((minutes - start).mod(24 * 60)).coerceIn(0, 10 * 60)
    Column(Modifier.staggerIn().padding(start = 4.dp, end = 4.dp, bottom = 8.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.textDim.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 13.sp))
            val m = (start + from18) % (24 * 60)
            Text("%02d:%02d".format(m / 60, m % 60),
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = if (enabled) look.accent else look.textDim.copy(alpha = 0.4f), fontSize = 13.sp))
        }
        Slider(
            value = from18 / 15f,
            onValueChange = { onChange((start + it.roundToInt() * 15) % (24 * 60)) },
            valueRange = 0f..40f,
            steps = 39,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = look.accent, activeTrackColor = look.accent, inactiveTrackColor = look.text.copy(alpha = 0.18f),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
