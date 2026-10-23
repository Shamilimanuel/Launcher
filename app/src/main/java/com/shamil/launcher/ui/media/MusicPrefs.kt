package com.shamil.launcher.ui.media

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONObject

/*
 * The music card's choices (locked feature #6), set in Seelie settings → Home screen → Music card.
 * His ask (23 Oct): "allow me to customize it too but don't show it yet", so it starts hidden and nearly
 * every part can be changed. Same pattern as HomeStyle: one description, saved on the phone, Compose state,
 * ONE store for the whole app, so a change in settings shows on the home at once.
 * The strings are the saved names of each choice, so never rename them.
 */
data class MusicStyle(
    /** Show the card on the home. Off until he likes it (the settings preview shows it anyway). */
    val show: Boolean = false,
    /** "large" (cover, title, artist, buttons) or "compact" (one row). */
    val size: String = "large",

    /** true = the card looks like his widget card (Look → Home style); false = [cardStyle] below. */
    val followHome: Boolean = true,
    /** Its own card: "none", "glass", "solid" or "outline". */
    val cardStyle: String = "glass",
    /** Its own see-through level: 0 = clear, 100 = solid. */
    val cardOpacity: Int = 55,
    /** Its own corner rounding (dp). */
    val cardRadius: Int = 24,
    /** The cover fills the whole card, blurred and dimmed, with white text on top (like Samsung's player). */
    val artBackground: Boolean = false,

    /** Cover size: "small", "medium" or "large" (≈ 48 / 72 / 96 dp on the large card). */
    val artSize: String = "medium",
    /** "rounded" (square with soft corners), "circle", or "vinyl" (a record that slowly spins while playing). */
    val artShape: String = "rounded",
    /** The soft light in the cover's colour around the cover and across the card. */
    val artGlow: Boolean = true,

    /** Colour of ⏯ and the bar: "theme" (his theme / home style), "gold", or "art" (taken from the cover). */
    val accent: String = "theme",

    /** ⏯ as a "filled" circle, an "outline" circle, or a "plain" icon. */
    val buttons: String = "filled",
    /** Show ⏮ and ⏭. */
    val skipButtons: Boolean = true,
    /** The app's extra buttons (shuffle, ♥ like, + add…), at most two. */
    val extraButtons: Boolean = true,

    /** Title size in sp (14–22). */
    val titleSize: Float = 16.5f,
    /** The music app's little icon and name. */
    val showApp: Boolean = true,
    val showArtist: Boolean = true,

    /** The cover on the "left" or the "right". */
    val artSide: String = "left",
    /** Progress bar: "thin" line, "thick" rounded bar, "wavy" (a gently moving wave while playing), or "none". */
    val bar: String = "thin",

    /** When nothing plays: "keep" its spot ("Nothing playing"), shrink to a small "line", or "hide". */
    val idle: String = "keep",
) {
    fun toJson(): String = JSONObject().apply {
        put("show", show); put("size", size)
        put("followHome", followHome); put("cardStyle", cardStyle); put("cardOpacity", cardOpacity); put("cardRadius", cardRadius)
        put("artBackground", artBackground)
        put("artSize", artSize); put("artShape", artShape); put("artGlow", artGlow)
        put("accent", accent); put("buttons", buttons); put("skipButtons", skipButtons); put("extraButtons", extraButtons)
        put("titleSize", titleSize.toDouble()); put("showApp", showApp); put("showArtist", showArtist)
        put("artSide", artSide); put("bar", bar); put("idle", idle)
    }.toString()

    companion object {
        /** Reads a saved style; anything missing or broken falls back to [base]. */
        fun fromJson(json: String?, base: MusicStyle): MusicStyle = try {
            val o = JSONObject(json ?: return base)
            MusicStyle(
                show = o.optBoolean("show", base.show),
                size = o.optString("size", base.size),
                followHome = o.optBoolean("followHome", base.followHome),
                cardStyle = o.optString("cardStyle", base.cardStyle),
                cardOpacity = o.optInt("cardOpacity", base.cardOpacity),
                cardRadius = o.optInt("cardRadius", base.cardRadius),
                artBackground = o.optBoolean("artBackground", base.artBackground),
                artSize = o.optString("artSize", base.artSize),
                artShape = o.optString("artShape", base.artShape),
                artGlow = o.optBoolean("artGlow", base.artGlow),
                accent = o.optString("accent", base.accent),
                buttons = o.optString("buttons", base.buttons),
                skipButtons = o.optBoolean("skipButtons", base.skipButtons),
                extraButtons = o.optBoolean("extraButtons", base.extraButtons),
                titleSize = o.optDouble("titleSize", base.titleSize.toDouble()).toFloat(),
                showApp = o.optBoolean("showApp", base.showApp),
                showArtist = o.optBoolean("showArtist", base.showArtist),
                artSide = o.optString("artSide", base.artSide),
                bar = o.optString("bar", base.bar),
                idle = o.optString("idle", base.idle),
            )
        } catch (e: Exception) {
            base
        }
    }
}

/** Remembers his music card choices. [style] is what the card draws; change it with `change { it.copy(...) }`. */
class MusicPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("music_card", Context.MODE_PRIVATE)

    var style by mutableStateOf(MusicStyle.fromJson(prefs.getString(KEY_STYLE, null), oldStyle()))
        private set

    /** Changes one or more choices, e.g. `change { it.copy(artShape = "vinyl") }`. */
    fun change(edit: (MusicStyle) -> MusicStyle) {
        style = edit(style)
        prefs.edit { putString(KEY_STYLE, style.toJson()) }
    }

    /** The first version saved four separate switches; those still count until the new style is saved. */
    private fun oldStyle() = MusicStyle(
        show = prefs.getBoolean("show", false),
        size = prefs.getString("size", null) ?: SIZE_LARGE,
        bar = if (prefs.getBoolean("progress", true)) "thin" else "none",
        idle = if (prefs.getBoolean("reserve_spot", true)) "keep" else "hide",
    )

    /** Reads the card's style again from the phone (after a saved look was applied). */
    fun reload() {
        style = MusicStyle.fromJson(prefs.getString(KEY_STYLE, null), oldStyle())
    }

    companion object {
        const val SIZE_LARGE = "large"
        const val SIZE_COMPACT = "compact"

        private const val KEY_STYLE = "style"
        @Volatile private var instance: MusicPrefs? = null

        fun get(context: Context): MusicPrefs =
            instance ?: synchronized(this) { instance ?: MusicPrefs(context.applicationContext).also { instance = it } }
    }
}
