package com.shamil.launcher.ui.vault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Fun lock: Catch the seelies (24 Oct, from the PIN looks page). Six coloured seelie orbs drift slowly around the lower
 * two-thirds of the screen; he catches (taps) them in his secret colour order. The code is the colour ids joined by
 * commas, for example "gold,pink,blue,green", sent by itself on the 4th catch. A caught seelie bursts and comes back
 * somewhere else after 0.75 s. Wrong: they all dim and flee fast, then calm down. Right: they gather into one big light
 * that rises away. The lock never checks the code itself; the vault answers with wrongTick or okTick.
 */

/** The six seelies: the id that goes in the code, and their colour. */
private class SeelieKind(val id: String, val color: Color)

private val SeelieKinds = listOf(
    SeelieKind("gold", Color(0xFFF3D79C)),
    SeelieKind("pink", Color(0xFFF5A3C7)),
    SeelieKind("blue", Color(0xFF8CC8FF)),
    SeelieKind("green", Color(0xFF9BE3B0)),
    SeelieKind("violet", Color(0xFFC7A6FF)),
    SeelieKind("white", Color(0xFFDFF7FF)),
)

/** The colour of the one big seelie they merge into on a right code. */
private val SeelieMergedColor = Color(0xFFFFF6DC)
private val SeelieBad = Color(0xFFFF8E9E)

private const val SEELIE_DRIFT = 0
private const val SEELIE_FLEE = 1
private const val SEELIE_GATHER = 2
private const val SEELIE_MERGED = 3
private const val SEELIE_SPARKS = 48
private const val SEELIE_TAU = 6.2831855f

/**
 * Everything that moves, kept in plain arrays and changed in place by the frame loop (no new objects per frame).
 * Sizes are in screen pixels; [unit] is one page pixel (the page is a 390-wide phone).
 */
private class SeelieField {
    val x = FloatArray(6)
    val y = FloatArray(6)
    val heading = FloatArray(6)
    val speed = FloatArray(6)
    /** 0 = invisible, 1 = fully there (fades over 0.3 s, like the page's opacity transition). */
    val fade = FloatArray(6)
    val gone = BooleanArray(6)
    /** When a caught seelie comes back (frame time in nanoseconds). */
    val backAt = LongArray(6)
    var placed = false
    var w = 0f
    var h = 0f
    var unit = 1f
    var mode = SEELIE_DRIFT
    var dim = 0f
    var dimTarget = 0f
    var now = 0L
    private var lastFrame = 0L
    /** Bumped every frame; the field's drawing reads it, so it redraws without recomposing. */
    val frame = mutableLongStateOf(0L)

    // Sparks: a fixed pool, reused round-robin. start = 0 means unused.
    val sparkStart = LongArray(SEELIE_SPARKS)
    val sparkX = FloatArray(SEELIE_SPARKS)
    val sparkY = FloatArray(SEELIE_SPARKS)
    val sparkAngle = FloatArray(SEELIE_SPARKS)
    val sparkReach = FloatArray(SEELIE_SPARKS)
    val sparkLife = FloatArray(SEELIE_SPARKS)
    val sparkColor = IntArray(SEELIE_SPARKS)
    private var nextSpark = 0

    val merge = Animatable(0f)
    val rise = Animatable(0f)
    val ripple = Animatable(1f)

    var seq by mutableStateOf(emptyList<String>())
    var busy by mutableStateOf(false)
    var bad by mutableStateOf(false)
    /** Clears the entry if the vault never answers (for example the first step of setting it up). */
    var fallback: Job? = null
    var lastSeq: List<String> = emptyList()

    fun place(i: Int) {
        val x0 = 40f * unit
        val y0 = 30f * unit
        x[i] = x0 + Random.nextFloat() * max(1f, w - 2 * x0)
        y[i] = y0 + Random.nextFloat() * max(1f, h - 2 * y0)
    }

    /** One frame: drift with a little wander, bounce off the edges, fade in and out. */
    fun step(nowNs: Long) {
        val dt = if (lastFrame == 0L) 0f else ((nowNs - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrame = nowNs
        now = nowNs
        if (w <= 0f) return
        if (!placed) {
            for (i in 0 until 6) {
                place(i)
                heading[i] = Random.nextFloat() * SEELIE_TAU
                speed[i] = 14f + Random.nextFloat() * 12f
            }
            placed = true
        }
        val x0 = 40f * unit
        val x1 = w - x0
        val y0 = 30f * unit
        val y1 = h - y0
        for (i in 0 until 6) {
            if (gone[i] && nowNs >= backAt[i]) {
                // Back somewhere else, but only while things are calm.
                if (mode == SEELIE_DRIFT) { place(i); gone[i] = false } else backAt[i] = nowNs + 100_000_000L
            }
            when (mode) {
                SEELIE_GATHER -> {
                    val k = min(1f, dt * 5.5f)
                    x[i] += (w / 2f - x[i]) * k
                    y[i] += (h / 2f - y[i]) * k
                }
                SEELIE_MERGED -> Unit
                else -> {
                    val sp = speed[i] * unit * (if (mode == SEELIE_FLEE) 7f else 1f)
                    heading[i] += (Random.nextFloat() - 0.5f) * dt * 2.2f
                    x[i] += cos(heading[i]) * sp * dt
                    y[i] += sin(heading[i]) * sp * dt
                    if (x[i] < x0) { x[i] = x0; heading[i] = PI.toFloat() - heading[i] }
                    if (x[i] > x1) { x[i] = x1; heading[i] = PI.toFloat() - heading[i] }
                    if (y[i] < y0) { y[i] = y0; heading[i] = -heading[i] }
                    if (y[i] > y1) { y[i] = y1; heading[i] = -heading[i] }
                }
            }
            val want = if (gone[i] || mode == SEELIE_MERGED) 0f else 1f
            fade[i] = seelieApproach(fade[i], want, dt / 0.3f)
        }
        dim = seelieApproach(dim, dimTarget, dt / 0.3f)
    }

    /** [count] sparks flying out from (cx, cy); [color] = a seelie index, or -1 for all six colours. */
    fun burst(cx: Float, cy: Float, color: Int, count: Int, reach: Float) {
        for (k in 0 until count) {
            val s = nextSpark
            nextSpark = (nextSpark + 1) % SEELIE_SPARKS
            sparkStart[s] = if (now == 0L) 1L else now
            sparkX[s] = cx
            sparkY[s] = cy
            sparkAngle[s] = Random.nextFloat() * SEELIE_TAU
            sparkReach[s] = reach * (0.4f + 0.6f * Random.nextFloat())
            sparkLife[s] = 420f + 340f * Random.nextFloat()
            sparkColor[s] = if (color < 0) k % 6 else color
        }
    }
}

private fun seelieApproach(value: Float, target: Float, step: Float): Float =
    if (value < target) min(target, value + step) else max(target, value - step)

@Composable
fun SeelieCatchLock(
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
    // Always 4 catches; the parameter is kept so all the fun locks look the same from outside.
    val need by rememberUpdatedState(if (codeLength > 0) codeLength else 4)
    val setup by rememberUpdatedState(codeLength == 0)
    val field = remember { SeelieField() }
    val firstWrong = remember { wrongTick }
    val firstOk = remember { okTick }
    val touchRadius = with(density) { 30.dp.toPx() }

    // The frame loop: only while this screen is shown (it stops when the lock leaves the screen).
    LaunchedEffect(field) {
        val onFrame: (Long) -> Unit = { now ->
            field.step(now)
            field.frame.longValue = now
        }
        while (true) withFrameNanos(onFrame)
    }

    fun submit() {
        field.busy = true
        val entry = field.seq
        field.lastSeq = entry
        scope.launch {
            delay(340)   // let the burst show first
            latestOnCode(entry.joinToString(","))
            field.fallback = scope.launch {
                delay(if (setup) 450L else 1200L)
                field.seq = emptyList()
                field.busy = false
            }
        }
    }

    /** A finger came down at [p]: catch the nearest seelie within reach (about 60 dp across). */
    fun catchAt(p: Offset) {
        if (field.busy || field.mode != SEELIE_DRIFT) return
        var best = -1
        var bestDistance = max(touchRadius, 30f * field.unit)
        for (i in 0 until 6) {
            if (field.gone[i] || field.fade[i] < 0.5f) continue
            val d = hypot(p.x - field.x[i], p.y - field.y[i])
            if (d < bestDistance) { bestDistance = d; best = i }
        }
        if (best < 0) return
        field.seq = field.seq + SeelieKinds[best].id
        field.bad = false
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        field.burst(field.x[best], field.y[best], best, 10, 44f * field.unit)
        field.gone[best] = true
        field.backAt[best] = field.now + 750_000_000L
        if (field.seq.size >= need) submit()
    }

    // Wrong: the slots go red, the seelies dim and flee fast for a moment, then calm down and it's clear again.
    LaunchedEffect(wrongTick) {
        if (wrongTick == firstWrong) return@LaunchedEffect
        field.fallback?.cancel()
        field.busy = true
        field.bad = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        field.mode = SEELIE_FLEE
        field.dimTarget = 1f
        delay(750)
        field.mode = SEELIE_DRIFT
        field.dimTarget = 0f
        for (i in 0 until 6) field.gone[i] = false
        field.seq = emptyList()
        field.bad = false
        field.busy = false
    }

    // Right: they gather in the middle, merge into one big light with a ripple, and it rises away. About a second.
    LaunchedEffect(okTick) {
        if (okTick == firstOk) return@LaunchedEffect
        field.fallback?.cancel()
        field.busy = true
        field.bad = false
        if (field.lastSeq.isNotEmpty()) field.seq = field.lastSeq
        for (i in 0 until 6) field.gone[i] = false
        field.dimTarget = 0f
        field.mode = SEELIE_GATHER
        delay(480)
        field.mode = SEELIE_MERGED
        field.burst(field.w / 2f, field.h / 2f, -1, 20, 90f * field.unit)
        launch { field.ripple.snapTo(0f); field.ripple.animateTo(1f, tween(900, easing = LinearOutSlowInEasing)) }
        field.merge.snapTo(0f)
        field.merge.animateTo(1f, tween(380))
        field.rise.animateTo(1f, tween(380, easing = FastOutLinearInEasing))
        latestOnShown()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = maxWidth / 390f
        val unit = with(density) { u.toPx() }
        field.unit = unit
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The top third: the header and the slots, sitting low in it.
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                SeelieHeader(title, sub, field.bad)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(u * 10f)) {
                    for (i in 0 until maxOf(need, field.seq.size)) {
                        SeelieSlot(on = i < field.seq.size, bad = field.bad && i < field.seq.size, accent = look.accent, size = u * 12f)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            // The lower two-thirds: where the seelies drift.
            Box(
                Modifier.fillMaxWidth().weight(2f)
                    .onSizeChanged { field.w = it.width.toFloat(); field.h = it.height.toFloat() }
                    .pointerInput(field) { detectTapGestures(onPress = { p -> catchAt(p) }) }
                    .drawWithCache {
                        val colors = Array(7) { if (it < 6) SeelieKinds[it].color else SeelieMergedColor }
                        // Each orb's soft glow, its body (a white highlight up-left, like the page's radial gradient)
                        // and its tail, built once around (0, 0) and moved into place when drawn.
                        val glows = Array(7) { i ->
                            val c = colors[i]
                            Brush.radialGradient(
                                0f to c.copy(alpha = 0.7f), 0.3f to c.copy(alpha = 0.45f), 0.6f to c.copy(alpha = 0.15f), 1f to Color.Transparent,
                                center = Offset.Zero, radius = 58f * unit,
                            )
                        }
                        val bodies = Array(7) { i ->
                            val c = colors[i]
                            Brush.radialGradient(
                                0f to Color.White, 0.11f to Color.White, 0.4f to c, 0.72f to c.copy(alpha = 0.6f), 1f to Color.Transparent,
                                center = Offset(-4.8f * unit, -6.1f * unit), radius = 31.75f * unit,
                            )
                        }
                        val tails = Array(6) { i ->
                            Brush.horizontalGradient(0f to Color.Transparent, 1f to colors[i], startX = -32f * unit, endX = -8f * unit)
                        }
                        val rippleRing = Stroke(1.5.dp.toPx())
                        onDrawBehind {
                            drawSeelieField(field, field.frame.longValue, glows, bodies, tails, colors, rippleRing, look.accent)
                        }
                    }
            )
            SeelieFooter(onFinger, onUsePin)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Draws the drifting seelies, the sparks, and (on a right code) the merged light and its ripple. */
private fun DrawScope.drawSeelieField(
    field: SeelieField,
    now: Long,
    glows: Array<Brush>,
    bodies: Array<Brush>,
    tails: Array<Brush>,
    colors: Array<Color>,
    rippleRing: Stroke,
    accent: Color,
) {
    val unit = field.unit
    if (!field.placed) return
    // Seconds, kept small so the float stays precise.
    val t = ((now / 1_000_000L) % 3_600_000L) / 1000f
    val dim = field.dim
    for (i in 0 until 6) {
        val a = field.fade[i]
        if (a <= 0.01f) continue
        // A gentle wobble on top of the drift (the page's 3.6 s "drift" keyframes).
        val wob = SEELIE_TAU * (t / 7.2f) + i * 1.1f
        val ox = field.x[i] + sin(wob) * 4f * unit
        val oy = field.y[i] + cos(wob * 1.3f) * 4f * unit
        val deg = field.heading[i] * 180f / PI.toFloat()
        withTransform({ translate(ox, oy) }) {
            drawCircle(glows[i], radius = 58f * unit, center = Offset.Zero, alpha = a * (1f - 0.55f * dim))
            // The tail trails behind, opposite to where it's heading.
            rotate(deg, pivot = Offset.Zero) {
                drawLine(tails[i], Offset(-32f * unit, 0f), Offset(-8f * unit, 0f), strokeWidth = 5f * unit, cap = StrokeCap.Round, alpha = 0.75f * a * (1f - 0.5f * dim))
            }
            drawCircle(bodies[i], radius = 17f * unit, center = Offset.Zero, alpha = a * (1f - 0.3f * dim))
            if (dim > 0f) drawCircle(Color(0xFF2A2C3A), radius = 17f * unit, center = Offset.Zero, alpha = 0.5f * dim * a)
        }
    }
    if (field.mode == SEELIE_MERGED) {
        val cx = field.w / 2f
        val cy = field.h / 2f
        val rp = field.ripple.value
        if (rp < 1f) drawCircle(accent.copy(alpha = 0.9f * (1f - rp)), radius = 10f * unit * (0.4f + 8.6f * rp), center = Offset(cx, cy), style = rippleRing)
        val p = field.merge.value
        val r = field.rise.value
        val pop = if (p < 0.45f) 0.4f + 1.2f * (p / 0.45f) else 1.6f - 0.4f * ((p - 0.45f) / 0.55f)
        val alpha = (if (p < 0.45f) 0.4f + 0.6f * (p / 0.45f) else 1f) * (1f - r)
        val s = (pop + r) * (50f / 34f)
        if (alpha > 0.01f) {
            withTransform({
                translate(cx, cy - 80f * unit * r)
                scale(s, s, Offset.Zero)
            }) {
                drawCircle(glows[6], radius = 58f * unit, center = Offset.Zero, alpha = alpha)
                drawCircle(bodies[6], radius = 17f * unit, center = Offset.Zero, alpha = alpha)
            }
        }
    }
    // Sparks: fly out, shrink and fade.
    for (k in 0 until SEELIE_SPARKS) {
        val start = field.sparkStart[k]
        if (start == 0L) continue
        val q = ((now - start) / 1_000_000f) / field.sparkLife[k]
        if (q >= 1f) { field.sparkStart[k] = 0L; continue }
        if (q < 0f) continue
        val e = 1f - (1f - q) * (1f - q) * (1f - q)
        val d = field.sparkReach[k]
        val a = field.sparkAngle[k]
        val pt = Offset(field.sparkX[k] + cos(a) * d * e, field.sparkY[k] + (sin(a) * d - 6f * unit) * e)
        val c = colors[field.sparkColor[k]]
        val sz = 1f - 0.8f * q
        drawCircle(c.copy(alpha = 0.35f * (1f - q)), radius = 5f * unit * sz, center = pt)
        drawCircle(c.copy(alpha = 1f - q), radius = 2.5f * unit * sz, center = pt)
    }
}

/** The calm header from Compact glass: lock, a small spaced Cinzel title, the line under it (red while it says no). */
@Composable
private fun SeelieHeader(title: String, sub: String, bad: Boolean) {
    val look = LocalLauncherLook.current
    Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = look.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(title.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Medium, color = look.accent, fontSize = 17.sp,
                letterSpacing = 0.2.em, shadow = Shadow(look.accent.copy(alpha = 0.35f), blurRadius = 16f)))
        Spacer(Modifier.height(5.dp))
        Text(sub, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = look.font, color = if (bad) SeelieBad else look.textDim, fontSize = 12.5.sp))
    }
}

/** One slot: a hollow gold ring that springs bigger and fills (and glows) when a seelie is caught; red when wrong. */
@Composable
private fun SeelieSlot(on: Boolean, bad: Boolean, accent: Color, size: Dp) {
    val fill = animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "slot")
    val red = animateFloatAsState(if (bad) 1f else 0f, tween(200), label = "slot bad")
    Spacer(
        Modifier.size(size).drawWithCache {
            val ring = Stroke(1.3.dp.toPx())
            val r0 = this.size.minDimension / 2f
            val glow = Brush.radialGradient(0f to accent.copy(alpha = 0.6f), 1f to Color.Transparent, radius = r0 * 2.6f)
            onDrawBehind {
                val f = fill.value
                val b = red.value
                val fc = f.coerceIn(0f, 1f)
                val r = r0 * (1f + 0.25f * f)
                val c = lerp(accent, SeelieBad, b)
                if (fc > 0f) drawCircle(glow, radius = r0 * 2.6f, alpha = fc * (1f - b))
                if (fc > 0f) drawCircle(c, radius = r, alpha = fc)
                drawCircle(lerp(accent.copy(alpha = 0.6f), c, fc), radius = r - ring.width / 2f, style = ring)
            }
        }
    )
}

/** Fingerprint (when it's on) and a small "Use PIN" under it. */
@Composable
private fun SeelieFooter(onFinger: (() -> Unit)?, onUsePin: (() -> Unit)?) {
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
