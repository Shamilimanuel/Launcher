package com.shamil.launcher.ui.media

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/*
 * The music card's choices (locked feature #6, 23 Oct), set in Seelie settings → Home screen → Music card.
 * Same pattern as HomePrefs: saved on the phone, Compose state, and ONE store for the whole app,
 * so a change in settings shows on the home at once.
 */
class MusicPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("music_card", Context.MODE_PRIVATE)

    /** Show the music card on the home at all. */
    var showCard by mutableStateOf(prefs.getBoolean(KEY_SHOW, true))
        private set

    /**
     * Keep the card's spot when nothing plays. On = it stays and says "Nothing playing", so the favourites never jump.
     * Off = it disappears (like Niagara's) and slides back when music starts.
     */
    var reserveSpot by mutableStateOf(prefs.getBoolean(KEY_RESERVE, true))
        private set

    /** "large" (cover, title, artist, ⏮ ⏯ ⏭) or "compact" (one row). These strings are saved, never rename them. */
    var size by mutableStateOf(prefs.getString(KEY_SIZE, SIZE_LARGE) ?: SIZE_LARGE)
        private set

    /** The thin progress bar with the elapsed / total times. */
    var showProgress by mutableStateOf(prefs.getBoolean(KEY_PROGRESS, true))
        private set

    fun useCard(on: Boolean) { showCard = on; prefs.edit { putBoolean(KEY_SHOW, on) } }
    fun useReserve(on: Boolean) { reserveSpot = on; prefs.edit { putBoolean(KEY_RESERVE, on) } }
    fun useSize(value: String) { size = value; prefs.edit { putString(KEY_SIZE, value) } }
    fun useProgress(on: Boolean) { showProgress = on; prefs.edit { putBoolean(KEY_PROGRESS, on) } }

    companion object {
        const val SIZE_LARGE = "large"
        const val SIZE_COMPACT = "compact"

        private const val KEY_SHOW = "show"
        private const val KEY_RESERVE = "reserve_spot"
        private const val KEY_SIZE = "size"
        private const val KEY_PROGRESS = "progress"
        @Volatile private var instance: MusicPrefs? = null

        fun get(context: Context): MusicPrefs =
            instance ?: synchronized(this) { instance ?: MusicPrefs(context.applicationContext).also { instance = it } }
    }
}
