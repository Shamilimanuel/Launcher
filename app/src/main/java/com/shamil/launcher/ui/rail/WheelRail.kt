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
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.DOT
import com.shamil.launcher.ui.STAR
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * "Element wheel" (his pick #8, 24 Oct). Hold the right edge and a dial of letters opens around your thumb; turn
 * your thumb around it to choose (like turning a dial, not sliding along a strip). Inside sits a ring of the seven
 * element gems: decorative for now, they're where categories will live later (letting go on one does nothing yet).
 * The first letter is the one where you pressed, so a quick tap on the edge still jumps to that letter.
 * There's no "slide onto the list" here (your thumb is busy turning), so it never calls onSlide.
 * Hold the dial on • for 600 ms for the vault. Look, motion and numbers are the playground's mkWheel().
 */

private val WHEEL_COUNT = RAIL_ITEMS.size
private const val WHEEL_DOT_HOLD_MS = 600L
/** How long to hold the edge before the dial opens (the playground's 160 ms)... */
private const val WHEEL_OPEN_MS = 160L
/** ...or it opens straight away once the thumb has moved this far. */
private val WHEEL_OPEN_MOVE = 16.dp
/** The dial's size, in playground units (screen width / 390). */
private const val WHEEL_RADIUS = 118f
/** How many letters one turn of your thumb moves (the page's "Turn speed": 2.4 = a letter per ~5°). */
private const val WHEEL_GAIN = 2.4f
/** How far apart the letters sit around the dial (radians). */
private const val WHEEL_STEP = 0.19f
private const val WHEEL_GLOW = 0.85f
private const val WHEEL_TWO_PI = 6.2831855f
private const val WHEEL_RAD_TO_DEG = 57.29578f

private val WHEEL_GOLD = Color(0xFFF3D79C)
private val WHEEL_LIGHT = Color(0xFFFFF6DC)
private val WHEEL_CAPTION = Color(0xFFFFF1CE)

/** The seven element gems, in the playground's order. */
private val WHEEL_GEM_NAMES = listOf("Pyro", "Hydro", "Anemo", "Electro", "Dendro", "Cryo", "Geo")
private val WHEEL_GEM_COLOURS = listOf(
    Color(0xFFEF7938), Color(0xFF4CC2F1), Color(0xFF74C2A8), Color(0xFFB47FDC),
    Color(0xFFA5C83B), Color(0xFF9FD6E3), Color(0xFFF8C84A),
)

/** The dial's moving parts. Plain fields read only while drawing, so nothing recomposes per frame. */
private class WheelState {
    var awake by mutableStateOf(false)
    val tick = mutableIntStateOf(0)

    var railLeft = 0f
    var screenW = 0f
    var height = 1f

    var touching = false
    /** The dial is open (turning picks letters), and still visible (it fades out after you let go). */
    var open = false
    var visible = false
    var openA = 0f
    var t = 0f
    /** When (in [t]) the finger went down, for the little charging ring before the dial opens. */
    var downT = 0f
    var startX = 0f; var startY = 0f
    var fingerX = 0f; var fingerY = 0f
    var active = -1
    var shown = 0

    // The dial (all in px, in the rail's own coordinates; the dial is drawn well outside the 46dp strip).
    var radius = 1f
    var cx = 0f; var cy = 0f
    /** The thumb's angle last time, and how far it has turned in total since the dial opened. */
    private var lastAngle = 0f
    private var turned = 0f
    /** The angle the dial is drawn at (it follows the thumb on a quick spring). */
    var angle = 0f
    private var f0 = 0f
    /** The picked letter position, and its smoothed copy for drawing. */
    var f = 0f
    var fs = 0f
    /** The gem under the thumb (inside the dial), or -1. */
    var gem = -1
    val gemGrow = FloatArray(7) { 1f }
    var paint: WheelPaint? = null

    fun pageUnit() = if (screenW > 0f) screenW / 390f else 3f

    /** The letter straight from the strip, before the dial opens. */
    fun stripIndex(y: Float) = (y / height * WHEEL_COUNT).toInt().coerceIn(0, WHEEL_COUNT - 1)

    /** Opens the dial: its centre sits one radius left of where you pressed, kept on the screen. */
    fun openDial() {
        val s = pageUnit()
        radius = WHEEL_RADIUS * s
        cx = wheelClamp(railLeft + startX - radius, radius + 12f * s, 330f * s) - railLeft
        cy = wheelClamp(startY, radius - 10f * s, height - radius + 10f * s)
        lastAngle = atan2(fingerY - cy, fingerX - cx)
        angle = lastAngle
        turned = 0f
        f0 = stripIndex(fingerY).toFloat()
        f = f0; fs = f0
        gem = -1
        open = true
        visible = true
    }

    /** The thumb moved while the dial is open: turning round the ring picks letters, the middle points at a gem. */
    fun turn() {
        val dx = fingerX - cx
        val dy = fingerY - cy
        if (hypot(dx, dy) > radius - 62f * pageUnit()) {
            var d = atan2(dy, dx) - lastAngle
            if (d > WHEEL_TWO_PI / 2) d -= WHEEL_TWO_PI
            if (d < -WHEEL_TWO_PI / 2) d += WHEEL_TWO_PI
            turned += d
            lastAngle += d
            gem = -1
            f = (f0 + turned * WHEEL_GAIN / (WHEEL_TWO_PI / WHEEL_COUNT)).coerceIn(0f, WHEEL_COUNT - 1f)
        } else {
            val a = atan2(dy, dx)
            var best = 0
            var bestD = 9f
            for (k in 0 until 7) {
                val ga = k * WHEEL_TWO_PI / 7 + fs * 0.15f
                val diff = abs(atan2(sin(a - ga), cos(a - ga)))
                if (diff < bestD) { bestD = diff; best = k }
            }
            gem = best
        }
    }

    fun step(dt: Float): Boolean {
        t += dt
        openA = wheelDamp(openA, if (open) 1f else 0f, if (open) 22f else 16f, dt)
        if (visible && !open && openA < 0.01f) visible = false
        if (open) {
            // The dial's highlight follows the thumb's angle on a quick spring.
            var d = atan2(fingerY - cy, fingerX - cx) - angle
            d = atan2(sin(d), cos(d))
            angle += d * (1f - exp(-dt * 30f))
            fs = wheelDamp(fs, f, 26f, dt)
        }
        for (k in 0 until 7) gemGrow[k] = wheelDamp(gemGrow[k], if (gem == k) 1.7f else 1f, 18f, dt)
        return touching || visible
    }
}

/** Brushes, strokes and the caption texts for a size, made once (not per frame). */
private class WheelPaint(val s: Float, val h: Float, val w: Float, val captions: List<TextLayoutResult>) {
    val halo = Brush.radialGradient(listOf(WHEEL_GOLD.copy(alpha = 0.7f), WHEEL_GOLD.copy(alpha = 0.2f), Color.Transparent), center = Offset.Zero, radius = 18f * s)
    // A gem: white highlight up-left, its colour, then a darker rim; and a soft glow of its colour around it.
    val gems = WHEEL_GEM_COLOURS.map { c ->
        Brush.radialGradient(
            0f to Color.White, 0.5f to c, 1f to Color(c.red * 0.6f, c.green * 0.6f, c.blue * 0.6f),
            center = Offset(-2.7f * s, -3.6f * s), radius = 17f * s,
        )
    }
    val gemGlows = WHEEL_GEM_COLOURS.map { c -> Brush.radialGradient(listOf(c.copy(alpha = 0.8f), Color.Transparent), center = Offset.Zero, radius = 21f * s) }
    // The edge handle at rest: a faint gold bar that says "hold here".
    val handleTop = h / 2 - 60f * s
    val handle = Brush.verticalGradient(listOf(Color.Transparent, WHEEL_GOLD.copy(alpha = 0.8f), Color.Transparent), startY = handleTop, endY = handleTop + 120f * s)
    val thin = Stroke(width = 1.2f * s, cap = StrokeCap.Round)
    val wide = Stroke(width = 16f * s, cap = StrokeCap.Round)
    val bright = Stroke(width = 2.4f * s, cap = StrokeCap.Round)
    val charge = Stroke(width = 2f * s)
    val chargeGlow = Stroke(width = 8f * s)
    val bubbleRing = Stroke(width = 2f * s)
}

/**
 * The element-wheel rail, a drop-in for LetterRail (the same parameters). Hold the edge and a dial opens; turn to pick.
 * [onSlide] is never called (no sliding onto the list here) and [onRelease] always gets false.
 */
@Composable
fun WheelRail(
    present: Set<String>,
    modifier: Modifier = Modifier,
    /** Not used by the wheel (kept so it drops in where LetterRail goes). */
    onSlide: (Float?) -> Unit = {},
    /** Finger lifted; always false here (the wheel never slides onto the list). */
    onRelease: (Boolean) -> Unit = {},
    /** The dial resting on • for a moment: opens the vault. */
    onHoldDot: () -> Unit = {},
    onPick: (String) -> Unit,
) {
    val look = LocalLauncherLook.current
    val haptics = LocalHapticFeedback.current
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnHoldDot by rememberUpdatedState(onHoldDot)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val st = remember { WheelState() }
    val measurer = rememberTextMeasurer()
    val letterStyle = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, shadow = look.shadow)
    val bubbleStyle = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White)
    val captionStyle = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color.White, shadow = look.shadow)
    val letters = remember(look.font, look.textShadow) { RAIL_ITEMS.map { measurer.measure(it, letterStyle) } }
    val bubbles = remember(look.font) { RAIL_ITEMS.map { measurer.measure(it, bubbleStyle) } }
    // "turn to choose", then one caption per gem: categories come later.
    val captions = remember(look.font, look.textShadow) {
        (listOf("turn to choose") + WHEEL_GEM_NAMES.map { "$it · categories later" }).map { measurer.measure(it, captionStyle) }
    }
    val has = remember(present) { BooleanArray(WHEEL_COUNT) { RAIL_ITEMS[it] == STAR || RAIL_ITEMS[it] == DOT || RAIL_ITEMS[it] in present } }
    val railColour = if (look.rail != Color.Unspecified) look.rail else look.text

    // The frame loop: only while something moves; it wakes on a touch and sleeps when the dial has faded out.
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
                st.railLeft = p.x
                st.screenW = p.x + it.size.width
                st.height = it.size.height.toFloat()
            }
            .pointerInput(Unit) {
                fun pick(i: Int) {
                    if (i == st.active) return
                    st.active = i
                    st.shown = i
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnPick(RAIL_ITEMS[i])
                }
                val dot = WHEEL_COUNT - 1
                val openMove = WHEEL_OPEN_MOVE.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val downAt = SystemClock.uptimeMillis()
                    st.touching = true
                    st.open = false
                    st.openA = 0f
                    st.visible = false
                    st.downT = st.t
                    st.startX = down.position.x; st.startY = down.position.y
                    st.fingerX = st.startX; st.fingerY = st.startY
                    st.awake = true
                    // The first letter is the one where you pressed (a tap jumps there, like the other rails).
                    pick(st.stripIndex(st.fingerY))
                    fun openNow() {
                        st.openDial()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        pick(st.f.roundToInt())
                    }
                    try {
                        var dotSince = if (st.active == dot) downAt else -1L
                        var held = false
                        while (true) {
                            // Two timers can be running: "open the dial" (160 ms) and "vault" (600 ms on •).
                            val now = SystemClock.uptimeMillis()
                            var wait = Long.MAX_VALUE
                            if (!st.open) wait = WHEEL_OPEN_MS - (now - downAt)
                            if (st.active == dot && !held && dotSince >= 0) wait = min(wait, WHEEL_DOT_HOLD_MS - (now - dotSince))
                            val event = if (wait == Long.MAX_VALUE) awaitPointerEvent()
                            else withTimeoutOrNull(wait.coerceAtLeast(1L)) { awaitPointerEvent() }
                            if (event == null) {
                                val at = SystemClock.uptimeMillis()
                                if (!st.open && at - downAt >= WHEEL_OPEN_MS) {
                                    openNow()
                                    if (st.active == dot && dotSince < 0) dotSince = at
                                }
                                if (st.active == dot && !held && dotSince >= 0 && at - dotSince >= WHEEL_DOT_HOLD_MS) {
                                    held = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnHoldDot()
                                }
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            st.fingerX = change.position.x
                            st.fingerY = change.position.y
                            val before = st.active
                            if (!st.open && hypot(st.fingerX - st.startX, st.fingerY - st.startY) >= openMove) openNow()
                            if (st.open) {
                                st.turn()
                                pick(st.f.roundToInt())
                            } else {
                                pick(st.stripIndex(st.fingerY))
                            }
                            if (st.active != dot) dotSince = -1L
                            else if (before != dot) dotSince = SystemClock.uptimeMillis()
                        }
                    } finally {
                        // Letting go on a gem does nothing yet (categories come later). Never "sliding" here.
                        currentOnRelease(false)
                        st.touching = false
                        st.open = false
                        st.active = -1
                        st.awake = true
                    }
                }
            }
            // Its own layer: redrawing the dial never redraws the home behind it.
            .graphicsLayer()
            .drawBehind {
                st.tick.intValue   // redraw on every frame of the loop
                val s = st.pageUnit()
                val p = st.paint?.takeIf { it.s == s && it.h == size.height && it.w == size.width && it.captions === captions }
                    ?: WheelPaint(s, size.height, size.width, captions).also { st.paint = it }
                drawWheel(st, p, has, look.accent, railColour, look.panel, letters, bubbles)
            }
    )
}

private fun DrawScope.drawWheel(
    st: WheelState, p: WheelPaint, has: BooleanArray, accent: Color, railColour: Color, panel: Color,
    letters: List<TextLayoutResult>, bubbles: List<TextLayoutResult>,
) {
    val s = p.s
    val openA = st.openA
    // At rest: just the faint handle on the edge (it fades while the dial is open).
    val handleA = 0.55f * (1f - openA)
    if (handleA > 0.01f) {
        drawRoundRect(
            brush = p.handle, topLeft = Offset(size.width - 15f * s, p.handleTop), size = Size(10f * s, 120f * s),
            cornerRadius = CornerRadius(5f * s, 5f * s), alpha = handleA,
        )
    }
    // Before it opens: a ring under the thumb that grows as the hold "charges".
    if (st.touching && !st.open) {
        val prog = ((st.t - st.downT) / (WHEEL_OPEN_MS / 1000f)).coerceIn(0f, 1f)
        val r = 26f * s * (0.5f + 0.7f * prog)
        val a = 0.4f + 0.6f * prog
        val c = Offset(st.startX, st.startY)
        drawCircle(WHEEL_GOLD.copy(alpha = 0.25f * a), radius = r, center = c, style = p.chargeGlow)
        drawCircle(WHEEL_GOLD.copy(alpha = a), radius = r, center = c, style = p.charge)
    }
    if (!st.visible) return
    val c = Offset(st.cx, st.cy)
    val rr = st.radius * (0.55f + 0.45f * openA)
    val ang = st.angle
    // The rings: an outer gold line, a fainter inner one for the gems, and the highlight where your thumb is.
    drawCircle(WHEEL_GOLD.copy(alpha = 0.5f * openA), radius = rr + 18f * s, center = c, style = p.thin)
    drawCircle(WHEEL_GOLD.copy(alpha = 0.2f * openA), radius = st.radius - 46f * s, center = c, style = p.thin)
    drawWheelArc(WHEEL_GOLD.copy(alpha = 0.22f * openA), c, rr + 2f * s, ang - 0.2f, 0.4f, p.wide)
    drawWheelArc(WHEEL_LIGHT.copy(alpha = 0.9f * openA), c, rr + 18f * s, ang - 0.12f, 0.24f, p.bright)
    // The letters round the dial: the picked one sits at your thumb, its neighbours spread out and fade away.
    if (openA > 0.02f) {
        for (j in 0 until WHEEL_COUNT) {
            val rel = (j - st.fs) * WHEEL_STEP
            val ar = abs(rel)
            if (ar > 2.5f) continue
            val ps = ang + rel
            val q = wheelBell(ar / WHEEL_STEP, 1.15f)
            val x = st.cx + rr * cos(ps)
            val y = st.cy + rr * sin(ps)
            val grow = 0.78f + 1.15f * q
            val alpha = (openA * (1f - (ar / 2.5f) * (ar / 2.5f)) * (if (has[j]) 1f else 0.45f)).coerceIn(0f, 1f)
            val glow = (q * WHEEL_GLOW * openA).coerceIn(0f, 1f)
            val tl = letters[j]
            withTransform({ translate(x, y); scale(grow, grow, Offset.Zero) }) {
                if (glow > 0.01f) drawCircle(p.halo, radius = 18f * s, center = Offset.Zero, alpha = glow)
                railText(
                    tl, color = if (j == st.active) accent else railColour,
                    topLeft = Offset(-tl.size.width / 2f, -tl.size.height / 2f), alpha = alpha,
                )
            }
        }
    }
    // The seven element gems on the inner ring (they turn slowly with the dial).
    val gr = (st.radius - 46f * s) * (0.4f + 0.6f * openA)
    for (k in 0 until 7) {
        val a = k * WHEEL_TWO_PI / 7 + st.fs * 0.15f
        val g = st.gemGrow[k]
        withTransform({ translate(st.cx + gr * cos(a), st.cy + gr * sin(a)); scale(g, g, Offset.Zero) }) {
            drawCircle(p.gemGlows[k], radius = 21f * s, center = Offset.Zero, alpha = 0.6f * openA)
            drawCircle(p.gems[k], radius = 9f * s, center = Offset.Zero, alpha = 0.9f * openA)
        }
    }
    // The bubble in the middle shows the picked letter, big.
    val br = 30f * s * 1.5f
    drawCircle(panel.copy(alpha = 0.95f * openA), radius = br, center = c)
    drawCircle(WHEEL_GOLD.copy(alpha = openA), radius = br, center = c, style = p.bubbleRing)
    val tl = bubbles[st.shown.coerceIn(0, WHEEL_COUNT - 1)]
    withTransform({ scale(1.5f, 1.5f, c) }) {
        railText(tl, color = WHEEL_LIGHT, topLeft = Offset(st.cx - tl.size.width / 2f, st.cy - tl.size.height / 2f), alpha = openA)
    }
    // The caption under the dial.
    val cap = p.captions[if (st.gem >= 0) st.gem + 1 else 0]
    railText(
        cap, color = WHEEL_CAPTION,
        topLeft = Offset(st.cx - cap.size.width / 2f, st.cy + st.radius + 34f * s - cap.size.height / 2f), alpha = 0.9f * openA,
    )
}

/** A piece of a circle round [c], from angle [from] (radians, 0 = right, clockwise) for [sweep] radians. */
private fun DrawScope.drawWheelArc(colour: Color, c: Offset, r: Float, from: Float, sweep: Float, stroke: Stroke) {
    drawArc(
        colour, startAngle = from * WHEEL_RAD_TO_DEG, sweepAngle = sweep * WHEEL_RAD_TO_DEG, useCenter = false,
        topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = stroke,
    )
}

/** coerceIn that never crashes when the range is upside down (a very short rail): then it takes the middle. */
private fun wheelClamp(v: Float, lo: Float, hi: Float) = if (lo <= hi) v.coerceIn(lo, hi) else (lo + hi) / 2

private fun wheelDamp(c: Float, target: Float, k: Float, dt: Float) = c + (target - c) * (1f - exp(-k * dt))
private fun wheelBell(d: Float, sigma: Float) = exp(-(d * d) / (2 * sigma * sigma))
