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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.settings.Choice
import com.shamil.launcher.settings.Hint
import com.shamil.launcher.settings.ON_OFF
import com.shamil.launcher.settings.SectionLabel
import com.shamil.launcher.settings.onOff
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.theme.LocalLauncherLook

/*
 * Seelie settings → Home screen → Music card (locked feature #6, 23 Oct).
 * The real card at the top (with whatever is playing now), then: Notification access, the card's size and
 * progress bar, and what happens when nothing plays. Wrapped in PageFrame by the settings screen.
 */
@Composable
fun ColumnScope.MusicSettingsContent() {
    val context = LocalContext.current
    val prefs = remember { MusicPrefs.get(context) }
    val access = rememberMusicAccess()

    PreviewStage {
        if (prefs.showCard) MusicCardBody(Modifier, inSettings = true) else OffNote()
    }
    Hint("What's playing, right on your home: under the widgets, above your favourites. Tap the cover or the title to open the music app.")

    SectionLabel("Notification access")
    if (access) {
        Hint("On. Seelie can see what's playing (only that, and it never leaves your phone).")
    } else {
        AccessRow { openNotificationAccess(context) }
    }

    SectionLabel("Card")
    Choice("Show the music card", ON_OFF, prefs.showCard.onOff()) { prefs.useCard(it == "on") }
    Choice(
        "Size", listOf(MusicPrefs.SIZE_LARGE to "Large", MusicPrefs.SIZE_COMPACT to "Compact"), prefs.size,
        enabled = prefs.showCard,
    ) { prefs.useSize(it) }
    Choice("Progress bar", ON_OFF, prefs.showProgress.onOff(), enabled = prefs.showCard) { prefs.useProgress(it == "on") }

    SectionLabel("When nothing plays")
    Choice(
        "The card", listOf("on" to "Keeps its spot", "off" to "Hides"), prefs.reserveSpot.onOff(),
        enabled = prefs.showCard,
    ) { prefs.useReserve(it == "on") }
    Hint(
        if (prefs.reserveSpot) "It stays and calmly says “Nothing playing”, with ▶ to pick up where you left off. Your favourites never jump."
        else "It disappears and your favourites move up. It slides back in when music starts."
    )
}

/** A little stage like the home: the settings sheet's colours with a soft accent light, the card on top. */
@Composable
private fun PreviewStage(content: @Composable () -> Unit) {
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
    ) { content() }
}

/** In the preview when the card is switched off. */
@Composable
private fun OffNote() {
    val look = LocalLauncherLook.current
    Text(
        "The music card is off",
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp, textAlign = TextAlign.Center),
    )
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
