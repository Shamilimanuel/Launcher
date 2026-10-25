package com.shamil.launcher.ui.toggles

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.LookStore

/*
 * Seelie's volume side bar (25 Oct, his ask: "it doesn't come on the side bar"): a slim tall bar at the screen edge,
 * next to the real volume buttons. The volume service draws it over any app; the home draws it for the Volume
 * toggle when the service is off. His picks from the side-bar mock-up (25 Oct): Gold bar, Constellation, Gold thread,
 * Falling petals, Seelie trail, Lanterns; Glass, Elements and Wallpaper colours stay as extras.
 */

// Water blue at the bottom, wind green, earth gold, fire orange at the top (the Elements look).
private val Water = Color(0xFF5BC0EB)
private val Wind = Color(0xFF74C2A8)
private val Earth = Color(0xFFF0B232)
private val Fire = Color(0xFFF08A5D)
private val BarGold = Color(0xFFF3D79C)

/** The whole side bar: % on top, the tall slider, a round ringer button below. [left] = it sits on the left edge. */
@Composable
fun VolumeSideBar(
    level: Int,
    max: Int,
    look: VolumeLook,
    ringer: String?,
    left: Boolean,
    onChange: (Int) -> Unit,
    onRinger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherLook.current
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier
            .width(72.dp)
            .clip(shape)
            .background(theme.sheetTop.copy(alpha = 0.95f))
            .border(1.dp, theme.accent.copy(alpha = 0.35f), shape)
            .clickable(remember { MutableInteractionSource() }, null) {}   // taps on the frame don't fall through
            .semantics { contentDescription = if (left) "Volume, left edge" else "Volume" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(14.dp))
        Text("${(level * 100f / max.coerceAtLeast(1)).toInt()}%",
            style = TextStyle(fontFamily = theme.font, fontWeight = FontWeight.Bold, color = theme.accent, fontSize = 13.sp))
        Spacer(Modifier.height(10.dp))
        VolumeBar(level, max, look, Modifier.width(56.dp).height(240.dp), onChange)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(theme.card).clickable(onClick = onRinger)
                .semantics { contentDescription = "Sound mode: ${ringer ?: "unknown"}. Tap to change" },
            contentAlignment = Alignment.Center,
        ) { RingerIcon(ringer, theme.accent) }
        Spacer(Modifier.height(13.dp))
    }
}

/** The standing slider in one of the [VolumeLook]s, 0..[max] steps from the bottom up. Drag or tap anywhere on it. */
@Composable
fun VolumeBar(level: Int, max: Int, look: VolumeLook, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    val theme = LocalLauncherLook.current
    val wallpaper = LookStore.get(context).wallpaperLook
    val fraction by animateFloatAsState(level.toFloat() / max.coerceAtLeast(1), spring(stiffness = 700f), label = "volume")
    // Holding the bar: the handle sinks and glows (his press feel).
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    // His new picks take the wallpaper's colour when Seelie follows the wallpaper, gold (petals: pink) otherwise.
    val accent = wallpaper?.accent ?: BarGold
    val steps = max.coerceIn(5, 25)
    // The looks that live a little (twinkling stars, falling petals, floating dust) tick every frame, but only while
    // the bar is on screen: it's only composed for those few seconds.
    val living = look == VolumeLook.STARS || look == VolumeLook.PETALS || look == VolumeLook.TRAIL
    var now by remember { mutableLongStateOf(0L) }
    if (living) LaunchedEffect(Unit) { while (true) withFrameMillis { now = it } }
    val rain = remember { PetalRain() }
    fun step(y: Float, height: Float) = kotlin.math.round((1f - y / height).coerceIn(0f, 1f) * max).toInt()
    Canvas(
        modifier
            .pointerInput(max) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onChange(step(it.y, size.height.toFloat())) },
                )
            }
            .pointerInput(max) {
                detectVerticalDragGestures(
                    onDragStart = { pressed = true },
                    onDragEnd = { pressed = false },
                    onDragCancel = { pressed = false },
                ) { change, _ -> change.consume(); onChange(step(change.position.y, size.height.toFloat())) }
            }
    ) {
        val w = size.width
        val h = size.height
        val f = fraction
        val t = now / 1000f
        when (look) {
            VolumeLook.LANTERN -> {
                // A stack of little lanterns: the lit ones (from the bottom) glow warm, the rest wait dark.
                val gap = h / steps
                for (i in 0 until steps) {
                    val lit = (i + 0.5f) / steps <= f
                    val cy = h - gap * (i + 0.5f)
                    if (lit) drawCircle(Brush.radialGradient(listOf(Color(0x99FFB45E), Color.Transparent), center = Offset(w / 2, cy), radius = gap * 1.1f), radius = gap * 1.1f, center = Offset(w / 2, cy))
                    drawRoundRect(if (lit) Color(0xFFFFD58F) else Color.White.copy(alpha = 0.14f),
                        topLeft = Offset(w * 0.3f, cy - gap * 0.32f), size = Size(w * 0.4f, gap * 0.64f), cornerRadius = CornerRadius(gap * 0.2f))
                }
            }
            VolumeLook.STARS -> drawStars(f, steps, accent, t, press)
            VolumeLook.THREAD -> drawThread(f, steps, accent, press)
            VolumeLook.PETALS -> drawPetals(f, wallpaper?.accent ?: Petal, t, press, rain)
            VolumeLook.TRAIL -> drawTrail(f, accent, t, press)
            else -> {
                val track = w * (if (look == VolumeLook.GLASS) 0.34f else 0.5f)
                val x0 = (w - track) / 2
                val r = CornerRadius(track / 2)
                drawRoundRect(Color.White.copy(alpha = if (look == VolumeLook.GLASS) 0.12f else 0.1f), Offset(x0, 0f), Size(track, h), r)
                val fill = when (look) {
                    VolumeLook.GLASS -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.55f)), startY = 0f, endY = h)
                    VolumeLook.ELEMENT -> Brush.verticalGradient(listOf(Fire, Earth, Wind, Water), startY = 0f, endY = h)
                    VolumeLook.WALLPAPER -> {
                        val a = wallpaper?.accent ?: theme.accent
                        Brush.verticalGradient(listOf(a, lerp(a, Color.Black, 0.25f)), startY = 0f, endY = h)
                    }
                    else -> Brush.verticalGradient(listOf(BarGold, Color(0xFFD3BC8E)), startY = 0f, endY = h)
                }
                if (f > 0f) drawRoundRect(fill, Offset(x0, h * (1f - f)), Size(track, h * f), r)
                // The thumb: a glowing orb (gold, elements, wallpaper) or a clean white one (glass).
                val y = (h * (1f - f)).coerceIn(w / 2, h - w / 2)
                val thumb = when (look) {
                    VolumeLook.GLASS -> Color.White
                    VolumeLook.ELEMENT -> lerp(lerp(Water, Wind, (f * 3).coerceIn(0f, 1f)), lerp(Earth, Fire, ((f - 0.66f) * 3).coerceIn(0f, 1f)), ((f - 0.33f) * 3).coerceIn(0f, 1f))
                    VolumeLook.WALLPAPER -> wallpaper?.accent ?: theme.accent
                    else -> BarGold
                }
                val glow = w * (0.75f + 0.2f * press)
                drawCircle(Brush.radialGradient(listOf(thumb.copy(alpha = 0.45f + 0.2f * press), Color.Transparent), center = Offset(w / 2, y), radius = glow), radius = glow, center = Offset(w / 2, y))
                drawCircle(thumb, radius = w * 0.3f * (1f - 0.18f * press), center = Offset(w / 2, y))
            }
        }
    }
}

private val Petal = Color(0xFFF5A3BD)
private val StarLight = Color(0xFFFFF6DC)

/** Where the stars sit across a 56-wide bar: a zig-zag climbing the edge. */
private val StarX = floatArrayOf(28f, 16f, 38f, 14f, 40f, 20f, 36f, 14f, 40f, 18f, 38f, 16f, 36f, 20f, 28f)

/** A four-pointed star of radius [r] around [c]. */
private fun starPath(c: Offset, r: Float) = Path().apply {
    val k = r / 9f
    moveTo(c.x, c.y - 9 * k)
    quadraticTo(c.x + 1.3f * k, c.y - 1.3f * k, c.x + 9 * k, c.y)
    quadraticTo(c.x + 1.3f * k, c.y + 1.3f * k, c.x, c.y + 9 * k)
    quadraticTo(c.x - 1.3f * k, c.y + 1.3f * k, c.x - 9 * k, c.y)
    quadraticTo(c.x - 1.3f * k, c.y - 1.3f * k, c.x, c.y - 9 * k)
    close()
}

/** Constellation: a star per step climbing the edge, joined by threads of light; faint stars twinkle behind. */
private fun DrawScope.drawStars(f: Float, steps: Int, accent: Color, t: Float, press: Float) {
    val w = size.width
    val h = size.height
    val k = w / 56f
    for (i in 0 until 18) {
        val x = ((i * 37) % 53 + 2) * k
        val y = ((i * 71) % 97) / 97f * h
        val a = 0.08f + 0.42f * (0.5f + 0.5f * kotlin.math.sin(t * (1.1f + (i % 4) * 0.3f) + i))
        drawCircle(StarLight.copy(alpha = a), radius = (0.5f + (i % 3) * 0.3f) * k, center = Offset(x, y))
    }
    val pts = List(steps) { j -> Offset(StarX[j * StarX.size / steps] * k, h - (j + 0.5f) * (h / steps)) }
    val litCount = (0 until steps).count { (it + 0.5f) / steps <= f }
    val dash = PathEffect.dashPathEffect(floatArrayOf(2 * k, 5 * k))
    for (j in 1 until steps) drawLine(accent.copy(alpha = 0.3f), pts[j - 1], pts[j], strokeWidth = k, pathEffect = dash)
    for (j in 1 until litCount) {
        drawLine(accent.copy(alpha = 0.25f), pts[j - 1], pts[j], strokeWidth = 5 * k, cap = StrokeCap.Round)
        drawLine(accent, pts[j - 1], pts[j], strokeWidth = 1.5f * k, cap = StrokeCap.Round)
    }
    pts.forEachIndexed { j, p ->
        val lit = j < litCount
        val current = j == litCount - 1
        val r = 9f * k * when {
            current -> 1.42f - 0.27f * press
            lit -> 1f
            else -> 0.55f
        }
        if (lit) drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (current) 0.6f else 0.35f), Color.Transparent), center = p, radius = r * 1.8f), radius = r * 1.8f, center = p)
        val path = starPath(p, r)
        drawPath(path, if (current) StarLight else if (lit) accent else Color(0xE612172A))
        drawPath(path, if (lit) StarLight else accent.copy(alpha = 0.6f), style = Stroke(width = k))
    }
}

/** Gold thread: a hairline up the edge with tick marks per step and a glowing bead that swells when held. */
private fun DrawScope.drawThread(f: Float, steps: Int, accent: Color, press: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val line = 2.dp.toPx()
    val top = h * (1f - f)
    drawLine(accent.copy(alpha = 0.26f), Offset(cx, 0f), Offset(cx, h), strokeWidth = line, cap = StrokeCap.Round)
    if (f > 0f) {
        drawLine(accent.copy(alpha = 0.25f), Offset(cx, h), Offset(cx, top), strokeWidth = line * 3, cap = StrokeCap.Round)
        drawLine(Brush.verticalGradient(listOf(accent, lerp(accent, Color.Black, 0.25f)), startY = top, endY = h), Offset(cx, h), Offset(cx, top), strokeWidth = line, cap = StrokeCap.Round)
    }
    for (i in 0..steps) {
        val y = (h - i.toFloat() / steps * h).coerceIn(line, h - line)
        val on = i.toFloat() / steps <= f + 0.001f
        val half = (if (on) 3.dp else 6.dp).toPx()
        drawLine(if (on) accent else accent.copy(alpha = 0.22f), Offset(cx - half, y), Offset(cx + half, y), strokeWidth = line, cap = StrokeCap.Round)
    }
    val y = top.coerceIn(7.dp.toPx(), h - 7.dp.toPx())
    val bead = 7.dp.toPx() * (1f + 0.7f * press)
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), center = Offset(cx, y), radius = bead * 2.4f), radius = bead * 2.4f, center = Offset(cx, y))
    drawCircle(StarLight, radius = bead, center = Offset(cx, y))
}

/** The petals falling inside the Falling petals bar. Plain fields: they change every frame without recomposing. */
private class PetalRain {
    class P(var x: Float, var y: Float, val r: Float, val vy: Float, var ph: Float, val sway: Float, val spin: Float, var rot: Float, var a: Float = 1f)
    val list = ArrayList<P>()
    var last = 0f
    var due = 0f
}

/** Falling petals: the filled part is a soft pillar that rains petals (louder = more); a big petal is the handle. */
private fun DrawScope.drawPetals(f: Float, colour: Color, t: Float, press: Float, rain: PetalRain) {
    val w = size.width
    val h = size.height
    val k = w / 56f
    val inset = 8 * k
    val r = CornerRadius(24 * k)
    drawRoundRect(Color.White.copy(alpha = 0.06f), Offset(inset, 0f), Size(w - inset * 2, h), r)
    val top = h * (1f - f)
    if (f > 0f) drawRoundRect(Brush.verticalGradient(listOf(colour.copy(alpha = 0.16f), colour.copy(alpha = 0.78f)), startY = top, endY = h),
        Offset(inset, top), Size(w - inset * 2, h * f), r)
    // Move the petals on by the time since the last frame.
    val dt = if (rain.last == 0f) 0f else (t - rain.last).coerceIn(0f, 0.05f)
    rain.last = t
    if (f > 0.01f) {
        rain.due += dt * (3 + 34 * f)
        while (rain.due > 1f) {
            rain.due -= 1f
            if (rain.list.size < 60) rain.list += PetalRain.P(
                x = inset + 5 * k + Math.random().toFloat() * (w - inset * 2 - 10 * k), y = top + Math.random().toFloat() * 8 * k,
                r = (3.2f + Math.random().toFloat() * 2.8f) * k, vy = (16 + Math.random().toFloat() * 14) * k,
                ph = Math.random().toFloat() * 6.28f, sway = 0.6f + Math.random().toFloat() * 0.8f,
                spin = 1.5f + Math.random().toFloat() * 2f, rot = Math.random().toFloat() * 6.28f)
        }
    }
    val land = h - 16 * k
    rain.list.forEach { p ->
        p.y += p.vy * dt
        p.x += kotlin.math.sin(p.ph) * p.sway * dt * 12 * k
        p.ph += p.spin * dt
        p.rot += dt * 0.8f
        if (p.y > land) p.a -= dt * 2.2f
        if (p.y < top - 4 * k) p.a -= dt * 3f
    }
    rain.list.removeAll { it.a <= 0f }
    rain.list.forEach { p ->
        withTransform({
            translate(p.x, p.y)
            rotate(p.rot * 57.3f, Offset.Zero)
            scale(kotlin.math.abs(kotlin.math.cos(p.ph * 2.3f)) * 0.85f + 0.15f, 1f, Offset.Zero)
        }) { drawPath(petalPath(p.r), colour.copy(alpha = p.a.coerceIn(0f, 1f) * 0.9f)) }
    }
    // The handle: a big petal, tilted, glowing.
    val y = top.coerceIn(14 * k, h - 14 * k)
    val leaf = 14 * k * (1f - 0.18f * press)
    drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.5f + 0.2f * press), Color.Transparent), center = Offset(w / 2, y), radius = leaf * 2f), radius = leaf * 2f, center = Offset(w / 2, y))
    withTransform({ translate(w / 2, y); rotate(-25f, Offset.Zero) }) {
        drawPath(petalPath(leaf), Brush.radialGradient(listOf(Color.White, colour, lerp(colour, Color.Black, 0.3f)), center = Offset(-leaf * 0.3f, -leaf * 0.4f), radius = leaf * 1.6f))
    }
}

/** One petal pointing up, radius [r], around (0, 0). */
private fun petalPath(r: Float) = Path().apply {
    moveTo(0f, -r)
    cubicTo(r * 0.95f, -r * 0.6f, r * 0.8f, r * 0.55f, 0f, r)
    cubicTo(-r * 0.8f, r * 0.55f, -r * 0.95f, -r * 0.6f, 0f, -r)
    close()
}

/** Seelie trail: the app icon as a bar. A tapered ribbon of light winds up the edge with the orb at its head. */
private fun DrawScope.drawTrail(f: Float, accent: Color, t: Float, press: Float) {
    val w = size.width
    val h = size.height
    val k = w / 56f
    fun centre(p: Float) = Offset(w / 2 + 9 * k * kotlin.math.sin(p * Math.PI.toFloat() * 2 * 1.15f), h - 8 * k - (h - 16 * k) * p)
    fun half(p: Float) = (0.8f + 9.5f * Math.pow(p.toDouble(), 1.1).toFloat()) * k
    val ribbon = Path().apply {
        val n = 64
        for (i in 0..n) { val p = i / n.toFloat(); val c = centre(p); if (i == 0) moveTo(c.x - half(p), c.y) else lineTo(c.x - half(p), c.y) }
        for (i in n downTo 0) { val p = i / n.toFloat(); val c = centre(p); lineTo(c.x + half(p), c.y) }
        close()
    }
    drawPath(ribbon, accent.copy(alpha = 0.14f))
    val head = centre(f)
    clipRect(top = head.y) {
        drawPath(ribbon, Brush.verticalGradient(listOf(StarLight, accent.copy(alpha = 0.8f), accent.copy(alpha = 0.25f)), startY = 8 * k, endY = h - 8 * k))
    }
    // Dust floating up, brighter when louder.
    floatArrayOf(0.12f, 0.7f, 0.4f, 0.85f, 0.25f).forEachIndexed { i, x ->
        val p = ((t + i * 0.7f) / 3.4f) % 1f
        val a = (if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f) * (0.15f + 0.7f * f)
        drawCircle(StarLight.copy(alpha = a), radius = 1.5f * k, center = Offset(w * x, h - p * h * 0.8f))
    }
    val glow = 17 * k
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.7f), Color.Transparent), center = head, radius = glow * 1.4f), radius = glow * 1.4f, center = head)
    val orb = 12 * k * (1f - 0.18f * press)
    drawCircle(Brush.radialGradient(listOf(Color.White, StarLight, accent), center = Offset(head.x - orb * 0.24f, head.y - orb * 0.32f), radius = orb * 1.3f), radius = orb, center = head)
}

/** A small bell (Ring), a buzzing phone (Vibrate) or a crossed-out bell (Silent), drawn in [tint]. */
@Composable
private fun RingerIcon(ringer: String?, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
        if (ringer == "Vibrate") {
            drawRoundRect(tint, Offset(s * 0.34f, s * 0.14f), Size(s * 0.32f, s * 0.72f), CornerRadius(s * 0.08f), style = stroke)
            listOf(0.16f, 0.84f).forEach { x -> drawLine(tint, Offset(s * x, s * 0.34f), Offset(s * x, s * 0.66f), stroke.width, StrokeCap.Round) }
            return@Canvas
        }
        val bell = Path().apply {
            moveTo(s * 0.22f, s * 0.72f)
            lineTo(s * 0.26f, s * 0.46f)
            cubicTo(s * 0.28f, s * 0.2f, s * 0.72f, s * 0.2f, s * 0.74f, s * 0.46f)
            lineTo(s * 0.78f, s * 0.72f)
            close()
        }
        drawPath(bell, tint, style = stroke)
        drawLine(tint, Offset(s * 0.42f, s * 0.86f), Offset(s * 0.58f, s * 0.86f), stroke.width, StrokeCap.Round)
        if (ringer == "Silent") drawLine(tint, Offset(s * 0.14f, s * 0.14f), Offset(s * 0.86f, s * 0.86f), stroke.width, StrokeCap.Round)
    }
}

/** Ring → Vibrate → Silent → Ring. Silent (and leaving it) can need Do Not Disturb access on newer Android. */
fun cycleRinger(context: Context): String? {
    val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    val next = when (ToggleActions.ringer(context)) {
        "Ring" -> AudioManager.RINGER_MODE_VIBRATE
        "Vibrate" -> AudioManager.RINGER_MODE_SILENT
        else -> AudioManager.RINGER_MODE_NORMAL
    }
    val ok = runCatching { audio?.ringerMode = next }.isSuccess
    if (!ok) Toast.makeText(context, "Android needs Do Not Disturb access for that", Toast.LENGTH_SHORT).show()
    return ToggleActions.ringer(context)
}
