package com.shamil.launcher.night

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
 * The night screen's living skies and scenes (25 Oct), drawn like the night-and-lock mock-up's canvases: the same
 * colours, counts and motion, ported from its JavaScript. One scene per page; it redraws only itself (its own layer),
 * only when the slow clock moves (~30 frames a second, only while the screen saver runs).
 * Sizes follow the mock-up's "k" (the screen's short side / 260), so they scale with the phone.
 */

/** Every scene a page can stand on: the eight skies he can pick, plus the trail and the rainy window. */
internal enum class SceneKind { CONSTEL, STARFALL, AURORA, DUSK, FIREFLIES, LANTERNS, PETALS, RAIN, WINDOW, TRAIL }

internal fun NightSky.scene(): SceneKind = when (this) {
    NightSky.CONSTELLATION -> SceneKind.CONSTEL
    NightSky.STARFALL -> SceneKind.STARFALL
    NightSky.AURORA -> SceneKind.AURORA
    NightSky.DUSK -> SceneKind.DUSK
    NightSky.FIREFLIES -> SceneKind.FIREFLIES
    NightSky.LANTERNS -> SceneKind.LANTERNS
    NightSky.PETALS -> SceneKind.PETALS
    NightSky.RAIN -> SceneKind.RAIN
}

internal fun sceneFor(kind: SceneKind): Scene = when (kind) {
    SceneKind.CONSTEL -> ConstelScene(fall = false)
    SceneKind.STARFALL -> ConstelScene(fall = true)
    SceneKind.AURORA -> AuroraScene()
    SceneKind.DUSK -> DuskScene()
    SceneKind.FIREFLIES -> FirefliesScene()
    SceneKind.LANTERNS -> LanternsScene()
    SceneKind.PETALS -> PetalsScene()
    SceneKind.RAIN -> RainScene(mugX = 0.2f)
    SceneKind.WINDOW -> RainScene(mugX = 0.9f)
    SceneKind.TRAIL -> TrailScene()
}

/** A scene filling its box, on its own layer, moving with [seconds]. [setup] lets a page hand it extras (the trail's rest spot). */
@Composable
internal fun SceneLayer(kind: SceneKind, seconds: FloatState, modifier: Modifier = Modifier, setup: (Scene) -> Unit = {}) {
    val scene = remember(kind) { sceneFor(kind) }
    setup(scene)
    Box(modifier.graphicsLayer { }.drawBehind { scene.draw(this, seconds.floatValue) })
}

/**
 * One scene. It builds its stars, drops and so on for the box's size (again when the size changes, e.g. on rotating),
 * then paints a frame at time t. dt = the time since the last frame (for things that fall or rise), at most 0.1 s.
 */
internal abstract class Scene {
    private var w = 0f
    private var h = 0f
    protected var k = 1f
    private var last = -1f

    fun draw(scope: DrawScope, t: Float) {
        val s = scope.size
        if (s.width < 2f || s.height < 2f) return
        if (s.width != w || s.height != h) {
            w = s.width; h = s.height; k = min(w, h) / 260f
            setup(w, h, Random(SEED))
        }
        val dt = if (last < 0f) 0f else (t - last).coerceIn(0f, 0.1f)
        last = t
        scope.paint(w, h, t, dt)
    }

    protected abstract fun setup(w: Float, h: Float, r: Random)
    protected abstract fun DrawScope.paint(w: Float, h: Float, t: Float, dt: Float)

    private companion object { const val SEED = 7 }
}

// ---------------------------------------------------------------------------------------------------------------
// Small drawing helpers (shared)

internal const val TAU = (2 * PI).toFloat()
private val CreamStar = Color(0xFFFFF4D6)

/** A little four-point twinkle (the mock-up's "flare"): two thin crossing lines. */
internal fun DrawScope.flare(x: Float, y: Float, r: Float, a: Float) {
    if (a <= 0.01f) return
    val c = CreamStar.copy(alpha = a.coerceIn(0f, 1f))
    val sw = max(0.6f, r * 0.12f)
    drawLine(c, Offset(x - r, y), Offset(x + r, y), sw)
    drawLine(c, Offset(x, y - r), Offset(x, y + r), sw)
}

/** Draws a soft round sprite centred on (x, y), [size] wide. */
internal fun DrawScope.stamp(img: ImageBitmap, x: Float, y: Float, size: Float, alpha: Float, blend: BlendMode = BlendMode.SrcOver) {
    val s = size.roundToInt()
    if (s < 1 || alpha <= 0.004f) return
    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset((x - s / 2f).roundToInt(), (y - s / 2f).roundToInt()),
        IntSize(s, s), alpha = alpha.coerceIn(0f, 1f), blendMode = blend)
}

private fun bitmap(w: Int, h: Int, draw: (android.graphics.Canvas) -> Unit): ImageBitmap {
    val b = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    draw(android.graphics.Canvas(b))
    return b.asImageBitmap()
}

/** A round soft dot: colours (ARGB) at the given stops from the centre out. */
internal fun softDot(px: Int, colors: IntArray, stops: FloatArray): ImageBitmap = bitmap(px, px) { c ->
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(px / 2f, px / 2f, px / 2f, colors, stops, Shader.TileMode.CLAMP) }
    c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), p)
}

private fun argb(r: Int, g: Int, b: Int, a: Float) = android.graphics.Color.argb((a * 255).roundToInt().coerceIn(0, 255), r, g, b)

/** Sprites every scene may use, made once for the whole app. */
internal object NightSprites {
    /** Warm-white glow, full in the middle, fading out (stars, sparkles). */
    val glow by lazy { softDot(64, intArrayOf(argb(255, 250, 225, 0.95f), argb(255, 240, 200, 0.5f), argb(243, 215, 156, 0f)), floatArrayOf(0f, 0.35f, 1f)) }
    /** A plain white soft dot (tinted blobs are baked from it below). */
    fun blob(r: Int, g: Int, b: Int) = softDot(128, intArrayOf(argb(r, g, b, 1f), argb(r, g, b, 0f)), floatArrayOf(0f, 1f))
    /** The Seelie orb: halo and bright core. */
    val orbHalo by lazy { softDot(96, intArrayOf(argb(255, 240, 200, 0.45f), argb(243, 215, 156, 0f)), floatArrayOf(0f, 1f)) }
    val orbCore by lazy { softDot(96, intArrayOf(argb(255, 255, 255, 1f), argb(255, 240, 190, 0.95f), argb(243, 215, 156, 0f)), floatArrayOf(0f, 0.28f, 1f)) }
    /** A firefly: white-yellow heart, green glow. */
    val firefly by lazy {
        softDot(64, intArrayOf(argb(255, 250, 200, 1f), argb(210, 255, 140, 0.7f), argb(120, 220, 120, 0.18f), argb(80, 200, 120, 0f)),
            floatArrayOf(0f, 0.18f, 0.5f, 1f))
    }

    /** A paper lantern (the mock-up's lanternSprite, drawn 3× as sharp). */
    val lantern by lazy {
        val s = 3f
        bitmap((96 * s).toInt(), (128 * s).toInt()) { c ->
            c.scale(s, s)
            val w = 96f; val h = 128f
            val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(w / 2, h * .5f, w * .5f, intArrayOf(argb(255, 200, 110, .6f), argb(255, 150, 50, 0f)), null, Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, w, h, halo)
            val bx = w / 2; val top = h * .28f; val bot = h * .74f; val tw = w * .17f; val bw = w * .2f; val mid = (top + bot) / 2
            val body = android.graphics.Path().apply {
                moveTo(bx - tw, top); lineTo(bx + tw, top); quadTo(bx + bw * 1.45f, mid, bx + bw * .85f, bot)
                lineTo(bx - bw * .85f, bot); quadTo(bx - bw * 1.45f, mid, bx - tw, top); close()
            }
            c.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(bx, mid, w * .3f, intArrayOf(0xFFFFF6D2.toInt(), 0xFFFFC25C.toInt(), 0xFFE8772A.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
            })
            val rib = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = argb(150, 70, 20, .35f) }
            for (i in -2..2) c.drawPath(android.graphics.Path().apply { moveTo(bx + i * tw * .4f, top); quadTo(bx + i * bw * .75f, mid, bx + i * bw * .34f, bot) }, rib)
            val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7A3A1A.toInt() }
            c.drawRect(bx - tw - 1, top - 4, bx + tw + 1, top + 1, cap)
            c.drawRect(bx - bw * .85f, bot, bx + bw * .85f, bot + 5, cap)
            c.drawLine(bx, bot + 5, bx, bot + 16, Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.4f; color = 0xFFD58A3A.toInt() })
            c.drawCircle(bx, bot + 18, 2.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC25C.toInt() })
        }
    }

    /** One pink petal, pointing up, filling a 96 px square. */
    val petal by lazy {
        bitmap(96, 96) { c ->
            val z = 46f
            c.translate(48f, 48f)
            val path = android.graphics.Path().apply {
                moveTo(0f, -z); cubicTo(z * .95f, -z * .6f, z * .8f, z * .6f, 0f, z); cubicTo(-z * .8f, z * .6f, -z * .95f, -z * .6f, 0f, -z); close()
            }
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, -z, 0f, z, 0xFFFFD6E0.toInt(), 0xFFF29BB4.toInt(), Shader.TileMode.CLAMP)
            })
        }
    }
}

/** A star in a field: where (0–1), how big (mock-up units), twinkle speed and start, and gold or white. */
private class FieldStar(val x: Float, val y: Float, val r: Float, val p: Float, val sp: Float, val gold: Boolean)

private val StarWhite = Color(0xFFECF0FF)
private val StarGold = Color(0xFFF3D79C)

/** Shooting stars: one every [gap]–[gap]+[jit] seconds, each crossing in 0.9 s. */
internal class Shooter(private val gap: Float, private val jit: Float, private val r: Random) {
    private class Shot { var on = false; var x = 0f; var y = 0f; var t0 = 0f; var a = 0f }
    private val pool = Array(6) { Shot() }
    private var next = 2f + r.nextFloat() * 3f

    fun DrawScope.shoot(t: Float, w: Float, h: Float, k: Float) {
        if (t < next - gap - jit - 1f) next = t + 1f   // the clock went back (a new run): start fresh
        if (t > next) {
            pool.firstOrNull { !it.on }?.apply { on = true; x = .1f + r.nextFloat() * .6f; y = .04f + r.nextFloat() * .28f; t0 = t; a = .4f + r.nextFloat() * .35f }
            next = t + gap + r.nextFloat() * jit
        }
        for (q in pool) {
            if (!q.on) continue
            val u = (t - q.t0) / .9f
            if (u > 1f || u < 0f) { q.on = false; continue }
            val len = w * .26f
            val x = q.x * w + cos(q.a) * w * .5f * u
            val y = q.y * h + sin(q.a) * w * .5f * u
            val end = Offset(x - cos(q.a) * len, y - sin(q.a) * len)
            drawLine(Brush.linearGradient(listOf(Color(1f, 0.97f, 0.86f, 1f - u), Color(1f, 0.97f, 0.86f, 0f)), Offset(x, y), end),
                Offset(x, y), end, strokeWidth = 1.6f * k, cap = StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Constellation and Starfall

/** The deep blue night with twinkling white and gold stars; [fall] = Starfall (shooting stars every second or so). */
internal class ConstelScene(private val fall: Boolean) : Scene() {
    private var stars = emptyList<FieldStar>()
    private lateinit var sky: Brush
    private lateinit var goldGlow: Brush
    private lateinit var violetGlow: Brush
    private var shooters = emptyList<Shooter>()

    override fun setup(w: Float, h: Float, r: Random) {
        val n = (w * h / 1900f / 3f).roundToInt().coerceIn(60, 300)
        stars = List(n) { FieldStar(r.nextFloat(), r.nextFloat().pow(1.1f) * .95f, .35f + r.nextFloat().pow(3) * 1.5f, r.nextFloat() * TAU, .6f + r.nextFloat() * 1.8f, r.nextFloat() < .25f) }
        sky = Brush.verticalGradient(0f to Color(0xFF04071A), .55f to Color(0xFF0C1633), 1f to Color(0xFF1D1A47), startY = 0f, endY = h)
        goldGlow = Brush.radialGradient(listOf(Color(0x33F3D79C), Color(0x00F3D79C)), Offset(w * .5f, h * 1.05f), h * .75f)
        violetGlow = Brush.radialGradient(listOf(Color(0x21B47FDC), Color(0x00B47FDC)), Offset(w * .2f, h * .4f), w * .6f)
        shooters = if (fall) List(2) { Shooter(.5f, 1.1f, Random(11 + it)) } else listOf(Shooter(5f, 6f, Random(11)))
    }

    override fun DrawScope.paint(w: Float, h: Float, t: Float, dt: Float) {
        drawRect(sky)
        drawRect(goldGlow)
        drawRect(violetGlow)
        for (q in stars) {
            val a = .34f + .66f * (.5f + .5f * sin(t * q.sp + q.p))
            val x = q.x * w; val y = q.y * h
            drawCircle((if (q.gold) StarGold else StarWhite).copy(alpha = a), q.r * k * 1.1f, Offset(x, y))
            if (q.r > 1.35f) flare(x, y, q.r * k * 3.2f, a * .5f)
        }
        for (s in shooters) with(s) { shoot(t, w, h, k) }
    }
}

/**
 * StandBy's battery constellation (his pick): 14 stars along the top, one lights per ~7 % of battery, the next one
 * pulses while charging, and a spark runs along the lit line. Drawn over whatever sky he chose.
 * [shooting] adds the "now and then" shooting star (off over the skies that already have their own).
 */
internal class BatteryStars(private val shooting: Boolean) {
    private val count = 14
    private var cur = 0f
    private var last = -1f
    private val shooter = Shooter(5f, 6f, Random(21))

    fun DrawScope.paint(t: Float, battery: Float, charging: Boolean) {
        val w = size.width; val h = size.height
        if (w < 2f) return
        val k = min(w, h) / 260f
        val land = w > h
        val dt = if (last < 0f) 0f else (t - last).coerceIn(0f, .1f)
        last = t
        // The stars fill up to the battery level one by one when it starts (a little "waking up"), then follow it.
        cur += Math.signum(battery - cur) * min(abs(battery - cur), dt * .22f)
        fun px(i: Int): Float = (.07f + .86f * i / (count - 1)) * w
        fun py(i: Int): Float = ((if (land) .05f else .03f) + (if (land) .022f else .01f) * sin(i / (count - 1f) * PI.toFloat() * 2.3f)) * h
        val lit = cur * count
        val full = floor(lit + 1e-6f).toInt()
        val line = Color(0x80F3D79C)
        for (i in 1 until min(full, count)) drawLine(line, Offset(px(i - 1), py(i - 1)), Offset(px(i), py(i)), max(.8f, k), cap = StrokeCap.Round)
        if (full > 1) {
            val segs = min(full, count) - 1
            val u = ((t * .16f) % 1f) * segs
            val i = min(segs - 1, floor(u).toInt())
            val f = u - i
            val x = px(i) + (px(i + 1) - px(i)) * f
            val y = py(i) + (py(i + 1) - py(i)) * f
            stamp(NightSprites.glow, x, y, 16f * k, .95f)
        }
        for (i in 0 until count) {
            val x = px(i); val y = py(i)
            when {
                i < full -> {
                    val a = .75f + .25f * sin(t * 1.6f + i * 1.3f)
                    val r2 = 2.5f * k
                    stamp(NightSprites.glow, x, y, r2 * 8f, a * .6f)
                    drawCircle(Color(1f, .97f, .89f, a), r2, Offset(x, y))
                    flare(x, y, r2 * 3f, a * .6f)
                }
                i == full && cur < .999f && charging -> {
                    val fr = max(.2f, lit - full)
                    val a = fr * (.55f + .45f * sin(t * 4.5f))
                    val r2 = (1.6f + 1.6f * fr) * k
                    stamp(NightSprites.glow, x, y, r2 * 10f, a)
                    drawCircle(Color(1f, .94f, .78f, .4f + a * .6f), r2, Offset(x, y))
                }
                else -> drawCircle(Color(0x47D3BC8E), 1.2f * k, Offset(x, y))
            }
        }
        if (shooting) with(shooter) { shoot(t, w, h, k) }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Aurora

internal class AuroraScene : Scene() {
    private class Dot(val x: Float, val y: Float, val a: Float, val p: Float)
    private var dots = emptyList<Dot>()
    private lateinit var sky: Brush
    private lateinit var foot: Brush
    private var ribbons = emptyList<ImageBitmap>()

    override fun setup(w: Float, h: Float, r: Random) {
        dots = List(46) { Dot(r.nextFloat() * w, r.nextFloat() * h * .62f, .25f + r.nextFloat() * .6f, r.nextFloat() * TAU) }
        sky = Brush.verticalGradient(0f to Color(0xFF030815), .62f to Color(0xFF0A1A33), 1f to Color(0xFF0C2A33), startY = 0f, endY = h)
        foot = Brush.verticalGradient(listOf(Color(0x0002060C), Color(0xE602060C)), startY = h * .84f, endY = h)
        // One upright strip per ribbon colour: clear at the top, half at 72 %, full at the bottom (the mock-up's gradient).
        ribbons = listOf(intArrayOf(116, 194, 168), intArrayOf(180, 127, 220), intArrayOf(243, 215, 156)).map { (cr, cg, cb) ->
            bitmap(2, 128) { c ->
                c.drawRect(0f, 0f, 2f, 128f, Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, 128f, intArrayOf(argb(cr, cg, cb, 0f), argb(cr, cg, cb, .55f), argb(cr, cg, cb, 1f)),
                        floatArrayOf(0f, .72f, 1f), Shader.TileMode.CLAMP)
                })
            }
        }
    }

    override fun DrawScope.paint(w: Float, h: Float, t: Float, dt: Float) {
        drawRect(sky)
        for (q in dots) drawCircle(Color(.93f, .96f, 1f, q.a * (.6f + .4f * sin(t * 1.3f + q.p))), .55f * k, Offset(q.x, q.y))
        val col = max(3f, w / 300f)
        val cw = col.roundToInt() + 1
        for (i in 0 until 3) {
            val img = ribbons[i]
            var x = 0f
            while (x < w) {
                val u = x / w
                val y0 = h * (.34f + .08f * i) + sin(u * 5 + t * .35f * (1 + i * .2f) + i * 2) * h * .05f + sin(u * 11 - t * .5f + i) * h * .018f
                val len = h * (.2f + .09f * sin(u * 4 + t * .25f + i * 1.7f) + .04f * i)
                val s = sin(u * 7 + t * .6f + i * 3)
                val a = (.1f + .3f * s * s) * (if (i == 2) .65f else 1f)
                drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(x.roundToInt(), (y0 - len).roundToInt()),
                    IntSize(cw, (len + h * .008f).roundToInt().coerceAtLeast(1)), alpha = a, blendMode = BlendMode.Plus)
                x += col
            }
        }
        drawRect(foot, topLeft = Offset(0f, h * .84f))
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Gold dusk

internal class DuskScene : Scene() {
    private class Mist(val x: Float, val y: Float, val p: Float, val sp: Float, val r: Float, val c: Int, val a: Float)
    private class Speck(val x: Float, val y: Float, val p: Float, val r: Float)
    private class Dust(val x: Float, val ph: Float, val sp: Float, val r: Float)
    private var mists = emptyList<Mist>()
    private var specks = emptyList<Speck>()
    private var dust = emptyList<Dust>()
    private lateinit var sky: Brush
    private lateinit var warm: Brush
    private val blobs by lazy { listOf(NightSprites.blob(255, 177, 138), NightSprites.blob(180, 127, 220), NightSprites.blob(243, 215, 156), NightSprites.blob(255, 140, 160)) }

    override fun setup(w: Float, h: Float, r: Random) {
        mists = List(9) { Mist(r.nextFloat(), .3f + r.nextFloat() * .6f, r.nextFloat() * TAU, .05f + r.nextFloat() * .08f, .2f + r.nextFloat() * .25f, it % 4, .1f + r.nextFloat() * .1f) }
        specks = List(40) { Speck(r.nextFloat(), r.nextFloat() * .42f, r.nextFloat() * TAU, .4f + r.nextFloat() * .9f) }
        dust = List(30) { Dust(r.nextFloat(), r.nextFloat(), .012f + r.nextFloat() * .02f, .5f + r.nextFloat() * 1.1f) }
        sky = Brush.verticalGradient(0f to Color(0xFF15123F), .42f to Color(0xFF3E2470), .74f to Color(0xFFB2507F), 1f to Color(0xFF2B1A4A), startY = 0f, endY = h)
        warm = Brush.radialGradient(listOf(Color(0x29FFC896), Color(0x00FFC896)), Offset(w * .5f, h * .9f), h * .6f)
    }

    override fun DrawScope.paint(w: Float, h: Float, t: Float, dt: Float) {
        drawRect(sky)
        for (m in mists) {
            val x = (m.x + sin(t * m.sp + m.p) * .12f) * w
            val y = (m.y + cos(t * m.sp * 1.3f + m.p) * .07f) * h
            val big = m.r * max(w, h * .6f)
            stamp(blobs[m.c], x, y, big * 2f, m.a, BlendMode.Plus)
        }
        drawRect(warm, blendMode = BlendMode.Plus)
        for (q in specks) drawCircle(Color(1f, .96f, .88f, .25f + .5f * (.5f + .5f * sin(t * 1.2f + q.p))), q.r * k, Offset(q.x * w, q.y * h))
        for (d in dust) {
            val f = (t * d.sp + d.ph) % 1f
            val a = sin(f * PI.toFloat())
            drawCircle(Color(1f, .89f, .67f, a * .7f), d.r * k, Offset((d.x + sin(f * 5 + d.ph * 9) * .02f) * w, h * (1.02f - f * .8f)))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Fireflies

internal class FirefliesScene : Scene() {
    private class Fly(val x: Float, val y: Float, val sx: Float, val sy: Float, val p: Float, val bs: Float)
    private var flies = emptyList<Fly>()
    private lateinit var sky: Brush
    private val near = Path()
    private val far = Path()

    override fun setup(w: Float, h: Float, r: Random) {
        flies = List(28) { Fly(r.nextFloat(), .3f + r.nextFloat() * .6f, .25f + r.nextFloat() * .5f, .2f + r.nextFloat() * .4f, r.nextFloat() * TAU, .7f + r.nextFloat() * 1.2f) }
        sky = Brush.verticalGradient(0f to Color(0xFF03100B), .7f to Color(0xFF0A2216), 1f to Color(0xFF10331F), startY = 0f, endY = h)
        fun hill(path: Path, base: Float, f: Float) {
            path.reset(); path.moveTo(0f, h)
            var x = 0f
            while (x <= w) { val u = x / w; path.lineTo(x, h * (base + .035f * sin(u * 5 + f) + .025f * sin(u * 11 + f * 2))); x += 4f }
            path.lineTo(w, h); path.close()
        }
        hill(far, .84f, 0f)
        hill(near, .9f, 2f)
    }

    override fun DrawScope.paint(w: Float, h: Float, t: Float, dt: Float) {
        drawRect(sky)
        drawPath(far, Color(0xFF04120C))
        drawPath(near, Color(0xFF020A06))
        for (q in flies) {
            val x = (q.x + .05f * sin(t * q.sx + q.p) + .025f * sin(t * q.sx * 2.3f + q.p * 2)) * w
            val y = (q.y + .05f * sin(t * q.sy + q.p * 1.7f)) * h
            val pu = max(0f, sin(t * q.bs + q.p)).let { it * it }
            stamp(NightSprites.firefly, x, y, (10 + 16 * pu) * k, .18f + .82f * pu)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Lanterns

internal class LanternsScene : Scene() {
    private class Lamp(val x: Float, val z: Float, val off: Float, val ph: Float, val sp: Float)
    private class Speck(val x: Float, val y: Float, val p: Float, val r: Float)
    private var lamps = emptyList<Lamp>()
    private var specks = emptyList<Speck>()
    private lateinit var sky: Brush
    private lateinit var warm: Brush

    override fun setup(w: Float, h: Float, r: Random) {
        lamps = List(11) { Lamp(r.nextFloat(), .35f + r.nextFloat() * .65f, r.nextFloat(), r.nextFloat() * TAU, r.nextFloat()) }.sortedBy { it.z }
        specks = List(40) { Speck(r.nextFloat(), r.nextFloat() * .6f, r.nextFloat() * TAU, .4f + r.nextFloat() * .8f) }
        sky = Brush.verticalGradient(0f to Color(0xFF08061C), .55f to Color(0xFF1E1241), 1f to Color(0xFF4A2250), startY = 0f, endY = h)
        warm = Brush.radialGradient(listOf(Color(0x47FF965A), Color(0x00FF965A)), Offset(w * .5f, h * 1.05f), h * .8f)
    }

    override fun DrawScope.paint(w: Float, h: Float, t: Float, dt: Float) {
        drawRect(sky)
        drawRect(warm)
        for (q in specks) drawCircle(Color(1f, .94f, .86f, .2f + .4f * (.5f + .5f * sin(t * 1.2f + q.p))), q.r * k, Offset(q.x * w, q.y * h))
        val img = NightSprites.lantern
        for (q in lamps) {
            val p = ((t / 70f * (.5f + q.z * .7f) * (.8f + q.sp * .4f)) + q.off) % 1f
            val sw = min(w, h * .75f) * (.17f + .2f * q.z)
            val sh = sw * 1.333f
            val y = h * (1.08f - 1.2f * p) - sh / 2
            val x = (q.x + sin(t * .35f + q.ph) * .025f) * w
            val a = (.3f + .7f * q.z) * min(1f, p * 6) * min(1f, (1 - p) * 5) * (.88f + .12f * sin(t * 5 + q.ph))
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset((x - sw / 2).roundToInt(), y.roundToInt()),
                IntSize(sw.roundToInt().coerceAtLeast(1), sh.roundToInt().coerceAtLeast(1)), alpha = a.coerceIn(0f, 1f))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Petals

internal class PetalsScene : Scene() {
    private class Petal(val x: Float, val y0: Float, val sp: Float, val z: Float, val rot: Float, val ph: Float)
    private class Speck(val x: Float, val y: Float, val p: Float, val r: Float)
    private var petals = emptyList<Petal>()
    private var specks = emptyList<Speck>()
    private lateinit var sky: Brush
    private lateinit var moon: Brush

    override fun setup(w: Float, h: Float, r: Random) {
        petals = List(26) { Petal(r.nextFloat(), r.nextFloat(), .03f + r.nextFloat() * .04f, .4f + r.nextFloat() * .6f, r.nextFloat() * TAU, r.nextFloat() * TAU) }
        specks = List(34) { Speck(r.nextFloat(), r.nextFloat() * .5f, r.nextFloat() * TAU, .4f + r.nextFloat() * .9f) }
        sky = Brush.verticalGradient(0f to Color(0xFF120A24), .55f to Color(0xFF2E1646), 1f to Color(0xFF6A2F5E), startY = 0f, endY = h)
        moon = Brush.radialGradient(listOf(Color(0x38FFD6C8), Color(0x00FFD6C8)), Offset(w * .82f, h * .12f), w * .5f)
    }

    override fun DrawScope.paint(w: Float, h: Float, t: Float, dt: Float) {
        drawRect(sky)
        drawRect(moon)
        for (q in specks) drawCircle(Color(1f, .94f, .9f, .2f + .45f * (.5f + .5f * sin(t * 1.1f + q.p))), q.r * k, Offset(q.x * w, q.y * h))
        val img = NightSprites.petal
        for (p in petals) {
            val f = ((p.y0 + t * p.sp * .55f) % 1.12f) - .06f
            val x = (((p.x + sin(t * .6f * p.z + p.ph) * .05f + t * .004f) % 1f + 1f) % 1f) * w
            val y = f * h
            val z = (3.5f + 6 * p.z) * k
            val a = (.3f + .65f * p.z) * min(1f, f * 8 + .3f) * min(1f, (1.06f - f) * 8)
            if (a <= .01f) continue
            rotate(((p.rot + t * .8f * p.z) * 180f / PI).toFloat(), Offset(x, y)) {
                stamp(img, x, y, z * 2.1f, a)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Rain window (also the Rainy window page, with the mug on the right)

internal class RainScene(private val mugX: Float) : Scene() {
    private class Streak(var x: Float, var y: Float, val l: Float, val v: Float, val a: Float)
    private class Bead(var x: Float, var y: Float, val r: Float, var v: Float = 0f, var dist: Float = 0f, var tr: Float = 0f)
    private var streaks = emptyList<Streak>()
    private var beads = emptyList<Bead>()
    private var city: ImageBitmap? = null
    private lateinit var sill: Brush
    private lateinit var mugGlow: Brush
    private var rnd = Random(3)
    private val steam = Path()
    private val mug = Path()

    override fun setup(w: Float, h: Float, r: Random) {
        rnd = Random(5)
        val n = (w / (3f * k)).roundToInt().coerceIn(60, 420)
        streaks = List(n) { Streak(r.nextFloat() * w * 1.1f, r.nextFloat() * h, (8 + r.nextFloat() * 20) * k, (230 + r.nextFloat() * 280) * k, .1f + r.nextFloat() * .22f) }
        beads = List(18) { Bead(r.nextFloat() * w, h * (.05f + r.nextFloat() * .75f), (1.4f + r.nextFloat() * 3.2f) * k) }
        // The blurry city lights behind the glass never move: drawn once, at half size (they're soft anyway).
        val cw = (w / 2).roundToInt(); val ch = (h / 2).roundToInt(); val kk = k / 2
        city = bitmap(cw, ch) { c ->
            c.drawRect(0f, 0f, cw.toFloat(), ch.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, ch.toFloat(), intArrayOf(0xFF08111C.toInt(), 0xFF122338.toInt(), 0xFF1A2B3A.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
            })
            val cols = listOf(intArrayOf(255, 217, 160), intArrayOf(116, 194, 168), intArrayOf(229, 138, 160), intArrayOf(120, 170, 255), intArrayOf(255, 190, 120))
            for (i in 0 until 22) {
                val x = r.nextFloat() * cw; val y = ch * (.42f + r.nextFloat() * .38f); val rad = (10 + r.nextFloat() * 26) * kk
                val (cr, cg, cb) = cols[i % 5]
                c.drawCircle(x, y, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(x, y, rad, intArrayOf(argb(cr, cg, cb, .35f + r.nextFloat() * .3f), argb(cr, cg, cb, .12f), argb(cr, cg, cb, 0f)), floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP)
                })
            }
            c.drawRect(0f, 0f, cw.toFloat(), ch.toFloat(), Paint().apply {
                shader = LinearGradient(0f, ch * .55f, 0f, ch.toFloat(), argb(0, 0, 0, 0f), argb(0, 0, 0, .35f), Shader.TileMode.CLAMP)
            })
        }
        val sh = h * .095f
        sill = Brush.verticalGradient(listOf(Color(0xFF2A1D14), Color(0xFF0A0604)), startY = h - sh, endY = h)
        mugGlow = Brush.radialGradient(listOf(Color(0x38FFBE6E), Color(0x00FFBE6E)), Offset(w * mugX, h * .92f), w * .5f)
        val mx = w * mugX; val my = h - sh; val mw = 22 * k; val mh = 20 * k
        mug.reset(); mug.moveTo(mx - mw / 2, my - mh); mug.lineTo(mx + mw / 2, my - mh); mug.lineTo(mx + mw * .42f, my); mug.lineTo(mx - mw * .42f, my); mug.close()
    }

    override fun DrawScope.paint(w: Float, h: Float, t: Float, dt: Float) {
        city?.let { drawImage(it, IntOffset.Zero, IntSize(it.width, it.height), IntOffset.Zero, IntSize(w.roundToInt(), h.roundToInt())) }
        val lw = max(.7f, k * .8f)
        for (d in streaks) {
            d.y += d.v * dt; d.x -= d.v * dt * .12f
            if (d.y > h) { d.y = -d.l; d.x = rnd.nextFloat() * w * 1.1f }
            if (d.x < -10f) d.x += w
            drawLine(Color(.75f, .87f, 1f, d.a), Offset(d.x, d.y), Offset(d.x + d.l * .12f, d.y - d.l), lw)
        }
        // Drops on the glass: now and then one slides down a little, leaving a wet trail.
        for (b in beads) {
            if (b.v == 0f && rnd.nextFloat() < dt * .08f) { b.v = (10 + rnd.nextFloat() * 20) * k; b.dist = (20 + rnd.nextFloat() * 50) * k; b.tr = 0f }
            if (b.v > 0f) {
                val dy = b.v * dt
                b.y += dy; b.tr += dy
                if (b.tr > b.dist) b.v = 0f
                if (b.y > h * .86f) { b.y = h * .04f; b.tr = 0f; b.v = 0f }
                drawLine(Color(.78f, .88f, 1f, .13f), Offset(b.x, b.y - b.tr), Offset(b.x, b.y), b.r * .7f)
            }
            drawCircle(Color(.82f, .9f, 1f, .14f), b.r, Offset(b.x, b.y))
            drawArc(Color(1f, 1f, 1f, .35f), 162f, 126f, false, Offset(b.x - b.r, b.y - b.r), androidx.compose.ui.geometry.Size(b.r * 2, b.r * 2), style = Stroke(max(.6f, k * .5f)))
            drawCircle(Color(1f, 1f, 1f, .55f), b.r * .22f, Offset(b.x - b.r * .3f, b.y - b.r * .35f))
        }
        // The window frame, the sill and the mug.
        val fr = min(w, h) * .045f
        val frame = Color(0xFF03060B)
        drawRect(frame, size = androidx.compose.ui.geometry.Size(w, fr))
        drawRect(frame, size = androidx.compose.ui.geometry.Size(fr, h))
        drawRect(frame, topLeft = Offset(w - fr, 0f), size = androidx.compose.ui.geometry.Size(fr, h))
        val sh = h * .095f
        drawRect(sill, topLeft = Offset(0f, h - sh))
        drawRect(Color(0x59FFC882), topLeft = Offset(0f, h - sh), size = androidx.compose.ui.geometry.Size(w, max(1f, k * .8f)))
        val mx = w * mugX; val my = h - sh; val mw = 22 * k; val mh = 20 * k
        val cream = Color(0xFFE9DDC0)
        drawPath(mug, cream)
        drawArc(cream, -90f, 180f, false, Offset(mx + mw / 2 + 2 * k - mh * .28f, my - mh * .5f - mh * .28f),
            androidx.compose.ui.geometry.Size(mh * .56f, mh * .56f), style = Stroke(3 * k))
        drawRect(Color(0xE65A3214), topLeft = Offset(mx - mw / 2 + 1, my - mh), size = androidx.compose.ui.geometry.Size(mw - 2, 2.4f * k))
        for (i in 0 until 3) {
            val ph = (t * .35f + i / 3f) % 1f
            steam.reset()
            for (j in 0..10) {
                val u = j / 10f
                val yy = my - mh - u * 34 * k * (.4f + ph * .6f) - ph * 8 * k
                val xx = mx + (i - 1) * 5 * k + sin(u * 5 + t * 1.2f + i) * 3.4f * k
                if (j == 0) steam.moveTo(xx, yy) else steam.lineTo(xx, yy)
            }
            drawPath(steam, Color(1f, .94f, .84f, .32f * (1 - ph)), style = Stroke(2 * k, cap = StrokeCap.Round))
        }
        drawRect(mugGlow)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Seelie trail

/**
 * The Seelie orb wanders across a violet night leaving a ribbon of light and sparkles; every 28 s it glides to rest
 * above the clock ([rest], set by the page; a fixed spot until it's known), sleeps a few seconds (little z letters)
 * and wakes with a sparkle burst.
 */
internal class TrailScene : Scene() {
    private class Speck(val x: Float, val y: Float, val p: Float, val sp: Float, val r: Float)
    private var specks = emptyList<Speck>()
    private lateinit var sky: Brush
    private lateinit var glow: Brush
    private val pts = FloatArray(53 * 2)
    /** Where it sleeps, in this scene's pixels (above the clock); asked every frame, null = the mock-up's spot. */
    var rest: () -> Offset? = { null }
    /** The "z" letters, measured once by the page (big and small). */
    var zBig: TextLayoutResult? = null
    var zSmall: TextLayoutResult? = null

    override fun setup(w: Float, h: Float, r: Random) {
        specks = List(70) { Speck(r.nextFloat(), r.nextFloat() * .92f, r.nextFloat() * TAU, .5f + r.nextFloat() * 1.5f, .4f + r.nextFloat() * 1.1f) }
        sky = Brush.verticalGradient(0f to Color(0xFF090725), .55f to Color(0xFF231247), 1f to Color(0xFF4B2352), startY = 0f, endY = h)
        glow = Brush.radialGradient(listOf(Color(0x38F3D79C), Color(0x00F3D79C)), Offset(w * .5f, h * 1.05f), h * .8f)
    }

    private fun ease(x: Float) = x * x * (3 - 2 * x)

    /** The orb's spot (0–1) at time tt; into [out]. */
    private fun at(tt: Float, land: Boolean, rx: Float, ry: Float, out: FloatArray, i: Int) {
        val c = ((tt % CYCLE) + CYCLE) % CYCLE
        fun wx(u: Float) = .5f + .38f * sin(u * .45f + .5f)
        fun wy(u: Float) = (if (land) .46f else .5f) + (if (land) .24f else .27f) * sin(u * .71f + 1.3f)
        val (x, y) = when {
            c < 16f -> wx(c) to wy(c)
            c < 20f -> { val f = ease((c - 16) / 4); (wx(16f) + (rx - wx(16f)) * f) to (wy(16f) + (ry - wy(16f)) * f) }
            c < 25f -> rx to ry
            else -> { val f = ease((c - 25) / 3); (rx + (wx(0f) - rx) * f) to (ry + (wy(0f) - ry) * f) }
        }
        out[i] = x; out[i + 1] = y
    }

    override fun DrawScope.paint(w: Float, h: Float, t: Float, dt: Float) {
        val land = w > h
        drawRect(sky)
        drawRect(glow)
        for (q in specks) drawCircle(Color(.94f, .93f, 1f, .2f + .55f * (.5f + .5f * sin(t * q.sp + q.p))), q.r * k, Offset(q.x * w, q.y * h))
        val restPx = rest()
        val rx = restPx?.let { (it.x / w).coerceIn(.1f, .9f) } ?: .5f
        val ry = restPx?.let { (it.y / h).coerceIn(.06f, .9f) } ?: (if (land) .17f else .31f)
        val c = t % CYCLE
        val sleeping = c >= 19f && c < 25.2f
        // The ribbon: 52 short pieces behind the orb, from violet (old) to warm gold (new), a wide faint pass and a thin bright one.
        val n = 52
        for (i in 0..n) at(t - i * .05f, land, rx, ry, pts, i * 2)
        for (pass in 0..1) {
            for (i in n downTo 1) {
                val f = 1 - i / n.toFloat()
                val cr = (255 + (180 - 255) * (1 - f)) / 255f
                val cg = (236 + (127 - 236) * (1 - f)) / 255f
                val cb = (190 + (220 - 190) * (1 - f)) / 255f
                drawLine(Color(cr, cg, cb, if (pass == 1) f * .8f else f * .14f),
                    Offset(pts[i * 2] * w, pts[i * 2 + 1] * h), Offset(pts[i * 2 - 2] * w, pts[i * 2 - 1] * h),
                    strokeWidth = max(.6f, f * (if (pass == 1) 5f else 15f) * k), cap = StrokeCap.Round, blendMode = BlendMode.Plus)
            }
        }
        val tmp = FLOATS
        for (j in 0 until 12) {
            val tt = t - j * .28f - .1f
            at(tt, land, rx, ry, tmp, 0)
            val x = tmp[0] * w + sin(tt * 7 + j) * 7 * k
            val y = tmp[1] * h + cos(tt * 5 + j) * 7 * k
            val a = (1 - j / 12f) * .9f * (.4f + .6f * abs(sin(t * 5 + j * 1.7f)))
            if (a > .05f) {
                drawCircle(Color(1f, .96f, .84f, a), 1.3f * k, Offset(x, y))
                flare(x, y, 3.6f * k, a * .7f)
            }
        }
        val ox = pts[0] * w; val oy = pts[1] * h
        val pulse = if (sleeping) .72f + .1f * sin(t * 1.4f) else 1f + .1f * sin(t * 5f)
        val big = 17 * k * pulse
        stamp(NightSprites.orbHalo, ox, oy, big * 4.4f, 1f, BlendMode.Plus)
        stamp(NightSprites.orbCore, ox, oy, big * 2f, 1f, BlendMode.Plus)
        if (sleeping) {
            val f = (t * .5f) % 1f
            val a = sin(f * PI.toFloat()) * .8f
            zBig?.let { drawText(it, StarGold, Offset(ox + 12 * k + f * 8 * k, oy - 10 * k - f * 14 * k - it.size.height), alpha = a) }
            zSmall?.let { drawText(it, StarGold, Offset(ox + 22 * k + f * 6 * k, oy - 18 * k - f * 12 * k - it.size.height), alpha = a) }
        }
        if (c >= 25f && c < 25.8f) {
            val u = (c - 25) / .8f
            for (i in 0 until 8) {
                val an = i / 8f * TAU
                val d = u * 26 * k
                drawCircle(Color(1f, .96f, .84f, 1 - u), 1.6f * k, Offset(ox + cos(an) * d, oy + sin(an) * d))
            }
        }
    }

    private companion object {
        const val CYCLE = 28f
        val FLOATS = FloatArray(2)
    }
}
