package com.shamil.launcher.ui.toggles

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** How the quick toggles row is set up. All the strings are saved, so never rename them. */
data class ToggleSetup(
    /** Show the row between the favourites. Off until he switches it on (his ask, 23 Oct: "don't show it yet"). */
    val show: Boolean = false,
    /** Which toggles, in this order. */
    val toggles: List<QuickToggle> = QuickToggle.DEFAULT,
    /** The row sits after this many favourites (0 = above the first one). */
    val after: Int = 3,
    /** [TogglePrefs.STYLE_ICONS] = icon only, [TogglePrefs.STYLE_PILLS] = icon with its name beside it. */
    val style: String = TogglePrefs.STYLE_ICONS,
    /** Button size: "small" (36dp), "medium" (44dp, like the app icons) or "large" (52dp). */
    val size: String = "medium",
    /** Space between the buttons (dp). */
    val gap: Int = 12,
    /** "circle", "squircle" (rounded square) or "pill" (wider). */
    val shape: String = "circle",
    /** Colour when on: "accent" (the theme's), "gold", "white" or "home" (follows Home style's widget accent). */
    val onColour: String = "accent",
    /** A soft halo around a button that's on. */
    val glow: Boolean = true,
    /** Look when off: "glass" (see-through with an outline), "soft" (a soft filled circle) or "bare" (just the icon). */
    val offLook: String = "glass",
    /** "none" = straight on the wallpaper, "card" = a card behind the whole row that follows Home style. */
    val backing: String = "none",
    /** A small "QUICK TOGGLES" heading inside the card. */
    val cardLabel: Boolean = true,
    /** "start" = on the favourites' icon edge, "center" = centred, "spread" = spread evenly over the width. */
    val align: String = "start",
    /** The name under each button (icon-only style). */
    val labels: Boolean = false,
    /** The small "On" / "Off" line (off by default, so the approved look stays: just the round icons). */
    val stateLine: Boolean = false,
    /** The Volume button's look ([VolumeLook] id): Seelie's own sheet in a style, or "system" = the phone's pop-up. */
    val volumeLook: String = "gold",
    /** A soft click on each volume step (from the video he liked). */
    val volumeClick: Boolean = false,
)

/** The saved quick-toggles setup (on the phone, Compose state, one shared store: like HomePrefs). */
class TogglePrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("quick_toggles", Context.MODE_PRIVATE)

    var setup by mutableStateOf(load())
        private set

    fun change(edit: (ToggleSetup) -> ToggleSetup) {
        setup = edit(setup)
        val s = setup
        prefs.edit {
            putString(KEY, JSONObject().apply {
                put("v", VERSION)
                put("show", s.show)
                put("toggles", JSONArray(s.toggles.map { it.id }))
                put("after", s.after)
                put("style", s.style)
                put("size", s.size); put("gap", s.gap); put("shape", s.shape)
                put("onColour", s.onColour); put("glow", s.glow); put("offLook", s.offLook)
                put("backing", s.backing); put("cardLabel", s.cardLabel)
                put("align", s.align); put("labels", s.labels); put("stateLine", s.stateLine)
                put("volumeLook", s.volumeLook); put("volumeClick", s.volumeClick)
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
        val d = ToggleSetup()
        val list = o.optJSONArray("toggles")
        /** A saved word, but only if it's one we know; otherwise the default. */
        fun pick(key: String, fallback: String, vararg allowed: String): String =
            o.optString(key, fallback).takeIf { it in allowed } ?: fallback
        ToggleSetup(
            // A setup saved by the first version (no "v") was shown by default; his new wish is hidden until he says so.
            show = if (o.optInt("v", 1) >= VERSION) o.optBoolean("show", d.show) else false,
            toggles = if (list == null) d.toggles
            else (0 until list.length()).mapNotNull { QuickToggle.byId(list.optString(it)) }.distinct(),
            after = o.optInt("after", d.after).coerceIn(0, MAX_AFTER),
            style = pick("style", d.style, STYLE_ICONS, STYLE_PILLS),
            size = pick("size", d.size, "small", "medium", "large"),
            gap = o.optInt("gap", d.gap).coerceIn(MIN_GAP, MAX_GAP),
            shape = pick("shape", d.shape, "circle", "squircle", "pill"),
            onColour = pick("onColour", d.onColour, "accent", "gold", "white", "home"),
            glow = o.optBoolean("glow", d.glow),
            offLook = pick("offLook", d.offLook, "glass", "soft", "bare"),
            backing = pick("backing", d.backing, "none", "card"),
            cardLabel = o.optBoolean("cardLabel", d.cardLabel),
            align = pick("align", d.align, "start", "center", "spread"),
            labels = o.optBoolean("labels", d.labels),
            stateLine = o.optBoolean("stateLine", d.stateLine),
            volumeLook = VolumeLook.byId(o.optString("volumeLook", d.volumeLook)).id,
            volumeClick = o.optBoolean("volumeClick", d.volumeClick),
        )
    } catch (e: Exception) {
        ToggleSetup()
    }

    /** Reads the setup again from the phone (after a saved look was applied). */
    fun reload() {
        setup = load()
    }

    companion object {
        const val STYLE_ICONS = "icons"
        const val STYLE_PILLS = "pills"
        /** The position slider goes up to this many favourites. */
        const val MAX_AFTER = 10
        /** The spacing slider's range (dp). */
        const val MIN_GAP = 4
        const val MAX_GAP = 28
        private const val KEY = "setup"
        /** 2 = the customisable version (23 Oct). */
        private const val VERSION = 2
        @Volatile private var instance: TogglePrefs? = null

        fun get(context: Context): TogglePrefs =
            instance ?: synchronized(this) { instance ?: TogglePrefs(context.applicationContext).also { instance = it } }
    }
}
