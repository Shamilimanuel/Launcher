package com.shamil.launcher.ui.home

import android.Manifest
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shamil.launcher.R
import com.shamil.launcher.glance.AgendaSource
import com.shamil.launcher.glance.CalEvent
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.HomePrefs
import com.shamil.launcher.ui.openSafely
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * The event line under the weather line (his idea from the prototype, 23 Oct): his next event today, in the calendar's
 * colour, with its time. Tap it for the agenda pop-up (today + tomorrow). Nothing shows when the day is empty.
 */

/** The event line. It looks like the glance line (his home style), with a small dot in the event's own colour. */
@Composable
fun EventLine(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val home = remember { HomePrefs.get(context) }
    if (!home.showEvent) return
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(AgendaSource.hasPermission(context)) }
    var events by remember { mutableStateOf(emptyList<CalEvent>()) }
    var agendaOpen by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }

    // Read the calendar while home is on screen: right away, then every minute (cheap: a small query on the phone).
    LaunchedEffect(lifecycleOwner, allowed) {
        if (!allowed) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                events = withContext(Dispatchers.IO) { AgendaSource.upcoming(context) }
                delay(60_000L)
            }
        }
    }

    val gl = glanceLook(currentHomeStyle(), look)
    val text = TextStyle(fontFamily = look.font, color = gl.color, fontSize = gl.size, fontWeight = gl.weight, shadow = gl.shadow)
    val noRipple = remember { MutableInteractionSource() }

    if (!allowed) {
        // First time: a quiet hint that asks for the permission when tapped.
        Row(modifier.clickable(noRipple, null) { ask.launch(Manifest.permission.READ_CALENDAR) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_calendar_month), null, tint = gl.color, modifier = Modifier.size((gl.size.value * 1.15f).dp))
            Spacer(Modifier.width(6.dp))
            Text("Tap to show your events", style = text)
        }
        return
    }

    val next = AgendaSource.nextToday(events) ?: return   // an empty day: nothing at all
    Row(
        modifier.clickable(noRipple, null) { agendaOpen = true }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(Color(next.colour), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(next.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false), style = text)
        Spacer(Modifier.width(8.dp))
        Text(whenText(next), maxLines = 1, style = text.copy(fontWeight = gl.weight, color = gl.color.copy(alpha = gl.color.alpha * 0.85f)))
        Text("  ›", style = text)
    }
    if (agendaOpen) AgendaSheet(events) { agendaOpen = false }
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(HM)

/** "18:45–20:45", "now · until 20:45" or "all day". */
private fun whenText(e: CalEvent, now: Long = System.currentTimeMillis()): String = when {
    e.allDay -> "all day"
    e.begin <= now -> "now · until ${time(e.end)}"
    else -> "${time(e.begin)}–${time(e.end)}"
}

/** "in 25 min", "in 2 h", "now" or "" (all day / already started), for the agenda rows. */
private fun relative(e: CalEvent, now: Long = System.currentTimeMillis()): String {
    if (e.allDay) return ""
    if (e.begin <= now) return "now"
    val min = ((e.begin - now) / 60_000L).toInt()
    return if (min < 60) "in $min min" else "in ${min / 60} h"
}

/** The agenda pop-up: today and tomorrow as rounded cards, like Niagara's but in Seelie's look. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaSheet(events: List<CalEvent>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss(); action() }
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    fun dayOf(e: CalEvent) = Instant.ofEpochMilli(maxOf(e.begin, System.currentTimeMillis())).atZone(zone).toLocalDate()
    val todays = events.filter { dayOf(it) == today }
    val tomorrows = events.filter { dayOf(it) == today.plusDays(1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = look.sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(0f to look.sheetTop, 0.4f to look.sheet))
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 42.dp, height = 4.dp).background(look.text.copy(alpha = 0.28f), CircleShape))
            Spacer(Modifier.height(16.dp))
            Text("Your agenda", modifier = Modifier.padding(start = 6.dp),
                style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 26.sp, letterSpacing = 1.sp))
            AgendaDay("Today", todays) { e -> closeThen { openSafely(context, AgendaSource.openIntent(e)) } }
            AgendaDay("Tomorrow", tomorrows) { e -> closeThen { openSafely(context, AgendaSource.openIntent(e)) } }
            Spacer(Modifier.height(14.dp))
            // Open the calendar app on today.
            val press = remember { MutableInteractionSource() }
            Text(
                "Open calendar",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .pressFeel(press, look.accent, 22.dp)
                    .clip(CircleShape)
                    .border(1.dp, look.accent.copy(alpha = 0.7f), CircleShape)
                    .clickable(press, null) {
                        closeThen {
                            val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                            android.content.ContentUris.appendId(uri, System.currentTimeMillis())
                            openSafely(context, Intent(Intent.ACTION_VIEW, uri.build()))
                        }
                    }
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 15.sp),
            )
        }
    }
}

/** One day: a small accent label, then its events in one rounded card (or "Nothing planned"). */
@Composable
private fun AgendaDay(label: String, events: List<CalEvent>, onOpen: (CalEvent) -> Unit) {
    val look = LocalLauncherLook.current
    Text(label.uppercase(), modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 11.sp, letterSpacing = 2.4.sp))
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.staggerIn().fillMaxWidth().clip(shape).background(look.card).border(1.dp, Color.White.copy(alpha = 0.06f), shape).padding(vertical = 6.dp)) {
        if (events.isEmpty()) {
            Text("Nothing planned", modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 15.sp))
        }
        events.forEach { e ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(e) }.padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The coloured bar, like his calendar.
                Box(Modifier.width(4.dp).height(36.dp).background(Color(e.colour), RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 16.sp))
                    val rel = relative(e)
                    Text(whenText(e) + (if (rel.isNotEmpty() && rel != "now") " · $rel" else "") + (e.location?.let { " · $it" } ?: ""),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp))
                }
            }
        }
    }
}
