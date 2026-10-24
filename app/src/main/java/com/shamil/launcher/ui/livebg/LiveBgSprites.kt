package com.shamil.launcher.ui.livebg

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI

/*
 * The little pictures the effects are made of (a "sprite" = a small pre-drawn image that gets stamped many times):
 * petals, leaves, glow dots, raindrops, snowflakes, a lantern and butterfly wings.
 * Drawn ONCE with Android's own canvas into bitmaps, the same shapes as the mockup's mkS() sprites,
 * so every frame only stamps finished images (cheap) instead of building shapes and gradients.
 * Most are drawn at 2× the mockup's 64px so they stay sharp on his 1440px-wide screen.
 */
internal class LiveSprites(mood: Int) {
    val pet = petal(0xFFFFE3EE, 0xFFF29AB8, 0x8CFFFFFF, 0f)
    val petLit = petal(0xFFFFFFFF, 0xFFFFD3E4, 0xCCFFFFFF, 0f)
    val petBlur = petal(0xFFFFD3E4, 0xFFF28AB0, 0x4DFFFFFF, 3.2f)
    val petDark = petal(0xFFF6B7CC, 0xFFD9769D, 0x4DFFFFFF, 0f)
    val leaves = arrayOf(
        leaf(true, 0xFFE5502E, 0xFF9E2418),
        leaf(true, 0xFFF29A38, 0xFFC85A1A),
        leaf(true, 0xFFF3C13F, 0xFFD5871E),
        leaf(false, 0xFFF7DE5A, 0xFFD9AF2A),
        leaf(true, 0xFFD13C2A, 0xFF7C1C16),
    )
    val drop = drop()
    val flake = softDot(32, floatArrayOf(0f, .35f, 1f), floatArrayOf(1f, .75f, 0f), 16f)
    val flakeBlur = softDot(48, floatArrayOf(0f, .6f, 1f), floatArrayOf(.7f, .35f, 0f), 22f)
    val lantern = lantern()

    /** Glow dots: the mood colour (motes, butterflies, touch), the spores' colour, a warm white core, the lantern glow. */
    val tone = MOOD_TONES[mood]
    val glowTone = glow(tone)
    val glowSpore = glow(if (mood == 0 || mood == 1) Color(150, 255, 170) else tone)
    val glowCore = glow(Color(255, 255, 235))
    val glowLantern = glow(Color(255, 170, 70))
    val glowStar = glow(Color(255, 235, 180))
    /** The lightning's wash of light behind the clouds, in the mood's flash colour. */
    val flashWash = wash(
        when (mood) {
            0 -> Color(240, 235, 255)
            1 -> Color(255, 200, 225)
            2 -> Color(205, 170, 255)
            else -> Color(200, 255, 225)
        }
    )
    /** One butterfly wing pair half (the right side), in the mood's two colours. */
    val wing = wing(
        when (mood) {
            0 -> 0xFFFFD27A to 0xFFFF9A4D
            1 -> 0xFFFFA0D0 to 0xFFC95BD6
            2 -> 0xFFC9A0FF to 0xFF6F6BFF
            else -> 0xFFB6FF8A to 0xFF3FD0A0
        }
    )

    private companion object {
        fun make(w: Int, h: Int, draw: (Canvas) -> Unit): ImageBitmap {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            draw(Canvas(b))
            return b.asImageBitmap().also { it.prepareToDraw() }
        }

        fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)

        /** The mockup's petalPath: a soft heart-shaped petal with a little notch, in a 64×64 box. */
        fun petalPath(): Path = Path().apply {
            moveTo(32f, 58f)
            cubicTo(14f, 42f, 6f, 22f, 16f, 8f)
            quadTo(24f, 2f, 30f, 10f)
            lineTo(32f, 14f)
            lineTo(34f, 10f)
            quadTo(40f, 2f, 48f, 8f)
            cubicTo(58f, 22f, 50f, 42f, 32f, 58f)
            close()
        }

        fun petal(c1: Long, c2: Long, vein: Long, blur: Float) = make(128, 128) { c ->
            c.scale(2f, 2f)
            val fill = paint().apply {
                shader = LinearGradient(0f, 10f, 0f, 58f, c1.toInt(), c2.toInt(), Shader.TileMode.CLAMP)
                if (blur > 0f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawPath(petalPath(), fill)
            val line = paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = vein.toInt() }
            c.drawLine(32f, 56f, 32f, 24f, line)
        }

        fun leaf(maple: Boolean, c1: Long, c2: Long) = make(128, 128) { c ->
            c.scale(2f, 2f)
            val fill = paint().apply { shader = LinearGradient(0f, 0f, 0f, 64f, c1.toInt(), c2.toInt(), Shader.TileMode.CLAMP) }
            val edge = paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; strokeJoin = Paint.Join.ROUND; color = 0x593C1400 }
            val vein = paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f }
            if (maple) {
                val pts = floatArrayOf(32f, 4f, 38f, 18f, 50f, 12f, 46f, 28f, 60f, 30f, 48f, 40f, 52f, 52f, 38f, 48f, 34f, 58f, 30f, 58f, 26f, 48f, 12f, 52f, 16f, 40f, 4f, 30f, 18f, 28f, 14f, 12f, 26f, 18f)
                val p = Path()
                for (i in pts.indices step 2) if (i == 0) p.moveTo(pts[i], pts[i + 1]) else p.lineTo(pts[i], pts[i + 1])
                p.close()
                c.drawPath(p, fill); c.drawPath(p, edge)
                vein.color = 0x80FFE6A0.toInt()
                c.drawLine(32f, 58f, 32f, 12f, vein); c.drawLine(32f, 44f, 16f, 32f, vein); c.drawLine(32f, 44f, 48f, 32f, vein)
                val stem = paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.6f; color = 0xFF6A3A18.toInt() }
                c.drawLine(32f, 58f, 32f, 63f, stem)
            } else {
                // Ginkgo: a fan.
                val p = Path().apply {
                    moveTo(32f, 60f); lineTo(30f, 42f); quadTo(6f, 38f, 4f, 12f); quadTo(18f, 22f, 30f, 16f)
                    lineTo(32f, 22f); lineTo(34f, 16f); quadTo(46f, 22f, 60f, 12f); quadTo(58f, 38f, 34f, 42f); close()
                }
                c.drawPath(p, fill); c.drawPath(p, edge)
                vein.color = 0x73FFFAC8
                c.drawLine(32f, 58f, 32f, 24f, vein); c.drawLine(32f, 44f, 18f, 24f, vein); c.drawLine(32f, 44f, 46f, 24f, vein)
            }
        }

        /** A raindrop on the glass: a faint lens with a dark lower rim, a bright upper rim and two highlights. */
        fun drop() = make(128, 128) { c ->
            c.scale(2f, 2f)
            c.drawCircle(32f, 32f, 24f, paint().apply { color = 0x1ABECDFF })
            val arc = RectF(10f, 10f, 54f, 54f)
            val stroke = paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f }
            stroke.color = 0x61000000
            c.drawArc(arc, (1.05 * 180).toFloat(), (0.70 * 180).toFloat(), false, stroke)
            stroke.color = 0xB3FFFFFF.toInt()
            c.drawArc(arc, (0.1 * 180).toFloat(), (0.6 * 180).toFloat(), false, stroke)
            val hi = paint()
            c.save(); c.rotate((-0.6 * 180 / PI).toFloat(), 24f, 22f)
            hi.color = 0xE6FFFFFF.toInt(); c.drawOval(RectF(19f, 19f, 29f, 25f), hi); c.restore()
            c.save(); c.rotate((-0.6 * 180 / PI).toFloat(), 38f, 40f)
            hi.color = 0x38FFFFFF; c.drawOval(RectF(32f, 36.5f, 44f, 43.5f), hi); c.restore()
        }

        /** A white dot fading out (snowflakes). [alphas] at [stops], out to [radius] px. */
        fun softDot(size: Int, stops: FloatArray, alphas: FloatArray, radius: Float) = make(size, size) { c ->
            val colors = IntArray(stops.size) { i -> Color.White.copy(alpha = alphas[i]).toArgb() }
            val half = size / 2f
            c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint().apply {
                shader = RadialGradient(half, half, radius, colors, stops, Shader.TileMode.CLAMP)
            })
        }

        /** The mockup's glow(): white in the middle, then the colour, fading to nothing. 64×64. */
        fun glow(c0: Color) = make(64, 64) { c ->
            val colors = intArrayOf(
                Color.White.toArgb(), c0.copy(alpha = .95f).toArgb(), c0.copy(alpha = .28f).toArgb(), c0.copy(alpha = 0f).toArgb(),
            )
            c.drawRect(0f, 0f, 64f, 64f, paint().apply {
                shader = RadialGradient(32f, 32f, 32f, colors, floatArrayOf(0f, .14f, .42f, 1f), Shader.TileMode.CLAMP)
            })
        }

        /** A wide soft light (the lightning wash): the colour in the middle fading out evenly. */
        fun wash(c0: Color) = make(64, 64) { c ->
            c.drawRect(0f, 0f, 64f, 64f, paint().apply {
                shader = RadialGradient(32f, 32f, 32f, c0.toArgb(), c0.copy(alpha = 0f).toArgb(), Shader.TileMode.CLAMP)
            })
        }

        /**
         * A Liyue lantern, 60×80 px, centred, drawn at 50 px per lantern "size" (so the engine scales it by size/50):
         * a warm gradient body with curved sides and dark bands top and bottom.
         */
        fun lantern() = make(60, 80) { c ->
            c.translate(30f, 40f)
            val s = 50f
            val w = s
            val h = s * 1.28f
            val body = Path().apply {
                moveTo(-w * .38f, -h / 2); lineTo(w * .38f, -h / 2)
                quadTo(w * .62f, 0f, w * .4f, h / 2); lineTo(-w * .4f, h / 2)
                quadTo(-w * .62f, 0f, -w * .38f, -h / 2); close()
            }
            c.drawPath(body, paint().apply {
                shader = LinearGradient(0f, -h / 2, 0f, h / 2, intArrayOf(0xFFFFF1B8.toInt(), 0xFFFFB447.toInt(), 0xFFE5702A.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            })
            val band = paint().apply { color = 0xFF5A2A12.toInt() }
            c.drawRect(-w * .4f, -h / 2 - s * .08f, w * .4f, -h / 2 + s * .04f, band)
            c.drawRect(-w * .4f, h / 2 - s * .04f, w * .4f, h / 2 + s * .08f, band)
        }

        /**
         * The right half of a butterfly (upper + lower wing), from the mockup's bezier shapes, at 4 px per unit.
         * The wing's own units run x 0…26, y −28…16; the bitmap's (0, 112) is the body point (0, 0).
         */
        fun wing(colors: Pair<Long, Long>) = make(108, 184) { c ->
            c.scale(4f, 4f)
            c.translate(0f, 28f)
            val fill = paint().apply { shader = LinearGradient(0f, -18f, 14f, 8f, colors.first.toInt(), colors.second.toInt(), Shader.TileMode.CLAMP) }
            val edge = paint().apply { style = Paint.Style.STROKE; strokeWidth = .9f; color = 0xBF280E32.toInt() }
            val upper = Path().apply { moveTo(0f, -1f); cubicTo(12f, -2f, 24f, -12f, 16f, -22f); cubicTo(8f, -26f, 1f, -12f, 0f, -3f); close() }
            val lower = Path().apply { moveTo(0f, 0f); cubicTo(12f, 0f, 18f, 9f, 11f, 14f); cubicTo(4f, 16f, 1f, 7f, 0f, 2f); close() }
            c.drawPath(upper, fill); c.drawPath(upper, edge)
            c.drawPath(lower, fill); c.drawPath(lower, edge)
            val dots = paint().apply { color = 0x99FFFFFF.toInt() }
            c.drawCircle(14f, -17f, 1.7f, dots); c.drawCircle(7f, -9f, 1.2f, dots)
        }
    }
}

/** Where the wing bitmap's body point sits, and its pixels per wing unit (see [LiveSprites] wing()). */
internal const val WING_ORIGIN_Y = 112f
internal const val WING_PX = 4f
