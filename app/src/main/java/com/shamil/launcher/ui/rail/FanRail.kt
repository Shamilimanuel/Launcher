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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.LaunchStats
import com.shamil.launcher.ui.DOT
import com.shamil.launcher.ui.STAR
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * "Petal fan" (his pick #4, 24 Oct): touch a letter and the apps starting with it bloom as petals in a fan to the
 * left of your thumb. Slide onto a petal and let go to open it: letter to app in one move, no list scrolling.
 * At most 6 petals: the 5 he opens most, plus "+N more" (letting go on that one just leaves the list open there).
 *
 * The letter lock (the playground's fix): once the thumb is 8 dp left of the rail it keeps the letter, so reaching
 * for a petal can't switch letters by accident. On the rail there's a 10 dp dead zone too, so a wobble doesn't.
 * While petals show, there's no sliding onto the list: the fan replaces it. A letter with no petals still slides.
 * Numbers are the playground's (own-rail-ideas.html, mkFan(), a 390-wide phone), scaled to the real screen.
 */

private const val N = 29
private const val HOLD_MS = 600L
private val LETTERS_WIDTH = 28.dp
/** How far left of the rail the thumb must go before the letter locks. */
private val LOCK_GAP = 8.dp
/** On the rail, how far past a letter's edge the thumb may wobble before the letter changes. */
private val DEAD_ZONE = 10.dp
private const val MAX_PETALS = 6
/** The fan's radius, in playground units. */
private const val FAN_R = 118f
/** The angle between two petals: petals 74 units apart along the circle. */
private val PETAL_GAP = 2f * asin((37f / FAN_R).coerceIn(0f, 1f))
/** Half a petal (the icon is 46 units wide). */
private const val PETAL_HALF = 23f
/** How close the thumb must be to a petal's centre to pick it. */
private const val PETAL_HIT = 40f
/** Each next petal starts blooming this much later (seconds): the stagger. */
private const val STAGGER = 0.035f
private const val GO_TIME = 0.4f
private val GOLD = Color(0xFFF3D79C)
private val LIGHT = Color(0xFFFFF6DC)
private val GLOW_TEXT = Color(0xFFFFE9B0)
private val NAME_TEXT = Color(0xFFFFF1CE)
private val NAME_PILL = Color(0xB30E1226)   // the playground's rgba(14,18,38,.7)
private val MORE_DARK = Color(0xFFD3BC8E)
private val MORE_INK = Color(0xFF1C1830)

/**
 * The petal fan rail. The same as [com.shamil.launcher.ui.LetterRail] plus two things: [appsFor] gives the apps
 * filed under a rail item ("A".."Z", "#", and ☆ / • if you want petals there too; return an empty list for none),
 * and [onOpen] opens the app whose petal the thumb let go on.
 */
@Composable
fun PetalFanRail(
    present: Set<String>,
    appsFor: (String) -> List<AppEntry>,
    onOpen: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    /** While the thumb slides left off the rail onto the list (only on letters without petals): its screen y. */
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
    val st = remember { FanRailState(scope) }

    val measurer = rememberTextMeasurer()
    val letters = remember(look.font) {
        val style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 17.sp)
        RAIL_ITEMS.map { measurer.measure(it, style) }
    }
    val bubbles = remember(look.font) {
        val style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 28.sp)
        RAIL_ITEMS.map { measurer.measure(it, style) }
    }
    val dots = remember(look.font) {
        measurer.measure("…", TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 20.sp))
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

                /** A new letter: fill the petals with its apps, most used first. */
                fun choose(i: Int) {
                    st.active = i
                    st.bubbleIdx = i
                    st.hot = -1
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val item = RAIL_ITEMS[i]
                    currentOnPick(item)
                    val list = currentAppsFor(item)
                    // ☆ and • keep the order they come in (favourites, the launcher's own items); letters go by use.
                    val sorted = if (item == STAR || item == DOT) list else byUse(list, st.uses)
                    val many = sorted.size > MAX_PETALS
                    val shown = if (many) MAX_PETALS - 1 else sorted.size
                    st.more = if (many) sorted.size - shown else 0
                    st.n = shown + if (many) 1 else 0
                    val maxW = (96f * st.unit()).roundToInt().coerceAtLeast(1)
                    for (k in 0 until MAX_PETALS) {
                        st.apps[k] = if (k < shown) sorted[k] else null
                        val text = when {
                            k < shown -> sorted[k].label
                            k == shown && many -> "+${st.more} more"
                            else -> null
                        }
                        st.labels[k] = text?.let {
                            measurer.measure(it, nameStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxW))
                        }
                        // Every petal blooms again from the middle, one after the other.
                        st.appear[k] = 0f
                        st.appearV[k] = 0f
                    }
                    st.shownAt = st.t
                }

                fun follow(x: Float, y: Float) {
                    st.fingerX = x
                    st.fingerY = y.coerceIn(-size.height * 0.6f, size.height * 1.6f)
                    st.reaching = false
                    if (st.n > 0 && st.active >= 0) {
                        // The letter lock: left of the rail, or still close to the letter, keeps the letter.
                        val left = x < st.lx - lockPx
                        val near = abs(st.fingerY - (st.active + 0.5f) * st.slot) < st.slot / 2 + deadPx
                        if (!left) st.f = st.fingerY / st.slot - 0.5f
                        if (left || near) {
                            st.reaching = left
                            val k = if (left) st.hitPetal(x, y) else -1
                            if (k != st.hot) {
                                st.hot = k
                                if (k >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            return
                        }
                    } else {
                        // No petals here: today's slide-onto-the-list, past the middle of the screen.
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
                        st.f = st.fingerY / st.slot - 0.5f
                    }
                    st.hot = -1
                    val i = (st.fingerY / size.height * N).toInt().coerceIn(0, N - 1)
                    if (i != st.active) choose(i)
                }

                awaitEachGesture {
                    val down = awaitFirstDown().position
                    // How often each app was opened lately, read once per touch.
                    st.uses = LaunchStats.get(context).mostOpened().toMap()
                    st.touching = true
                    follow(down.x, down.y)
                    st.snapToFinger()
                    st.wake()
                    try {
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
                            // Reaching for a petal of • isn't "holding •": no vault this time.
                            if (st.reaching) dotSince = -1L
                            change.consume()
                        }
                        val k = st.hot
                        val app = if (k >= 0) st.apps[k] else null
                        if (k >= 0) { st.goK = k; st.goT = st.t }
                        currentOnRelease(st.sliding)
                        if (st.sliding) currentOnSlide(null)
                        // "+N more" has no app: letting go there just leaves the list open at the letter.
                        if (app != null) currentOnOpen(app)
                    } finally {
                        st.sliding = false
                        st.reaching = false
                        st.active = -1
                        st.hot = -1
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
                    st.layout(size.width, size.height, LETTERS_WIDTH.toPx() / 2, density)
                    drawFan(st, present, letters, bubbles, dots, railColour, dimColour, look.accent, look.panel, shadow)
                },
        )
    }
}

/** The fan's moving parts: plain fields read while drawing (only [tick] is Compose state). */
private class FanRailState(private val scope: CoroutineScope) {
    val tick = mutableIntStateOf(0)
    private var running = false

    var railTop = 0f
    var railLeft = 0f
    var s = 0f
    var h = 1f
    var lx = 0f
    var slot = 1f

    var touching = false
    var sliding = false
    /** True while the thumb is left of the rail, reaching for a petal. */
    var reaching = false
    var fingerX = 0f
    var fingerY = 0f
    var active = -1
    var bubbleIdx = 0
    var uses: Map<String, Int> = emptyMap()

    // The petals of the current letter.
    var n = 0
    var more = 0
    val apps = arrayOfNulls<AppEntry>(MAX_PETALS)
    val labels = arrayOfNulls<TextLayoutResult>(MAX_PETALS)
    /** How far each petal has bloomed (0 = hidden, 1 = open; a bit over 1 while it springs). */
    val appear = FloatArray(MAX_PETALS)
    val appearV = FloatArray(MAX_PETALS)
    /** How "hovered" each petal looks (0..1, smoothed). */
    val hotA = FloatArray(MAX_PETALS)
    var hot = -1
    var shownAt = 0f
    // The petal let go on: it swells and fades as its app opens.
    var goK = -1
    var goT = 0f

    /** The letter position the thumb is at (only follows while the thumb is on the rail). */
    var f = 0f
    /** The fan's smoothed centre height. */
    var fyS = 0f
    var act = 0f
    val glow = FloatArray(N)
    var t = 0f

    var glowBrush: Brush? = null
    var hotBrush: Brush? = null
    var moreBrush: Brush? = null
    var ring: Stroke? = null
    var hotRing: Stroke? = null
    var glowShadow: Shadow? = null
    var iconPx = 1

    /** One playground unit in px (a fallback before the first layout). */
    fun unit(): Float = if (s > 0f) s else 3f

    fun setScreenWidth(px: Float) {
        val ns = px / 390f
        if (ns != s) { s = ns; glowBrush = null; tick.intValue++ }
    }

    fun layout(width: Float, height: Float, letterCentre: Float, density: Float) {
        h = height; lx = letterCentre; slot = height / N
        if (s <= 0f) s = density
        if (glowBrush == null) {
            glowBrush = Brush.radialGradient(listOf(GOLD.copy(alpha = 0.45f), Color.Transparent), center = Offset.Zero, radius = 16f * s)
            // The hovered petal's glow: solid gold under the icon, fading out 28 units beyond its edge.
            hotBrush = Brush.radialGradient(
                0f to GOLD.copy(alpha = 0.9f), 0.45f to GOLD.copy(alpha = 0.9f), 1f to Color.Transparent,
                center = Offset.Zero, radius = 51f * s,
            )
            moreBrush = Brush.radialGradient(listOf(LIGHT, GOLD, MORE_DARK), center = Offset(-6f * s, -7f * s), radius = 30f * s)
            ring = Stroke(width = 2f * s)
            hotRing = Stroke(width = 3f * s)
            glowShadow = Shadow(color = GOLD, blurRadius = 10f * s)
            iconPx = (PETAL_HALF * 2f * s).roundToInt().coerceAtLeast(1)
        }
    }

    fun snapToFinger() {
        f = fingerY / slot - 0.5f
        fyS = fanTarget()
    }

    /** The fan's centre x: 30 units left of the letters. */
    fun cx() = lx - 30f * s

    /** Where the fan's centre wants to be: beside the letter, but kept far enough in that every petal fits. */
    fun fanTarget(): Float {
        val span = (n - 1).coerceAtLeast(0) * PETAL_GAP / 2f
        val margin = FAN_R * s * sin(max(0.4f, span).coerceAtMost((PI / 2).toFloat())) + 10f * s
        val y = (f.coerceIn(0f, N - 1f) + 0.5f) * slot
        return if (margin * 2 > h) h / 2 else y.coerceIn(margin, h - margin)
    }

    /** The angle of petal [k] around the fan's centre (pointing left = PI). */
    fun angle(k: Int) = PI.toFloat() + (k - (n - 1) / 2f) * PETAL_GAP

    /** Which petal is under the thumb (or -1). */
    fun hitPetal(x: Float, y: Float): Int {
        var best = -1
        var bd = Float.MAX_VALUE
        val r = FAN_R * s
        val c = cx()
        for (k in 0 until n) {
            val a = angle(k)
            val d = hypot(x - (c + r * cos(a)), y - (fyS + r * sin(a)))
            if (d < bd) { bd = d; best = k }
        }
        return if (bd > PETAL_HIT * s) -1 else best
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
        act = damp(act, if (touching && !sliding) 1f else 0f, 16f, dt)
        fyS = damp(fyS, fanTarget(), 20f, dt)
        var moving = abs(act - (if (touching && !sliding) 1f else 0f)) > 0.01f
        // The letters near the thumb lean out a little and glow.
        for (i in 0 until N) {
            glow[i] = bell(abs(i - f), 1.5f) * act
        }
        // The petals pop in on a bouncy spring, one after the other; they shrink away fast when you let go.
        val show = touching && !sliding
        val k = 420f
        val c = 2f * sqrt(k) * 0.55f   // under-damped: a little overshoot = the "pop"
        for (p in 0 until MAX_PETALS) {
            if (show && p < n) {
                if (t - shownAt >= p * STAGGER) {
                    appearV[p] += ((1f - appear[p]) * k - appearV[p] * c) * dt
                    appear[p] += appearV[p] * dt
                }
                if (abs(1f - appear[p]) > 0.002f || abs(appearV[p]) > 0.01f) moving = true
            } else {
                appearV[p] = 0f
                appear[p] = damp(appear[p], 0f, 20f, dt)
                if (appear[p] > 0.002f) moving = true else appear[p] = 0f
            }
            val hotT = if (show && p == hot) 1f else 0f
            hotA[p] = damp(hotA[p], hotT, 18f, dt)
            if (abs(hotA[p] - hotT) > 0.01f) moving = true
        }
        if (goK >= 0) {
            if (t - goT > GO_TIME) goK = -1 else moving = true
        }
        return touching || moving || abs(fyS - fanTarget()) > 0.5f
    }
}

private fun damp(c: Float, target: Float, k: Float, dt: Float) = c + (target - c) * (1f - exp(-k * dt))
private fun bell(d: Float, sigma: Float) = exp(-(d * d) / (2 * sigma * sigma))

/** Most opened first (from LaunchStats, the last 14 days), then A–Z. */
private fun byUse(apps: List<AppEntry>, uses: Map<String, Int>): List<AppEntry> =
    apps.sortedWith(
        compareByDescending<AppEntry> { uses[it.component.packageName] ?: 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
    )

private fun DrawScope.drawFan(
    st: FanRailState, present: Set<String>, letters: List<TextLayoutResult>, bubbles: List<TextLayoutResult>,
    dots: TextLayoutResult, railColour: Color, dimColour: Color, accent: Color, panel: Color, shadow: Shadow,
) {
    val s = st.s
    val glowBrush = st.glowBrush ?: return
    val glowShadow = st.glowShadow ?: return
    // The letters: the ones near the thumb lean out 14 units, grow a little and glow.
    for (i in 0 until N) {
        val item = RAIL_ITEMS[i]
        val has = item == STAR || item == DOT || item in present
        val b = st.glow[i]
        val q = Offset(st.lx - 14f * s * b, (i + 0.5f) * st.slot)
        val tl = letters[i]
        val topLeft = Offset(q.x - tl.size.width / 2f, q.y - tl.size.height / 2f)
        val colour = when {
            i == st.active -> accent
            has -> railColour
            else -> dimColour
        }
        scale(1f + 0.3f * b, q) {
            if (b > 0.02f) {
                val g = if (has) b * 0.9f else b * 0.45f
                translate(q.x, q.y) { drawCircle(glowBrush, radius = 16f * s, center = Offset.Zero, alpha = g) }
                drawText(tl, color = GLOW_TEXT, topLeft = topLeft, alpha = g.coerceIn(0f, 1f), shadow = glowShadow)
            }
            drawText(tl, color = colour, topLeft = topLeft, shadow = shadow)
        }
    }
    val cx = st.cx()
    // The letter bubble sits in the middle of the fan; the petals ring around it.
    val act = st.act
    if (act > 0.01f) {
        val q = Offset(cx, st.fyS)
        val r = 30f * s * 0.85f
        drawCircle(panel.copy(alpha = 0.95f * act), radius = r, center = q)
        st.ring?.let { drawCircle(GOLD.copy(alpha = act), radius = r, center = q, style = it) }
        val tl = bubbles[st.bubbleIdx.coerceIn(0, N - 1)]
        drawText(tl, color = LIGHT, topLeft = Offset(q.x - tl.size.width / 2f, q.y - tl.size.height / 2f), alpha = act)
    }
    // The petals.
    val r = FAN_R * s
    for (k in 0 until min(st.n, MAX_PETALS)) {
        val going = k == st.goK
        val ap = if (going) 1f else st.appear[k]
        if (ap < 0.01f) continue
        val u = if (going) ((st.t - st.goT) / GO_TIME).coerceIn(0f, 1f) else 0f
        val a = st.angle(k)
        val px = cx + r * cos(a) * ap
        val py = st.fyS + r * sin(a) * ap
        val hot = st.hotA[k]
        val sc = if (going) 1.3f * (1f + 0.4f * u) else ap * (1f + 0.3f * hot)
        val alpha = if (going) 1f - u else ap.coerceIn(0f, 1f)
        translate(px, py) {
            scale(sc, Offset.Zero) {
                val glowA = if (going) alpha else hot * alpha
                if (glowA > 0.01f) {
                    st.hotBrush?.let { drawCircle(it, radius = 51f * s, center = Offset.Zero, alpha = glowA) }
                    st.hotRing?.let { drawCircle(LIGHT.copy(alpha = glowA), radius = (PETAL_HALF + 1.5f) * s, center = Offset.Zero, style = it) }
                }
                val app = st.apps[k]
                if (app != null) {
                    val half = st.iconPx / 2
                    drawImage(
                        image = app.icon,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(app.icon.width, app.icon.height),
                        dstOffset = IntOffset(-half, -half),
                        dstSize = IntSize(st.iconPx, st.iconPx),
                        alpha = alpha,
                        filterQuality = FilterQuality.Low,
                    )
                } else {
                    // "+N more": a gold petal with "…".
                    st.moreBrush?.let { drawCircle(it, radius = PETAL_HALF * s, center = Offset.Zero, alpha = alpha) }
                    drawText(dots, color = MORE_INK, topLeft = Offset(-dots.size.width / 2f, -dots.size.height / 2f), alpha = alpha)
                }
                // The name in a small dark pill under the icon.
                val label = st.labels[k]
                if (label != null) {
                    val pw = label.size.width + 14f * s
                    val ph = label.size.height + 4f * s
                    val top = (PETAL_HALF + 6f) * s
                    drawRoundRect(NAME_PILL, topLeft = Offset(-pw / 2f, top), size = Size(pw, ph), cornerRadius = CornerRadius(ph / 2f), alpha = alpha)
                    drawText(label, topLeft = Offset(-label.size.width / 2f, top + 2f * s), alpha = alpha, shadow = shadow)
                }
            }
        }
    }
}
