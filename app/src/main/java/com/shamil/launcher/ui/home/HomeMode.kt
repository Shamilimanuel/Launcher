package com.shamil.launcher.ui.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/*
 * The home style (25 Oct): his ask "more styles for the home screen, not always the A–Z", then two mockup rounds
 * and focused research. Today's tall page stays the default; Poster and the Radial wheel are the first new ones.
 * Part of a save, so a "Niagara-style" save and a "Poster" save switch the whole home in one tap.
 * The ids are what gets saved, so never rename them.
 */
enum class HomeMode(val id: String, val title: String, val about: String) {
    SEELIE("seelie", "Seelie", "One tall page: clock, widgets, music, your favourites, the A–Z rail."),
    POSTER("poster", "Poster", "Your wallpaper as a painting: a big clock and four apps. Swipe up for all apps."),
    WHEEL("wheel", "Radial wheel", "A calm home. Hold the bottom corner and flick to a shelf, then to an app.");

    companion object {
        fun byId(id: String?): HomeMode = entries.firstOrNull { it.id == id } ?: SEELIE
    }
}

class HomeModeStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var mode by mutableStateOf(HomeMode.byId(prefs.getString(KEY, null)))
        private set

    fun use(m: HomeMode) {
        mode = m
        prefs.edit { putString(KEY, m.id) }
    }

    /** Reads it again (after a saved theme was applied). */
    fun reload() { mode = HomeMode.byId(prefs.getString(KEY, null)) }

    companion object {
        const val PREFS = "home_mode"
        private const val KEY = "mode"
        @Volatile private var instance: HomeModeStore? = null

        fun get(context: Context): HomeModeStore =
            instance ?: synchronized(this) { instance ?: HomeModeStore(context.applicationContext).also { instance = it } }
    }
}
