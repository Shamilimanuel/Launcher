package com.shamil.launcher.ui.home

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
    val u = 1.dp.toPx() * 390f / 390f
    val path = Path().apply {
        moveTo(x, fromY)
        cubicTo(x + 20 * u, fromY + 22 * u, x - 18 * u, fromY + 50 * u, x, fromY + 74 * u)
        lineTo(x, size.height)
    }
    val brush = Brush.verticalGradient(
        0f to TRAIL_LIGHT.copy(alpha = 0.95f), 0.3f to TRAIL_GOLD.copy(alpha = 0.8f), 1f to tint.copy(alpha = 0.06f),
        startY = fromY, endY = size.height,
    )
    drawPath(path, brush, style = Stroke(width = 5 * u, cap = StrokeCap.Round), alpha = 0.35f)   // the glow
    drawPath(path, brush, style = Stroke(width = 1.5f * u, cap = StrokeCap.Round))
    // The spirit at its head.
    val head = Offset(x, fromY)
    drawCircle(TRAIL_LIGHT.copy(alpha = 0.16f), radius = 12 * u, center = head)
    drawCircle(TRAIL_LIGHT.copy(alpha = 0.32f), radius = 7 * u, center = head)
    drawCircle(Color.White, radius = 3.6f * u, center = head)
}

/** The trail's bead beside favourite number [index] (fading further down), in that row's own coordinates. */
fun DrawScope.trailBead(index: Int) {
    val c = Offset(TRAIL_X.toPx(), 28.dp.toPx())
    val a = max(0.28f, 1f - index * 0.11f)
    drawCircle(Brush.radialGradient(listOf(TRAIL_GOLD.copy(alpha = 0.65f * a), Color.Transparent), center = c, radius = 7.dp.toPx()),
        radius = 7.dp.toPx(), center = c)
    drawCircle(TRAIL_GOLD.copy(alpha = a), radius = 3.dp.toPx(), center = c)
}
