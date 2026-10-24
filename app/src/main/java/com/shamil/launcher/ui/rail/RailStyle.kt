package com.shamil.launcher.ui.rail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/*
 * Seelie's own A–Z rail (24 Oct): "we have something from Niagara like the A–Z but we don't have our very own".
 * He played 10 rails on the ideas page and picked 9, in this order. Guide on the arc is Seelie's signature (the
 * default); the Niagara wave stays as "Today". Styles not built yet are marked so settings can say "coming".
 * The ids are what gets saved, so never rename them.
 */
enum class RailStyle(val id: String, val title: String, val about: String, val built: Boolean) {
    ARC_GUIDE("arcguide", "Guide on the arc", "Letters on a curve where your thumb rests; a seelie guide rides along with its light trail.", true),
    GUIDE("guide", "Seelie guide", "The rail on the edge; a seelie rides under your thumb and lights the letters up.", false),
    THUMB_ARC("arc", "Thumb arc", "Letters on a curve at the bottom right; the ones near your thumb bulge out.", false),
    FAN("fan", "Petal fan", "Touch a letter and its apps bloom beside your thumb; slide onto one to open it.", false),
    CONSTELLATION("constellation", "Constellation", "The letters are stars; sliding draws constellation lines.", false),
    LANTERN("lantern", "Lanterns", "Letters hang like paper lanterns; the one under your thumb glows and its neighbours swing.", false),
    GLIDER("glider", "Wind glider", "Like gliding: the letter lifts and its neighbours flow out on a wind of light.", false),
    WHEEL("wheel", "Element wheel", "Hold the edge and a dial of letters opens around your thumb; turn to choose.", false),
    NIAGARA("niagara", "Today (Niagara-style)", "The wave you had before: letters bend out towards your thumb.", true);

    companion object {
        fun byId(id: String?): RailStyle = entries.firstOrNull { it.id == id && it.built } ?: ARC_GUIDE
    }
}

class RailStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var style by mutableStateOf(RailStyle.byId(prefs.getString(KEY, null)))
        private set

    fun use(s: RailStyle) {
        style = s
        prefs.edit { putString(KEY, s.id) }
    }

    /** Reads it again (after a saved theme was applied). */
    fun reload() { style = RailStyle.byId(prefs.getString(KEY, null)) }

    companion object {
        const val PREFS = "rail"
        private const val KEY = "style"
        @Volatile private var instance: RailStore? = null

        fun get(context: Context): RailStore =
            instance ?: synchronized(this) { instance ?: RailStore(context.applicationContext).also { instance = it } }
    }
}
