package com.shamil.launcher.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shamil.launcher.R
import com.shamil.launcher.glance.DeviceStats
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import java.time.format.TextStyle as DateStyle

/*
 * The widgets of the stack. Each one fills a rounded card (see [WidgetCard]) that the stack sizes:
 * full width, [WIDGET_HEIGHT] tall.
 */

/** How tall every widget card is. */
val WIDGET_HEIGHT: Dp = 160.dp

/** Sundays in pink, like his Samsung lock screen calendar. */
private val SundayPink = Color(0xFFFF8CA8)
/** The dark "ink" for a number on a light circle (today). */
private val Ink = Color(0xFF171C29)

/** Text on the Outline style sits right on the wallpaper, so it gets the soft shadow; on Glass/Solid it doesn't need it. */
private val LocalWidgetShadow = staticCompositionLocalOf<Shadow?> { null }

/** The rounded card around one widget, drawn in his chosen [CardStyle]. */
@Composable
internal fun WidgetCard(style: CardStyle, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(24.dp)
    val card = when (style) {
        // Glass: the see-through panel colour of the search button, a faint light edge and a soft shine at the top.
        CardStyle.GLASS -> Modifier
            .background(look.panel, shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent)), shape)
            .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
        // Solid: the (almost opaque) colour of the sheets.
        CardStyle.SOLID -> Modifier
            .background(Brush.verticalGradient(listOf(look.sheetTop, look.sheet)), shape)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
        // Outline: hollow, only a glowing edge in the accent colour.
        CardStyle.OUTLINE -> Modifier
            .border(1.5.dp, look.accent.copy(alpha = 0.55f), shape)
    }
    CompositionLocalProvider(LocalWidgetShadow provides if (style == CardStyle.OUTLINE) look.shadow else null) {
        Box(
            modifier
                .fillMaxSize()
                .clip(shape)
                .then(card)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = content,
        )
    }
}

/** Plain text in the theme's font (with the wallpaper shadow on the Outline style). */
@Composable
private fun WText(
    text: String,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
) {
    val look = LocalLauncherLook.current
    Text(
        text, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = look.font, color = color, fontSize = size, fontWeight = weight,
            shadow = LocalWidgetShadow.current,
        ),
    )
}

/** The small gold caps label in the top-left of a widget ("QUICK NOTES"). */
@Composable
private fun WidgetLabel(text: String) {
    val look = LocalLauncherLook.current
    Text(
        text.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent.copy(alpha = 0.9f),
            fontSize = 10.sp, letterSpacing = 2.2.sp, shadow = LocalWidgetShadow.current,
        ),
    )
}

/* ---------------- Month calendar ---------------- */

/**
 * The month, like his Samsung lock screen: the year, the month's name big in Cinzel and today's day on the left;
 * on the right the grid, Monday first, Sundays pink, other months' days dimmed, today = a white circle.
 */
@Composable
internal fun MonthWidget(today: LocalDate) {
    val look = LocalLauncherLook.current
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            WText("${today.year}", 11.sp, look.accent, weight = FontWeight.Bold)
            // "okt." → "OKT": the short month name, without the dot some languages add.
            val month = today.month.getDisplayName(DateStyle.SHORT, locale).trimEnd('.').uppercase(locale)
            Text(
                month, maxLines = 1,
                style = TextStyle(
                    fontFamily = Cinzel, fontWeight = FontWeight.Medium, color = look.text, fontSize = 30.sp,
                    letterSpacing = 1.5.sp, shadow = LocalWidgetShadow.current,
                ),
            )
            val day = today.dayOfWeek.getDisplayName(DateStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
            WText("$day ${today.dayOfMonth}", 12.sp, look.textDim)
        }
        Spacer(Modifier.width(8.dp))
        MonthGrid(today, locale)
    }
}

@Composable
private fun MonthGrid(today: LocalDate, locale: Locale) {
    val look = LocalLauncherLook.current
    val cellW = 25.dp
    val cellH = 18.dp
    // The grid starts on the Monday on or before the 1st, and has as many weeks as the month needs (4–6).
    val first = today.withDayOfMonth(1)
    val lead = first.dayOfWeek.value - 1   // Monday = 1 in java.time, so this is how many days of last month show
    val weeks = (lead + today.lengthOfMonth() + 6) / 7
    val start = first.minusDays(lead.toLong())
    Column {
        // Day letters in his phone's language (Dutch: M D W D V Z Z).
        Row {
            for (d in 1..7) {
                val letter = DayOfWeek.of(d).getDisplayName(DateStyle.NARROW, locale).uppercase(locale)
                Box(Modifier.size(cellW, cellH), contentAlignment = Alignment.Center) {
                    WText(letter, 10.sp, if (d == 7) SundayPink else look.textDim, weight = FontWeight.Bold)
                }
            }
        }
        for (w in 0 until weeks) {
            Row {
                for (d in 0 until 7) {
                    val date = start.plusDays((w * 7 + d).toLong())
                    val thisMonth = date.month == today.month
                    Box(Modifier.size(cellW, cellH), contentAlignment = Alignment.Center) {
                        if (date == today) {
                            // Today: a filled white circle with a dark number.
                            Box(Modifier.size(17.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${date.dayOfMonth}", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = Ink, fontSize = 10.sp))
                            }
                        } else {
                            val base = if (d == 6) SundayPink else look.text
                            WText("${date.dayOfMonth}", 10.sp, if (thisMonth) base else base.copy(alpha = 0.35f))
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- Gauge tiles ---------------- */

/**
 * Three round gauge tiles: battery, humidity and temperature, in the stats card's ring look.
 * Battery comes from the phone; humidity + temperature from [GaugeWeatherSource] ("—" without location).
 * Refreshes only while the home is on screen: battery every minute, the weather check every 5 minutes.
 */
@Composable
internal fun GaugesWidget() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var stats by remember { mutableStateOf(DeviceStats.read(context)) }
    var weather by remember { mutableStateOf(if (GaugeWeatherSource.canUseLocation(context)) GaugeWeatherSource.cached(context) else null) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = withContext(Dispatchers.Default) { DeviceStats.read(context) }
                delay(60_000L)
            }
        }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // load() returns null without permission or with Location off, so the tiles then show "—".
                weather = GaugeWeatherSource.load(context)
                delay(if (weather == null) 60_000L else 5 * 60_000L)
            }
        }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        val w = weather
        Gauge(
            fill = stats.batteryPercent / 100f,
            value = "${stats.batteryPercent}%",
            label = if (stats.charging) "Charging" else "Battery",
        )
        Gauge(fill = (w?.humidity ?: 0) / 100f, value = w?.let { "${it.humidity}%" } ?: "—", label = "Humidity")
        // The temperature ring runs from -10° (empty) to 40° (full).
        Gauge(fill = ((w?.temp ?: -10) + 10) / 50f, value = w?.let { "${it.temp}°" } ?: "—", label = "Temperature")
    }
}

/** One tile: a dark glassy circle with a 3/4 ring (open at the bottom) in the accent, the value inside, a label under it. */
@Composable
private fun Gauge(fill: Float, value: String, label: String) {
    val look = LocalLauncherLook.current
    // The ring fills up from empty when it first shows, then glides to new values (like the stats card).
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(fill) { target = fill.coerceIn(0f, 1f) }
    val shown by animateFloatAsState(target, tween(700), label = "gauge")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(70.dp)
                .drawBehind {
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))))
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2 + 4.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    // 270° of ring, starting bottom-left (135°) and going clockwise to bottom-right.
                    drawArc(look.text.copy(alpha = 0.14f), 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (shown > 0.005f) {
                        drawArc(look.accent, 135f, 270f * shown, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            WText(value, 15.sp, look.text, weight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        WText(label, 11.sp, look.textDim)
    }
}

/* ---------------- This week ---------------- */

/** Today and the six days after it, today in an accent circle. Dates only (no calendar permission needed). */
@Composable
internal fun WeekWidget(today: LocalDate) {
    val look = LocalLauncherLook.current
    val locale = Locale.getDefault()
    // Week numbers the European way (ISO: weeks start Monday), as Dutch calendars show them.
    val weekNumber = today.get(WeekFields.ISO.weekOfWeekBasedYear())
    Column(Modifier.fillMaxSize()) {
        WidgetLabel("This week · week $weekNumber")
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0 until 7) {
                val date = today.plusDays(i.toLong())
                val isToday = i == 0
                val sunday = date.dayOfWeek == DayOfWeek.SUNDAY
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val name = date.dayOfWeek.getDisplayName(DateStyle.SHORT, locale).trimEnd('.')
                    WText(name, 12.sp, if (sunday) SundayPink else look.textDim)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(34.dp)
                            .then(if (isToday) Modifier.background(look.accent, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isToday) {
                            Text("${date.dayOfMonth}", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp))
                        } else {
                            WText("${date.dayOfMonth}", 16.sp, look.text, weight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Under the 1st of a month: the month's short name, so a new month is easy to spot.
                    WText(
                        if (date.dayOfMonth == 1) date.month.getDisplayName(DateStyle.SHORT, locale).trimEnd('.') else " ",
                        10.sp, look.accent,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/* ---------------- Quick notes ---------------- */

/** His notes, one per line (the first three show). Tap the card to write or change them. */
@Composable
internal fun NotesWidget(store: WidgetStore) {
    val look = LocalLauncherLook.current
    var editing by remember { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    val lines = store.notes.lines().map { it.trim() }.filter { it.isNotEmpty() }
    Column(
        Modifier
            .fillMaxSize()
            .pressFeel(press, look.accent, 18.dp)
            .clickable(interactionSource = press, indication = null) { editing = true }
    ) {
        // More than three notes: the label says how many there are in all.
        WidgetLabel(if (lines.size > 3) "Quick notes · ${lines.size}" else "Quick notes")
        Spacer(Modifier.height(10.dp))
        if (lines.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = look.textDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                WText("Tap to write a note", 15.sp, look.textDim)
            }
        } else {
            lines.take(3).forEach { line ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    // A small hollow circle in the accent, like a to-do bullet.
                    Box(Modifier.size(12.dp).border(1.5.dp, look.accent, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    WText(line, 15.sp, look.text)
                }
            }
        }
    }
    if (editing) {
        NotesDialog(store.notes) { text ->
            if (text != null) store.saveNotes(text.trimEnd())
            editing = false
        }
    }
}

/** A box to type the notes in. [onDone] gets the new text on Save, or null on Cancel. */
@Composable
private fun NotesDialog(current: String, onDone: (String?) -> Unit) {
    val look = LocalLauncherLook.current
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = look.sheet,
        titleContentColor = look.text,
        textContentColor = Color.Unspecified,
        title = { Text("Quick notes", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 4,
                maxLines = 8,
                placeholder = { Text("One note per line", style = TextStyle(fontFamily = look.font, fontSize = 16.sp)) },
                textStyle = TextStyle(fontFamily = look.font, color = look.text, fontSize = 17.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = look.text,
                    unfocusedTextColor = look.text,
                    focusedBorderColor = look.accent,
                    unfocusedBorderColor = look.textDim.copy(alpha = 0.4f),
                    cursorColor = look.accent,
                    focusedPlaceholderColor = look.textDim,
                    unfocusedPlaceholderColor = look.textDim,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text("Save", color = look.accent) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel", color = look.textDim) } },
    )
}
