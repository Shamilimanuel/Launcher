package com.shamil.launcher.ui.rail

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * "Seelie guide" (his pick #2, 24 Oct): today's edge strip, with a glowing seelie riding beside it under your thumb.
 * The letters near the seelie light up and grow a little (they don't bend sideways, so nothing slides away under
 * your thumb), and keep glowing for a moment after it passes: the light remembers. A gold light trail and sparks
 * follow it. Let go and it drifts back to rest beside the last letter, small and dim.
 * Touch works exactly like today's rail: slide past the middle of the screen onto the list, hold • for the vault.
 * Numbers are the playground's (own-rail-ideas.html, mkGuide('strip'), a 390-wide phone), scaled to the real screen.
 */

private const val N = 29
private const val HOLD_MS = 600L
/** The same letter column as today's rail, so the letters sit exactly where they did. */
private val LETTERS_WIDTH = 28.dp
private const val TRAIL_MAX = 80
private const val TRAIL_LIFE = 0.9f
private const val SPARK_MAX = 26
private val GOLD = Color(0xFFF3D79C)
private val LIGHT = Color(0xFFFFF6DC)
/** The colour of a lit letter's glow (the playground's #FFE9B0). */
private val GLOW_TEXT = Color(0xFFFFE9B0)

/**
 * The Seelie guide rail: the ☆ # A–Z • strip on the right edge with a seelie that rides beside your thumb.
 * Drops in exactly where [com.shamil.launcher.ui.LetterRail] goes (same parameters, same behaviour).
 */
@Composable
fun SeelieGuideRail(
    present: Set<String>,
    modifier: Modifier = Modifier,
    /** While the thumb slides left off the rail onto the list: its screen y. null when it's back on the rail. */
    onSlide: (Float?) -> Unit = {},
    /** Finger lifted; true if it was sliding on the list (so home can open the app under it). */
    onRelease: (Boolean) -> Unit = {},
    /** Holding • for a moment: opens the vault. */
    onHoldDot: () -> Unit = {},
    onPick: (String) -> Unit,
) {
    val look = LocalLauncherLook.current
    val haptics = LocalHapticFeedback.current
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnHoldDot by rememberUpdatedState(onHoldDot)
    val currentOnSlide by rememberUpdatedState(onSlide)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val scope = rememberCoroutineScope()
    val st = remember { GuideRailState(scope) }

    // Every letter is measured once (a "text layout"), then only drawn: no Text composables to update per frame.
    val measurer = rememberTextMeasurer()
    val letters = remember(look.font) {
        val style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 17.sp)
        RAIL_ITEMS.map { measurer.measure(it, style) }
    }
    val bubbles = remember(look.font) {
        val style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 28.sp)
        RAIL_ITEMS.map { measurer.measure(it, style) }
    }
    val shadow = remember(look.textShadow) { look.shadow }
    val railColour = if (look.rail != Color.Unspecified) look.rail else look.text
    val dimColour = look.text.copy(alpha = 0.4f)

    Box(
        modifier
            // Tell Android not to start its back-swipe here (phones with gesture navigation).
            .systemGestureExclusion()
            .onGloballyPositioned {
                val p = it.positionInRoot()
                st.railTop = p.y
                st.railLeft = p.x
                // The rail touches the screen's right edge, so its right side is the screen width.
                st.setScreenWidth(p.x + it.size.width)
            }
            .pointerInput(Unit) {
                fun follow(x: Float, y: Float) {
                    st.fingerX = x
                    // The seelie and bubble follow the thumb freely, even above ☆ or below • (like today's rail).
                    st.fingerY = y.coerceIn(-size.height * 0.6f, size.height * 1.6f)
                    // Past the middle of the screen = sliding onto the list (same rule as today's rail).
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
                    val i = (st.fingerY / size.height * N).toInt().coerceIn(0, N - 1)
                    if (i != st.active) {
                        st.active = i
                        st.bubbleIdx = i
                        st.restF = i.toFloat()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnPick(RAIL_ITEMS[i])
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown().position
                    st.touching = true
                    follow(down.x, down.y)
                    st.snapToFinger()
                    st.wake()
                    try {
                        // Holding still on • for HOLD_MS = "open the vault" (a buzz, once per touch).
                        var dotSince = if (st.active == N - 1) SystemClock.uptimeMillis() else -1L
                        var held = false
                        while (true) {
                            val waiting = st.active == N - 1 && !held && !st.sliding && dotSince >= 0
                            val event = if (waiting) {
                                val left = (HOLD_MS - (SystemClock.uptimeMillis() - dotSince)).coerceAtLeast(1L)
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
                            val change = event.changes.first()
                            if (!change.pressed) break
                            val before = st.active
                            follow(change.position.x, change.position.y)
                            if (st.active == N - 1 && before != N - 1) dotSince = SystemClock.uptimeMillis()
                            change.consume()
                        }
                        currentOnRelease(st.sliding)
                        if (st.sliding) currentOnSlide(null)
                    } finally {
                        // Also runs if the touch is taken away from us, so the seelie never gets stuck "touched".
                        st.sliding = false
                        st.active = -1
                        st.touching = false
                        st.wake()
                    }
                }
            },
    ) {
        // The drawing gets a layer of its own (graphicsLayer), so redrawing it never redraws the home behind it.
        // It may draw outside the rail's box (the seelie, trail and bubble float to the left): nothing clips it.
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer()
                .drawBehind {
                    st.tick.intValue   // redraw on every frame of the loop
                    st.layout(size.width, size.height, LETTERS_WIDTH.toPx() / 2, density)
                    drawGuide(st, present, letters, bubbles, railColour, dimColour, look.accent, look.panel, shadow)
                },
        )
    }
}

/**
 * The guide's moving parts. Plain fields (not Compose state), read only while drawing, so nothing recomposes per
 * frame. Only [tick] is state: bumping it asks for one redraw.
 */
private class GuideRailState(private val scope: CoroutineScope) {
    val tick = mutableIntStateOf(0)
    private var running = false

    // Where the rail is, in px. [s] = one playground unit (the playground is 390 wide).
    var railTop = 0f
    var railLeft = 0f
    var s = 0f
    var w = 0f
    var h = 1f
    var lx = 0f      // the letters' centre line
    var slot = 1f    // the height of one letter

    // The finger.
    var touching = false
    var sliding = false
    var fingerX = 0f
    var fingerY = 0f
    var active = -1
    var bubbleIdx = 0

    // The seelie (the "orb"): its position, speed, size and brightness.
    var ox = 0f; var oy = 0f
    private var vx = 0f; private var vy = 0f
    var placed = false
    var orbScale = 0.72f
    var orbAlpha = 0.62f
    var restF = 0f
    /** 0 at rest, 1 while the thumb is on the rail. */
    var act = 0f
    /** The smoothed letter position the light is centred on. */
    var fs = 0f
    /** The smoothed height of the bubble. */
    var by = 0f
    val lit = FloatArray(N)
    var t = 0f

    // The light trail: a ring of recent seelie positions with the time each was made ("ring buffer").
    val trailX = FloatArray(TRAIL_MAX); val trailY = FloatArray(TRAIL_MAX); val trailT = FloatArray(TRAIL_MAX)
    var trailStart = 0; var trailCount = 0
    // Sparks thrown off the seelie.
    val sparkX = FloatArray(SPARK_MAX); val sparkY = FloatArray(SPARK_MAX)
    private val sparkVX = FloatArray(SPARK_MAX); private val sparkVY = FloatArray(SPARK_MAX)
    val sparkT = FloatArray(SPARK_MAX); val sparkL = FloatArray(SPARK_MAX); val sparkR = FloatArray(SPARK_MAX)
    val sparkOn = BooleanArray(SPARK_MAX)

    // Gradients and strokes made once per screen size (not per frame), drawn around Offset.Zero and moved into place.
    var haloBrush: Brush? = null
    var coreBrush: Brush? = null
    var glowBrush: Brush? = null
    var ring: Stroke? = null
    var glowShadow: Shadow? = null

    fun setScreenWidth(px: Float) {
        val ns = px / 390f
        if (ns != s) { s = ns; haloBrush = null; tick.intValue++ }
    }

    fun layout(width: Float, height: Float, letterCentre: Float, density: Float) {
        w = width; h = height; lx = letterCentre; slot = height / N
        if (s <= 0f) s = density   // before the first layout pass: about right
        if (haloBrush == null) {
            haloBrush = Brush.radialGradient(listOf(GOLD.copy(alpha = 0.65f), GOLD.copy(alpha = 0.18f), Color.Transparent), center = Offset.Zero, radius = 30f * s)
            coreBrush = Brush.radialGradient(listOf(Color.White, LIGHT, GOLD), center = Offset(-2f * s, -2f * s), radius = 12f * s)
            glowBrush = Brush.radialGradient(listOf(GOLD.copy(alpha = 0.45f), Color.Transparent), center = Offset.Zero, radius = 16f * s)
            ring = Stroke(width = 2f * s)
            glowShadow = Shadow(color = GOLD, blurRadius = 10f * s)
        }
        if (!placed) { ox = lx - 36f * s; oy = (restF + 0.5f) * slot; placed = true }
    }

    /** At the start of a touch the light and bubble start right at the thumb (no sweep from the old spot). */
    fun snapToFinger() {
        fs = (fingerY / slot - 0.5f).coerceIn(0f, N - 1f)
        by = fingerY
    }

    /** Starts the frame loop if it isn't running. It stops itself once everything has settled. */
    fun wake() {
        if (running) return
        running = true
        scope.launch {
            try {
                var last = -1L
                while (true) {
                    val keep = withFrameNanos { now ->
                        val dt = if (last < 0) 1f / 60f else min(0.05f, (now - last) / 1_000_000_000f)
                        last = now
                        step(dt).also { tick.intValue++ }
                    }
                    if (!keep) break
                }
            } finally {
                running = false
            }
        }
    }

    /** One frame forward. Returns true while something still moves (the loop sleeps when it returns false). */
    private fun step(dt: Float): Boolean {
        t += dt
        val live = touching && !sliding
        val sl = touching && sliding
        val tx: Float; val ty: Float; val tos: Float; val toa: Float
        when {
            live -> { tx = lx - 36f * s; ty = fingerY; tos = 1.15f; toa = 1f }
            sl -> { tx = fingerX - 6f * s; ty = fingerY - 30f * s; tos = 1f; toa = 1f }
            // At rest: small and dim, beside the last letter.
            else -> { tx = lx - 36f * s; ty = (restF + 0.5f) * slot; tos = 0.72f; toa = 0.62f }
        }
        // The seelie follows on a soft spring ("k" = how strongly it's pulled, "c" = how much it's slowed down).
        val k = 640f
        val c = 2f * sqrt(k) * 0.6f
        vx += ((tx - ox) * k - vx * c) * dt
        vy += ((ty - oy) * k - vy * c) * dt
        ox += vx * dt
        oy += vy * dt
        orbScale = damp(orbScale, tos, 12f, dt)
        orbAlpha = damp(orbAlpha, toa, 10f, dt)
        act = damp(act, if (live) 1f else 0f, 14f, dt)
        if (live) {
            fs = damp(fs, (fingerY / slot - 0.5f).coerceIn(0f, N - 1f), 24f, dt)
            by = damp(by, fingerY, 24f, dt)
        }
        // Letters near the seelie light up and keep glowing a while: the light remembers.
        var anyLit = false
        val decay = exp(-dt * 2.56f)
        for (i in 0 until N) {
            lit[i] = max(lit[i] * decay, bell(abs(fs - i), 1.7f) * act)
            if (lit[i] > 0.01f) anyLit = true
        }
        // The trail: a new point when the seelie has moved a little; points fade out after TRAIL_LIFE.
        val speed = hypot(vx, vy)
        if (live || sl || speed > 40f * s) {
            val lastI = (trailStart + trailCount - 1).mod(TRAIL_MAX)
            if (trailCount == 0 || hypot(ox - trailX[lastI], oy - trailY[lastI]) > 3f * s) {
                val at = (trailStart + trailCount).mod(TRAIL_MAX)
                trailX[at] = ox; trailY[at] = oy; trailT[at] = t
                if (trailCount < TRAIL_MAX) trailCount++ else trailStart = (trailStart + 1).mod(TRAIL_MAX)
            }
            if ((live || sl) && Random.nextFloat() < 0.5f) {
                val free = sparkOn.indexOfFirst { !it }
                if (free >= 0) {
                    sparkOn[free] = true
                    sparkX[free] = ox + (Random.nextFloat() - 0.5f) * 8f * s
                    sparkY[free] = oy + (Random.nextFloat() - 0.5f) * 8f * s
                    sparkVX[free] = (Random.nextFloat() - 0.5f) * 46f * s
                    sparkVY[free] = ((Random.nextFloat() - 0.5f) * 46f - 8f) * s
                    sparkT[free] = t
                    sparkL[free] = 0.6f + Random.nextFloat() * 0.6f
                    sparkR[free] = (0.8f + Random.nextFloat() * 1.5f) * s
                }
            }
        }
        while (trailCount > 0 && t - trailT[trailStart] > TRAIL_LIFE) { trailStart = (trailStart + 1).mod(TRAIL_MAX); trailCount-- }
        var anySpark = false
        for (j in 0 until SPARK_MAX) if (sparkOn[j]) {
            if (t - sparkT[j] > sparkL[j]) sparkOn[j] = false
            else { sparkX[j] += sparkVX[j] * dt; sparkY[j] += sparkVY[j] * dt; anySpark = true }
        }
        val settling = hypot(tx - ox, ty - oy) > 0.5f || speed > 5f
        return touching || act > 0.01f || anyLit || trailCount > 0 || anySpark || settling ||
            abs(orbScale - tos) > 0.01f || abs(orbAlpha - toa) > 0.01f
    }
}

/** Moves [c] towards [target] smoothly; [k] is how fast (frame-rate independent). */
private fun damp(c: Float, target: Float, k: Float, dt: Float) = c + (target - c) * (1f - exp(-k * dt))
/** A "bell curve": 1 at distance 0, fading smoothly to 0; [sigma] is how wide it is. */
private fun bell(d: Float, sigma: Float) = exp(-(d * d) / (2 * sigma * sigma))

private fun DrawScope.drawGuide(
    st: GuideRailState, present: Set<String>, letters: List<TextLayoutResult>, bubbles: List<TextLayoutResult>,
    railColour: Color, dimColour: Color, accent: Color, panel: Color, shadow: Shadow,
) {
    val s = st.s
    val glowBrush = st.glowBrush ?: return
    val glowShadow = st.glowShadow ?: return
    // The light trail behind the seelie (drawn first, so the letters stay readable on top of it):
    // thick and bright where it just was, thinning and fading behind.
    if (st.trailCount > 1) {
        for (j in 1 until st.trailCount) {
            val a = (st.trailStart + j - 1).mod(TRAIL_MAX)
            val b = (st.trailStart + j).mod(TRAIL_MAX)
            var u = 1f - (st.t - st.trailT[b]) / TRAIL_LIFE
            if (u <= 0f) continue
            u = u * u * (3f - 2f * u)   // "smoothstep": eases the fade in and out
            val width = (1f + 7.5f * u) * s * 0.9f
            val from = Offset(st.trailX[a], st.trailY[a])
            val to = Offset(st.trailX[b], st.trailY[b])
            drawLine(GOLD.copy(alpha = 0.2f * 0.85f * u), from, to, strokeWidth = width * 2.7f, cap = StrokeCap.Round)
            drawLine(LIGHT.copy(alpha = 0.92f * u), from, to, strokeWidth = width, cap = StrokeCap.Round)
        }
    }
    // The letters: lit ones get a gold glow and grow a little.
    for (i in 0 until N) {
        val item = RAIL_ITEMS[i]
        val has = item == STAR || item == DOT || item in present
        val l = st.lit[i]
        val q = Offset(st.lx, (i + 0.5f) * st.slot)
        val tl = letters[i]
        val topLeft = Offset(q.x - tl.size.width / 2f, q.y - tl.size.height / 2f)
        val colour = when {
            i == st.active -> accent
            has -> railColour
            else -> dimColour
        }
        scale(1f + 0.42f * l, q) {
            if (l > 0.02f) {
                val g = if (has) l else l * 0.5f   // empty letters glow only half as much
                translate(q.x, q.y) { drawCircle(glowBrush, radius = 16f * s, center = Offset.Zero, alpha = g) }
                // The glow: the letter again, in warm gold with a blurred gold shadow, under the real letter.
                railText(tl, color = GLOW_TEXT, topLeft = topLeft, alpha = (0.935f * g).coerceIn(0f, 1f), shadow = glowShadow)
            }
            railText(tl, color = colour, topLeft = topLeft, shadow = shadow)
        }
    }
    // Sparks.
    for (k in 0 until SPARK_MAX) if (st.sparkOn[k]) {
        val u = max(0f, 1f - (st.t - st.sparkT[k]) / st.sparkL[k])
        drawCircle(LIGHT.copy(alpha = 0.9f * u), radius = st.sparkR[k] * u + 0.3f * s, center = Offset(st.sparkX[k], st.sparkY[k]))
    }
    // The seelie: a soft halo and a bright core.
    val halo = st.haloBrush
    val core = st.coreBrush
    if (halo != null && core != null) {
        translate(st.ox, st.oy) {
            scale(st.orbScale, Offset.Zero) {
                drawCircle(halo, radius = 30f * s, center = Offset.Zero, alpha = st.orbAlpha)
                drawCircle(core, radius = 12f * s, center = Offset.Zero, alpha = st.orbAlpha)
            }
        }
    }
    // The letter bubble, well left of the seelie, following the thumb.
    val act = st.act
    if (act > 0.01f) {
        val q = Offset(st.lx - 98f * s, st.by)
        val r = 30f * s * (0.8f + 0.2f * act)
        drawCircle(panel.copy(alpha = 0.95f * act), radius = r, center = q)
        st.ring?.let { drawCircle(GOLD.copy(alpha = act), radius = r, center = q, style = it) }
        val tl = bubbles[st.bubbleIdx.coerceIn(0, N - 1)]
        railText(tl, color = LIGHT, topLeft = Offset(q.x - tl.size.width / 2f, q.y - tl.size.height / 2f), alpha = act)
    }
}
