package com.shamil.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.ROW_START
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.max

/*
 * The layout editor's drawn extras (Look → Layout): the background fade and the headings of grouped favourites.
 * (The light trail touch was removed on his call, 24 Oct; it lives on in git history, commit b5c7d26.)
 */

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

/** A small heading over a group of favourites ("SOCIAL ——"), for the grouped layout (Smart Launcher-ish). */
@Composable
fun GroupHeading(name: String) {
    val look = LocalLauncherLook.current
    Row(Modifier.padding(start = ROW_START, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name.uppercase(), style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, color = look.accent,
            fontSize = 11.5.sp, letterSpacing = 0.18.em, shadow = look.shadow))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(28.dp).height(1.dp).background(Brush.horizontalGradient(listOf(look.accent, Color.Transparent))))
    }
}
