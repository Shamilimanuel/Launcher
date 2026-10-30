package com.shamil.launcher.ui.rail

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.DOT
import com.shamil.launcher.ui.STAR
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * "Guide on the arc", Seelie's own A–Z (his #1 pick, 24 Oct). The letters sit on a quarter circle around the
 * bottom-right corner, where a thumb naturally sweeps (a large phone can't reach the top of an edge rail),
 * and a seelie guide rides along the curve under your thumb, lighting the letters it passes and leaving a gold
 * light trail: the app icon in motion. At rest only a faint curve and the resting spirit show.
 *
 * Touch: the arc never steals a TAP. It only takes over when you drag ALONG the curve (starting near it); a tap,
 * a scroll or a sideways app swipe goes to whatever is underneath, like before. Drag outwards (towards the screen's
 * middle) to slide onto the app list and let go on an app to open it. Hold still on • for the vault.
 * Geometry and feel are the playground's numbers (a 390 × 844 phone), scaled to the real screen.
 */

/** The letters along the arc, from its upper end to its lower end. */
internal val RAIL_ITEMS = listOf(STAR, "#") + ('A'..'Z').map { it.toString() } + DOT
private const val N = 29
private const val HOLD_MS = 600L

/** Where the arc sits for a home of [w] × [h] px. */
internal class ArcGeometry(val w: Float, val h: Float) {
    val s = w / 390f
    val px = w + 8f * s
    val py = h * (800f / 844f)
    val r = 300f * s
    private val a0 = Math.toRadians(10.0).toFloat()
    private val a1 = Math.toRadians(88.0).toFloat()
    fun angle(f: Float) = a0 + (a1 - a0) * f / (N - 1)
    /** The point on the arc for letter position [f], pushed [out] px away from the corner. */
    fun point(f: Float, out: Float = 0f): Offset {
        val a = angle(f); val rr = r + out
        return Offset(px - rr * sin(a), py - rr * cos(a))
    }
    /** Which letter position a point is at (fractional). */
    fun f(p: Offset): Float = (atan2(-(p.x - px), -(p.y - py)) - a0) / (a1 - a0) * (N - 1)
    fun dist(p: Offset) = hypot(p.x - px, p.y - py)
    /** The direction along the arc at [f]. */
    fun tangent(f: Float): Offset { val a = angle(f); return Offset(-cos(a), sin(a)) }
}

/** A touch at [p] lands on the arc's band (where arcRailInput would look at it). */
internal fun ArcGeometry.near(p: Offset): Boolean {
    if (abs(dist(p) - r) > 30f * s) return false
    val f0 = f(p)
    return f0 >= -0.8f && f0 <= N - 0.2f
}

/** A move [d] that started at [p] runs along the arc, not across it. */
internal fun ArcGeometry.along(p: Offset, d: Offset): Boolean {
    val tan = tangent(f(p))
    val dist = d.getDistance()
    return dist > 0f && abs(d.x * tan.x + d.y * tan.y) / dist >= 0.6f
}

/** What the home hears from the arc (the same as from the edge rail). */
class ArcCallbacks(
    val onPick: (String) -> Unit,
    val onSlide: (Float?) -> Unit,
    val onRelease: (Boolean) -> Unit,
    val onHoldDot: () -> Unit,
    val buzz: (strong: Boolean) -> Unit,
)

/** The arc's moving parts. Plain fields read only while drawing, so nothing recomposes per frame. */
class ArcRailState {
    internal var geo: ArcGeometry? = null
    var callbacks: ArcCallbacks? = null
    internal var awake by mutableStateOf(false)
    internal val tick = mutableIntStateOf(0)

    private var touching = false
    internal var sliding = false
    private var finger = Offset.Zero
    internal var active = -1
    internal val lit = FloatArray(N)
    internal var orb = Offset.Unspecified
    private var vel = Offset.Zero
    internal var orbScale = 0.72f
    internal var orbAlpha = 0.62f
    internal var act = 0f
    internal var fs = 0f
    private var fT = 0f
    private var restF = 0f
    private var t = 0f
    // The light trail: recent orb positions with the time they were made (a ring of 80).
    internal val trailX = FloatArray(80); internal val trailY = FloatArray(80); internal val trailT = FloatArray(80)
    internal var trailStart = 0; internal var trailCount = 0
    // Sparks thrown off the guide.
    internal val sparkX = FloatArray(26); internal val sparkY = FloatArray(26); private val sparkVX = FloatArray(26); private val sparkVY = FloatArray(26)
    internal val sparkT = FloatArray(26); internal val sparkL = FloatArray(26); internal val sparkR = FloatArray(26); internal val sparkOn = BooleanArray(26)
    internal val now get() = t

    internal fun start(p: Offset) { touching = true; awake = true; move(p) }

    internal fun move(p: Offset) {
        val g = geo ?: return
        val cb = callbacks
        finger = p
        // Outwards past the letters (towards the screen's middle) = sliding onto the list.
        if (g.dist(p) > g.r + 105f * g.s) {
            if (!sliding) cb?.buzz?.invoke(true)
            sliding = true
            cb?.onSlide?.invoke(p.y)
            return
        }
        if (sliding) { sliding = false; cb?.onSlide?.invoke(null) }
        val i = g.f(p).roundToInt().coerceIn(0, N - 1)
        if (i != active) {
            active = i
            restF = i.toFloat()
            cb?.buzz?.invoke(false)
            cb?.onPick?.invoke(RAIL_ITEMS[i])
        }
    }

    internal fun end(): Boolean {
        val wasSliding = sliding
        touching = false
        sliding = false
        active = -1
        awake = true
        return wasSliding
    }

    /** One frame forward. Returns true while something still moves (the loop sleeps when it returns false). */
    internal fun step(dt: Float): Boolean {
        val g = geo ?: return false
        t += dt
        val s = g.s
        val live = touching && !sliding
        val sl = touching && sliding
        val target: Offset
        val tos: Float
        val toa: Float
        if (live) {
            fT = g.f(finger).coerceIn(-0.5f, N - 0.5f)
            target = g.point(fT, 34f * s); tos = 1.15f; toa = 1f
        } else if (sl) {
            target = finger + Offset(-6f * s, -30f * s); tos = 1f; toa = 1f
        } else {
            target = g.point(restF, 34f * s); tos = 0.72f; toa = 0.62f
        }
        if (orb == Offset.Unspecified) orb = target
        // The guide follows on a soft spring.
        val k = 640f
        val c = 2f * sqrt(k) * 0.6f
        vel += ((target - orb) * k - vel * c) * dt
        orb += vel * dt
        orbScale = damp(orbScale, tos, 12f, dt)
        orbAlpha = damp(orbAlpha, toa, 10f, dt)
        act = damp(act, if (live) 1f else 0f, 14f, dt)
        fs = damp(fs, fT, 24f, dt)
        // Letters near the guide light up and keep glowing a while: the light remembers.
        var anyLit = false
        val decay = exp(-dt * 2.56f)
        for (i in 0 until N) {
            val tg = bell(abs(fs - i), 1.7f) * act
            lit[i] = max(lit[i] * decay, tg)
            if (lit[i] > 0.01f) anyLit = true
        }
        // The trail: a new point when the guide has moved a little; points fade out after 0.9 s.
        val moving = live || sl || vel.getDistance() > 40f * s
        if (moving) {
            val lastI = (trailStart + trailCount - 1).mod(80)
            if (trailCount == 0 || hypot(orb.x - trailX[lastI], orb.y - trailY[lastI]) > 3f * s) {
                val at = (trailStart + trailCount).mod(80)
                trailX[at] = orb.x; trailY[at] = orb.y; trailT[at] = t
                if (trailCount < 80) trailCount++ else trailStart = (trailStart + 1).mod(80)
            }
            if ((live || sl) && Random.nextFloat() < 0.5f) {
                val free = sparkOn.indexOfFirst { !it }
                if (free >= 0) {
                    sparkOn[free] = true
                    sparkX[free] = orb.x + (Random.nextFloat() - 0.5f) * 8f * s
                    sparkY[free] = orb.y + (Random.nextFloat() - 0.5f) * 8f * s
                    sparkVX[free] = (Random.nextFloat() - 0.5f) * 46f * s
                    sparkVY[free] = ((Random.nextFloat() - 0.5f) * 46f - 8f) * s
                    sparkT[free] = t; sparkL[free] = 0.6f + Random.nextFloat() * 0.6f; sparkR[free] = (0.8f + Random.nextFloat() * 1.5f) * s
                }
            }
        }
        while (trailCount > 0 && t - trailT[trailStart] > TRAIL_LIFE) { trailStart = (trailStart + 1).mod(80); trailCount-- }
        var anySpark = false
        for (k2 in 0 until 26) if (sparkOn[k2]) {
            if (t - sparkT[k2] > sparkL[k2]) sparkOn[k2] = false
            else { sparkX[k2] += sparkVX[k2] * dt; sparkY[k2] += sparkVY[k2] * dt; anySpark = true }
        }
        val settling = (target - orb).getDistance() > 0.5f || vel.getDistance() > 5f
        return touching || act > 0.01f || anyLit || trailCount > 0 || anySpark || settling ||
            abs(orbScale - tos) > 0.01f || abs(orbAlpha - toa) > 0.01f
    }

    companion object {
        internal const val TRAIL_LIFE = 0.9f
    }
}

private fun damp(c: Float, target: Float, k: Float, dt: Float) = c + (target - c) * (1f - exp(-k * dt))
private fun bell(d: Float, sigma: Float) = exp(-(d * d) / (2 * sigma * sigma))

/**
 * The arc's touch handling, on the home's root (so it sees touches first): it only claims a drag that starts near
 * the curve and runs along it, or a still hold on •. Everything else passes through untouched.
 */
fun Modifier.arcRailInput(state: ArcRailState, enabled: Boolean): Modifier = if (!enabled) this else pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val g = state.geo ?: return@awaitEachGesture
        // Left-hand mode: the arc is drawn mirrored, so read the finger mirrored too.
        fun m(o: Offset) = if (RailSide.left) Offset(g.w - o.x, o.y) else o
        val p = m(down.position)
        if (abs(g.dist(p) - g.r) > 30f * g.s) return@awaitEachGesture
        val f0 = g.f(p)
        if (f0 < -0.8f || f0 > N - 0.2f) return@awaitEachGesture
        val nearDot = f0.roundToInt() >= N - 1
        val since = SystemClock.uptimeMillis()
        var claimed = false
        var held = false
        while (true) {
            val waiting = !claimed && nearDot && !held
            val event = if (waiting) {
                val left = (HOLD_MS - (SystemClock.uptimeMillis() - since)).coerceAtLeast(1L)
                withTimeoutOrNull(left) { awaitPointerEvent(PointerEventPass.Initial) }
            } else {
                awaitPointerEvent(PointerEventPass.Initial)
            }
            if (event == null) {
                // Held still on • : the vault.
                held = true
                state.callbacks?.buzz?.invoke(true)
                state.callbacks?.onHoldDot?.invoke()
                continue
            }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (claimed || held) change.consume()
                break
            }
            if (held) { change.consume(); continue }
            if (!claimed) {
                val d = m(change.position) - p
                val dist = d.getDistance()
                if (dist < viewConfiguration.touchSlop) continue
                // Mostly along the curve = ours; across it (a scroll, an app swipe) = not ours.
                val tan = g.tangent(f0)
                if (abs(d.x * tan.x + d.y * tan.y) / dist < 0.6f) return@awaitEachGesture
                claimed = true
                state.start(m(change.position))
            }
            change.consume()
            state.move(m(change.position))
        }
        if (claimed) {
            val slid = state.end()
            state.callbacks?.onRelease?.invoke(slid)
            if (slid) state.callbacks?.onSlide?.invoke(null)
        }
    }
}

/** The arc's drawing: a full-screen layer of its own (redrawing it never redraws the home), lit only while it moves. */
@Composable
fun ArcRailLayer(state: ArcRailState, present: Set<String>, modifier: Modifier = Modifier) {
    val look = LocalLauncherLook.current
    val measurer = rememberTextMeasurer()
    val letterStyle = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
    val bubbleStyle = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White)
    val letters = remember(look.font) { RAIL_ITEMS.map { measurer.measure(it, letterStyle) } }
    val bubbles = remember(look.font) { RAIL_ITEMS.map { measurer.measure(it, bubbleStyle) } }
    val railColour = if (look.rail != Color.Unspecified) look.rail else look.text
    // The frame loop: only while something moves; it wakes on a touch and sleeps when everything has settled.
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
                drawArc(g, state, look.accent, look.panel, railColour, present, letters, bubbles)
            }
    )
}

private val GOLD = Color(0xFFF3D79C)
private val LIGHT = Color(0xFFFFF6DC)

private fun DrawScope.drawArc(
    g: ArcGeometry, st: ArcRailState, accent: Color, panel: Color, railColour: Color, present: Set<String>,
    letters: List<TextLayoutResult>, bubbles: List<TextLayoutResult>,
) {
    val s = g.s
    val act = st.act
    // The curve itself: faint at rest, brighter while you use it.
    val track = Path().apply {
        var f = -0.9f
        var first = true
        while (f <= N - 0.1f) {
            val q = g.point(f)
            if (first) { moveTo(q.x, q.y); first = false } else lineTo(q.x, q.y)
            f += 0.25f
        }
    }
    drawPath(track, GOLD.copy(alpha = 0.06f + 0.12f * act), style = Stroke(width = 12f * s, cap = StrokeCap.Round))
    drawPath(track, GOLD.copy(alpha = 0.22f + 0.4f * act), style = Stroke(width = 1.6f * s, cap = StrokeCap.Round))
    // The letters: tiny dots at rest, the letters themselves while you use the arc (and where the light lingers).
    for (i in 0 until N) {
        val item = RAIL_ITEMS[i]
        val has = item == STAR || item == DOT || item in present
        val l = st.lit[i]
        val show = max(act, l)
        val q = g.point(i.toFloat(), l * 13f * s)
        if (show < 0.02f) {
            drawCircle(GOLD.copy(alpha = if (has) 0.4f else 0.18f), radius = (if (has) 1.7f else 1f) * s, center = g.point(i.toFloat(), -17f * s))
            continue
        }
        if (l > 0.05f) drawCircle(Brush.radialGradient(listOf(GOLD.copy(alpha = 0.45f * l), Color.Transparent), center = q, radius = 16f * s),
            radius = 16f * s, center = q)
        val colour = when {
            i == st.active -> accent
            has -> railColour
            else -> railColour.copy(alpha = 0.4f)
        }
        val tl = letters[i]
        val k = 1f + 0.42f * l
        withTransform({ scale(k, k, q) }) {
            railText(tl, color = colour, topLeft = Offset(q.x - tl.size.width / 2f, q.y - tl.size.height / 2f), alpha = show.coerceIn(0f, 1f))
        }
    }
    // The light trail behind the guide: thick and bright where it just was, thinning and fading behind.
    val t = st.now
    if (st.trailCount > 1) {
        for (j in 1 until st.trailCount) {
            val a = (st.trailStart + j - 1).mod(80)
            val b = (st.trailStart + j).mod(80)
            var u = 1f - (t - st.trailT[b]) / ArcRailState.TRAIL_LIFE
            if (u <= 0f) continue
            u = u * u * (3f - 2f * u)
            val w = (1f + 7.5f * u) * s * 0.9f
            val from = Offset(st.trailX[a], st.trailY[a]); val to = Offset(st.trailX[b], st.trailY[b])
            drawLine(GOLD.copy(alpha = 0.2f * u), from, to, strokeWidth = w * 2.7f, cap = StrokeCap.Round)
            drawLine(LIGHT.copy(alpha = 0.92f * u), from, to, strokeWidth = w, cap = StrokeCap.Round)
        }
    }
    for (k in 0 until 26) if (st.sparkOn[k]) {
        val u = max(0f, 1f - (t - st.sparkT[k]) / st.sparkL[k])
        drawCircle(LIGHT.copy(alpha = 0.9f * u), radius = st.sparkR[k] * u + 0.3f * s, center = Offset(st.sparkX[k], st.sparkY[k]))
    }
    // The guide: a soft halo and a bright core.
    if (st.orb != Offset.Unspecified) {
        val sc = st.orbScale
        drawCircle(Brush.radialGradient(listOf(GOLD.copy(alpha = 0.65f * st.orbAlpha), GOLD.copy(alpha = 0.18f * st.orbAlpha), Color.Transparent),
            center = st.orb, radius = 30f * s * sc), radius = 30f * s * sc, center = st.orb)
        drawCircle(Brush.radialGradient(listOf(Color.White, LIGHT, GOLD), center = st.orb - Offset(2f * s, 2f * s), radius = 12f * s * sc),
            radius = 12f * s * sc, center = st.orb, alpha = st.orbAlpha)
    }
    // The letter bubble, a little further out from the guide.
    if (act > 0.01f) {
        val idx = st.fs.roundToInt().coerceIn(0, N - 1)
        val q0 = g.point(st.fs, 84f * s)
        val q = Offset(max(46f * s, q0.x), q0.y)
        val r = 30f * s * (0.8f + 0.2f * act)
        drawCircle(panel.copy(alpha = 0.95f * act), radius = r, center = q)
        drawCircle(GOLD.copy(alpha = act), radius = r, center = q, style = Stroke(width = 2f * s))
        val tl = bubbles[idx]
        railText(tl, color = LIGHT, topLeft = Offset(q.x - tl.size.width / 2f, q.y - tl.size.height / 2f), alpha = act)
    }
}

@Composable
fun rememberArcRailState(): ArcRailState = remember { ArcRailState() }
