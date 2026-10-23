package com.shamil.launcher.ui.media

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shamil.launcher.R
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.home.Gold
import com.shamil.launcher.ui.home.HomeStyle
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.home.homeCard
import com.shamil.launcher.ui.theme.LauncherLook
import com.shamil.launcher.ui.theme.LocalLauncherLook
import java.util.Locale
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/*
 * Seelie's music card (locked feature #6, 23 Oct). Niagara's card is a cover, a title and ⏮ ⏯ ⏭ straight on the
 * wallpaper; he said "this we can improve much", then "allow me to customize it too". Seelie's version:
 *  - the card of his home style (or its own: none / glass / solid / outline, see-through, corners)
 *  - a cover (small / medium / large; rounded, circle, or a vinyl record that spins while playing) with a glow
 *  - the title gliding sideways (a "marquee") when too long, the artist, the app's icon and name
 *  - ⏯ filled / outlined / plain, springing between ▶ and ⏸; ⏮ ⏭ optional; colours from the theme, gold or the cover
 *  - a thin or thick progress bar with times (tap it to jump)
 *  - when nothing plays: keep its spot calmly, shrink to one small line, or hide.
 * Cheap on the home: when paused nothing moves; while playing it updates about once a second
 * (the vinyl spin runs in the draw phase only, and only while playing).
 */

/** The music card for the home. Takes no space when it's switched off in settings (the default for now). */
@Composable
fun MusicCard(modifier: Modifier = Modifier) = MusicCardBody(modifier, inSettings = false)

/** [inSettings] = the live preview in settings: always shows the card, even when it's off or would hide on the home. */
@Composable
internal fun MusicCardBody(modifier: Modifier, inSettings: Boolean) {
    val context = LocalContext.current
    val ms = MusicPrefs.get(context).style
    if (!ms.show && !inSettings) return
    val hs = HomeStyleStore.get(context).style
    if (!rememberMusicAccess()) {
        AccessCard(ms, hs, modifier)
        return
    }
    val playing = rememberNowPlaying()
    if (ms.idle != "hide" || inSettings) {
        CardFrame(playing, ms, hs, modifier)
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
            CardFrame(playing ?: last.value, ms, hs, Modifier)
        }
    }
}

/** What every piece of the card needs to know, worked out once per card. */
private class CardLook(
    val ms: MusicStyle,
    /** ⏯ and the bar. */
    val accent: Color,
    /** The cover's light. */
    val glow: Color,
    /** 0 = no glow, 1 = full glow (a cover's colour), in between = the accent's softer glow. */
    val strength: Float,
    val artSide: Dp,
    val artRight: Boolean,
)

/** The card itself: its card look, a glow in the cover's colour, and the large / compact / one-line layout inside. */
@Composable
private fun CardFrame(np: NowPlaying?, ms: MusicStyle, hs: HomeStyle, modifier: Modifier) {
    val look = LocalLauncherLook.current
    val compact = ms.size == MusicPrefs.SIZE_COMPACT
    // Colours glide to the new cover's colour when the song changes (only then: no work while it stays the same).
    val accentTarget = accentFor(ms, hs, look, np)
    val accent by animateColorAsState(accentTarget, tween(600), label = "music accent")
    val glow by animateColorAsState(np?.artColor ?: accentTarget, tween(700), label = "music glow")
    val strength = when {
        !ms.artGlow -> 0f
        np?.artColor != null -> 1f
        else -> 0.45f
    }
    val artSide = artSize(ms.artSize, compact)
    val artRight = ms.artSide == "right"
    val parts = CardLook(ms, accent, glow, strength, artSide, artRight)
    val idleLine = np == null && ms.idle == "line"
    val pad = if (compact) 12.dp else 14.dp
    val onArt = ms.artBackground
    // The wash is skipped with no card at all: without a card edge it would look like a smudge.
    val wash = strength > 0f && (onArt || !(!ms.followHome && ms.cardStyle == "none")) && !idleLine
    // On the album art, text and icons turn white (with a darker shadow) so they read on the dimmed cover.
    val cardLook = remember(look, onArt) {
        if (onArt) look.copy(text = Color.White, textDim = Color.White.copy(alpha = 0.78f), textShadow = Color(0x99000000)) else look
    }
    Box(
        modifier
            .fillMaxWidth()
            // Grows and shrinks softly when he changes a setting or it switches to the small line (only during the change).
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 380f))
            .musicCard(ms, hs, look)
    ) {
        // "Album art" card: the cover fills the card behind everything (sized by the content box below).
        if (onArt) ArtBackdrop(np, Modifier.matchParentSize())
        Box(
            Modifier
                .then(
                    if (wash) Modifier.drawBehind {
                        // A soft wash of the cover's colour, spreading from the cover across the card.
                        val fromEdge = (pad + artSide / 2).toPx()
                        val center = Offset(if (artRight) size.width - fromEdge else fromEdge, size.height / 2f)
                        val radius = size.width * 0.75f
                        drawRect(
                            Brush.radialGradient(
                                0f to glow.copy(alpha = (if (onArt) 0.2f else 0.26f) * strength),
                                1f to Color.Transparent,
                                center = center,
                                radius = radius,
                            )
                        )
                    } else Modifier
                )
                .padding(horizontal = pad, vertical = if (idleLine) 8.dp else if (compact) 10.dp else 14.dp)
        ) {
            CompositionLocalProvider(LocalLauncherLook provides cardLook) {
                when {
                    idleLine -> IdleLine(parts)
                    compact -> CompactBody(np, parts)
                    else -> LargeBody(np, parts)
                }
            }
        }
    }
}

/**
 * The cover filling the card: zoomed in a little (so the blurred edges stay outside the card), blurred and dimmed.
 * Blur needs Android 12+; older phones get a darker dim instead. It only redraws when the cover changes.
 */
@Composable
private fun ArtBackdrop(np: NowPlaying?, modifier: Modifier) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(modifier) {
        Crossfade(targetState = np?.art, modifier = Modifier.matchParentSize(), animationSpec = tween(500), label = "backdrop") { cover ->
            if (cover != null) {
                Image(
                    bitmap = cover, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }
                        .blur(if (canBlur) 22.dp else 0.dp),
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = if (canBlur) 0.30f else 0.48f),
                            Color.Black.copy(alpha = if (canBlur) 0.52f else 0.68f),
                        )
                    )
                )
        )
    }
}

/**
 * Large: cover on one side; title, artist, then ⏮ ⏯ ⏭ with the app's icon and name beside it; the bar under everything.
 * With nothing playing the same pieces stay (so the height doesn't change) but calmer: a note, and only ▶.
 */
@Composable
private fun LargeBody(np: NowPlaying?, p: CardLook) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val ms = p.ms
    // Extra buttons on = the buttons move to their own full-width row (there's no room for five beside the cover).
    val wide = ms.extraButtons
    val open: () -> Unit = { np?.open(context) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!p.artRight) {
                Art(np, p, open)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                TitleText(np?.title ?: "Nothing playing", np, ms.titleSize.sp, Modifier.fillMaxWidth(), open)
                if (ms.showArtist) {
                    val subtitle = when {
                        np == null -> "Tap ▶ to pick up where you left off"
                        np.artist.isNotBlank() -> np.artist
                        else -> np.album
                    }
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = cardText((ms.titleSize - 3f).sp, look.textDim))
                }
                if (wide) {
                    // The buttons get their own row below, so the app's icon and name sit under the artist.
                    if (ms.showApp) {
                        Spacer(Modifier.height(4.dp))
                        AppTag(np, Modifier.fillMaxWidth(), alignEnd = false)
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (ms.skipButtons) {
                                SideButton(Glyph.PREV, 36.dp, "Previous", visible = np != null, enabled = np?.canSkipPrevious == true) { np?.previous() }
                            }
                            PlayButton(np?.isPlaying == true, 42.dp, p) { if (np == null) resumeLastMedia(context) else np.playPause() }
                            if (ms.skipButtons) {
                                SideButton(Glyph.NEXT, 36.dp, "Next", visible = np != null, enabled = np?.canSkipNext == true) { np?.next() }
                            }
                        }
                        if (ms.showApp) {
                            Spacer(Modifier.width(10.dp))
                            AppTag(np, Modifier.weight(1f), alignEnd = true)
                        }
                    }
                }
            }
            if (p.artRight) {
                Spacer(Modifier.width(14.dp))
                Art(np, p, open)
            }
        }
        if (wide) {
            Spacer(Modifier.height(10.dp))
            WideControls(np, p)
        }
        if (ms.bar != "none") {
            Spacer(Modifier.height(8.dp))
            ProgressRow(np, p.accent, ms.bar)
        }
    }
}

/**
 * With extra buttons on: one full-width row, like Samsung's player: [extra] ⏮ ⏯ ⏭ [extra].
 * Empty extra slots keep their space, so the row stays balanced and the card keeps its height.
 */
@Composable
private fun WideControls(np: NowPlaying?, p: CardLook) {
    val context = LocalContext.current
    val extras = np?.extras.orEmpty().take(2)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        ExtraSlot(extras.getOrNull(0), 36.dp, p)
        if (p.ms.skipButtons) {
            SideButton(Glyph.PREV, 40.dp, "Previous", visible = np != null, enabled = np?.canSkipPrevious == true) { np?.previous() }
        }
        PlayButton(np?.isPlaying == true, 46.dp, p) { if (np == null) resumeLastMedia(context) else np.playPause() }
        if (p.ms.skipButtons) {
            SideButton(Glyph.NEXT, 40.dp, "Next", visible = np != null, enabled = np?.canSkipNext == true) { np?.next() }
        }
        ExtraSlot(extras.getOrNull(1), 36.dp, p)
    }
}

/** An extra button, or an empty space of the same size. */
@Composable
private fun ExtraSlot(extra: MediaExtra?, diameter: Dp, p: CardLook) {
    if (extra != null) ExtraButton(extra, diameter, p) else Spacer(Modifier.size(diameter))
}

/**
 * One of the app's extra buttons with the app's own icon (tinted to match the card), or Seelie's drawn shuffle arrows,
 * or a small star when the app's icon can't be loaded. Lit in the accent while it's on (shuffle).
 */
@Composable
private fun ExtraButton(extra: MediaExtra, diameter: Dp, p: CardLook) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val tint = if (extra.isOn) p.accent else look.text
    Box(
        Modifier
            .size(diameter)
            .pressFeel(press, look.text, diameter / 2)
            .clip(CircleShape)
            .background(if (extra.isOn) p.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(interactionSource = press, indication = null) { extra.perform() }
            .semantics { contentDescription = extra.label.ifBlank { "Extra button" }; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        val icon = extra.icon
        when {
            icon != null -> Icon(bitmap = icon, contentDescription = null, tint = tint, modifier = Modifier.size(diameter * 0.56f))
            extra.isShuffle -> GlyphIcon(Glyph.SHUFFLE, tint, diameter * 0.62f)
            else -> Icon(painterResource(R.drawable.ic_star), contentDescription = null, tint = tint, modifier = Modifier.size(diameter * 0.5f))
        }
    }
}

/** Compact: one row (cover, app icon, "title · artist", ⏯ ⏭), and a slim bar under it. */
@Composable
private fun CompactBody(np: NowPlaying?, p: CardLook) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val ms = p.ms
    val open: () -> Unit = { np?.open(context) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!p.artRight) {
                Art(np, p, open)
                Spacer(Modifier.width(12.dp))
            }
            if (ms.showApp) {
                np?.appIcon?.let { icon ->
                    Image(bitmap = icon, contentDescription = np?.appLabel, modifier = Modifier.size(14.dp).clip(CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
            }
            val line = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = look.text)) { append(np?.title ?: "Nothing playing") }
                if (ms.showArtist) {
                    val second = when {
                        np == null -> "tap ▶ to resume"
                        np.artist.isNotBlank() -> np.artist
                        else -> np.album
                    }
                    if (second.isNotBlank()) withStyle(SpanStyle(color = look.textDim)) { append("  ·  $second") }
                }
            }
            MarqueeLine(np, Modifier.weight(1f), open) {
                Text(
                    line, maxLines = 1,
                    overflow = if (np?.isPlaying == true) TextOverflow.Clip else TextOverflow.Ellipsis,
                    style = cardText((ms.titleSize - 1.5f).sp, look.text),
                )
            }
            Spacer(Modifier.width(6.dp))
            // Compact has room for one extra (the first: usually shuffle or ♥).
            if (ms.extraButtons) np?.extras?.firstOrNull()?.let { ExtraButton(it, 34.dp, p) }
            PlayButton(np?.isPlaying == true, 38.dp, p) { if (np == null) resumeLastMedia(context) else np.playPause() }
            if (ms.skipButtons) {
                SideButton(Glyph.NEXT, 36.dp, "Next", visible = np != null, enabled = np?.canSkipNext == true) { np?.next() }
            }
            if (p.artRight) {
                Spacer(Modifier.width(12.dp))
                Art(np, p, open)
            }
        }
        if (ms.bar != "none") {
            Spacer(Modifier.height(6.dp))
            val fraction = np.fraction()
            val accent = p.accent
            val track = look.text.copy(alpha = 0.18f)
            val wave = rememberWave(ms.bar == "wavy" && np?.isPlaying == true)
            val wavePath = remember { Path() }
            val bar = ms.bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (bar == "wavy") 10.dp else 6.dp)
                    .drawBehind { drawBar(bar, fraction, accent, track, wave, wavePath, thumb = false, slim = true) }
            )
        }
    }
}

/** "Nothing playing ▶" in one small line, for "When nothing plays → Small line". */
@Composable
private fun IdleLine(p: CardLook) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = look.textDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Nothing playing", modifier = Modifier.weight(1f), maxLines = 1, style = cardText(14.sp, look.textDim))
        PlayButton(false, 32.dp, p) { resumeLastMedia(context) }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Pieces

/**
 * The cover: rounded square, circle or vinyl, with a soft glow of its own colour around it.
 * A music note when there's no cover.
 */
@Composable
private fun Art(np: NowPlaying?, p: CardLook, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val side = p.artSide
    val round = p.ms.artShape != "rounded"
    val vinyl = p.ms.artShape == "vinyl"
    val corner = if (round) side / 2 else side * 0.22f
    val shape = if (round) CircleShape else RoundedCornerShape(corner)
    val spin = if (vinyl) rememberSpin(np?.isPlaying == true) else null
    val glow = p.glow
    val strength = p.strength
    Box(
        Modifier
            .size(side)
            .pressFeel(press, glow, corner)
            .then(
                if (strength > 0f) Modifier.drawBehind {
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
                } else Modifier
            )
            .clip(shape)
            .background(if (vinyl) Color(0xFF15141B) else look.text.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(enabled = np != null, interactionSource = press, indication = null, onClickLabel = "Open the music app", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // The spin is read here, in the draw phase, so turning the record never recomposes anything.
                .graphicsLayer { if (spin != null) rotationZ = spin.value }
                .then(if (vinyl) Modifier.drawWithContent { drawContent(); drawRecord() } else Modifier),
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
}

/** The vinyl touches over the cover: a few faint grooves, a sheen, and the small hole in the middle. */
private fun DrawScope.drawRecord() {
    val r = size.minDimension / 2f
    val hairline = 1.dp.toPx()
    listOf(0.48f, 0.64f, 0.8f, 0.93f).forEach { k ->
        drawCircle(Color.Black.copy(alpha = 0.22f), radius = r * k, style = Stroke(hairline))
    }
    // A soft light streak, so the turning is visible even on a plain cover.
    drawCircle(
        Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.13f), Color.Transparent),
            start = Offset(0f, 0f), end = Offset(size.width, size.height),
        ),
        radius = r,
    )
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = r * 0.2f)
    drawCircle(Color(0xFF0B0A10), radius = r * 0.07f)
    drawCircle(Color.White.copy(alpha = 0.25f), radius = r * 0.2f, style = Stroke(hairline))
}

/**
 * The record's angle: turns once every 9 seconds while playing, stops where it is when paused.
 * Nothing runs while paused, and nothing at all when Android's animations are switched off.
 */
@Composable
private fun rememberSpin(spinning: Boolean): Animatable<Float, AnimationVector1D> {
    val context = LocalContext.current
    val angle = remember { Animatable(0f) }
    val allowed = remember { animationsOn(context) }
    LaunchedEffect(spinning, allowed) {
        if (!spinning || !allowed) return@LaunchedEffect
        while (true) {
            angle.animateTo(angle.value + 360f, tween(9_000, easing = LinearEasing))
            angle.snapTo(angle.value % 360f)
        }
    }
    return angle
}

/** The music app's little icon and name, at the end of the button row. */
@Composable
private fun AppTag(np: NowPlaying?, modifier: Modifier, alignEnd: Boolean) {
    val look = LocalLauncherLook.current
    Row(modifier, horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        np?.appIcon?.let { icon ->
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(14.dp).clip(CircleShape))
            Spacer(Modifier.width(5.dp))
        }
        // Always a line of text (empty when nothing plays), so the card keeps the same height.
        Text(np?.appLabel.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = cardText(11.5.sp, look.textDim))
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

/**
 * ⏯ in his chosen style: "filled" (accent circle with a soft halo), "outline" (accent ring), or "plain" (just the icon).
 * The ▶ and ⏸ swap with a little spring.
 */
@Composable
private fun PlayButton(playing: Boolean, diameter: Dp, p: CardLook, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    val accent = p.accent
    val style = p.ms.buttons
    // Dark ink on the filled accent (like the picked pills in settings); the accent itself otherwise.
    val ink = if (style == "filled") look.sheet.copy(alpha = 1f) else accent
    val shell = when (style) {
        "filled" -> Modifier
            .drawBehind {
                val radius = size.minDimension * 0.5f + 5.dp.toPx()
                drawCircle(
                    Brush.radialGradient(0.7f to accent.copy(alpha = 0.32f), 1f to Color.Transparent, center = center, radius = radius),
                    radius = radius,
                )
            }
            .clip(CircleShape)
            .background(accent)
        "outline" -> Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f))
            .border(1.5.dp, accent, CircleShape)
        else -> Modifier.clip(CircleShape)
    }
    Box(
        Modifier
            .size(diameter)
            .pressFeel(press, if (style == "filled") Color.White else accent, diameter / 2)
            .then(shell)
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
            GlyphIcon(if (isPlaying) Glyph.PAUSE else Glyph.PLAY, ink, diameter * if (style == "plain") 0.7f else 0.5f)
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

/**
 * Elapsed time, the bar, total time. Same height when nothing plays.
 * Tap the bar to jump there, or drag along it (the times follow your finger) when the app allows it.
 */
@Composable
private fun ProgressRow(np: NowPlaying?, accent: Color, bar: String) {
    val look = LocalLauncherLook.current
    // The touch handlers are set up once; this keeps them pointing at the latest song.
    val latest by rememberUpdatedState(np)
    val duration = np?.durationMs ?: 0L
    // While dragging, the bar shows where his finger is (0-1) instead of the song's position.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: np.fraction()
    val shownPosition = dragging?.let { (it * duration).toLong() } ?: (np?.positionMs ?: 0L)
    val canSeek = np?.canSeek == true && duration > 0L
    val track = look.text.copy(alpha = 0.18f)
    val wave = rememberWave(bar == "wavy" && np?.isPlaying == true && dragging == null)
    val wavePath = remember { Path() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TimeText(if (duration > 0L) clockText(shownPosition) else "", TextAlign.Start)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(18.dp)
                .then(
                    if (canSeek) Modifier
                        .pointerInput(duration) {
                            detectTapGestures { tap -> latest?.seekTo((tap.x / size.width * duration).toLong()) }
                        }
                        .pointerInput(duration) {
                            detectHorizontalDragGestures(
                                onDragStart = { start -> dragging = (start.x / size.width).coerceIn(0f, 1f) },
                                onDragEnd = {
                                    dragging?.let { f -> latest?.seekTo((f * duration).toLong()) }
                                    dragging = null
                                },
                                onDragCancel = { dragging = null },
                            ) { change, _ ->
                                change.consume()
                                dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                    else Modifier
                )
                .drawBehind { drawBar(bar, fraction, accent, track, wave, wavePath, thumb = true, slim = false) }
        )
        TimeText(if (duration > 0L) clockText(duration) else "", TextAlign.End)
    }
}

/** The wave's state, read only while drawing: how tall it is now (0 = flat) and how far it has travelled. */
private class Wave(val height: State<Float>, val phase: FloatState)

/** Wave size (Samsung-like): about 3dp tall, one ripple every 24dp, travelling one ripple every ~2 seconds. */
private val WAVE_AMPLITUDE = 3.dp
private val WAVE_LENGTH = 24.dp
private const val WAVE_SPEED = 3.2f   // radians per second

/**
 * The wavy bar's motion. It moves only while [moving] (playing), only while the screen is visible, and not at all
 * when Android's animations are off. When the song pauses it smooths down flat, then nothing runs.
 */
@Composable
private fun rememberWave(moving: Boolean): Wave {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val allowed = remember { animationsOn(context) }
    val height = animateFloatAsState(if (moving) 1f else 0f, tween(450), label = "wave height")
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(moving, allowed, owner) {
        if (!moving || !allowed) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        val seconds = (now - last) / 1_000_000_000f
                        phase.floatValue = (phase.floatValue + seconds * WAVE_SPEED) % (2f * PI.toFloat())
                    }
                    last = now
                }
            }
        }
    }
    return Wave(height, phase)
}

/**
 * Draws a bar in [style]: "thin" (a line with a small bead), "thick" (a rounded bar) or "wavy" (the played part a
 * soft travelling wave, the rest a thin straight line, a round thumb at the position). [slim] = the compact card's
 * smaller version; [thumb] = draw the bead / thumb.
 */
private fun DrawScope.drawBar(
    style: String, fraction: Float, accent: Color, track: Color, wave: Wave, path: Path, thumb: Boolean, slim: Boolean,
) {
    val y = size.height / 2f
    val x = size.width * fraction
    when (style) {
        "thick" -> {
            val line = (if (slim) 4.dp else 6.dp).toPx()
            drawLine(track, Offset(0f, y), Offset(size.width, y), line, StrokeCap.Round)
            if (fraction > 0f) drawLine(accent, Offset(0f, y), Offset(x, y), line, StrokeCap.Round)
        }
        "wavy" -> {
            val rest = 2.dp.toPx()
            val played = (if (slim) 2.5.dp else 3.dp).toPx()
            if (x < size.width) drawLine(track, Offset(x, y), Offset(size.width, y), rest, StrokeCap.Round)
            val amplitude = (if (slim) 2.dp else WAVE_AMPLITUDE).toPx() * wave.height.value
            if (x > 0f) {
                if (amplitude < 0.3f) {
                    drawLine(accent, Offset(0f, y), Offset(x, y), played, StrokeCap.Round)
                } else {
                    // A sine wave from the start to the position, calmer at both ends so it meets the thumb flat.
                    val length = WAVE_LENGTH.toPx()
                    val ease = length / 2f
                    val step = 2.dp.toPx()
                    val phase = wave.phase.floatValue
                    path.reset()
                    path.moveTo(0f, y)
                    var px = 0f
                    while (px < x) {
                        px = min(px + step, x)
                        val calm = min(1f, min(px / ease, (x - px) / ease))
                        path.lineTo(px, y + amplitude * calm * sin(2f * PI.toFloat() * px / length - phase))
                    }
                    drawPath(path, accent, style = Stroke(played, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (thumb) drawCircle(accent, radius = 5.dp.toPx(), center = Offset(x, y))
        }
        else -> {
            val line = 2.dp.toPx()
            drawLine(track, Offset(0f, y), Offset(size.width, y), line, StrokeCap.Round)
            if (fraction > 0f) {
                drawLine(accent, Offset(0f, y), Offset(x, y), line, StrokeCap.Round)
                if (thumb) drawCircle(accent, radius = 3.5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun TimeText(text: String, align: TextAlign) {
    val look = LocalLauncherLook.current
    Text(text, modifier = Modifier.widthIn(min = 34.dp), maxLines = 1, style = cardText(11.sp, look.textDim).copy(textAlign = align))
}

/** The small card shown while Notification access is off: one tap to Android's switch for it. */
@Composable
private fun AccessCard(ms: MusicStyle, hs: HomeStyle, modifier: Modifier) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val accent = accentFor(ms, hs, look, null)
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pressFeel(press, accent, (if (ms.followHome) hs.cardRadius else ms.cardRadius).dp)
            .musicCard(ms, hs, look)
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

private enum class Glyph { PLAY, PAUSE, NEXT, PREV, SHUFFLE }

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
        // Two crossing paths with arrow tips: the usual shuffle sign.
        Glyph.SHUFFLE -> {
            val line = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun arrowPath(fromY: Float, toY: Float) = Path().apply {
                moveTo(w * 0.14f, h * fromY)
                lineTo(w * 0.34f, h * fromY)
                lineTo(w * 0.62f, h * toY)
                lineTo(w * 0.84f, h * toY)
                // The arrow tip at the end.
                moveTo(w * 0.73f, h * (toY - 0.11f))
                lineTo(w * 0.84f, h * toY)
                lineTo(w * 0.73f, h * (toY + 0.11f))
            }
            drawPath(arrowPath(0.32f, 0.68f), color, style = line)
            drawPath(arrowPath(0.68f, 0.32f), color, style = line)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Small helpers

/**
 * The card look: the album-art frame, his home style's card ("Home style"), or its own style, see-through and corners.
 * Its own "none" means truly no card; the text keeps its shadow so it stays readable.
 */
private fun Modifier.musicCard(ms: MusicStyle, hs: HomeStyle, look: LauncherLook): Modifier = when {
    // "Album art": a dark rounded frame; the cover itself is drawn inside it (see ArtBackdrop).
    ms.artBackground -> {
        val shape = RoundedCornerShape(ms.cardRadius.dp)
        clip(shape).background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 0.92f), look.sheet.copy(alpha = 0.92f))), shape)
    }
    ms.followHome -> homeCard(hs, look)
    ms.cardStyle == "none" -> this
    else -> homeCard(
        hs.copy(
            cardStyle = ms.cardStyle,
            cardOpacity = ms.cardOpacity,
            cardRadius = ms.cardRadius,
            cardBorder = ms.cardStyle == "glass" || ms.cardStyle == "outline",
        ),
        look,
    )
}

/** The colour of ⏯ and the bar. "theme" follows the home style's gold when the card follows it. */
private fun accentFor(ms: MusicStyle, hs: HomeStyle, look: LauncherLook, np: NowPlaying?): Color = when (ms.accent) {
    "gold" -> Gold
    "art" -> np?.artColor ?: look.accent
    else -> if (ms.followHome && hs.cardAccent == "gold") Gold else look.accent
}

/** Cover size: small / medium / large, a bit smaller on the one-row compact card. */
private fun artSize(choice: String, compact: Boolean): Dp = when (choice) {
    "small" -> if (compact) 36.dp else 48.dp
    "large" -> if (compact) 56.dp else 96.dp
    else -> if (compact) 46.dp else 72.dp
}

/** False when he switched animations off in Android's developer options ("Animator duration scale" = off). */
private fun animationsOn(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

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
