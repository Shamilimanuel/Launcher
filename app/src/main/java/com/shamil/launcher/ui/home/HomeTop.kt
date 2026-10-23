package com.shamil.launcher.ui.home

import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.clock.LocalClockStyleOverride
import com.shamil.launcher.ui.Glance
import com.shamil.launcher.ui.GlanceState
import com.shamil.launcher.ui.clock.ClockFace
import com.shamil.launcher.ui.clock.ClockStore
import com.shamil.launcher.ui.clock.DateFace
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.CormorantGaramond
import com.shamil.launcher.ui.theme.LauncherLook
import com.shamil.launcher.ui.theme.LocalLauncherLook
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The top of the home screen drawn from his home style: clock, date and glance line, with one left edge and the
 * bundle's spacing (the widget stack sits right under it, in the favourites). See HomeStyle.kt.
 */

/** The gold of the Gold bundle. */
internal val Gold = Color(0xFFF3D79C)

/** A light system font for the Light clock weight (Seelie's own font files start at Regular). */
private val SystemLight: FontFamily = FontFamily(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL))

/** The colour picked for a piece of text: "white" (the theme's text, a little dimmed by [dim]), "accent" or "gold". */
internal fun LauncherLook.pick(colour: String, dim: Float = 1f): Color = when (colour) {
    "accent" -> accent
    "gold" -> Gold
    else -> text.copy(alpha = text.alpha * dim)
}

/**
 * The clock, date and glance line. [clickable] modifiers come from the home screen (tap the clock = clock app, …).
 * With the Seelie touch on, a small glowing spirit drifts beside the clock.
 */
@Composable
fun HomeTop(
    time: LocalDateTime,
    glance: GlanceState,
    modifier: Modifier = Modifier,
    clockModifier: Modifier = Modifier,
    dateModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hs = currentHomeStyle()
    val clocks = remember { ClockStore.get(context) }
    val is24h = DateFormat.is24HourFormat(context)
    Box(modifier) {
        Column(Modifier.padding(start = hs.edge.dp)) {
            // Optical alignment: big digits carry extra empty space on their left ("side bearing"), so the clock is
            // nudged left by ~7% of its size to line up with the date by eye (23 Oct: "the clock needs to be a bit more left").
            val base = LocalLauncherLook.current
            CompositionLocalProvider(LocalLauncherLook provides base.forPart("clock")) {
                ClockFace(LocalClockStyleOverride.current ?: clocks.clockStyle, time, is24h, clockModifier.offset(x = -(hs.clockSize * 0.07f).dp))
            }
            Spacer(Modifier.height(hs.gap1.dp))
            if (hs.inline) {
                // Date and glance side by side; a small dot between them unless one of them is a pill.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalLauncherLook provides base.forPart("date")) { BundleDate(time, dateModifier) }
                    val pills = hs.dateStyle == "pill" || hs.glanceStyle == "pill"
                    if (!pills) {
                        Spacer(Modifier.width(hs.gap2.dp / 2))
                        Box(Modifier.size(3.dp).background(LocalLauncherLook.current.text.copy(alpha = 0.55f), CircleShape))
                    }
                    Spacer(Modifier.width(if (pills) hs.gap2.dp else hs.gap2.dp / 2))
                    CompositionLocalProvider(LocalLauncherLook provides base.forPart("line")) { Glance(glance) }
                }
                // His next event today (tap = agenda), under the line.
                CompositionLocalProvider(LocalLauncherLook provides base.forPart("line")) { EventLine(Modifier.padding(top = (hs.gap2 / 2).dp)) }
            } else {
                CompositionLocalProvider(LocalLauncherLook provides base.forPart("date")) { BundleDate(time, dateModifier) }
                Spacer(Modifier.height(hs.gap2.dp))
                CompositionLocalProvider(LocalLauncherLook provides base.forPart("line")) { Glance(glance) }
                CompositionLocalProvider(LocalLauncherLook provides base.forPart("line")) { EventLine(Modifier.padding(top = (hs.gap2 / 2).dp)) }
            }
        }
        if (hs.spirit) Spirit(Modifier.offset(x = hs.edge.dp + 196.dp, y = 30.dp))
    }
}

/** The bundle's clock (what "Classic" in Clock & date now means). [scale] shrinks it for previews. */
@Composable
fun BundleClock(time: LocalDateTime, is24h: Boolean, modifier: Modifier = Modifier, scale: Float = 1f) {
    val look = LocalLauncherLook.current
    val hs = currentHomeStyle()
    val cinzel = hs.clockFont == "cinzel"
    val family = when {
        cinzel -> Cinzel
        hs.clockWeight <= 300 -> SystemLight
        else -> look.font
    }
    val weight = FontWeight(if (cinzel && hs.clockWeight >= 800) 700 else hs.clockWeight.coerceIn(100, 900))
    val size = (hs.clockSize * scale).sp
    val spacing = if (cinzel) 0.02.em else if (hs.clockSize >= 44) (-1 * scale).sp else (-0.5f * scale).sp
    val base = TextStyle(fontFamily = family, fontWeight = weight, fontSize = size, letterSpacing = spacing)
    val style = when (hs.clockColor) {
        "accent" -> base.copy(color = look.accent, shadow = Shadow(look.accent.copy(alpha = 0.45f), blurRadius = 24f * scale))
        "gold" -> base.copy(brush = Brush.verticalGradient(listOf(Color(0xFFFFF0C8), Gold, Color(0xFFD3BC8E), Color(0xFFB8995F))),
            shadow = Shadow(Gold.copy(alpha = 0.35f), blurRadius = 14f * scale))
        "glass" -> base.copy(brush = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.58f))),
            shadow = look.shadow)
        else -> base.copy(color = look.text, shadow = look.shadow)
    }
    val hh = time.format(DateTimeFormatter.ofPattern(if (is24h) "HH" else "h"))
    val mm = time.format(DateTimeFormatter.ofPattern("mm"))
    if (hs.spirit && (hs.clockColor == "white" || hs.clockColor == "accent")) {
        // The Seelie touch: the colon is the accent colour and slowly breathes.
        val breath by rememberInfiniteTransition(label = "colon")
            .animateFloat(1f, 0.35f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "breath")
        Row(modifier) {
            Text(hh, style = style)
            Text(":", modifier = Modifier.graphicsLayer { alpha = breath },
                style = style.copy(color = look.accent, shadow = Shadow(look.accent, blurRadius = 16f * scale)))
            Text(mm, style = style)
        }
    } else {
        Text("$hh:$mm", modifier = modifier, style = style)
    }
}

/**
 * Every date look, in the order the settings show them: (saved name, title). 23 Oct he asked for "more Date looks",
 * so besides plain / capitals / pill there are nine more.
 */
val DATE_LOOKS = listOf(
    "plain" to "Plain", "short" to "Short", "caps" to "Capitals", "split" to "Split", "big" to "Big day", "badge" to "Badge",
    "serif" to "Serif", "spaced" to "Spaced", "numbers" to "Numbers", "pill" to "Pill", "outline" to "Outline", "stamp" to "Stamp",
)

/**
 * The bundle's date (what "Plain" in Clock & date now means), in one of [DATE_LOOKS], maybe with a hairline under it.
 * [lookOverride] draws another look (for the sample tiles in settings) without changing his choice.
 */
@Composable
fun BundleDate(time: LocalDateTime, modifier: Modifier = Modifier, scale: Float = 1f, lookOverride: String? = null) {
    val look = LocalLauncherLook.current
    val hs = currentHomeStyle()
    val dateLook = lookOverride ?: hs.dateStyle
    val locale = Locale.getDefault()
    fun format(pattern: String) = time.format(DateTimeFormatter.ofPattern(pattern, locale)).replace(".", "")
    // His phone's language: "vrijdag 23 oktober".
    val words = format("EEEE d MMMM")
    val colour = look.pick(hs.dateColor, dim = 0.86f)
    val size = (hs.dateSize * scale).sp
    val plain = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = size, color = colour, shadow = look.shadow)
    val bold = plain.copy(fontWeight = FontWeight.Bold)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (dateLook) {
                // "vr 23 okt"
                "short" -> Text(format("EEE d MMM"), style = plain)
                "caps" -> Text(words.uppercase(locale), style = plain.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.22.em))
                // The day's name in the accent, the rest calm: "Vrijdag 23 oktober".
                "split" -> {
                    Text(format("EEEE").replaceFirstChar { it.titlecase(locale) }, style = bold.copy(color = look.accent))
                    Text(" " + format("d MMMM"), style = plain)
                }
                // A big day number with the day and month stacked small beside it.
                "big" -> {
                    Text(format("d"), style = bold.copy(fontSize = size * 2.3f, letterSpacing = (-0.02).em))
                    Spacer(Modifier.width(8.dp * scale))
                    Column {
                        Text(format("EEEE").uppercase(locale), style = plain.copy(fontWeight = FontWeight.ExtraBold, fontSize = size * 0.72f, letterSpacing = 0.18.em))
                        Text(format("MMMM").uppercase(locale), style = plain.copy(fontSize = size * 0.72f, letterSpacing = 0.18.em, color = colour.copy(alpha = colour.alpha * 0.75f)))
                    }
                }
                // The day number in a glowing accent circle.
                "badge" -> {
                    Box(
                        Modifier
                            .size(size.value.dp * 1.9f)
                            .drawBehind { drawCircle(Brush.radialGradient(listOf(look.accent.copy(alpha = 0.5f), Color.Transparent)), radius = this.size.minDimension * 0.75f) }
                            .background(look.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(format("d"), style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = size * 0.95f, color = look.sheet.copy(alpha = 1f)))
                    }
                    Spacer(Modifier.width(8.dp * scale))
                    Text(format("EEEE") + " · " + format("MMMM"), style = plain)
                }
                // Elegant italic serif.
                "serif" -> Text(words, style = plain.copy(fontFamily = CormorantGaramond, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, fontSize = size * 1.25f))
                // Wide-spaced capitals: "VR 23 OKT".
                "spaced" -> Text(format("EEE d MMM").uppercase(locale), style = plain.copy(fontWeight = FontWeight.Bold, fontSize = size * 0.85f, letterSpacing = 0.45.em))
                // "23 · 10 · 2026", the dots in the accent.
                "numbers" -> {
                    val dot = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = size, color = look.accent)
                    Text(format("dd"), style = bold)
                    Text(" · ", style = dot)
                    Text(format("MM"), style = bold)
                    Text(" · ", style = dot)
                    Text(format("yyyy"), style = plain)
                }
                "pill" -> Text(words, modifier = Modifier.homeChip(look, scale), style = bold.copy(shadow = null))
                // A pill with only a glowing accent border.
                "outline" -> Text(
                    words,
                    modifier = Modifier
                        .border(1.dp, look.accent.copy(alpha = 0.75f), RoundedCornerShape(16.dp * scale))
                        .padding(horizontal = 13.dp * scale, vertical = 5.dp * scale),
                    style = bold,
                )
                // A little tilted postmark tag in small capitals.
                "stamp" -> Text(
                    format("EEE d MMM").uppercase(locale),
                    modifier = Modifier
                        .graphicsLayer { rotationZ = -3f }
                        .border(1.5.dp, colour.copy(alpha = 0.8f), RoundedCornerShape(4.dp * scale))
                        .padding(horizontal = 9.dp * scale, vertical = 3.dp * scale),
                    style = plain.copy(fontWeight = FontWeight.ExtraBold, fontSize = size * 0.8f, letterSpacing = 0.16.em),
                )
                else -> Text(words, style = plain)
            }
            if (hs.spirit && !hs.dateRule) {
                Text(" ✦", style = TextStyle(color = look.accent, fontSize = size * 0.8f, shadow = Shadow(look.accent, blurRadius = 8f)))
            }
        }
        if (hs.dateRule) {
            Spacer(Modifier.height(7.dp * scale))
            Box(Modifier.width(150.dp * scale).height(1.dp).background(Brush.horizontalGradient(listOf(colour, Color.Transparent))))
        }
    }
}

/** The glass pill behind the date or glance line in the Glass bundle (one shared look, so they match). */
internal fun Modifier.homeChip(look: LauncherLook, scale: Float = 1f): Modifier = this
    .background(look.panel.copy(alpha = 0.5f), RoundedCornerShape(16.dp * scale))
    .border(1.dp, Color.White.copy(alpha = 0.17f), RoundedCornerShape(16.dp * scale))
    .padding(horizontal = 13.dp * scale, vertical = 6.dp * scale)

/** How the glance line's text looks in his home style. */
internal data class GlanceLook(val size: TextUnit, val color: Color, val weight: FontWeight, val shadow: Shadow?, val chip: Boolean)

internal fun glanceLook(hs: HomeStyle, look: LauncherLook): GlanceLook {
    val colour = look.pick(hs.glanceColor, dim = 0.76f)
    return when (hs.glanceStyle) {
        "pill" -> GlanceLook(hs.glanceSize.sp, colour, FontWeight.Bold, null, chip = true)
        "glow" -> GlanceLook(hs.glanceSize.sp, colour, FontWeight.Bold,
            Shadow(if (hs.glanceColor == "gold") Gold else look.accent, blurRadius = 22f), chip = false)
        else -> GlanceLook(hs.glanceSize.sp, colour, FontWeight.SemiBold, look.shadow, chip = false)
    }
}

/**
 * The Seelie touch: a tiny glowing spirit that drifts slowly beside the clock (an 11-second loop), with a short trail.
 * It only moves while it's on screen; Android's animation scale slows or stops it like any other animation.
 */
@Composable
private fun Spirit(modifier: Modifier = Modifier) {
    val look = LocalLauncherLook.current
    val t by rememberInfiniteTransition(label = "spirit")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(11_000, easing = LinearEasing)), label = "drift")
    Box(
        modifier
            .size(80.dp)
            .drawBehind {
                // The drift path: a slow loop down and back, a little left and right (like the bundles page).
                val angle = t * 2 * Math.PI
                val dx = (-14 * kotlin.math.sin(angle) + 8 * kotlin.math.sin(2 * angle)).toFloat().dp.toPx()
                val dy = (36 * (1 - kotlin.math.cos(angle))).toFloat().dp.toPx() / 2
                val centre = Offset(size.width / 2 + dx, 12.dp.toPx() + dy)
                val pulse = 1f + 0.2f * kotlin.math.sin(angle * 5).toFloat()
                // The trail: three fading dots behind it.
                for (k in 1..3) {
                    drawCircle(look.accent.copy(alpha = 0.5f / k), radius = (3.2f - k * 0.6f).dp.toPx(),
                        center = centre + Offset(-(7 * k).dp.toPx(), (5 * k).dp.toPx()))
                }
                // The glow, then the bright core.
                drawCircle(Brush.radialGradient(listOf(look.accent.copy(alpha = 0.45f), Color.Transparent), center = centre, radius = 20.dp.toPx() * pulse),
                    radius = 20.dp.toPx() * pulse, center = centre)
                drawCircle(Brush.radialGradient(listOf(Color.White, look.accent, Color.Transparent), center = centre, radius = 6.dp.toPx()),
                    radius = 6.dp.toPx(), center = centre)
            }
    )
}

