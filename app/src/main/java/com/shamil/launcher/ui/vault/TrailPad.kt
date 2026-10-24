package com.shamil.launcher.ui.vault

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/*
 * Light trail (24 Oct, look 3 on the PIN looks page): bare thin numbers, no circles. Each tap throws a short gold
 * stroke of light from the previous key to this one (the first one starts at Seelie's icon at the top); the dots are
 * four-point sparkles that light up and turn. A wrong PIN turns the light rose-red, drops a few sparks and shakes the pad.
 * The strokes live on one see-through layer over the pad, with a frame loop that only runs while light is still moving.
 * Distances come from the page, which is a 390-wide phone: [TrailSky.u] turns one page pixel into real pixels.
 */

private const val STROKES = 10
private const val TRAIL_SPARKS = 120
private const val DRAW_MS = 380f      // how long a stroke takes to shoot from key to key
private const val FADE_MS = 900f      // then how long it takes to fade
private const val TAIL = 0.62f        // how much of the curve the glowing tail covers
private const val PIECES = 34         // each stroke is drawn as this many short pieces, so it can taper

private val TrailGold = Color(0xFFF3D79C)
private val TrailRed = Color(0xFFFF7E92)
private val SparkRose = Color(0xFFFF8E9E)
private val HeadWhite = Color(0xFFFFFAEB)

/** A light system font for the thin digits. */
private val TrailLight: FontFamily = FontFamily(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL))

// The soft glow behind a lit sparkle, gold or rose, made once.
private val SparkGlowGold = Brush.radialGradient(0f to TrailGold.copy(alpha = 0.9f), 0.45f to TrailGold.copy(alpha = 0.3f), 1f to TrailGold.copy(alpha = 0f),
    center = Offset.Zero, radius = 13f)
private val SparkGlowRose = Brush.radialGradient(0f to SparkRose.copy(alpha = 0.9f), 0.45f to SparkRose.copy(alpha = 0.3f), 1f to SparkRose.copy(alpha = 0f),
    center = Offset.Zero, radius = 13f)

/** Where a digit's key sits in the key arrays: 1–9 are 0–8, 0 is 10 (the bottom middle). -1 = not a digit. */
private fun trailKey(d: Char): Int = when (d) {
    '0' -> 10
    in '1'..'9' -> d - '1'
    else -> -1
}

private fun rand(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

/**
 * Light trail: thin bare digits, gold strokes of light between the keys you tap, sparkles for dots. Same rules as
 * [GlassPadLayout]; [wrongTick] goes up by one on every wrong PIN and turns the light red. [dotsOffset] shakes the pad.
 */
@Composable
fun TrailPadLayout(
    title: String, sub: String, entry: String, dotCount: Int,
    onDigit: (String) -> Unit, onDelete: () -> Unit, doneLabel: String?, onDone: () -> Unit,
    dotsOffset: Float = 0f, onFinger: (() -> Unit)? = null, wrongTick: Int = 0,
) {
    val look = LocalLauncherLook.current
    // Made with what's already there, so an old wrong PIN doesn't replay when this look first shows.
    val sky = remember { TrailSky(entry, wrongTick) }
    val shake = rememberUpdatedState(dotsOffset)
    // After a wrong PIN the sparkles stay lit in rose for a moment (this many of them), then go out.
    var badCount by remember { mutableIntStateOf(0) }
    val bad = badCount > 0

    // The frame loop: only while light is moving; it wakes when a stroke is thrown and sleeps when everything has faded.
    LaunchedEffect(sky.awake) {
        if (!sky.awake) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 16.7f else min(50f, (now - last) / 1_000_000f)
                last = now
                sky.step(dt)
                sky.tick.intValue++
            }
            if (!sky.alive()) { sky.awake = false; break }
        }
    }

    // A digit typed (throw a stroke) or a wrong PIN (turn it red). Both keys in one effect, because a wrong PIN clears
    // [entry] in the same moment; this way we still know which key was pressed last.
    LaunchedEffect(entry, wrongTick) {
        val before = sky.lastEntry
        sky.lastEntry = entry
        if (wrongTick != sky.lastTick) {
            sky.lastTick = wrongTick
            sky.bad = true
            before.lastOrNull()?.let { sky.fallAt(trailKey(it)) }
            sky.tick.intValue++
            badCount = before.length.coerceAtLeast(1)
            delay(760)
            sky.clear()
            badCount = 0
            return@LaunchedEffect
        }
        if (entry.length > before.length) {
            // Typing again straight after a wrong PIN: wipe the red away first.
            if (sky.bad || badCount > 0) {
                sky.clear()
                badCount = 0
            }
            val from = if (entry.length >= 2) trailKey(entry[entry.length - 2]) else -1
            sky.throwStroke(from, trailKey(entry.last()))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.55f))
            TrailMark(sky)
            Spacer(Modifier.height(12.dp))
            Text(title.uppercase(), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp), style = TextStyle(
                fontFamily = Cinzel, fontWeight = FontWeight.Medium, color = look.accent, fontSize = 19.sp, letterSpacing = 0.2.em))
            Spacer(Modifier.height(5.dp))
            Text(sub, textAlign = TextAlign.Center, style = TextStyle(fontFamily = look.font, color = if (bad) SparkRose else look.textDim, fontSize = 12.5.sp))
            Spacer(Modifier.weight(0.8f))
            // The dots: four-point sparkles.
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                for (i in 0 until dotCount.coerceAtLeast(entry.length)) {
                    TrailSparkle(on = i < entry.length || i < badCount, bad = bad, dim = look.accent)
                }
            }
            Spacer(Modifier.weight(0.75f))
            // The pad: 100×72 touch cells, no circles. It shakes with [dotsOffset] (moved in the layer, no rebuild).
            Column(Modifier.graphicsLayer { translationX = shake.value }) {
                val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
                rows.forEach { row ->
                    Row { row.forEach { d -> TrailKey(d, sky.keys, trailKey(d[0]), shake) { onDigit(d) } } }
                }
                Row {
                    when {
                        doneLabel != null -> TrailKey(doneLabel, accent = true, onClick = onDone)
                        onFinger != null -> TrailKey("", icon = R.drawable.ic_touch_app, onClick = onFinger)
                        else -> Spacer(Modifier.size(100.dp, 72.dp))
                    }
                    TrailKey("0", sky.keys, 10, shake) { onDigit("0") }
                    TrailKey("⌫", quiet = true, onClick = onDelete)
                }
            }
            Spacer(Modifier.height(72.dp))
        }
        // The light layer over everything. It takes no touches.
        Spacer(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val p = it.positionInRoot()
                    sky.origin[0] = p.x
                    sky.origin[1] = p.y
                    sky.u = it.size.width / 390f
                }
                .drawBehind {
                    sky.tick.intValue   // redraw on every frame of the loop
                    sky.draw(this)
                }
        )
    }
}

/**
 * The strokes of light and their sparks, in plain arrays (not Compose state), so a frame never rebuilds the screen and
 * nothing new is made per frame. Times are milliseconds on the trail's own clock, which only runs while it's awake.
 */
private class TrailSky(var lastEntry: String, var lastTick: Int) {
    var awake by mutableStateOf(false)
    val tick = mutableIntStateOf(0)
    var bad = false
    var u = 1f                                   // one page pixel, in real pixels
    val origin = FloatArray(2)                   // the light layer's top-left, in root coordinates
    val keys = FloatArray(24) { Float.NaN }      // each key's centre (x, y), in root coordinates, without the shake
    val mark = floatArrayOf(Float.NaN, Float.NaN) // the orb on Seelie's icon, where the first stroke starts
    private var now = 0f
    private var thrown = 0

    // Each stroke is a curve from a to b, bent towards c (a "quadratic Bézier"), started at t0.
    private val live = BooleanArray(STROKES)
    private val ax = FloatArray(STROKES)
    private val ay = FloatArray(STROKES)
    private val bx = FloatArray(STROKES)
    private val by = FloatArray(STROKES)
    private val cx = FloatArray(STROKES)
    private val cy = FloatArray(STROKES)
    private val t0 = FloatArray(STROKES)

    // Sparks: where, how fast (pixels a second), how old and how long they live (0 = free).
    private val kx = FloatArray(TRAIL_SPARKS)
    private val ky = FloatArray(TRAIL_SPARKS)
    private val kvx = FloatArray(TRAIL_SPARKS)
    private val kvy = FloatArray(TRAIL_SPARKS)
    private val kAge = FloatArray(TRAIL_SPARKS)
    private val kLife = FloatArray(TRAIL_SPARKS)
    private var kNext = 0

    /** A stroke from key [from] (-1 = the icon at the top) to key [to]. */
    fun throwStroke(from: Int, to: Int) {
        if (to < 0 || keys[to * 2].isNaN()) return
        val sx: Float
        val sy: Float
        if (from >= 0 && !keys[from * 2].isNaN()) {
            sx = keys[from * 2] - origin[0]; sy = keys[from * 2 + 1] - origin[1]
        } else if (!mark[0].isNaN()) {
            sx = mark[0] - origin[0]; sy = mark[1] - origin[1]
        } else return
        val ex = keys[to * 2] - origin[0]
        val ey = keys[to * 2 + 1] - origin[1]
        var s = live.indexOfFirst { !it }
        if (s < 0) {
            s = 0
            for (i in 1 until STROKES) if (t0[i] < t0[s]) s = i
        }
        // Bend it sideways, left and right in turn, more for longer strokes (up to 70 page pixels).
        val dx = ex - sx
        val dy = ey - sy
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val bend = min(70f * u, len * 0.3f) * (if (thrown++ % 2 == 1) 1f else -1f)
        ax[s] = sx; ay[s] = sy; bx[s] = ex; by[s] = ey
        cx[s] = (sx + ex) / 2f - dy / len * bend
        cy[s] = (sy + ey) / 2f + dx / len * bend
        t0[s] = now
        live[s] = true
        awake = true
    }

    /** A wrong PIN: a little shower of sparks falls from key [key]. */
    fun fallAt(key: Int) {
        if (key < 0 || keys[key * 2].isNaN()) return
        val x = keys[key * 2] - origin[0]
        val y = keys[key * 2 + 1] - origin[1]
        repeat(16) { spark(x + rand(-10f, 10f) * u, y, rand(-30f, 30f) * u, rand(-20f, 30f) * u, rand(500f, 900f)) }
        awake = true
    }

    fun clear() {
        live.fill(false)
        kLife.fill(0f)
        bad = false
        thrown = 0
        tick.intValue++
    }

    fun alive(): Boolean = live.any { it } || kLife.any { it > 0f }

    private fun spark(x: Float, y: Float, vx: Float, vy: Float, life: Float) {
        val k = kNext
        kNext = (kNext + 1) % TRAIL_SPARKS
        kx[k] = x; ky[k] = y; kvx[k] = vx; kvy[k] = vy; kAge[k] = 0f; kLife[k] = life
    }

    private fun pointX(s: Int, t: Float): Float { val m = 1f - t; return m * m * ax[s] + 2f * m * t * cx[s] + t * t * bx[s] }
    private fun pointY(s: Int, t: Float): Float { val m = 1f - t; return m * m * ay[s] + 2f * m * t * cy[s] + t * t * by[s] }

    /** How far along its curve a stroke's bright head is, [age] ms after it was thrown (fast, then easing in). */
    private fun headAt(age: Float): Float { val p = min(1f, age / DRAW_MS); return 1f - (1f - p) * (1f - p) * (1f - p) }

    /** One frame, [dt] ms long: heads throw off sparks, sparks drift (and fall, when it went wrong), old ones go. */
    fun step(dt: Float) {
        now += dt
        for (s in 0 until STROKES) {
            if (!live[s]) continue
            val age = now - t0[s]
            if (age >= DRAW_MS + FADE_MS) { live[s] = false; continue }
            if (age < DRAW_MS && Random.nextFloat() < 0.7f * dt / 16.7f) {
                val h = headAt(age)
                spark(pointX(s, h), pointY(s, h), rand(-24f, 24f) * u, rand(-30f, 10f) * u, rand(380f, 760f))
            }
        }
        val fall = if (bad) 0.5f * u * dt / 16.7f else 0f
        for (k in 0 until TRAIL_SPARKS) {
            if (kLife[k] <= 0f) continue
            kAge[k] += dt
            if (kAge[k] >= kLife[k]) { kLife[k] = 0f; continue }
            kx[k] += kvx[k] * dt / 1000f
            ky[k] += kvy[k] * dt / 1000f + fall
        }
    }

    fun draw(d: DrawScope) = with(d) {
        val col = if (bad) TrailRed else TrailGold
        for (s in 0 until STROKES) {
            if (!live[s]) continue
            val age = now - t0[s]
            if (age < 0f) continue
            // While it shoots: the tail follows the head. Then: the tail catches up with the end while it all fades.
            val head = headAt(age)
            val q = ((age - DRAW_MS) / FADE_MS).coerceIn(0f, 1f)
            val u1 = if (q > 0f) 1f else head
            val u0 = if (q > 0f) (1f - TAIL) + TAIL * q else (head - TAIL).coerceAtLeast(0f)
            val al = 1f - q * q
            var px = pointX(s, u0)
            var py = pointY(s, u0)
            for (i in 0 until PIECES) {
                // Each piece is a bit brighter and thicker than the one before: a tail that tapers off behind the head.
                val f = (i + 1f) / PIECES
                val t = u0 + (u1 - u0) * f
                val nx = pointX(s, t)
                val ny = pointY(s, t)
                val a = al * f.pow(1.4f)
                val w = (0.8f + 4.4f * f) * u
                drawLine(col, Offset(px, py), Offset(nx, ny), strokeWidth = w + 9f * u, alpha = a * 0.18f, blendMode = BlendMode.Plus)
                drawLine(col, Offset(px, py), Offset(nx, ny), strokeWidth = w, alpha = a, blendMode = BlendMode.Plus)
                px = nx
                py = ny
            }
            // The bright head.
            if (q < 0.5f) {
                val at = Offset(pointX(s, u1), pointY(s, u1))
                drawCircle(col, radius = 11f * u, center = at, alpha = 0.3f * al, blendMode = BlendMode.Plus)
                drawCircle(HeadWhite, radius = (3.4f * al + 0.6f) * u, center = at, alpha = 0.95f * al, blendMode = BlendMode.Plus)
            }
        }
        for (k in 0 until TRAIL_SPARKS) {
            if (kLife[k] <= 0f) continue
            val f = 1f - kAge[k] / kLife[k]
            val at = Offset(kx[k], ky[k])
            drawCircle(col, radius = 4f * u * f + u, center = at, alpha = 0.25f * f, blendMode = BlendMode.Plus)
            drawCircle(col, radius = (1.3f * f + 0.3f) * u, center = at, alpha = 0.9f * f, blendMode = BlendMode.Plus)
        }
    }
}

/** Seelie's app icon (the foreground drawing over its background) in a 60dp rounded square, with a soft gold glow. */
@Composable
private fun TrailMark(sky: TrailSky) {
    Box(
        Modifier
            .size(60.dp)
            .onGloballyPositioned {
                // The icon's orb sits at (66.8, 40.6) of its 108×108 drawing: that's where the first stroke starts.
                val b = it.boundsInRoot()
                sky.mark[0] = b.left + b.width * 66.8f / 108f
                sky.mark[1] = b.top + b.height * 40.6f / 108f
            }
            .drawWithCache {
                val glow = Brush.radialGradient(0.5f to TrailGold.copy(alpha = 0.32f), 1f to TrailGold.copy(alpha = 0f),
                    center = Offset(size.width / 2f, size.height / 2f), radius = size.minDimension * 0.9f)
                onDrawBehind { drawCircle(glow, radius = size.minDimension * 0.9f) }
            }
            .clip(RoundedCornerShape(17.dp))
    ) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
    }
}

/** One dot: a four-point sparkle. Lit, it fills gold, grows, turns a quarter and glows; [bad] turns it rose. */
@Composable
private fun TrailSparkle(on: Boolean, bad: Boolean, dim: Color) {
    val turn by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 320f), label = "sparkle turn")
    val fill by animateFloatAsState(if (on) 1f else 0f, tween(250), label = "sparkle fill")
    val red by animateFloatAsState(if (bad) 1f else 0f, tween(250), label = "sparkle red")
    val star = remember {
        Path().apply {
            moveTo(0f, -9f); quadraticTo(0.8f, -0.8f, 9f, 0f); quadraticTo(0.8f, 0.8f, 0f, 9f)
            quadraticTo(-0.8f, 0.8f, -9f, 0f); quadraticTo(-0.8f, -0.8f, 0f, -9f); close()
        }
    }
    val edge = remember { Stroke(width = 0.7f) }
    Spacer(Modifier.size(20.dp).drawBehind {
        val c = lerp(TrailGold, SparkRose, red)
        val sc = size.width / 20f * (1f + 0.35f * turn)
        withTransform({ translate(size.width / 2f, size.height / 2f); rotate(90f * turn, Offset.Zero); scale(sc, sc, Offset.Zero) }) {
            if (fill > 0.01f) {
                if (red < 1f) drawCircle(SparkGlowGold, radius = 13f, center = Offset.Zero, alpha = 0.6f * fill * (1f - red))
                if (red > 0f) drawCircle(SparkGlowRose, radius = 13f, center = Offset.Zero, alpha = 0.6f * fill * red)
                drawPath(star, c, alpha = fill)
            }
            drawPath(star, lerp(dim.copy(alpha = 0.55f), c, fill), style = edge)
        }
    })
}

/**
 * One touch cell: a bare thin digit, no circle. Pressed, it sinks a little and lights up gold (a second, glowing copy
 * of the digit fades in on top, so the glow never rebuilds the text). Digit cells write their centre into [centres].
 */
@Composable
private fun TrailKey(
    label: String, centres: FloatArray? = null, index: Int = -1, shake: State<Float>? = null,
    accent: Boolean = false, quiet: Boolean = false, icon: Int? = null, onClick: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val lit by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 90 else 320), label = "trail key")
    val sink by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "trail key sink")
    Box(
        Modifier
            .then(if (centres != null && index >= 0) Modifier.onGloballyPositioned {
                // Stored without the "no" shake, so a stroke always lands where the key really rests.
                val c = it.boundsInRoot().center
                centres[index * 2] = c.x - (shake?.value ?: 0f)
                centres[index * 2 + 1] = c.y
            } else Modifier)
            .size(100.dp, 72.dp)
            .clickable(press, null, onClick = onClick)
            .graphicsLayer { scaleX = sink; scaleY = sink },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = look.accent, modifier = Modifier.size(28.dp))
        else {
            val digit = label.length == 1 && label[0].isDigit()
            val style = TextStyle(
                fontFamily = if (digit || quiet) TrailLight else look.font,
                fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (accent) 17.sp else if (digit) 36.sp else 28.sp,
                color = if (accent || quiet) look.accent else look.text,
                shadow = Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 18f),
            )
            Text(label, style = style)
            Text(label, modifier = Modifier.graphicsLayer { alpha = lit },
                style = style.copy(color = Color(0xFFFFF6DC), shadow = Shadow(TrailGold, blurRadius = 40f)))
        }
    }
}
