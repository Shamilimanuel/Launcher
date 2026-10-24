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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.LaunchStats
import com.shamil.launcher.ui.DOT
import com.shamil.launcher.ui.STAR
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * "Constellation" (his pick #5, 24 Oct): the letters are stars, set in a slight zigzag. A faint "sleeping
 * constellation" joins the letters that have apps; empty letters are small, dim stars. Sliding draws bright
 * constellation lines between the stars you pass, which fade out about a second after you let go.
 * Hold still on a star for 0.4 s and a little shape traces out to the left, one star per app, with the names:
 * slide onto one of those stars and let go to open that app. (Once the shape shows, the letter locks like the
 * petal fan's, so reaching for a star can't switch letters or slide onto the list.)
 * Everything else works like today's rail. Numbers are the playground's (own-rail-ideas.html, mkConstellation(),
 * a 390-wide phone), scaled to the real screen.
 */

private const val N = 29
private const val HOLD_MS = 600L
/** How long to stay on a star before its shape traces out. */
private const val PEEK_MS = 400L
private val LETTERS_WIDTH = 28.dp
private val LOCK_GAP = 8.dp
private val DEAD_ZONE = 10.dp
/** One star per app in the shape, at most this many. */
private const val MAX_SHAPE = 6
/** How many passed stars the bright line remembers. */
private const val PATH_MAX = 96
/** How long the shape takes to trace out (seconds). */
private const val TRACE_TIME = 0.9f
/** How long the bright lines take to fade after you let go (seconds). */
private const val LINE_FADE = 1.1f
private const val SHAPE_HIT = 24f
private val GOLD = Color(0xFFF3D79C)
private val LIGHT = Color(0xFFFFF6DC)
private val GLOW_TEXT = Color(0xFFFFE9B0)
private val NAME_TEXT = Color(0xFFFFF1CE)
private val NAME_PILL = Color(0xB30E1226)

/**
 * The constellation rail. The same as [com.shamil.launcher.ui.LetterRail] plus [appsFor] (the apps filed under a
 * letter, for the shape) and [onOpen] (the app whose star the thumb let go on).
 */
@Composable
fun ConstellationRail(
    present: Set<String>,
    appsFor: (String) -> List<AppEntry>,
    onOpen: (AppEntry) -> Unit,
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
    val context = LocalContext.current
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnHoldDot by rememberUpdatedState(onHoldDot)
    val currentOnSlide by rememberUpdatedState(onSlide)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentAppsFor by rememberUpdatedState(appsFor)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val scope = rememberCoroutineScope()
    val st = remember { ConstellationState(scope) }

    val measurer = rememberTextMeasurer()
    val letters = remember(look.font) {
        val style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 17.sp)
        RAIL_ITEMS.map { measurer.measure(it, style) }
    }
    val bubbles = remember(look.font) {
        val style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 28.sp)
        RAIL_ITEMS.map { measurer.measure(it, style) }
    }
    val nameStyle = remember(look.font) {
        TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = NAME_TEXT)
    }
    val shadow = remember(look.textShadow) { look.shadow }
    val railColour = if (look.rail != Color.Unspecified) look.rail else look.text
    val dimColour = look.text.copy(alpha = 0.4f)

    Box(
        modifier
            .systemGestureExclusion()
            .onGloballyPositioned {
                val p = it.positionInRoot()
                st.railTop = p.y
                st.railLeft = p.x
                st.setScreenWidth(p.x + it.size.width)
            }
            .pointerInput(Unit) {
                val lockPx = LOCK_GAP.toPx()
                val deadPx = DEAD_ZONE.toPx()

                fun choose(i: Int) {
                    st.active = i
                    st.bubbleIdx = i
                    st.selAt = SystemClock.uptimeMillis()
                    st.clearShape()
                    st.lit[i] = 1f
                    st.passed(i)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnPick(RAIL_ITEMS[i])
                }

                /** Held on a star long enough: trace its shape (if the letter has apps). */
                fun peek() {
                    st.shapeTried = true
                    val i = st.active
                    if (i <= 0 || i >= N - 1) return
                    val apps = byUse(currentAppsFor(RAIL_ITEMS[i]), st.uses)
                    if (apps.isEmpty()) return
                    val maxW = (110f * st.unit()).roundToInt().coerceAtLeast(1)
                    val count = min(apps.size, MAX_SHAPE)
                    for (k in 0 until MAX_SHAPE) {
                        st.shapeApps[k] = if (k < count) apps[k] else null
                        st.shapeLabels[k] = if (k < count) {
                            measurer.measure(apps[k].label, nameStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxW))
                        } else null
                    }
                    st.makeShape(i, count)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }

                fun follow(x: Float, y: Float) {
                    st.fingerX = x
                    st.fingerY = y.coerceIn(-size.height * 0.6f, size.height * 1.6f)
                    st.reaching = false
                    if (st.shapeOn) {
                        // The letter lock, as in the petal fan: reaching left for the shape keeps the letter.
                        val left = x < st.lx - lockPx
                        val near = abs(st.fingerY - (st.shapeIdx + 0.5f) * st.slot) < st.slot / 2 + deadPx
                        if (left || near) {
                            st.reaching = left
                            val k = if (left) st.hitShape(x, y) else -1
                            if (k != st.hover) {
                                st.hover = k
                                if (k >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            return
                        }
                    }
                    st.hover = -1
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
                    if (i != st.active) choose(i)
                }

                awaitEachGesture {
                    val down = awaitFirstDown().position
                    st.uses = LaunchStats.get(context).mostOpened().toMap()
                    st.touching = true
                    st.startTouch()
                    follow(down.x, down.y)
                    st.by = st.fingerY
                    st.wake()
                    try {
                        var dotSince = if (st.active == N - 1) SystemClock.uptimeMillis() else -1L
                        var held = false
                        while (true) {
                            // Two "hold still" timers: • for the vault (600 ms), any letter star for its shape (400 ms).
                            val now = SystemClock.uptimeMillis()
                            val dotWait = if (st.active == N - 1 && !held && !st.sliding && dotSince >= 0) HOLD_MS - (now - dotSince) else Long.MAX_VALUE
                            val starWait = if (st.active in 1 until N - 1 && !st.shapeTried && !st.sliding) PEEK_MS - (now - st.selAt) else Long.MAX_VALUE
                            val wait = min(dotWait, starWait)
                            val event = if (wait != Long.MAX_VALUE) {
                                withTimeoutOrNull(wait.coerceAtLeast(1L)) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                if (dotWait <= starWait) {
                                    held = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnHoldDot()
                                } else {
                                    peek()
                                }
                                continue
                            }
                            val change = event.changes.first()
                            if (!change.pressed) break
                            val before = st.active
                            follow(change.position.x, change.position.y)
                            if (st.active == N - 1 && before != N - 1) dotSince = SystemClock.uptimeMillis()
                            change.consume()
                        }
                        val app = if (st.hover >= 0) st.shapeApps[st.hover] else null
                        currentOnRelease(st.sliding)
                        if (st.sliding) currentOnSlide(null)
                        if (app != null) currentOnOpen(app)
                    } finally {
                        st.sliding = false
                        st.reaching = false
                        st.active = -1
                        st.hover = -1
                        st.touching = false
                        st.wake()
                    }
                }
            },
    ) {
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer()
                .drawBehind {
                    st.tick.intValue   // redraw on every frame of the loop
                    st.layout(size.height, LETTERS_WIDTH.toPx() / 2, density, present)
                    drawConstellation(st, letters, bubbles, railColour, dimColour, look.accent, look.panel, shadow)
                },
        )
    }
}

/** The constellation's moving parts: plain fields read while drawing (only [tick] is Compose state). */
private class ConstellationState(private val scope: CoroutineScope) {
    val tick = mutableIntStateOf(0)
    private var running = false

    var railTop = 0f
    var railLeft = 0f
    var s = 0f
    var h = 0f
    var lx = 0f
    var slot = 1f
    /** Where each star sits (the playground's zigzag). */
    val starX = FloatArray(N)
    val starY = FloatArray(N)
    /** Which rail items have apps (☆ and • always count). */
    val has = BooleanArray(N)
    private var hasFor: Set<String>? = null

    var touching = false
    var sliding = false
    var reaching = false
    var fingerX = 0f
    var fingerY = 0f
    var active = -1
    var bubbleIdx = 0
    var selAt = 0L
    var uses: Map<String, Int> = emptyMap()
    var act = 0f
    var by = 0f
    var t = 0f
    val lit = FloatArray(N)
    /** How bright each star's four-pointed glint is (smoothed). */
    val glint = FloatArray(N)

    // The bright line through the stars you passed: their indexes, oldest first.
    val path = IntArray(PATH_MAX)
    var pathLen = 0
    var fade = 0f

    // The shape of the star you're holding.
    var shapeOn = false
    var shapeTried = false
    var shapeIdx = 0
    var shapeN = 0
    var shapeT0 = 0f
    var shA = 0f
    val ptsX = FloatArray(MAX_SHAPE)
    val ptsY = FloatArray(MAX_SHAPE)
    val shapeApps = arrayOfNulls<AppEntry>(MAX_SHAPE)
    val shapeLabels = arrayOfNulls<TextLayoutResult>(MAX_SHAPE)
    var hover = -1
    val hoverA = FloatArray(MAX_SHAPE)

    // Paths are reused (reset and refilled), never made new per frame.
    val sleepPath = Path()
    val linePath = Path()
    val shapePath = Path()
    val glintPath = Path()
    var glowBrush: Brush? = null
    var glintBrush: Brush? = null
    var sleepStroke: Stroke? = null
    var glowStroke: Stroke? = null
    var coreStroke: Stroke? = null
    var shapeStroke: Stroke? = null
    var ring: Stroke? = null
    var glowShadow: Shadow? = null

    fun unit(): Float = if (s > 0f) s else 3f

    fun setScreenWidth(px: Float) {
        val ns = px / 390f
        if (ns != s) { s = ns; glowBrush = null; tick.intValue++ }
    }

    fun layout(height: Float, letterCentre: Float, density: Float, present: Set<String>) {
        var moved = false
        if (s <= 0f) { s = density; glowBrush = null }
        if (glowBrush == null || height != h || letterCentre != lx) {
            h = height; lx = letterCentre; slot = height / N
            for (i in 0 until N) {
                starX[i] = lx + sin(i * 2.1f) * 8f * s
                starY[i] = (i + 0.5f) * slot + cos(i * 1.7f) * 2.5f * s
            }
            glowBrush = Brush.radialGradient(listOf(GOLD.copy(alpha = 0.45f), Color.Transparent), center = Offset.Zero, radius = 16f * s)
            glintBrush = Brush.radialGradient(
                0f to LIGHT, 0.22f to GOLD, 0.62f to Color.Transparent,
                center = Offset.Zero, radius = 21.2f * s,
            )
            // A four-pointed star, 30 units across, around Offset.Zero.
            val a = 15f * s
            val b = 2.7f * s
            glintPath.reset()
            glintPath.moveTo(0f, -a); glintPath.lineTo(b, -b); glintPath.lineTo(a, 0f); glintPath.lineTo(b, b)
            glintPath.lineTo(0f, a); glintPath.lineTo(-b, b); glintPath.lineTo(-a, 0f); glintPath.lineTo(-b, -b)
            glintPath.close()
            sleepStroke = Stroke(width = 1f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
            glowStroke = Stroke(width = 7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
            coreStroke = Stroke(width = 1.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
            shapeStroke = Stroke(width = 1.3f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
            ring = Stroke(width = 2f * s)
            glowShadow = Shadow(color = GOLD, blurRadius = 10f * s)
            moved = true
        }
        // The sleeping constellation: a faint line through the letters that have apps (empty ones break it).
        if (moved || hasFor !== present) {
            hasFor = present
            sleepPath.reset()
            var prev = false
            for (i in 0 until N) {
                val item = RAIL_ITEMS[i]
                has[i] = item == STAR || item == DOT || item in present
                if (!has[i]) { prev = false; continue }
                if (prev) sleepPath.lineTo(starX[i], starY[i]) else sleepPath.moveTo(starX[i], starY[i])
                prev = true
            }
            // The glints' resting brightness (the frame loop isn't running now, so set it straight away).
            if (!touching) for (i in 0 until N) glint[i] = if (has[i]) 0.55f else 0.12f
        }
    }

    fun startTouch() {
        pathLen = 0
        fade = 1f
        clearShape()
    }

    fun clearShape() {
        shapeOn = false
        shapeTried = false
        hover = -1
    }

    /** The thumb reached star [i]: add it to the bright line (or step back, if it went back the way it came). */
    fun passed(i: Int) {
        if (pathLen > 1 && path[pathLen - 2] == i) { pathLen--; return }
        if (pathLen > 0 && path[pathLen - 1] == i) return
        if (pathLen == PATH_MAX) { path.copyInto(path, 0, 1, PATH_MAX); pathLen-- }
        path[pathLen++] = i
    }

    /** Lays out the shape for star [i] with [n] stars. The same letter always gets the same shape. */
    fun makeShape(i: Int, n: Int) {
        val rnd = Random(i * 97 + 5)
        ptsX[0] = lx - 112f * s
        ptsY[0] = starY[i]
        for (k in 1 until n) {
            ptsX[k] = ptsX[k - 1] - (34f + rnd.nextFloat() * 14f) * s
            ptsY[k] = (ptsY[k - 1] + (rnd.nextFloat() - 0.5f) * 62f * s).coerceIn(0f, h)
        }
        shapeIdx = i
        shapeN = n
        shapeT0 = t
        shapeOn = true
        hover = -1
    }

    /** How far the shape has traced, in stars (0..shapeN). */
    fun reach(): Float = ((t - shapeT0) / TRACE_TIME).coerceIn(0f, 1f) * shapeN

    /** Which shape star is under the thumb (only stars already traced), or -1. */
    fun hitShape(x: Float, y: Float): Int {
        val r = reach()
        var best = -1
        var bd = Float.MAX_VALUE
        for (k in 0 until shapeN) {
            if (r - k < 0.5f) continue
            val d = hypot(x - ptsX[k], y - ptsY[k])
            if (d < bd) { bd = d; best = k }
        }
        return if (bd > SHAPE_HIT * s) -1 else best
    }

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

    private fun step(dt: Float): Boolean {
        t += dt
        val live = touching && !sliding
        val actT = if (live) 1f else 0f
        act = damp(act, actT, 16f, dt)
        if (live) by = damp(by, fingerY, 24f, dt)
        var moving = abs(act - actT) > 0.01f
        // After you let go, the bright line fades out.
        if (!touching && fade > 0f) {
            fade -= dt / LINE_FADE
            if (fade <= 0f) { fade = 0f; pathLen = 0 }
        }
        val shT = if (shapeOn && touching) 1f else 0f
        shA = damp(shA, shT, 12f, dt)
        if (abs(shA - shT) > 0.005f) moving = true
        // While the shape traces out, keep drawing.
        if (shapeOn && touching && t - shapeT0 < TRACE_TIME) moving = true
        val fall = dt * (if (touching) 0.35f else 0.7f)
        for (i in 0 until N) {
            if (lit[i] > 0f) { lit[i] = max(0f, lit[i] - fall); moving = true }
            val gT = when {
                i == active && touching -> 1f
                has[i] -> 0.55f
                else -> 0.12f
            }
            glint[i] = damp(glint[i], gT, 10f, dt)
            if (abs(glint[i] - gT) > 0.005f) moving = true
        }
        for (k in 0 until MAX_SHAPE) {
            val hT = if (k == hover && touching) 1f else 0f
            hoverA[k] = damp(hoverA[k], hT, 18f, dt)
            if (abs(hoverA[k] - hT) > 0.01f) moving = true
        }
        return touching || fade > 0f || moving
    }
}

private fun damp(c: Float, target: Float, k: Float, dt: Float) = c + (target - c) * (1f - exp(-k * dt))

/** Most opened first (from LaunchStats, the last 14 days), then A–Z. */
private fun byUse(apps: List<AppEntry>, uses: Map<String, Int>): List<AppEntry> =
    apps.sortedWith(
        compareByDescending<AppEntry> { uses[it.component.packageName] ?: 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
    )

private fun DrawScope.drawConstellation(
    st: ConstellationState, letters: List<TextLayoutResult>, bubbles: List<TextLayoutResult>,
    railColour: Color, dimColour: Color, accent: Color, panel: Color, shadow: Shadow,
) {
    val s = st.s
    val glowBrush = st.glowBrush ?: return
    val glintBrush = st.glintBrush ?: return
    val glowShadow = st.glowShadow ?: return
    // The sleeping constellation.
    st.sleepStroke?.let { drawPath(st.sleepPath, GOLD.copy(alpha = 0.13f), style = it) }
    // The bright line through the stars you passed.
    if (st.pathLen > 1 && st.fade > 0f) {
        val al = min(1f, st.fade)
        st.linePath.reset()
        for (k in 0 until st.pathLen) {
            val i = st.path[k]
            if (k == 0) st.linePath.moveTo(st.starX[i], st.starY[i]) else st.linePath.lineTo(st.starX[i], st.starY[i])
        }
        st.glowStroke?.let { drawPath(st.linePath, GOLD.copy(alpha = 0.28f * 0.85f * al), style = it) }
        st.coreStroke?.let { drawPath(st.linePath, LIGHT.copy(alpha = 0.95f * al), style = it) }
    }
    // The held star's shape: a line traces from the star through one star per app, the names beside them.
    // (Drawn while it fades too, even after the thumb moved to another letter.)
    if (st.shapeN > 0 && st.shA > 0.01f) {
        val shA = st.shA
        val reach = st.reach()
        st.shapePath.reset()
        st.shapePath.moveTo(st.starX[st.shapeIdx], st.starY[st.shapeIdx])
        var fromX = st.starX[st.shapeIdx]
        var fromY = st.starY[st.shapeIdx]
        for (k in 0 until st.shapeN) {
            val u = (reach - k).coerceIn(0f, 1f)
            if (u <= 0f) break
            st.shapePath.lineTo(fromX + (st.ptsX[k] - fromX) * u, fromY + (st.ptsY[k] - fromY) * u)
            fromX = st.ptsX[k]; fromY = st.ptsY[k]
        }
        st.shapeStroke?.let { drawPath(st.shapePath, LIGHT.copy(alpha = 0.9f * shA), style = it) }
        val screenLeft = -st.railLeft
        for (k in 0 until st.shapeN) {
            val u = (reach - k).coerceIn(0f, 1f)
            if (u <= 0f) continue
            val q = Offset(st.ptsX[k], st.ptsY[k])
            val hv = st.hoverA[k]
            drawCircle(GOLD.copy(alpha = (0.25f * u + 0.35f * hv) * shA), radius = (9f * u + 6f * hv) * s, center = q)
            drawCircle(LIGHT.copy(alpha = 0.95f * u * shA), radius = (1.8f + 2.4f * u + 2f * hv) * s, center = q)
            // The name ends just left of its star (or starts right of it, if it would run off the screen).
            val label = st.shapeLabels[k] ?: continue
            var x = q.x - 6f * s - label.size.width
            if (x < screenLeft + 4f * s) x = q.x + 6f * s
            val y = q.y + 8f * s
            if (hv > 0.01f) {
                drawRoundRect(
                    NAME_PILL, topLeft = Offset(x - 7f * s, y - 2f * s),
                    size = Size(label.size.width + 14f * s, label.size.height + 4f * s),
                    cornerRadius = CornerRadius((label.size.height + 4f * s) / 2f), alpha = hv * shA,
                )
            }
            railText(label, topLeft = Offset(x, y), alpha = (u * shA).coerceIn(0f, 1f), shadow = shadow)
        }
    }
    // The stars: a four-pointed glint behind each letter; the one under the thumb lights up and steps out.
    for (i in 0 until N) {
        val has = st.has[i]
        val e = if (i == st.active && st.touching) 1f else 0f
        val v = max(st.lit[i] * 0.9f, e)
        val q = Offset(st.starX[i] - v * 8f * s, st.starY[i])
        val k = (if (has) 1f else 0.75f) * (1f + 0.5f * v)
        val tl = letters[i]
        val topLeft = Offset(q.x - tl.size.width / 2f, q.y - tl.size.height / 2f)
        val colour = when {
            i == st.active -> accent
            has -> railColour
            else -> dimColour
        }
        translate(q.x, q.y) {
            scale(k, Offset.Zero) { drawPath(st.glintPath, glintBrush, alpha = st.glint[i].coerceIn(0f, 1f)) }
        }
        scale(k, q) {
            if (v > 0.02f) {
                val g = v * 0.85f
                translate(q.x, q.y) { drawCircle(glowBrush, radius = 16f * s, center = Offset.Zero, alpha = g) }
                railText(tl, color = GLOW_TEXT, topLeft = topLeft, alpha = g.coerceIn(0f, 1f), shadow = glowShadow)
            }
            railText(tl, color = colour, topLeft = topLeft, shadow = shadow)
        }
    }
    // The letter bubble, 44 units left of the stars, following the thumb.
    val act = st.act
    if (act > 0.01f) {
        val q = Offset(st.lx - 44f * s, st.by)
        val r = 30f * s * (0.8f + 0.2f * act)
        drawCircle(panel.copy(alpha = 0.95f * act), radius = r, center = q)
        st.ring?.let { drawCircle(GOLD.copy(alpha = act), radius = r, center = q, style = it) }
        val tl = bubbles[st.bubbleIdx.coerceIn(0, N - 1)]
        railText(tl, color = LIGHT, topLeft = Offset(q.x - tl.size.width / 2f, q.y - tl.size.height / 2f), alpha = act)
    }
}
