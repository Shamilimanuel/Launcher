package com.shamil.launcher.ui.vault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/*
 * Fun lock: Elemental combo (24 Oct, from the PIN looks page). The seven Genshin elements sit in a ring around a slowly
 * turning dashed orbit, and he taps them in his secret order. The code is the element ids joined by commas, for example
 * "pyro,hydro,electro,cryo" (4 to 6 taps). The lock never checks the code itself: it hands it to onCode, and the vault
 * answers with wrongTick (shake, dim, start again) or okTick (the elemental reactions flash, then onSuccessShown).
 * Everything is sized on the page's 390-wide phone and scaled to this screen's width.
 */

/** One element: the id that goes in the code, its colour, and its glyph as SVG path data on a 24-unit grid. */
private class ElementKind(val id: String, val color: Color, val stroke: String, val fill: String? = null)

/** The glyph paths, copied from the page's icons. [ElementKind.fill] is the part tinted at 30%, like `fill-opacity=".3"`. */
private val ElementKinds = listOf(
    ElementKind(
        "pyro", Color(0xFFEF7938),
        "M12 2.5c.8 3.2 5.2 5.4 5.2 10a5.2 5.2 0 0 1-10.4 0c0-2.1 1-3.4 2.1-4.4.2 1.4.9 2.3 1.8 2.7C10.3 8.4 10.6 5 12 2.5z",
        "M12 2.5c.8 3.2 5.2 5.4 5.2 10a5.2 5.2 0 0 1-10.4 0c0-2.1 1-3.4 2.1-4.4.2 1.4.9 2.3 1.8 2.7C10.3 8.4 10.6 5 12 2.5z",
    ),
    ElementKind(
        "hydro", Color(0xFF4CC2F1),
        "M12 2.8C9.2 7 6.4 10 6.4 14a5.6 5.6 0 0 0 11.2 0c0-4-2.8-7-5.6-11.2z",
        "M12 2.8C9.2 7 6.4 10 6.4 14a5.6 5.6 0 0 0 11.2 0c0-4-2.8-7-5.6-11.2z",
    ),
    ElementKind(
        "anemo", Color(0xFF74C2A8),
        "M3.5 8.5h9.2a2.8 2.8 0 1 0-2.8-2.8M2.5 12.5h14.2a3 3 0 1 1-3 3M5.5 17h6",
    ),
    ElementKind(
        "electro", Color(0xFFB47FDC),
        "M13.4 2.5L5.5 13.2h5.6l-1 8.3 8.4-11.4h-5.8z",
        "M13.4 2.5L5.5 13.2h5.6l-1 8.3 8.4-11.4h-5.8z",
    ),
    ElementKind(
        "dendro", Color(0xFFA5C83B),
        "M12 21v-9M12 13.5c0-4.2-3-6.8-7.2-6.8 0 4.2 2.6 6.8 7.2 6.8zM12 11c0-3.6 2.6-6.2 6.8-6.2 0 3.7-2.4 6.2-6.8 6.2z",
        "M12 13.5c0-4.2-3-6.8-7.2-6.8 0 4.2 2.6 6.8 7.2 6.8zM12 11c0-3.6 2.6-6.2 6.8-6.2 0 3.7-2.4 6.2-6.8 6.2z",
    ),
    ElementKind(
        "cryo", Color(0xFF9FD6E3),
        "M12 2.5v19M3.8 7.2l16.4 9.6M3.8 16.8l16.4-9.6M9.5 4.6L12 6.8l2.5-2.2M9.5 19.4L12 17.2l2.5 2.2" +
            "M4.2 10.4l3.2-.6-.2-3.2M19.8 13.6l-3.2.6.2 3.2M4.2 13.6l3.2.6-.2 3.2M19.8 10.4l-3.2-.6.2-3.2",
    ),
    ElementKind(
        "geo", Color(0xFFF8C84A),
        "M12 2.8L20 9l-8 12.2L4 9zM4 9h16M9 9l3 12.2L15 9M9 9l3-6.2L15 9",
        "M12 2.8L20 9l-8 12.2L4 9z",
    ),
)

/** The reaction two elements make, from the page's `reaction()`. Anemo swirls and Geo crystallizes with anything. */
private fun elementReaction(a: String, b: String): String {
    val pair = if (a < b) "$a-$b" else "$b-$a"
    return when (pair) {
        "hydro-pyro" -> "Vaporize"
        "electro-hydro" -> "Electro-Charged"
        "cryo-electro" -> "Superconduct"
        "cryo-pyro" -> "Melt"
        "electro-pyro" -> "Overloaded"
        "cryo-hydro" -> "Frozen"
        "dendro-pyro" -> "Burning"
        "dendro-hydro" -> "Bloom"
        "dendro-electro" -> "Quicken"
        else -> when {
            a == "anemo" || b == "anemo" -> "Swirl"
            a == "geo" || b == "geo" -> "Crystallize"
            else -> "Resonance"
        }
    }
}

/** A glyph turned into real Paths once (PathParser reads SVG path text), so drawing it is just two drawPath calls. */
private class ElementGlyph(kind: ElementKind) {
    val stroke: Path = PathParser().parsePathString(kind.stroke).toPath()
    val fill: Path? = kind.fill?.let { PathParser().parsePathString(it).toPath() }
}

/** The glyph's line, in 24-grid units (it's scaled with the glyph). */
private val ElementGlyphLine = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val ElementInk = Color(0xFF171C29)
private val ElementBad = Color(0xFFFF8E9E)
private val ElementShakeSteps = floatArrayOf(-1f, 0.8f, -0.55f, 0.3f, 0f)

/** One burst of little sparks. Reused from a small pool, so a tap never allocates new ones. */
private class ElementBurst {
    val t = Animatable(1f)
    var x = 0f
    var y = 0f
    var c1 = Color.White
    var c2 = Color.White
    var count = 0
    var reach = 0f
    var seed = 0
}

/** The ring's effects: spark bursts, the big reaction flash, and what the lock last sent. */
private class ElementFx {
    val bursts = List(6) { ElementBurst() }
    private var next = 0
    val flash = Animatable(1f)
    var flashBrush: Brush? = null
    var flashCenter = Offset.Zero
    /** Clears the entry if the vault never answers (for example the first step of setting it up). */
    var fallback: Job? = null
    var lastSeq: List<String> = emptyList()

    fun burst(scope: CoroutineScope, x: Float, y: Float, c1: Color, c2: Color, count: Int, reach: Float) {
        val b = bursts[next]
        next = (next + 1) % bursts.size
        b.x = x; b.y = y; b.c1 = c1; b.c2 = c2; b.count = count; b.reach = reach; b.seed += 7919
        scope.launch { b.t.snapTo(0f); b.t.animateTo(1f, tween(760, easing = LinearEasing)) }
    }
}

/** A cheap repeatable "random" number from 0 to 1 (a hash), so sparks don't need stored random values. */
private fun elementNoise(seed: Int, i: Int): Float {
    var h = seed * 374761393 + i * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777216f
}

@Composable
fun ElementLock(
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
    val checking by rememberUpdatedState(LocalFunLockChecking.current)
    val glyphs = remember { ElementKinds.map { ElementGlyph(it) } }
    val lit = remember { List(ElementKinds.size) { Animatable(0f) } }
    val dim = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val fx = remember { ElementFx() }
    var seq by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var bad by remember { mutableStateOf(false) }
    var reactionName by remember { mutableStateOf("") }
    var reactionGlow by remember { mutableStateOf(Color.White) }
    val reactionPop = remember { Animatable(1f) }
    val reactionFade = remember { Animatable(0f) }
    // The ticks' starting values, so the effects below skip their first run (nothing happened yet).
    val firstWrong = remember { wrongTick }
    val firstOk = remember { okTick }
    // The dashed orbit turns once a minute. It only redraws a cached layer, so it costs next to nothing.
    val spin = rememberInfiniteTransition(label = "orbit")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "orbit turn")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = maxWidth / 390f
        val unit = with(density) { u.toPx() }
        val ringPx = unit * 318f
        val mid = Offset(ringPx / 2f, ringPx / 2f)

        fun elementCenter(i: Int): Offset {
            val a = Math.toRadians(-90.0 + i * 360.0 / ElementKinds.size).toFloat()
            return Offset(mid.x + unit * 128f * cos(a), mid.y + unit * 128f * sin(a))
        }

        fun flash(i: Int) {
            scope.launch { lit[i].animateTo(1f, tween(110)); delay(190); lit[i].animateTo(0f, tween(250)) }
            val c = elementCenter(i)
            fx.burst(scope, c.x, c.y, ElementKinds[i].color, ElementKinds[i].color, 7, unit * 34f)
        }

        fun submit(wait: Long) {
            busy = true
            val entry = seq
            fx.lastSeq = entry
            scope.launch {
                delay(wait)   // let the last flash show first
                latestOnCode(entry.joinToString(","))
                fx.fallback = scope.launch {
                    delay(if (codeLength > 0) 1200L else 450L)
                    // Still being checked: wait for the answer before starting again (review, 25 Oct).
                    while (checking) delay(50)
                    seq = emptyList()
                    busy = false
                }
            }
        }

        fun tap(i: Int) {
            if (busy || seq.size >= 6) return
            seq = seq + ElementKinds[i].id
            bad = false
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            flash(i)
            if (codeLength > 0 && seq.size == codeLength) submit(380L)
        }

        // Wrong: the slots go red, the elements dim, the ring shakes its head, then it's clear again.
        LaunchedEffect(wrongTick) {
            if (wrongTick == firstWrong) return@LaunchedEffect
            fx.fallback?.cancel()
            busy = true
            bad = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            launch { dim.animateTo(1f, tween(250)) }
            launch { for (k in ElementShakeSteps) shake.animateTo(k * 12f, tween(88)) }
            delay(620)
            dim.animateTo(0f, tween(300))
            seq = emptyList()
            bad = false
            busy = false
        }

        // Right: each element flashes in turn, and every pair shows its reaction ("Vaporize!"). About a second.
        LaunchedEffect(okTick) {
            if (okTick == firstOk) return@LaunchedEffect
            fx.fallback?.cancel()
            busy = true
            bad = false
            if (fx.lastSeq.isNotEmpty()) seq = fx.lastSeq
            val order = fx.lastSeq.map { id -> ElementKinds.indexOfFirst { it.id == id } }.filter { it >= 0 }
            if (order.size < 2) {
                // Opened another way (fingerprint): the whole ring lights up once.
                for (i in ElementKinds.indices) { flash(i); delay(70) }
                fx.burst(scope, mid.x, mid.y, look.accent, Color.White, 12, unit * 80f)
                delay(450)
            } else {
                val step = (1050L / order.size).coerceIn(200L, 300L)
                order.forEachIndexed { n, i ->
                    flash(i)
                    if (n < order.lastIndex) {
                        val a = ElementKinds[i]
                        val b = ElementKinds[order[n + 1]]
                        reactionName = elementReaction(a.id, b.id) + "!"
                        reactionGlow = b.color
                        fx.flashBrush = Brush.radialGradient(
                            0f to a.color, 0.45f to b.color, 0.7f to Color.Transparent,
                            center = Offset.Zero, radius = unit * 110f,
                        )
                        fx.flashCenter = mid
                        launch { fx.flash.snapTo(0f); fx.flash.animateTo(1f, tween(520, easing = LinearOutSlowInEasing)) }
                        reactionFade.snapTo(1f)
                        launch { reactionPop.snapTo(0f); reactionPop.animateTo(1f, tween(380)) }
                        fx.burst(scope, mid.x, mid.y, a.color, b.color, 10, unit * 70f)
                    }
                    delay(step)
                }
                reactionFade.animateTo(0f, tween(240))
            }
            latestOnShown()
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            ElementHeader(title, sub, bad)
            Spacer(Modifier.height(10.dp))
            // The ring: orbit, seven elements, the reaction name, the slots, and the sparks on top.
            Box(Modifier.size(u * 318f).graphicsLayer { translationX = shake.value * unit }) {
                val orbitColor = look.accent.copy(alpha = 0.3f)
                Spacer(
                    Modifier.align(Alignment.Center).size(u * 256f)
                        .graphicsLayer { rotationZ = spin.value }
                        .drawWithCache {
                            val dash = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                            )
                            onDrawBehind { drawCircle(orbitColor, radius = size.minDimension / 2f - dash.width, style = dash) }
                        }
                )
                ElementKinds.forEachIndexed { i, kind ->
                    val a = Math.toRadians(-90.0 + i * 360.0 / ElementKinds.size).toFloat()
                    ElementOrb(
                        kind, glyphs[i], lit[i], dim,
                        Modifier.offset(x = u * (159f - 31f + 128f * cos(a)), y = u * (159f - 31f + 128f * sin(a))).size(u * 62f),
                    ) { tap(i) }
                }
                Text(
                    reactionName,
                    modifier = Modifier.align(Alignment.Center).offset(y = u * -52f).graphicsLayer {
                        val p = reactionPop.value
                        val s = if (p < 0.35f) 0.6f + 0.55f * (p / 0.35f) else 1.15f - 0.15f * ((p - 0.35f) / 0.65f)
                        scaleX = s
                        scaleY = s
                        alpha = (p / 0.35f).coerceAtMost(1f) * reactionFade.value
                    },
                    style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 22.sp,
                        letterSpacing = 0.06.em, shadow = Shadow(reactionGlow, blurRadius = 30f)),
                )
                val slots = maxOf(if (codeLength > 0) codeLength else 4, seq.size)
                Row(Modifier.align(Alignment.Center).offset(y = u * 12f), horizontalArrangement = Arrangement.spacedBy(u * 10f)) {
                    for (i in 0 until slots) ElementSlot(on = i < seq.size, bad = bad && i < seq.size, accent = look.accent, size = u * 12f)
                }
                Spacer(Modifier.matchParentSize().drawBehind { drawElementFx(fx, unit) })
            }
            Spacer(Modifier.height(8.dp))
            // OK: from 4 taps on (setup has no fixed length; on unlock it also checks by itself at the right length).
            val showOk = seq.size >= 4 && !busy
            val okShown = animateFloatAsState(if (showOk) 1f else 0f, tween(200), label = "ok")
            val okPress = remember { MutableInteractionSource() }
            Box(
                Modifier.height(44.dp).width(120.dp)
                    .graphicsLayer { alpha = okShown.value; val s = 0.9f + 0.1f * okShown.value; scaleX = s; scaleY = s }
                    .pressFeel(okPress, look.accent, 22.dp)
                    .clip(CircleShape)
                    .background(look.accent)
                    .clickable(okPress, null, enabled = showOk) { submit(120L) },
                contentAlignment = Alignment.Center,
            ) {
                Text("OK", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    letterSpacing = 0.06.em, color = look.sheet.copy(alpha = 1f)))
            }
            Spacer(Modifier.height(14.dp))
            ElementFooter(onFinger, onUsePin)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The calm header from Compact glass: lock, a small spaced Cinzel title, the line under it (red while it says no). */
@Composable
private fun ElementHeader(title: String, sub: String, bad: Boolean) {
    val look = LocalLauncherLook.current
    Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = look.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(title.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Medium, color = look.accent, fontSize = 17.sp,
                letterSpacing = 0.2.em, shadow = Shadow(look.accent.copy(alpha = 0.35f), blurRadius = 16f)))
        Spacer(Modifier.height(5.dp))
        Text(sub, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = look.font, color = if (bad) ElementBad else look.textDim, fontSize = 12.5.sp))
    }
}

/**
 * One element button: a dark glass disc with a coloured rim and glow. Tapped it sinks; "lit" fills it with its colour
 * (glyph turns ink); "dim" greys it out. All of that is drawn from animation values, so nothing recomposes.
 */
@Composable
private fun ElementOrb(
    kind: ElementKind,
    glyph: ElementGlyph,
    lit: Animatable<Float, AnimationVector1D>,
    dim: Animatable<Float, AnimationVector1D>,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink = animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "element press")
    Box(
        modifier
            .graphicsLayer { val s = sink.value; scaleX = s; scaleY = s }
            .drawWithCache {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val base = Brush.radialGradient(
                    0f to Color(0xE6283054), 1f to Color(0xEB0E1222),
                    center = Offset(c.x, size.height * 0.35f), radius = r * 1.3f,
                )
                val litFill = Brush.radialGradient(
                    0f to Color.White, 0.7f to kind.color,
                    center = Offset(c.x, size.height * 0.3f), radius = r * 1.7f,
                )
                val glow = Brush.radialGradient(
                    0.55f to kind.color.copy(alpha = 0.5f), 1f to Color.Transparent,
                    center = c, radius = r * 1.6f,
                )
                val rim = Stroke(1.3.dp.toPx())
                val halo = 5.dp.toPx()
                onDrawBehind {
                    val l = lit.value
                    val d = dim.value
                    drawCircle(glow, radius = r * 1.6f, center = c, alpha = (0.45f + 0.55f * l) * (1f - 0.7f * d))
                    if (l > 0f) drawCircle(kind.color.copy(alpha = 0.3f * l), radius = r + halo * l, center = c)
                    drawCircle(base, radius = r, center = c)
                    if (l > 0f) drawCircle(litFill, radius = r, center = c, alpha = l)
                    drawCircle(lerp(kind.color.copy(alpha = 0.7f), Color(0x80808088), d), radius = r - rim.width / 2f, center = c, style = rim)
                    drawElementGlyph(glyph, c, r * 30f / 31f, lerp(lerp(kind.color, ElementInk, l), Color(0xFF6E7080), d * 0.7f))
                    if (d > 0f) drawCircle(Color.Black.copy(alpha = 0.35f * d), radius = r, center = c)
                }
            }
            .clickable(press, null, onClick = onTap)
    )
}

/** Draws an element glyph [sizePx] wide, centred on [center]: the 30% fill first, then the line. */
private fun DrawScope.drawElementGlyph(glyph: ElementGlyph, center: Offset, sizePx: Float, color: Color) {
    val k = sizePx / 24f
    withTransform({
        translate(center.x - sizePx / 2f, center.y - sizePx / 2f)
        scale(k, k, Offset.Zero)
    }) {
        glyph.fill?.let { drawPath(it, color.copy(alpha = color.alpha * 0.3f)) }
        drawPath(glyph.stroke, color, style = ElementGlyphLine)
    }
}

/** One slot: a hollow gold ring that springs bigger and fills (and glows) when an element lands in it; red when wrong. */
@Composable
private fun ElementSlot(on: Boolean, bad: Boolean, accent: Color, size: Dp) {
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
                val r = r0 * (1f + 0.25f * f)
                val c = lerp(accent, ElementBad, b)
                if (f > 0f) drawCircle(glow, radius = r0 * 2.6f, alpha = f.coerceIn(0f, 1f) * (1f - b))
                if (f > 0f) drawCircle(c, radius = r, alpha = f.coerceIn(0f, 1f))
                drawCircle(lerp(accent.copy(alpha = 0.6f), c, f.coerceIn(0f, 1f)), radius = r - ring.width / 2f, style = ring)
            }
        }
    )
}

/** The reaction flash (a two-colour bloom that grows and fades) and the spark bursts. [unit] = one page pixel. */
private fun DrawScope.drawElementFx(fx: ElementFx, unit: Float) {
    val f = fx.flash.value
    val brush = fx.flashBrush
    if (f < 1f && brush != null) {
        val s = 0.2f + 1.3f * f
        withTransform({
            translate(fx.flashCenter.x, fx.flashCenter.y)
            scale(s, s, Offset.Zero)
        }) {
            drawCircle(brush, radius = unit * 110f, center = Offset.Zero, alpha = 0.95f * (1f - f))
        }
    }
    for (b in fx.bursts) {
        val t = b.t.value
        if (t >= 1f) continue
        for (i in 0 until b.count) {
            // Each spark: its own speed, direction and distance, all from the hash.
            val life = 0.55f + 0.45f * elementNoise(b.seed, i * 3)
            val q = (t / life).coerceAtMost(1f)
            if (q >= 1f) continue
            val e = 1f - (1f - q) * (1f - q) * (1f - q)
            val a = elementNoise(b.seed, i * 3 + 1) * 6.2832f
            val d = b.reach * (0.4f + 0.6f * elementNoise(b.seed, i * 3 + 2))
            val p = Offset(b.x + cos(a) * d * e, b.y + (sin(a) * d - 6f * unit) * e)
            val c = if (i % 2 == 0) b.c1 else b.c2
            val s = 1f - 0.8f * q
            drawCircle(c.copy(alpha = 0.35f * (1f - q)), radius = 5f * unit * s, center = p)
            drawCircle(c.copy(alpha = 1f - q), radius = 2.5f * unit * s, center = p)
        }
    }
}

/** Fingerprint (when it's on) and a small "Use PIN" under it. */
@Composable
private fun ElementFooter(onFinger: (() -> Unit)?, onUsePin: (() -> Unit)?) {
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
