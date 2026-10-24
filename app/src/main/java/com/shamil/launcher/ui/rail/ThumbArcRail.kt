package com.shamil.launcher.ui.rail

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.DOT
import com.shamil.launcher.ui.STAR
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * "Thumb arc" (his pick #3, 24 Oct). The same quarter circle round the bottom-right corner as Guide on the arc, and
 * the same touch (ArcRailState + arcRailInput from ArcRail.kt), but no guide orb: the letters near your thumb bulge
 * outwards along the curve and grow a little, and a short bright gold arc follows your thumb. At rest only the faint
 * curve and the letter dots show. Look, motion and numbers are the playground's mkArc().
 * This file only draws; HomeScreen swaps ThumbArcLayer in for ArcRailLayer when this style is chosen.
 */

private val THUMB_COUNT = RAIL_ITEMS.size
/** How far the letters near the thumb bulge outwards (playground units, the page's "Bulge" slider). */
private const val THUMB_BULGE = 32f
/** How many letters around the thumb join the bulge ("sigma" of the bell curve). */
private const val THUMB_BULGE_WIDTH = 2.5f
/** How much bigger the letters at the top of the bulge get. */
private const val THUMB_GROW = 0.38f
/** The bright arc that follows the thumb reaches this many letters either side. */
private const val THUMB_SEGMENT = 3f
private const val THUMB_RAD_TO_DEG = 57.29578f

private val THUMB_GOLD = Color(0xFFF3D79C)
private val THUMB_LIGHT = Color(0xFFFFF6DC)

/** The curve's path, brushes and strokes for one arc size, made once (not per frame). */
private class ThumbArcPaint(val g: ArcGeometry) {
    val track = Path().apply {
        var f = -0.9f
        var first = true
        while (f <= THUMB_COUNT - 0.1f) {
            val q = g.point(f)
            if (first) { moveTo(q.x, q.y); first = false } else lineTo(q.x, q.y)
            f += 0.25f
        }
    }
    val trackGlow = Stroke(width = 12f * g.s, cap = StrokeCap.Round)
    val trackLine = Stroke(width = 1.6f * g.s, cap = StrokeCap.Round)
    val segmentGlow = Stroke(width = 14f * g.s, cap = StrokeCap.Round)
    val segmentCore = Stroke(width = 2.2f * g.s, cap = StrokeCap.Round)
    val bubbleRing = Stroke(width = 2f * g.s)
    val halo = Brush.radialGradient(listOf(THUMB_GOLD.copy(alpha = 0.5f), Color.Transparent), center = Offset.Zero, radius = 16f * g.s)
}

/** Keeps the paint between frames (rebuilt only when the arc's size changes). */
private class ThumbArcSlot { var paint: ThumbArcPaint? = null }

/**
 * The thumb arc's drawing: a full-screen layer of its own (redrawing it never redraws the home), lit only while it
 * moves. Use it with the same [state] and `Modifier.arcRailInput(state, ...)` as Guide on the arc.
 */
@Composable
fun ThumbArcLayer(state: ArcRailState, present: Set<String>, modifier: Modifier = Modifier) {
    val look = LocalLauncherLook.current
    val measurer = rememberTextMeasurer()
    val letterStyle = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
    val bubbleStyle = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White)
    val letters = remember(look.font) { RAIL_ITEMS.map { measurer.measure(it, letterStyle) } }
    val bubbles = remember(look.font) { RAIL_ITEMS.map { measurer.measure(it, bubbleStyle) } }
    val has = remember(present) { BooleanArray(THUMB_COUNT) { RAIL_ITEMS[it] == STAR || RAIL_ITEMS[it] == DOT || RAIL_ITEMS[it] in present } }
    val railColour = if (look.rail != Color.Unspecified) look.rail else look.text
    val slot = remember { ThumbArcSlot() }
    // The frame loop: only while something moves; it wakes on a touch and sleeps when everything has settled.
    // (ArcRailState.step also moves the guide's orb and trail; this layer simply doesn't draw them.)
    LaunchedEffect(state.awake) {
        if (!state.awake) return@LaunchedEffect
        var last = -1L
        while (true) {
            val keep = withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min(0.05f, (now - last) / 1_000_000_000f)
                last = now
                state.step(dt).also { state.tick.intValue++ }
            }
            if (!keep) { state.awake = false; break }
        }
    }
    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer()
            .drawBehind {
                state.tick.intValue   // redraw on every frame of the loop
                val g = state.geo?.takeIf { it.w == size.width && it.h == size.height } ?: ArcGeometry(size.width, size.height).also { state.geo = it }
                val p = slot.paint?.takeIf { it.g === g } ?: ThumbArcPaint(g).also { slot.paint = it }
                drawThumbArc(g, p, state, look.accent, look.panel, railColour, has, letters, bubbles)
            }
    )
}

private fun DrawScope.drawThumbArc(
    g: ArcGeometry, p: ThumbArcPaint, st: ArcRailState, accent: Color, panel: Color, railColour: Color, has: BooleanArray,
    letters: List<TextLayoutResult>, bubbles: List<TextLayoutResult>,
) {
    val s = g.s
    val act = st.act
    val fs = st.fs
    // The curve itself: faint at rest, brighter while you use it (the same as Guide on the arc).
    drawPath(p.track, THUMB_GOLD.copy(alpha = 0.06f + 0.12f * act), style = p.trackGlow)
    drawPath(p.track, THUMB_GOLD.copy(alpha = 0.22f + 0.4f * act), style = p.trackLine)
    // The short bright arc that follows your thumb along the curve, a little outside it.
    if (act > 0.01f) {
        val a1 = g.angle(fs - THUMB_SEGMENT)
        val a2 = g.angle(fs + THUMB_SEGMENT)
        val r = g.r + 4f * s
        // ArcGeometry measures angles from straight up, turning left; drawArc measures them in degrees from
        // pointing right, turning clockwise. 270° - a converts one into the other.
        val start = 270f - a2 * THUMB_RAD_TO_DEG
        val sweep = (a2 - a1) * THUMB_RAD_TO_DEG
        val topLeft = Offset(g.px - r, g.py - r)
        val box = Size(2 * r, 2 * r)
        drawArc(THUMB_GOLD.copy(alpha = 0.35f * act), start, sweep, useCenter = false, topLeft = topLeft, size = box, style = p.segmentGlow)
        drawArc(THUMB_LIGHT.copy(alpha = 0.9f * act), start, sweep, useCenter = false, topLeft = topLeft, size = box, style = p.segmentCore)
    }
    // The letters: tiny dots at rest; while you touch, the letters themselves, bulging out around the thumb.
    for (i in 0 until THUMB_COUNT) {
        val dotA = 1f - act
        if (dotA > 0.02f) {
            drawCircle(
                THUMB_GOLD.copy(alpha = (if (has[i]) 0.4f else 0.18f) * dotA), radius = (if (has[i]) 1.7f else 1f) * s,
                center = g.point(i.toFloat(), -17f * s),
            )
        }
        if (act < 0.02f) continue
        val pull = act * thumbBell(abs(fs - i), THUMB_BULGE_WIDTH)
        val q = g.point(i.toFloat(), THUMB_BULGE * s * pull)
        val grow = 1f + THUMB_GROW * pull
        val colour = when {
            i == st.active -> accent
            has[i] -> railColour
            else -> railColour.copy(alpha = 0.4f)
        }
        val tl = letters[i]
        withTransform({ translate(q.x, q.y); scale(grow, grow, Offset.Zero) }) {
            val glow = (pull * 0.95f).coerceIn(0f, 1f)
            if (glow > 0.02f) drawCircle(p.halo, radius = 16f * s, center = Offset.Zero, alpha = glow)
            drawText(tl, color = colour, topLeft = Offset(-tl.size.width / 2f, -tl.size.height / 2f), alpha = act.coerceIn(0f, 1f))
        }
    }
    // The letter bubble, further out from the bulge (kept on the screen).
    if (act > 0.01f) {
        val idx = fs.roundToInt().coerceIn(0, THUMB_COUNT - 1)
        val q0 = g.point(fs, (THUMB_BULGE + 84f) * s)
        val q = Offset(max(46f * s, q0.x), q0.y)
        val r = 30f * s * (0.8f + 0.2f * act)
        drawCircle(panel.copy(alpha = 0.95f * act), radius = r, center = q)
        drawCircle(THUMB_GOLD.copy(alpha = act), radius = r, center = q, style = p.bubbleRing)
        val tl = bubbles[idx]
        drawText(tl, color = THUMB_LIGHT, topLeft = Offset(q.x - tl.size.width / 2f, q.y - tl.size.height / 2f), alpha = act)
    }
}

private fun thumbBell(d: Float, sigma: Float) = exp(-(d * d) / (2 * sigma * sigma))
