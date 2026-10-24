package com.shamil.launcher.ui.livebg

import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.channels.Channel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * The living background's "world": every particle, how it moves each frame (step) and how it is drawn (draw).
 * A port of the mockup's updateWorld / drawBack / drawGL / drawFront (prototype/living-backgrounds.html),
 * with its speeds, sizes and fades. Positions are in the mockup's units: the screen is always 390 wide
 * (so a petal is the same share of the screen on any phone) and as tall as it needs to be.
 *
 * Cheap on purpose:
 *  - every particle is made once into a fixed "pool" (an array reused forever); nothing is created per frame;
 *  - shapes are pre-drawn sprites (LiveSprites), stamped with a move/turn/scale;
 *  - step() runs once per frame, draw() only in the draw phase, so Compose never recomposes for the animation.
 */
internal class LiveWorld {

    // ---------------------------------------------------------------- inputs

    /** His choices. Setting a new one wakes a sleeping loop. */
    var style: LiveBgStyle = LiveBgStyle()
        set(v) {
            if (v !== field) {
                field = v
                styleDirty = true
                wake.trySend(Unit)
            }
        }

    /** The real weather as one of W_CLEAR…W_MIST (used when the weather choice is "real"). */
    var realWeather = W_CLEAR

    /** True when the phone can run the sky shader (Android 13+ and it compiled). */
    var skyOn = false

    /** The tilt sensor's reading, −1…1 each way (written by TiltSensor). */
    var tiltTx = 0f
    var tiltTy = 0f

    /** The frame loop sleeps on this when nothing moves; a tap or a new style wakes it. */
    val wake = Channel<Unit>(Channel.CONFLATED)

    /** Uptime (ms) when the Glints Height slider last moved: the dashed band guide shows for 2 s after. */
    var glintGuideAt = 0L

    // ---------------------------------------------------------------- derived from the style

    private var styleDirty = true
    private val fxOn = BooleanArray(LiveFx.COUNT)
    private val fxAmt = FloatArray(LiveFx.COUNT)
    private var calm = true
    private var speed = 0.4f
    private var calmK = 0.6f
    private var glintH = 0.62f
    private var tiltK = 0f
    private var mood = 1
    private var forcedTime = -1
    private var forcedWeather = -1
    private var touchOn = false

    // ---------------------------------------------------------------- size

    private var pxW = 0f
    private var pxH = 0f
    /** Screen pixels per mockup unit. */
    private var k = 1f
    private var lw = 390f
    private var lh = 844f
    private var ready = false

    // ---------------------------------------------------------------- eased scene values

    private val tv = FloatArray(4)
    private val wv = FloatArray(5)
    private val cur = FloatArray(LiveFx.COUNT)
    private var snapped = false
    private var t = 0f
    private var clockTime = T_DUSK
    private var clockCheck = 0f
    private var cx = 0f
    private var cy = 0f
    /** Whether anything moved last step (false = the loop may sleep). */
    var busy = true
        private set

    private var sprites: LiveSprites? = null
    private var spriteMood = -1

    // ---------------------------------------------------------------- pools

    private class Petal { var x = 0f; var y = 0f; var z = 0f; var ph = 0f; var fph = 0f; var fv = 0f; var r = 0f; var rv = 0f; var vis = 0f; var sizeK = 1f }
    private class Settler {
        var leaf = false; var st = FALL; var x = 0f; var y = 0f; var z = 0f; var vy = 0f; var restY = 0f
        var ph = 0f; var fph = 0f; var fv = 0f; var r = 0f; var rv = 0f; var a = 0f; var life = 0f; var t = 0f; var vx = 0f
        var hidden = true; var seen = false; var spr = 0; var sizeK = 1f
    }
    private class Mote { var x = 0f; var y = 0f; var z = 0f; var h = 0f; var sp = 0f; var ph = 0f; var w = 0f; var om = 0f; var vis = 0f }
    private class Spore { var x = 0f; var y = 0f; var z = 0f; var vy = 0f; var ph = 0f; var pf = 0f; var vis = 0f }
    private class Flake { var x = 0f; var y = 0f; var z = 0f; var ph = 0f; var vis = 0f }
    private class Streak { var x = 0f; var y = 0f; var tier = 0; var v = 1f }
    private class Drop { var alive = false; var x = 0f; var y = 0f; var r = 0f; var t = 0f; var life = 0f; var sliding = false; var wait = 0f; var v = 0f; var trail = 0f; var y0 = 0f }
    private class Star { var x = 0f; var yk = 0f; var z = 0f; var ph = 0f; var sp = 0f; var r = 0f; var big = false; var warm = false; var vis = 0f }
    private class Lantern { var x = 0f; var y = 0f; var z = 0f; var ph = 0f; var vis = 0f }
    private class Seed { var x = 0f; var y = 0f; var z = 0f; var ph = 0f; var r = 0f; var vis = 0f }
    private class Butterfly {
        var x = 0f; var y = 0f; var h = 0f; var resting = false; var t = 0f; var rt = 0f; var tx = 0f; var ty = 0f
        var fp = 0f; var sz = 8f; var vis = 0f; var wob = 0f
    }
    private class Flock { var alive = false; var x = 0f; var y = 0f; var v = 0f; var dir = 1; var n = 0; var s = 1f; val dx = FloatArray(6); val dy = FloatArray(6); val ph = FloatArray(6) }
    private class Glint { var fx = 0f; var fy = 0f; var ph = 0f; var sp = 0f; var w = 0f; var vis = 0f }
    private class Spark { var alive = false; var ring = false; var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var t = 0f; var life = 0f; var s = 0f; var ph = 0f }

    private val petals = Array(64) { Petal() }
    private val fallen = Array(70) { Settler() }
    private val leaves = Array(40) { Settler().also { it.leaf = true } }
    private val motes = Array(56) { Mote() }
    private val spores = Array(30) { Spore() }
    private val snow = Array(150) { Flake() }
    private val rain = Array(200) { Streak() }
    private val drops = Array(14) { Drop() }
    private val stars = Array(140) { Star() }
    private val lanterns = Array(14) { Lantern() }
    private val seeds = Array(40) { Seed() }
    private val butterflies = Array(3) { Butterfly() }
    private val flocks = Array(2) { Flock() }
    private val glints = Array(150) { Glint() }
    private val sparks = Array(64) { Spark() }

    // lightning, gusts, shooting star
    private var flash = 0f
    private var flashT = 99f
    private var nextFlash = 4f
    private var doubleFlash = false
    private var boltOn = false
    private var boltT = 0f
    private val boltPath = Path()
    private val branchPath = Path()
    private var gust = 0f
    private var nextGust = 9f
    private var nextFlock = 4f
    private var shootOn = false
    private var shX = 0f; private var shY = 0f; private var shVx = 0f; private var shVy = 0f; private var shT = 0f
    private var nextShoot = 3f

    // re-used drawing helpers (made once)
    private val path = Path()
    private val rainPaths = arrayOf(Path(), Path(), Path())
    private val rainStrokes = Array(3) { Stroke(width = .6f + it * .45f, cap = StrokeCap.Round) }
    private val boltStrokes = floatArrayOf(7f, 2.6f, 1.1f, 3f, .9f).map { Stroke(width = it, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    private val seedStroke = Stroke(width = .55f, cap = StrokeCap.Round)
    private val birdStroke = Stroke(width = 1.2f, cap = StrokeCap.Round)
    private val ringStrokes = Array(9) { Stroke(width = .4f + it * .2f) }
    private val guideStroke = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
    /** The sky shader's six colours, three floats each (CL, CD, Glow, Tone, MistC, FlashC). */
    private val skyColours = FloatArray(18)

    // ================================================================ public

    /** A tap at ([px], [py]) screen pixels from the layer's top-left: a ring, sparkles, and nearby resting petals blown away. */
    fun tap(px: Float, py: Float) {
        if (!touchOn || !ready) return
        val x = px / k
        val y = py / k
        var made = 0
        for (s in sparks) {
            if (s.alive) continue
            s.alive = true; s.t = 0f; s.x = x; s.y = y
            if (made == 0) {
                s.ring = true; s.life = .9f
            } else {
                val a = rand(0f, 6.28f)
                val v = rand(18f, 60f)
                s.ring = false; s.vx = cos(a) * v; s.vy = sin(a) * v - 12f; s.life = rand(.7f, 1.2f); s.s = rand(2f, 4.2f); s.ph = rand(0f, 6f)
            }
            if (++made >= 15) break
        }
        for (arr in SETTLER_SETS) for (p in settlers(arr)) {
            if (!p.hidden && p.st == REST && abs(p.x - x) < 70f && abs(p.y - y) < 70f) {
                p.st = LIFT; p.t = 0f; p.vx = (p.x - x) * 1.2f + rand(-10f, 20f); p.vy = -rand(30f, 70f)
            }
        }
        busy = true
        wake.trySend(Unit)
    }

    /** One frame forward by [dt] real seconds. */
    fun step(dt: Float) {
        if (!ready) return
        if (styleDirty) applyStyle()
        val sp = speed * dt
        t += dt * speed

        // Time of day from the clock, checked every 15 s (it changes only a few times a day).
        clockCheck -= dt
        if (clockCheck <= 0f) { clockCheck = 15f; clockTime = clockTimeOfDay() }
        val time = if (forcedTime >= 0) forcedTime else clockTime
        val weather = if (forcedWeather >= 0) forcedWeather else realWeather
        if (!snapped) {
            // The very first frame starts in the right scene (no 2 s fade from dusk to day).
            for (i in 0 until 4) tv[i] = if (i == time) 1f else 0f
            for (i in 0 until 5) wv[i] = if (i == weather) 1f else 0f
            snapped = true
        }
        for (i in 0 until 4) ease(tv, i, if (i == time) 1f else 0f, dt, .5f)
        for (i in 0 until 5) ease(wv, i, if (i == weather) 1f else 0f, dt, .5f)

        // Each effect eases toward (his density × calm × the scene's gate).
        var anyTarget = false
        val all = LiveFx.ALL
        for (n in all.indices) {
            val info = all[n]
            val i = info.index
            if (i == LiveFx.TOUCH || i == LiveFx.TILT) continue
            var target = 0f
            if (fxOn[i] && (!info.shader || skyOn || i == LiveFx.STORM)) {
                val g = when (i) {
                    LiveFx.RAIN -> wv[W_RAIN] + wv[W_STORM] * .9f
                    LiveFx.SNOW -> wv[W_SNOW]
                    LiveFx.STORM -> wv[W_STORM]
                    else -> gate(info)
                }
                target = fxAmt[i] * calmK * g
            }
            if (target > 0.001f) anyTarget = true
            ease(cur, i, target, dt, .35f)
        }

        // Tilt: glide toward the sensor reading.
        val glide = min(1f, dt * 5f)
        cx += (tiltTx * (if (tiltK > 0f) 1f else 0f) - cx) * glide
        cy += (tiltTy * (if (tiltK > 0f) 1f else 0f) - cy) * glide

        var moving = anyTarget
        for (c in cur) if (c > .003f) { moving = true; break }

        // A light breeze now and then lifts a few resting petals and leaves.
        nextGust -= dt
        if (nextGust <= 0f) {
            nextGust = rand(10f, 20f); gust = 1f
            for (arr in SETTLER_SETS) {
                val list = settlers(arr)
                var lifted = 0
                var tries = 0
                while (lifted < 5 && tries < 30) {
                    tries++
                    val p = list[Random.nextInt(list.size)]
                    if (!p.hidden && p.st == REST) { p.st = LIFT; p.t = 0f; p.vx = rand(30f, 70f); p.vy = -rand(30f, 75f); lifted++ }
                }
            }
        }
        gust = max(0f, gust - dt * .35f)
        val wnd = wind(t) * (1f + wv[W_STORM] * 1.6f + wv[W_RAIN] * .4f)

        stepPetals(dt, sp, wnd)
        if (stepSettlers(fallen, LiveFx.FALLEN, dt, sp, wnd)) moving = true
        if (stepSettlers(leaves, LiveFx.LEAVES, dt, sp, wnd)) moving = true
        stepMotes(dt, sp)
        stepSmall(dt, sp, wnd)
        stepRain(dt, sp, wnd)
        stepButterflies(dt)
        stepBirds(dt, sp)
        for (i in glints.indices) fadeVis(glints[i], active(LiveFx.GLINTS, i, glints.size), dt, 1.5f)

        // Touch sparkles.
        for (s in sparks) {
            if (!s.alive) continue
            moving = true
            s.t += dt
            if (!s.ring) { s.x += s.vx * dt; s.y += s.vy * dt; s.vy += 20f * dt; s.vx *= .4f.pow(dt) }
            if (s.t >= s.life) s.alive = false
        }

        stepLightning(dt)
        if (flash > .005f || boltOn || shootOn) moving = true
        busy = moving
    }

    /**
     * Draws everything into [scope]. [sky] + [skyLayer] = the half-size shader layer (null below Android 13).
     * Order like the mockup: weather wash, the back (lightning, stars), the sky shader, then the front.
     */
    fun draw(scope: DrawScope, sky: SkyShader?, skyLayer: GraphicsLayer?) = with(scope) {
        ensureSize(size)
        if (!ready) return@with
        if (styleDirty) applyStyle()
        val art = spritesFor(mood)

        // A slightly darker, cooler look while it rains or storms; a pale one in snow.
        val rainA = max(cur[LiveFx.RAIN], cur[LiveFx.STORM] * .95f)
        val dim = rainA * .12f + cur[LiveFx.STORM] * .08f
        if (dim > .004f) drawRect(DIM, alpha = min(1f, dim))
        if (cur[LiveFx.SNOW] > .01f) drawRect(SNOWY, alpha = cur[LiveFx.SNOW] * .06f)

        withTransform({ scale(k, k, Offset.Zero) }) { drawBack(art) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && sky != null && skyLayer != null) drawSky(sky, skyLayer)
        withTransform({ scale(k, k, Offset.Zero) }) { drawFront(art) }
    }

    // ================================================================ style, size, sprites

    private fun applyStyle() {
        styleDirty = false
        val s = style
        for (info in LiveFx.ALL) {   // only when the style changed
            val f = s.fx(info.id)
            fxOn[info.index] = s.enabled && f.on
            fxAmt[info.index] = f.density / 100f
        }
        glintH = s.fx("glints").height / 100f
        calm = s.calm
        speed = if (calm) .4f else 1f
        calmK = if (calm) .6f else 1f
        tiltK = if (fxOn[LiveFx.TILT]) fxAmt[LiveFx.TILT] else 0f
        touchOn = fxOn[LiveFx.TOUCH]
        mood = moodIndex(s.mood)
        forcedTime = TIME_IDS.indexOf(s.time)
        forcedWeather = WEATHER_IDS.indexOf(s.weather)
    }

    /** True while the tilt sensor is wanted (tilt on and something to move). */
    val wantsTilt: Boolean get() = tiltK > 0f && busy

    private fun ensureSize(s: Size) {
        if (s.width < 2f || s.height < 2f) return
        if (ready && s.width == pxW && s.height == pxH) return
        pxW = s.width; pxH = s.height
        k = pxW / 390f
        lw = 390f
        lh = pxH / k
        initPools()
        ready = true
    }

    private fun spritesFor(m: Int): LiveSprites {
        val have = sprites
        if (have != null && spriteMood == m) return have
        return LiveSprites(m).also { sprites = it; spriteMood = m }
    }

    private fun initPools() {
        for (p in petals) resetPetal(p, true)
        for (p in fallen) { p.hidden = true; p.seen = false }
        for (p in leaves) { p.hidden = true; p.seen = false }
        for (m in motes) { m.x = rand(0f, lw); m.y = rand(lh * .4f, lh); m.z = rand(.2f, 1f); m.h = rand(0f, 6.28f); m.sp = rand(5f, 15f); m.ph = rand(0f, 6.28f); m.w = rand(.4f, 1.1f); m.om = rand(.7f, 1.8f) }
        for (s in spores) { s.x = rand(0f, lw); s.y = rand(0f, lh); s.z = rand(.2f, 1f); s.vy = rand(6f, 16f); s.ph = rand(0f, 6.28f); s.pf = rand(.25f, .6f) }
        for (s in snow) { s.x = rand(0f, lw); s.y = rand(0f, lh); s.z = Random.nextFloat(); s.ph = rand(0f, 6.28f) }
        for (r in rain) { r.x = rand(-40f, lw + 40f); r.y = rand(0f, lh); r.tier = Random.nextInt(3); r.v = rand(.85f, 1.15f) }
        for (d in drops) d.alive = false
        for (s in stars) {
            val sd = Random.nextFloat()
            s.x = rand(0f, lw); s.yk = Random.nextFloat().pow(1.5f) * .56f; s.z = Random.nextFloat(); s.ph = rand(0f, 6.28f); s.sp = rand(.6f, 2.4f)
            s.big = sd > .86f; s.r = if (s.big) rand(1.1f, 1.8f) else rand(.5f, 1.1f); s.warm = Random.nextFloat() >= .35f
        }
        for (l in lanterns) { l.x = rand(0f, lw); l.y = rand(0f, lh); l.z = rand(.35f, 1f); l.ph = rand(0f, 6.28f) }
        for (s in seeds) { s.x = rand(0f, lw); s.y = rand(lh * .1f, lh * .95f); s.z = rand(.2f, 1f); s.ph = rand(0f, 6.28f); s.r = rand(0f, 6.28f) }
        for (b in butterflies) {
            b.x = rand(40f, lw - 40f); b.y = rand(lh * .6f, lh * .9f); b.h = rand(0f, 6.28f); b.resting = false; b.t = 0f; b.rt = 0f
            b.tx = rand(40f, lw - 40f); b.ty = rand(lh * .6f, lh * .9f); b.fp = rand(0f, 6.28f); b.sz = rand(7.5f, 10f); b.wob = rand(0f, 6.28f)
        }
        for (f in flocks) f.alive = false
        for (g in glints) { g.fx = Random.nextFloat(); g.fy = Random.nextFloat() * 2f - 1f; g.ph = rand(0f, 6.28f); g.sp = rand(.6f, 2.2f); g.w = rand(2f, 10f) }
        for (s in sparks) s.alive = false
    }

    // ================================================================ step helpers

    private fun gate(info: FxInfo): Float {
        var a = 1f
        val gt = info.gt
        if (gt != null) { a = 0f; for (i in 0 until 4) a += tv[i] * gt[i] }
        var b = 1f
        val gw = info.gw
        if (gw != null) { b = 0f; for (i in 0 until 5) b += wv[i] * gw[i] }
        return a * b
    }

    /** The mockup's active(): the first (density × max) particles of a pool are "on". */
    private fun active(fx: Int, i: Int, max: Int) = i < ceil(max * cur[fx]).toInt()

    private fun wind(t: Float) = 14f + sin(t * .23f) * 9f + sin(t * .57f + 1.3f) * 5f + (if (gust > 0f) gust * 36f else 0f)

    private fun resetPetal(p: Petal, init: Boolean) {
        p.x = rand(-20f, lw + 20f); p.y = if (init) rand(-20f, lh) else rand(-40f, -10f); p.z = Random.nextFloat().pow(1.2f)
        p.ph = rand(0f, 6.28f); p.fph = rand(0f, 6.28f); p.fv = rand(1.2f, 3.2f); p.r = rand(0f, 6.28f); p.rv = rand(-1.4f, 1.4f); p.sizeK = rand(.8f, 1.15f)
    }

    private fun stepPetals(dt: Float, sp: Float, wnd: Float) {
        for (i in petals.indices) {
            val p = petals[i]
            val on = active(LiveFx.PETALS, i, petals.size)
            p.vis += ((if (on) 1f else 0f) - p.vis) * min(1f, dt * 2f)
            if (p.vis < .01f && !on) continue
            p.fph += p.fv * sp; p.r += p.rv * sp
            p.x += (wnd * (.5f + p.z * .8f) + sin(t * .6f + p.ph) * 13f) * sp
            p.y += (14f + p.z * 28f) * (1f + wv[W_STORM] * .5f) * sp + sin(t * .9f + p.ph) * 3f * sp
            if (p.y > lh + 20f || p.x > lw + 40f) { p.y = rand(-30f, -10f); p.x = rand(-40f, lw * .8f) }
        }
    }

    /** The mockup's mkSettler, bottom edge only for now (the favourites ledge comes later). */
    private fun resetSettler(p: Settler, init: Boolean) {
        val z = rand(.3f, 1f)
        val restY = lh - rand(3f, 22f * z + 4f)
        val x0 = rand(0f, lw)
        val vy = if (p.leaf) rand(40f, 62f) else rand(36f, 58f)
        val y0 = if (init) rand(-10f, restY * .9f) else rand(-24f, -8f)
        p.st = FALL; p.x = (x0 - 2.2f * (restY - y0) / vy).coerceIn(-10f, lw + 10f); p.y = y0; p.z = z; p.vy = vy; p.restY = restY
        p.ph = rand(0f, 6.28f); p.fph = rand(0f, 6.28f); p.fv = rand(1.1f, 2.6f); p.r = rand(0f, 6.28f); p.rv = rand(-.9f, .9f)
        p.a = 0f; p.life = rand(10f, 24f); p.t = 0f; p.vx = 0f; p.spr = Random.nextInt(5); p.sizeK = rand(.85f, 1.2f)
        if (init && Random.nextFloat() < .4f) { p.st = REST; p.x = x0; p.y = restY; p.a = 1f; p.t = rand(0f, p.life * .6f); p.r = rand(-1.8f, 1.8f) }
    }

    /** Fall → rest (fades after 10–24 s) → fade; a breeze or a tap can "lift" a resting one. Returns true if any is visible. */
    private fun stepSettlers(arr: Array<Settler>, fx: Int, dt: Float, sp: Float, wnd: Float): Boolean {
        var any = false
        for (i in arr.indices) {
            val p = arr[i]
            val on = active(fx, i, arr.size)
            if (p.hidden) {
                if (!on) continue
                resetSettler(p, !p.seen); p.seen = true; p.hidden = false
                if (p.st == FALL) p.a = 0f
            }
            any = true
            if (p.x > lw + 40f || p.x < -40f) { p.st = FADE; p.t = 0f }
            if (!on && p.st != FADE && p.st != LIFT) { p.st = FADE; p.t = 0f }
            when (p.st) {
                FALL -> {
                    p.fph += p.fv * sp
                    if (p.leaf) { p.r = sin(t * 1.3f + p.ph) * .9f; p.x += (wnd * .15f + sin(t * 1.1f + p.ph) * 20f) * sp }
                    else { p.r += p.rv * sp; p.x += (wnd * .15f + sin(t * .7f + p.ph) * 10f) * sp }
                    p.y += p.vy * (1f + wv[W_STORM] * .4f) * sp
                    p.a = min(1f, p.a + dt * 1.5f)
                    if (p.y >= p.restY) { p.st = REST; p.t = 0f; p.r = rand(-.6f, .6f) * 3f }
                }
                REST -> { p.t += dt; if (p.t > p.life) { p.st = FADE; p.t = 0f } }
                LIFT -> {
                    p.t += dt; p.x += p.vx * sp; p.y += p.vy * sp; p.vy += 40f * sp; p.vx *= .9f.pow(dt * 8f); p.fph += 6f * sp; p.r += 1.5f * sp
                    if (p.t > 1.3f) { p.st = FALL; p.restY = lh - rand(3f, 22f * p.z + 4f); p.y = min(p.y, p.restY - 30f) }
                }
                else -> {
                    p.t += dt
                    p.a = max(0f, p.a - dt * (if (on) .4f else 1.2f))
                    if (p.a <= 0f) { if (on) { resetSettler(p, false); p.hidden = false } else p.hidden = true }
                }
            }
        }
        return any
    }

    private fun stepMotes(dt: Float, sp: Float) {
        for (i in motes.indices) {
            val m = motes[i]
            val on = active(LiveFx.MOTES, i, motes.size)
            m.vis += ((if (on) 1f else 0f) - m.vis) * min(1f, dt * 2f)
            if (m.vis < .01f && !on) continue
            m.h += sin(t * m.w + m.ph) * 1.6f * sp
            val vx = cos(m.h) * m.sp
            val vy = sin(m.h) * m.sp * .7f - 3f
            if (m.y < lh * .3f) m.h += .05f
            m.x += vx * sp; m.y += vy * sp
            if (m.x < -10f) m.x = lw + 10f
            if (m.x > lw + 10f) m.x = -10f
            if (m.y < lh * .28f) { m.y = lh * .28f; m.h = (PI / 2).toFloat() + rand(-.5f, .5f) }
            if (m.y > lh + 10f) { m.y = lh * .4f; m.x = rand(0f, lw) }
        }
    }

    /** Spores, snow, lanterns and dandelion seeds: simple drifters. */
    private fun stepSmall(dt: Float, sp: Float, wnd: Float) {
        for (i in spores.indices) {
            val s = spores[i]
            val on = active(LiveFx.SPORES, i, spores.size)
            s.vis += ((if (on) 1f else 0f) - s.vis) * min(1f, dt)
            if (s.vis < .01f && !on) continue
            s.y -= s.vy * (.5f + s.z * .7f) * sp; s.x += sin(t * .35f + s.ph) * 7f * sp
            if (s.y < -30f) { s.y = lh + 30f; s.x = rand(0f, lw) }
        }
        for (i in snow.indices) {
            val s = snow[i]
            val on = active(LiveFx.SNOW, i, snow.size)
            s.vis += ((if (on) 1f else 0f) - s.vis) * min(1f, dt * 1.5f)
            if (s.vis < .01f && !on) continue
            s.y += (18f + s.z * 46f) * sp; s.x += (sin(t * .8f + s.ph) * 10f + wnd * .3f) * sp
            if (s.y > lh + 10f) { s.y = -10f; s.x = rand(-20f, lw) }
            if (s.x > lw + 20f) s.x = -20f
            if (s.x < -20f) s.x = lw + 20f
        }
        for (i in lanterns.indices) {
            val l = lanterns[i]
            val on = active(LiveFx.LANTERNS, i, lanterns.size)
            l.vis += ((if (on) 1f else 0f) - l.vis) * min(1f, dt)
            if (l.vis < .01f && !on) continue
            l.y -= (7f + l.z * 9f) * sp; l.x += sin(t * .4f + l.ph) * 5f * sp + 2f * sp
            if (l.y < -30f) { l.y = lh + 30f; l.x = rand(10f, lw - 10f) }
        }
        for (i in seeds.indices) {
            val s = seeds[i]
            val on = active(LiveFx.DANDELIONS, i, seeds.size)
            s.vis += ((if (on) 1f else 0f) - s.vis) * min(1f, dt)
            if (s.vis < .01f && !on) continue
            s.x += (8f + s.z * 12f + wnd * .25f) * sp; s.y += (sin(t * .9f + s.ph) * 7f - 1.5f) * sp; s.r += .5f * sp
            if (s.x > lw + 20f) { s.x = -20f; s.y = rand(lh * .1f, lh * .95f) }
        }
    }

    /** Rain streaks and drops on the glass. Calm mode slows rain less (0.65×): at 0.4× it looks like slow motion. */
    private fun stepRain(dt: Float, sp0: Float, wnd: Float) {
        val rainA = max(cur[LiveFx.RAIN], cur[LiveFx.STORM] * .95f)
        val sp = if (calm) dt * .65f else sp0
        val n = ceil(rain.size * rainA).toInt()
        val sl = .22f + wv[W_STORM] * .25f + (wnd - 14f) * .002f
        for (i in 0 until n) {
            val r = rain[i]
            val spd = (520f + r.tier * 170f) * r.v
            r.x += spd * sl * sp; r.y += spd * sp
            if (r.y > lh + 30f) { r.y = rand(-80f, -10f); r.x = rand(-80f, lw + 10f) }
        }
        val target = ceil(drops.size * rainA).toInt()
        var alive = 0
        for (d in drops) {
            if (!d.alive) continue
            alive++
            d.t += dt
            if (d.sliding) {
                d.y += d.v * sp; d.v *= .35f.pow(dt * 1.4f)
                if (d.v < 4f) { d.sliding = false; d.wait = rand(.8f, 3.5f) }
            } else {
                d.wait -= dt
                if (d.wait <= 0f) { d.sliding = true; d.v = rand(60f, 130f); d.trail = max(d.trail, 0f) }
            }
            d.trail = max(0f, d.trail - dt * .25f)
            // Where its wet trail starts (set when it starts sliding, cleared once the trail has dried).
            if (d.sliding && d.y0 == 0f) d.y0 = d.y
            if (!d.sliding && d.trail <= 0f) d.y0 = 0f
            if (d.t > d.life || d.y > lh + 10f) d.alive = false
        }
        if (alive < target && Random.nextFloat() < dt * 2.2f) {
            val d = drops.firstOrNull { !it.alive }
            if (d != null) {
                d.alive = true; d.x = rand(14f, lw - 14f); d.y = rand(lh * .05f, lh * .8f); d.r = rand(2.2f, 5.4f); d.t = 0f; d.life = rand(6f, 12f)
                d.sliding = false; d.wait = rand(.4f, 2.5f); d.v = 0f; d.trail = 1f; d.y0 = 0f
            }
        }
    }

    private fun stepButterflies(dt: Float) {
        for (i in butterflies.indices) {
            val b = butterflies[i]
            val on = i < ceil(butterflies.size * cur[LiveFx.BUTTERFLIES] - .0001f).toInt()
            b.vis += ((if (on) 1f else 0f) - b.vis) * min(1f, dt * 1.2f)
            if (b.vis < .01f && !on) continue
            b.t += dt
            if (!b.resting) {
                val dx = b.tx - b.x
                val dy = b.ty - b.y
                val dist = hypot(dx, dy)
                val want = atan2(dy, dx) + sin(t * 2.3f + b.wob) * .9f
                val dh = atan2(sin(want - b.h), cos(want - b.h))
                b.h += dh * min(1f, dt * 3.2f)
                val v = (26f + sin(t * 3f + b.wob) * 10f) * speed
                b.x += cos(b.h) * v * dt; b.y += sin(b.h) * v * dt - sin(t * 9f + b.fp) * .15f
                b.fp += dt * 17f * speed
                if (dist < 14f) {
                    if (Random.nextFloat() < .65f) { b.resting = true; b.rt = rand(2.5f, 6f); b.t = 0f }
                    else { b.tx = rand(30f, lw - 30f); b.ty = rand(lh * .45f, lh * .93f) }
                }
            } else {
                b.fp += dt * 1.3f * speed
                if (b.t > b.rt) { b.resting = false; b.tx = rand(30f, lw - 30f); b.ty = rand(lh * .45f, lh * .93f) }
            }
        }
    }

    private fun stepBirds(dt: Float, sp: Float) {
        nextFlock -= dt * (.5f + cur[LiveFx.BIRDS] * 1.5f)
        if (cur[LiveFx.BIRDS] > .12f && nextFlock <= 0f) {
            val f = flocks.firstOrNull { !it.alive }
            if (f != null) {
                nextFlock = rand(10f, 22f)
                f.alive = true; f.n = Random.nextInt(3, 7); f.dir = if (Random.nextBoolean()) 1 else -1
                f.x = if (f.dir > 0) -40f else lw + 40f; f.y = rand(lh * .12f, lh * .36f); f.v = f.dir * rand(28f, 44f); f.s = rand(.8f, 1.3f)
                for (j in 0 until f.n) {
                    val rank = ceil(j / 2f)
                    f.dx[j] = -f.dir * rank * 10f; f.dy[j] = (if (j % 2 == 1) 1f else -1f) * rank * 5f; f.ph[j] = rand(0f, 6f)
                }
            }
        }
        for (f in flocks) {
            if (!f.alive) continue
            f.x += f.v * sp; f.y += sin(t * .4f) * 2f * sp
            if ((f.dir > 0 && f.x > lw + 60f) || (f.dir < 0 && f.x < -60f)) f.alive = false
        }
    }

    private fun stepLightning(dt: Float) {
        flash *= .02f.pow(dt * (if (calm) 1.2f else 5f))
        val storm = cur[LiveFx.STORM] > .15f
        if (storm && !calm) {
            nextFlash -= dt
            if (nextFlash <= 0f) {
                nextFlash = rand(3.5f, 9f); flash = .9f; flashT = 0f; doubleFlash = Random.nextFloat() < .5f
                makeBolt(rand(lw * .15f, lw * .85f), lh * .04f, rand(lh * .3f, lh * .5f)); boltT = 0f
            }
        } else if (storm) {
            // Calm mode: a soft glow behind the clouds now and then, never a flash or a bolt.
            nextFlash -= dt
            if (nextFlash <= 0f) { nextFlash = rand(5f, 10f); flash = .28f; boltOn = false }
        }
        flashT += dt
        if (doubleFlash && flashT > .13f && flashT < .2f && !calm) { flash = max(flash, .75f); doubleFlash = false }
        boltT += dt
        if (boltT > .32f) boltOn = false

        // A rare shooting star on clear nights (rarer still in calm mode).
        if (cur[LiveFx.STARS] > .3f) {
            nextShoot -= dt
            if (nextShoot <= 0f) {
                nextShoot = if (calm) rand(25f, 50f) else rand(9f, 20f)
                val a = rand(.35f, .6f)
                shootOn = true; shX = rand(lw * .1f, lw * .7f); shY = rand(lh * .04f, lh * .22f); shVx = cos(a) * 260f; shVy = sin(a) * 260f; shT = 0f
            }
        }
        if (shootOn) { shT += dt; shX += shVx * dt; shY += shVy * dt; if (shT > .9f) shootOn = false }
    }

    /** A zig-zag bolt from the top with a few short branches, built into two re-used paths. */
    private fun makeBolt(x: Float, y0: Float, y1: Float) {
        boltPath.reset(); branchPath.reset()
        var y = y0
        var xx = x
        boltPath.moveTo(xx, y)
        var i = 0
        while (y < y1 && i < 60) {
            y += rand(14f, 30f); xx += rand(-16f, 16f); i++
            boltPath.lineTo(xx, y)
            // Every third point (from the second), a branch of three short steps.
            if (i >= 2 && (i - 2) % 3 == 0 && y < y1) {
                var bx = xx
                var by = y
                branchPath.moveTo(bx, by)
                repeat(3) { bx += rand(-14f, 22f); by += rand(10f, 20f); branchPath.lineTo(bx, by) }
            }
        }
        boltOn = true
    }

    private fun ease(arr: FloatArray, i: Int, target: Float, dt: Float, tau: Float) {
        arr[i] += (target - arr[i]) * (1f - exp(-dt / tau))
    }

    private fun fadeVis(g: Glint, on: Boolean, dt: Float, rate: Float) {
        g.vis += ((if (on) 1f else 0f) - g.vis) * min(1f, dt * rate)
    }

    private fun settlers(which: Int) = if (which == 0) fallen else leaves

    // ================================================================ drawing

    /** Parallax: how far a layer at depth [z] (0 far … 1 near) shifts with the tilt. */
    private fun tiltX(z: Float) = cx * tiltK * (3f + 17f * z)
    private fun tiltY(z: Float) = cy * tiltK * (2f + 10f * z)

    /**
     * Stamps [img] centred at ([px], [py]), [w]×[h] units, turned [rot] radians, squashed by [flipY]
     * (a petal tumbling = its height going through cos(...)), at [alpha]. The mockup's drawSpr.
     */
    private fun DrawScope.stamp(img: ImageBitmap, px: Float, py: Float, w: Float, h: Float, rot: Float, flipY: Float, alpha: Float, blend: BlendMode = BlendMode.SrcOver) {
        if (alpha < .004f) return
        val iw = img.width.toFloat()
        val ih = img.height.toFloat()
        withTransform({
            translate(px, py)
            if (rot != 0f) rotate(rot * RAD_TO_DEG, Offset.Zero)
            scale(w / iw, h / ih * flipY, Offset.Zero)
        }) {
            drawImage(img, Offset(-iw / 2f, -ih / 2f), alpha = min(1f, alpha), blendMode = blend)
        }
    }

    /** A glow dot [sz] units wide, added on top (light adds up, like the mockup's "lighter"). */
    private fun DrawScope.glowAt(img: ImageBitmap, px: Float, py: Float, sz: Float, alpha: Float) =
        stamp(img, px, py, sz, sz, 0f, 1f, alpha, BlendMode.Plus)

    private fun DrawScope.drawBack(art: LiveSprites) {
        // The lightning's wash of light from behind the clouds.
        if (flash > .01f) glowAt(art.flashWash, lw * .5f, lh * .12f, lh * 1.4f, flash * .55f)
        // The bolt itself, three strokes (glow, body, core) plus branches, fading over 0.3 s.
        if (boltOn && boltT < .3f) {
            val al = max(0f, 1f - boltT / .3f) * (if (Random.nextFloat() < .2f) .5f else 1f)
            val bc = if (mood == 2) BOLT_VIOLET else BOLT_WHITE
            drawPath(boltPath, bc, alpha = .18f * al, style = boltStrokes[0], blendMode = BlendMode.Plus)
            drawPath(boltPath, bc, alpha = .55f * al, style = boltStrokes[1], blendMode = BlendMode.Plus)
            drawPath(boltPath, bc, alpha = min(1f, .95f * al), style = boltStrokes[2], blendMode = BlendMode.Plus)
            drawPath(branchPath, bc, alpha = .12f * al, style = boltStrokes[3], blendMode = BlendMode.Plus)
            drawPath(branchPath, bc, alpha = .6f * al, style = boltStrokes[4], blendMode = BlendMode.Plus)
        }
        // Stars, twinkling; the bright ones get a small cross.
        val cs = cur[LiveFx.STARS]
        if (cs > .01f) {
            for (i in stars.indices) {
                val s = stars[i]
                val on = active(LiveFx.STARS, i, stars.size)
                s.vis += ((if (on) 1f else 0f) - s.vis) * .032f
                if (s.vis < .02f) continue
                val tw = .35f + .65f * (.5f + .5f * sin(t * s.sp + s.ph)).let { it * it }
                val a = min(1f, tw * s.vis * min(1f, cs * 1.4f + .2f))
                val px = s.x + tiltX(s.z * .2f)
                val py = s.yk * lh + tiltY(s.z * .2f)
                val c = if (s.warm) STAR_WARM else Color.White
                drawCircle(c, s.r, Offset(px, py), alpha = a, blendMode = BlendMode.Plus)
                if (s.big && tw > .7f) {
                    drawRect(c, Offset(px - s.r * 4f, py - .4f), Size(s.r * 8f, .8f), alpha = a * .7f, blendMode = BlendMode.Plus)
                    drawRect(c, Offset(px - .4f, py - s.r * 4f), Size(.8f, s.r * 8f), alpha = a * .7f, blendMode = BlendMode.Plus)
                }
            }
            if (shootOn) {
                val len = hypot(shVx, shVy)
                val kk = sin(PI.toFloat() * min(1f, shT / .9f))
                val ux = shVx / len
                val uy = shVy / len
                // The tail as six pieces, each fainter (a gradient without making a new brush every frame).
                for (j in 0 until 6) {
                    val a0 = 90f * j / 6f
                    val a1 = 90f * (j + 1) / 6f
                    drawLine(SHOOT, Offset(shX - ux * a0, shY - uy * a0), Offset(shX - ux * a1, shY - uy * a1), 1.6f, StrokeCap.Round,
                        alpha = .95f * kk * (1f - j / 6f), blendMode = BlendMode.Plus)
                }
                glowAt(art.glowStar, shX, shY, 14f, kk)
            }
        }
    }

    /**
     * The sky shader, drawn into a layer HALF the screen's size and stretched back up 2×: a quarter of the pixels
     * to compute, and clouds and mist are soft anyway. The layer is an "offscreen" one, so the GPU really renders it
     * small first. Re-recorded every frame, because the shader's numbers (time, tilt…) change.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun DrawScope.drawSky(sky: SkyShader, layer: GraphicsLayer) {
        val clouds = min(1f, cur[LiveFx.CLOUDS] * 1.3f)
        val mist = cur[LiveFx.MIST]
        val aurora = cur[LiveFx.AURORA]
        val rays = cur[LiveFx.RAYS] * 1.35f
        val fl = flash * min(1f, cur[LiveFx.STORM] * 2f)
        if (clouds <= .003f && mist <= .003f && aurora <= .003f && rays <= .003f && fl <= .003f) return
        val hw = max(1, (pxW * SKY_SCALE).toInt())
        val hh = max(1, (pxH * SKY_SCALE).toInt())
        fillSkyColours()
        val cover = (.3f + .55f * fxAmt[LiveFx.CLOUDS] + wv[W_RAIN] * .18f + wv[W_STORM] * .3f + wv[W_SNOW] * .12f).coerceIn(0f, 1f)
        sky.uniforms(hw.toFloat(), hh.toFloat(), t * .8f, cx * tiltK, cy * tiltK, clouds, cover, mist, aurora, rays, fl, skyColours)
        if (layer.compositingStrategy != CompositingStrategy.Offscreen) layer.compositingStrategy = CompositingStrategy.Offscreen
        layer.record(IntSize(hw, hh)) { drawRect(sky.brush) }
        withTransform({ scale(pxW / hw, pxH / hh, Offset.Zero) }) { drawLayer(layer) }
    }

    /** The sky's colours for the current mix of time of day, weather and mood (the mockup's PAL + tone mixing). */
    private fun fillSkyColours() {
        val tone = MOOD_TONES[mood]
        val tr = tone.red; val tg = tone.green; val tb = tone.blue
        val over = wv[W_RAIN] * .45f + wv[W_STORM] * .7f + wv[W_SNOW] * .18f + wv[W_MIST] * .1f
        val storm = wv[W_STORM]
        for (ch in 0 until 3) {
            var cl = 0f; var cd = 0f; var gl = 0f; var mc = 0f
            for (ti in 0 until 4) {
                val o = ti * 12 + ch
                cl += tv[ti] * PAL[o]; cd += tv[ti] * PAL[o + 3]; gl += tv[ti] * PAL[o + 6]; mc += tv[ti] * PAL[o + 9]
            }
            val tc = when (ch) { 0 -> tr; 1 -> tg; else -> tb }
            skyColours[ch] = lerp(cl, tc * .95f, storm * .35f) * (1f - over * .3f)
            skyColours[3 + ch] = lerp(cd, tc * .25f, storm * .4f) * (1f - over * .25f)
            skyColours[6 + ch] = lerp(gl, tc, .28f)
            skyColours[9 + ch] = tc
            skyColours[12 + ch] = mc
        }
        val violet = mood == 2
        skyColours[15] = if (violet) .8f else .88f
        skyColours[16] = if (violet) .66f else .92f
        skyColours[17] = 1f
    }

    private fun DrawScope.drawFront(art: LiveSprites) {
        drawGlints()
        drawGlows(art)
        drawLanterns(art)
        drawPetals(art)
        drawSettlers(art, fallen)
        drawSettlers(art, leaves)
        drawSeeds(art)
        drawButterflies(art)
        drawBirds()
        drawSnow(art)
        drawRain(art)
        drawTouch(art)
    }

    private fun DrawScope.drawGlints() {
        val cg = cur[LiveFx.GLINTS]
        val yb = lh * (.45f + glintH * .47f)
        val bh = lh * .09f
        if (cg > .01f) {
            val gc = glowColour()
            val path = lw * (.42f + sin(t * .05f) * .04f)
            // The soft band of light on the water, as 16 thin strips (a gradient without a new brush every frame).
            for (j in 0 until 16) {
                val f = (j + .5f) / 16f
                val a = .11f * cg * (1f - abs(f - .5f) * 2f)
                drawRect(gc, Offset(0f, yb - bh + f * 2f * bh - bh / 16f), Size(lw, bh / 8f), alpha = a, blendMode = BlendMode.Plus)
            }
            for (g in glints) {
                if (g.vis < .02f) continue
                val px = g.fx * lw
                val py = yb + g.fy * bh * .8f
                val d = (px - path) / (lw * .28f)
                val pr = exp(-d * d)
                val a = max(0f, sin(t * g.sp + g.ph)).pow(4) * (.25f + .75f * pr) * g.vis * min(1f, cg * 1.8f)
                if (a < .02f) continue
                val w = g.w * (.8f + abs(g.fy)) + pr * 10f
                val hh = 1f + abs(g.fy) * .7f
                drawOval(gc, Offset(px + tiltX(.6f) - w / 2f, py - hh), Size(w, hh * 2f), alpha = min(1f, a), blendMode = BlendMode.Plus)
            }
            // Slow shimmer lines sliding across.
            for (i in 0 until 9) {
                val ly = yb + (i / 8f - .5f) * bh * 1.5f
                val lx = ((t * (6f + i * 1.3f) + i * 61f) % (lw + 80f)) - 40f
                drawLine(gc, Offset(lx, ly), Offset(lx + 26f + i * 3f, ly), .8f, alpha = .1f * cg * (.5f + .5f * sin(t * .7f + i)), blendMode = BlendMode.Plus)
            }
        }
        // While he moves the Height slider: a dashed outline of the band.
        if (SystemClock.uptimeMillis() - glintGuideAt < 2000L) {
            drawRect(GUIDE, Offset(6f, yb - bh), Size(lw - 12f, bh * 2f), style = guideStroke)
        }
    }

    /** Glow spores and motes / fireflies: soft additive orbs. */
    private fun DrawScope.drawGlows(art: LiveSprites) {
        if (cur[LiveFx.SPORES] > .01f) {
            for (s in spores) {
                if (s.vis < .02f) continue
                val pu = .4f + .6f * (.5f + .5f * sin(t * s.pf * 6.28f + s.ph))
                val fadeY = sstep(-20f, lh * .25f, s.y) * sstep(lh + 40f, lh * .85f, s.y)
                val a = s.vis * pu * fadeY
                val px = s.x + tiltX(s.z)
                val py = s.y + tiltY(s.z)
                glowAt(art.glowSpore, px, py, 22f + s.z * 54f, a * .55f)
                glowAt(art.glowCore, px, py, 5f + s.z * 5f, a * .9f)
            }
        }
        if (cur[LiveFx.MOTES] > .01f) {
            for (m in motes) {
                if (m.vis < .02f) continue
                val bl = (.5f + .5f * sin(t * m.om * 1.6f + m.ph)).pow(1.6f)
                glowAt(art.glowTone, m.x + tiltX(m.z), m.y + tiltY(m.z), 12f + m.z * 22f, m.vis * (.25f + .75f * bl))
            }
        }
    }

    private fun DrawScope.drawLanterns(art: LiveSprites) {
        if (cur[LiveFx.LANTERNS] <= .01f) return
        for (l in lanterns) {
            if (l.vis < .02f) continue
            val fl = .85f + .15f * sin(t * 7f + l.ph) + .05f * sin(t * 17f + l.ph)
            val fa = sstep(-10f, lh * .2f, l.y) * sstep(lh + 30f, lh * .9f, l.y) * l.vis
            if (fa < .01f) continue
            val px = l.x + tiltX(l.z)
            val py = l.y + tiltY(l.z)
            val s = 8f + l.z * 14f
            glowAt(art.glowLantern, px, py, s * 5.5f, .55f * fl * fa)
            // The body sprite is 60×80 px at 50 px per "s".
            stamp(art.lantern, px, py, s * 60f / 50f, s * 80f / 50f, 0f, 1f, fa)
            val w = s
            val h = s * 1.28f
            drawRect(LANTERN_CORE, Offset(px - w * .12f, py - h * .35f), Size(w * .24f, h * .7f), alpha = min(1f, .5f * fl * fa), blendMode = BlendMode.Plus)
        }
    }

    private fun DrawScope.drawPetals(art: LiveSprites) {
        if (cur[LiveFx.PETALS] <= .01f) {
            var any = false
            for (p in petals) if (p.vis >= .02f) { any = true; break }
            if (!any) return
        }
        for (p in petals) {
            if (p.vis < .02f) continue
            val f = cos(p.fph)
            val px = p.x + tiltX(p.z)
            val py = p.y + tiltY(p.z)
            val s = (6f + p.z * 10f) * p.sizeK
            val near = p.z > .82f
            val lit = max(0f, sin(p.r * 2f + p.fph * .7f)).pow(9)
            val flip = max(.08f, abs(f)) * (if (f < 0f) -1f else 1f)
            val size = s * (if (near) 1.7f else 1f)
            stamp(if (near) art.petBlur else art.pet, px, py, size, size, p.r, flip, p.vis * (if (near) .6f else .95f))
            if (lit > .05f && !near) stamp(art.petLit, px, py, s, s, p.r, max(.08f, abs(f)), p.vis * lit * .9f)
        }
    }

    private fun DrawScope.drawSettlers(art: LiveSprites, arr: Array<Settler>) {
        for (p in arr) {
            if (p.hidden || p.a <= .01f) continue
            val leaf = p.leaf
            val rest = p.st == REST || (p.st == FADE && p.y >= p.restY - 1f)
            val f = if (rest) .5f else cos(p.fph)
            val px = p.x + tiltX(p.z)
            val py = p.y + tiltY(p.z)
            val s = (if (leaf) 9f else 6.5f) * (.7f + p.z * .7f) * p.sizeK * (if (leaf) 1.15f else 1f) * (if (rest) 1.25f else 1f)
            val img = if (leaf) art.leaves[p.spr] else if (rest || p.z > .8f) art.pet else art.petDark
            // A faint soft shadow under a resting one.
            if (rest) stamp(art.pet, px, py + 1.2f, s * 1.1f, s * .42f, p.r, .35f, p.a * .18f)
            stamp(img, px, py, s, s, if (leaf && !rest) p.r + (PI * .2).toFloat() else p.r, max(.1f, abs(f)) * (if (f < 0f) -1f else 1f), p.a * (if (rest) .92f else 1f))
            if (!leaf && !rest) {
                val lit = max(0f, sin(p.r * 2f + p.fph * .7f)).pow(9)
                if (lit > .05f) stamp(art.petLit, px, py, s, s, p.r, max(.1f, abs(f)), p.a * lit * .8f)
            }
        }
    }

    private fun DrawScope.drawSeeds(art: LiveSprites) {
        if (cur[LiveFx.DANDELIONS] <= .01f) return
        for (s in seeds) {
            if (s.vis < .02f) continue
            val px = s.x + tiltX(s.z)
            val py = s.y + tiltY(s.z)
            val r = 2.6f + s.z * 4.4f
            val a = s.vis * (.55f + .35f * s.z)
            val sx = cos(s.r * .7f)
            // Nine fine filaments of the "parachute", one path.
            path.reset()
            for (j in 0 until 9) {
                val an = -PI.toFloat() * .95f + j * PI.toFloat() * 1.9f / 8f
                path.moveTo(px, py)
                path.lineTo(px + cos(an) * r * (.5f + .5f * abs(sx)), py + sin(an) * r * .8f)
            }
            drawPath(path, Color.White, alpha = .9f * a, style = seedStroke)
            drawCircle(Color.White, .7f, Offset(px, py), alpha = .95f * a)
            drawLine(SEED_STALK, Offset(px, py), Offset(px - 1.2f, py + r * 1.2f), .55f, StrokeCap.Round, alpha = .6f * a)
            glowAt(art.flake, px, py, r * 3f, .25f * a)
        }
    }

    private fun DrawScope.drawButterflies(art: LiveSprites) {
        if (cur[LiveFx.BUTTERFLIES] <= .01f) return
        for (b in butterflies) {
            if (b.vis < .02f) continue
            val px = b.x + tiltX(.8f)
            val py = b.y + tiltY(.8f)
            val fl = if (!b.resting) abs(cos(b.fp)) else .5f + .5f * cos(b.fp) * .6f
            val s = b.sz / 10f
            glowAt(art.glowTone, px, py, b.sz * 3f, .2f * b.vis)
            val turn = if (!b.resting) b.h + (PI / 2).toFloat() else sin(t * .5f + b.wob) * .4f
            withTransform({
                translate(px, py)
                rotate(turn * RAD_TO_DEG, Offset.Zero)
                scale(s, s, Offset.Zero)
            }) {
                // Two wings: the right one, then the same picture mirrored; the flap squeezes them sideways.
                for (side in 0 until 2) {
                    val sd = if (side == 0) 1f else -1f
                    withTransform({ scale(sd * (.2f + .8f * fl) / WING_PX, 1f / WING_PX, Offset.Zero) }) {
                        drawImage(art.wing, Offset(0f, -WING_ORIGIN_Y), alpha = min(1f, b.vis))
                    }
                }
                drawOval(BODY, Offset(-1.2f, -9.5f), Size(2.4f, 13f), alpha = min(1f, b.vis))
            }
        }
    }

    private fun DrawScope.drawBirds() {
        if (cur[LiveFx.BIRDS] <= .02f) return
        val col = if (tv[T_DAY] > .5f) BIRD_DAY else if (tv[T_DAWN] > .5f) BIRD_DAWN else BIRD_DUSK
        for (f in flocks) {
            if (!f.alive) continue
            val edge = min(1f, min(f.x + 30f, lw + 30f - f.x) / 60f).coerceAtLeast(0f)
            path.reset()
            for (j in 0 until f.n) {
                val px = f.x + f.dx[j] + tiltX(.3f)
                val py = f.y + f.dy[j] + tiltY(.3f)
                val fl = sin(t * 7.5f + f.ph[j])
                val s = 5.5f * f.s
                path.moveTo(px - s, py + fl * s * .2f)
                path.quadraticTo(px - s * .45f, py - s * .55f * (.4f + fl * .6f), px, py)
                path.quadraticTo(px + s * .45f, py - s * .55f * (.4f + fl * .6f), px + s, py + fl * s * .2f)
            }
            drawPath(path, col, alpha = min(1f, .78f * min(1f, cur[LiveFx.BIRDS] * 1.6f) * edge), style = birdStroke)
        }
    }

    private fun DrawScope.drawSnow(art: LiveSprites) {
        if (cur[LiveFx.SNOW] <= .01f) return
        for (s in snow) {
            if (s.vis < .02f) continue
            val sz = 3f + s.z * 7f
            val near = s.z > .8f
            val d = sz * (if (near) 2.4f else 1f)
            stamp(if (near) art.flakeBlur else art.flake, s.x + tiltX(s.z), s.y + tiltY(s.z), d, d, 0f, 1f, s.vis * (if (near) .55f else .5f + s.z * .4f))
        }
    }

    private fun DrawScope.drawRain(art: LiveSprites) {
        val rainA = max(cur[LiveFx.RAIN], cur[LiveFx.STORM] * .95f)
        if (rainA <= .01f) return
        val n = ceil(rain.size * rainA).toInt()
        val sl = .22f + wv[W_STORM] * .25f + (wind(t) * (1f + wv[W_STORM] * 1.6f) - 14f) * .002f
        // Three depths of streaks, each one path (one draw call per depth).
        for (tier in 0 until 3) {
            val p = rainPaths[tier]
            p.reset()
            for (i in 0 until n) {
                val r = rain[i]
                if (r.tier != tier) continue
                val len = (10f + tier * 8f) * r.v
                val px = r.x + tiltX(.4f + tier * .25f)
                p.moveTo(px, r.y)
                p.lineTo(px - len * sl, r.y - len)
            }
            drawPath(p, RAIN_C, alpha = min(1f, min(1f, rainA * 1.4f) * (.16f + tier * .13f)), style = rainStrokes[tier])
        }
        // Drops on the glass, with a short wet trail while they slide.
        for (d in drops) {
            if (!d.alive) continue
            val a = min(1f, d.t * 1.5f) * min(1f, (d.life - d.t) * 1.2f) * min(1f, rainA * 2f)
            if (a < .02f) continue
            val px = d.x + tiltX(1f)
            val py = d.y + tiltY(1f)
            if (d.trail > 0f && d.y0 != 0f) {
                drawLine(TRAIL, Offset(px, d.y0 + tiltY(1f)), Offset(px, py), d.r * .35f, alpha = min(1f, a * d.trail * .5f * .35f))
            }
            stamp(art.drop, px, py, d.r * 3f, d.r * 3.5f, 0f, 1f, a)
        }
    }

    private fun DrawScope.drawTouch(art: LiveSprites) {
        val tone = art.tone
        for (p in sparks) {
            if (!p.alive) continue
            val u = p.t / p.life
            if (p.ring) {
                val rr = 6f + u * 46f
                val width = 1.6f * (1f - u) + .4f
                val stroke = ringStrokes[((width - .4f) / .2f).toInt().coerceIn(0, ringStrokes.size - 1)]
                drawCircle(tone, rr, Offset(p.x, p.y), alpha = (1f - u) * .8f, style = stroke, blendMode = BlendMode.Plus)
                glowAt(art.glowTone, p.x, p.y, rr * 2.4f, (1f - u) * .5f)
            } else {
                val tw = .6f + .4f * sin(p.t * 18f + p.ph)
                val a = min(1f, max(0f, (1f - u) * tw))
                val s = p.s * (1f - u * .4f)
                drawRect(SPARK, Offset(p.x - s, p.y - .5f), Size(s * 2f, 1f), alpha = a, blendMode = BlendMode.Plus)
                drawRect(SPARK, Offset(p.x - .5f, p.y - s), Size(1f, s * 2f), alpha = a, blendMode = BlendMode.Plus)
                glowAt(art.glowTone, p.x, p.y, s * 4f, a * .8f)
            }
        }
    }

    /** The warm/cool light colour of the current time of day (the mockup's glowRGB). */
    private fun glowColour(): Color {
        var r = 0f; var g = 0f; var b = 0f
        for (i in 0 until 4) { r += tv[i] * GLC[i * 3]; g += tv[i] * GLC[i * 3 + 1]; b += tv[i] * GLC[i * 3 + 2] }
        return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }

    private companion object {
        const val FALL = 0
        const val REST = 1
        const val LIFT = 2
        const val FADE = 3
        val SETTLER_SETS = intArrayOf(0, 1)
        const val RAD_TO_DEG = 57.29578f
        /** The sky shader's resolution: half the screen each way. */
        const val SKY_SCALE = .5f

        val DIM = Color(0xFF0A0E1E)
        val SNOWY = Color(0xFFC8D7F0)
        val BOLT_WHITE = Color(235, 240, 255)
        val BOLT_VIOLET = Color(210, 170, 255)
        val STAR_WARM = Color(255, 226, 170)
        val SHOOT = Color(255, 245, 215)
        val GUIDE = Color(0xFFF3D79C)
        val LANTERN_CORE = Color(0xFFFFE9A8)
        val SEED_STALK = Color(235, 225, 200)
        val BODY = Color(0xFF2A1636)
        val BIRD_DAY = Color(38, 42, 70)
        val BIRD_DAWN = Color(70, 52, 92)
        val BIRD_DUSK = Color(30, 18, 52)
        val RAIN_C = Color(200, 216, 255)
        val TRAIL = Color(210, 225, 255)
        val SPARK = Color(0xFFFFF3C8)

        /** Glow colour per time of day (dawn, day, dusk, night), 0–1. */
        val GLC = floatArrayOf(1f, .88f, .75f, 1f, 1f, 1f, 1f, .78f, .59f, .75f, .82f, 1f)

        /** The sky shader's palette per time of day: CL, CD, GL, MC (three floats each), dawn / day / dusk / night. */
        val PAL = floatArrayOf(
            1f, .84f, .70f, .50f, .40f, .66f, 1f, .76f, .58f, .98f, .80f, .82f,
            1f, 1f, 1f, .62f, .72f, .88f, 1f, .96f, .82f, .93f, .95f, 1f,
            1f, .56f, .64f, .20f, .12f, .37f, 1f, .62f, .52f, .92f, .66f, .78f,
            .56f, .60f, .96f, .10f, .09f, .24f, .68f, .74f, 1f, .46f, .50f, .80f,
        )

        fun rand(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
        fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

        /** Smooth 0→1 between [e0] and [e1] (also works backwards, e0 > e1). */
        fun sstep(e0: Float, e1: Float, x: Float): Float {
            val u = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return u * u * (3f - 2f * u)
        }
    }
}
