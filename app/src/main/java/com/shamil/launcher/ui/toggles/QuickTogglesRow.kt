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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Shape
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
import com.shamil.launcher.ui.home.Gold
import com.shamil.launcher.ui.home.HomeStyle
import com.shamil.launcher.ui.home.HomeStyleStore
import com.shamil.launcher.ui.home.homeCard
import com.shamil.launcher.ui.theme.LauncherLook
import com.shamil.launcher.ui.theme.LocalLauncherLook

/**
 * Everything about how the buttons look, worked out once from his setup (size, shape, colours…),
 * so each button doesn't have to decide it again.
 */
private class Skin(s: ToggleSetup, hs: HomeStyle, look: LauncherLook) {
    /** Height of a button: 36 / 44 (= the app icons) / 52 dp. */
    val button: Dp = when (s.size) { "small" -> 36.dp; "large" -> 52.dp; else -> 44.dp }
    val icon: Dp = button * 0.5f
    /** Name beside the icon, inside the button (the "Icon + name" style). */
    val beside = s.style == TogglePrefs.STYLE_PILLS
    /** A pill shape makes icon-only buttons wider, like a capsule. */
    val width: Dp? = when {
        beside -> null                    // as wide as icon + name
        s.shape == "pill" -> button * 1.55f
        else -> button
    }
    /** Corner radius, also used for the halo and the press glow. */
    val corner: Dp = if (s.shape == "squircle") button * 0.32f else button / 2
    val shape: Shape = if (s.shape == "squircle") RoundedCornerShape(corner) else CircleShape
    /** The ON fill. Its icon is dark, so it reads on every one of these. */
    val on: Color = when (s.onColour) {
        "gold" -> Gold
        "white" -> Color.White
        "home" -> if (hs.cardAccent == "gold") Gold else look.accent
        else -> look.accent
    }
    val onInk: Color = look.sheet.copy(alpha = 1f)
    /** The OFF fill and outline: glass / soft filled / just the icon. */
    val offFill: Color = when (s.offLook) {
        "soft" -> look.panel
        "bare" -> Color.Transparent
        else -> Color.White.copy(alpha = 0.08f)
    }
    val offEdge: Color = if (s.offLook == "glass") Color.White.copy(alpha = 0.24f) else Color.Transparent
    val glow = s.glow
    val labels = s.labels && !beside   // beside already shows the name
    val stateLine = s.stateLine
}

/**
 * The quick toggles row that sits between the favourites. By default as tall as an app row (56dp), its first
 * button's left edge on the same line as the app icons ([ROW_START]). Lit = on; off = glass, soft or just the icon.
 * Tap = switch or open the panel; long-press = its full settings page. Too many to fit? The row scrolls sideways.
 * Everything about its look comes from [TogglePrefs] (Seelie settings → Home screen → Quick toggles).
 * [startPadding]: where the first button starts when aligned to the icons (the settings preview uses a smaller one).
 */
@Composable
fun QuickTogglesRow(modifier: Modifier = Modifier, startPadding: Dp = ROW_START) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val s = remember { TogglePrefs.get(context) }.setup
    val hs = remember { HomeStyleStore.get(context) }.style
    val states = rememberToggleStates()
    // Leave out what this phone doesn't have (no flash, no NFC chip).
    val shown = remember(s.toggles) { s.toggles.filter { ToggleActions.isAvailable(context, it) } }
    if (shown.isEmpty()) return
    val skin = remember(s, hs, look) { Skin(s, hs, look) }

    val card = s.backing == "card"
    // Where the first button sits: on the icon edge, or a plain margin when centred / spread.
    val edge = if (s.align == "start") startPadding else 16.dp
    // With a card, the card starts 12dp before the buttons, so the buttons stay on the same line as without one.
    val cardInset = if (card) 12.dp else 0.dp
    val outerStart = maxOf(edge - cardInset, 0.dp)
    val innerStart = edge - outerStart
    val innerEnd = when {
        card -> 12.dp
        s.align == "start" -> 24.dp
        else -> 16.dp
    }
    val gap = s.gap.dp
    val arrangement = when (s.align) {
        "center" -> Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
        "spread" -> Arrangement.SpaceEvenly
        else -> Arrangement.spacedBy(gap)
    }

    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (card) Modifier.padding(start = outerStart, end = 16.dp, top = 4.dp, bottom = 4.dp).homeCard(hs, look).padding(vertical = 8.dp)
                else Modifier
            ),
    ) {
        if (card && s.cardLabel) {
            Text(
                "QUICK TOGGLES",
                modifier = Modifier.padding(start = innerStart + 2.dp, bottom = 2.dp),
                style = TextStyle(
                    fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.2.sp,
                    color = if (hs.cardAccent == "gold") Gold else look.accent,
                ),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    // At least as wide as the screen, so "centred" and "spread" have room to work; wider = scrolls.
                    .widthIn(min = maxWidth)
                    .heightIn(min = if (card) 48.dp else 56.dp)
                    .padding(start = innerStart, end = innerEnd, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = arrangement,
            ) {
                shown.forEach { t ->
                    key(t) {
                        // Spread evenly gives no space when it overflows, so each button keeps half a gap on each side.
                        Box(if (s.align == "spread") Modifier.padding(horizontal = gap / 2) else Modifier) {
                            ToggleButton(t, states, skin)
                        }
                    }
                }
            }
        }
    }
}

/** One button: the shaped part (icon, or icon + name beside it), with an optional name and state line under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToggleButton(t: QuickToggle, states: ToggleStates, skin: Skin) {
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    val on = states.isOn(t)   // null = nothing to show (it only opens something)
    val lit = on == true

    // Colours glide between off and on instead of jumping.
    val fill by animateColorAsState(if (lit) skin.on else skin.offFill, tween(220), label = "toggle fill")
    val edge by animateColorAsState(if (lit) skin.on else skin.offEdge, tween(220), label = "toggle edge")
    val ink by animateColorAsState(if (lit) skin.onInk else look.text, tween(220), label = "toggle ink")
    val glow by animateFloatAsState(if (lit && skin.glow) 1f else 0f, tween(300), label = "toggle glow")

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

    val stateText = when {
        !skin.stateLine -> null
        t == QuickToggle.VOLUME -> states.ringer
        on == null -> null
        on -> "On"
        else -> "Off"
    }
    val spoken = t.title + (if (t == QuickToggle.VOLUME) states.ringer else on?.let { if (it) "On" else "Off" })?.let { ", $it" }.orEmpty()
    // Text on the wallpaper gets the usual soft shadow; text on a lit button doesn't need it.
    val wallShadow = look.shadow

    Column(
        Modifier
            .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
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
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                // A soft halo around a lit button (two see-through rings, drawn outside the button).
                .drawBehind {
                    if (glow > 0f) {
                        val c = skin.corner.toPx()
                        for ((grow, alpha) in HALO) {
                            val g = grow.toPx()
                            drawRoundRect(
                                skin.on.copy(alpha = alpha * glow),
                                topLeft = Offset(-g, -g),
                                size = Size(size.width + 2 * g, size.height + 2 * g),
                                cornerRadius = CornerRadius(c + g),
                            )
                        }
                    }
                }
                .pressFeel(press, skin.on, skin.corner)
                .height(skin.button)
                .then(if (skin.width != null) Modifier.width(skin.width) else Modifier)
                .clip(skin.shape)
                .background(fill)
                .border(1.dp, edge, skin.shape)
                .padding(start = if (skin.beside) 12.dp else 0.dp, end = if (skin.beside) 16.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (skin.beside) Arrangement.Start else Arrangement.Center,
        ) {
            Icon(painterResource(t.icon), contentDescription = null, tint = ink, modifier = Modifier.size(skin.icon))
            if (skin.beside) {
                Spacer(Modifier.width(8.dp))
                val shadow = if (lit) null else wallShadow
                Column {
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
        // Under the button (icon-only style): the name and/or the state line, on the wallpaper.
        if (!skin.beside && (skin.labels || stateText != null)) {
            Spacer(Modifier.height(4.dp))
            if (skin.labels) {
                Text(t.short, maxLines = 1, style = TextStyle(
                    fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 11.sp, lineHeight = 13.sp, shadow = wallShadow,
                ))
            }
            if (stateText != null) {
                Text(stateText, maxLines = 1, style = TextStyle(
                    fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, lineHeight = 11.sp, shadow = wallShadow,
                    color = if (lit) skin.on else look.textDim,
                ))
            }
        }
    }
}

/** The halo's two rings: how far out, and how strong. */
private val HALO = listOf(7.dp to 0.10f, 3.5.dp to 0.18f)

/** A small empty-looking box the size of the row, for the settings preview when nothing is picked. */
@Composable
internal fun EmptyTogglesHint(text: String, startPadding: Dp) {
    val look = LocalLauncherLook.current
    Box(Modifier.fillMaxWidth().height(56.dp).padding(start = startPadding), contentAlignment = Alignment.CenterStart) {
        Text(text, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
    }
}
