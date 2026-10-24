package com.shamil.launcher.ui.rail

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.DOT
import com.shamil.launcher.ui.STAR
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * "Lanterns" (his pick #6, 24 Oct). The ☆ # A–Z • letters hang like little Liyue paper lanterns on a thin gold
 * string down the right edge. Lanterns with apps glow warm, empty ones are dark. The lantern under your thumb swells,
 * glows and drifts a little left; its neighbours swing like pendulums, pushed by how fast your thumb moves.
 * Touch works exactly like the Niagara-style LetterRail: drag past the middle of the screen to slide onto the list,
 * hold • for the vault. Look, motion and numbers are the playground's mkLantern() (own-rail-ideas.html).
 *
 * Units: the playground's rail has 23.3 px per letter. Lantern sizes use that (so a lantern fits its slot like on the
 * page); sideways distances use the screen width / 390, like the arc.
 */

private val LANTERN_COUNT = RAIL_ITEMS.size
private const val LANTERN_HOLD_MS = 600L
/** Width of the lantern column inside the 46dp touch strip (the same as LetterRail's letters). */
private val LANTERN_COLUMN = 28.dp
/** The playground's room per letter (676 / 29), the unit lantern sizes are measured in. */
private const val LANTERN_PAGE_SLOT = 676f / 29f
/** How far the lanterns lean towards your thumb while you hold it near them (the page's "Swing" slider). */
private const val LANTERN_LEAN = 0.9f
/**
 * A gentle sway even when nobody touches the rail. OFF (0): it would keep the screen redrawing all the time,
 * which costs battery. Set it to e.g. 0.25 to try it.
 */
private const val LANTERN_IDLE_SWAY = 0f
/** How brightly the lanterns glow (the page's "Glow" slider for this idea). */
private const val LANTERN_GLOW = 1f
private const val LANTERN_TO_DEG = 57.29578f

private val LANTERN_GOLD = Color(0xFFF3D79C)
private val LANTERN_LIGHT = Color(0xFFFFF6DC)
private val LANTERN_CAP = Color(0xFF8E2F27)
private val LANTERN_TASSEL = Color(0xD9C0392F)
private val LANTERN_INK = Color(0xFF4A1F08)
private val LANTERN_SHADOW = Color(0x4D000000)
private val LANTERN_LIT_OUTLINE = Color(0x59783210)
private val LANTERN_DARK_OUTLINE = Color(0x14FFFFFF)

/** The rail's moving parts. Plain fields read only while drawing, so nothing recomposes per frame. */
private class LanternState {
    var awake by mutableStateOf(LANTERN_IDLE_SWAY > 0f)
    val tick = mutableIntStateOf(0)

    // Where the rail sits on the screen (for "past the middle" and the screen width).
    var railTop = 0f
    var railLeft = 0f
    var screenW = 0f
    var height = 1f

    var touching = false
    var sliding = false
    var active = -1
    /** The last letter picked, so the bubble keeps showing it while it fades out. */
    var shown = 0
    var fingerY = 0f
    /** The thumb's speed up and down, in px per second (smoothed). */
    var vy = 0f
    private var sinceMove = 0f
    var act = 0f
    /** Which letter position the thumb is at (fractional, 0 = ☆). */
    var f = 0f
    private var t = 0f
    /** Each lantern's swing angle (radians, + = its bottom swings left) and how fast it is swinging. */
    val ang = FloatArray(LANTERN_COUNT)
    private val angV = FloatArray(LANTERN_COUNT)
    var paint: LanternPaint? = null

    fun moved(newVy: Float) { vy = vy * 0.55f + newVy * 0.45f; sinceMove = 0f }

    /** One frame forward. Returns true while something still moves (the loop sleeps when it returns false). */
    fun step(dt: Float): Boolean {
        t += dt
        val slot = height / LANTERN_COUNT
        val u = slot / LANTERN_PAGE_SLOT
        act = lanternDamp(act, if (touching && !sliding) 1f else 0f, 14f, dt)
        if (touching) f = fingerY / slot - 0.5f
        // A thumb that stops moving sends no more touch events, so its speed fades out here instead.
        sinceMove += dt
        if (sinceMove > 0.04f) vy *= exp(-dt * 7f)
        val vyPage = vy / u
        // The swing is a "damped spring" per lantern: pulled back to its resting angle, slowed by friction, and
        // kicked by the thumb's speed. Small time steps (at most 20 ms) keep it steady when a frame comes late.
        val steps = max(1, ceil(dt / 0.02f).toInt())
        val sub = dt / steps
        var moving = false
        for (i in 0 until LANTERN_COUNT) {
            val d = abs(i - f)
            val bw = lanternBell(d, 3f)
            val target = act * bw * LANTERN_LEAN * 0.6f +
                (if (LANTERN_IDLE_SWAY > 0f) sin(t * 1.4f + i * 0.55f) * LANTERN_IDLE_SWAY * 0.07f else 0f)
            val kick = if (touching) vyPage * 0.012f * bw * act else 0f
            var a = ang[i]
            var v = angV[i]
            for (k in 0 until steps) {
                val acc = -70f * (a - target) - 3.4f * v + kick
                v += acc * sub
                a += v * sub
            }
            a = a.coerceIn(-1.1f, 1.1f)
            ang[i] = a
            angV[i] = v
            if (abs(a) > 0.004f || abs(v) > 0.03f) moving = true
        }
        return touching || act > 0.004f || moving || LANTERN_IDLE_SWAY > 0f
    }
}

/** Brushes, strokes and the measured letters, made once for a size (not per frame). */
private class LanternPaint(val u: Float, val s: Float, val h: Float, val font: FontFamily, val letters: List<TextLayoutResult>) {
    // Each lantern is drawn around its hook at (0, 0): body from y = 3u to 22u, 20u wide.
    val glow = Brush.radialGradient(
        0f to Color(0xE6FFC46E), 0.5f to Color(0x40FFA046), 0.72f to Color(0x00FFA046),
        center = Offset(0f, 16f * u), radius = 26f * u,
    )
    val lit = Brush.verticalGradient(listOf(Color(0xFFFFD58F), Color(0xFFF0953B)), startY = 3f * u, endY = 22f * u)
    val dark = Brush.verticalGradient(listOf(Color(0xFF3B3560), Color(0xFF241F45)), startY = 3f * u, endY = 22f * u)
    val on = Brush.verticalGradient(listOf(Color(0xFFFFF1CE), Color(0xFFFFC470)), startY = 3f * u, endY = 22f * u)
    val rope = Brush.verticalGradient(
        0f to Color.Transparent, 0.06f to LANTERN_GOLD.copy(alpha = 0.65f),
        0.94f to LANTERN_GOLD.copy(alpha = 0.65f), 1f to Color.Transparent,
        startY = -16f * u, endY = h + 12f * u,
    )
    val hair = Stroke(width = 1f * u)
    val ring = Stroke(width = 1.4f * u)
    val bubbleRing = Stroke(width = 2f * s)
    val corner = CornerRadius(9f * u, 8f * u)
}

/**
 * The lantern rail, a drop-in for LetterRail (the same parameters, the same touch behaviour).
 * Press or drag on it to pick a letter; drag past the middle of the screen to slide onto the list; hold • for the vault.
 */
@Composable
fun LanternRail(
    present: Set<String>,
    modifier: Modifier = Modifier,
    /** While the thumb slides left off the rail onto the list: its screen y. null when it's back on the rail. */
    onSlide: (Float?) -> Unit = {},
    /** Finger lifted; true if it was sliding on the list (so home can open the app under it). */
    onRelease: (Boolean) -> Unit = {},
    /** Holding • (the bottom of the rail) for a moment: opens the vault. */
    onHoldDot: () -> Unit = {},
    onPick: (String) -> Unit,
) {
    val look = LocalLauncherLook.current
    val haptics = LocalHapticFeedback.current
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnHoldDot by rememberUpdatedState(onHoldDot)
    val currentOnSlide by rememberUpdatedState(onSlide)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val st = remember { LanternState() }
    val measurer = rememberTextMeasurer()
    val bubbleStyle = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White)
    val bubbles = remember(look.font) { RAIL_ITEMS.map { measurer.measure(it, bubbleStyle) } }
    // Which lanterns are lit: ☆ and • always, letters only if some app starts with them.
    val has = remember(present) { BooleanArray(LANTERN_COUNT) { RAIL_ITEMS[it] == STAR || RAIL_ITEMS[it] == DOT || RAIL_ITEMS[it] in present } }
    val railColour = if (look.rail != Color.Unspecified) look.rail else look.text

    // The frame loop: only while something moves; it wakes on a touch and sleeps when everything has settled.
    LaunchedEffect(st.awake) {
        if (!st.awake) return@LaunchedEffect
        var last = -1L
        while (true) {
            val keep = withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min(0.05f, (now - last) / 1_000_000_000f)
                last = now
                st.step(dt).also { st.tick.intValue++ }
            }
            if (!keep) { st.awake = false; break }
        }
    }

    Spacer(
        modifier
            // Tell Android not to start its back-swipe here (phones with gesture navigation).
            .systemGestureExclusion()
            .onGloballyPositioned {
                val p = it.positionInRoot()
                st.railTop = p.y; st.railLeft = p.x
                st.screenW = p.x + it.size.width
                st.height = it.size.height.toFloat()
            }
            .pointerInput(Unit) {
                fun follow(x: Float, y: Float) {
                    st.fingerY = y.coerceIn(-size.height * 0.6f, size.height * 1.6f)
                    // Past the middle of the screen = sliding onto the list (the rail sits at the screen's right edge).
                    val middle = (st.railLeft + size.width) / 2 - st.railLeft
                    if (x < middle) {
                        if (!st.sliding) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        st.sliding = true
                        currentOnSlide(st.railTop + y)
                        return
                    }
                    if (st.sliding) {
                        st.sliding = false
                        currentOnSlide(null)
                    }
                    val i = (st.fingerY / size.height * LANTERN_COUNT).toInt().coerceIn(0, LANTERN_COUNT - 1)
                    if (i != st.active) {
                        st.active = i
                        st.shown = i
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnPick(RAIL_ITEMS[i])
                    }
                }
                val dot = LANTERN_COUNT - 1
                // One "gesture" = finger down, any dragging, finger up.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    st.touching = true
                    st.vy = 0f
                    st.awake = true
                    follow(down.position.x, down.position.y)
                    var released = false
                    try {
                        // Holding still on • for 600 ms = "open the vault" (a buzz, once per touch).
                        var dotSince = if (st.active == dot) SystemClock.uptimeMillis() else -1L
                        var held = false
                        while (true) {
                            val waiting = st.active == dot && !held && !st.sliding && dotSince >= 0
                            val event = if (waiting) {
                                val left = (LANTERN_HOLD_MS - (SystemClock.uptimeMillis() - dotSince)).coerceAtLeast(1L)
                                withTimeoutOrNull(left) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                held = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnHoldDot()
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            // The thumb's speed up and down: it kicks the lanterns into a swing.
                            val ms = change.uptimeMillis - change.previousUptimeMillis
                            if (ms >= 2) st.moved((change.position.y - change.previousPosition.y) / (ms / 1000f))
                            val before = st.active
                            follow(change.position.x, change.position.y)
                            if (st.active == dot && before != dot) dotSince = SystemClock.uptimeMillis()
                            change.consume()
                        }
                        currentOnRelease(st.sliding)
                        released = true
                    } finally {
                        // Also runs if the touch was cancelled, so the rail never stays "held".
                        if (!released) currentOnRelease(false)
                        if (st.sliding) currentOnSlide(null)
                        st.sliding = false
                        st.touching = false
                        st.active = -1
                        st.awake = true
                    }
                }
            }
            // Its own layer: redrawing the lanterns never redraws the home behind them.
            .graphicsLayer()
            .drawBehind {
                st.tick.intValue   // redraw on every frame of the loop
                val slot = size.height / LANTERN_COUNT
                val u = slot / LANTERN_PAGE_SLOT
                val s = if (st.screenW > 0f) st.screenW / 390f else u
                val p = st.paint?.takeIf { it.u == u && it.s == s && it.h == size.height && it.font == look.font }
                    ?: LanternPaint(
                        u, s, size.height, look.font,
                        RAIL_ITEMS.map {
                            measurer.measure(it, TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = (13f * u).toSp()))
                        },
                    ).also { st.paint = it }
                drawLanterns(st, p, has, look.accent, railColour, look.panel, bubbles)
            }
    )
}

private fun DrawScope.drawLanterns(
    st: LanternState, p: LanternPaint, has: BooleanArray, accent: Color, railColour: Color, panel: Color,
    bubbles: List<TextLayoutResult>,
) {
    val u = p.u
    val s = p.s
    val slot = size.height / LANTERN_COUNT
    val x0 = LANTERN_COLUMN.toPx() / 2
    val act = st.act
    val f = st.f
    // The string the lanterns hang from.
    drawLine(p.rope, Offset(x0, -16f * u), Offset(x0, size.height + 12f * u), strokeWidth = 2f * u)
    // Two passes: all the warm glows first, then the lanterns on top (so a glow never covers a neighbour).
    for (pass in 0..1) {
        for (i in 0 until LANTERN_COUNT) {
            val d = abs(i - f)
            val wide = lanternBell(d, 3f)
            val near = lanternBell(d, 1.05f)
            val hookX = x0 - act * (near * 26f + wide * 5f) * s
            val hookY = i * slot + 2f * u
            val grow = 1f + 0.95f * act * near
            if (pass == 0) {
                val glow = ((if (has[i]) 0.35f else 0f) * LANTERN_GLOW + near * act * 0.75f * LANTERN_GLOW).coerceAtMost(1f)
                if (glow < 0.01f) continue
                withTransform({
                    translate(hookX, hookY); rotate(st.ang[i] * LANTERN_TO_DEG, Offset.Zero); scale(grow, grow, Offset.Zero)
                }) {
                    drawCircle(p.glow, radius = 26f * u, center = Offset(0f, 16f * u), alpha = glow)
                }
                continue
            }
            val on = i == st.active
            withTransform({
                translate(hookX, hookY); rotate(st.ang[i] * LANTERN_TO_DEG, Offset.Zero); scale(grow, grow, Offset.Zero)
            }) {
                val bodyTop = Offset(-10f * u, 3f * u)
                val bodySize = Size(20f * u, 19f * u)
                // The little hook string, a soft shadow, the paper body and its thin outline.
                drawLine(LANTERN_GOLD.copy(alpha = 0.7f), Offset.Zero, Offset(0f, 1.5f * u), strokeWidth = 1f * u)
                drawRoundRect(LANTERN_SHADOW, topLeft = Offset(-10f * u, 5f * u), size = bodySize, cornerRadius = p.corner)
                drawRoundRect(
                    brush = if (on) p.on else if (has[i]) p.lit else p.dark,
                    topLeft = bodyTop, size = bodySize, cornerRadius = p.corner,
                )
                drawRoundRect(if (has[i] || on) LANTERN_LIT_OUTLINE else LANTERN_DARK_OUTLINE, topLeft = bodyTop, size = bodySize, cornerRadius = p.corner, style = p.hair)
                // The dark cap and base, and the tassel hanging below.
                drawRect(LANTERN_CAP, topLeft = Offset(-6f * u, 1f * u), size = Size(12f * u, 3f * u))
                drawRect(LANTERN_CAP, topLeft = Offset(-6f * u, 20f * u), size = Size(12f * u, 3f * u))
                drawRect(LANTERN_TASSEL, topLeft = Offset(-0.75f * u, 22.5f * u), size = Size(1.5f * u, 1.5f * u))
                drawRect(LANTERN_TASSEL, topLeft = Offset(-1.2f * u, 23.6f * u), size = Size(2.4f * u, 2f * u))
                // The theme's accent marks the lantern you're on.
                if (on) drawRoundRect(accent, topLeft = bodyTop, size = bodySize, cornerRadius = p.corner, style = p.ring)
                val tl = p.letters[i]
                val ink = if (has[i] || on) LANTERN_INK else railColour.copy(alpha = 0.45f)
                drawText(tl, color = ink, topLeft = Offset(-tl.size.width / 2f, 12.5f * u - tl.size.height / 2f))
            }
        }
    }
    // The letter bubble, out to the left of the lanterns.
    if (act > 0.01f) {
        val q = Offset(x0 - 98f * s, (f.coerceIn(0f, LANTERN_COUNT - 1f) + 0.5f) * slot)
        val r = 30f * s * (0.8f + 0.2f * act)
        drawCircle(panel.copy(alpha = 0.95f * act), radius = r, center = q)
        drawCircle(LANTERN_GOLD.copy(alpha = act), radius = r, center = q, style = p.bubbleRing)
        val tl = bubbles[st.shown.coerceIn(0, LANTERN_COUNT - 1)]
        drawText(tl, color = LANTERN_LIGHT, topLeft = Offset(q.x - tl.size.width / 2f, q.y - tl.size.height / 2f), alpha = act)
    }
}

private fun lanternDamp(c: Float, target: Float, k: Float, dt: Float) = c + (target - c) * (1f - exp(-k * dt))
private fun lanternBell(d: Float, sigma: Float) = exp(-(d * d) / (2 * sigma * sigma))
