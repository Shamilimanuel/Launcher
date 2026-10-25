package com.shamil.launcher.ui.home

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shamil.launcher.apps.AppCategories
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.LaunchStats
import com.shamil.launcher.ui.GlanceState
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Radial wheel home (his pick from the home-styles round, 25 Oct; v0). A calm home: just the clock, date and glance
 * line. Everything else hides in a wheel at the bottom-right corner: hold the soft glow there (160 ms) or drag out
 * of it and a ring of shelves fans out round your thumb (Favourites first, then his biggest app groups). Slide onto
 * a shelf and its apps bloom on an outer ring; let go on one to open it. One move, without looking.
 * A quick tap on the corner leaves the wheel open instead, so you can tap a shelf, then an app (also the TalkBack
 * way in). Tap outside, tap the corner again, or Back, and it closes.
 *
 * Numbers are the mockup's (home-styles-2.html, HOMES.wheel, a 390-wide phone), scaled by min(width, height) / 390.
 * Like the rails, everything that moves is a plain field read while drawing: nothing recomposes per frame, and the
 * frame loop sleeps as soon as nothing moves (the closed home draws nothing new at all).
 *
 * Left hand is v1: everything goes through [WheelHomeState.mirrored] / [WheelHomeState.side], so flipping it (from
 * RailSide.left or a setting) moves the corner to the bottom left and mirrors the angles.
 */

/** The mockup's phone width: one "unit" = screen / 390. */
private const val UNIT_W = 390f
/** The hot corner's quarter circle. */
private const val CORNER_R = 132f
/** Inner ring (shelves) and outer ring (apps). */
private const val R1 = 168f
private const val R2 = 286f
private const val SHELF_HALF = 21f
private const val APP_HALF = 23f
/** Closer than this to the corner = "back at the thumb": nothing picked. */
private const val CENTRE_DEAD = 58f
/** Where the shelves' ring ends and the apps' ring starts (with a little give either way, see [RING_HYST]). */
private const val RING1_EDGE = R1 + 44f
private const val RING2_EDGE = 450f
private const val RING_HYST = 10f
/** A few degrees of give on the wedge edges, so a wobble on a border doesn't flicker between two shelves. */
private const val WEDGE_HYST_DEG = 4f
/** For taps (not flicks), how near the ring line a tap must land to count. */
private const val TAP_BAND = 46f
/** Hold the corner this long (or move [OPEN_MOVE]) and the wheel opens. */
private const val OPEN_MS = 160L
private val OPEN_MOVE = 14.dp
private const val MAX_SHELVES = 6
private const val MAX_APPS = 8
/** The stagger: each next shelf / app pops in this much later (seconds). */
private const val SHELF_STAGGER = 0.035f
private const val APP_STAGGER = 0.028f
/** His colour glide: 0.4 s. */
private const val GLIDE = 0.4f
private const val GO_TIME = 0.4f
private const val SPARKS = 28
private const val SPARK_LIFE = 0.85f
private const val PI_F = 3.1415927f
private const val HALF_PI = 1.5707964f
private const val TWO_PI = 6.2831855f

// What's under the finger: a shelf index (0..5), APP + its slot, the All apps chip, or nothing.
private const val NONE = -1
private const val APP = 100
private const val CHIP = 200

private val WH_GOLD = Color(0xFFF3D79C)
private val WH_LIGHT = Color(0xFFFFF6DC)
private val WH_INK = Color(0xFF171C29)
private val WH_DISC = Color(0xE612162E)      // the mockup's rgba(18,22,46,.9)
private val WH_DIM_0 = Color(0xF0060814)     // rgba(6,8,20,.94), right at the corner
private val WH_DIM_1 = Color(0xBD060814)     // .74
private val WH_DIM_2 = Color(0x8C060814)     // .55, far away
private val WH_MORE_DARK = Color(0xFFD3BC8E)
private val WH_SUB = Color(0xB3F1EBDD)
private val WH_CHIP = Color(0xB311152C)      // rgba(17,21,44,.7)

/** The little line drawing inside a shelf bubble (24-unit boxes, like the mockup's SVG icons). */
private enum class ShelfGlyph { STAR, SOCIAL, MEDIA, GAMES, WORK, MONEY, TRAVEL, NEWS, TOOLS, MORE }

/** One shelf on the inner ring. [keepOrder]: Favourites stay in his order; the others go by use. */
private class Shelf(val name: String, val colour: Color, val glyph: ShelfGlyph, val apps: List<AppEntry>, val keepOrder: Boolean)

/** Each category wears an element colour: Hydro for chat, Pyro for media, Electro for games, and so on. */
private fun styleOf(category: String): Pair<Color, ShelfGlyph> = when (category) {
    AppCategories.SOCIAL -> Color(0xFF4CC2F1) to ShelfGlyph.SOCIAL    // Hydro
    AppCategories.MEDIA -> Color(0xFFEF7938) to ShelfGlyph.MEDIA      // Pyro
    AppCategories.GAMES -> Color(0xFFB47FDC) to ShelfGlyph.GAMES      // Electro
    AppCategories.WORK -> Color(0xFFA5C83B) to ShelfGlyph.WORK        // Dendro
    AppCategories.MONEY -> Color(0xFFF8C84A) to ShelfGlyph.MONEY      // Geo
    AppCategories.TRAVEL -> Color(0xFF74C2A8) to ShelfGlyph.TRAVEL    // Anemo
    AppCategories.NEWS -> Color(0xFF9FD6E3) to ShelfGlyph.NEWS        // Cryo
    AppCategories.TOOLS -> Color(0xFFB8C4D9) to ShelfGlyph.TOOLS      // a cool silver
    else -> WH_MORE_DARK to ShelfGlyph.MORE
}

/**
 * The shelves: Favourites (his first 8) first, then the app groups, biggest first, six shelves at most. If the
 * groups don't all fit, the biggest named ones keep their own shelf and the rest (plus "Other") share one "More".
 */
private fun buildShelves(favourites: List<AppEntry>, groups: Map<String, List<AppEntry>>?): List<Shelf> = buildList {
    if (favourites.isNotEmpty()) add(Shelf("Favourites", WH_GOLD, ShelfGlyph.STAR, favourites.take(MAX_APPS), keepOrder = true))
    if (groups == null) return@buildList
    val slots = MAX_SHELVES - size
    val named = groups.filterKeys { it != AppCategories.OTHER }.entries.filter { it.value.isNotEmpty() }.sortedByDescending { it.value.size }
    val other = groups[AppCategories.OTHER].orEmpty()
    fun shelf(name: String, apps: List<AppEntry>): Shelf {
        val (colour, glyph) = styleOf(name)
        return Shelf(name, colour, glyph, apps, keepOrder = false)
    }
    if (named.size + (if (other.isEmpty()) 0 else 1) <= slots) {
        named.forEach { add(shelf(it.key, it.value)) }
        if (other.isNotEmpty()) add(shelf(AppCategories.OTHER, other))
    } else {
        named.take(slots - 1).forEach { add(shelf(it.key, it.value)) }
        val rest = (named.drop(slots - 1).flatMap { it.value } + other).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        add(Shelf("More", WH_MORE_DARK, ShelfGlyph.MORE, rest, keepOrder = false))
    }
}

/** Most opened first (LaunchStats, the last 14 days), then A–Z: the same order as the petal fan. */
private fun byUse(apps: List<AppEntry>, uses: Map<String, Int>): List<AppEntry> =
    apps.sortedWith(
        compareByDescending<AppEntry> { uses[it.component.packageName] ?: 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
    )

/** The caption texts: the fixed ones measured once, the rest when they change (never per frame). */
private class WheelTexts(private val measurer: TextMeasurer, font: FontFamily) {
    private val shade = Shadow(Color.Black.copy(alpha = 0.85f), blurRadius = 10f)
    private val titleStyle = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 17.sp, shadow = shade)
    private val subStyle = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, shadow = shade)
    val chip = measurer.measure("All apps", TextStyle(fontFamily = font, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp))
    val dots = measurer.measure("…", TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 20.sp))
    val pick = title("Pick a shelf", Int.MAX_VALUE)
    val flick = sub("flick toward it, or tap")

    fun title(text: String, maxWidth: Int): TextLayoutResult =
        measurer.measure(text, titleStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(1)))
    fun sub(text: String): TextLayoutResult = measurer.measure(text, subStyle, maxLines = 1)
}

/** The wheel's moving parts. Only [tick], [isOpen], [sub] and [ringApps] are Compose state; the rest is plain. */
private class WheelHomeState(private val scope: CoroutineScope) {
    val tick = mutableIntStateOf(0)
    private var running = false

    /** Open = the wheel takes touches (and Back). It still fades out for a moment after closing. */
    var isOpen by mutableStateOf(false)
        private set
    /** The shelf whose apps are on the outer ring, or -1. */
    var sub by mutableIntStateOf(-1)
        private set
    /** The outer ring: that shelf's apps; null = the "All apps" slot when a shelf has more than fit. */
    var ringApps by mutableStateOf<List<AppEntry?>>(emptyList())
        private set

    // Wired up from the composable.
    var shelves: List<Shelf> = emptyList()
    var texts: WheelTexts? = null
    var haptics: HapticFeedback? = null
    var onOpen: (AppEntry) -> Unit = {}
    var onAll: () -> Unit = {}
    var usesNow: () -> Map<String, Int> = { emptyMap() }
    private var uses: Map<String, Int> = emptyMap()

    /** v1 hook: true = left-hand wheel in the bottom-left corner. Positions and angles all go through [side]. */
    var mirrored = false
    val side: Float get() = if (mirrored) -1f else 1f

    // Sizes (px, in the wheel layer's own coordinates = the whole home).
    var w = 1f; var h = 1f
    /** One mockup unit in px. */
    var s = 1f
    /** The wheel's centre: the screen corner, lifted above the system's bottom gesture strip. */
    var cx = 0f; var cy = 0f
    var rootX = 0f; var rootY = 0f
    var cornerX = 0f; var cornerY = 0f
    var chipRect = Rect.Zero

    var t = 0f
    var openA = 0f
    var visible = false
    var touching = false
    var downT = 0f
    var startX = 0f; var startY = 0f
    /** Under the finger while sliding (hover), and under a finger that's down for a tap (press sink). */
    var hov = NONE
    var press = NONE

    val shelfAp = FloatArray(MAX_SHELVES)
    val shelfV = FloatArray(MAX_SHELVES)
    val shelfLift = FloatArray(MAX_SHELVES) { 1f }
    val shelfGlow = FloatArray(MAX_SHELVES)
    /** 0..1: how far the shelf's bubble has filled with its colour (the 0.4 s glide). */
    val shelfOn = FloatArray(MAX_SHELVES)
    var shelfShownAt = 0f
    val appAp = FloatArray(MAX_APPS)
    val appV = FloatArray(MAX_APPS)
    val appLift = FloatArray(MAX_APPS) { 1f }
    val appGlow = FloatArray(MAX_APPS)
    var appShownAt = 0f
    /** The app let go on: it swells and fades as it opens. */
    var goK = -1
    var goT = 0f

    // The outer ring's tint glides from the last shelf's colour to the new one.
    private var glideFrom = WH_GOLD
    private var glideTo = WH_GOLD
    private var glideT0 = -10f

    var title: TextLayoutResult? = null
    var subLine: TextLayoutResult? = null
    /** The title is an app's name (drawn light) rather than a shelf (drawn in its colour). */
    var titleIsApp = false

    // Sparkles: a small burst when the wheel opens, a shelf opens or an app launches. They die out in under a second.
    val spX = FloatArray(SPARKS); val spY = FloatArray(SPARKS)
    val spVx = FloatArray(SPARKS); val spVy = FloatArray(SPARKS)
    val spLife = FloatArray(SPARKS); val spSize = FloatArray(SPARKS)

    var paint: WheelHomePaint? = null

    fun setSize(width: Float, height: Float, unit: Float, bottom: Float) {
        w = width; h = height; s = unit
        cx = if (mirrored) 0f else width
        cy = height - bottom
        val tx = texts ?: return
        val cw = tx.chip.size.width + 28f * s
        val ch = tx.chip.size.height + 12f * s
        val chipBottom = cy - 376f * s
        val left = if (mirrored) 20f * s else w - 20f * s - cw
        chipRect = Rect(left, chipBottom - ch, left + cw, chipBottom)
    }

    /** Lays the chip out again (new texts, same size). */
    fun relayout() { if (w > 1f) setSize(w, h, s, h - cy) }

    /** A point [r] units from the centre, [u] of the way round the quarter (0 = flat toward the middle, 1 = straight up). */
    fun px(r: Float, u: Float) = cx + side * r * s * cos(PI_F + u * HALF_PI)
    fun py(r: Float, u: Float) = cy + r * s * sin(PI_F + u * HALF_PI)

    fun shelfCount() = min(shelves.size, MAX_SHELVES)

    /**
     * Polar hit-test: distance picks the ring, angle picks the wedge. [prev] gets a little give on its edges.
     * [strict] (taps): the tap must land near the ring line; flicks may land anywhere in the ring's band.
     */
    fun hit(x: Float, y: Float, prev: Int, strict: Boolean): Int {
        if (isOpen && chipRect.inflate(6f * s).contains(Offset(x, y))) return CHIP
        val dx = (x - cx) * side
        val dy = y - cy
        val r = hypot(dx, dy) / s
        var a = atan2(dy, dx)
        if (a < 0f) a += TWO_PI
        val u = (a - PI_F) / HALF_PI
        if (r < CENTRE_DEAD || u < -0.04f || u > 1.04f) return NONE
        val n = shelfCount()
        val edge = RING1_EDGE + when {
            prev in 0 until APP -> RING_HYST
            prev in APP until CHIP -> -RING_HYST
            else -> 0f
        }
        if (r < edge) {
            if (n == 0 || (strict && abs(r - R1) > TAP_BAND)) return NONE
            return wedge(u, n, if (prev in 0 until APP) prev else -1)
        }
        val m = ringApps.size
        if (sub < 0 || m == 0 || r > RING2_EDGE || (strict && abs(r - R2) > TAP_BAND)) return NONE
        return APP + wedge(u, m, if (prev in APP until CHIP) prev - APP else -1)
    }

    private fun wedge(u: Float, n: Int, prev: Int): Int {
        val raw = u.coerceIn(0f, 0.9999f) * n
        if (prev in 0 until n) {
            val give = WEDGE_HYST_DEG / (90f / n)
            if (raw >= prev - give && raw < prev + 1 + give) return prev
        }
        return raw.toInt().coerceIn(0, n - 1)
    }

    fun open() {
        if (isOpen) return
        isOpen = true
        visible = true
        // How often each app was opened lately, read once per opening.
        uses = usesNow()
        sub = -1
        ringApps = emptyList()
        shelfShownAt = t
        for (i in 0 until MAX_SHELVES) { shelfAp[i] = 0f; shelfV[i] = 0f; shelfOn[i] = 0f; shelfLift[i] = 1f; shelfGlow[i] = 0f }
        resetApps()
        hov = NONE; press = NONE; goK = -1
        glideFrom = WH_GOLD; glideTo = WH_GOLD; glideT0 = t - GLIDE
        texts?.let { setCaption(it.pick, it.flick, app = false) }
        haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
        // A soft shimmer along the shelves' ring as it fans out.
        for (k in 0 until 10) { val u = Random.nextFloat(); burst(px(R1, u), py(R1, u), 1) }
        wake()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        sub = -1
        hov = NONE; press = NONE
        wake()
    }

    /** At once, no fade (home went to the background: an app opened, the screen went off). */
    fun closeNow() {
        isOpen = false
        sub = -1
        hov = NONE; press = NONE; goK = -1
        openA = 0f; visible = false
        for (i in 0 until MAX_SHELVES) { shelfAp[i] = 0f; shelfV[i] = 0f; shelfOn[i] = 0f }
        resetApps()
        spLife.fill(0f)
        tick.intValue++
    }

    fun toggle() { if (isOpen) close() else open() }

    private fun resetApps() {
        appShownAt = t
        for (k in 0 until MAX_APPS) { appAp[k] = 0f; appV[k] = 0f; appLift[k] = 1f; appGlow[k] = 0f }
    }

    /** Opens shelf [i]: its apps bloom on the outer ring, most used first. */
    fun selectShelf(i: Int) {
        if (i == sub || i !in 0 until shelfCount()) return
        val shelf = shelves[i]
        val list = if (shelf.keepOrder) shelf.apps else byUse(shelf.apps, uses)
        // More than fit: the last slot becomes "All apps" (the A–Z list has the rest).
        ringApps = if (list.size > MAX_APPS) list.take(MAX_APPS - 1) + listOf(null) else list
        sub = i
        goK = -1
        resetApps()
        glideFrom = ringColour(); glideTo = shelf.colour; glideT0 = t
        shelfCaption()
        val u = (i + 0.5f) / shelfCount()
        burst(px(R1, u), py(R1, u), 6)
        wake()
    }

    /** The outer ring's tint right now (mid-glide or settled). */
    fun ringColour(): Color {
        val p = ((t - glideT0) / GLIDE).coerceIn(0f, 1f)
        return lerp(glideFrom, glideTo, p * p * (3f - 2f * p))
    }

    private fun setCaption(t1: TextLayoutResult, t2: TextLayoutResult, app: Boolean) {
        title = t1; subLine = t2; titleIsApp = app
    }

    private fun shelfCaption() {
        val tx = texts ?: return
        val shelf = shelves.getOrNull(sub)
        if (shelf == null) { setCaption(tx.pick, tx.flick, app = false); return }
        val n = shelf.apps.size
        setCaption(tx.title(shelf.name, (w * 0.7f).toInt()), tx.sub(if (n == 1) "1 app" else "$n apps"), app = false)
    }

    private fun appCaption(k: Int) {
        val tx = texts ?: return
        val app = ringApps.getOrNull(k)
        val shelf = shelves.getOrNull(sub)?.name ?: ""
        setCaption(tx.title(app?.label ?: "All apps", (w * 0.7f).toInt()), tx.sub(if (app == null) "everything, A–Z" else shelf), app = true)
    }

    private fun tickHaptic() = haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** The finger slid to ([x], [y]): sliding onto a shelf opens it, onto an app lights it up (a tick each time). */
    fun hover(x: Float, y: Float) {
        val now = hit(x, y, hov, strict = false)
        if (now == hov) return
        hov = now
        when {
            now in 0 until APP -> { if (now != sub) selectShelf(now) else shelfCaption(); tickHaptic() }
            now in APP until CHIP -> { appCaption(now - APP); tickHaptic() }
            now == CHIP -> { texts?.let { setCaption(it.title("All apps", Int.MAX_VALUE), it.sub("everything, A–Z"), app = true) }; tickHaptic() }
            else -> shelfCaption()
        }
        wake()
    }

    /** A finger is down for a tap: the thing under it sinks a little and glows. */
    fun pressAt(x: Float, y: Float): Int {
        press = hit(x, y, NONE, strict = true)
        if (press in APP until CHIP) appCaption(press - APP)
        wake()
        return press
    }

    /** Let go after a flick (or tapped an app): open the app, open All apps, stay on a shelf, or close. */
    fun release(code: Int) {
        when {
            code in APP until CHIP -> {
                val k = code - APP
                val app = ringApps.getOrNull(k)
                if (app != null) {
                    goK = k; goT = t
                    val u = (k + 0.5f) / ringApps.size
                    burst(px(R2, u), py(R2, u), 8)
                    close()
                    onOpen(app)
                } else {
                    close()
                    onAll()
                }
            }
            code == CHIP -> { close(); onAll() }
            code in 0 until APP -> {}   // let go on a shelf: it stays open, tap or flick on from there
            else -> close()
        }
    }

    /** A tap while the wheel is open (not a flick). */
    fun tap(code: Int) {
        when {
            code in 0 until APP -> if (code != sub) { selectShelf(code); tickHaptic() }
            code >= APP -> release(code)
            else -> close()
        }
    }

    private fun burst(x: Float, y: Float, count: Int) {
        var made = 0
        for (i in 0 until SPARKS) {
            if (made >= count) break
            if (spLife[i] > 0f) continue
            val a = Random.nextFloat() * TWO_PI
            val speed = (14f + Random.nextFloat() * 46f) * s
            spX[i] = x + cos(a) * 6f * s
            spY[i] = y + sin(a) * 6f * s
            spVx[i] = cos(a) * speed
            spVy[i] = sin(a) * speed - 22f * s
            spLife[i] = 1f
            spSize[i] = 0.8f + Random.nextFloat() * 1.4f
            made++
        }
    }

    fun wake() {
        tick.intValue++
        if (running) return
        running = true
        scope.launch {
            try {
                var last = -1L
                while (true) {
                    val keep = withFrameNanos { now ->
                        val dt = if (last < 0) 1f / 60f else min(0.05f, (now - last) / 1_000_000_000f)
                        last = now
                        step(dt).also { tick.intValue++ }
                    }
                    if (!keep) break
                }
            } finally {
                running = false
            }
        }
    }

    private fun step(dt: Float): Boolean {
        t += dt
        val target = if (isOpen) 1f else 0f
        openA = whDamp(openA, target, if (isOpen) 16f else 12f, dt)
        var moving = abs(openA - target) > 0.004f
        if (!moving) openA = target
        val nS = shelfCount()
        for (i in 0 until MAX_SHELVES) {
            if (isOpen && i < nS) {
                // Pop in on a bouncy spring, one after the other (his stagger).
                if (t - shelfShownAt >= i * SHELF_STAGGER) { if (springTo1(shelfAp, shelfV, i, dt)) moving = true } else moving = true
            } else {
                shelfV[i] = 0f
                if (settle(shelfAp, i, 0f, 14f, dt)) moving = true
            }
            // Press sink (0.9) for a tap, lift (1.3) for a flick, with a glow either way.
            val lift = when { press == i -> 0.9f; hov == i -> 1.3f; else -> 1f }
            if (settle(shelfLift, i, lift, 22f, dt)) moving = true
            if (settle(shelfGlow, i, if (press == i || hov == i) 1f else 0f, 18f, dt)) moving = true
            // The colour fill: about 0.4 s.
            if (settle(shelfOn, i, if (sub == i) 1f else 0f, 8f, dt)) moving = true
        }
        val nA = ringApps.size
        val showApps = isOpen && sub >= 0
        for (k in 0 until MAX_APPS) {
            if (showApps && k < nA) {
                if (t - appShownAt >= k * APP_STAGGER) { if (springTo1(appAp, appV, k, dt)) moving = true } else moving = true
            } else {
                appV[k] = 0f
                if (settle(appAp, k, 0f, 18f, dt)) moving = true
            }
            val code = APP + k
            val lift = when { press == code -> 0.9f; hov == code -> 1.3f; else -> 1f }
            if (settle(appLift, k, lift, 22f, dt)) moving = true
            if (settle(appGlow, k, if (press == code || hov == code) 1f else 0f, 18f, dt)) moving = true
        }
        if (goK >= 0) { if (t - goT > GO_TIME) goK = -1 else moving = true }
        if (t - glideT0 < GLIDE) moving = true
        val fade = exp(-3f * dt)
        for (i in 0 until SPARKS) {
            if (spLife[i] <= 0f) continue
            spLife[i] -= dt / SPARK_LIFE
            spX[i] += spVx[i] * dt
            spY[i] += spVy[i] * dt
            spVx[i] *= fade
            spVy[i] = spVy[i] * fade - 8f * s * dt   // and drift upward, like seelie light
            moving = true
        }
        if (!isOpen && openA <= 0f && goK < 0) visible = false
        return touching || moving
    }
}

private fun whDamp(c: Float, target: Float, k: Float, dt: Float) = c + (target - c) * (1f - exp(-k * dt))

/** Damps [arr]`[i]` toward [target]; true while it's still moving (it snaps the last bit, so the loop can sleep). */
private fun settle(arr: FloatArray, i: Int, target: Float, k: Float, dt: Float): Boolean {
    arr[i] = whDamp(arr[i], target, k, dt)
    if (abs(arr[i] - target) > 0.003f) return true
    arr[i] = target
    return false
}

/** The petal fan's pop: an under-damped spring to 1 (a little overshoot). True while it's still moving. */
private fun springTo1(ap: FloatArray, v: FloatArray, i: Int, dt: Float): Boolean {
    val k = 420f
    val c = 2f * sqrt(k) * 0.55f
    v[i] += ((1f - ap[i]) * k - v[i] * c) * dt
    ap[i] += v[i] * dt
    if (abs(1f - ap[i]) > 0.002f || abs(v[i]) > 0.01f) return true
    ap[i] = 1f; v[i] = 0f
    return false
}

/** Brushes and strokes for one size and corner, made once (not per frame). */
private class WheelHomePaint(val s: Float, val cx: Float, val cy: Float, val w: Float, val h: Float) {
    val c = Offset(cx, cy)
    // The hot corner: the mockup's radial gold, fading out just inside the quarter circle.
    val hint = Brush.radialGradient(
        0f to WH_GOLD.copy(alpha = 0.55f), 0.52f to WH_GOLD.copy(alpha = 0.14f), 0.7f to Color.Transparent,
        center = c, radius = CORNER_R * 1.414f * s,
    )
    val dim = Brush.radialGradient(0f to WH_DIM_0, 0.55f to WH_DIM_1, 1f to WH_DIM_2, center = c, radius = max(1f, hypot(w, cy)))
    val dashed = Stroke(width = 1f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * s, 5f * s)))
    val thin = Stroke(width = 1.2f * s, cap = StrokeCap.Round)
    val ring = Stroke(width = 1.5f * s)
    val glyph = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)   // in glyph units (scaled with it)
    val hot = Brush.radialGradient(
        0f to WH_GOLD.copy(alpha = 0.9f), 0.45f to WH_GOLD.copy(alpha = 0.9f), 1f to Color.Transparent,
        center = Offset.Zero, radius = 51f * s,
    )
    val hotRing = Stroke(width = 4f * s)
    val more = Brush.radialGradient(listOf(WH_LIGHT, WH_GOLD, WH_MORE_DARK), center = Offset(-6f * s, -7f * s), radius = 30f * s)
    val charge = Stroke(width = 2f * s)
    val chargeGlow = Stroke(width = 8f * s)
    val iconPx = (APP_HALF * 2f * s).roundToInt().coerceAtLeast(1)
    private val glows = HashMap<Color, Brush>()
    /** A shelf's soft glow in its own colour (the mockup's box-shadow). */
    fun glowOf(colour: Color): Brush = glows.getOrPut(colour) {
        Brush.radialGradient(listOf(colour.copy(alpha = 0.7f), Color.Transparent), center = Offset.Zero, radius = 34f * s)
    }
}

/** The glyph shapes, made once. */
private val GLYPHS: Map<ShelfGlyph, Path> by lazy { ShelfGlyph.entries.associateWith { glyphPath(it) } }

private fun glyphPath(g: ShelfGlyph): Path = Path().apply {
    fun circle(x: Float, y: Float, r: Float) = addOval(Rect(x - r, y - r, x + r, y + r))
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) { moveTo(x1, y1); lineTo(x2, y2) }
    fun box(l: Float, t: Float, r: Float, b: Float, c: Float) = addRoundRect(RoundRect(l, t, r, b, CornerRadius(c)))
    when (g) {
        ShelfGlyph.STAR -> {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 9f else 3.9f
                val a = -HALF_PI + i * PI_F / 5f
                val x = 12f + r * cos(a)
                val y = 12.6f + r * sin(a)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        ShelfGlyph.SOCIAL -> { box(4f, 5f, 20f, 16f, 4f); moveTo(8.5f, 16f); lineTo(7.5f, 19.5f); lineTo(12f, 16f) }
        ShelfGlyph.MEDIA -> { circle(12f, 12f, 8.5f); moveTo(10f, 8.6f); lineTo(15.6f, 12f); lineTo(10f, 15.4f); close() }
        ShelfGlyph.GAMES -> {
            box(3f, 7f, 21f, 17f, 5f); line(7.5f, 10f, 7.5f, 14f); line(5.5f, 12f, 9.5f, 12f)
            circle(15.5f, 10.8f, 0.6f); circle(17.6f, 13.2f, 0.6f)
        }
        ShelfGlyph.WORK -> { box(4f, 4f, 20f, 20f, 4f); moveTo(8f, 12.5f); lineTo(11f, 15.5f); lineTo(16.5f, 9f) }
        ShelfGlyph.MONEY -> { circle(12f, 12f, 8.5f); circle(12f, 12f, 4.2f) }
        ShelfGlyph.TRAVEL -> { circle(12f, 12f, 8.5f); moveTo(12f, 6.8f); lineTo(14.4f, 12f); lineTo(12f, 17.2f); lineTo(9.6f, 12f); close() }
        ShelfGlyph.NEWS -> { box(4f, 5f, 20f, 19f, 2.5f); line(7.5f, 9.5f, 16.5f, 9.5f); line(7.5f, 12.5f, 16.5f, 12.5f); line(7.5f, 15.5f, 13f, 15.5f) }
        ShelfGlyph.TOOLS -> { line(5f, 8f, 19f, 8f); line(5f, 16f, 19f, 16f); circle(9f, 8f, 2f); circle(15f, 16f, 2f) }
        ShelfGlyph.MORE -> { circle(7f, 12f, 0.9f); circle(12f, 12f, 0.9f); circle(17f, 12f, 0.9f) }
    }
}

/**
 * Whether the wheel is open right now, for HomeScreen: while it is, the edge rail should step aside (its strip on the
 * right edge overlaps the wheel's top shelves and apps, and it sits on top, so it would catch their taps).
 */
object WheelHomeStatus {
    var open by mutableStateOf(false)
        internal set
}

/**
 * Radial wheel home: a calm home; hold the bottom corner and flick to a shelf, then to an app.
 * [favourites] = his favourites in order (the first shelf); [apps] = every listed app (vault and hidden apps already
 * removed), sorted A–Z, for the category shelves; [onOpenApp] opens one (vault-guarded, counted);
 * [onAllApps] opens the A–Z list.
 */
@Composable
fun WheelHome(
    favourites: List<AppEntry>,
    apps: List<AppEntry>,
    glance: GlanceState,
    onOpenApp: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val st = remember { WheelHomeState(scope) }

    // Which group each app is in: worked out once per app list, off the main thread (it can ask the package manager).
    val groups by produceState<Map<String, List<AppEntry>>?>(initialValue = null, apps) {
        value = withContext(Dispatchers.IO) {
            runCatching { apps.groupBy { AppCategories.of(context, it.component.packageName) } }.getOrNull()
        }
    }
    val shelves = remember(favourites, groups) { buildShelves(favourites, groups) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer, look.font) { WheelTexts(measurer, look.font) }
    val appContext = context.applicationContext
    SideEffect {
        if (st.shelves !== shelves) {
            // The app list changed under an open wheel (an app installed): start fresh rather than point at a moved shelf.
            if (st.isOpen) st.close()
            st.shelves = shelves
        }
        if (st.texts !== texts) { st.texts = texts; st.relayout() }
        st.haptics = haptics
        st.onOpen = onOpenApp
        st.onAll = onAllApps
        st.usesNow = { LaunchStats.get(appContext).mostOpened().toMap() }
        WheelHomeStatus.open = st.isOpen
    }
    DisposableEffect(Unit) { onDispose { WheelHomeStatus.open = false } }

    BackHandler(enabled = st.isOpen) { st.close() }

    // Home went away (an app opened, screen off): close at once, so coming back finds the calm home.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) st.closeNow() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The corner sits above the bottom strip Android keeps for home / recents (that strip can never be excluded).
    val bottomPx = maxOf(
        WindowInsets.systemGestures.getBottom(density),
        WindowInsets.mandatorySystemGestures.getBottom(density),
        WindowInsets.navigationBars.getBottom(density),
    ) + with(density) { 2.dp.roundToPx() }

    BoxWithConstraints(modifier) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else wPx * 2f
        val unit = min(wPx, hPx) / UNIT_W
        SideEffect { st.setSize(wPx, hPx, unit, bottomPx.toFloat()) }
        val cornerDp = with(density) { (CORNER_R * unit).toDp() }
        val bottomDp = with(density) { bottomPx.toDp() }

        // The calm home: clock, date and glance line in his home style, in the top third. Nothing else.
        val lay = currentHomeLayout()
        val now = rememberWheelNow()
        val noRipple = remember { MutableInteractionSource() }
        val screenH = maxHeight
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(screenH * lay.clockTop.coerceIn(0.05f, 0.33f)))
            HomeTop(
                time = now,
                glance = glance,
                clockModifier = Modifier.clickable(interactionSource = noRipple, indication = null) {
                    wheelOpenSafely(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
                },
                dateModifier = Modifier.clickable(interactionSource = noRipple, indication = null) {
                    val today = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                    ContentUris.appendId(today, System.currentTimeMillis())
                    wheelOpenSafely(context, Intent(Intent.ACTION_VIEW, today.build()))
                },
            )
        }

        // The wheel layer: draws the corner glow always, and the dim + wheel while open. It only takes touches while
        // the wheel is open, so the clock underneath stays tappable.
        Spacer(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { val p = it.positionInRoot(); st.rootX = p.x; st.rootY = p.y }
                .then(
                    if (st.isOpen) Modifier.pointerInput(Unit) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            st.touching = true
                            val first = st.pressAt(down.position.x, down.position.y)
                            var dragged = false
                            var lifted = false
                            try {
                                lifted = followFinger(down.id, down.position, slop) { x, y, far ->
                                    if (far && !dragged) { dragged = true; st.press = NONE }
                                    if (dragged) st.hover(x, y)
                                }
                            } finally {
                                st.touching = false
                                st.press = NONE
                                st.wake()
                            }
                            if (lifted) { if (dragged) st.release(st.hov) else st.tap(first) }
                            st.hov = NONE
                        }
                    } else Modifier,
                )
                // Its own layer: redrawing the wheel never redraws the clock behind it.
                .graphicsLayer()
                .drawBehind {
                    st.tick.intValue   // redraw on every frame of the loop
                    drawWheelHome(st)
                },
        )

        WheelTalkBack(st)

        // The hot corner. Holding it charges a ring for 160 ms, then the wheel opens; dragging out opens it at once.
        val cornerLabel = if (st.isOpen) "Close the app wheel" else "App wheel: hold and flick to a shelf, or tap to open it"
        Box(
            Modifier
                .align(if (st.mirrored) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(bottom = bottomDp)
                .size(cornerDp)
                // Tell Android not to start its back swipe here (gesture navigation).
                .systemGestureExclusion()
                .onGloballyPositioned { val p = it.positionInRoot(); st.cornerX = p.x; st.cornerY = p.y }
                .semantics {
                    contentDescription = cornerLabel
                    role = Role.Button
                    onClick { st.toggle(); true }
                }
                .pointerInput(Unit) {
                    val openMove = OPEN_MOVE.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // The corner's own coordinates → the wheel layer's.
                        val ox = st.cornerX - st.rootX
                        val oy = st.cornerY - st.rootY
                        val bx = ox + down.position.x
                        val by = oy + down.position.y
                        // Only the quarter circle counts, not the square's far corner.
                        if (hypot(bx - st.cx, by - st.cy) > CORNER_R * st.s) return@awaitEachGesture
                        down.consume()
                        val downAt = SystemClock.uptimeMillis()
                        val wasOpen = st.isOpen
                        var opened = wasOpen
                        var dragged = false
                        var lifted = false
                        st.touching = true
                        st.downT = st.t
                        st.startX = bx; st.startY = by
                        st.wake()
                        try {
                            while (true) {
                                val wait = if (opened) Long.MAX_VALUE else OPEN_MS - (SystemClock.uptimeMillis() - downAt)
                                val event = if (wait == Long.MAX_VALUE) awaitPointerEvent()
                                else withTimeoutOrNull(wait.coerceAtLeast(1L)) { awaitPointerEvent() }
                                if (event == null) {
                                    // Held long enough: open.
                                    if (!opened) { st.open(); opened = true }
                                    continue
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) { change.consume(); lifted = true; break }
                                change.consume()
                                val x = ox + change.position.x
                                val y = oy + change.position.y
                                if (!dragged && hypot(x - bx, y - by) >= openMove) {
                                    dragged = true
                                    if (!opened) { st.open(); opened = true }
                                }
                                if (dragged) st.hover(x, y)
                            }
                        } finally {
                            st.touching = false
                            st.wake()
                        }
                        if (lifted) {
                            when {
                                // A quick tap: open and stay open, to tap around (the accessible way).
                                !opened -> st.open()
                                dragged -> st.release(st.hov)
                                // A quick tap on the corner of an open wheel closes it.
                                wasOpen && SystemClock.uptimeMillis() - downAt < OPEN_MS -> st.close()
                                else -> {}   // held without moving: it stays open
                            }
                        }
                        st.hov = NONE
                    }
                },
        )
    }
}

/**
 * Follows one finger until it lifts (true) or the gesture is taken away (false), consuming its moves.
 * [onMove] gets the position and whether it has moved past [slop] since it went down.
 */
private suspend fun AwaitPointerEventScope.followFinger(
    id: PointerId, start: Offset, slop: Float, onMove: (Float, Float, Boolean) -> Unit,
): Boolean {
    var far = false
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return false
        if (!change.pressed) { change.consume(); return true }
        change.consume()
        val p = change.position
        if (!far && hypot(p.x - start.x, p.y - start.y) >= slop) far = true
        onMove(p.x, p.y, far)
    }
}

/**
 * Invisible stand-ins for TalkBack while the wheel is open: one per shelf and per app on the ring, and the All apps
 * chip. They have no touch handling, so real fingers still go to the wheel layer underneath.
 */
@Composable
private fun WheelTalkBack(st: WheelHomeState) {
    if (!st.isOpen) return
    val density = LocalDensity.current
    val shelves = st.shelves.take(MAX_SHELVES)
    val sub = st.sub
    val ring = st.ringApps
    val shelfDp = with(density) { (SHELF_HALF * 2f * st.s).toDp() }
    val appDp = with(density) { (APP_HALF * 2f * st.s).toDp() }
    shelves.forEachIndexed { i, shelf ->
        val u = (i + 0.5f) / shelves.size
        Box(
            Modifier
                .offset { IntOffset((st.px(R1, u) - SHELF_HALF * st.s).roundToInt(), (st.py(R1, u) - SHELF_HALF * st.s).roundToInt()) }
                .size(shelfDp)
                .clearAndSetSemantics {
                    contentDescription = "${shelf.name} shelf, ${shelf.apps.size} apps"
                    role = Role.Button
                    selected = i == sub
                    onClick { st.tap(i); true }
                }
        )
    }
    if (sub >= 0) ring.forEachIndexed { k, app ->
        val u = (k + 0.5f) / ring.size
        Box(
            Modifier
                .offset { IntOffset((st.px(R2, u) - APP_HALF * st.s).roundToInt(), (st.py(R2, u) - APP_HALF * st.s).roundToInt()) }
                .size(appDp)
                .clearAndSetSemantics {
                    contentDescription = app?.label ?: "All apps"
                    role = Role.Button
                    onClick { st.release(APP + k); true }
                }
        )
    }
    val chip = st.chipRect
    Box(
        Modifier
            .offset { IntOffset(chip.left.roundToInt(), chip.top.roundToInt()) }
            .size(with(density) { chip.width.toDp() }, with(density) { chip.height.toDp() })
            .clearAndSetSemantics {
                contentDescription = "All apps"
                role = Role.Button
                onClick { st.release(CHIP); true }
            }
    )
}

private fun DrawScope.drawWheelHome(st: WheelHomeState) {
    val s = st.s
    val p = st.paint?.takeIf { it.s == s && it.cx == st.cx && it.cy == st.cy && it.w == size.width && it.h == size.height }
        ?: WheelHomePaint(s, st.cx, st.cy, size.width, size.height).also { st.paint = it }
    val c = p.c
    val openA = st.openA
    val startDeg = if (st.mirrored) 270f else 180f

    // At rest: only the soft glowing quarter circle (and a faint arc), above the gesture strip.
    val hintA = 1f - openA
    if (hintA > 0.01f) {
        clipRect(bottom = st.cy) {
            drawCircle(p.hint, radius = CORNER_R * s, center = c, alpha = 0.8f * hintA)
            val r = CORNER_R * 0.56f * s
            drawArc(
                WH_GOLD.copy(alpha = 0.35f * hintA), startAngle = startDeg, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = p.thin,
            )
        }
    }
    // Holding the corner: a ring under the thumb grows as the hold "charges" (like the element wheel rail).
    if (st.touching && !st.isOpen) {
        val prog = ((st.t - st.downT) / (OPEN_MS / 1000f)).coerceIn(0f, 1f)
        val r = 26f * s * (0.5f + 0.7f * prog)
        val a = 0.4f + 0.6f * prog
        val at = Offset(st.startX, st.startY)
        drawCircle(WH_GOLD.copy(alpha = 0.25f * a), radius = r, center = at, style = p.chargeGlow)
        drawCircle(WH_GOLD.copy(alpha = a), radius = r, center = at, style = p.charge)
    }
    if (!st.visible) return

    // The home dims toward the corner.
    drawRect(p.dim, alpha = openA)

    // The two rings' dashed guides; the outer one glides to the open shelf's colour.
    val ringTint = st.ringColour()
    for (k in 0..1) {
        val rr = (if (k == 0) R1 else R2) * s
        val colour = if (k == 0) WH_GOLD else ringTint
        drawArc(
            colour.copy(alpha = 0.32f * openA), startAngle = startDeg, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(c.x - rr, c.y - rr), size = Size(2 * rr, 2 * rr), style = p.dashed,
        )
    }

    // Sparkles: a glow, a bright core and a little cross, fading as they rise.
    for (i in 0 until SPARKS) {
        val life = st.spLife[i]
        if (life <= 0f) continue
        val a = (life * (1.4f - life) * 2.2f).coerceIn(0f, 1f)
        val at = Offset(st.spX[i], st.spY[i])
        val r = st.spSize[i] * s
        drawCircle(WH_GOLD.copy(alpha = 0.25f * a), radius = r * 3.2f, center = at)
        drawCircle(WH_LIGHT.copy(alpha = a), radius = r, center = at)
        drawLine(WH_LIGHT.copy(alpha = 0.6f * a), Offset(at.x - r * 3f, at.y), Offset(at.x + r * 3f, at.y), strokeWidth = 0.6f * s)
        drawLine(WH_LIGHT.copy(alpha = 0.6f * a), Offset(at.x, at.y - r * 3f), Offset(at.x, at.y + r * 3f), strokeWidth = 0.6f * s)
    }

    // The shelves: they bloom out from the thumb, one after the other.
    val nS = st.shelfCount()
    for (i in 0 until nS) {
        val ap = st.shelfAp[i]
        if (ap < 0.01f) continue
        val shelf = st.shelves[i]
        val u = (i + 0.5f) / nS
        val r = R1 * (0.45f + 0.55f * ap)
        val sc = ap.coerceAtLeast(0f) * st.shelfLift[i]
        val alpha = (openA * ap).coerceIn(0f, 1f)
        val on = st.shelfOn[i]
        val glow = st.shelfGlow[i]
        translate(st.px(r, u), st.py(r, u)) {
            scale(sc, Offset.Zero) {
                drawCircle(p.glowOf(shelf.colour), radius = 34f * s, center = Offset.Zero, alpha = alpha * (0.45f + 0.55f * max(glow, on)))
                drawCircle(lerp(WH_DISC, shelf.colour, on), radius = SHELF_HALF * s, center = Offset.Zero, alpha = alpha)
                drawCircle(shelf.colour, radius = SHELF_HALF * s - 0.75f * s, center = Offset.Zero, style = p.ring, alpha = alpha)
                val k = SHELF_HALF * s / 24f   // the mockup: a 21-unit glyph in a 42-unit bubble
                withTransform({ scale(k, k, Offset.Zero); translate(-12f, -12f) }) {
                    GLYPHS[shelf.glyph]?.let { drawPath(it, lerp(shelf.colour, WH_INK, on), alpha = alpha, style = p.glyph) }
                }
            }
        }
    }

    // The open shelf's apps on the outer ring.
    val ring = st.ringApps
    val nA = min(ring.size, MAX_APPS)
    for (k in 0 until nA) {
        val going = k == st.goK
        val ap = if (going) 1f else st.appAp[k]
        if (ap < 0.01f) continue
        val g = if (going) ((st.t - st.goT) / GO_TIME).coerceIn(0f, 1f) else 0f
        val u = (k + 0.5f) / nA
        val r = R2 * (0.6f + 0.4f * ap)
        val sc = if (going) 1.3f * (1f + 0.4f * g) else ap.coerceAtLeast(0f) * st.appLift[k]
        val alpha = if (going) 1f - g else (openA * ap).coerceIn(0f, 1f)
        translate(st.px(r, u), st.py(r, u)) {
            scale(sc, Offset.Zero) {
                val glowA = if (going) alpha else st.appGlow[k] * alpha
                if (glowA > 0.01f) {
                    drawCircle(p.hot, radius = 51f * s, center = Offset.Zero, alpha = 0.6f * glowA)
                    drawCircle(WH_GOLD.copy(alpha = 0.5f), radius = (APP_HALF + 2f) * s, center = Offset.Zero, style = p.hotRing, alpha = glowA)
                }
                val app = ring[k]
                if (app != null) {
                    val half = p.iconPx / 2
                    drawImage(
                        image = app.icon,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(app.icon.width, app.icon.height),
                        dstOffset = IntOffset(-half, -half),
                        dstSize = IntSize(p.iconPx, p.iconPx),
                        alpha = alpha,
                        filterQuality = FilterQuality.Low,
                    )
                } else {
                    // "All apps": a gold bubble with "…" (the petal fan's "+N more").
                    drawCircle(p.more, radius = APP_HALF * s, center = Offset.Zero, alpha = alpha)
                    st.texts?.dots?.let { d -> drawText(d, color = WH_INK, topLeft = Offset(-d.size.width / 2f, -d.size.height / 2f), alpha = alpha) }
                }
            }
        }
    }

    // The caption above the wheel (shelf name and count, or the app under the finger), and the All apps chip.
    val subLine = st.subLine
    val title = st.title
    val edgeX = if (st.mirrored) 20f * s else size.width - 20f * s
    fun leftOf(width: Int) = if (st.mirrored) edgeX else edgeX - width
    var top = st.cy - 322f * s
    if (subLine != null) {
        top -= subLine.size.height
        drawText(subLine, color = WH_SUB, topLeft = Offset(leftOf(subLine.size.width), top), alpha = openA)
    }
    if (title != null) {
        top -= title.size.height + 1f * s
        val colour = if (st.titleIsApp) WH_LIGHT else if (st.sub >= 0) ringTint else WH_GOLD
        drawText(title, color = colour, topLeft = Offset(leftOf(title.size.width), top), alpha = openA)
    }
    val chip = st.chipRect
    val chipText = st.texts?.chip
    if (chipText != null && chip.width > 0f) {
        val lit = st.hov == CHIP || st.press == CHIP
        val rad = CornerRadius(chip.height / 2f)
        drawRoundRect(if (lit) WH_GOLD.copy(alpha = 0.35f) else WH_CHIP, topLeft = chip.topLeft, size = chip.size, cornerRadius = rad, alpha = openA)
        drawRoundRect(WH_GOLD.copy(alpha = 0.45f), topLeft = chip.topLeft, size = chip.size, cornerRadius = rad, style = p.thin, alpha = openA)
        drawText(
            chipText, color = if (lit) WH_LIGHT else WH_GOLD,
            topLeft = Offset(chip.center.x - chipText.size.width / 2f, chip.center.y - chipText.size.height / 2f), alpha = openA,
        )
    }
}

/** Through the lock like the Seelie home (25 Oct, audit S2): a locked clock or calendar app asks its code first. */
private fun wheelOpenSafely(context: Context, intent: Intent) = com.shamil.launcher.ui.openSafely(context, intent)

/**
 * The current time as state, nudged every minute by Android's time tick while home is on screen (the same as the
 * Seelie home's own clock, which is private to HomeScreen.kt).
 */
@Composable
private fun rememberWheelNow(): LocalDateTime {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { now = LocalDateTime.now() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        var listening = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!listening) {
                    now = LocalDateTime.now()
                    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                    listening = true
                }
                Lifecycle.Event.ON_STOP -> if (listening) {
                    context.unregisterReceiver(receiver)
                    listening = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (listening) context.unregisterReceiver(receiver)
        }
    }
    return now
}
