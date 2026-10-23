package com.shamil.launcher.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/*
 * The small animations of Seelie settings (his pick, 23 Oct: all four).
 * Everything moves in the draw phase (graphicsLayer / drawBehind), so it stays smooth.
 * Android's own "Animator duration scale" setting slows or switches these off, like every app.
 */

/**
 * Hands out a running number to each card on a page, so they can float in one after another.
 * Only while the page is opening: cards built later (scrolled into a long list) just appear, no waiting.
 */
class Stagger {
    var next = 0
    private val openedAt = System.currentTimeMillis()
    val opening: Boolean get() = System.currentTimeMillis() - openedAt < OPENING_MS

    private companion object { const val OPENING_MS = 500L }
}
val LocalStagger = compositionLocalOf<Stagger?> { null }

/**
 * Stagger: the card fades in and floats up ~28dp, about 35 ms after the card above it.
 * (23 Oct: in Edit favourites every A–Z row scrolled into view waited ~0.5 s; now only the first screenful animates.)
 */
@Composable
fun Modifier.staggerIn(): Modifier {
    val stagger = LocalStagger.current ?: return this
    val index = remember { stagger.next++ }
    val progress = remember { Animatable(if (stagger.opening && index < 14) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value >= 1f) return@LaunchedEffect
        delay(40L + index * 35L)
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f))
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

/**
 * Press feel (a micro-interaction): while your finger is down the card sinks to ~96.5% and a soft glow
 * in [glow] lights it up; letting go springs it back with a tiny bounce. Replaces the grey ripple.
 */
@Composable
fun Modifier.pressFeel(source: InteractionSource, glow: Color, corner: Dp): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "press")
    val light by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 90 else 260), label = "press glow")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawWithContent {
            drawContent()
            if (light > 0f) drawRoundRect(glow.copy(alpha = 0.18f * light), cornerRadius = CornerRadius(corner.toPx()))
        }
}

/** Ambient animation: tiny sparkles drifting slowly upward and twinkling, like seelie light on the wallpaper. */
@Composable
fun Modifier.sparkles(color: Color, count: Int = 18): Modifier {
    val t by rememberInfiniteTransition(label = "sparkles")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "sparkle time")
    // Each sparkle: x, starting y, speed, size, twinkle offset. Same every time (fixed seed).
    val dots = remember(count) {
        val r = Random(23)
        List(count) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.35f + r.nextFloat() * 0.65f, 0.8f + r.nextFloat() * 1.6f, r.nextFloat()) }
    }
    return drawBehind {
        dots.forEach { (x, y0, speed, radius, phase) ->
            val y = ((y0 - t * speed) % 1f + 1f) % 1f
            val twinkle = 0.25f + 0.6f * (0.5f + 0.5f * sin(2 * PI.toFloat() * (t * 4 * speed + phase)))
            val center = Offset(x * size.width, y * size.height)
            drawCircle(color.copy(alpha = twinkle * 0.25f), radius = radius.dp.toPx() * 3.2f, center = center)
            drawCircle(color.copy(alpha = twinkle), radius = radius.dp.toPx(), center = center)
        }
    }
}
