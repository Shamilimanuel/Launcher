package com.shamil.launcher.ui.toggles

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** How the quick toggles row is set up. The style strings are saved, so never rename them. */
data class ToggleSetup(
    /** Show the row between the favourites at all. */
    val show: Boolean = true,
    /** Which toggles, in this order. */
    val toggles: List<QuickToggle> = QuickToggle.DEFAULT,
    /** The row sits after this many favourites (0 = above the first one). */
    val after: Int = 3,
    /** [TogglePrefs.STYLE_ICONS] = round icons only, [TogglePrefs.STYLE_PILLS] = icon + short name + state. */
    val style: String = TogglePrefs.STYLE_ICONS,
)

/** The saved quick-toggles setup (on the phone, Compose state, one shared store: like HomePrefs). */
class TogglePrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("quick_toggles", Context.MODE_PRIVATE)

    var setup by mutableStateOf(load())
        private set

    fun change(edit: (ToggleSetup) -> ToggleSetup) {
        setup = edit(setup)
        prefs.edit {
            putString(KEY, JSONObject().apply {
                put("show", setup.show)
                put("toggles", JSONArray(setup.toggles.map { it.id }))
                put("after", setup.after)
                put("style", setup.style)
            }.toString())
        }
    }

    /** Adds a toggle (at the end) or takes it out. */
    fun flip(t: QuickToggle) = change { s -> s.copy(toggles = if (t in s.toggles) s.toggles - t else s.toggles + t) }

    /**
     * Where the row goes in a list of [favouriteCount] favourites: how many favourites sit above it
     * (0 = above the first, favouriteCount = after the last). Null = don't show the row (switched off or nothing picked).
     */
    fun rowSlot(favouriteCount: Int): Int? =
        if (!setup.show || setup.toggles.isEmpty()) null else setup.after.coerceIn(0, favouriteCount.coerceAtLeast(0))

    private fun load(): ToggleSetup = try {
        val o = JSONObject(prefs.getString(KEY, null) ?: return ToggleSetup())
        val list = o.optJSONArray("toggles")
        ToggleSetup(
            show = o.optBoolean("show", true),
            toggles = if (list == null) QuickToggle.DEFAULT
            else (0 until list.length()).mapNotNull { QuickToggle.byId(list.optString(it)) }.distinct(),
            after = o.optInt("after", 3).coerceIn(0, MAX_AFTER),
            style = o.optString("style", STYLE_ICONS).takeIf { it == STYLE_ICONS || it == STYLE_PILLS } ?: STYLE_ICONS,
        )
    } catch (e: Exception) {
        ToggleSetup()
    }

    companion object {
        const val STYLE_ICONS = "icons"
        const val STYLE_PILLS = "pills"
        /** The position slider goes up to this many favourites. */
        const val MAX_AFTER = 10
        private const val KEY = "setup"
        @Volatile private var instance: TogglePrefs? = null

        fun get(context: Context): TogglePrefs =
            instance ?: synchronized(this) { instance ?: TogglePrefs(context.applicationContext).also { instance = it } }
    }
}
