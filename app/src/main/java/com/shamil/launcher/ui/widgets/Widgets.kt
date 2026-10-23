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
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.home.HomeStyle
import androidx.compose.ui.graphics.lerp
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
 * full width, as tall as widgetHeight() says for his home style.
 */

/** How tall Seelie's own widgets are, from his home style: full or compact, a little taller inside a visible card. */
fun widgetHeight(style: HomeStyle): Dp = when {
    style.cardSize == "compact" -> if (style.cardVisible) 100.dp else 92.dp
    else -> if (style.cardVisible) 164.dp else 138.dp
}

/** The gold of the Gold bundle (headings, rings, "today"). */
private val GoldHi = Color(0xFFF3D79C)

/** Heading + ring colour inside the widgets (the theme's accent, or gold), set by [WidgetCard] from the home style. */
private val LocalWidgetAccent = staticCompositionLocalOf { Color.White }
/** The fill of the "today" circle, set by [WidgetCard]. */
private val LocalTodayFill = staticCompositionLocalOf { Color.White }
/** Compact widgets: fewer rows, smaller rings. */
private val LocalCompact = staticCompositionLocalOf { false }

/** Sundays in pink, like his Samsung lock screen calendar. */
private val SundayPink = Color(0xFFFF8CA8)
/** The dark "ink" for a number on a light circle (today). */
private val Ink = Color(0xFF171C29)

/** Text on the Outline style sits right on the wallpaper, so it gets the soft shadow; on Glass/Solid it doesn't need it. */
private val LocalWidgetShadow = staticCompositionLocalOf<Shadow?> { null }

/**
 * The card around one widget, drawn from his home style (23 Oct bundles): no card, glass, solid or outline, how see-through
 * it is (0% = fully transparent), its corner rounding and border. When the card is (almost) see-through, the text gets the
 * wallpaper shadow so it stays readable.
 */
@Composable
internal fun WidgetCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val look = LocalLauncherLook.current
    val hs = HomeStyleStore.get(LocalContext.current).style
    val none = hs.cardStyle == "none"
    val opacity = if (none) 0f else hs.cardOpacity / 100f
    val visible = hs.cardVisible
    val shape = RoundedCornerShape(if (none) 0.dp else hs.cardRadius.dp)
    val fill: Brush = when (hs.cardStyle) {
        // Glass: the theme's see-through panel colour, with a soft shine at the top.
        "glass" -> Brush.verticalGradient(listOf(lerp(look.panel, Color.White, 0.06f).copy(alpha = opacity), look.panel.copy(alpha = opacity)))
        // Solid: the sheet colours.
        "solid" -> Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = opacity), look.sheet.copy(alpha = opacity)))
        // Outline: a deep night fill behind a glowing edge.
        "outline" -> Brush.verticalGradient(listOf(Color(0xFF0E1126).copy(alpha = opacity), Color(0xFF0E1126).copy(alpha = opacity)))
        else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val gold = hs.cardAccent == "gold"
    val borderColor = when {
        hs.cardStyle == "outline" -> if (gold) Color(0xFFD3BC8E).copy(alpha = 0.8f) else look.accent.copy(alpha = 0.7f)
        hs.spirit -> look.accent.copy(alpha = 0.45f)
        else -> Color.White.copy(alpha = 0.17f)
    }
    val accent = if (gold) GoldHi else look.accent
    val today = when (hs.cardToday) { "gold" -> GoldHi; "accent" -> look.accent; else -> Color.White }
    CompositionLocalProvider(
        LocalWidgetShadow provides if (opacity < 0.5f) look.shadow else null,
        LocalWidgetAccent provides accent,
        LocalTodayFill provides today,
        LocalCompact provides (hs.cardSize == "compact"),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .clip(shape)
                .background(fill, shape)
                .then(if (!none && hs.cardBorder) Modifier.border(1.dp, borderColor, shape) else Modifier)
                // Inside a visible card the content sits in from the edge; without one it lines up with the clock.
                .padding(
                    start = if (visible) 14.dp else 0.dp, end = if (visible) 14.dp else 0.dp,
                    top = if (visible) 15.dp else 0.dp, bottom = if (visible) 12.dp else 0.dp,
                ),
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
            fontFamily = look.font, fontWeight = FontWeight.Bold, color = LocalWidgetAccent.current.copy(alpha = 0.9f),
            fontSize = 10.sp, letterSpacing = 2.2.sp, shadow = LocalWidgetShadow.current,
        ),
    )
}

/* ---------------- Month calendar ---------------- */

/**
 * The month, like his Samsung lock screen: a small header ("OKT 2026") and the grid, Monday first, Sundays pink,
 * other months' days left out, today a filled circle. The full date is already under the clock, so it isn't repeated
 * (23 Oct bundles). Compact: only this week and the next.
 */
@Composable
internal fun MonthWidget(today: LocalDate) {
    val look = LocalLauncherLook.current
    val locale = Locale.getDefault()
    val compact = LocalCompact.current
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Bottom) {
            // "okt." -> "OKT": the short month name, without the dot some languages add.
            val month = today.month.getDisplayName(DateStyle.SHORT, locale).trimEnd('.').uppercase(locale)
            Text(month, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, color = LocalWidgetAccent.current,
                fontSize = if (compact) 11.5.sp else 13.sp, letterSpacing = 2.sp, shadow = LocalWidgetShadow.current))
            Spacer(Modifier.width(6.dp))
            WText("${today.year}", if (compact) 11.sp else 12.sp, look.textDim, weight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(if (compact) 3.dp else 5.dp))
        MonthGrid(today, locale, compact)
    }
}

@Composable
private fun MonthGrid(today: LocalDate, locale: Locale, compact: Boolean) {
    val look = LocalLauncherLook.current
    val cellH = if (compact) 15.dp else 17.dp
    // The grid starts on the Monday on or before the 1st, and has as many weeks as the month needs (4-6).
    val first = today.withDayOfMonth(1)
    val lead = first.dayOfWeek.value - 1   // Monday = 1 in java.time, so this is how many days of last month there are
    val weeks = (lead + today.lengthOfMonth() + 6) / 7
    val start = first.minusDays(lead.toLong())
    // Compact: just the week with today in it and the one after.
    val todayWeek = ((today.toEpochDay() - start.toEpochDay()) / 7).toInt()
    val shownWeeks = if (compact) (todayWeek..minOf(todayWeek + 1, weeks - 1)) else (0 until weeks)
    Column(Modifier.fillMaxWidth()) {
        // Day letters in his phone's language (Dutch: M D W D V Z Z).
        Row(Modifier.fillMaxWidth()) {
            for (d in 1..7) {
                val letter = DayOfWeek.of(d).getDisplayName(DateStyle.NARROW, locale).uppercase(locale)
                Box(Modifier.weight(1f).height(cellH), contentAlignment = Alignment.Center) {
                    WText(letter, if (compact) 9.5.sp else 10.5.sp, if (d == 7) SundayPink else look.textDim, weight = FontWeight.Bold)
                }
            }
        }
        for (w in shownWeeks) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val date = start.plusDays((w * 7 + d).toLong())
                    Box(Modifier.weight(1f).height(cellH), contentAlignment = Alignment.Center) {
                        when {
                            date.month != today.month -> {}   // other months' days: left out, so the month reads cleanly
                            date == today -> Box(
                                Modifier.size(if (compact) 18.dp else 20.dp).background(LocalTodayFill.current, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${date.dayOfMonth}", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = Ink,
                                    fontSize = if (compact) 11.sp else 12.sp))
                            }
                            else -> WText("${date.dayOfMonth}", if (compact) 11.5.sp else 12.5.sp,
                                if (d == 6) SundayPink else look.text, weight = FontWeight.Bold)
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
    val ringColor = LocalWidgetAccent.current
    val compact = LocalCompact.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(if (compact) 50.dp else 70.dp)
                .drawBehind {
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))))
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2 + 4.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    // 270° of ring, starting bottom-left (135°) and going clockwise to bottom-right.
                    drawArc(look.text.copy(alpha = 0.14f), 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (shown > 0.005f) {
                        drawArc(ringColor, 135f, 270f * shown, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            WText(value, if (compact) 12.sp else 15.sp, look.text, weight = FontWeight.Bold)
        }
        Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
        WText(label, if (compact) 9.5.sp else 11.sp, look.textDim)
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
                            .size(if (LocalCompact.current) 26.dp else 34.dp)
                            .then(if (isToday) Modifier.background(LocalTodayFill.current, CircleShape) else Modifier),
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
                        10.sp, LocalWidgetAccent.current,
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
            lines.take(if (LocalCompact.current) 2 else 3).forEach { line ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    // A small hollow circle in the accent, like a to-do bullet.
                    Box(Modifier.size(12.dp).border(1.5.dp, LocalWidgetAccent.current, CircleShape))
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
