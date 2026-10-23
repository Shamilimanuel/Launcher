package com.shamil.launcher.ui.notify

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.shamil.launcher.notify.NotifyHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** One message inside a chat notification (MessagingStyle): who said it, what, and when. */
@Immutable
data class NotifMessage(
    val sender: String,
    val text: String,
    val time: Long,
    val avatar: ImageBitmap?,
)

/**
 * One notification, already read out into plain pieces so the screen never has to dig in Android's bundles.
 * Lives in memory only: nothing here is ever saved or logged (privacy).
 * (@Immutable = a promise to Compose that it never changes, so rows only redraw when a new one arrives.)
 */
@Immutable
data class AppNotif(
    /** Android's id for it; needed to dismiss it. */
    val key: String,
    val packageName: String,
    /** When it was posted (milliseconds). */
    val postTime: Long,
    /** Usually the sender or the chat's name. */
    val title: String?,
    /** The message: the long version if the app gave one. For chats: the last message. */
    val text: String?,
    /** For chat apps: the last few messages with their senders (oldest first). Empty for other apps. */
    val messages: List<NotifMessage>,
    /** "Server · #channel", a group name, or the app's small extra line. */
    val conversation: String?,
    /** The round picture (a contact photo or a server icon), if the app sent one. */
    val avatar: ImageBitmap?,
    /** The app's small status-bar glyph (white shape; tint it). */
    val smallIcon: ImageBitmap?,
    /** The big picture some apps add (a YouTube video's thumbnail, a photo someone sent), shrunk for the card. */
    val picture: ImageBitmap? = null,
    /** What tapping the notification would open. */
    val contentIntent: PendingIntent?,
    /** The app's "Reply" button with a text box behind it (a RemoteInput), if it has one. */
    val replyAction: Notification.Action?,
    /** False for ones you can't swipe away. */
    val clearable: Boolean,
    /** The app wants it gone once it's opened. */
    val autoCancel: Boolean,
) {
    /** How many messages to count for "3 new messages". */
    val count: Int get() = max(1, messages.size)
}

/** Pictures decoded once per notification (key + post time), so scrolling never decodes again. */
private class NotifImages(val avatar: ImageBitmap?, val small: ImageBitmap?, val people: Map<String, ImageBitmap?>, val picture: ImageBitmap?)

/**
 * Turns Android's raw notification list into [AppNotif]s per app. Shared by every row: the work is done once per
 * change of the list, however many favourites ask for it.
 */
internal object NotifReader {
    private val lock = Mutex()
    private var lastRaw: List<StatusBarNotification>? = null
    private var lastResult: Map<String, List<AppNotif>> = emptyMap()
    private val images = LruCache<String, NotifImages>(80)

    /** Apps that had notifications since Seelie started, for the settings list. Memory only (privacy). */
    var seen by mutableStateOf<Set<String>>(emptySet())
        private set

    suspend fun read(context: Context, raw: List<StatusBarNotification>): Map<String, List<AppNotif>> {
        val result = lock.withLock {
            if (raw === lastRaw) return@withLock lastResult
            // Off the main (UI) thread: reading bundles and decoding pictures takes a moment.
            val fresh = withContext(Dispatchers.Default) { build(context, raw) }
            lastRaw = raw
            lastResult = fresh
            fresh
        }
        if (!seen.containsAll(result.keys)) seen = seen + result.keys
        return result
    }

    private fun build(context: Context, raw: List<StatusBarNotification>): Map<String, List<AppNotif>> {
        val own = context.packageName
        val kept = raw.filter { it.packageName != own && worthShowing(it) }
        // A "group summary" is the bundle header ("3 new messages") Android shows above the real ones.
        // Skip it when its real notifications are here too; keep it if it's all the app posted.
        val groupsWithChildren = kept.filterNot { it.isSummary() }.mapNotNull { it.groupKey }.toSet()
        return kept
            .filterNot { it.isSummary() && it.groupKey in groupsWithChildren }
            .mapNotNull { runCatching { parse(context, it) }.getOrNull() }
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.sortedByDescending { it.postTime } }
    }

    private fun StatusBarNotification.isSummary() = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    /** Leaves out what isn't a "new thing for him": music players, downloads in progress, "running" notices. */
    private fun worthShowing(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        if (sbn.isOngoing) return false
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
        val extras = n.extras ?: return true
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return false
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return !template.contains("MediaStyle")
    }

    private fun parse(context: Context, sbn: StatusBarNotification): AppNotif? {
        val n = sbn.notification
        val extras = n.extras ?: Bundle.EMPTY
        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()

        val plainTitle = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString()?.trim()?.ifEmpty { null }
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.ifEmpty { null }
        val shortText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.ifEmpty { null }
        val inboxLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.ifEmpty { null } }
            ?.joinToString("\n")?.ifEmpty { null }
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()?.ifEmpty { null }

        val pics = images.get(cacheKey(sbn)) ?: decodeImages(context, n, style).also { images.put(cacheKey(sbn), it) }

        // Chat apps: the last few messages, each with who sent it (no sender = him, "You").
        val me = style?.user?.name?.toString() ?: "You"
        val messages = style?.messages.orEmpty().takeLast(MAX_MESSAGES).mapNotNull { m ->
            val text = m.text?.toString()?.trim()?.ifEmpty { null } ?: return@mapNotNull null
            val person = m.person
            NotifMessage(
                sender = person?.name?.toString() ?: me,
                text = text,
                time = m.timestamp,
                avatar = person?.let { pics.people[personId(it)] },
            )
        }

        val last = messages.lastOrNull()
        val title = when {
            last != null && style?.isGroupConversation == true -> last.sender
            else -> plainTitle ?: last?.sender
        }
        val text = last?.text ?: bigText ?: inboxLines ?: shortText
        if (title == null && text == null) return null
        val conversation = style?.conversationTitle?.toString()?.trim()?.ifEmpty { null }
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()?.ifEmpty { null }
            ?: subText

        return AppNotif(
            key = sbn.key,
            packageName = sbn.packageName,
            postTime = sbn.postTime,
            title = title,
            text = text,
            messages = messages,
            conversation = conversation,
            avatar = pics.avatar,
            smallIcon = pics.small,
            picture = pics.picture,
            contentIntent = n.contentIntent,
            replyAction = findReply(n),
            clearable = sbn.isClearable,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
        )
    }

    private fun cacheKey(sbn: StatusBarNotification) = "${sbn.key}|${sbn.postTime}"

    private fun personId(p: androidx.core.app.Person) = p.key ?: p.name?.toString().orEmpty()

    /** The app's "Reply" action: one with a free-text box. Some apps hide it in the watch (Wear) actions. */
    private fun findReply(n: Notification): Notification.Action? {
        val wear = runCatching { Notification.WearableExtender(n).actions }.getOrNull().orEmpty()
        return (n.actions.orEmpty().asList() + wear).firstOrNull { action ->
            action.actionIntent != null && action.remoteInputs?.any { it.allowFreeFormInput } == true
        }
    }

    private fun decodeImages(context: Context, n: Notification, style: NotificationCompat.MessagingStyle?): NotifImages {
        val avatar = runCatching { n.getLargeIcon()?.loadDrawable(context)?.toImage() }.getOrNull()
        val small = runCatching { n.smallIcon?.loadDrawable(context)?.toImage(SMALL_PX) }.getOrNull()
        // A few chat photos (only the last senders), so a busy group chat doesn't decode dozens.
        val people = HashMap<String, ImageBitmap?>()
        style?.messages.orEmpty().takeLast(MAX_MESSAGES).mapNotNull { it.person }.forEach { p ->
            val id = personId(p)
            if (id !in people && people.size < MAX_MESSAGES) {
                people[id] = runCatching { p.icon?.loadDrawable(context)?.toImage() }.getOrNull()
            }
        }
        return NotifImages(avatar, small, people, bigPicture(context, n.extras ?: Bundle.EMPTY))
    }

    /** The BigPictureStyle picture: a Bitmap in older apps, an Icon on Android 12+. Shrunk so the cache stays small. */
    @Suppress("DEPRECATION")
    private fun bigPicture(context: Context, extras: Bundle): ImageBitmap? = runCatching {
        val bitmap = extras.get(Notification.EXTRA_PICTURE) as? android.graphics.Bitmap
        when {
            bitmap != null -> {
                val scale = minOf(1f, PICTURE_PX.toFloat() / max(bitmap.width, bitmap.height))
                if (scale >= 1f) bitmap.asImageBitmap()
                else android.graphics.Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * scale).roundToInt()), max(1, (bitmap.height * scale).roundToInt()), true).asImageBitmap()
            }
            android.os.Build.VERSION.SDK_INT >= 31 ->
                (extras.get(Notification.EXTRA_PICTURE_ICON) as? android.graphics.drawable.Icon)?.loadDrawable(context)?.toImage(PICTURE_PX)
            else -> null
        }
    }.getOrNull()

    /** Draws a picture at most [px] pixels on its longest side (enough for a 40dp circle), keeping its shape. */
    private fun Drawable.toImage(px: Int = AVATAR_PX): ImageBitmap {
        val w = intrinsicWidth.takeIf { it > 0 } ?: px
        val h = intrinsicHeight.takeIf { it > 0 } ?: px
        val scale = px.toFloat() / max(w, h)
        return toBitmap(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt())).asImageBitmap()
    }

    private const val MAX_MESSAGES = 5
    private const val AVATAR_PX = 128
    private const val SMALL_PX = 48
    private const val PICTURE_PX = 540
}

/**
 * Every app's notifications right now: package name -> its notifications, newest first.
 * [forRows] = true (the home screen) leaves out everything when rows are switched off, and his muted apps.
 * Cheap to call from every favourite row: the reading happens once per change, in [NotifReader].
 */
@Composable
fun rememberAppNotifications(forRows: Boolean = true): Map<String, List<AppNotif>> {
    val context = LocalContext.current.applicationContext
    val prefs = remember { NotifPrefs.get(context) }
    val raw by NotifyHub.notifications.collectAsState()
    var all by remember { mutableStateOf<Map<String, List<AppNotif>>>(emptyMap()) }
    LaunchedEffect(raw) { all = NotifReader.read(context, raw) }
    if (!forRows) return all
    val muted = prefs.muted
    val on = prefs.showRows
    return remember(all, muted, on) {
        when {
            !on -> emptyMap()
            muted.isEmpty() -> all
            else -> all.filterKeys { it !in muted }
        }
    }
}

/** The things you can do with a notification from Seelie: open it, swipe it away, answer it. */
object NotifActions {

    /**
     * Opens what the notification points to (the chat, the video), like tapping it in the shade.
     * Falls back to just opening the app. Returns false if nothing could be opened.
     */
    fun open(context: Context, notif: AppNotif): Boolean {
        val opened = notif.contentIntent?.let { pi ->
            try {
                pi.send(context, 0, null, null, null, null, startOptions())
                true
            } catch (e: PendingIntent.CanceledException) {
                false   // the app already took it back
            } catch (e: Exception) {
                false
            }
        } ?: false
        val ok = opened || openApp(context, notif.packageName)
        if (ok && notif.autoCancel) dismiss(notif)
        return ok
    }

    /** Swipes it away for real (it leaves the phone's notification shade too). */
    fun dismiss(notif: AppNotif) {
        if (!notif.clearable) return
        runCatching { NotifyHub.listener?.cancelNotification(notif.key) }
    }

    /**
     * Sends [text] through the app's own Reply button, exactly like replying from the shade.
     * Returns false when the app no longer accepts it (the notification was updated or removed).
     */
    fun reply(context: Context, notif: AppNotif, text: String): Boolean {
        val action = notif.replyAction ?: return false
        val inputs = action.remoteInputs ?: return false
        val answer = text.trim()
        if (answer.isEmpty()) return false
        return try {
            // Fill the app's text box(es) with his answer, then press its Reply button.
            val results = Bundle()
            inputs.filter { it.allowFreeFormInput }.forEach { results.putCharSequence(it.resultKey, answer) }
            val fillIn = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(inputs, fillIn, results)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
            action.actionIntent.send(context, 0, fillIn)
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun openApp(context: Context, packageName: String): Boolean = try {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } else false
    } catch (e: Exception) {
        false
    }

    /**
     * Android 14+ only lets an opened notification start the other app's screen if the sender (Seelie, which is
     * on screen) says so. Without this, tapping "Open" could silently do nothing.
     */
    @Suppress("DEPRECATION")
    private fun startOptions(): Bundle? = when {
        Build.VERSION.SDK_INT >= 36 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
            .toBundle()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
        else -> null
    }
}
