package com.shamil.launcher.ui.vault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Fun lock: Lantern lock (24 Oct, from the PIN looks page). Four rows of Liyue paper lanterns; each row is an endless
 * ring of six symbols. He swipes a row sideways (it snaps, with a little fling) or taps a neighbour to turn it; the
 * lantern in the gold frame is the chosen one. "Light the lanterns" sends the four ids joined by commas, for example
 * "star,moon,flower,wave". Right: they light up warm and float away. Wrong: they flicker dark and shake, then the rows
 * spin to new places (that's the "start again"). The lock never checks the code itself; the vault answers with
 * wrongTick or okTick. Sizes come from the page's 390-wide phone, scaled to this screen.
 */

/** One symbol: the id that goes in the code, and its drawing as SVG path data on a 24-unit grid. */
private class LanternGlyph(val id: String, d: String, extra: String? = null, val petals: Boolean = false) {
    val path: Path = PathParser().parsePathString(d).toPath()
    val extra: Path? = extra?.let { PathParser().parsePathString(it).toPath() }
}

/** The six symbols, in ring order. Paths copied from the page's icons (the flower's petal is drawn five times, turned). */
private fun lanternGlyphs() = listOf(
    LanternGlyph("star", "M12 3.2l2.6 5.5 6 .8-4.4 4.2 1.1 6-5.3-2.9-5.3 2.9 1.1-6L3.4 9.5l6-.8z"),
    LanternGlyph("moon", "M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z"),
    LanternGlyph("flower", "M9.4 6.6a2.6 3.4 0 1 0 5.2 0a2.6 3.4 0 1 0-5.2 0z", extra = "M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z", petals = true),
    LanternGlyph("wave", "M2.5 9c2.2-2.6 4.4-2.6 6.5 0s4.3 2.6 6.5 0 3.4-2.1 5-.6M2.5 15c2.2-2.6 4.4-2.6 6.5 0s4.3 2.6 6.5 0 3.4-2.1 5-.6"),
    LanternGlyph("sun", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0z",
        extra = "M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8"),
    LanternGlyph("leaf", "M5 19c0-8 4.5-13.5 14-14 0 9-5 14-14 14z", extra = "M5 19l7-7"),
)

private val LanternGlyphLine = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val LanternGold = Color(0xFFF3D79C)
private val LanternPaleGold = Color(0xFFD3BC8E)
private val LanternBad = Color(0xFFFF8E9E)
private val LanternShakeSteps = floatArrayOf(-1f, 0.8f, -0.55f, 0.3f, 0f)
/** Lantern spacing in page pixels. */
private const val LANTERN_PITCH = 84f

/** The page's snap: ease out, with the tiniest overshoot in the middle. */
private val LanternSnap = Easing { p -> 1f - (1f - p).pow(3) + sin(p * PI.toFloat()) * 0.04f }

/** One row (a ring of six). [pos] = which lantern is centred (it may run past 6; it wraps). */
private class LanternRing(start: Int) {
    val pos = Animatable(start.toFloat())
    var dragging by mutableStateOf(false)
    var dragPos by mutableFloatStateOf(start.toFloat())
    val lit = Animatable(0f)
    val dark = Animatable(0f)
    val shake = Animatable(0f)
    val rise = Animatable(0f)

    val shown: Float get() = if (dragging) dragPos else pos.value

    /** The symbol in the frame: where the row is heading, so a press mid-spin counts the lantern it lands on. */
    fun chosen(): Int = (if (dragging) dragPos else pos.targetValue).roundToInt().mod(6)
}

/** A burst of warm sparks over one row when it lights. */
private class LanternSpark {
    val t = Animatable(1f)
    var x = 0f
    var y = 0f
    var seed = 0
}

/** The whole lock's moving parts, kept together so gestures and effects share them. */
private class LanternState {
    val rings = List(4) { LanternRing(Random.nextInt(6)) }
    val sparks = List(4) { LanternSpark() }
    var busy by mutableStateOf(false)
    var bad by mutableStateOf(false)
    /** 1 = normal, lower = the dark flicker after a wrong try. */
    var flick by mutableFloatStateOf(1f)
    /** Starts again if the vault never answers (for example the first step of setting it up). */
    var fallback: Job? = null

    /** Spins every row to a new random place (the lantern lock's "clear"). */
    fun scramble(scope: CoroutineScope) {
        rings.forEach { r ->
            val steps = (2 + Random.nextInt(3)) * (if (Random.nextBoolean()) 1 else -1)
            val target = r.pos.value.roundToInt() + steps
            scope.launch { r.pos.animateTo(target.toFloat(), tween(520, easing = LanternSnap)) }
        }
    }
}

/** A cheap repeatable "random" number from 0 to 1 (a hash), so sparks don't need stored random values. */
private fun lanternNoise(seed: Int, i: Int): Float {
    var h = seed * 374761393 + i * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777216f
}

@Composable
fun LanternLock(
    title: String,
    sub: String,
    codeLength: Int,
    wrongTick: Int,
    okTick: Int,
    onCode: (String) -> Unit,
    onSuccessShown: () -> Unit,
    onFinger: (() -> Unit)?,
    onUsePin: (() -> Unit)?,
) {
    val look = LocalLauncherLook.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val latestOnCode by rememberUpdatedState(onCode)
    val latestOnShown by rememberUpdatedState(onSuccessShown)
    val glyphs = remember { lanternGlyphs() }
    val state = remember { LanternState() }
    val firstWrong = remember { wrongTick }
    val firstOk = remember { okTick }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = maxWidth / 390f
        val unit = with(density) { u.toPx() }
        val widthPx = constraints.maxWidth.toFloat()

        fun spark(row: Int) {
            val s = state.sparks[row]
            s.x = widthPx / 2f
            s.y = unit * (row * 104f + 52f)
            s.seed += 7919
            scope.launch { s.t.snapTo(0f); s.t.animateTo(1f, tween(760, easing = LinearEasing)) }
        }

        fun light() {
            if (state.busy) return
            state.busy = true
            state.bad = false
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            latestOnCode(state.rings.joinToString(",") { glyphs[it.chosen()].id })
            state.fallback = scope.launch {
                delay(if (codeLength > 0) 1200L else 450L)
                state.scramble(scope)
                state.busy = false
            }
        }

        // Wrong: the lanterns go dark, flicker and shake one after another, then the rows spin to new places.
        LaunchedEffect(wrongTick) {
            if (wrongTick == firstWrong) return@LaunchedEffect
            state.fallback?.cancel()
            state.busy = true
            state.bad = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            state.rings.forEachIndexed { i, r ->
                launch { r.dark.animateTo(1f, tween(400)) }
                launch { delay(i * 60L); for (k in LanternShakeSteps) r.shake.animateTo(k * 8f, tween(84)) }
            }
            launch { repeat(3) { state.flick = 0.45f; delay(250); state.flick = 1f; delay(250) } }
            delay(1150)
            state.rings.forEach { r -> launch { r.dark.animateTo(0f, tween(450)) } }
            state.scramble(scope)
            state.bad = false
            state.busy = false
        }

        // Right: row by row they light up warm (with sparks), then all float up and away. About a second.
        LaunchedEffect(okTick) {
            if (okTick == firstOk) return@LaunchedEffect
            state.fallback?.cancel()
            state.busy = true
            state.bad = false
            state.rings.forEachIndexed { i, r ->
                launch { r.lit.animateTo(1f, tween(450)) }
                spark(i)
                delay(70)
            }
            delay(160)
            state.rings.forEachIndexed { i, r -> launch { delay(i * 50L); r.rise.animateTo(1f, tween(650, easing = FastOutLinearInEasing)) } }
            delay(830L)   // the last row's float (50 ms late x 3 + 650 ms), then a beat
            latestOnShown()
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            LanternHeader(title, sub, state.bad)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(u * 4f)) {
                    state.rings.forEach { ring -> LanternRow(ring, state, glyphs, unit, u.value) }
                }
                Spacer(Modifier.matchParentSize().drawBehind { drawLanternSparks(state, unit) })
            }
            Spacer(Modifier.height(u * 18f))
            // "Light the lanterns": the gold pill that sends the four symbols.
            val press = remember { MutableInteractionSource() }
            val ready = animateFloatAsState(if (state.busy) 0.5f else 1f, tween(200), label = "light button")
            Box(
                Modifier.width(u * 220f).height(54.dp)
                    .graphicsLayer { alpha = ready.value }
                    .pressFeel(press, look.accent, 27.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(look.accent, lerp(look.accent, Color.Black, 0.14f))))
                    .clickable(press, null) { light() },
                contentAlignment = Alignment.Center,
            ) {
                Text("Light the lanterns", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                    letterSpacing = 0.06.em, color = look.sheet.copy(alpha = 1f)))
            }
            Spacer(Modifier.height(18.dp))
            LanternFooter(onFinger, onUsePin)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * One row: swipe it sideways and it follows the finger, then snaps to the nearest lantern (a quick flick carries it one
 * or two further); a tap on a side lantern turns it to the middle. Drawn from the ring's values, so turning it never
 * recomposes. [uDp] = one page pixel in dp.
 */
@Composable
private fun LanternRow(ring: LanternRing, state: LanternState, glyphs: List<LanternGlyph>, unit: Float, uDp: Float) {
    val look = LocalLauncherLook.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val accent = look.accent
    Spacer(
        Modifier
            .fillMaxWidth()
            .height((100f * uDp).dp)
            .graphicsLayer {
                // Wrong: shake. Right: float up (fast, then faster) and fade.
                val r = ring.rise.value
                translationX = ring.shake.value * unit
                translationY = -unit * (if (r < 0.4f) 70f * (r / 0.4f) else 70f + 190f * ((r - 0.4f) / 0.6f))
                alpha = if (r < 0.4f) 1f else 1f - (r - 0.4f) / 0.6f
            }
            .pointerInput(ring, unit) {
                val pitch = LANTERN_PITCH * unit
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (state.busy) return@awaitEachGesture
                    val start = ring.shown
                    ring.dragPos = start
                    ring.dragging = true
                    scope.launch { ring.pos.stop() }
                    val x0 = down.position.x
                    var lastX = x0
                    var lastT = down.uptimeMillis
                    var upX = x0
                    var upT = down.uptimeMillis
                    var v = 0f
                    var moved = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { upX = change.position.x; upT = change.uptimeMillis; break }
                        val x = change.position.x
                        moved = max(moved, abs(x - x0))
                        v = (x - lastX) / max(8L, change.uptimeMillis - lastT).toFloat()
                        lastX = x
                        lastT = change.uptimeMillis
                        upX = x
                        upT = lastT
                        ring.dragPos = start - (x - x0) / pitch
                        if (moved > 7f * unit) change.consume()
                    }
                    val target = if (moved < 7f * unit) {
                        // A tap: turn the tapped lantern to the middle.
                        ring.dragPos.roundToInt() + ((upX - size.width / 2f) / pitch).roundToInt()
                    } else {
                        // A swipe: a quick flick carries it on (speed in page pixels per ms, like the page).
                        val fling = if (upT - lastT > 90) 0f else (v / unit * 2.2f).coerceIn(-1.6f, 1.6f)
                        (ring.dragPos - fling).roundToInt()
                    }
                    scope.launch {
                        ring.pos.snapTo(ring.dragPos)
                        ring.dragging = false
                        ring.pos.animateTo(target.toFloat(), tween(320, easing = LanternSnap))
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            .drawWithCache {
                val line = Brush.horizontalGradient(
                    0f to Color.Transparent, 0.5f to LanternPaleGold.copy(alpha = 0.5f), 1f to Color.Transparent,
                    startX = 0f, endX = size.width,
                )
                val kit = LanternKit()
                // The gold frame: four rounded corner brackets around the middle lantern (96 x 94 page pixels).
                val frame = lanternFramePath(size.width / 2f - 48f * unit, 3f * unit, 96f * unit, 94f * unit, 16f * unit, 7f * unit)
                val frameLine = Stroke(3f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    drawLine(line, Offset(0f, 4f * unit), Offset(size.width, 4f * unit), strokeWidth = 1.dp.toPx())
                    drawLanternRing(ring, state.flick, kit, glyphs, unit)
                    drawPath(frame, accent.copy(alpha = 0.95f), style = frameLine)
                }
            }
    )
}

/** The lantern's brushes, built once in lantern-local page pixels (centre of the lantern at 0, 0). */
private class LanternKit {
    val body = Brush.radialGradient(0f to Color(0xFF3A4272), 0.7f to Color(0xFF222A4C), center = Offset(0f, -9f), radius = 49f)
    val litBody = Brush.radialGradient(0f to Color(0xFFFFF1C4), 0.75f to Color(0xFFF6B25E), center = Offset(0f, -5.75f), radius = 48f)
    val darkBody = Brush.radialGradient(0f to Color(0xFF2A2F4E), 1f to Color(0xFF171C33), center = Offset(0f, -9f), radius = 49f)
    val glow = Brush.radialGradient(0.45f to Color(0xCCFFBE64), 1f to Color.Transparent, center = Offset(0f, -2.5f), radius = 62f)
    val rim = Stroke(1.5f)
}

/** Four rounded corner brackets (like a camera's focus frame) around the box at (left, top). */
private fun lanternFramePath(left: Float, top: Float, w: Float, h: Float, arm: Float, round: Float): Path {
    val p = Path()
    val right = left + w
    val bottom = top + h
    val k = round * 0.45f
    // Top-left, top-right, bottom-right, bottom-left: down the side, round the corner, along the edge.
    p.moveTo(left, top + arm); p.lineTo(left, top + round)
    p.cubicTo(left, top + round - k, left + round - k, top, left + round, top); p.lineTo(left + arm, top)
    p.moveTo(right - arm, top); p.lineTo(right - round, top)
    p.cubicTo(right - round + k, top, right, top + round - k, right, top + round); p.lineTo(right, top + arm)
    p.moveTo(right, bottom - arm); p.lineTo(right, bottom - round)
    p.cubicTo(right, bottom - round + k, right - round + k, bottom, right - round, bottom); p.lineTo(right - arm, bottom)
    p.moveTo(left + arm, bottom); p.lineTo(left + round, bottom)
    p.cubicTo(left + round - k, bottom, left, bottom - round + k, left, bottom - round); p.lineTo(left, bottom - arm)
    return p
}

/**
 * The six lanterns of one ring. The centred one is full size; the others shrink, drop a little and fade with distance,
 * and the far ones are drawn first so the near ones sit on top.
 */
private fun DrawScope.drawLanternRing(ring: LanternRing, flick: Float, kit: LanternKit, glyphs: List<LanternGlyph>, unit: Float) {
    val p = ring.shown
    val lit = ring.lit.value
    val dark = ring.dark.value
    val cx0 = size.width / 2f
    for (pass in 0..2) {
        for (i in 0 until 6) {
            val x = i - p
            val d = x - 6f * floor(x / 6f + 0.5f)   // wrap round: -3 to 3
            val ab = abs(d)
            val layer = if (ab > 1.5f) 0 else if (ab > 0.5f) 1 else 2
            if (layer != pass) continue
            val s = 1f - 0.17f * min(ab, 2.5f)
            val op = if (ab <= 1f) 1f - 0.3f * ab else max(0f, 0.7f - (ab - 1f) * 0.7f)
            if (op <= 0.01f) continue
            val cx = cx0 + d * LANTERN_PITCH * unit
            val cy = (52f + ab * ab * 3f) * unit
            drawLantern(kit, glyphs[i], cx, cy, s * unit, op * (if (dark > 0f) flick else 1f), lit, dark)
        }
    }
}

/** One paper lantern 70 x 84 page pixels, centred on (cx, cy) and scaled by [k]: cap, body with ribs, tassel, symbol. */
private fun DrawScope.drawLantern(kit: LanternKit, glyph: LanternGlyph, cx: Float, cy: Float, k: Float, alpha: Float, lit: Float, dark: Float) {
    withTransform({
        translate(cx, cy)
        scale(k, k, Offset.Zero)
    }) {
        val bodyTopLeft = Offset(-30f, -35f)
        val bodySize = Size(60f, 65f)
        val corner = CornerRadius(30f, 26f)
        if (lit > 0f) drawCircle(kit.glow, radius = 62f, center = Offset(0f, -2.5f), alpha = alpha * lit)
        drawRoundRect(LanternPaleGold, topLeft = Offset(-12f, -42f), size = Size(24f, 7f), cornerRadius = CornerRadius(3f), alpha = alpha)
        drawRoundRect(LanternGold, topLeft = Offset(-2f, 34f), size = Size(4f, 6f), cornerRadius = CornerRadius(2f), alpha = alpha)
        drawRoundRect(kit.body, topLeft = bodyTopLeft, size = bodySize, cornerRadius = corner, alpha = alpha)
        if (lit > 0f) drawRoundRect(kit.litBody, topLeft = bodyTopLeft, size = bodySize, cornerRadius = corner, alpha = alpha * lit)
        if (dark > 0f) drawRoundRect(kit.darkBody, topLeft = bodyTopLeft, size = bodySize, cornerRadius = corner, alpha = alpha * dark)
        // The two paper ribs.
        val rib = LanternPaleGold.copy(alpha = 0.4f)
        drawLine(rib, Offset(-12f, -32f), Offset(-12f, 27f), strokeWidth = 1f, alpha = alpha)
        drawLine(rib, Offset(12f, -32f), Offset(12f, 27f), strokeWidth = 1f, alpha = alpha)
        drawRoundRect(LanternPaleGold.copy(alpha = 0.75f), topLeft = bodyTopLeft, size = bodySize, cornerRadius = corner, style = kit.rim, alpha = alpha)
        val ink = lerp(lerp(LanternGold, Color(0xFF6B3A10), lit), Color(0xFF6E7390), dark)
        drawLanternGlyph(glyph, Offset(0f, -2.5f), 30f, ink, alpha)
    }
}

/** Draws a symbol [sizePx] wide, centred on [center]. */
private fun DrawScope.drawLanternGlyph(glyph: LanternGlyph, center: Offset, sizePx: Float, color: Color, alpha: Float) {
    val k = sizePx / 24f
    withTransform({
        translate(center.x - sizePx / 2f, center.y - sizePx / 2f)
        scale(k, k, Offset.Zero)
    }) {
        if (glyph.petals) {
            for (n in 0 until 5) rotate(72f * n, pivot = Offset(12f, 12f)) { drawPath(glyph.path, color, alpha = alpha, style = LanternGlyphLine) }
        } else {
            drawPath(glyph.path, color, alpha = alpha, style = LanternGlyphLine)
        }
        glyph.extra?.let { drawPath(it, color, alpha = alpha, style = LanternGlyphLine) }
    }
}

/** The warm sparks over each row as it lights. */
private fun DrawScope.drawLanternSparks(state: LanternState, unit: Float) {
    for (s in state.sparks) {
        val t = s.t.value
        if (t >= 1f) continue
        for (i in 0 until 9) {
            val life = 0.55f + 0.45f * lanternNoise(s.seed, i * 3)
            val q = (t / life).coerceAtMost(1f)
            if (q >= 1f) continue
            val e = 1f - (1f - q) * (1f - q) * (1f - q)
            val a = lanternNoise(s.seed, i * 3 + 1) * 6.2832f
            val d = 50f * unit * (0.4f + 0.6f * lanternNoise(s.seed, i * 3 + 2))
            val p = Offset(s.x + cos(a) * d * e, s.y + (sin(a) * d - 6f * unit) * e)
            val sz = 1f - 0.8f * q
            drawCircle(Color(0xFFFFD98A).copy(alpha = 0.35f * (1f - q)), radius = 5f * unit * sz, center = p)
            drawCircle(Color(0xFFFFD98A).copy(alpha = 1f - q), radius = 2.5f * unit * sz, center = p)
        }
    }
}

/** The calm header from Compact glass: lock, a small spaced Cinzel title, the line under it (red while it says no). */
@Composable
private fun LanternHeader(title: String, sub: String, bad: Boolean) {
    val look = LocalLauncherLook.current
    Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = look.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(title.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Medium, color = look.accent, fontSize = 17.sp,
                letterSpacing = 0.2.em, shadow = Shadow(look.accent.copy(alpha = 0.35f), blurRadius = 16f)))
        Spacer(Modifier.height(5.dp))
        Text(sub, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = look.font, color = if (bad) LanternBad else look.textDim, fontSize = 12.5.sp))
    }
}

/** Fingerprint (when it's on) and a small "Use PIN" under it. */
@Composable
private fun LanternFooter(onFinger: (() -> Unit)?, onUsePin: (() -> Unit)?) {
    val look = LocalLauncherLook.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (onFinger != null) {
            val press = remember { MutableInteractionSource() }
            Box(
                Modifier.size(56.dp).pressFeel(press, look.accent, 28.dp).clip(CircleShape).background(Color(0x590B0F19))
                    .border(1.dp, look.accent.copy(alpha = 0.4f), CircleShape).clickable(press, null, onClick = onFinger),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_touch_app), "Fingerprint", tint = look.accent, modifier = Modifier.size(26.dp)) }
        }
        if (onUsePin != null) {
            val press = remember { MutableInteractionSource() }
            Text("Use PIN",
                modifier = Modifier.padding(top = 8.dp).pressFeel(press, look.accent, 16.dp).clip(CircleShape)
                    .clickable(press, null, onClick = onUsePin).padding(horizontal = 14.dp, vertical = 8.dp),
                style = TextStyle(fontFamily = look.font, color = look.accent.copy(alpha = 0.85f), fontSize = 13.sp, letterSpacing = 0.04.em))
        }
    }
}
