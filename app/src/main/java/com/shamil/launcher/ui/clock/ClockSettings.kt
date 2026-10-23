package com.shamil.launcher.ui.clock

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.settings.Hint
import com.shamil.launcher.settings.SectionLabel
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.sparkles
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.theme.LocalLauncherLook
import java.time.LocalDateTime

/*
 * Seelie settings → Look → Clock & date (the prototype's "look/clock" page).
 * A live preview on top, then tiles for the 12 clock styles, the date styles and the glance styles.
 * Each tile draws its own style, small, with the real time. Tap one and the preview (and the home) change at once.
 */

/** The content of the "Clock & date" page; goes inside PageFrame("Look", "Clock & date", ...). */
@Composable
fun ColumnScope.ClockSettingsContent() {
    val context = LocalContext.current
    val store = remember { ClockStore.get(context) }
    val time = rememberClockTime()
    val is24h = DateFormat.is24HourFormat(context)

    ClockPreview(store, time, is24h)
    Spacer(Modifier.height(4.dp))
    Hint("Twelve clock styles and fun date styles. All of them follow your theme's colours, accent and font.")

    SectionLabel("Clock")
    TileGrid(ClockStyle.entries) { style, modifier ->
        StyleTile(
            name = style.label,
            blurb = style.blurb,
            selected = store.clockStyle == style,
            sampleHeight = 78.dp,
            modifier = modifier,
            onClick = { store.useClockStyle(style) },
        ) {
            ClockFace(style, time, is24h, scale = tileScale(style))
        }
    }

    SectionLabel("Date")
    TileGrid(DateStyle.entries) { style, modifier ->
        StyleTile(
            name = style.label,
            blurb = null,
            selected = store.dateStyle == style,
            sampleHeight = 48.dp,
            modifier = modifier,
            onClick = { store.useDateStyle(style) },
        ) {
            DateFace(style, time, scale = 0.75f)
        }
    }

    Hint("\"Home style\" follows your bundle (Look → Home style), like the weather line under the date. The others are fun faces of their own.")
}

/** How small each clock is drawn in its tile: two-line styles and the words need a bit more room. */
private fun tileScale(style: ClockStyle): Float = when (style) {
    ClockStyle.Stacked, ClockStyle.Vertical, ClockStyle.Words -> 0.5f
    else -> 0.6f
}

/** Lays [items] out two per row; an odd last one keeps half the width. */
@Composable
private fun <T> TileGrid(items: List<T>, tile: @Composable (item: T, modifier: Modifier) -> Unit) {
    items.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            pair.forEach { tile(it, Modifier.weight(1f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * A style to pick: a small sample on top, its name (and one line about it) below.
 * The picked one gets an accent border and glow, and its ✓ springs in.
 */
@Composable
private fun StyleTile(
    name: String,
    blurb: String?,
    selected: Boolean,
    sampleHeight: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
    sample: @Composable () -> Unit,
) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(20.dp)
    val press = remember { MutableInteractionSource() }
    val accentHi = lerp(look.accent, Color.White, 0.45f)
    Column(
        modifier
            .staggerIn()
            .fillMaxWidth()
            .pressFeel(press, look.accent, 20.dp)
            .clip(shape)
            .background(
                if (selected) Brush.linearGradient(listOf(look.accent.copy(alpha = 0.18f), look.card))
                else Brush.linearGradient(listOf(look.card, look.card))
            )
            .border(if (selected) 1.5.dp else 1.dp, if (selected) look.accent else Color.White.copy(alpha = 0.07f), shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(sampleHeight), contentAlignment = Alignment.CenterStart) {
            sample()
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = if (selected) accentHi else look.text, fontSize = 14.sp),
            )
            // The ✓ pops in with a little spring, like on the theme tiles.
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn(),
                exit = scaleOut(tween(150)) + fadeOut(tween(150)),
            ) {
                Box(Modifier.size(20.dp).background(look.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", style = TextStyle(color = look.sheet.copy(alpha = 1f), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        if (blurb != null) {
            Text(blurb, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 11.sp))
        }
    }
}

/**
 * The live preview: his clock, date and glance line on a little night sky in the theme's colours.
 * A new pick fades and grows in, and the card smoothly changes height for the taller styles.
 */
@Composable
private fun ClockPreview(store: ClockStore, time: LocalDateTime, is24h: Boolean) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 1f), look.sheet.copy(alpha = 1f))))
            .drawBehind {
                // A soft glow in the accent colour, like light on the wallpaper (same as the other previews).
                drawCircle(
                    Brush.radialGradient(listOf(look.accent.copy(alpha = 0.32f), Color.Transparent), center = Offset(size.width * 0.25f, 0f), radius = size.width * 0.7f),
                    radius = size.width * 0.7f, center = Offset(size.width * 0.25f, 0f),
                )
            }
            .sparkles(look.accent.copy(alpha = 1f), count = 12)
            .border(1.dp, look.accent.copy(alpha = 0.3f), shape)
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        AnimatedContent(
            targetState = Triple(store.clockStyle, store.dateStyle, store.glanceStyle),
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 400f), initialScale = 0.94f, transformOrigin = TransformOrigin(0f, 0.5f)))
                    .togetherWith(fadeOut(tween(140)))
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.TopStart,
            label = "clock preview",
        ) { (clock, date, glance) ->
            Column {
                ClockBlock(clock, date, time, is24h, scale = 0.9f)
            }
        }
        Text(
            "LIVE PREVIEW",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color(0x99000000), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp, letterSpacing = 1.5.sp),
        )
    }
}
