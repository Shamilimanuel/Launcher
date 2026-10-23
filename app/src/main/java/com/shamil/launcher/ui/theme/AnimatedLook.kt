package com.shamil.launcher.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

/**
 * Smooth theme switch: when the theme changes, every colour glides to the new one (~0.4 s)
 * instead of jumping. The font can't glide, so it simply swaps.
 */
@Composable
fun animatedLook(target: LauncherLook): LauncherLook {
    @Composable
    fun glide(color: Color, label: String): Color = animateColorAsState(color, tween(420), label = label).value
    return target.copy(
        text = glide(target.text, "text"),
        textDim = glide(target.textDim, "textDim"),
        accent = glide(target.accent, "accent"),
        textShadow = glide(target.textShadow, "textShadow"),
        panel = glide(target.panel, "panel"),
        sheet = glide(target.sheet, "sheet"),
        sheetTop = glide(target.sheetTop, "sheetTop"),
        card = glide(target.card, "card"),
    )
}
