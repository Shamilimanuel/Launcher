package com.shamil.launcher.ui.clock

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shamil.launcher.R
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.CormorantGaramond
import com.shamil.launcher.ui.theme.Fredoka
import com.shamil.launcher.ui.theme.LauncherLook
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.Oswald
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Clock & date styles (his look review: "clocks good, add more fun ones, not just for clock").
 * The looks are copied from the prototype's Serenitea page: 12 clock styles, 5 date styles, 4 glance styles.
 * Everything takes its colours and font from the theme (LocalLauncherLook), so a theme switch restyles it all.
 * Animations only move in the draw phase (graphicsLayer / Canvas) and only while the screen is visible,
 * so the home screen stays as cheap as before.
 */

/** One clock style. [id] is what gets saved, so never rename it. */
enum class ClockStyle(val id: String, val label: String, val blurb: String) {
    Classic("classic", "Classic", "Plain, like today"),
    Thin("thin", "Thin", "Light and airy"),
    Bold("bold", "Bold", "Heavy and loud"),
    Stacked("stacked", "Stacked", "Two lines"),
    Vertical("vertical", "Vertical", "Tall and narrow"),
    Gold("gold", "Cinzel Gold", "Seelie's own"),
    Rounded("rounded", "Rounded", "Soft and friendly"),
    Outline("outline", "Outline", "Hollow glow"),
    Serif("serif", "Serif", "Elegant italic"),
    Words("words", "In words", "“ten past eight”"),
    Neon("neon", "Neon tube", "Glowing, blinking colon"),
    SeelieColon("seelie", "Seelie colon", "A spirit sits in the colon");

    companion object {
        /** The style saved under [id]; unknown or nothing saved = Classic (today's clock). */
        fun byId(id: String?): ClockStyle = entries.firstOrNull { it.id == id } ?: Classic
    }
}

/** One date style. [id] is what gets saved, so never rename it. */
enum class DateStyle(val id: String, val label: String) {
    Plain("plain", "Plain"),
    Capitals("caps", "Capitals"),
    Long("long", "Long"),
    Pill("pill", "Pill"),
    Stamp("stamp", "Stamp");

    companion object {
        fun byId(id: String?): DateStyle = entries.firstOrNull { it.id == id } ?: Plain
    }
}

/**
 * How the glance line under the date looks (weather · battery). Glance.kt reads this when it's wired up:
 * wrap its Row in [glanceFrame] and leave out the weather words when [showsWords] is false.
 */
enum class GlanceStyle(val id: String, val label: String, val showsWords: Boolean) {
    Plain("plain", "Plain text", true),
    Pill("pill", "Pill", true),
    Glow("glow", "Glow", true),
    IconsOnly("mini", "Icons only", false);

    companion object {
        fun byId(id: String?): GlanceStyle = entries.firstOrNull { it.id == id } ?: Plain
    }
}

/**
 * Remembers his clock, date and glance styles (saved on the phone, so they survive a restart).
 * Compose state, and ONE store for the whole app ([get]), like LookStore: a pick in settings shows on the home at once.
 */
class ClockStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("clock", Context.MODE_PRIVATE)

    var clockStyle by mutableStateOf(ClockStyle.byId(prefs.getString(KEY_CLOCK, null)))
        private set
    var dateStyle by mutableStateOf(DateStyle.byId(prefs.getString(KEY_DATE, null)))
        private set
    var glanceStyle by mutableStateOf(GlanceStyle.byId(prefs.getString(KEY_GLANCE, null)))
        private set

    // "use" instead of "set": setClockStyle would clash with the property's own setter (same as LookStore).
    fun useClockStyle(style: ClockStyle) {
        clockStyle = style
        prefs.edit { putString(KEY_CLOCK, style.id) }
    }

    fun useDateStyle(style: DateStyle) {
        dateStyle = style
        prefs.edit { putString(KEY_DATE, style.id) }
    }

    fun useGlanceStyle(style: GlanceStyle) {
        glanceStyle = style
        prefs.edit { putString(KEY_GLANCE, style.id) }
    }

    companion object {
        private const val KEY_CLOCK = "clock_style"
        private const val KEY_DATE = "date_style"
        private const val KEY_GLANCE = "glance_style"
        @Volatile private var instance: ClockStore? = null

        fun get(context: Context): ClockStore =
            instance ?: synchronized(this) { instance ?: ClockStore(context.applicationContext).also { instance = it } }
    }
}

/**
 * The home screen's clock + date in his chosen styles. Tap the clock = [onClockClick], long-press = [onClockLongClick],
 * tap the date = [onDateClick]. With Classic + Plain it looks exactly like the old clock.
 */
@Composable
fun SeelieClock(
    time: LocalDateTime,
    is24h: Boolean,
    onClockClick: () -> Unit,
    onClockLongClick: () -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { ClockStore.get(context) }
    val noRipple = remember { MutableInteractionSource() }
    ClockBlock(
        clock = store.clockStyle,
        date = store.dateStyle,
        time = time,
        is24h = is24h,
        modifier = modifier,
        clockModifier = Modifier.combinedClickable(
            interactionSource = noRipple,
            indication = null,
            onClick = onClockClick,
            onLongClick = onClockLongClick,
        ),
        dateModifier = Modifier.clickable(interactionSource = noRipple, indication = null, onClick = onDateClick),
    )
}

/** Clock above date, with a little air between them when a style needs it. [scale] 1 = home size. */
@Composable
internal fun ClockBlock(
    clock: ClockStyle,
    date: DateStyle,
    time: LocalDateTime,
    is24h: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    clockModifier: Modifier = Modifier,
    dateModifier: Modifier = Modifier,
) {
    Column(modifier) {
        ClockFace(clock, time, is24h, clockModifier, scale)
        // Classic + Plain keep today's exact spacing (none); taller styles and boxed dates get a few dp.
        val gap = when {
            date == DateStyle.Pill || date == DateStyle.Stamp -> 6.dp
            clock == ClockStyle.Stacked || clock == ClockStyle.Vertical || clock == ClockStyle.Words -> 4.dp
            else -> 0.dp
        }
        if (gap > 0.dp) Spacer(Modifier.height(gap * scale))
        DateFace(date, time, dateModifier, scale)
    }
}

/** Today's clock size on the home (47sp); every style is a multiple of it, like the prototype's --k. */
private const val CLOCK_SP = 47f

/** A light system font for "Thin": Seelie's own font files stop at Regular, so they can't go thinner. */
private val SystemLight: FontFamily = FontFamily(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL))

/** The accent mixed with white, for highlights (the prototype's --accent-hi). */
private fun LauncherLook.accentHi(): Color = lerp(accent, Color.White, 0.45f)

/** Just the clock, in [style]. [scale] shrinks it for the little tiles in settings. */
@Composable
fun ClockFace(style: ClockStyle, time: LocalDateTime, is24h: Boolean, modifier: Modifier = Modifier, scale: Float = 1f) {
    val look = LocalLauncherLook.current
    val density = LocalDensity.current
    fun size(k: Float): TextUnit = (CLOCK_SP * k * scale).sp
    // Glow and shadow sizes are in pixels; they shrink along with the clock.
    fun px(dp: Float): Float = with(density) { (dp * scale).dp.toPx() }
    val hh = time.format(DateTimeFormatter.ofPattern(if (is24h) "HH" else "h"))
    val mm = time.format(DateTimeFormatter.ofPattern("mm"))
    val hhmm = "$hh:$mm"

    when (style) {
        ClockStyle.Classic -> ClockText(hhmm, modifier, TextStyle(
            fontFamily = look.font, fontWeight = look.clockWeight, color = look.text, fontSize = size(1f), shadow = look.shadow))

        ClockStyle.Thin -> ClockText(hhmm, modifier, TextStyle(
            fontFamily = SystemLight, color = look.text, fontSize = size(1.12f), letterSpacing = (-0.01).em, shadow = look.shadow))

        ClockStyle.Bold -> ClockText(hhmm, modifier, TextStyle(
            fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = size(1.02f), shadow = look.shadow))

        // Hours above minutes, minutes in the accent with a soft glow.
        ClockStyle.Stacked -> {
            val base = TextStyle(
                fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = size(1.36f),
                letterSpacing = (-0.02).em, lineHeight = 0.84.em, lineHeightStyle = TightLines, shadow = look.shadow)
            Column(modifier) {
                ClockText(hh, style = base)
                ClockText(mm, style = base.copy(color = look.accent, shadow = Shadow(look.accent.copy(alpha = 0.45f), blurRadius = px(22f))))
            }
        }

        // Tall Oswald digits with a fading accent line between hours and minutes.
        ClockStyle.Vertical -> {
            val base = TextStyle(
                fontFamily = Oswald, fontWeight = FontWeight.Normal, color = look.text, fontSize = size(1.46f),
                letterSpacing = 0.01.em, lineHeight = 0.9.em, lineHeightStyle = TightLines, shadow = look.shadow)
            val em = with(density) { size(1.46f).toDp() }
            Column(modifier) {
                ClockText(hh, style = base)
                Box(
                    Modifier
                        .padding(vertical = em * 0.08f)
                        .width(em * 1.7f)
                        .height(1.dp)
                        .background(Brush.horizontalGradient(listOf(look.accent, Color.Transparent)))
                )
                ClockText(mm, style = base)
            }
        }

        // Seelie's own: Cinzel capitals filled with a gold-to-accent gradient, glowing softly.
        ClockStyle.Gold -> ClockText(hhmm, modifier, TextStyle(
            brush = Brush.verticalGradient(
                0.06f to Color(0xFFFFF3CE),
                0.62f to look.accent,
                1f to lerp(look.accent, Color(0xFF6B4A10), 0.3f),
            ),
            fontFamily = Cinzel, fontWeight = FontWeight.Medium, fontSize = size(1.02f), letterSpacing = 0.03.em,
            shadow = Shadow(look.accent.copy(alpha = 0.38f), Offset(0f, px(3f)), px(18f))))

        // Fredoka in a light accent, with a hard "3D" edge underneath and a soft drop shadow.
        ClockStyle.Rounded -> LayeredText(
            hhmm, modifier,
            style = TextStyle(
                fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, color = lerp(look.accent, Color.White, 0.22f), fontSize = size(1.1f),
                shadow = Shadow(lerp(look.accent, Color.Black, 0.62f), Offset(0f, px(3f)), 0f)),
            backShadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, px(8f)), px(22f)),
        )

        // Hollow digits: only the outline is drawn, with an accent glow.
        ClockStyle.Outline -> ClockText(hhmm, modifier, TextStyle(
            fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accentHi(), fontSize = size(1.1f),
            drawStyle = Stroke(width = px(1.4f), join = StrokeJoin.Round),
            shadow = Shadow(look.accent.copy(alpha = 0.45f), blurRadius = px(12f))))

        // Cormorant Garamond in italic (Android slants it for us: there's no italic file).
        ClockStyle.Serif -> ClockText(hhmm, modifier, TextStyle(
            fontFamily = CormorantGaramond, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic, color = look.text,
            fontSize = size(1.3f), letterSpacing = (-0.01).em, shadow = look.shadow))

        // "quarter past" / "eight", the second line in the accent.
        ClockStyle.Words -> {
            val (first, second) = timeInWords(time.hour, time.minute)
            val base = TextStyle(
                fontFamily = CormorantGaramond, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, color = look.text,
                fontSize = size(0.74f), lineHeight = 1.02.em, lineHeightStyle = TightLines, shadow = look.shadow)
            Column(modifier) {
                ClockText(first, style = base)
                ClockText(second, style = base.copy(color = look.accentHi(), fontWeight = FontWeight.SemiBold))
            }
        }

        ClockStyle.Neon -> NeonClock(hh, mm, modifier, TextStyle(
            fontFamily = look.font, fontWeight = FontWeight.Normal, color = Color.White, fontSize = size(1.12f),
            shadow = Shadow(look.accent, blurRadius = px(10f))),
            backShadow = Shadow(look.accent.copy(alpha = 0.75f), blurRadius = px(28f)))

        ClockStyle.SeelieColon -> {
            val base = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Normal, color = look.text, fontSize = size(1.1f), shadow = look.shadow)
            val em = with(density) { size(1.1f).toDp() }
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                ClockText(hh, style = base)
                // The spirit sits a hair above the middle, where a colon would be.
                SeelieSpirit(em * 0.55f, Modifier.padding(horizontal = em * 0.03f).offset(y = -(em * 0.05f)))
                ClockText(mm, style = base)
            }
        }
    }
}

/** Lines packed tightly (for the two-line clocks): the extra line space is trimmed off the top and bottom. */
private val TightLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/** One line of clock text that never wraps (the small tiles in settings are narrow). */
@Composable
private fun ClockText(text: String, modifier: Modifier = Modifier, style: TextStyle) {
    Text(text, modifier = modifier, style = style, softWrap = false, maxLines = 1)
}

/** Text drawn twice on top of itself: a wide [backShadow] behind, the normal [style] in front (two shadows, like CSS). */
@Composable
private fun LayeredText(text: String, modifier: Modifier = Modifier, style: TextStyle, backShadow: Shadow) {
    Box(modifier) {
        ClockText(text, style = style.copy(shadow = backShadow))
        ClockText(text, style = style)
    }
}

/**
 * Neon tube: white digits with an accent glow. Each time the screen shows, the tube flickers on (~0.4 s);
 * after that only the colon blinks, once a second. That's a timer, not a running animation: just the colon redraws.
 */
@Composable
private fun NeonClock(hh: String, mm: String, modifier: Modifier, style: TextStyle, backShadow: Shadow) {
    val context = LocalContext.current
    val moving = remember { animationsOn(context) }
    val flicker = remember { Animatable(if (moving) 0f else 1f) }
    var colonLit by remember { mutableStateOf(true) }
    WhileOnScreen(moving) {
        colonLit = true
        flicker.snapTo(0f)
        flicker.animateTo(1f, keyframes {
            durationMillis = 420
            0.7f at 40
            0.1f at 90
            0.9f at 150
            0.3f at 210
            1f at 300
        })
        while (true) {
            delay(1000)
            colonLit = !colonLit
        }
    }
    // Both values are read inside graphicsLayer, so changing them only redraws, never rebuilds.
    Row(modifier.graphicsLayer { alpha = flicker.value }) {
        LayeredText(hh, style = style, backShadow = backShadow)
        LayeredText(":", Modifier.graphicsLayer { alpha = if (colonLit) 1f else 0.25f }, style, backShadow)
        LayeredText(mm, style = style, backShadow = backShadow)
    }
}

/**
 * A tiny seelie (from the prototype's SVG, 32×32 units): halo, two wings, a glowing body with two eyes and a tail.
 * Every few seconds she flaps twice and her halo brightens (~1.8 s), then rests: no animation runs while she rests.
 */
@Composable
private fun SeelieSpirit(size: Dp, modifier: Modifier = Modifier) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val moving = remember { animationsOn(context) }
    val beat = remember { Animatable(0f) }
    WhileOnScreen(moving) {
        while (true) {
            beat.snapTo(0f)
            beat.animateTo(1f, tween(1800, easing = LinearEasing))
            delay(2600)
        }
    }
    val accent = look.accent
    val accentHi = look.accentHi()
    // The shapes, in the SVG's own 32-unit coordinates (the Canvas scales them to the right size).
    val leftWing = remember {
        Path().apply { moveTo(15f, 15f); cubicTo(9f, 15f, 3.5f, 11.5f, 3f, 5.5f); cubicTo(8.5f, 5f, 14f, 8f, 15f, 15f); close() }
    }
    val rightWing = remember {
        Path().apply { moveTo(17f, 15f); cubicTo(23f, 15f, 28.5f, 11.5f, 29f, 5.5f); cubicTo(23.5f, 5f, 18f, 8f, 17f, 15f); close() }
    }
    val tail = remember { Path().apply { moveTo(15.6f, 23.5f); cubicTo(15.2f, 25.2f, 14.3f, 26.4f, 13.2f, 27.3f) } }
    val eye = Color(0xFF4A3A14).copy(alpha = 0.55f)

    Canvas(modifier.size(size)) {
        // Read here (the draw phase), so the animation only redraws this little canvas.
        val t = beat.value
        val flap = 0.5f - 0.5f * cos(t * 4f * PI.toFloat())   // 0 → 1 → 0, twice
        val glow = sin(t * PI.toFloat())                         // 0 → 1 → 0, once
        scale(this.size.width / 32f, pivot = Offset.Zero) {
            // Halo
            val haloR = 14f * (1f + 0.08f * glow)
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.45f + 0.25f * glow), accent.copy(alpha = 0f)), center = Offset(16f, 17f), radius = haloR),
                radius = haloR, center = Offset(16f, 17f),
            )
            // Wings: they fold in a little around their inner corner when she flaps.
            rotate(-14f * flap, pivot = Offset(15f, 13.1f)) {
                scale(1f - 0.16f * flap, 1f, pivot = Offset(15f, 13.1f)) {
                    drawPath(leftWing, Brush.linearGradient(listOf(accentHi.copy(alpha = 0.95f), Color.White.copy(alpha = 0.2f)), start = Offset(3f, 15f), end = Offset(15f, 5.5f)))
                }
            }
            rotate(14f * flap, pivot = Offset(17f, 13.1f)) {
                scale(1f - 0.16f * flap, 1f, pivot = Offset(17f, 13.1f)) {
                    drawPath(rightWing, Brush.linearGradient(listOf(accentHi.copy(alpha = 0.95f), Color.White.copy(alpha = 0.2f)), start = Offset(17f, 15f), end = Offset(29f, 5.5f)))
                }
            }
            // Body, eyes, tail
            val body = Brush.radialGradient(0f to Color.White, 0.5f to Color(0xFFFFFBE8), 1f to accentHi, center = Offset(16f, 18f), radius = 5.2f)
            drawCircle(body, radius = 5.2f, center = Offset(16f, 18f))
            drawCircle(eye, radius = 0.75f, center = Offset(14.1f, 17.4f))
            drawCircle(eye, radius = 0.75f, center = Offset(17.9f, 17.4f))
            drawPath(tail, accentHi.copy(alpha = 0.6f), style = Stroke(width = 1.2f, cap = StrokeCap.Round))
        }
    }
}

/** The date, in [style]. [scale] shrinks it for the settings tiles. Plain = today's date exactly. */
@Composable
fun DateFace(style: DateStyle, time: LocalDateTime, modifier: Modifier = Modifier, scale: Float = 1f) {
    val look = LocalLauncherLook.current
    val locale = Locale.getDefault()
    // Phone language decides the words: Dutch gives "do 22 okt" (we drop the dot after "okt.").
    fun format(pattern: String) = time.format(DateTimeFormatter.ofPattern(pattern, locale)).replace(".", "")
    val plain = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = (17 * scale).sp, shadow = look.shadow)

    when (style) {
        DateStyle.Plain -> Text(format("EEE d MMM"), modifier, style = plain, softWrap = false, maxLines = 1)

        DateStyle.Capitals -> Text(format("EEE d MMM").uppercase(locale), modifier, softWrap = false, maxLines = 1,
            style = plain.copy(fontSize = (14.5f * scale).sp, letterSpacing = 0.2.em))

        // "donderdag 23 oktober"
        DateStyle.Long -> Text(format("EEEE d MMMM"), modifier, softWrap = false, maxLines = 1,
            style = plain.copy(letterSpacing = 0.01.em))

        // The short date on a small frosted pill.
        DateStyle.Pill -> Text(
            format("EEE d MMM"),
            modifier
                .clip(CircleShape)
                .background(look.panel.copy(alpha = 0.5f))
                .border(1.dp, look.text.copy(alpha = 0.14f), CircleShape)
                .padding(horizontal = 14.dp * scale, vertical = 4.dp * scale),
            softWrap = false, maxLines = 1,
            style = plain.copy(shadow = null),
        )

        // A big day number in the accent, with the month and weekday stacked beside it, like a calendar stamp.
        DateStyle.Stamp -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(time.dayOfMonth.toString(), softWrap = false, maxLines = 1,
                style = TextStyle(fontFamily = look.font, color = look.accent, fontSize = (42 * scale).sp,
                    lineHeight = 1.em, lineHeightStyle = TightLines, shadow = look.shadow))
            Spacer(Modifier.width(9.dp * scale))
            val small = plain.copy(fontSize = (13 * scale).sp, letterSpacing = 0.14.em, lineHeight = 1.2.em)
            Column {
                Text(format("MMM").uppercase(locale), style = small, softWrap = false, maxLines = 1)
                Text(format("EEEE").uppercase(locale), style = small, softWrap = false, maxLines = 1)
            }
        }
    }
}

/**
 * The frame around the glance line for [style]: nothing (Plain), a frosted pill (Pill, Icons only),
 * or a pill with an accent ring and halo (Glow). Put it before the line's own padding.
 */
@Composable
fun Modifier.glanceFrame(style: GlanceStyle): Modifier {
    val look = LocalLauncherLook.current
    return when (style) {
        GlanceStyle.Plain -> this
        // Framed styles get a little room above (23 Oct: the pill sat right against the date).
        GlanceStyle.Pill, GlanceStyle.IconsOnly -> this
            .padding(top = 8.dp, bottom = 2.dp)
            .clip(CircleShape)
            .background(look.panel.copy(alpha = 0.5f))
            .border(1.dp, look.text.copy(alpha = 0.14f), CircleShape)
            .padding(start = 10.dp, end = 14.dp)
        GlanceStyle.Glow -> this
            // Room for the halo, so it doesn't press against the date above (his 23 Oct screenshot).
            .padding(top = 10.dp, bottom = 4.dp)
            // A soft halo: three faint rings, drawn once (no blur effect needed, so it costs nothing).
            .drawBehind {
                fun ring(grow: Dp, alpha: Float) {
                    val g = grow.toPx()
                    drawRoundRect(
                        look.accent.copy(alpha = alpha),
                        topLeft = Offset(-g, -g),
                        size = Size(size.width + 2 * g, size.height + 2 * g),
                        cornerRadius = CornerRadius(size.height / 2 + g),
                    )
                }
                // Kept tight (it used to reach 9dp out, past the clock's left edge).
                ring(5.dp, 0.05f)
                ring(3.dp, 0.09f)
                ring(1.5.dp, 0.15f)
            }
            .clip(CircleShape)
            .background(look.panel.copy(alpha = 0.5f))
            .background(look.accent.copy(alpha = 0.14f))
            .border(1.dp, look.accent.copy(alpha = 0.6f), CircleShape)
            .padding(start = 10.dp, end = 14.dp)
    }
}

/** A made-up glance line ("11° Partly cloudy · 82%") in [style], for the settings page. */
@Composable
fun GlanceSample(style: GlanceStyle, modifier: Modifier = Modifier, fontSize: TextUnit = 15.sp) {
    val look = LocalLauncherLook.current
    val text = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = fontSize, shadow = look.shadow)
    val iconSize = (fontSize.value * 1.2f).dp
    Row(modifier.glanceFrame(style).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_partly_cloudy_day), contentDescription = null, tint = look.text, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(6.dp))
        Text(if (style.showsWords) "11° Partly cloudy" else "11°", style = text, softWrap = false, maxLines = 1)
        Text("  ·  ", style = text)
        Icon(painterResource(R.drawable.ic_battery_full), contentDescription = null, tint = look.text, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(6.dp))
        Text("82%", style = text, softWrap = false, maxLines = 1)
    }
}

/** The time in words, rounded to 5 minutes: (first line, second line), e.g. ("quarter past", "eight"). */
internal fun timeInWords(hour: Int, minute: Int): Pair<String, String> {
    var rounded = (minute + 2) / 5 * 5
    var h = hour % 12
    if (rounded == 60) {
        rounded = 0
        h = (h + 1) % 12
    }
    val now = HOUR_WORDS[h]
    val next = HOUR_WORDS[(h + 1) % 12]
    val phrase = when (rounded) {
        0 -> return now to "o’clock"
        5 -> "five past"
        10 -> "ten past"
        15 -> "quarter past"
        20 -> "twenty past"
        25 -> "twenty-five past"
        30 -> "half past"
        35 -> "twenty-five to"
        40 -> "twenty to"
        45 -> "quarter to"
        50 -> "ten to"
        else -> "five to"
    }
    // Up to half past we count from this hour, after that toward the next one.
    return phrase to (if (rounded <= 30) now else next)
}

private val HOUR_WORDS = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")

/**
 * The current time as state, for screens other than the home (the home has its own TIME_TICK one).
 * Wakes once a minute, right on the minute, and only while the screen is visible.
 */
@Composable
fun rememberClockTime(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    WhileOnScreen(true) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    return now
}

/** Runs [block] while the screen is visible ("STARTED"); it stops when you leave and starts again when you're back. */
@Composable
private fun WhileOnScreen(enabled: Boolean, block: suspend CoroutineScope.() -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, enabled) {
        if (enabled) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }
}

/** False when he switched animations off in Android's developer options ("Animator duration scale" = off). */
private fun animationsOn(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
