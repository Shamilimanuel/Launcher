package com.shamil.launcher.ui.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONObject

/*
 * Home style: ONE description of the top of the home screen, so the clock, date, glance line and widget card
 * always look like one matching set (his 23 Oct feedback: "something feels off… bundle options so all four look the same").
 * The five bundles from the bundles page are presets; every Fine-tune control changes one field.
 * Values are the page's own: sizes in sp, spaces in dp.
 */

/** Everything about the home's top. Strings are the saved names of each choice, so never rename them. */
data class HomeStyle(
    /** "theme" = the theme's own font (his pick in Fonts), or "cinzel". */
    val clockFont: String = "theme",
    val clockSize: Float = 47f,
    /** 300 Light, 600 Medium, 800 Bold. */
    val clockWeight: Int = 800,
    /** "white", "accent", "gold" (a gold gradient) or "glass" (a frosted white gradient). */
    val clockColor: String = "white",
    /** "plain", "caps" or "pill". */
    val dateStyle: String = "plain",
    val dateSize: Float = 17f,
    /** "white", "accent" or "gold". */
    val dateColor: String = "white",
    /** A thin line under the date. */
    val dateRule: Boolean = false,
    /** "plain", "pill" or "glow". */
    val glanceStyle: String = "plain",
    val glanceSize: Float = 15f,
    val glanceColor: String = "white",
    /** Date and glance line side by side on one line. */
    val inline: Boolean = false,
    /** "none", "glass", "solid" or "outline". */
    val cardStyle: String = "none",
    /** 0 = fully see-through, 100 = solid. */
    val cardOpacity: Int = 0,
    val cardRadius: Int = 24,
    val cardBorder: Boolean = false,
    /** "full" or "compact". */
    val cardSize: String = "full",
    /** Heading + ring colour in the widgets: "theme" (the accent) or "gold". */
    val cardAccent: String = "theme",
    /** The "today" circle: "white", "accent" or "gold". */
    val cardToday: String = "white",
    /** Space clock→date, date→glance, glance→widget (dp). */
    val gap1: Int = 2,
    val gap2: Int = 6,
    val gap3: Int = 16,
    /** The one left edge for clock, date, glance and widget (dp). */
    val edge: Int = 48,
    /** The Seelie touch: a little glowing spirit, a breathing colon and a sparkle. */
    val spirit: Boolean = false,
) {
    /** Is the widget card really drawn (style chosen and not fully see-through or at least a border)? */
    val cardVisible: Boolean get() = cardStyle != "none" && (cardOpacity >= 4 || cardBorder)

    fun toJson(): String = JSONObject().apply {
        put("clockFont", clockFont); put("clockSize", clockSize.toDouble()); put("clockWeight", clockWeight); put("clockColor", clockColor)
        put("dateStyle", dateStyle); put("dateSize", dateSize.toDouble()); put("dateColor", dateColor); put("dateRule", dateRule)
        put("glanceStyle", glanceStyle); put("glanceSize", glanceSize.toDouble()); put("glanceColor", glanceColor); put("inline", inline)
        put("cardStyle", cardStyle); put("cardOpacity", cardOpacity); put("cardRadius", cardRadius); put("cardBorder", cardBorder)
        put("cardSize", cardSize); put("cardAccent", cardAccent); put("cardToday", cardToday)
        put("gap1", gap1); put("gap2", gap2); put("gap3", gap3); put("edge", edge); put("spirit", spirit)
    }.toString()

    companion object {
        /** Reads a saved style; anything missing or broken falls back to [base]. */
        fun fromJson(json: String?, base: HomeStyle): HomeStyle = try {
            val o = JSONObject(json ?: return base)
            HomeStyle(
                clockFont = o.optString("clockFont", base.clockFont),
                clockSize = o.optDouble("clockSize", base.clockSize.toDouble()).toFloat(),
                clockWeight = o.optInt("clockWeight", base.clockWeight),
                clockColor = o.optString("clockColor", base.clockColor),
                dateStyle = o.optString("dateStyle", base.dateStyle),
                dateSize = o.optDouble("dateSize", base.dateSize.toDouble()).toFloat(),
                dateColor = o.optString("dateColor", base.dateColor),
                dateRule = o.optBoolean("dateRule", base.dateRule),
                glanceStyle = o.optString("glanceStyle", base.glanceStyle),
                glanceSize = o.optDouble("glanceSize", base.glanceSize.toDouble()).toFloat(),
                glanceColor = o.optString("glanceColor", base.glanceColor),
                inline = o.optBoolean("inline", base.inline),
                cardStyle = o.optString("cardStyle", base.cardStyle),
                cardOpacity = o.optInt("cardOpacity", base.cardOpacity),
                cardRadius = o.optInt("cardRadius", base.cardRadius),
                cardBorder = o.optBoolean("cardBorder", base.cardBorder),
                cardSize = o.optString("cardSize", base.cardSize),
                cardAccent = o.optString("cardAccent", base.cardAccent),
                cardToday = o.optString("cardToday", base.cardToday),
                gap1 = o.optInt("gap1", base.gap1),
                gap2 = o.optInt("gap2", base.gap2),
                gap3 = o.optInt("gap3", base.gap3),
                edge = o.optInt("edge", base.edge),
                spirit = o.optBoolean("spirit", base.spirit),
            )
        } catch (e: Exception) {
            base
        }
    }
}

/** The five bundles from the bundles page (23 Oct; he said "add every single one"). [id] is saved, never rename it. */
/** A home style to draw with instead of his own (the theme previews show a save without applying it). */
val LocalHomeStyleOverride = compositionLocalOf<HomeStyle?> { null }

/** The home style to draw with: a preview's, or his saved one. */
@Composable
fun currentHomeStyle(): HomeStyle = LocalHomeStyleOverride.current ?: HomeStyleStore.get(LocalContext.current).style

enum class HomeBundle(val id: String, val title: String, val feel: String, val style: HomeStyle) {
    GLASS(
        "glass", "Glass", "One frosted material for the whole set: glass lettering, matching pills, the same glass card.",
        HomeStyle(clockWeight = 700, clockColor = "glass", dateStyle = "pill", dateSize = 14.5f, glanceStyle = "pill", glanceSize = 14.5f,
            inline = true, cardStyle = "glass", cardOpacity = 55, cardRadius = 24, cardBorder = true, gap1 = 6, gap2 = 8, gap3 = 14),
    ),
    FLOATING(
        "floating", "Floating", "Everything sits straight on the wallpaper, nothing boxed. Quiet and airy.",
        HomeStyle(),
    ),
    GOLD(
        "gold", "Gold", "Night-and-gold ceremony: Cinzel clock, capital date, glowing glance line, a gold-framed card.",
        HomeStyle(clockFont = "cinzel", clockSize = 46f, clockWeight = 600, clockColor = "gold", dateStyle = "caps", dateSize = 12.5f,
            dateColor = "gold", dateRule = true, glanceStyle = "glow", glanceSize = 14f, glanceColor = "gold", cardStyle = "outline",
            cardOpacity = 78, cardRadius = 20, cardBorder = true, cardAccent = "gold", cardToday = "gold", gap1 = 2, gap2 = 9, gap3 = 16),
    ),
    MINIMAL(
        "minimal", "Minimal", "Small clock, one info line, a compact widget with no box. Your favourites lead.",
        HomeStyle(clockSize = 38f, clockWeight = 600, dateSize = 15f, glanceSize = 14f, inline = true, cardSize = "compact",
            cardToday = "accent", gap1 = 0, gap2 = 8, gap3 = 12),
    ),
    SPIRIT(
        "seelie", "Seelie spirit", "A tiny glowing spirit drifts by the clock. Light clock, breathing colon, a dewy glowing card.",
        HomeStyle(clockSize = 54f, clockWeight = 300, dateSize = 16f, glanceSize = 14f, glanceStyle = "glow", cardStyle = "glass",
            cardOpacity = 50, cardRadius = 28, cardBorder = true, cardToday = "accent", gap1 = 2, gap2 = 8, gap3 = 16, spirit = true),
    );

    companion object {
        fun byId(id: String?): HomeBundle = entries.firstOrNull { it.id == id } ?: FLOATING
    }
}

/**
 * Remembers his bundle and his tweaks (saved on the phone). Compose state and ONE store for the whole app, so a change
 * in Seelie settings shows on the home at once. [style] is what the home draws; [isCustom] = it differs from the bundle.
 */
class HomeStyleStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("home_style", Context.MODE_PRIVATE)

    var bundle by mutableStateOf(HomeBundle.byId(prefs.getString(KEY_BUNDLE, null)))
        private set
    var style by mutableStateOf(HomeStyle.fromJson(prefs.getString(KEY_STYLE, null), bundle.style))
        private set

    val isCustom: Boolean get() = style != bundle.style

    /** Picks a bundle: its preset replaces any tweaks. */
    fun useBundle(b: HomeBundle) {
        bundle = b
        save(b.style)
    }

    /** Changes one or more fields, e.g. `change { it.copy(cardOpacity = 0) }`. */
    fun change(edit: (HomeStyle) -> HomeStyle) = save(edit(style))

    /** Back to the bundle's own values. */
    fun reset() = save(bundle.style)

    private fun save(s: HomeStyle) {
        style = s
        prefs.edit { putString(KEY_BUNDLE, bundle.id); putString(KEY_STYLE, s.toJson()) }
    }

    /** Reads the bundle and tweaks again from the phone (after a saved look was applied). */
    fun reload() {
        bundle = HomeBundle.byId(prefs.getString(KEY_BUNDLE, null))
        style = HomeStyle.fromJson(prefs.getString(KEY_STYLE, null), bundle.style)
    }

    companion object {
        private const val KEY_BUNDLE = "bundle"
        private const val KEY_STYLE = "style"
        @Volatile private var instance: HomeStyleStore? = null

        fun get(context: Context): HomeStyleStore =
            instance ?: synchronized(this) { instance ?: HomeStyleStore(context.applicationContext).also { instance = it } }
    }
}
