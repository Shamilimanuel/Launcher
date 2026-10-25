package com.shamil.launcher.ui.notify

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shamil.launcher.R
import com.shamil.launcher.apps.AppCache
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.rememberFavourites
import com.shamil.launcher.notify.NotifyHub
import com.shamil.launcher.settings.Anemo
import com.shamil.launcher.settings.Choice
import com.shamil.launcher.settings.GroupCard
import com.shamil.launcher.settings.Hint
import com.shamil.launcher.settings.ON_OFF
import com.shamil.launcher.settings.Pyro
import com.shamil.launcher.settings.SectionLabel
import com.shamil.launcher.settings.onOff
import com.shamil.launcher.settings.staggerIn
import com.shamil.launcher.ui.search.MiniSwitch
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "Home screen → Notifications" settings page (wrap it in PageFrame): notification access, the two switches
 * for the lines under favourites, and per app: show its line, and quick reply.
 */
@Composable
fun ColumnScope.NotifSettingsContent() {
    val context = LocalContext.current
    val prefs = remember { NotifPrefs.get(context) }

    // Is access on? Checked again whenever he comes back from Android's settings page.
    val connected by NotifyHub.connected.collectAsState()
    var enabled by remember { mutableStateOf(NotifyHub.isEnabled(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = NotifyHub.isEnabled(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val hasAccess = enabled || connected

    Hint("Favourites with a notification get a short line under their name, like Niagara. Tap › for the full messages, Open, dismiss, and a reply box for chat apps. Everything stays on this phone; Seelie never saves what a notification says.")

    SectionLabel("Access")
    GroupCard(
        icon = R.drawable.ic_lock,
        tint = if (hasAccess) Anemo else Pyro,
        title = "Notification access",
        sub = if (hasAccess) "On · tap to change it in Android" else "Off · tap to switch it on for Seelie",
    ) {
        runCatching { context.startActivity(NotifyHub.accessIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    if (enabled && !connected) {
        Hint("Access is on, but Android hasn't connected Seelie's listener yet. It usually does within a few seconds; if not, switch access off and on again.")
    }

    SectionLabel("On the home screen")
    Choice("Notification lines under favourites", ON_OFF, prefs.showRows.onOff()) { prefs.useRows(it == "on") }
    Choice("Show the message text", ON_OFF, prefs.showText.onOff(), enabled = prefs.showRows) { prefs.useText(it == "on") }
    Hint("Off: the line only says \"1 new message\" (no sender, no text), handy when others can see your screen. The card then asks for a tap before it shows the messages.")

    SectionLabel("Apps")
    Hint("Line: show this app's notifications under its favourite. Reply: a reply box in the card. Chat apps start with Reply on.")
    AppSwitches(prefs)
}

/** The per-app list: apps with notifications now (or since Seelie started), then his other favourites. */
@Composable
private fun AppSwitches(prefs: NotifPrefs) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val favourites = rememberFavourites()
    val now = rememberAppNotifications(forRows = false)
    val seen = NotifReader.seen

    // The home screen's saved app list (names + his icon pack's icons), read once and only read (never written).
    var installed by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.IO) { AppCache(context.applicationContext).readIndex() }
    }

    // Apps hidden in the vault never show up here (25 Oct, audit S5): their names would give them away.
    val vault = remember { com.shamil.launcher.apps.VaultStore.get(context) }
    val rows = remember(installed, favourites.packages, now.keys, seen, vault.apps) {
        val byPackage = installed.groupBy { it.component.packageName }.mapValues { it.value.first() }
        // Newest notifications first, then favourites in his order, then the rest that had any.
        val order = (now.entries.sortedByDescending { it.value.firstOrNull()?.postTime ?: 0L }.map { it.key } +
            favourites.packages + seen.sorted()).distinct()
        order.filter { it !in vault }.mapNotNull { byPackage[it] }
    }

    if (installed.isEmpty()) {
        Hint("Loading your apps…")
        return
    }
    if (rows.isEmpty()) {
        Hint("No apps yet. Add favourites, or wait for a notification.")
        return
    }
    rows.forEach { app ->
        val pkg = app.component.packageName
        val count = now[pkg]?.size ?: 0
        val sub = when {
            count > 0 -> "$count now"
            pkg in favourites -> "Favourite"
            else -> "Had notifications"
        }
        AppSwitchRow(
            app = app,
            sub = sub,
            line = !prefs.isMuted(pkg),
            reply = prefs.quickReply(pkg),
            linesOn = prefs.showRows,
            onLine = { prefs.setMuted(pkg, !it) },
            onReply = { prefs.setQuickReply(pkg, it) },
        )
    }
    Spacer(Modifier.size(8.dp))
    Text(
        "Quick reply sends through the app's own Reply button, the same as replying from the notification shade. It only shows when the app offers one.",
        modifier = Modifier.padding(horizontal = 4.dp),
        style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp, lineHeight = 16.sp),
    )
}

/** One app: icon, name, and two small switches you tap separately ("Line" and "Reply"). */
@Composable
private fun AppSwitchRow(
    app: AppEntry,
    sub: String,
    line: Boolean,
    reply: Boolean,
    linesOn: Boolean,
    onLine: (Boolean) -> Unit,
    onReply: (Boolean) -> Unit,
) {
    val look = LocalLauncherLook.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .staggerIn()
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(look.card)
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(app.icon, contentDescription = null, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, color = look.text, fontSize = 16.sp))
            Text(sub, maxLines = 1, style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
        }
        LabelledSwitch("Line", line, enabled = linesOn) { onLine(!line) }
        LabelledSwitch("Reply", reply, enabled = true) { onReply(!reply) }
    }
}

/** A tiny label above a [MiniSwitch]; tapping either flips it. Dimmed when the whole feature is off. */
@Composable
private fun LabelledSwitch(label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.textDim.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 10.sp, letterSpacing = 1.sp))
        Spacer(Modifier.size(4.dp))
        MiniSwitch(on && enabled)
    }
}
