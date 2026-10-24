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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * "Wind glider" (his pick #7, 24 Oct): like gliding in Genshin. The letter under your thumb lifts and glows, its
 * neighbours flow out to the left on a wind current that lags behind your thumb (each letter follows on its own
 * soft spring, tilted by how fast you move), and thin anemo-teal light streaks stream away to the left, more of them
 * the faster you go. Touch works exactly like the Niagara-style LetterRail: drag past the middle of the screen to
 * slide onto the list, hold • for the vault. Look, motion and numbers are the playground's mkGlider().
 */

private val GLIDER_COUNT = RAIL_ITEMS.size
private const val GLIDER_HOLD_MS = 600L
/** Width of the letter column inside the 46dp touch strip (the same as LetterRail's). */
private val GLIDER_COLUMN = 28.dp
/** The playground's room per letter (676 / 29); vertical sizes are measured in it. */
private const val GLIDER_PAGE_SLOT = 676f / 29f
/** How far the wind carries the letters out to the left (playground units, the page's "Reach" slider). */
private const val GLIDER_REACH = 56f
/** How many wind streaks (1 = the page's default). */
private const val GLIDER_STREAKS = 1f
/** How brightly the lifted letters glow. */
private const val GLIDER_GLOW = 0.8f
/** At most this many streaks at once (a fixed "pool", so nothing new is created while you drag). */
private const val GLIDER_STREAK_POOL = 44
private const val GLIDER_TO_DEG = 57.29578f

private val GLIDER_GOLD = Color(0xFFF3D79C)
private val GLIDER_LIGHT = Color(0xFFFFF6DC)
/** A streak goes from bright mint at its head to anemo teal at its tail, fading out as it goes. */
private val GLIDER_STREAK_HEAD = Color(0xFFBEFFEB)
private val GLIDER_STREAK_TAIL = Color(0xFF74C2A8)
/** The streak is drawn as 4 short pieces, each a step further from head to tail (colours worked out once). */
private val GLIDER_STREAK_COLOURS = Array(4) { lerp(GLIDER_STREAK_HEAD, GLIDER_STREAK_TAIL, (it + 0.5f) / 4f) }

/** The rail's moving parts. Plain fields read only while drawing, so nothing recomposes per frame. */
private class GliderState {
    var awake by mutableStateOf(false)
    val tick = mutableIntStateOf(0)

    var railTop = 0f
    var railLeft = 0f
    var screenW = 0f
    var height = 1f
    /** Where the letter column's middle is, in px from the rail's left edge (where new streaks start). */
    var columnX = 0f

    var touching = false
    var sliding = false
    var active = -1
    var shown = 0
    var fingerY = 0f
    var vy = 0f
    private var sinceMove = 0f
    var act = 0f
    var f = 0f
    var t = 0f
    /** Each letter's sideways offset (px) and tilt (radians): they chase their targets with a lag. */
    val gx = FloatArray(GLIDER_COUNT)
    val tilt = FloatArray(GLIDER_COUNT)
    // The streak pool: position, length, speed, birth time, life, bend.
    val sx = FloatArray(GLIDER_STREAK_POOL); val sy = FloatArray(GLIDER_STREAK_POOL); val sLen = FloatArray(GLIDER_STREAK_POOL)
    private val sV = FloatArray(GLIDER_STREAK_POOL)
    val sT = FloatArray(GLIDER_STREAK_POOL); val sLife = FloatArray(GLIDER_STREAK_POOL); val sBend = FloatArray(GLIDER_STREAK_POOL)
    val sOn = BooleanArray(GLIDER_STREAK_POOL)
    private var spawn = 0f
    var paint: GliderPaint? = null

    fun moved(newVy: Float) { vy = vy * 0.55f + newVy * 0.45f; sinceMove = 0f }

    fun step(dt: Float): Boolean {
        t += dt
        val slot = height / GLIDER_COUNT
        val u = slot / GLIDER_PAGE_SLOT
        val s = if (screenW > 0f) screenW / 390f else u
        val live = touching && !sliding
        act = gliderDamp(act, if (live) 1f else 0f, 14f, dt)
        if (touching) f = fingerY / slot - 0.5f
        sinceMove += dt
        if (sinceMove > 0.04f) vy *= exp(-dt * 7f)
        val vyPage = vy / u
        val speed = (abs(vyPage) / 700f).coerceIn(0f, 1f)
        var moving = false
        for (i in 0 until GLIDER_COUNT) {
            val d = abs(i - f)
            val b = gliderBell(d, 3.3f)
            // Out to the left on the wind, with a little flutter that grows with speed.
            val tx = (-GLIDER_REACH * b * act - sin(t * 9f - d * 0.9f) * 5f * b * act * (0.3f + speed)) * s
            // Letters far from the thumb follow more slowly: that lag is the "wind current".
            gx[i] = gliderDamp(gx[i], tx, 26f * (0.3f + 0.7f * b), dt)
            val tr = (vyPage / 900f).coerceIn(-1f, 1f) * 0.55f * b * act
            tilt[i] = gliderDamp(tilt[i], tr, 14f, dt)
            if (abs(gx[i]) > 0.3f || abs(tilt[i]) > 0.002f) moving = true
        }
        // New streaks: about 10 a second when still, up to 80 when fast.
        if (live) {
            spawn += dt * (10f + speed * 70f) * GLIDER_STREAKS
            while (spawn > 1f) {
                spawn -= 1f
                val k = sOn.indexOfFirst { !it }
                if (k < 0) { spawn = 0f; break }
                sOn[k] = true
                sx[k] = columnX - 18f * s
                sy[k] = fingerY + (Random.nextFloat() - 0.5f) * 70f * s
                sLen[k] = (36f + Random.nextFloat() * 80f) * s
                sV[k] = (260f + Random.nextFloat() * 320f) * s
                sT[k] = t
                sLife[k] = 0.5f + Random.nextFloat() * 0.45f
                sBend[k] = (Random.nextFloat() - 0.5f) * 34f * s
            }
        } else {
            spawn = 0f
        }
        var anyStreak = false
        for (k in 0 until GLIDER_STREAK_POOL) if (sOn[k]) {
            if (t - sT[k] >= sLife[k]) sOn[k] = false
            else { sx[k] -= sV[k] * dt; anyStreak = true }
        }
        return touching || act > 0.004f || moving || anyStreak
    }
}

/** Brushes and strokes for a size, made once (not per frame). */
private class GliderPaint(val u: Float, val s: Float) {
    val halo = Brush.radialGradient(listOf(GLIDER_GOLD.copy(alpha = 0.7f), GLIDER_GOLD.copy(alpha = 0.2f), Color.Transparent), center = Offset.Zero, radius = 16f * u)
    val bubbleRing = Stroke(width = 2f * s)
}

/**
 * The wind-glider rail, a drop-in for LetterRail (the same parameters, the same touch behaviour).
 * Press or drag on it to pick a letter; drag past the middle of the screen to slide onto the list; hold • for the vault.
 */
@Composable
fun GliderRail(
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
    val st = remember { GliderState() }
    val measurer = rememberTextMeasurer()
    // The letters carry the theme's text shadow (like LetterRail), so they read on any wallpaper.
    val letterStyle = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, shadow = look.shadow)
    val bubbleStyle = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White)
    val letters = remember(look.font, look.textShadow) { RAIL_ITEMS.map { measurer.measure(it, letterStyle) } }
    val bubbles = remember(look.font) { RAIL_ITEMS.map { measurer.measure(it, bubbleStyle) } }
    val has = remember(present) { BooleanArray(GLIDER_COUNT) { RAIL_ITEMS[it] == STAR || RAIL_ITEMS[it] == DOT || RAIL_ITEMS[it] in present } }
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
                st.columnX = GLIDER_COLUMN.toPx() / 2
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
                    val i = (st.fingerY / size.height * GLIDER_COUNT).toInt().coerceIn(0, GLIDER_COUNT - 1)
                    if (i != st.active) {
                        st.active = i
                        st.shown = i
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnPick(RAIL_ITEMS[i])
                    }
                }
                val dot = GLIDER_COUNT - 1
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
                                val left = (GLIDER_HOLD_MS - (SystemClock.uptimeMillis() - dotSince)).coerceAtLeast(1L)
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
                            // The thumb's speed up and down: it tilts the letters and thickens the wind.
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
            // Its own layer: redrawing the rail never redraws the home behind it.
            .graphicsLayer()
            .drawBehind {
                st.tick.intValue   // redraw on every frame of the loop
                val slot = size.height / GLIDER_COUNT
                val u = slot / GLIDER_PAGE_SLOT
                val s = if (st.screenW > 0f) st.screenW / 390f else u
                st.columnX = GLIDER_COLUMN.toPx() / 2
                val p = st.paint?.takeIf { it.u == u && it.s == s } ?: GliderPaint(u, s).also { st.paint = it }
                drawGlider(st, p, has, look.accent, railColour, look.panel, letters, bubbles)
            }
    )
}

private fun DrawScope.drawGlider(
    st: GliderState, p: GliderPaint, has: BooleanArray, accent: Color, railColour: Color, panel: Color,
    letters: List<TextLayoutResult>, bubbles: List<TextLayoutResult>,
) {
    val u = p.u
    val s = p.s
    val slot = size.height / GLIDER_COUNT
    val x0 = st.columnX
    val act = st.act
    val f = st.f
    val t = st.t
    // The wind streaks, behind the letters: a gentle curve from a bright head (left, leading) to a faded tail.
    val width = 1.6f * s
    for (k in 0 until GLIDER_STREAK_POOL) if (st.sOn[k]) {
        val life = (t - st.sT[k]) / st.sLife[k]
        if (life >= 1f) continue
        val a = (1f - life) * 0.85f
        val hx = st.sx[k]
        val hy = st.sy[k]
        val tx = hx + st.sLen[k] * (1f - life * 0.4f)
        val ty = hy + st.sBend[k] * life * 0.5f
        val cx = (hx + tx) / 2
        val cy = hy + st.sBend[k] * life
        var px = hx
        var py = hy
        for (j in 1..4) {
            // A point along the curve (a "quadratic Bézier": head, a bend point, tail).
            val q = j / 4f
            val m = 1f - q
            val nx = m * m * hx + 2 * m * q * cx + q * q * tx
            val ny = m * m * hy + 2 * m * q * cy + q * q * ty
            drawLine(GLIDER_STREAK_COLOURS[j - 1], Offset(px, py), Offset(nx, ny), strokeWidth = width, cap = StrokeCap.Butt, alpha = a * (1f - (j - 0.5f) / 4f))
            px = nx; py = ny
        }
    }
    // The letters: lifted and glowing right under the thumb, carried out on the wind around it.
    for (i in 0 until GLIDER_COUNT) {
        val d = abs(i - f)
        val b = gliderBell(d, 3.3f)
        val lift = gliderBell(d, 0.9f) * act
        val cx = x0 + st.gx[i]
        val cy = (i + 0.5f) * slot - 6f * u * lift
        val grow = 1f + 0.6f * lift
        val glow = (lift * 0.9f * GLIDER_GLOW + b * act * 0.15f).coerceIn(0f, 1f)
        val colour = when {
            i == st.active -> accent
            has[i] -> railColour
            else -> railColour.copy(alpha = 0.4f)
        }
        val tl = letters[i]
        withTransform({
            translate(cx, cy); rotate(st.tilt[i] * GLIDER_TO_DEG, Offset.Zero); scale(grow, grow, Offset.Zero)
        }) {
            if (glow > 0.01f) drawCircle(p.halo, radius = 16f * u, center = Offset.Zero, alpha = glow)
            railText(tl, color = colour, topLeft = Offset(-tl.size.width / 2f, -tl.size.height / 2f))
        }
    }
    // The letter bubble, riding the wind a little left of the lifted letter.
    if (act > 0.01f) {
        val idx = f.roundToInt().coerceIn(0, GLIDER_COUNT - 1)
        val q = Offset(x0 - 64f * s + st.gx[idx] * 0.9f, (f.coerceIn(0f, GLIDER_COUNT - 1f) + 0.5f) * slot)
        val r = 30f * s * (0.8f + 0.2f * act)
        drawCircle(panel.copy(alpha = 0.95f * act), radius = r, center = q)
        drawCircle(GLIDER_GOLD.copy(alpha = act), radius = r, center = q, style = p.bubbleRing)
        val tl = bubbles[st.shown.coerceIn(0, GLIDER_COUNT - 1)]
        railText(tl, color = GLIDER_LIGHT, topLeft = Offset(q.x - tl.size.width / 2f, q.y - tl.size.height / 2f), alpha = act)
    }
}

private fun gliderDamp(c: Float, target: Float, k: Float, dt: Float) = c + (target - c) * (1f - exp(-k * dt))
private fun gliderBell(d: Float, sigma: Float) = exp(-(d * d) / (2 * sigma * sigma))
