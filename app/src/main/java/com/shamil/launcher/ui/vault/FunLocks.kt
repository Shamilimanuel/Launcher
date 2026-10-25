package com.shamil.launcher.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.R
import com.shamil.launcher.apps.VaultStore
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook

/*
 * The fun locks (24 Oct, from the PIN looks page): little games as a code for locked apps. Since 25 Oct (audit S8) they
 * never open the vault itself (far fewer combos than a PIN). The PIN always stays as the backup ("Use PIN" on every
 * game). He liked these three. The ids are what gets saved, so never rename them.
 */
enum class FunLockKind(val id: String, val title: String, val about: String, val prompt: String) {
    ELEMENTS("elements", "Elemental combo", "Tap 4 to 6 elements in your own order", "Cast your elemental combo"),
    SEELIES("seelies", "Catch the seelies", "Catch 4 seelies in your colour order", "Catch your seelies"),
    LANTERNS("lanterns", "Lantern lock", "Turn the 4 lantern rows to your symbols", "Turn the lanterns to your symbols");

    /** Elements can be 4 to 6 long, so they wait for OK (0); the others are always 4. */
    val codeLength: Int get() = if (this == ELEMENTS) 0 else 4

    companion object {
        fun byId(id: String?): FunLockKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * True while the code a game just handed over is still being checked or saved (review, 25 Oct: that takes a moment
 * now). The games wait for the answer instead of clearing themselves halfway through "Checking…".
 */
val LocalFunLockChecking = compositionLocalOf { false }

/** The game for [kind]; all three talk to the vault the same way (see ElementLock). */
@Composable
fun FunLockGame(
    kind: FunLockKind, title: String, sub: String, codeLength: Int, wrongTick: Int, okTick: Int,
    onCode: (String) -> Unit, onSuccessShown: () -> Unit, onFinger: (() -> Unit)?, onUsePin: (() -> Unit)?,
) = when (kind) {
    FunLockKind.ELEMENTS -> ElementLock(title, sub, codeLength, wrongTick, okTick, onCode, onSuccessShown, onFinger, onUsePin)
    FunLockKind.SEELIES -> SeelieCatchLock(title, sub, codeLength, wrongTick, okTick, onCode, onSuccessShown, onFinger, onUsePin)
    FunLockKind.LANTERNS -> LanternLock(title, sub, codeLength, wrongTick, okTick, onCode, onSuccessShown, onFinger, onUsePin)
}

/**
 * Vault settings → Fun lock: pick a game, then play your code twice (like making a PIN). Only its hash is kept.
 * [onDone] goes back to the vault, whether it was set, removed or cancelled.
 */
@Composable
fun FunLockSetup(vault: VaultStore, onDone: () -> Unit) {
    var kind by remember { mutableStateOf<FunLockKind?>(null) }
    val k = kind
    if (k == null) FunLockChooser(vault, onPick = { kind = it }, onDone = onDone)
    else key(k) { FunLockMake(vault, k, onBack = { kind = null }, onDone = onDone) }
}

@Composable
private fun FunLockChooser(vault: VaultStore, onPick: (FunLockKind) -> Unit, onDone: () -> Unit) {
    val look = LocalLauncherLook.current
    val current = FunLockKind.byId(vault.gameKind)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = look.accent, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(10.dp))
        Text("Fun lock", style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 28.sp,
            shadow = Shadow(look.accent.copy(alpha = 0.4f), blurRadius = 22f)))
        Spacer(Modifier.height(6.dp))
        Text(if (current == null) "For locked apps only: a little game as an app's code. The vault keeps your PIN or password."
            else "Now: ${current.title} (for locked apps only). Pick one to make a new code.",
            textAlign = TextAlign.Center, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
        Spacer(Modifier.height(22.dp))
        FunLockKind.entries.forEach { k -> FunCard(k.title + if (k == current) "  ✓" else "", k.about) { onPick(k) } }
        if (current != null) FunCard("Turn the fun lock off", "Apps locked with it go back to the vault's code", quiet = true) {
            vault.removeGameLock(); onDone()
        }
        FunCard("Cancel", "Keep it as it is", quiet = true, onClick = onDone)
    }
}

/** Play the code, then play it again; a match saves it. */
@Composable
private fun FunLockMake(vault: VaultStore, kind: FunLockKind, onBack: () -> Unit, onDone: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var wrongTick by remember { mutableIntStateOf(0) }
    var okTick by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    androidx.activity.compose.BackHandler(onBack = onBack)
    CompositionLocalProvider(LocalFunLockChecking provides saving) { FunLockGame(
        kind = kind,
        title = if (first == null) "Make your code" else "Once more",
        sub = if (saving) "Saving…" else message ?: if (first == null) kind.about else "Play the same code again, to be sure.",
        codeLength = 0,
        wrongTick = wrongTick,
        okTick = okTick,
        onCode = { code ->
            val f = first
            when {
                saving -> {}
                // The first go: no tick, the game clears itself for the second go.
                f == null -> { first = code; message = null }
                // The slow hash runs in the background (25 Oct, audit S9).
                f == code -> { saving = true; scope.launch { vault.setGameLock(kind.id, code); okTick++ } }
                else -> { first = null; message = "They didn't match. Start again."; wrongTick++ }
            }
        },
        onSuccessShown = onDone,
        onFinger = null,
        onUsePin = null,
    ) }
}

@Composable
private fun FunCard(title: String, sub: String, quiet: Boolean = false, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(shape).background(if (quiet) Color.Transparent else look.card)
        .border(1.dp, if (quiet) look.accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f), shape)
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = TextStyle(fontFamily = look.font, color = if (quiet) look.accent else look.text, fontSize = 16.sp))
        Text(sub, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.5.sp))
    }
}
