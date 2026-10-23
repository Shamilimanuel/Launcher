package com.shamil.launcher.ui.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.home.Gold
import com.shamil.launcher.ui.home.HomeStyle
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.home.homeCard
import com.shamil.launcher.ui.theme.LocalLauncherLook
import java.util.Locale

/*
 * Seelie's music card (locked feature #6, 23 Oct). Niagara's card is a cover, a title and ⏮ ⏯ ⏭ straight on the
 * wallpaper; he said "this we can improve much". Seelie's version:
 *  - the card look of his home style (so it matches the widget card), washed with a soft glow in the cover's colour
 *  - a rounded cover that glows, the title gliding sideways (a "marquee") when it's too long, the app's little icon
 *  - ⏯ in an accent circle that springs between ▶ and ⏸, a thin progress bar with times (tap it to jump)
 *  - "reserved spot": when nothing plays it can keep its place and say so calmly, so the favourites never jump.
 * Cheap on the home: when paused nothing moves; while playing it updates about once a second.
 */

/** The music card for the home. Takes no space when he switched it off in settings. */
@Composable
fun MusicCard(modifier: Modifier = Modifier) = MusicCardBody(modifier, inSettings = false)

/** [inSettings] = the live preview in settings: always shows the card, even when it would hide on the home. */
@Composable
internal fun MusicCardBody(modifier: Modifier, inSettings: Boolean) {
    val context = LocalContext.current
    val prefs = remember { MusicPrefs.get(context) }
    if (!prefs.showCard) return
    val hs = HomeStyleStore.get(context).style
    if (!rememberMusicAccess()) {
        AccessCard(hs, modifier)
        return
    }
    val playing = rememberNowPlaying()
    if (prefs.reserveSpot || inSettings) {
        CardFrame(playing, prefs, hs, modifier)
    } else {
        // Hiding when nothing plays: keep the last song for a moment so the card can fade away with it still on.
        val last = remember { mutableStateOf<NowPlaying?>(null) }
        SideEffect { if (playing != null) last.value = playing }
        AnimatedVisibility(
            visible = playing != null,
            modifier = modifier,
            enter = fadeIn(tween(260)) + expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(260)),
            label = "music card",
        ) {
            CardFrame(playing ?: last.value, prefs, hs, Modifier)
        }
    }
}

/** The card itself: his home-style card, a glow in the cover's colour, and the large or compact layout inside. */
@Composable
private fun CardFrame(np: NowPlaying?, prefs: MusicPrefs, hs: HomeStyle, modifier: Modifier) {
    val look = LocalLauncherLook.current
    val accent = if (hs.cardAccent == "gold") Gold else look.accent
    // The glow glides to the new cover's colour when the song changes (only then: no work while it stays the same).
    val glow by animateColorAsState(np?.artColor ?: accent, tween(700), label = "music glow")
    val strength = if (np?.artColor != null) 1f else 0.45f
    val compact = prefs.size == MusicPrefs.SIZE_COMPACT
    Box(
        modifier
            .fillMaxWidth()
            .homeCard(hs, look)
            .drawBehind {
                // A soft wash of the cover's colour, spreading from the cover across the card.
                val center = Offset((if (compact) 35.dp else 50.dp).toPx(), size.height / 2f)
                val radius = size.width * 0.75f
                drawRect(
                    Brush.radialGradient(
                        0f to glow.copy(alpha = 0.26f * strength),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    )
                )
            }
            .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 10.dp else 14.dp)
    ) {
        if (compact) CompactBody(np, prefs.showProgress, accent, glow, strength)
        else LargeBody(np, prefs.showProgress, accent, glow, strength)
    }
}

/**
 * Large: cover on the left; title (+ app icon), artist and ⏮ ⏯ ⏭ beside it; the progress bar under everything.
 * With nothing playing the same pieces stay (so the height doesn't change) but calmer: a note, and only ▶.
 */
@Composable
private fun LargeBody(np: NowPlaying?, showProgress: Boolean, accent: Color, glow: Color, strength: Float) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val open: () -> Unit = { np?.open(context) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(np, 72.dp, glow, strength, open)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TitleText(np?.title ?: "Nothing playing", np, 16.5.sp, Modifier.weight(1f), open)
                    np?.appIcon?.let { icon ->
                        Spacer(Modifier.width(8.dp))
                        Image(bitmap = icon, contentDescription = np?.appLabel, modifier = Modifier.size(16.dp).clip(CircleShape))
                    }
                }
                val subtitle = when {
                    np == null -> "Tap ▶ to pick up where you left off"
                    np.artist.isNotBlank() -> np.artist
                    else -> np.album
                }
                Text(
                    subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = cardText(13.5.sp, look.textDim),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SideButton(Glyph.PREV, 36.dp, "Previous", visible = np != null, enabled = np?.canSkipPrevious == true) { np?.previous() }
                    PlayButton(np?.isPlaying == true, 42.dp, accent) { if (np == null) resumeLastMedia(context) else np.playPause() }
                    SideButton(Glyph.NEXT, 36.dp, "Next", visible = np != null, enabled = np?.canSkipNext == true) { np?.next() }
                }
            }
        }
        if (showProgress) {
            Spacer(Modifier.height(8.dp))
            ProgressRow(np, accent)
        }
    }
}

/** Compact: one row (small cover, "title · artist", ⏯ ⏭), and a hairline progress bar under it. */
@Composable
private fun CompactBody(np: NowPlaying?, showProgress: Boolean, accent: Color, glow: Color, strength: Float) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val open: () -> Unit = { np?.open(context) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(np, 46.dp, glow, strength, open)
            Spacer(Modifier.width(12.dp))
            val line = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = look.text)) { append(np?.title ?: "Nothing playing") }
                val second = when {
                    np == null -> "tap ▶ to resume"
                    np.artist.isNotBlank() -> np.artist
                    else -> np.album
                }
                if (second.isNotBlank()) withStyle(SpanStyle(color = look.textDim)) { append("  ·  $second") }
            }
            MarqueeLine(np, Modifier.weight(1f), open) {
                Text(line, maxLines = 1, overflow = if (np?.isPlaying == true) TextOverflow.Clip else TextOverflow.Ellipsis, style = cardText(15.sp, look.text))
            }
            Spacer(Modifier.width(6.dp))
            PlayButton(np?.isPlaying == true, 38.dp, accent) { if (np == null) resumeLastMedia(context) else np.playPause() }
            SideButton(Glyph.NEXT, 36.dp, "Next", visible = np != null, enabled = np?.canSkipNext == true) { np?.next() }
        }
        if (showProgress) {
            Spacer(Modifier.height(8.dp))
            val fraction = np.fraction()
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .drawBehind {
                        val y = size.height / 2f
                        drawLine(look.text.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
                        if (fraction > 0f) drawLine(accent, Offset(0f, y), Offset(size.width * fraction, y), size.height, StrokeCap.Round)
                    }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Pieces

/** The cover: rounded, with a soft glow of its own colour around it. A music note when there's no cover. */
@Composable
private fun Art(np: NowPlaying?, side: Dp, glow: Color, strength: Float, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val corner = side * 0.22f
    val shape = RoundedCornerShape(corner)
    Box(
        Modifier
            .size(side)
            .pressFeel(press, glow, corner)
            .drawBehind {
                // Light from the cover, spilling a little past its edges.
                val radius = size.minDimension * 0.78f
                drawCircle(
                    Brush.radialGradient(
                        0f to glow.copy(alpha = 0.5f * strength),
                        0.6f to glow.copy(alpha = 0.16f * strength),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )
            }
            .clip(shape)
            .background(look.text.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(enabled = np != null, interactionSource = press, indication = null, onClickLabel = "Open the music app", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // A gentle fade from the old cover to the new one when the song changes.
        Crossfade(targetState = np?.art, animationSpec = tween(350), label = "cover") { cover ->
            if (cover != null) {
                Image(bitmap = cover, contentDescription = "Album cover", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = look.textDim, modifier = Modifier.size(side * 0.42f))
                }
            }
        }
    }
}

/** The song title: bold, one line; glides sideways (3 rounds, then rests) when too long, only while playing. */
@Composable
private fun TitleText(text: String, np: NowPlaying?, size: TextUnit, modifier: Modifier, onOpen: () -> Unit) {
    val look = LocalLauncherLook.current
    MarqueeLine(np, modifier, onOpen) {
        Text(
            text, maxLines = 1,
            overflow = if (np?.isPlaying == true) TextOverflow.Clip else TextOverflow.Ellipsis,
            style = cardText(size, look.text, FontWeight.Bold),
        )
    }
}

/** A tappable one-line box (opens the music app) that runs a marquee over its text while the song plays. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarqueeLine(np: NowPlaying?, modifier: Modifier, onOpen: () -> Unit, content: @Composable () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .clickable(enabled = np != null, interactionSource = press, indication = null, onClickLabel = "Open the music app", onClick = onOpen)
            // A marquee animates every frame, so it only runs while playing, and stops after 3 rounds.
            .then(if (np?.isPlaying == true) Modifier.basicMarquee(iterations = 3) else Modifier)
    ) { content() }
}

/** ⏯: an accent circle with a soft halo; the ▶ and ⏸ swap with a little spring. */
@Composable
private fun PlayButton(playing: Boolean, diameter: Dp, accent: Color, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    // Dark ink on the accent, like the picked pills in settings.
    val ink = look.sheet.copy(alpha = 1f)
    Box(
        Modifier
            .size(diameter)
            .pressFeel(press, Color.White, diameter / 2)
            .drawBehind {
                val radius = size.minDimension * 0.5f + 5.dp.toPx()
                drawCircle(
                    Brush.radialGradient(0.7f to accent.copy(alpha = 0.32f), 1f to Color.Transparent, center = center, radius = radius),
                    radius = radius,
                )
            }
            .clip(CircleShape)
            .background(accent)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .semantics { contentDescription = if (playing) "Pause" else "Play"; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.45f, stiffness = 600f), initialScale = 0.5f) + fadeIn(tween(120))) togetherWith
                    (scaleOut(tween(110), targetScale = 0.5f) + fadeOut(tween(110)))
            },
            label = "play pause",
        ) { isPlaying ->
            GlyphIcon(if (isPlaying) Glyph.PAUSE else Glyph.PLAY, ink, diameter * 0.5f)
        }
    }
}

/** ⏮ or ⏭: a plain glyph that sinks and glows when pressed. Invisible (but keeping its space) when nothing plays. */
@Composable
private fun SideButton(glyph: Glyph, diameter: Dp, label: String, visible: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(diameter)
            .graphicsLayer { alpha = if (!visible) 0f else if (enabled) 1f else 0.35f }
            .pressFeel(press, look.text, diameter / 2)
            .clip(CircleShape)
            .clickable(enabled = visible && enabled, interactionSource = press, indication = null, onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, look.text, diameter * 0.62f)
    }
}

/** Elapsed time, the bar (tap to jump there, when the app allows it), total time. Same height when nothing plays. */
@Composable
private fun ProgressRow(np: NowPlaying?, accent: Color) {
    val look = LocalLauncherLook.current
    // The tap handler is set up once; this keeps it pointing at the latest song.
    val latest by rememberUpdatedState(np)
    val duration = np?.durationMs ?: 0L
    val fraction = np.fraction()
    val canSeek = np?.canSeek == true && duration > 0L
    Row(verticalAlignment = Alignment.CenterVertically) {
        TimeText(if (duration > 0L) clockText(np?.positionMs ?: 0L) else "", TextAlign.Start)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(18.dp)
                .then(
                    if (canSeek) Modifier.pointerInput(duration) {
                        detectTapGestures { tap -> latest?.seekTo((tap.x / size.width * duration).toLong()) }
                    } else Modifier
                )
                .drawBehind {
                    val y = size.height / 2f
                    val thick = 3.dp.toPx()
                    drawLine(look.text.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), thick, StrokeCap.Round)
                    if (fraction > 0f) {
                        drawLine(accent, Offset(0f, y), Offset(size.width * fraction, y), thick, StrokeCap.Round)
                        // A small bead at the playing point.
                        drawCircle(accent, radius = thick * 1.25f, center = Offset(size.width * fraction, y))
                    }
                }
        )
        TimeText(if (duration > 0L) clockText(duration) else "", TextAlign.End)
    }
}

@Composable
private fun TimeText(text: String, align: TextAlign) {
    val look = LocalLauncherLook.current
    Text(text, modifier = Modifier.widthIn(min = 34.dp), maxLines = 1, style = cardText(11.sp, look.textDim).copy(textAlign = align))
}

/** The small card shown while Notification access is off: one tap to Android's switch for it. */
@Composable
private fun AccessCard(hs: HomeStyle, modifier: Modifier) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val accent = if (hs.cardAccent == "gold") Gold else look.accent
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pressFeel(press, accent, hs.cardRadius.dp)
            .homeCard(hs, look)
            .clickable(interactionSource = press, indication = null) { openNotificationAccess(context) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(accent.copy(alpha = 0.18f), CircleShape)
                .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Show your music here", style = cardText(15.sp, look.text, FontWeight.SemiBold))
            Text("Allow notification access", style = cardText(13.sp, accent))
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = look.textDim)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Drawn icons (Seelie has no ▶ ⏸ ⏮ ⏭ icon files, so they're drawn: sharp at any size, and they take the theme colour)

private enum class Glyph { PLAY, PAUSE, NEXT, PREV }

@Composable
private fun GlyphIcon(glyph: Glyph, color: Color, side: Dp) {
    Canvas(Modifier.size(side)) { drawGlyph(glyph, color) }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val w = size.width
    val h = size.height
    // Filling AND outlining with round joins softens the triangle's sharp corners.
    val soft = Stroke(width = w * 0.09f, join = StrokeJoin.Round)
    fun triangle(left: Float, right: Float, top: Float, bottom: Float) {
        val path = Path().apply {
            moveTo(left, top)
            lineTo(right, (top + bottom) / 2f)
            lineTo(left, bottom)
            close()
        }
        drawPath(path, color)
        drawPath(path, color, style = soft)
    }
    when (glyph) {
        Glyph.PLAY -> triangle(w * 0.32f, w * 0.80f, h * 0.20f, h * 0.80f)
        Glyph.PAUSE -> {
            val barWidth = w * 0.18f
            val top = h * 0.22f
            val barSize = Size(barWidth, h * 0.56f)
            val round = CornerRadius(barWidth * 0.35f)
            drawRoundRect(color, Offset(w * 0.26f, top), barSize, round)
            drawRoundRect(color, Offset(w * 0.56f, top), barSize, round)
        }
        Glyph.NEXT -> {
            triangle(w * 0.20f, w * 0.64f, h * 0.24f, h * 0.76f)
            drawRoundRect(color, Offset(w * 0.68f, h * 0.24f), Size(w * 0.11f, h * 0.52f), CornerRadius(w * 0.04f))
        }
        // ⏮ is ⏭ in a mirror.
        Glyph.PREV -> scale(-1f, 1f) { drawGlyph(Glyph.NEXT, color) }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Small helpers

/** How far through the song (0–1); 0 when nothing plays or the length is unknown. */
private fun NowPlaying?.fraction(): Float {
    val duration = this?.durationMs ?: 0L
    if (this == null || duration <= 0L) return 0f
    return (positionMs.toFloat() / duration).coerceIn(0f, 1f)
}

/** 83 000 ms → "1:23"; over an hour → "1:02:03". */
private fun clockText(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

/** Card text in the theme's font, with the soft shadow so it stays readable on any wallpaper. */
@Composable
private fun cardText(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal): TextStyle {
    val look = LocalLauncherLook.current
    return TextStyle(fontFamily = look.font, color = color, fontSize = size, fontWeight = weight, shadow = look.shadow)
}
