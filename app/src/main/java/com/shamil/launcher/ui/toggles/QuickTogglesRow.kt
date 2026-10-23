package com.shamil.launcher.ui.toggles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.ROW_START
import com.shamil.launcher.ui.theme.LocalLauncherLook

/** Size of one round button (the app icons are 44dp, so the row lines up with them). */
private val BUTTON = 44.dp

/**
 * The quick toggles row that sits between the favourites: as tall as an app row (56dp), its first button's
 * left edge on the same line as the app icons ([ROW_START]). Lit (accent fill, dark icon) = on; glassy outline = off
 * or "just opens something". Tap = switch or open the panel; long-press = its full settings page.
 * When there are more than fit, the row scrolls sideways.
 * [startPadding]: where the first button starts (the settings preview uses a smaller one).
 */
@Composable
fun QuickTogglesRow(modifier: Modifier = Modifier, startPadding: Dp = ROW_START) {
    val context = LocalContext.current
    val prefs = remember { TogglePrefs.get(context) }
    val setup = prefs.setup
    val states = rememberToggleStates()
    // Leave out what this phone doesn't have (no flash, no NFC chip).
    val shown = remember(setup.toggles) { setup.toggles.filter { ToggleActions.isAvailable(context, it) } }
    val pills = setup.style == TogglePrefs.STYLE_PILLS
    if (shown.isNotEmpty()) {
        Row(
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .horizontalScroll(rememberScrollState())
                .padding(start = startPadding, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (pills) 8.dp else 12.dp),
        ) {
            shown.forEach { t -> key(t) { ToggleButton(t, states, pills) } }
        }
    }
}

/** One button: round (icons style) or a pill with the name and a tiny state line (pills style). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToggleButton(t: QuickToggle, states: ToggleStates, pills: Boolean) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    val on = states.isOn(t)   // null = nothing to show (it only opens something)
    val lit = on == true

    // Colours glide between off and on instead of jumping.
    val fill by animateColorAsState(if (lit) look.accent else Color.White.copy(alpha = 0.08f), tween(220), label = "toggle fill")
    val edge by animateColorAsState(if (lit) look.accent else Color.White.copy(alpha = 0.24f), tween(220), label = "toggle edge")
    val ink by animateColorAsState(if (lit) look.sheet.copy(alpha = 1f) else look.text, tween(220), label = "toggle ink")
    val glow by animateFloatAsState(if (lit) 1f else 0f, tween(300), label = "toggle glow")

    // A springy little bounce each time it changes (not when it first appears).
    val bump = remember { Animatable(1f) }
    val first = remember { mutableStateOf(true) }
    LaunchedEffect(lit) {
        if (first.value) {
            first.value = false
            return@LaunchedEffect
        }
        bump.snapTo(0.86f)
        bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f))
    }

    val corner = BUTTON / 2
    val shape = if (pills) RoundedCornerShape(corner) else CircleShape
    val stateText = when {
        t == QuickToggle.VOLUME -> states.ringer
        on == null -> null
        on -> "On"
        else -> "Off"
    }
    val spoken = t.title + (stateText?.let { ", $it" } ?: "")

    Row(
        Modifier
            .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
            // A soft accent halo around a lit button (two see-through rings, drawn outside the button).
            .drawBehind {
                if (glow > 0f) {
                    for ((grow, alpha) in listOf(7.dp to 0.10f, 3.5.dp to 0.18f)) {
                        val g = grow.toPx()
                        drawRoundRect(
                            look.accent.copy(alpha = alpha * glow),
                            topLeft = Offset(-g, -g),
                            size = Size(size.width + 2 * g, size.height + 2 * g),
                            cornerRadius = CornerRadius(size.height / 2 + g),
                        )
                    }
                }
            }
            .pressFeel(press, look.accent, corner)
            .height(BUTTON)
            .then(if (pills) Modifier else Modifier.width(BUTTON))
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .combinedClickable(
                interactionSource = press,
                indication = null,
                role = Role.Button,
                onLongClickLabel = "Open its settings",
                onLongClick = { ToggleActions.openSettings(context, t) },   // the long-press buzz is built in
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    ToggleActions.tap(context, t, on)?.let { now -> states.guess(t, now) }
                },
            )
            .semantics { contentDescription = spoken }
            .padding(start = if (pills) 12.dp else 0.dp, end = if (pills) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (pills) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(painterResource(t.icon), contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        if (pills) {
            Spacer(Modifier.width(8.dp))
            Column {
                // Off, the pill is see-through over the wallpaper, so the text gets the usual soft shadow.
                val shadow = if (lit) null else look.shadow
                Text(t.short, maxLines = 1, style = TextStyle(
                    fontFamily = look.font, fontWeight = FontWeight.Bold, color = ink, fontSize = 13.sp, lineHeight = 15.sp, shadow = shadow,
                ))
                if (stateText != null) {
                    Text(stateText, maxLines = 1, style = TextStyle(
                        fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp,
                        color = if (lit) ink.copy(alpha = 0.75f) else look.textDim, shadow = shadow,
                    ))
                }
            }
        }
    }
}

/** A small empty-looking box the size of the row, for the settings preview when nothing is picked. */
@Composable
internal fun EmptyTogglesHint(text: String, startPadding: Dp) {
    val look = LocalLauncherLook.current
    Box(Modifier.fillMaxWidth().height(56.dp).padding(start = startPadding), contentAlignment = Alignment.CenterStart) {
        Text(text, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
    }
}
