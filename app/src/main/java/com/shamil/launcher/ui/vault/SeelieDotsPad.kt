package com.shamil.launcher.ui.vault

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shamil.launcher.R
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Seelie dots (24 Oct, look 2 on the PIN looks page): the PIN dots are little glowing seelie orbs, each its own colour.
 * Every digit sends an orb flying from the key you pressed into its slot, with a small burst; delete makes the last one
 * shrink away; a wrong PIN scatters them. Everything that moves is drawn on one see-through layer over the pad (an
 * "overlay"), so typing never rebuilds the screen, it only redraws that one layer.
 * Distances come from the page, which is a 390-wide phone: [OrbSky.u] turns one page pixel into real pixels.
 */

private const val MAX_ORBS = 6
private const val GHOSTS = 12
private const val SPARKS = 72
private const val FLIGHT_MS = 640f
private const val LAND_MS = 560f
private const val LEAVE_MS = 260f
private const val SCATTER_MS = 1260f

/** One colour per orb, in typing order. */
private val OrbColours = arrayOf(
    Color(0xFFF3D79C), Color(0xFFF5A3C7), Color(0xFF8CC8FF), Color(0xFF9BE3B0), Color(0xFFC7A6FF), Color(0xFFDFF7FF),
)
private val SeelieGold = Color(0xFFF3D79C)
private val SeelieRose = Color(0xFFFF8E9E)

// Each orb's paint, made once. They're drawn around (0, 0) in page pixels; the overlay moves and scales them into place.
// The body: a white glint up and to the left, then the colour, fading out at the rim (a "radial gradient").
private val OrbBodies = Array(MAX_ORBS) { i ->
    val c = OrbColours[i]
    Brush.radialGradient(
        0f to Color.White, 0.11f to Color.White, 0.4f to c, 0.72f to c.copy(alpha = 0.6f), 1f to c.copy(alpha = 0f),
        center = Offset(-4.2f, -5.4f), radius = 28f,
    )
}
// The soft glow around it (the page's two box-shadows in one).
private val OrbHalos = Array(MAX_ORBS) { i ->
    val c = OrbColours[i]
    Brush.radialGradient(
        0f to c.copy(alpha = 0.7f), 0.3f to c.copy(alpha = 0.5f), 0.48f to c.copy(alpha = 0.22f), 0.7f to c.copy(alpha = 0.08f), 1f to c.copy(alpha = 0f),
        center = Offset.Zero, radius = 58f,
    )
}
// The little comet tail that trails behind it.
private val OrbTails = Array(MAX_ORBS) { i ->
    Brush.horizontalGradient(listOf(OrbColours[i].copy(alpha = 0f), OrbColours[i]), startX = -13f, endX = 13f)
}

/** A wrong PIN drains the orbs' colour: almost grey (15% colour left) and a quarter darker, like the page's CSS filter. */
private val DimFilter: ColorFilter = run {
    val s = 0.15f
    val b = 0.75f
    ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        (0.213f + 0.787f * s) * b, (0.715f - 0.715f * s) * b, (0.072f - 0.072f * s) * b, 0f, 0f,
        (0.213f - 0.213f * s) * b, (0.715f + 0.285f * s) * b, (0.072f - 0.072f * s) * b, 0f, 0f,
        (0.213f - 0.213f * s) * b, (0.715f - 0.715f * s) * b, (0.072f + 0.928f * s) * b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
}

// The page's easing curves (how a move speeds up and slows down).
private val FlightEase = CubicBezierEasing(0.3f, 0.9f, 0.4f, 1f)
private val SparkEase = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
private val ScatterEase = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)
private val InOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

// The wobble after a wrong PIN: five little jerks (page pixels and degrees).
private val WobbleX = floatArrayOf(0f, 9f, -8f, 6f, -4f, 0f)
private val WobbleY = floatArrayOf(0f, -6f, 5f, -4f, 3f, 0f)
private val WobbleTurn = floatArrayOf(0f, 10f, -9f, 6f, -3f, 0f)

/** Where a digit's key sits in the key arrays: 1–9 are 0–8, 0 is 10 (the bottom middle). -1 = not a digit. */
private fun keyIndex(d: Char): Int = when (d) {
    '0' -> 10
    in '1'..'9' -> d - '1'
    else -> -1
}

private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

/**
 * Seelie dots: glowing orbs for dots, rounded Cinzel keys low on the screen. Same rules as [GlassPadLayout];
 * [wrongTick] goes up by one on every wrong PIN and makes the orbs scatter.
 */
@Composable
fun SeelieDotsPadLayout(
    title: String, sub: String, entry: String, dotCount: Int,
    onDigit: (String) -> Unit, onDelete: () -> Unit, doneLabel: String?, onDone: () -> Unit,
    dotsOffset: Float = 0f, onFinger: (() -> Unit)? = null, wrongTick: Int = 0,
) {
    val look = LocalLauncherLook.current
    // Made with what's already there, so orbs already typed just sit in their slots and an old wrong PIN doesn't replay.
    val sky = remember { OrbSky(entry.length, wrongTick) }
    val shake = rememberUpdatedState(dotsOffset)
    val clock = remember { mutableFloatStateOf(0f) }
    var bad by remember { mutableStateOf(false) }
    val slots = dotCount.coerceAtLeast(entry.length).coerceIn(1, MAX_ORBS)

    // The frame loop. The beads breathe and the orbs bob all the time (that's the look), so it runs while the pad shows;
    // but when only that slow motion is going, it redraws about 30 times a second instead of every frame. It also stops
    // while Seelie isn't on screen (the "lifecycle" is below STARTED), like the music card's wave.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var start = -1L
            var base = 0f
            while (true) {
                withFrameNanos { t ->
                    if (start < 0) { start = t; base = sky.now }
                    val now = base + (t - start) / 1_000_000f
                    val busy = sky.step(now)
                    if (busy || now - clock.floatValue >= 32f) clock.floatValue = now
                }
            }
        }
    }

    // A digit typed (an orb flies in), deleted (it shrinks away) or a wrong PIN (they scatter). Both keys in one effect,
    // because a wrong PIN clears [entry] in the same moment: this way the orbs scatter instead of just shrinking.
    LaunchedEffect(entry, wrongTick) {
        val wrong = wrongTick != sky.tick
        sky.update(entry, wrongTick)
        if (wrong) bad = true else if (entry.isNotEmpty()) bad = false
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.75f))
            SeelieMark()
            Spacer(Modifier.height(8.dp))
            Text(title, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp), style = TextStyle(
                fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 24.sp,
                letterSpacing = 0.06.em, shadow = Shadow(look.accent.copy(alpha = 0.4f), blurRadius = 22f)))
            Spacer(Modifier.height(5.dp))
            Text(sub, textAlign = TextAlign.Center, style = TextStyle(fontFamily = look.font, color = if (bad) SeelieRose else look.textDim, fontSize = 13.sp))
            Spacer(Modifier.weight(1f))
            // The slots: empty boxes that only say where they are; the overlay draws the beads and orbs into them.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until slots) {
                    Spacer(Modifier.size(56.dp).onGloballyPositioned {
                        val c = it.boundsInRoot().center
                        sky.slots[i * 2] = c.x
                        sky.slots[i * 2 + 1] = c.y
                    })
                }
            }
            Spacer(Modifier.weight(0.85f))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEach { d -> SeelieKey(d, sky.keys, keyIndex(d[0])) { onDigit(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    doneLabel != null -> SeelieKey(doneLabel, accent = true, onClick = onDone)
                    onFinger != null -> SeelieKey("", icon = R.drawable.ic_touch_app, quiet = true, onClick = onFinger)
                    else -> Spacer(Modifier.size(80.dp, 60.dp))
                }
                SeelieKey("0", sky.keys, 10) { onDigit("0") }
                SeelieKey("⌫", quiet = true, onClick = onDelete)
            }
            Spacer(Modifier.height(34.dp))
        }
        // The overlay: every bead, orb and spark, in one layer over the pad. It takes no touches.
        Spacer(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val p = it.positionInRoot()
                    sky.origin[0] = p.x
                    sky.origin[1] = p.y
                    sky.u = it.size.width / 390f
                }
                .drawBehind { sky.draw(this, clock.floatValue, look.accent, shake.value, slots) }
        )
    }
}

/**
 * Everything that moves on the Seelie pad, kept in plain arrays (not Compose state), so a frame never rebuilds the
 * screen and nothing new is made per frame. Times are milliseconds on the pad's own clock.
 */
private class OrbSky(startLen: Int, startTick: Int) {
    var len = startLen.coerceAtMost(MAX_ORBS)   // orbs in their slots (= digits typed)
    var tick = startTick                         // the last wrong-PIN tick seen
    var now = 0f
    var u = 1f                                   // one page pixel, in real pixels
    val origin = FloatArray(2)                   // the overlay's top-left, in root coordinates
    val keys = FloatArray(24) { Float.NaN }      // each key's centre (x, y), in root coordinates
    val slots = FloatArray(MAX_ORBS * 2) { Float.NaN }

    // The orb in each slot: when it was born, and where its flight started (overlay coordinates; NaN = no flight).
    private val born = FloatArray(MAX_ORBS) { -1e6f }
    private val fromX = FloatArray(MAX_ORBS) { Float.NaN }
    private val fromY = FloatArray(MAX_ORBS)
    private val landed = BooleanArray(MAX_ORBS) { true }

    // "Ghosts": orbs on their way out. Kind 1 = deleted (shrinks away), 2 = wrong PIN (scatters, wobbles, fades), 0 = free.
    private val gKind = IntArray(GHOSTS)
    private val gSlot = IntArray(GHOSTS)
    private val gX = FloatArray(GHOSTS)
    private val gY = FloatArray(GHOSTS)
    private val gT0 = FloatArray(GHOSTS)
    private val gTx = FloatArray(GHOSTS)
    private val gTy = FloatArray(GHOSTS)

    // Burst sparks: start, how far they fly, when, how long (0 = free) and the orb colour.
    private val pX = FloatArray(SPARKS)
    private val pY = FloatArray(SPARKS)
    private val pDx = FloatArray(SPARKS)
    private val pDy = FloatArray(SPARKS)
    private val pT0 = FloatArray(SPARKS)
    private val pDur = FloatArray(SPARKS)
    private val pColour = IntArray(SPARKS)
    private var pNext = 0

    private val bob = FloatArray(2)
    private val ring = Stroke(width = 1f)

    /** [entry] grew, shrank, or a wrong PIN came in ([wrongTick] changed). */
    fun update(entry: String, wrongTick: Int) {
        val n = entry.length.coerceAtMost(MAX_ORBS)
        if (wrongTick != tick) {
            tick = wrongTick
            for (i in 0 until len) letGo(i, 2)
            len = 0
        }
        if (n > len) {
            for (i in len until n) {
                born[i] = now
                fromX[i] = Float.NaN
                landed[i] = true
            }
            // Only the newest one flies, from the key that was just pressed.
            val i = n - 1
            val k = keyIndex(entry.last())
            if (k >= 0 && !keys[k * 2].isNaN() && !slots[i * 2].isNaN()) {
                fromX[i] = keys[k * 2] - origin[0]
                fromY[i] = keys[k * 2 + 1] - origin[1]
                landed[i] = false
                burst(fromX[i], fromY[i], i, 5, 16f)
            }
        } else if (n < len) {
            for (i in n until len) letGo(i, 1)
        }
        len = n
    }

    /** Hands the orb in slot [i] over to a ghost that plays its exit. */
    private fun letGo(i: Int, kind: Int) {
        val sx = slots[i * 2]
        if (sx.isNaN()) return
        var g = gKind.indexOfFirst { it == 0 }
        if (g < 0) {
            g = 0
            for (j in 1 until GHOSTS) if (gT0[j] < gT0[g]) g = j
        }
        gKind[g] = kind
        gSlot[g] = i
        gX[g] = sx - origin[0]
        gY[g] = slots[i * 2 + 1] - origin[1]
        gT0[g] = now
        gTx[g] = rnd(-85f, 85f) * u
        gTy[g] = rnd(-70f, 50f) * u
    }

    /** [n] sparks flying out from (x, y), up to [reach] page pixels, in orb [colour]'s colour. */
    private fun burst(x: Float, y: Float, colour: Int, n: Int, reach: Float) {
        repeat(n) {
            val p = pNext
            pNext = (pNext + 1) % SPARKS
            val a = rnd(0f, 2f * PI.toFloat())
            val d = rnd(reach * 0.4f, reach) * u
            pX[p] = x
            pY[p] = y
            pDx[p] = cos(a) * d
            pDy[p] = sin(a) * d - 6f * u
            pT0[p] = now
            pDur[p] = rnd(420f, 760f)
            pColour[p] = colour
        }
    }

    /** One frame: the landing bursts, and tidy up what's done. True while something fast is still moving. */
    fun step(t: Float): Boolean {
        now = t
        var busy = false
        for (i in 0 until len) {
            val age = now - born[i]
            if (!landed[i] && age >= LAND_MS) {
                landed[i] = true
                burst(slots[i * 2] - origin[0], slots[i * 2 + 1] - origin[1], i, 7, 30f)
            }
            if (!fromX[i].isNaN() && age < FLIGHT_MS) busy = true
        }
        for (g in 0 until GHOSTS) {
            if (gKind[g] == 0) continue
            if (now - gT0[g] > (if (gKind[g] == 1) LEAVE_MS else SCATTER_MS)) gKind[g] = 0 else busy = true
        }
        for (p in 0 until SPARKS) {
            if (pDur[p] <= 0f) continue
            if (now - pT0[p] >= pDur[p]) pDur[p] = 0f else busy = true
        }
        return busy
    }

    /** The gentle bob of orb [n] at time [t] (page pixels, into [bob]): the page's 3.6 s "drift", back and forth. */
    private fun bobAt(t: Float, n: Int) {
        var s = ((t / 1000f + 1.1f * n) / 3.6f) % 2f
        if (s > 1f) s = 2f - s
        if (s < 0.5f) {
            val e = InOut.transform(s / 0.5f)
            bob[0] = -4f + 7f * e
            bob[1] = 3f - 8f * e
        } else {
            val e = InOut.transform((s - 0.5f) / 0.5f)
            bob[0] = 3f + 2f * e
            bob[1] = -5f + 7f * e
        }
    }

    fun draw(d: DrawScope, t: Float, bead: Color, shake: Float, count: Int) = with(d) {
        val ox = origin[0]
        val oy = origin[1]
        // The empty slots: faint beads that breathe (grow a little and brighten, every 3.2 s).
        for (i in 0 until count) {
            val sx = slots[i * 2]
            if (sx.isNaN()) continue
            val s = ((t / 1000f + 0.7f * i) / 3.2f) % 1f
            val k = InOut.transform(if (s < 0.5f) s / 0.5f else (1f - s) / 0.5f)
            val sc = (1f + 0.5f * k) * u
            withTransform({ translate(sx - ox + shake, slots[i * 2 + 1] - oy); scale(sc, sc, Offset.Zero) }) {
                drawCircle(bead, radius = 4.5f, center = Offset.Zero, alpha = 0.18f + 0.14f * k)
                drawCircle(bead, radius = 4f, center = Offset.Zero, alpha = 0.45f, style = ring)
            }
        }
        // The orbs in their slots (the newest one may still be flying in).
        for (i in 0 until len) {
            val sx = slots[i * 2] - ox + shake
            val sy = slots[i * 2 + 1] - oy
            if (sx.isNaN()) continue
            var x = sx
            var y = sy
            var sc = 1f
            var a = 1f
            val age = t - born[i]
            if (!fromX[i].isNaN() && age < FLIGHT_MS) {
                // The flight: a curve from the key, up through a high point, down into the slot (a "quadratic Bézier"
                // bent so it passes through the page's halfway keyframe), growing big mid-air, then settling.
                val p = FlightEase.transform((age / FLIGHT_MS).coerceIn(0f, 1f))
                val ax = fromX[i]
                val ay = fromY[i]
                val mx = sx + 0.4f * (ax - sx)
                val my = sy + 0.55f * (ay - sy) - 70f * u
                val cx = 2f * mx - (ax + sx) / 2f
                val cy = 2f * my - (ay + sy) / 2f
                val m = 1f - p
                x = m * m * ax + 2f * m * p * cx + p * p * sx
                y = m * m * ay + 2f * m * p * cy + p * p * sy
                if (p < 0.55f) {
                    val f = p / 0.55f
                    sc = 0.45f + 0.9f * f
                    a = 0.2f + 0.8f * f
                } else sc = 1.35f - 0.35f * (p - 0.55f) / 0.45f
            }
            bobAt(t, i)
            orb(i, x + bob[0] * u, y + bob[1] * u, sc, a, 0f, 0f, u)
        }
        // Orbs on their way out.
        for (g in 0 until GHOSTS) {
            if (gKind[g] == 0) continue
            val age = (t - gT0[g]).coerceAtLeast(0f)
            val n = gSlot[g]
            bobAt(t, n)
            var x = gX[g] + bob[0] * u
            var y = gY[g] + bob[1] * u
            if (gKind[g] == 1) {
                // Deleted: shrinks and rises a little as it fades.
                val f = (age / LEAVE_MS).coerceIn(0f, 1f)
                val sc = 1f - 0.8f * f
                orb(n, x, y - 18f * f * sc * u, sc, 1f - f, 0f, 0f, u)
            } else {
                // Wrong PIN: flung away and drained of colour, then a wobble, then it fades out where it ended up.
                val dim = (age / 300f).coerceIn(0f, 1f)
                val turn: Float
                val a: Float
                when {
                    age < 420f -> {
                        val e = ScatterEase.transform(age / 420f)
                        x += gTx[g] * e
                        y += gTy[g] * e
                        turn = 50f * e
                        a = 1f - 0.4f * e
                    }
                    age < 960f -> {
                        val q = (age - 420f) / 540f * 5f
                        val j = q.toInt().coerceAtMost(4)
                        val f = q - j
                        x += gTx[g] + (WobbleX[j] + (WobbleX[j + 1] - WobbleX[j]) * f) * u
                        y += gTy[g] + (WobbleY[j] + (WobbleY[j + 1] - WobbleY[j]) * f) * u
                        turn = 50f + WobbleTurn[j] + (WobbleTurn[j + 1] - WobbleTurn[j]) * f
                        a = 0.6f
                    }
                    else -> {
                        x += gTx[g]
                        y += gTy[g]
                        turn = 50f
                        a = 0.6f * (1f - ((age - 960f) / 300f).coerceIn(0f, 1f))
                    }
                }
                orb(n, x, y, 1f, a, turn, dim, u)
            }
        }
        // The burst sparks: little glowing dots that fly out, shrink and fade.
        for (p in 0 until SPARKS) {
            if (pDur[p] <= 0f) continue
            val f = ((t - pT0[p]) / pDur[p]).coerceIn(0f, 1f)
            val e = SparkEase.transform(f)
            val c = OrbColours[pColour[p]]
            val at = Offset(pX[p] + pDx[p] * e, pY[p] + pDy[p] * e)
            val r = 2.5f * u * (1f - 0.8f * e)
            drawCircle(c, radius = r * 2.6f, center = at, alpha = 0.3f * (1f - e))
            drawCircle(c, radius = r, center = at, alpha = 1f - e)
        }
    }
}

/** One orb ([c] = its colour), centred on (x, y), drawn in page pixels scaled by [u]. [dim] 0–1 drains its colour. */
private fun DrawScope.orb(c: Int, x: Float, y: Float, sc: Float, alpha: Float, turn: Float, dim: Float, u: Float) {
    if (alpha <= 0.01f) return
    withTransform({ translate(x, y); rotate(turn, Offset.Zero); scale(sc * u, sc * u, Offset.Zero) }) {
        if (dim < 1f) orbBody(c, alpha * (1f - dim), null)
        if (dim > 0f) orbBody(c, alpha * dim, DimFilter)
    }
}

private fun DrawScope.orbBody(c: Int, alpha: Float, filter: ColorFilter?) {
    drawCircle(OrbHalos[c], radius = 58f, center = Offset.Zero, alpha = alpha, colorFilter = filter)
    drawCircle(OrbBodies[c], radius = 15f, center = Offset.Zero, alpha = alpha, colorFilter = filter)
    withTransform({ translate(-19f, 4.8f); rotate(24f, Offset.Zero) }) {
        drawRoundRect(OrbTails[c], topLeft = Offset(-13f, -3f), size = Size(26f, 6f), cornerRadius = CornerRadius(3f),
            alpha = alpha * 0.75f, colorFilter = filter)
    }
}

/** The little seelie at the top: a gold orb with a tail and a twinkle (the page's 40×40 drawing). */
@Composable
private fun SeelieMark() {
    Spacer(Modifier.size(40.dp).drawWithCache {
        val k = size.width / 40f
        val tail = Path().apply { moveTo(5f * k, 33f * k); quadraticTo(16f * k, 31f * k, 22f * k, 22f * k) }
        val twinkle = Path().apply {
            moveTo(33f * k, 7f * k)
            quadraticTo(33.4f * k, 9f * k, 35.4f * k, 9.4f * k)
            quadraticTo(33.4f * k, 9.8f * k, 33f * k, 11.8f * k)
            quadraticTo(32.6f * k, 9.8f * k, 30.6f * k, 9.4f * k)
            quadraticTo(32.6f * k, 9f * k, 33f * k, 7f * k)
            close()
        }
        val glow = Brush.radialGradient(0f to Color.White, 0.4f to SeelieGold, 1f to SeelieGold.copy(alpha = 0f),
            center = Offset(20.92f * k, 14.04f * k), radius = 15.4f * k)
        val line = Stroke(width = 2f * k, cap = StrokeCap.Round)
        onDrawBehind {
            drawPath(tail, SeelieGold.copy(alpha = 0.55f), style = line)
            drawCircle(glow, radius = 11f * k, center = Offset(24f * k, 18f * k))
            drawPath(twinkle, Color(0xFFFFF6DC))
        }
    })
}

/**
 * One key: an 80×60 rounded rectangle, a dark blue glass gradient with a thin gold shine along its top edge.
 * Pressed, it sinks 2dp, turns warm gold and gets a gold ring. Digit keys write their centre into [centres] so the
 * orbs know where to fly from.
 */
@Composable
private fun SeelieKey(
    label: String, centres: FloatArray? = null, index: Int = -1,
    accent: Boolean = false, quiet: Boolean = false, icon: Int? = null, onClick: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val lit by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 90 else 260), label = "seelie key")
    Box(
        Modifier
            .then(if (centres != null && index >= 0) Modifier.onGloballyPositioned {
                val c = it.boundsInRoot().center
                centres[index * 2] = c.x
                centres[index * 2 + 1] = c.y
            } else Modifier)
            .size(80.dp, 60.dp)
            .graphicsLayer { translationY = 2.dp.toPx() * lit }
            .pressFeel(press, look.accent, 19.dp)
            .drawWithCache {
                val r = CornerRadius(19.dp.toPx())
                val w = size.width
                val h = size.height
                val one = 1.dp.toPx()
                val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, r)) }
                // The shine: the key's shape minus itself moved 1dp down = a thin band along the top edge (the page's
                // "inset" shadow).
                val lower = Path().apply { addRoundRect(RoundRect(0f, one, w, h + one, r)) }
                val shine = Path().apply { op(shape, lower, PathOperation.Difference) }
                val rest = Brush.verticalGradient(listOf(Color(0xBD283054), Color(0xBD151B2E)))
                val down = Brush.verticalGradient(listOf(Color(0x57F3D79C), Color(0xCC3C3456)))
                val ringW = 4.dp.toPx()
                val ring = Stroke(width = ringW)
                val haze = Stroke(width = ringW * 2.5f)
                val edge = Stroke(width = one)
                onDrawBehind {
                    val k = lit
                    if (k > 0f) {
                        // The gold ring and a softer glow around it while pressed.
                        drawRoundRect(SeelieGold, Offset(-ringW * 1.25f, -ringW * 1.25f), Size(w + ringW * 2.5f, h + ringW * 2.5f),
                            CornerRadius(r.x + ringW * 1.25f), alpha = 0.12f * k, style = haze)
                        drawRoundRect(SeelieGold, Offset(-ringW / 2f, -ringW / 2f), Size(w + ringW, h + ringW),
                            CornerRadius(r.x + ringW / 2f), alpha = 0.16f * k, style = ring)
                    }
                    if (accent) drawPath(shape, look.accent)
                    else {
                        drawPath(shape, rest, alpha = 1f - k)
                        if (k > 0f) drawPath(shape, down, alpha = k)
                        drawPath(shine, SeelieGold, alpha = 0.28f)
                        drawPath(shape, Color.White, alpha = 0.08f, style = edge)
                    }
                }
            }
            .clickable(press, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = look.accent, modifier = Modifier.size(26.dp))
        else Text(label, style = TextStyle(
            fontFamily = if (label.length == 1 && label[0].isDigit()) Cinzel else look.font,
            fontWeight = if (accent) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = if (label.length > 1) 16.sp else 24.sp,
            color = when {
                accent -> look.sheet.copy(alpha = 1f)
                quiet -> look.accent
                else -> look.text
            },
        ))
    }
}
