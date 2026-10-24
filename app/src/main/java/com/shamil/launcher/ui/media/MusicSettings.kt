package com.shamil.launcher.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.settings.Choice
import com.shamil.launcher.settings.Hint
import com.shamil.launcher.settings.ON_OFF
import com.shamil.launcher.settings.SectionLabel
import com.shamil.launcher.settings.Slide
import com.shamil.launcher.settings.onOff
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlin.math.roundToInt

/*
 * Seelie settings → Home screen → Music card (locked feature #6, 23 Oct).
 * The real card at the top (with whatever is playing now, shown even while it's off on the home), then every choice:
 * the card, the cover, colours, buttons, text, layout, and what happens when nothing plays.
 * Wrapped in PageFrame by the settings screen.
 */

/**
 * The page content. [showPreview] = false when the settings screen pins [MusicPreview] at the top instead
 * (recommended: this page is long, so a pinned preview stays in view while he changes things).
 * [part] = one tab of the Music card page (24 Oct, "tap the thing", so no more long scroll): "card" (access, show,
 * size, card look), "cover" (cover art + accent colour) or "controls" (buttons, text, layout, when nothing plays).
 * null = everything, as before.
 */
@Composable
fun ColumnScope.MusicSettingsContent(showPreview: Boolean = true, part: String? = null) {
    val context = LocalContext.current
    val prefs = remember { MusicPrefs.get(context) }
    val ms = prefs.style
    val access = rememberMusicAccess()
    fun change(edit: (MusicStyle) -> MusicStyle) = prefs.change(edit)
    fun show(p: String) = part == null || part == p

    if (showPreview) {
        MusicPreview()
        Spacer(Modifier.height(6.dp))
    }
    if (show("card")) {
        Hint(
            if (ms.show) "On your home: under the widgets, above your favourites. Tap the cover or the title to open the music app."
            else "Hidden on your home for now. Shape it here (the preview shows how it'll look), then switch it on below."
        )

        SectionLabel("Notification access")
        if (access) {
            Hint("On. Seelie can see what's playing (only that, and it never leaves your phone).")
        } else {
            AccessRow { openNotificationAccess(context) }
        }

        SectionLabel("Card")
        Choice("Show on the home", ON_OFF, ms.show.onOff()) { v -> change { it.copy(show = v == "on") } }
        Choice("Size", listOf(MusicPrefs.SIZE_LARGE to "Large", MusicPrefs.SIZE_COMPACT to "Compact"), ms.size) { v ->
            change { it.copy(size = v) }
        }
        val cardLook = when {
            ms.artBackground -> "art"
            ms.followHome -> "home"
            else -> "own"
        }
        Choice("Card look", listOf("home" to "Home style", "own" to "Its own", "art" to "Album art"), cardLook) { v ->
            change { it.copy(artBackground = v == "art", followHome = if (v == "art") it.followHome else v == "home") }
        }
        Hint(
            when (cardLook) {
                "art" -> "The cover fills the card, softly blurred and dimmed, with white text on top."
                "home" -> "Matches your widget card (Widgets → Card)."
                else -> "Its own card, shaped below."
            }
        )
        val own = cardLook == "own"
        Choice("Style", listOf("none" to "None", "glass" to "Glass", "solid" to "Solid", "outline" to "Outline"), ms.cardStyle, enabled = own) { v ->
            change { it.copy(cardStyle = v) }
        }
        val ownCard = own && ms.cardStyle != "none"
        Slide("See-through", ms.cardOpacity.toFloat(), 0f..100f, steps = 19, unit = "%", enabled = ownCard) { v ->
            change { it.copy(cardOpacity = v.roundToInt()) }
        }
        // Corners also shape the album-art card.
        Slide("Corners", ms.cardRadius.toFloat(), 0f..36f, steps = 17, unit = "dp", enabled = ownCard || cardLook == "art") { v ->
            change { it.copy(cardRadius = v.roundToInt()) }
        }
    }

    if (show("cover")) {
        SectionLabel("Cover art")
        Choice("Size", listOf("small" to "Small", "medium" to "Medium", "large" to "Large"), ms.artSize) { v -> change { it.copy(artSize = v) } }
        Choice("Shape", listOf("rounded" to "Rounded", "circle" to "Circle", "vinyl" to "Vinyl"), ms.artShape) { v -> change { it.copy(artShape = v) } }
        if (ms.artShape == "vinyl") Hint("The record turns slowly while the song plays, and rests when you pause.")
        Choice("Glow", ON_OFF, ms.artGlow.onOff()) { v -> change { it.copy(artGlow = v == "on") } }

        SectionLabel("Colours")
        Choice("Accent", listOf("theme" to "Theme", "gold" to "Gold", "art" to "Album art"), ms.accent) { v -> change { it.copy(accent = v) } }
        Hint("Colours the ⏯ button and the progress bar. Album art takes each cover's own colour, song by song.")
    }

    if (show("controls")) {
        SectionLabel("Buttons")
        Choice("Play button", listOf("filled" to "Filled", "outline" to "Outline", "plain" to "Plain"), ms.buttons) { v -> change { it.copy(buttons = v) } }
        Choice("⏮ ⏭ buttons", ON_OFF, ms.skipButtons.onOff()) { v -> change { it.copy(skipButtons = v == "on") } }
        Choice("Extra buttons", ON_OFF, ms.extraButtons.onOff()) { v -> change { it.copy(extraButtons = v == "on") } }
        Hint(
            "Up to two of the music app's own buttons, like shuffle or ♥ like (Spotify, YouTube Music…). " +
                "On the large card the buttons get their own row; compact shows one."
        )

        SectionLabel("Text")
        Slide("Title size", ms.titleSize, 14f..22f, steps = 15, unit = "sp") { v -> change { it.copy(titleSize = v) } }
        Choice("Artist", ON_OFF, ms.showArtist.onOff()) { v -> change { it.copy(showArtist = v == "on") } }
        Choice("App icon and name", ON_OFF, ms.showApp.onOff()) { v -> change { it.copy(showApp = v == "on") } }

        SectionLabel("Layout")
        Choice("Cover", listOf("left" to "Left", "right" to "Right"), ms.artSide) { v -> change { it.copy(artSide = v) } }
        Choice("Progress bar", listOf("thin" to "Thin", "thick" to "Thick", "wavy" to "Wavy", "none" to "None"), ms.bar) { v -> change { it.copy(bar = v) } }
        if (ms.bar == "wavy") Hint("While the song plays, the played part ripples gently; it lies flat when you pause.")
        if (ms.bar != "none") Hint("Tap or drag along the bar to jump in the song (when the music app allows it).")

        SectionLabel("When nothing plays")
        Choice("The card", listOf("keep" to "Keeps its spot", "line" to "Small line", "hide" to "Hides"), ms.idle) { v ->
            change { it.copy(idle = v) }
        }
        Hint(
            when (ms.idle) {
                "line" -> "It shrinks to one small line, “Nothing playing ▶”, and grows back when music starts."
                "hide" -> "It disappears and your favourites move up. It slides back in when music starts."
                else -> "It stays and calmly says “Nothing playing”, with ▶ to pick up where you left off. Your favourites never jump."
            }
        )
    }
}

/**
 * The live preview: the real card on a little stage in the settings colours. Shown even while the card is off
 * on the home, and even when it would hide (so he can always see what he's shaping).
 * Can be pinned by the settings screen: PageFrame(…, pinned = { MusicPreview() }) { MusicSettingsContent(showPreview = false) }.
 */
@Composable
fun MusicPreview() {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .staggerIn()
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 1f), look.sheet.copy(alpha = 1f))))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(look.accent.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.2f, 0f),
                        radius = size.width * 0.7f,
                    ),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.2f, 0f),
                )
            }
            .border(1.dp, look.accent.copy(alpha = 0.3f), shape)
            .padding(16.dp)
    ) {
        MusicCardBody(Modifier, inSettings = true)
    }
}

/** "Off" line with an Allow button that opens Android's Notification access switch. */
@Composable
private fun AccessRow(onAllow: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(18.dp)
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .staggerIn()
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Off", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 16.sp))
            Text(
                "Android shows Seelie what's playing only with Notification access. It stays on your phone.",
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp, lineHeight = 18.sp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "Allow",
            modifier = Modifier
                .pressFeel(press, Color.White, 20.dp)
                .clip(CircleShape)
                .background(look.accent)
                .clickable(interactionSource = press, indication = null, onClick = onAllow)
                .padding(horizontal = 18.dp, vertical = 9.dp),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.sheet.copy(alpha = 1f), fontSize = 14.sp),
        )
    }
}
