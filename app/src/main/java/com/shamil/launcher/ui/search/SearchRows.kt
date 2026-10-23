package com.shamil.launcher.ui.search

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.theme.LocalLauncherLook

/*
 * The building blocks of the search results (from the approved prototype): frosted group cards with a small
 * gold header, pill-shaped rows, the big answer / command card, and little icons drawn in code.
 */

/** Small icons for search, drawn in code (like the search button's magnifying glass), so no image files are needed. */
enum class Glyph { Calc, Globe, Store, Person, Wifi, Bluetooth, Torch, Note, Timer, Alarm, Command }

/** Draws one [Glyph] in [color]: thin rounded lines, like the app's other icons. */
@Composable
internal fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = w * 0.09f
        val stroke = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // p(0.5f, 0.5f) = the middle; coordinates are fractions of the icon's size.
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        fun lineFrom(a: Offset, b: Offset) = drawLine(color, a, b, strokeWidth = line, cap = StrokeCap.Round)
        // A list, not "vararg": Kotlin doesn't allow vararg of Compose's Offset (it's a value class).
        fun path(points: List<Offset>, closed: Boolean = false) = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            if (closed) close()
        }
        when (glyph) {
            Glyph.Calc -> {
                drawRoundRect(color, topLeft = p(0.22f, 0.1f), size = Size(w * 0.56f, h * 0.8f), cornerRadius = CornerRadius(w * 0.1f), style = stroke)
                lineFrom(p(0.36f, 0.3f), p(0.64f, 0.3f))
                for (row in 0..1) for (col in 0..2) {
                    drawCircle(color, radius = w * 0.05f, center = p(0.36f + col * 0.14f, 0.53f + row * 0.18f))
                }
            }
            Glyph.Globe -> {
                drawCircle(color, radius = w * 0.38f, center = p(0.5f, 0.5f), style = stroke)
                drawOval(color, topLeft = p(0.34f, 0.12f), size = Size(w * 0.32f, h * 0.76f), style = stroke)
                lineFrom(p(0.14f, 0.5f), p(0.86f, 0.5f))
            }
            Glyph.Store -> {
                // A shopping bag with a play triangle on it.
                drawRoundRect(color, topLeft = p(0.18f, 0.32f), size = Size(w * 0.64f, h * 0.56f), cornerRadius = CornerRadius(w * 0.08f), style = stroke)
                drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = p(0.34f, 0.12f), size = Size(w * 0.32f, h * 0.4f), style = stroke)
                drawPath(path(listOf(p(0.44f, 0.47f), p(0.62f, 0.6f), p(0.44f, 0.73f)), closed = true), color)
            }
            Glyph.Person -> {
                drawCircle(color, radius = w * 0.16f, center = p(0.5f, 0.32f), style = stroke)
                drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = p(0.2f, 0.6f), size = Size(w * 0.6f, h * 0.5f), style = stroke)
            }
            Glyph.Wifi -> {
                val centre = p(0.5f, 0.8f)
                listOf(0.18f, 0.36f, 0.54f).forEach { r ->
                    val radius = w * r
                    drawArc(color, startAngle = 225f, sweepAngle = 90f, useCenter = false,
                        topLeft = Offset(centre.x - radius, centre.y - radius), size = Size(radius * 2, radius * 2), style = stroke)
                }
                drawCircle(color, radius = w * 0.055f, center = centre)
            }
            Glyph.Bluetooth -> drawPath(path(listOf(p(0.3f, 0.32f), p(0.68f, 0.66f), p(0.5f, 0.84f), p(0.5f, 0.16f), p(0.68f, 0.34f), p(0.3f, 0.68f))), color, style = stroke)
            Glyph.Torch -> {
                drawPath(path(listOf(p(0.3f, 0.12f), p(0.7f, 0.12f), p(0.62f, 0.38f), p(0.38f, 0.38f)), closed = true), color, style = stroke)
                drawRoundRect(color, topLeft = p(0.38f, 0.38f), size = Size(w * 0.24f, h * 0.5f), cornerRadius = CornerRadius(w * 0.05f), style = stroke)
                lineFrom(p(0.5f, 0.52f), p(0.5f, 0.6f))
            }
            Glyph.Note -> {
                drawRoundRect(color, topLeft = p(0.22f, 0.12f), size = Size(w * 0.56f, h * 0.76f), cornerRadius = CornerRadius(w * 0.08f), style = stroke)
                lineFrom(p(0.34f, 0.35f), p(0.66f, 0.35f))
                lineFrom(p(0.34f, 0.5f), p(0.66f, 0.5f))
                lineFrom(p(0.34f, 0.65f), p(0.54f, 0.65f))
            }
            Glyph.Timer -> {
                drawCircle(color, radius = w * 0.32f, center = p(0.5f, 0.57f), style = stroke)
                lineFrom(p(0.5f, 0.57f), p(0.5f, 0.4f))
                lineFrom(p(0.42f, 0.1f), p(0.58f, 0.1f))
                lineFrom(p(0.5f, 0.1f), p(0.5f, 0.24f))
            }
            Glyph.Alarm -> {
                drawCircle(color, radius = w * 0.3f, center = p(0.5f, 0.56f), style = stroke)
                lineFrom(p(0.5f, 0.56f), p(0.5f, 0.4f))
                lineFrom(p(0.5f, 0.56f), p(0.62f, 0.64f))
                lineFrom(p(0.14f, 0.26f), p(0.27f, 0.13f))
                lineFrom(p(0.86f, 0.26f), p(0.73f, 0.13f))
            }
            Glyph.Command -> {
                drawPath(path(listOf(p(0.26f, 0.3f), p(0.5f, 0.5f), p(0.26f, 0.7f))), color, style = stroke)
                lineFrom(p(0.56f, 0.72f), p(0.76f, 0.72f))
            }
        }
    }
}

/** The small rounded square in front of a row: a glyph or a drawable, in the accent colour. */
@Composable
internal fun ResultBadge(glyph: Glyph? = null, @DrawableRes icon: Int? = null, size: Dp = 36.dp) {
    val look = LocalLauncherLook.current
    Box(
        Modifier.size(size).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) GlyphIcon(glyph, look.accent, Modifier.size(size * 0.6f))
        else if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = look.accent, modifier = Modifier.size(size * 0.6f))
    }
}

/** A contact's round badge: their first letter on a soft accent gradient. */
@Composable
internal fun Avatar(name: String) {
    val look = LocalLauncherLook.current
    Box(
        Modifier
            .size(36.dp)
            .background(Brush.linearGradient(listOf(look.accent.copy(alpha = 0.85f), look.accent.copy(alpha = 0.35f))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp),
        )
    }
}

/** A frosted card holding one kind of result, with a small gold header ("APPS") and an optional note on the right. */
@Composable
internal fun ResultGroup(title: String, note: String = "", content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLauncherLook.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(look.panel)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 10.sp, letterSpacing = 2.2.sp))
            Spacer(Modifier.weight(1f))
            if (note.isNotEmpty()) {
                Text(note, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 11.sp))
            }
        }
        content()
    }
}

/**
 * One result row: badge, title (+ a dim sub line after it), and something small on the right.
 * [best] = the row Enter opens: softly lit in the accent, with an ↵ hint like Niagara's "press Search to open".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ResultRow(
    title: String,
    sub: String? = null,
    best: Boolean = false,
    monospace: Boolean = false,
    leading: @Composable () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .pressFeel(press, look.accent, 25.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(if (best) look.accent.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(
                interactionSource = press,
                indication = null,
                onClick = onClick,
                // combinedClickable buzzes by itself on a long press (hapticFeedbackEnabled).
                onLongClick = onLongClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = if (monospace) FontFamily.Monospace else look.font,
                    color = if (monospace) look.accent else look.text,
                    fontSize = if (monospace) 15.sp else 16.sp,
                ),
            )
            if (sub != null) {
                Spacer(Modifier.width(8.dp))
                Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
            }
        }
        if (best) {
            Spacer(Modifier.width(8.dp))
            KeyHint("↵")
        }
    }
}

/** A little keyboard-key label, like "Enter" or "↵". */
@Composable
internal fun KeyHint(text: String) {
    val look = LocalLauncherLook.current
    Text(
        text,
        modifier = Modifier
            .border(1.dp, look.accent.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 11.sp),
    )
}

/**
 * The big card for an answer or a command: round badge, a small line, then the title (big for answers),
 * and on the right either a switch (torch) or the key that runs it.
 */
@Composable
internal fun ActionCard(
    glyph: Glyph,
    small: String,
    title: String,
    big: Boolean,
    toggle: Boolean?,
    keyHint: String,
    onClick: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .pressFeel(press, look.accent, 22.dp)
            .clip(shape)
            .background(look.panel)
            .background(Brush.horizontalGradient(listOf(look.accent.copy(alpha = 0.18f), Color.Transparent)))
            .border(1.dp, look.accent.copy(alpha = 0.35f), shape)
            .combinedClickableNoIndication(press, onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(look.accent.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, look.accent, Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(small, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
            Text(
                title,
                maxLines = if (big) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = look.font,
                    fontWeight = if (big) FontWeight.Light else FontWeight.Bold,
                    color = look.text,
                    fontSize = if (big) 34.sp else 16.sp,
                ),
            )
        }
        Spacer(Modifier.width(10.dp))
        if (toggle != null) MiniSwitch(toggle) else KeyHint(keyHint)
    }
}

/** A small on/off switch (display only: the card around it is what you tap). */
@Composable
internal fun MiniSwitch(on: Boolean) {
    val look = LocalLauncherLook.current
    // The knob springs across, with a tiny overshoot.
    val slide by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "switch")
    Box(
        Modifier
            .size(width = 44.dp, height = 26.dp)
            .background(if (on) look.accent else Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .graphicsLayer { translationX = slide * 18.dp.toPx() }
                .background(if (on) look.sheet.copy(alpha = 1f) else Color.White, CircleShape),
        )
    }
}

/** Shown before anything is typed: what search can do, plus example commands to tap. */
@Composable
internal fun EmptySearch(commandsOn: Boolean, onPick: (String) -> Unit) {
    val look = LocalLauncherLook.current
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Search everything", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = 17.sp, shadow = look.shadow))
        Spacer(Modifier.height(2.dp))
        Text("Apps, settings, contacts, the web. Sums too: 12*7", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp, shadow = look.shadow))
        if (commandsOn) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("> timer 5m", "> calc 12*7", "> wifi", "> torch").forEach { example ->
                    val press = remember { MutableInteractionSource() }
                    Text(
                        example,
                        modifier = Modifier
                            .pressFeel(press, look.accent, 16.dp)
                            .clip(CircleShape)
                            .background(look.panel)
                            .border(1.dp, look.accent.copy(alpha = 0.25f), CircleShape)
                            .combinedClickableNoIndication(press) { onPick(example) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

/** A tap without the grey ripple (pressFeel draws the press instead). */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoIndication(source: MutableInteractionSource, onClick: () -> Unit): Modifier =
    combinedClickable(interactionSource = source, indication = null, onClick = onClick)
