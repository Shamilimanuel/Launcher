package com.shamil.launcher.ui.home

import kotlin.math.sin
import kotlin.math.floor
import kotlin.math.PI
import kotlinx.coroutines.delay
import android.os.PowerManager
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.max

/*
 * The layout editor's drawn extras (Look → Layout): the background fade and the Seelie touch "Light trail"
 * (from the mockup he kept, 24 Oct). Plain drawing, no animation, so they cost nothing between redraws.
 */

/** Where the trail runs: a little left of the app icons. */
private val TRAIL_X = 24.dp
private val TRAIL_GOLD = Color(0xFFF3D79C)
private val TRAIL_LIGHT = Color(0xFFFFF6DC)

/**
 * The background fade, full screen and fixed: clear at the top, melting into the chosen colour lower down.
 * [HomeLayout.fadeReach] = how far down the wallpaper shows; [HomeLayout.fadeSoft] = how long the melt is.
 */
@Composable
fun WallpaperFade(layout: HomeLayout) {
    val colour = when (layout.fadeColour) {
        "theme" -> LocalLauncherLook.current.sheet.copy(alpha = 1f)
        "navy" -> Color(0xFF141C3A)
        else -> Color.Black
    }
    // The same sums as the mockup: a band of 10–70% of the screen, placed so "reach" is where the wallpaper ends.
    val band = (10f + layout.fadeSoft * 0.6f) / 100f
    val clear = layout.fadeReach / 100f * (1f + band) - band
    Box(
        Modifier.fillMaxSize().drawBehind {
            val y0 = clear * size.height
            val y1 = (clear + band) * size.height
            drawRect(
                Brush.verticalGradient(
                    0f to colour.copy(alpha = 0f), 0.25f to colour.copy(alpha = 0.16f), 0.5f to colour.copy(alpha = 0.5f),
                    0.75f to colour.copy(alpha = 0.84f), 1f to colour,
                    startY = y0, endY = max(y0 + 1f, y1),
                ),
            )
        }
    )
}

/**
 * Seelie touch "Light trail": a thin gold trail from the clock ([fromY]) winding down past the favourites to the
 * bottom, with a soft glow and a small spirit at its head. Draws in the scrolling content's coordinates.
 */
fun DrawScope.lightTrail(fromY: Float, tint: Color) {
    val x = TRAIL_X.toPx()
    val u = 1.dp.toPx()
    val path = trailPath(x, fromY, size.height, u)
    val brush = Brush.verticalGradient(
        0f to TRAIL_LIGHT.copy(alpha = 0.95f), 0.3f to TRAIL_GOLD.copy(alpha = 0.8f), 1f to tint.copy(alpha = 0.06f),
        startY = fromY, endY = size.height,
    )
    drawPath(path, brush, style = Stroke(width = 5 * u, cap = StrokeCap.Round), alpha = 0.35f)   // the glow
    drawPath(path, brush, style = Stroke(width = 1.5f * u, cap = StrokeCap.Round))
}

/** The trail's shape: a little S from the clock, then straight down (the mockup's curve). */
private fun trailPath(x: Float, fromY: Float, toY: Float, u: Float) = Path().apply {
    moveTo(x, fromY)
    cubicTo(x + 20 * u, fromY + 22 * u, x - 18 * u, fromY + 50 * u, x, fromY + 74 * u)
    lineTo(x, toY)
}

/**
 * The living part of the light trail (his ask, 24 Oct: "if it had something moving inside it, it would look better"):
 * light pulses flowing down it like small comets, the spirit at its head breathing, and now and then a tiny mote
 * drifting off sideways. A full-screen overlay in its OWN drawing layer, so only it redraws (about 30 times a second,
 * 15 in battery saver), and only while the home is in front. [fromY] = where the trail starts in the scrolling
 * content, [contentHeight] = how tall that content is; [scroll] = how far it is scrolled, so the light stays on the trail.
 */
@Composable
fun TrailFlow(fromY: Float, contentHeight: Float, scroll: () -> Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val power = context.getSystemService(PowerManager::class.java)
            var start = -1L
            while (true) {
                withFrameNanos { now ->
                    if (start < 0) start = now
                    seconds.floatValue = (now - start) / 1_000_000_000f
                }
                delay(if (power?.isPowerSaveMode == true) 60L else 26L)
            }
        }
    }
    // The trail's shape and length, measured once per position (not every frame).
    val density = LocalDensity.current
    val measure = remember(fromY, contentHeight, density) {
        with(density) { PathMeasure().apply { setPath(trailPath(TRAIL_X.toPx(), fromY, contentHeight, 1.dp.toPx()), false) } }
    }
    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer()   // its own layer: redrawing the light never redraws the apps
            .drawBehind {
                val t = seconds.floatValue
                val u = 1.dp.toPx()
                val dy = -scroll().toFloat()
                val length = measure.length
                if (length <= 0f) return@drawBehind
                // The spirit at its head, breathing.
                val head = measure.getPosition(0f) + Offset(0f, dy)
                val breath = 1f + 0.18f * sin(t * 1.8f)
                drawCircle(TRAIL_LIGHT.copy(alpha = 0.14f * breath), radius = 13 * u * breath, center = head)
                drawCircle(TRAIL_LIGHT.copy(alpha = 0.32f), radius = 7 * u * breath, center = head)
                drawCircle(Color.White, radius = 3.6f * u, center = head)
                // Light pulses flowing down: four comets at their own speeds, fading in at the top and out at the end.
                for (k in 0 until 4) {
                    val speed = 0.075f + k * 0.018f
                    val p = (t * speed + k * 0.27f) % 1f
                    val fade = sin(PI.toFloat() * p)
                    val d = p * length
                    for (tail in 0 until 6) {
                        val pos = measure.getPosition((d - tail * 7 * u).coerceAtLeast(0f)) + Offset(0f, dy)
                        if (pos.y < -20f || pos.y > size.height + 20f) continue
                        val a = fade * (1f - tail / 6f)
                        if (tail == 0) {
                            drawCircle(Brush.radialGradient(listOf(TRAIL_LIGHT.copy(alpha = 0.55f * a), Color.Transparent), center = pos, radius = 9 * u),
                                radius = 9 * u, center = pos)
                        }
                        drawCircle(if (tail == 0) Color.White.copy(alpha = a) else TRAIL_GOLD.copy(alpha = 0.7f * a),
                            radius = (2.4f - tail * 0.3f) * u, center = pos)
                    }
                }
                // Motes: now and then a speck leaves the trail and drifts off to the side, fading out.
                for (m in 0 until 5) {
                    val cycle = 5.5f + m * 1.3f
                    val life = (t + m * 2.1f) % cycle / cycle
                    val seed = floor((t + m * 2.1f) / cycle) * 13f + m * 7f
                    val along = ((sin(seed) * 0.5f + 0.5f) * 0.85f + 0.1f) * length
                    val base = measure.getPosition(along) + Offset(0f, dy)
                    val drift = Offset((if (m % 2 == 0) 1f else -0.6f) * life * 22 * u, -life * 14 * u)
                    val a = sin(PI.toFloat() * life) * 0.8f
                    drawCircle(TRAIL_LIGHT.copy(alpha = a), radius = 1.4f * u, center = base + drift)
                }
            }
    )
}

/** The trail's bead beside favourite number [index] (fading further down), in that row's own coordinates. */
fun DrawScope.trailBead(index: Int) {
    val c = Offset(TRAIL_X.toPx(), 28.dp.toPx())
    val a = max(0.28f, 1f - index * 0.11f)
    drawCircle(Brush.radialGradient(listOf(TRAIL_GOLD.copy(alpha = 0.65f * a), Color.Transparent), center = c, radius = 7.dp.toPx()),
        radius = 7.dp.toPx(), center = c)
    drawCircle(TRAIL_GOLD.copy(alpha = a), radius = 3.dp.toPx(), center = c)
}
