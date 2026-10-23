package com.shamil.launcher.ui.notify

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * His choices for notification rows (feature #6), saved on the phone like [com.shamil.launcher.ui.HomePrefs].
 * Only switches and package names are saved here, never what a notification says (that stays in memory only).
 * Every value is Compose state, so the screen redraws the moment he flips a switch.
 */
class NotifPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("notify", Context.MODE_PRIVATE)

    /** Notification lines under the favourites at all. */
    var showRows by mutableStateOf(prefs.getBoolean(KEY_ROWS, true))
        private set

    /** Privacy: off = the line only says "1 new message", no sender and no text (for when others see the screen). */
    var showText by mutableStateOf(prefs.getBoolean(KEY_TEXT, true))
        private set

    /** Apps he never wants a notification line for. */
    var muted by mutableStateOf(readSet(KEY_MUTED))
        private set

    // Quick reply: messaging apps are on by default, the rest off. We only save where he changed the default.
    private var replyOn by mutableStateOf(readSet(KEY_REPLY_ON))
    private var replyOff by mutableStateOf(readSet(KEY_REPLY_OFF))

    fun useRows(on: Boolean) { showRows = on; prefs.edit { putBoolean(KEY_ROWS, on) } }
    fun useText(on: Boolean) { showText = on; prefs.edit { putBoolean(KEY_TEXT, on) } }

    fun isMuted(packageName: String) = packageName in muted

    fun setMuted(packageName: String, mute: Boolean) {
        muted = if (mute) muted + packageName else muted - packageName
        prefs.edit { putStringSet(KEY_MUTED, muted) }
    }

    /** Is the reply box shown for this app's notifications? */
    fun quickReply(packageName: String): Boolean =
        packageName in replyOn || (packageName !in replyOff && isMessagingApp(packageName))

    fun setQuickReply(packageName: String, on: Boolean) {
        replyOn = if (on) replyOn + packageName else replyOn - packageName
        replyOff = if (!on) replyOff + packageName else replyOff - packageName
        prefs.edit {
            putStringSet(KEY_REPLY_ON, replyOn)
            putStringSet(KEY_REPLY_OFF, replyOff)
        }
    }

    // Android hands back a set we may not change, so we copy it.
    private fun readSet(key: String): Set<String> = prefs.getStringSet(key, null)?.toSet() ?: emptySet()

    companion object {
        private const val KEY_ROWS = "show_rows"
        private const val KEY_TEXT = "show_text"
        private const val KEY_MUTED = "muted"
        private const val KEY_REPLY_ON = "reply_on"
        private const val KEY_REPLY_OFF = "reply_off"

        /** Bits of package names that mean "a chat app" (Signal's real package is org.thoughtcrime.securesms). */
        private val MESSAGING = listOf(
            "whatsapp", "telegram", "signal", "thoughtcrime", "messenger", "facebook.orca",
            "discord", "instagram", "snapchat", "messages", "messaging",
        )

        /** Chat apps get quick reply switched on by default (his note: "useful for WhatsApp", not for YouTube). */
        fun isMessagingApp(packageName: String): Boolean {
            val p = packageName.lowercase()
            return MESSAGING.any { it in p }
        }

        @Volatile private var instance: NotifPrefs? = null

        fun get(context: Context): NotifPrefs =
            instance ?: synchronized(this) { instance ?: NotifPrefs(context.applicationContext).also { instance = it } }
    }
}
