package com.shamil.launcher.ui.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/*
 * The layout editor (24 Oct): the home as BLOCKS a layout arranges. The base for his all-in-one idea, where a saved
 * theme can hold a whole layout ("a Seelie save and a Smart Launcher save"). Set in Seelie settings → Look → Layout.
 * His picks from the mockup: the Classic and Big day clock faces, the layouts "Seelie now" and "Light trail",
 * the light trail and sparkles touches, and the clock lower on the screen (thumb reach).
 * The strings are what gets saved, so never rename them.
 */

/** The big blocks, top to bottom. "top" = clock, date and glance line (plus the stats card). */
const val BLOCK_TOP = "top"
const val BLOCK_WIDGETS = "widgets"
const val BLOCK_MUSIC = "music"
const val BLOCK_FAVS = "favs"
val ALL_BLOCKS = listOf(BLOCK_TOP, BLOCK_WIDGETS, BLOCK_MUSIC, BLOCK_FAVS)

data class HomeLayout(
    val order: List<String> = ALL_BLOCKS,
    val clock: Boolean = true,
    val date: Boolean = true,
    val glance: Boolean = true,
    val widgets: Boolean = true,
    val favs: Boolean = true,
    /** "left", "centre" or "right". */
    val alignClock: String = "left",
    val alignDate: String = "left",
    val alignGlance: String = "left",
    /** How far down the clock starts, as a share of the screen height (0.105 = how Seelie always was). */
    val clockTop: Float = 0.105f,
    /** Clock size, 0.7–1.15 (1 = the face's own size). */
    val clockScale: Float = 1f,
    /** Background fade: the wallpaper only at the top, melting into a colour below. */
    val fade: Boolean = false,
    /** How far down the wallpaper shows (0–100) and how soft the edge is (0–100). */
    val fadeReach: Int = 60,
    val fadeSoft: Int = 50,
    /** "black", "theme" (the theme's sheet colour) or "navy". */
    val fadeColour: String = "black",
    /** Favourites: "normal", "small" or "text" (no icons, a plain list). */
    val icons: String = "normal",
    /** Seelie touches: a thin gold light trail with a spirit at its head beside the favourites; sparkles by the clock. */
    val trail: Boolean = false,
    val sparkles: Boolean = false,
    /** The layout he started from ("now", "trail", ...), or "" once changed by hand. Only for the settings page. */
    val preset: String = "now",
) {
    fun toJson(): String = JSONObject().apply {
        put("order", JSONArray(order)); put("clock", clock); put("date", date); put("glance", glance)
        put("widgets", widgets); put("favs", favs)
        put("alignClock", alignClock); put("alignDate", alignDate); put("alignGlance", alignGlance)
        put("clockTop", clockTop.toDouble()); put("clockScale", clockScale.toDouble())
        put("fade", fade); put("fadeReach", fadeReach); put("fadeSoft", fadeSoft); put("fadeColour", fadeColour)
        put("icons", icons); put("trail", trail); put("sparkles", sparkles); put("preset", preset)
    }.toString()

    companion object {
        fun fromJson(json: String?, base: HomeLayout = HomeLayout()): HomeLayout = try {
            val o = JSONObject(json ?: return base)
            val savedOrder = o.optJSONArray("order")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            // Keep only known blocks, and add any block a newer Seelie knows about at the end.
            val order = savedOrder.filter { it in ALL_BLOCKS }.distinct().let { it + ALL_BLOCKS.filter { b -> b !in it } }
            fun align(key: String, d: String) = o.optString(key, d).takeIf { it in ALIGNS } ?: d
            HomeLayout(
                order = order,
                clock = o.optBoolean("clock", base.clock), date = o.optBoolean("date", base.date),
                glance = o.optBoolean("glance", base.glance), widgets = o.optBoolean("widgets", base.widgets),
                favs = o.optBoolean("favs", base.favs),
                alignClock = align("alignClock", base.alignClock), alignDate = align("alignDate", base.alignDate),
                alignGlance = align("alignGlance", base.alignGlance),
                clockTop = o.optDouble("clockTop", base.clockTop.toDouble()).toFloat().coerceIn(0f, 0.45f),
                clockScale = o.optDouble("clockScale", base.clockScale.toDouble()).toFloat().coerceIn(0.7f, 1.15f),
                fade = o.optBoolean("fade", base.fade),
                fadeReach = o.optInt("fadeReach", base.fadeReach).coerceIn(0, 100),
                fadeSoft = o.optInt("fadeSoft", base.fadeSoft).coerceIn(0, 100),
                fadeColour = o.optString("fadeColour", base.fadeColour).takeIf { it in FADE_COLOURS } ?: base.fadeColour,
                icons = o.optString("icons", base.icons).takeIf { it in ICON_STYLES } ?: base.icons,
                trail = o.optBoolean("trail", base.trail), sparkles = o.optBoolean("sparkles", base.sparkles),
                preset = o.optString("preset", base.preset),
            )
        } catch (e: Exception) {
            base
        }

        val ALIGNS = listOf("left", "centre", "right")
        val FADE_COLOURS = listOf("black", "theme", "navy")
        val ICON_STYLES = listOf("normal", "small", "text")
    }
}

/** A whole layout in one tap (the mockup's Layouts row; he kept "Seelie now" and "Light trail"). */
enum class LayoutPreset(val id: String, val title: String, val about: String) {
    NOW("now", "Seelie now", "Your home as it was: clock, date, glance line, widgets, favourites."),
    TRAIL("trail", "Light trail", "Seelie's own: a spirit leads a thin gold trail down beside your favourites, sparkles by the clock, the wallpaper melting into the theme colour."),
    BIG_DAY("bigday", "Big day", "The weekday is the clock, centred, with the wallpaper fading below."),
    CALM("calm", "Calm night", "Wallpaper only at the top, melting into black. No widgets, a calm list.");

    /** The layout itself (keeps his clock height and size). [face] = the clock face it uses, if it picks one. */
    fun apply(from: HomeLayout): HomeLayout = when (this) {
        NOW -> HomeLayout(clockTop = from.clockTop, clockScale = from.clockScale)
        TRAIL -> HomeLayout(clockTop = from.clockTop, clockScale = from.clockScale, fade = true, fadeReach = 86, fadeSoft = 55,
            fadeColour = "theme", trail = true, sparkles = true, preset = id)
        BIG_DAY -> HomeLayout(clockTop = from.clockTop, clockScale = from.clockScale, alignClock = "centre", alignDate = "centre",
            alignGlance = "centre", widgets = false, fade = true, fadeReach = 72, fadeSoft = 60, fadeColour = "theme", preset = id)
        CALM -> HomeLayout(clockTop = from.clockTop, clockScale = from.clockScale, widgets = false, fade = true, fadeReach = 36,
            fadeSoft = 75, fadeColour = "black", preset = id)
    }

    /** The clock face this layout uses ("thin", "bigday"…), or null to keep his own. */
    val face: String?
        get() = when (this) { TRAIL -> "thin"; BIG_DAY -> "bigday"; CALM -> "thin"; NOW -> null }
}

class HomeLayoutStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var layout by mutableStateOf(load())
        private set

    fun change(edit: (HomeLayout) -> HomeLayout) = save(edit(layout).copy(preset = ""))
    fun use(newLayout: HomeLayout) = save(newLayout)

    /** Reads it again from the phone (after a saved theme was applied). */
    fun reload() { layout = load() }

    private fun save(l: HomeLayout) {
        layout = l
        prefs.edit { putString(KEY, l.toJson()) }
    }

    // His mockup picks (24 Oct) as the starting point: the clock lower on the screen and a touch smaller.
    private fun load(): HomeLayout = HomeLayout.fromJson(prefs.getString(KEY, null), HomeLayout(clockTop = 0.29f, clockScale = 0.95f))

    companion object {
        const val PREFS = "home_layout"
        private const val KEY = "layout"
        @Volatile private var instance: HomeLayoutStore? = null

        fun get(context: Context): HomeLayoutStore =
            instance ?: synchronized(this) { instance ?: HomeLayoutStore(context.applicationContext).also { instance = it } }
    }
}

/** The layout to draw with (a preview could hand in another one later). */
@Composable
fun currentHomeLayout(): HomeLayout = HomeLayoutStore.get(LocalContext.current).layout

/** "left" / "centre" / "right" as a Compose alignment. */
fun alignOf(a: String): Alignment.Horizontal = when (a) {
    "centre" -> Alignment.CenterHorizontally
    "right" -> Alignment.End
    else -> Alignment.Start
}
