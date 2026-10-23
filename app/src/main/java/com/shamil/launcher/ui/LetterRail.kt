package com.shamil.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.abs
import kotlin.math.exp

/** The rail's top item: back to the favourites. */
const val STAR = "☆"
/** The rail's bottom item: the launcher's own section at the end of the list (like Niagara's). */
const val DOT = "•"
private val RAIL = listOf(STAR, "#") + ('A'..'Z').map { it.toString() } + DOT

/*
 * The wave, tuned from his Niagara screen recording (23 Oct): the letters follow the thumb sideways,
 * the hill is very wide, and the letters hardly grow.
 */
/** How far the letter under your finger bends out while your thumb is still on the rail. */
private val WAVE_REACH = 90.dp
/**
 * Once your thumb drags left, the peak letter stays this far left of it, so the thumb never covers it.
 * (23 Oct: 56dp felt like the letters kept their distance; he wanted them "pulled towards my thumb more".)
 */
private val FINGER_GAP = 30.dp
/** How wide the wave's hill is, in letters ("sigma" of a Gaussian bell): ~8, so most of the rail moves a little. */
private const val WAVE_WIDTH = 8f
/** How much bigger the letter under the finger gets (Niagara: barely). */
private const val WAVE_GROW = 0.08f
/** Width of the letter column; the touch strip around it is wider and runs to the screen edge. */
private val LETTERS_WIDTH = 28.dp

/**
 * The ☆ # A–Z • strip on the right edge, always on screen. Press or drag on it to pick a letter.
 * While your finger is down the letters bend out to the left in a wave that follows your thumb,
 * and a bubble shows the letter, like Niagara. Drag past the middle of the screen and the wave lets go:
 * you're sliding onto the list instead (let go on an app to open it).
 */
@Composable
fun LetterRail(
    present: Set<String>,
    modifier: Modifier = Modifier,
    /** While the thumb slides left off the rail onto the list: its screen y. null when it's back on the rail. */
    onSlide: (Float?) -> Unit = {},
    /** Finger lifted; true if it was sliding on the list (so home can open the app under it). */
    onRelease: (Boolean) -> Unit = {},
    onPick: (String) -> Unit,
) {
    val look = LocalLauncherLook.current
    val haptics = LocalHapticFeedback.current
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnSlide by rememberUpdatedState(onSlide)
    val currentOnRelease by rememberUpdatedState(onRelease)
    var sliding by remember { mutableStateOf(false) }
    var railTop by remember { mutableFloatStateOf(0f) }
    var railLeft by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf<String?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    var railHeight by remember { mutableFloatStateOf(1f) }
    // 0 = straight rail, 1 = full wave. Springs out when you touch, settles back when you let go.
    val wave by animateFloatAsState(
        targetValue = if (active != null && !sliding) 1f else 0f,
        // Quick and nearly bounce-free, like the open-source Niagara look-alikes.
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 700f),
        label = "wave",
    )
    // The bubble keeps showing the last letter while it fades out.
    var bubbleLetter by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            // Tell Android not to start its back-swipe here (phones with gesture navigation).
            .systemGestureExclusion()
            .onSizeChanged { railHeight = it.height.toFloat() }
            .onGloballyPositioned { railTop = it.positionInRoot().y; railLeft = it.positionInRoot().x }
            .pointerInput(Unit) {
                fun follow(x: Float, y: Float) {
                    fingerX = x
                    fingerY = y.coerceIn(0f, size.height.toFloat())
                    // Past the middle of the screen = sliding onto the list: the wave lets go, a firmer buzz
                    // marks the switch, and we report where the thumb is. (The rail sits at the screen's right edge.)
                    val middle = (railLeft + size.width) / 2 - railLeft
                    if (x < middle) {
                        if (!sliding) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        sliding = true
                        currentOnSlide(railTop + y)
                        return
                    }
                    if (sliding) {
                        sliding = false
                        currentOnSlide(null)
                    }
                    val i = (fingerY / size.height * RAIL.size).toInt().coerceIn(0, RAIL.lastIndex)
                    val item = RAIL[i]
                    if (item != active) {
                        active = item
                        bubbleLetter = item
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnPick(item)
                    }
                }
                // One "gesture" = finger down, any dragging, finger up.
                awaitEachGesture {
                    val down = awaitFirstDown().position
                    follow(down.x, down.y)
                    while (true) {
                        val change = awaitPointerEvent().changes.first()
                        if (!change.pressed) break
                        follow(change.position.x, change.position.y)
                        change.consume()
                    }
                    currentOnRelease(sliding)
                    if (sliding) currentOnSlide(null)
                    sliding = false
                    active = null
                }
            },
    ) {
        Column(Modifier.fillMaxHeight().width(LETTERS_WIDTH)) {
            RAIL.forEachIndexed { i, item ->
                val color = when {
                    item == active -> look.accent
                    item == STAR || item == DOT || item in present -> look.text
                    else -> look.text.copy(alpha = 0.4f)
                }
                Box(
                    // Every letter gets an equal slot, so we know exactly where each one sits.
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // graphicsLayer moves/scales while drawing only, so the wave stays smooth (no re-layout).
                        .graphicsLayer {
                            val slot = railHeight / RAIL.size
                            val lettersAway = abs((i + 0.5f) * slot - fingerY) / slot
                            val pull = wave * bell(lettersAway)
                            translationX = -reachPx(fingerX) * pull
                            scaleX = 1f + WAVE_GROW * pull
                            scaleY = 1f + WAVE_GROW * pull
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        item,
                        style = TextStyle(
                            fontFamily = look.font, fontWeight = FontWeight.Bold, color = color,
                            fontSize = 15.sp, lineHeight = 17.sp, shadow = look.shadow,
                        ),
                    )
                }
            }
        }

        // The letter bubble, floating just left of the wave's peak. Always there (invisible at rest):
        // the wave only changes how it's drawn, so nothing has to be rebuilt on every animation frame.
        run {
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = -(reachPx(fingerX) * wave + 56.dp.toPx())
                        translationY = fingerY - 24.dp.toPx()
                        alpha = wave.coerceIn(0f, 1f)
                        scaleX = 0.85f + 0.15f * wave
                        scaleY = 0.85f + 0.15f * wave
                    }
                    .size(48.dp)
                    .background(look.panel, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    bubbleLetter,
                    style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = 28.sp),
                )
            }
        }
    }
}

/**
 * How strongly a letter joins the wave, by how many letters away from the finger it is:
 * 1 right under the finger, fading smoothly toward 0 (a Gaussian "bell curve").
 */
private fun bell(lettersAway: Float): Float =
    exp(-(lettersAway * lettersAway) / (2 * WAVE_WIDTH * WAVE_WIDTH))

/** The wave's reach: the base amount, or just left of the thumb once it has dragged further left than that. */
private fun Density.reachPx(fingerX: Float): Float {
    val inward = (LETTERS_WIDTH.toPx() / 2 - fingerX).coerceAtLeast(0f)
    return maxOf(WAVE_REACH.toPx(), inward + FINGER_GAP.toPx())
}
