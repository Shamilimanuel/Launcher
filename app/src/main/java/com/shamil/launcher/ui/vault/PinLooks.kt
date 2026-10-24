package com.shamil.launcher.ui.vault

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.shamil.launcher.R
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook

/*
 * The PIN screen's looks (24 Oct, from the PIN looks page): his favourite is Compact glass, and he also liked
 * Seelie dots and Light trail (those come next). "Round" is the first pad, kept as an option.
 * Every look draws the same pad with the same rules (auto-check on the last digit, fingerprint, delete);
 * only the drawing differs. The ids are what gets saved, so never rename them.
 */
enum class PinLook(val id: String, val title: String, val built: Boolean) {
    GLASS("glass", "Compact glass", true),
    SEELIE("seelie", "Seelie dots", false),
    TRAIL("trail", "Light trail", false),
    ROUND("round", "Round (first)", true);

    companion object {
        fun byId(id: String?): PinLook = entries.firstOrNull { it.id == id && it.built } ?: GLASS
    }
}

class PinLookStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var look by mutableStateOf(PinLook.byId(prefs.getString(KEY, null)))
        private set

    fun use(l: PinLook) {
        look = l
        prefs.edit { putString(KEY, l.id) }
    }

    /** The next look that's built (the vault's "PIN look" row steps through them). */
    fun next() = use(PinLook.entries.filter { it.built }.let { built -> built[(built.indexOf(look) + 1) % built.size] })

    fun reload() { look = PinLook.byId(prefs.getString(KEY, null)) }

    companion object {
        const val PREFS = "pin_look"
        private const val KEY = "look"
        @Volatile private var instance: PinLookStore? = null

        fun get(context: Context): PinLookStore =
            instance ?: synchronized(this) { instance ?: PinLookStore(context.applicationContext).also { instance = it } }
    }
}

/** A light system font for the glass keys' thin digits. */
private val Light: FontFamily = FontFamily(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL))

/**
 * Compact glass (his pick): small frosted keys low on the screen where the thumb rests, thin gold rings, a calm small
 * title and lots of space above, so it feels like a lock screen, not a calculator. [dotCount] dots ([entry] filled);
 * [dotsOffset] = the "no" shake; [doneLabel] puts an OK key where the fingerprint key would be.
 */
@Composable
fun GlassPadLayout(
    title: String, sub: String, entry: String, dotCount: Int,
    onDigit: (String) -> Unit, onDelete: () -> Unit, doneLabel: String?, onDone: () -> Unit,
    dotsOffset: Float = 0f, onFinger: (() -> Unit)? = null,
) {
    val look = LocalLauncherLook.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_lock), null, tint = look.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(title.uppercase(), style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Medium, color = look.accent,
            fontSize = 17.sp, letterSpacing = 0.2.em, shadow = Shadow(look.accent.copy(alpha = 0.35f), blurRadius = 16f)))
        Spacer(Modifier.height(5.dp))
        Text(sub, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.5.sp))
        Spacer(Modifier.height(22.dp))
        // The dots: hollow gold rings that fill and glow as you type.
        Row(Modifier.graphicsLayer { translationX = dotsOffset }, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            for (i in 0 until dotCount.coerceAtLeast(entry.length)) {
                val on = i < entry.length
                val fill by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "dot")
                Box(
                    Modifier.size(10.dp).graphicsLayer { scaleX = 1f + 0.2f * fill; scaleY = 1f + 0.2f * fill }
                        .then(if (on) Modifier.shadow(6.dp, CircleShape, ambientColor = look.accent, spotColor = look.accent) else Modifier)
                        .clip(CircleShape)
                        .background(look.accent.copy(alpha = fill))
                        .border(0.9.dp, look.accent.copy(alpha = 0.7f), CircleShape)
                )
            }
        }
        Spacer(Modifier.height(40.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                row.forEach { d -> GlassKey(d) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            when {
                doneLabel != null -> GlassKey(doneLabel, accent = true, onClick = onDone)
                onFinger != null -> GlassKey("", icon = R.drawable.ic_touch_app, quiet = true, onClick = onFinger)
                else -> Spacer(Modifier.size(60.dp))
            }
            GlassKey("0") { onDigit("0") }
            GlassKey("⌫", quiet = true, onClick = onDelete)
        }
        Spacer(Modifier.height(36.dp))
    }
}

/** One frosted key: 60dp, a thin gold ring, a thin digit; it sinks and glows gold when pressed. */
@Composable
private fun GlassKey(label: String, accent: Boolean = false, quiet: Boolean = false, icon: Int? = null, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(60.dp)
            .pressFeel(press, look.accent, 30.dp)
            .clip(CircleShape)
            .background(if (accent) look.accent else Color.White.copy(alpha = if (quiet) 0.03f else 0.065f))
            .border(0.8.dp, look.accent.copy(alpha = if (accent) 0f else if (quiet) 0.3f else 0.55f), CircleShape)
            .clickable(press, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = look.accent, modifier = Modifier.size(24.dp))
        else Text(label, style = TextStyle(
            fontFamily = if (accent || label.length > 1) look.font else Light,
            fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (label.length > 1) 15.sp else 25.sp,
            color = when {
                accent -> look.sheet.copy(alpha = 1f)
                quiet -> look.accent
                else -> look.text
            },
        ))
    }
}
