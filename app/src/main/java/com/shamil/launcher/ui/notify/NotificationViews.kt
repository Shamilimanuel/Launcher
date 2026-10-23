package com.shamil.launcher.ui.notify

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.shamil.launcher.apps.AppEntry
import com.shamil.launcher.apps.Apps
import com.shamil.launcher.ui.ROW_START
import com.shamil.launcher.ui.theme.LauncherLook
import com.shamil.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.delay

/** "now", "12m", "3h", "2d": how long ago, as short as Niagara writes it. */
internal fun ago(time: Long, now: Long): String {
    val minutes = ((now - time) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}

/** The current time, refreshed every 30 seconds, so "12m" keeps counting up without anything else redrawing. */
@Composable
private fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(30_000L)
        value = System.currentTimeMillis()
    }
}

/** Where a favourite's name starts (icon start + icon + gap, as in AppRow), so the line sits right under the name. */
@Composable
internal fun nameStart(): Dp = ROW_START + 44.dp * LocalLauncherLook.current.iconScale + 20.dp

/** Draws [content] [by] higher and takes that much less room, so the line tucks up under the row's name. */
private fun Modifier.pullUp(by: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

/**
 * Just the age, " · 12m", in a soft accent: for right after the app's name (Niagara: "YouTube · 12m").
 * Shows nothing when there are no notifications.
 */
@Composable
fun NotificationAge(notifs: List<AppNotif>, modifier: Modifier = Modifier) {
    val newest = notifs.firstOrNull() ?: return
    val look = LocalLauncherLook.current
    val now by rememberNow()
    Text(
        " · ${ago(newest.postTime, now)}",
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(fontFamily = look.font, color = look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, shadow = look.shadow),
    )
}

/**
 * What goes under a favourite's name when it has notifications: 1–2 lines "Sender: message" and a › that opens
 * the full card ([onExpand]). Tapping the text opens the newest notification itself (the chat, the video).
 * [showAge] = start the line with "12m ·" (turn it off if the age is shown next to the name with [NotificationAge]).
 * With "show message text" off it only says "1 new message", so nobody reads along over his shoulder.
 */
@Composable
fun NotificationLine(
    packageName: String,
    notifs: List<AppNotif>,
    modifier: Modifier = Modifier,
    showAge: Boolean = true,
    onExpand: () -> Unit,
) {
    val newest = notifs.firstOrNull() ?: return
    val look = LocalLauncherLook.current
    val context = LocalContext.current
    val prefs = remember { NotifPrefs.get(context) }
    val showText = prefs.showText
    val now by rememberNow()
    val age = if (showAge) ago(newest.postTime, now) else null

    // Built only when the notifications, the privacy switch or the look change (not on every frame).
    val lines: List<AnnotatedString> = remember(notifs, showText, look, packageName) {
        if (!showText) listOf(AnnotatedString(countLabel(packageName, notifs)))
        else previewEntries(notifs).map { (sender, text) -> senderLine(sender, text, look) }
    }

    Row(
        modifier
            .pullUp(12.dp)
            .fillMaxWidth()
            .padding(start = nameStart(), bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable { NotifActions.open(context, newest) },
        ) {
            lines.forEachIndexed { index, line ->
                val shown = if (index == 0 && age != null) buildAnnotatedString {
                    withStyle(SpanStyle(color = look.accent, fontWeight = FontWeight.SemiBold)) { append("$age · ") }
                    append(line)
                } else line
                Text(
                    shown,
                    // One message: up to 3 lines, like Niagara (23 Oct: 2 lines cut his messages off). More: 2 each.
                    maxLines = if (lines.size == 1) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp, lineHeight = 18.sp, shadow = look.shadow),
                )
            }
        }
        // The ›: opens the full card with every message (and quick reply).
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onExpand),
            contentAlignment = Alignment.Center,
        ) {
            Text("›", style = TextStyle(color = look.text, fontSize = 24.sp, shadow = look.shadow))
        }
    }
}

/** "1 new message" / "3 new notifications": the private version of the line. */
private fun countLabel(packageName: String, notifs: List<AppNotif>): String {
    val chat = NotifPrefs.isMessagingApp(packageName) || notifs.any { it.messages.isNotEmpty() }
    val count = if (chat) notifs.sumOf { it.count } else notifs.size
    val word = if (chat) "message" else "notification"
    return "$count new $word" + if (count == 1) "" else "s"
}

/**
 * Up to two (sender, text) pairs for the short line: the last two messages of a chat (in reading order),
 * or else the two newest notifications.
 */
private fun previewEntries(notifs: List<AppNotif>): List<Pair<String?, String>> {
    val newest = notifs.first()
    if (newest.messages.size >= 2) return newest.messages.takeLast(2).map { it.sender to it.text }
    return notifs.take(2).mapNotNull { n ->
        val text = n.text ?: return@mapNotNull n.title?.let { null to it }
        n.title to text
    }
}

/** "**Sender**: message" on one line, with the message's own markdown styled. */
private fun senderLine(sender: String?, text: String, look: LauncherLook): AnnotatedString = buildAnnotatedString {
    if (!sender.isNullOrBlank()) {
        withStyle(SpanStyle(color = look.text, fontWeight = FontWeight.SemiBold)) { append(cleanMessage(sender, flat = true)) }
        append(": ")
    }
    append(richText(text, look, flat = true))
}

/**
 * The expanded card under the row (like the shortcuts pop-up): every notification of [app] with its picture,
 * "conversation · time", the sender in bold and the full message (markdown styled), an Open button and a ✕.
 * When quick reply is on for this app and the app offers a Reply, there's a reply box right in the card.
 * Closes itself when the last notification is gone.
 */
@Composable
fun NotificationCard(app: AppEntry, notifs: List<AppNotif>, onClose: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    val prefs = remember { NotifPrefs.get(context) }
    val packageName = app.component.packageName
    val quickReply = prefs.quickReply(packageName)
    // Privacy: with "show message text" off the card asks first; one tap shows it until the card closes.
    var revealed by remember { mutableStateOf(prefs.showText) }
    // "Sent ✓" lives here (not in the reply box), so it stays even if the app removes the notification after replying.
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(note) {
        if (note != null) { delay(2_500L); note = null }
    }
    LaunchedEffect(notifs.isEmpty(), note) {
        if (notifs.isEmpty() && note == null) onClose()
    }
    val now by rememberNow()
    val shape = RoundedCornerShape(24.dp)

    Column(
        Modifier
            .padding(start = ROW_START, end = 8.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(look.sheetTop, look.sheet)))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(top = 6.dp, bottom = 8.dp),
    ) {
        // Header: "↗ App" opens the app; on the right "Sent ✓" for a moment, and Clear all.
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { Apps.launch(context, app); onClose() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("↗", style = TextStyle(color = look.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(8.dp))
                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = 16.sp))
            }
            Spacer(Modifier.weight(1f))
            note?.let {
                Text(it, modifier = Modifier.padding(end = 6.dp),
                    style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 13.sp))
            }
            if (notifs.any { it.clearable }) {
                RoundButton("✕", look, "Clear all") {
                    notifs.forEach { NotifActions.dismiss(it) }
                    onClose()
                }
            }
        }

        if (!revealed) {
            Text(
                "${countLabel(packageName, notifs)} · tap to show",
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(CircleShape)
                    .background(look.text.copy(alpha = 0.08f))
                    .clickable { revealed = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 14.sp),
            )
        } else {
            // A long list stays a card, not a wall: the newest 6, then "+ 3 more" (opens the app).
            notifs.take(MAX_IN_CARD).forEachIndexed { index, notif ->
                if (index > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                NotifEntry(
                    app = app,
                    notif = notif,
                    now = now,
                    canReply = quickReply && notif.replyAction != null,
                    onOpen = { NotifActions.open(context, notif); onClose() },
                    onSent = { ok -> note = if (ok) "Sent ✓" else "Couldn't send" },
                )
            }
            val more = notifs.size - MAX_IN_CARD
            if (more > 0) {
                Text("+ $more more in ${app.label}",
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { Apps.launch(context, app); onClose() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 13.sp))
            }
        }
    }
}

private const val MAX_IN_CARD = 6

/** One notification in the card. */
@Composable
private fun NotifEntry(app: AppEntry, notif: AppNotif, now: Long, canReply: Boolean, onOpen: () -> Unit, onSent: (Boolean) -> Unit) {
    val look = LocalLauncherLook.current
    // The styled text is built once per notification, not on every redraw.
    val body = remember(notif, look) {
        if (notif.messages.isNotEmpty()) notif.messages.map { it.sender to richText(it.text, look) }
        else listOf(notif.title to richText(notif.text.orEmpty(), look))
    }
    val meta = listOfNotNull(notif.conversation?.let { cleanMessage(it, flat = true) }, ago(notif.postTime, now)).joinToString(" · ")

    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 6.dp)) {
        Avatar(notif, app)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(meta, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = look.font, color = look.textDim, fontSize = 12.sp))
            var lastSender: String? = null
            body.forEach { (sender, text) ->
                // Same person twice in a row: their name only once (like a chat).
                if (!sender.isNullOrBlank() && sender != lastSender) {
                    Text(cleanMessage(sender, flat = true), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.text, fontSize = 15.sp))
                }
                lastSender = sender
                if (text.isNotEmpty()) {
                    Text(text, style = TextStyle(fontFamily = look.font, color = look.text.copy(alpha = 0.92f), fontSize = 15.sp, lineHeight = 21.sp))
                }
            }
            // The big picture (a YouTube video's thumbnail, a sent photo): tap it to open, like the notification.
            notif.picture?.let { pic ->
                Image(
                    pic, contentDescription = "Preview", contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp).fillMaxWidth()
                        .aspectRatio((pic.width.toFloat() / pic.height).coerceIn(0.75f, 2.4f))
                        .clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen),
                )
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Open", look, onClick = onOpen)
            }
            if (canReply) {
                ReplyBox(notif, hint = "Reply to ${notif.conversation ?: notif.title ?: app.label}", onSent = onSent)
            }
        }
        if (notif.clearable) RoundButton("✕", look, "Dismiss") { NotifActions.dismiss(notif) }
    }
}

/** The round picture: the sender's photo or the app's own icon, with the app's small glyph as a badge. */
@Composable
private fun Avatar(notif: AppNotif, app: AppEntry) {
    val look = LocalLauncherLook.current
    val picture = notif.messages.lastOrNull()?.avatar ?: notif.avatar
    Box(Modifier.size(40.dp)) {
        if (picture != null) {
            Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape))
            val small = notif.smallIcon
            if (small != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .background(look.accent, CircleShape)
                        .padding(2.dp),
                ) {
                    Image(small, contentDescription = null, colorFilter = ColorFilter.tint(look.sheet.copy(alpha = 1f)), modifier = Modifier.size(12.dp))
                }
            }
        } else {
            Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
        }
    }
}

/**
 * The reply box: a pill with his accent-coloured cursor and a send button. Sends through the app's own Reply
 * (as if he answered from the shade), clears, and tells the card "Sent ✓" or "Couldn't send".
 */
@Composable
private fun ReplyBox(notif: AppNotif, hint: String, onSent: (Boolean) -> Unit) {
    val context = LocalContext.current
    val look = LocalLauncherLook.current
    var text by remember(notif.key) { mutableStateOf("") }

    fun send() {
        if (text.isBlank()) return
        val ok = NotifActions.reply(context, notif, text)
        if (ok) text = ""
        onSent(ok)
    }

    Row(
        Modifier
            .padding(top = 10.dp, end = 4.dp)
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(look.text.copy(alpha = 0.08f))
            .border(1.dp, look.accent.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            textStyle = TextStyle(fontFamily = look.font, color = look.text, fontSize = 15.sp),
            cursorBrush = SolidColor(look.accent),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            decorationBox = { field ->
                Box {
                    if (text.isEmpty()) {
                        Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = look.font, color = look.textDim.copy(alpha = 0.7f), fontSize = 15.sp))
                    }
                    field()
                }
            },
        )
        Spacer(Modifier.width(6.dp))
        val ready = text.isNotBlank()
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (ready) look.accent else look.text.copy(alpha = 0.12f))
                .clickable(enabled = ready) { send() },
            contentAlignment = Alignment.Center,
        ) {
            Text("➤", style = TextStyle(color = if (ready) look.sheet.copy(alpha = 1f) else look.textDim, fontSize = 15.sp))
        }
    }
}

/** A small text pill button ("Open"). */
@Composable
private fun Pill(label: String, look: LauncherLook, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, look.accent.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, color = look.accent, fontSize = 13.sp),
    )
}

/** A round 36dp button with a glyph (✕). */
@Composable
private fun RoundButton(glyph: String, look: LauncherLook, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TextStyle(color = look.textDim, fontSize = 15.sp))
    }
}
