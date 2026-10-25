package com.shamil.launcher.ui.home

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.GlanceState
import com.shamil.launcher.ui.clock.rememberClockTime
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.NunitoSans
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/*
 * Poster home (v0, 25 Oct): his pick from the home-styles mockup. The window is see-through, so his wallpaper
 * already IS the painting; this only adds what sits on it: a big Cinzel clock set into the picture, four apps
 * floating at the bottom, and "swipe up for all apps". Paintings of his own (a pager, layers, tilt) come later.
 * Nothing animates while it sits still: the float-in, the sparkles and the hint all run once and stop.
 */

/** How far down the clock sits (a share of the screen): the upper third, like the mockup. */
private const val CLOCK_TOP = 0.15f

/** How many favourites float at the bottom. */
private const val DOCK_APPS = 4

/** The hint shows the first few times only; after that he knows. */
private const val HINT_TIMES = 6

/**
 * Poster home: his wallpaper is the painting, with a big clock and four apps.
 * [favourites] = his favourites in order (the first four float at the bottom); [onOpenApp] opens one (vault-guarded,
 * counted); [onAppMenu] = long-press menu; [onAllApps] = swipe up (opens the A–Z list); [onHomeSheet] = a long-press
 * on the clock or the empty painting opens the same "Seelie widget" sheet as the tall home (25 Oct, audit P2).
 */
@Composable
fun PosterHome(
    favourites: List<AppEntry>,
    glance: GlanceState,
    onOpenApp: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
    modifier: Modifier = Modifier,
    onHomeSheet: () -> Unit = {},
    underClock: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    // The swipe is set up once; this keeps it calling today's onAllApps, not the first one.
    val allApps by rememberUpdatedState(onAllApps)
    val homeSheet by rememberUpdatedState(onHomeSheet)
    val swipeFar = with(density) { 56.dp.toPx() }

    BoxWithConstraints(
        modifier
            // The scrim: a soft dark wash at the top (behind the clock) and the bottom (behind the apps),
            // so both read on any wallpaper while the middle of the painting stays untouched.
            .drawBehind {
                val h = size.height
                drawRect(Brush.verticalGradient(0f to SCRIM.copy(alpha = 0.38f), 1f to Color.Transparent, startY = 0f, endY = h * 0.45f))
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to SCRIM.copy(alpha = 0.6f), startY = h * 0.6f, endY = h))
            }
            // Swipe up anywhere = all apps. Taps still reach the clock and the apps (they only react to taps).
            .pointerInput(Unit) {
                var pulled = 0f
                var done = false
                detectVerticalDragGestures(
                    onDragStart = { pulled = 0f; done = false },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        pulled += dy
                        if (!done && pulled < -swipeFar) {
                            done = true
                            allApps()
                        }
                    },
                )
            }
            // Hold the empty painting = the "Seelie widget" sheet (settings, Switch Home app). The clock and the apps
            // handle their own touches first, so holding an app still opens its menu.
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    homeSheet()
                })
            }
    ) {
        val screenH = maxHeight
        Column(Modifier.align(Alignment.TopCenter).padding(top = screenH * CLOCK_TOP), horizontalAlignment = Alignment.CenterHorizontally) {
            // Holding the clock opens the sheet too (combinedClickable buzzes by itself on a long press).
            PosterClock(glance, onLongPress = { homeSheet() })
            underClock()
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Above the navigation buttons, with a little air.
                .padding(bottom = 30.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SwipeHint()
            Spacer(Modifier.height(14.dp))
            val dock = favourites.take(DOCK_APPS)
            if (dock.isEmpty()) {
                // No favourites yet: say how to get some, instead of an empty bottom.
                val look = LocalLauncherLook.current
                Text(
                    "Hold an app in the list and tap Favourite to place it here",
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 20.dp),
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp, shadow = look.shadow),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    dock.forEachIndexed { i, app ->
                        // key: if he reorders favourites, each app keeps its own press state.
                        key(app.key) {
                            PosterApp(app, index = i + 1, onOpen = { onOpenApp(app) }, onMenu = { onAppMenu(app) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * The big clock and the date under it. Tap the clock = his alarms, tap the date = his calendar (like the tall home).
 * Two shadows, like the mockup's CSS: a wide accent glow behind, a soft dark one in front, so it reads on bright skies too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterClock(glance: GlanceState, modifier: Modifier = Modifier, onLongPress: () -> Unit = {}) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val look = LocalLauncherLook.current
    val time = rememberClockTime()
    val is24h = DateFormat.is24HourFormat(context)
    // A theme switch glides the glow to the new colour (his 0.4 s colour glide) instead of jumping.
    val accent by animateColorAsState(look.accent, tween(400), label = "poster accent")
    val clockColour = look.forPart("clock").text
    val dateColour = look.partText["date"] ?: accent

    val clockText = time.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm"))
    // "SUNDAY, 25 OCTOBER", plus the temperature when the weather is known.
    val dateText = buildString {
        append(time.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())).replace(".", ""))
        glance.weather?.let { append("  ·  ${it.temp}°") }
    }.uppercase(Locale.getDefault())

    val glow = with(density) { Shadow(accent.copy(alpha = 0.8f), Offset.Zero, 40.dp.toPx()) }
    val drop = with(density) { Shadow(Color(0x66000000), Offset(0f, 4.dp.toPx()), 30.dp.toPx()) }
    val clockStyle = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 92.sp,
        letterSpacing = 0.02.em, color = clockColour)

    val press = remember { MutableInteractionSource() }
    Column(modifier.floatIn(0), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .onceSparkles(Color(0xFFFFF6DC))
                .pressFeel(press, accent, 28.dp)
                .combinedClickable(interactionSource = press, indication = null, onLongClick = onLongPress, onClick = {
                    openOrNothing(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
                })
                .padding(horizontal = 12.dp)
        ) {
            // The same digits twice: the glow behind, the clock with its dark shadow on top.
            Text(clockText, style = clockStyle.copy(shadow = glow), maxLines = 1, softWrap = false)
            Text(clockText, style = clockStyle.copy(shadow = drop), maxLines = 1, softWrap = false)
        }
        Text(
            dateText,
            modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val today = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                    ContentUris.appendId(today, System.currentTimeMillis())
                    openOrNothing(context, Intent(Intent.ACTION_VIEW, today.build()))
                }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            maxLines = 1,
            style = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                letterSpacing = 0.14.em, color = dateColour,
                shadow = with(density) { Shadow(Color(0xAA000000), Offset(0f, 2.dp.toPx()), 10.dp.toPx()) }),
        )
    }
}

/** One of the four apps: a big icon with a soft shadow on the "ground" under it (no plate), and a small name. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterApp(app: AppEntry, index: Int, onOpen: () -> Unit, onMenu: () -> Unit) {
    val look = LocalLauncherLook.current
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    val iconSize = 60.dp * look.iconScale.coerceIn(0.7f, 1.4f)
    Column(
        Modifier
            .floatIn(index)
            .width(iconSize + 18.dp)
            .pressFeel(press, look.accent, 20.dp)
            .combinedClickable(
                interactionSource = press,
                indication = null,
                onClick = onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMenu()
                },
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(iconSize)
                // A dark soft oval just under the icon: works for every icon shape, unlike a shaped shadow.
                .drawBehind {
                    val c = Offset(size.width / 2f, size.height * 0.78f)
                    val r = size.width * 0.62f
                    drawCircle(Brush.radialGradient(listOf(Color(0x8C000000), Color.Transparent), center = c, radius = r), r, c)
                },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = look.font, color = look.forPart("apps").textDim, fontSize = 12.sp, shadow = look.shadow),
        )
    }
}

/** "Swipe up for all apps", the first few times: fades in, waits a few seconds, fades away for good. */
@Composable
private fun SwipeHint() {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val show = remember { prefs.getInt(KEY_HINTS, 0) < HINT_TIMES }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        prefs.edit { putInt(KEY_HINTS, prefs.getInt(KEY_HINTS, 0) + 1) }
        delay(700)
        alpha.animateTo(1f, tween(500))
        delay(3_500)
        alpha.animateTo(0f, tween(900))
    }
    // Keeps its room even when hidden, so the apps never jump when it fades.
    Text(
        "SWIPE UP FOR ALL APPS",
        modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp,
            letterSpacing = 0.1.em, color = look.text.copy(alpha = 0.6f), shadow = look.shadow),
    )
}

/**
 * Stagger in (his approved feel): fades in and floats up ~28dp, the clock first, then the apps one by one.
 * Only when the home is built; read in the draw phase, so it never rebuilds anything.
 */
@Composable
private fun Modifier.floatIn(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60L + index * 55L)
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f))
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

/**
 * A few four-point stars twinkle around the clock as the home appears (~2.6 s), then they're gone:
 * sparkles that greet you, not ones that run all day (nothing animates while idle).
 */
@Composable
private fun Modifier.onceSparkles(colour: Color): Modifier {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(350)
        t.animateTo(1f, tween(2_600, easing = LinearEasing))
    }
    val star = remember {
        // 12 units across, centred on 0,0 (the mockup's sparkle shape).
        Path().apply {
            moveTo(0f, -6f); quadraticTo(0.7f, -0.7f, 6f, 0f); quadraticTo(0.7f, 0.7f, 0f, 6f)
            quadraticTo(-0.7f, 0.7f, -6f, 0f); quadraticTo(-0.7f, -0.7f, 0f, -6f); close()
        }
    }
    return drawWithContent {
        drawContent()
        val now = t.value
        if (now <= 0f || now >= 1f) return@drawWithContent
        // Each star twinkles once, one after another (see SPOTS).
        for (s in SPOTS) {
            val local = ((now - s[3]) / 0.55f).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            val pulse = sin(PI.toFloat() * local)
            val k = s[2] * (0.5f + 0.5f * pulse) * 1.dp.toPx()
            val at = Offset(size.width * s[0], size.height * s[1])
            drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.5f * pulse), Color.Transparent), center = at, radius = 10f * k), 10f * k, at)
            translate(at.x, at.y) {
                scale(k, k, Offset.Zero) { drawPath(star, colour, alpha = pulse) }
            }
        }
    }
}

/** Where the sparkles sit: (x share, y share, size, start time): top-right, above the middle, low on the left. */
private val SPOTS = listOf(
    floatArrayOf(0.96f, 0.16f, 1.3f, 0f),
    floatArrayOf(0.58f, -0.04f, 0.8f, 0.22f),
    floatArrayOf(0.03f, 0.74f, 0.7f, 0.44f),
)

/** The scrim's colour: the mockup's deep night blue, nearly black. */
private val SCRIM = Color(0xFF060814)

private const val PREFS = "poster_home"
private const val KEY_HINTS = "hints_shown"

/** Opens [intent], or quietly does nothing when no app on the phone handles it. A locked clock or calendar asks its code (25 Oct). */
private fun openOrNothing(context: Context, intent: Intent) = com.shamil.launcher.ui.openSafely(context, intent)
