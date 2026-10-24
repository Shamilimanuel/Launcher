package com.shamil.launcher.ui

import com.shamil.launcher.ui.vault.PinLookStore
import com.shamil.launcher.ui.vault.PinLook
import com.shamil.launcher.ui.vault.GlassPadLayout
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.Apps
import com.shamil.launcher.apps.VaultStore
import com.shamil.launcher.settings.pressFeel
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The PIN vault (his locked feature #7): hold • on the rail to get here. First time: make a PIN (twice).
 * Then: the PIN pad (or the fingerprint), then his hidden apps. Leaving locks it again.
 */

/** The whole vault, full screen over the home. [allApps] = every installed app (the vault ones are picked from it). */
@Composable
fun VaultScreen(allApps: List<AppEntry>, onClose: () -> Unit) {
    val context = LocalContext.current
    val vault = remember { VaultStore.get(context) }
    val look = LocalLauncherLook.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 0.97f), look.sheet.copy(alpha = 0.99f))))
            .clickable(remember { MutableInteractionSource() }, null) {}   // nothing below can be touched through it
    ) {
        when {
            !vault.hasPin -> PinSetup(onDone = {})
            !vault.unlocked -> if (vault.passwordMode) PasswordUnlock(vault) else PinPad(vault)
            else -> VaultContent(vault, allApps, onClose)
        }
    }
}

/**
 * First time (or "Change code"): first pick Numbers (the round pad, 4–6 digits) or Anything (a password with letters,
 * numbers, symbols, any length), then type it twice.
 */
@Composable
private fun PinSetup(onDone: () -> Unit) {
    var mode by remember { mutableStateOf<String?>(null) }
    when (mode) {
        null -> CodeKindChooser { mode = it }
        "any" -> PasswordSetup(onDone)
        else -> NumberSetup(onDone)
    }
}

/** "How do you want to lock it?": two big cards. */
@Composable
private fun CodeKindChooser(onPick: (String) -> Unit) {
    val look = LocalLauncherLook.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = look.accent, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text("Lock your vault", style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 28.sp,
            shadow = Shadow(look.accent.copy(alpha = 0.4f), blurRadius = 22f)))
        Spacer(Modifier.height(6.dp))
        Text("Pick what kind of code you want.", style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
        Spacer(Modifier.height(24.dp))
        VaultSetting("Numbers", "A PIN of 4 to 6 digits on the round pad") { onPick("pin") }
        VaultSetting("Anything", "A password: letters, numbers, symbols, spaces, as long as you like") { onPick("any") }
        Text("Either way only a scrambled hash is kept, never the code itself.", modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
    }
}

/** Make a password: type it, then again. At least 4 characters, anything allowed. */
@Composable
private fun PasswordSetup(onDone: () -> Unit) {
    val context = LocalContext.current
    val vault = remember { VaultStore.get(context) }
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    PasswordLayout(
        title = if (first == null) "Make your password" else "Type it once more",
        sub = message ?: if (first == null) "Letters, numbers, symbols, any length (at least 4)." else "To be sure it's right.",
        value = entry,
        onValue = { entry = it; message = null },
        canSubmit = entry.length >= 4,
        onSubmit = {
            val f = first
            when {
                f == null -> { first = entry; entry = "" }
                f == entry -> { vault.setPin(entry, password = true); onDone() }
                else -> { first = null; entry = ""; message = "They didn't match. Start again." }
            }
        },
    )
}

/** Unlock with the password (+ fingerprint, which pops up by itself when it's on). */
@Composable
private fun PasswordUnlock(vault: VaultStore, title: String = "Vault") {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var entry by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val canFinger = vault.fingerprint && fingerprintAvailable(context)
    LaunchedEffect(Unit) { if (canFinger) askFingerprint(context, vault, title) }
    PasswordLayout(
        title = title,
        sub = message ?: "Type your password",
        value = entry,
        onValue = { entry = it; message = null },
        canSubmit = entry.isNotEmpty(),
        offset = shake.value,
        onFinger = if (canFinger) ({ askFingerprint(context, vault, title) }) else null,
        onSubmit = {
            if (SystemClock.uptimeMillis() < vault.lockedUntil) {
                message = "Too many tries. Wait a moment."
            } else if (!vault.tryPin(entry)) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                message = if (SystemClock.uptimeMillis() < vault.lockedUntil) "Too many tries. Wait 30 seconds." else "Wrong password"
                entry = ""
                scope.launch { for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, spring(dampingRatio = 0.4f, stiffness = 2000f)) }
            }
        },
    )
}

/** The password screen: title, a pill text field with 👁 show/hide, the keyboard opens by itself, OK (and fingerprint). */
@Composable
private fun PasswordLayout(
    title: String, sub: String, value: String, onValue: (String) -> Unit, canSubmit: Boolean, onSubmit: () -> Unit,
    offset: Float = 0f, onFinger: (() -> Unit)? = null,
) {
    val look = LocalLauncherLook.current
    var visible by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(title) { runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = look.accent, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, textAlign = TextAlign.Center, style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold,
            color = look.accent, fontSize = 30.sp, shadow = Shadow(look.accent.copy(alpha = 0.4f), blurRadius = 22f)))
        Spacer(Modifier.height(6.dp))
        Text(sub, textAlign = TextAlign.Center, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier
                .graphicsLayer { translationX = offset }
                .fillMaxWidth()
                .height(56.dp)
                .clip(CircleShape)
                .background(look.card)
                .border(1.5.dp, look.accent.copy(alpha = 0.6f), CircleShape)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
                cursorBrush = SolidColor(look.accent),
                textStyle = TextStyle(fontFamily = look.font, color = look.text, fontSize = 20.sp, letterSpacing = 2.sp),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            // 👁: show or hide what he typed.
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { visible = !visible }, contentAlignment = Alignment.Center) {
                Icon(painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility), null, tint = look.textDim, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onFinger != null) PadKey("", icon = R.drawable.ic_touch_app, onClick = onFinger)
            if (canSubmit) PadKey("OK", accent = true, onClick = onSubmit)
        }
    }
}

/**
 * The code screen for a locked app (or its notifications), over everything. The right code (or fingerprint) opens it;
 * back or ✕ cancels. It never leaves the vault open: it locks again right after.
 */
@Composable
fun LockGate(label: String) {
    val context = LocalContext.current
    val vault = remember { VaultStore.get(context) }
    val look = LocalLauncherLook.current
    BackHandler { vault.cancelPending() }
    // The code was right: open the app (once).
    LaunchedEffect(vault.unlocked) { if (vault.unlocked) vault.finishPending() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(look.sheetTop.copy(alpha = 0.97f), look.sheet.copy(alpha = 0.99f))))
            .clickable(remember { MutableInteractionSource() }, null) {}
    ) {
        if (vault.passwordMode) PasswordUnlock(vault, label) else PinPad(vault, label)
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).size(44.dp).clip(CircleShape)
                .clickable { vault.cancelPending() },
            contentAlignment = Alignment.Center,
        ) { Text("✕", style = TextStyle(color = look.textDim, fontSize = 20.sp)) }
    }
}

/** Make a number PIN on the round pad: 4–6 digits, then again. */
@Composable
private fun NumberSetup(onDone: () -> Unit) {
    val context = LocalContext.current
    val vault = remember { VaultStore.get(context) }
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    PadLayout(
        title = if (first == null) "Make your vault PIN" else "Type it once more",
        sub = message ?: if (first == null) "4 to 6 digits. Only a scrambled hash is kept." else "To be sure it's right.",
        entry = entry,
        maxDigits = 6,
        onDigit = { if (entry.length < 6) entry += it },
        onDelete = { entry = entry.dropLast(1) },
        doneLabel = if (entry.length >= 4) "OK" else null,
        onDone = {
            val f = first
            when {
                f == null -> { first = entry; entry = ""; message = null }
                f == entry -> { vault.setPin(entry); onDone() }
                else -> { first = null; entry = ""; message = "They didn't match. Start again." }
            }
        },
    )
}

/** The PIN pad (+ fingerprint, which pops up by itself when it's on). */
@Composable
private fun PinPad(vault: VaultStore, title: String = "Vault") {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var entry by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val canFinger = vault.fingerprint && fingerprintAvailable(context)
    fun askFinger() = askFingerprint(context, vault, title)
    LaunchedEffect(Unit) { if (canFinger) askFinger() }

    fun check() {
        if (SystemClock.uptimeMillis() < vault.lockedUntil) {
            message = "Too many tries. Wait a moment."
            entry = ""
            return
        }
        if (!vault.tryPin(entry)) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            message = if (SystemClock.uptimeMillis() < vault.lockedUntil) "Too many tries. Wait 30 seconds." else "Wrong PIN"
            entry = ""
            // A little shake, like a head saying "no".
            scope.launch {
                for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, spring(dampingRatio = 0.4f, stiffness = 2000f))
            }
        }
    }

    PadLayout(
        title = title,
        sub = message ?: "Type your PIN",
        entry = entry,
        maxDigits = 6,
        dotsOffset = shake.value,
        onDigit = {
            if (entry.length < 6) entry += it
            message = null
            // The last digit checks it by itself, like a phone's lock screen.
            if (vault.pinLength > 0 && entry.length == vault.pinLength) check()
        },
        onDelete = { entry = entry.dropLast(1) },
        doneLabel = if (vault.pinLength == 0 && entry.length >= 4) "OK" else null,
        onDone = { check() },
        // As many dots as his PIN has digits (4 until Seelie knows the length), not always six.
        dotCount = if (vault.pinLength > 0) vault.pinLength else 4,
        onFinger = if (canFinger) ({ askFinger() }) else null,
    )
}

/** Android's fingerprint prompt; success unlocks the vault ("Use code" closes it, back to typing). */
private fun askFingerprint(context: android.content.Context, vault: VaultStore, title: String = "Vault") {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val executor = context.mainExecutor
    runCatching {
        BiometricPrompt.Builder(context)
            .setTitle(if (title == "Vault") "Open your vault" else "Open $title")
            .setNegativeButton("Use code", executor) { _, _ -> }
            .build()
            .authenticate(CancellationSignal(), executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = vault.unlockWithFingerprint()
            })
    }
}

/** Fingerprint (or face) unlock is set up on this phone and Seelie may use it. */
private fun fingerprintAvailable(context: android.content.Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
        context.getSystemService(BiometricManager::class.java)?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
        @Suppress("DEPRECATION")
        (context.getSystemService(BiometricManager::class.java)?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS)
    else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
}

/** The look of every PIN screen: Cinzel title, a line under it, six dots, and a round 3x4 number pad. */
@Composable
private fun PadLayout(
    title: String, sub: String, entry: String, maxDigits: Int,
    onDigit: (String) -> Unit, onDelete: () -> Unit, doneLabel: String?, onDone: () -> Unit,
    dotsOffset: Float = 0f, onFinger: (() -> Unit)? = null, dotCount: Int = maxDigits,
) {
    // His PIN look (vault settings → PIN look). Compact glass is the default; Round is the first pad.
    val context = LocalContext.current
    when (remember { PinLookStore.get(context) }.look) {
        PinLook.GLASS -> GlassPadLayout(title, sub, entry, dotCount, onDigit, onDelete, doneLabel, onDone, dotsOffset, onFinger)
        else -> RoundPadLayout(title, sub, entry, maxDigits, onDigit, onDelete, doneLabel, onDone, dotsOffset, onFinger)
    }
}

/** The first pad: big round keys, six dots. */
@Composable
private fun RoundPadLayout(
    title: String, sub: String, entry: String, maxDigits: Int,
    onDigit: (String) -> Unit, onDelete: () -> Unit, doneLabel: String?, onDone: () -> Unit,
    dotsOffset: Float = 0f, onFinger: (() -> Unit)? = null,
) {
    val look = LocalLauncherLook.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.6f))
        Icon(painterResource(R.drawable.ic_lock), null, tint = look.accent, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, textAlign = TextAlign.Center, style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold,
            color = look.accent, fontSize = 30.sp, shadow = Shadow(look.accent.copy(alpha = 0.4f), blurRadius = 22f)))
        Spacer(Modifier.height(6.dp))
        Text(sub, textAlign = TextAlign.Center, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp))
        Spacer(Modifier.height(26.dp))
        // The dots: filled ones spring in.
        Row(Modifier.graphicsLayer { translationX = dotsOffset }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (i in 0 until maxDigits) {
                val on = i < entry.length
                val fill by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "dot")
                Box(Modifier.size(14.dp).border(1.5.dp, look.accent.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(14.dp).graphicsLayer { scaleX = fill; scaleY = fill }.background(look.accent, CircleShape))
                }
            }
        }
        Spacer(Modifier.weight(0.5f))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                row.forEach { d -> PadKey(d) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            when {
                doneLabel != null -> PadKey(doneLabel, accent = true, onClick = onDone)
                onFinger != null -> PadKey("", icon = R.drawable.ic_touch_app, onClick = onFinger)
                else -> Spacer(Modifier.size(72.dp))
            }
            PadKey("0") { onDigit("0") }
            PadKey("⌫", onClick = onDelete)
        }
        Spacer(Modifier.weight(0.4f))
    }
}

/** One round key of the pad, with the press feel. */
@Composable
private fun PadKey(label: String, accent: Boolean = false, icon: Int? = null, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(72.dp)
            .pressFeel(press, look.accent, 36.dp)
            .clip(CircleShape)
            .background(if (accent) look.accent else look.card)
            .border(1.dp, if (accent) Color.Transparent else Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable(press, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = look.text, modifier = Modifier.size(28.dp))
        else Text(label, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = if (label.length > 1) 18.sp else 26.sp,
            color = if (accent) look.sheet.copy(alpha = 1f) else look.text))
    }
}

/** Inside the vault: his hidden apps, + Add apps, and the vault's own settings. */
@Composable
private fun VaultContent(vault: VaultStore, allApps: List<AppEntry>, onClose: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    var adding by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AppEntry?>(null) }
    if (changingPin) {
        PinSetup(onDone = { changingPin = false })
        return
    }
    val inVault = allApps.filter { it.component.packageName in vault }
    val candidates = allApps.filter { it.component.packageName !in vault }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            end = 12.dp,
        ),
    ) {
        item {
            Row(Modifier.staggerIn().padding(start = 32.dp, end = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vault", style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = look.accent, fontSize = 32.sp,
                        shadow = Shadow(look.accent.copy(alpha = 0.4f), blurRadius = 22f)))
                    Text(if (adding) "Tap apps to hide them in here" else "${inVault.size} hidden · gone from the list and search",
                        style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp))
                }
                VaultChip(if (adding) "Done" else "+ Add apps") { adding = !adding }
                Spacer(Modifier.width(8.dp))
                VaultChip("Lock") { vault.lock(); onClose() }
            }
        }
        if (adding) {
            items(candidates, key = { "c-" + it.key }) { app ->
                AppRow(app, modifier = Modifier.animateItem(), onClick = { vault.add(app.component.packageName) }, onLongPress = {})
            }
        } else {
            if (inVault.isEmpty()) item {
                Text("Nothing hidden yet. Tap \"+ Add apps\", or long-press any app on your home → Move to vault.",
                    modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 12.dp),
                    style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 15.sp, lineHeight = 21.sp))
            }
            items(inVault, key = { "v-" + it.key }) { app ->
                AppRow(
                    app,
                    modifier = Modifier.animateItem(),
                    onClick = {
                        // Opening an app locks the vault again behind it.
                        Apps.launch(context, app)
                        vault.lock()
                        onClose()
                    },
                    onLongPress = { removing = app },
                )
            }
            item {
                Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 22.dp)) {
                    VaultSetting(if (vault.fingerprint) "Fingerprint: on" else "Fingerprint: off", "Unlock with your fingerprint as well as the PIN") {
                        vault.useFingerprint(!vault.fingerprint)
                    }
                    VaultSetting("Change code", "A new PIN or password (numbers, or anything you like)") { changingPin = true }
                    val pinLooks = remember { PinLookStore.get(context) }
                    VaultSetting("PIN look: " + pinLooks.look.title, "Tap to try the next look (Seelie dots and Light trail are coming)") { pinLooks.next() }
                    Text("Honest note: this hides apps inside Seelie. They still exist in your phone's Settings.",
                        modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                        style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
                }
            }
        }
    }
    removing?.let { app ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = look.sheet,
            title = { Text("Take ${app.label} out?", color = look.text) },
            text = { Text("It goes back to your list and search.", color = look.textDim) },
            confirmButton = { TextButton({ vault.remove(app.component.packageName); removing = null }) { Text("Take out", color = look.accent) } },
            dismissButton = { TextButton({ removing = null }) { Text("Cancel", color = look.textDim) } },
        )
    }
}

@Composable
private fun VaultChip(text: String, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val press = remember { MutableInteractionSource() }
    Text(text, modifier = Modifier.pressFeel(press, look.accent, 16.dp).clip(CircleShape).border(1.dp, look.accent.copy(alpha = 0.6f), CircleShape)
        .clickable(press, null, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 14.sp))
}

@Composable
private fun VaultSetting(title: String, sub: String, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(shape).background(look.card).border(1.dp, Color.White.copy(alpha = 0.06f), shape)
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = TextStyle(fontFamily = look.font, color = look.text, fontSize = 16.sp))
        Text(sub, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.5.sp))
    }
}
