package com.shamil.launcher.ui.livebg

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import org.json.JSONObject

/*
 * The living background's choices, set in Seelie settings → Living background.
 * Same pattern as MusicPrefs: one description, saved on the phone as JSON, Compose state, ONE store for the
 * whole app, so a change in settings shows on the home at once. Its own settings file is "living_bg"
 * (so saved themes can copy it as a whole). The strings are saved names, so never rename them.
 */

/** One effect's choices: on or off, how much of it (0–100), and for Glints on water where the band sits (0–100). */
data class FxSetting(val on: Boolean, val density: Int, val height: Int = 62)

data class LiveBgStyle(
    /** The main switch. */
    val enabled: Boolean = true,
    /** Calm mode: everything moves at 0.4× speed, about 60% as many things, no lightning flashes. */
    val calm: Boolean = true,
    /** 60 frames a second instead of 30 (smoother, about twice the battery). */
    val fps60: Boolean = false,
    /** Colour mood: "gold", "pink", "violet" or "green". */
    val mood: String = "pink",
    /** "auto" (from the clock) or a fixed "dawn", "day", "dusk", "night". */
    val time: String = "auto",
    /** "real" (follow the real weather) or a fixed "clear", "rain", "storm", "snow", "mist". */
    val weather: String = "real",
    /** Every effect by id (see LiveFx). Missing ones count as off at their starting density. */
    val fx: Map<String, FxSetting> = DEFAULT_FX,
    /** The preset he picked last ("fuji", …), or "" once he changed something himself. Only for the settings page. */
    val preset: String = "",
) {
    fun fx(id: String): FxSetting = fx[id] ?: FxSetting(false, LiveFx.byId(id)?.def ?: 50)

    /** Tilt strength 0–100 (0 when tilt is off). */
    val tiltStrength: Int get() = fx("tilt").let { if (it.on) it.density else 0 }

    /** Something to draw: the main switch is on and at least one effect (tilt alone doesn't count). */
    fun anythingOn(): Boolean = enabled && fx.any { (id, s) -> s.on && id != "tilt" }

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled); put("calm", calm); put("fps60", fps60)
        put("mood", mood); put("time", time); put("weather", weather); put("preset", preset)
        put("fx", JSONObject().apply {
            fx.forEach { (id, s) -> put(id, JSONObject().put("on", s.on).put("density", s.density).put("height", s.height)) }
        })
    }.toString()

    companion object {
        /** Reads a saved style; anything missing or broken falls back to [base]. */
        fun fromJson(json: String?, base: LiveBgStyle): LiveBgStyle = try {
            val o = JSONObject(json ?: return base)
            val saved = o.optJSONObject("fx")
            // Start from the defaults, so an effect added in a later version shows up with its default.
            val fx = base.fx.toMutableMap()
            if (saved != null) {
                LiveFx.ALL.forEach { info ->
                    val e = saved.optJSONObject(info.id) ?: return@forEach
                    val d = base.fx(info.id)
                    fx[info.id] = FxSetting(
                        on = e.optBoolean("on", d.on),
                        density = e.optInt("density", d.density).coerceIn(0, 100),
                        height = e.optInt("height", d.height).coerceIn(0, 100),
                    )
                }
            }
            LiveBgStyle(
                enabled = o.optBoolean("enabled", base.enabled),
                calm = o.optBoolean("calm", base.calm),
                fps60 = o.optBoolean("fps60", base.fps60),
                mood = o.optString("mood", base.mood).takeIf { it in MOOD_IDS } ?: base.mood,
                time = o.optString("time", base.time),
                weather = o.optString("weather", base.weather),
                fx = fx,
                preset = o.optString("preset", base.preset),
            )
        } catch (e: Exception) {
            base
        }
    }
}

/** His calm starting mix (24 Oct): petals 45, fallen blossoms 50, motes 40, stars 60, clouds 35, tilt 40. The rest off. */
private val START_ON = mapOf("petals" to 45, "fallen" to 50, "motes" to 40, "stars" to 60, "clouds" to 35, "tilt" to 40)

internal val DEFAULT_FX: Map<String, FxSetting> = LiveFx.ALL.associate { info ->
    val start = START_ON[info.id]
    info.id to FxSetting(on = start != null, density = start ?: info.def)
}

/** A ready-made mix, like the mockup's PRESETS. [calm] = null leaves his calm setting as it is. */
class LiveBgPreset(
    val id: String,
    val name: String,
    val dot: Color,
    val time: String,
    val weather: String,
    val mood: String,
    val mix: Map<String, Int>,
    val desc: String,
    val calm: Boolean? = null,
)

val LIVE_BG_PRESETS = listOf(
    LiveBgPreset("fuji", "Fuji", Color(0xFFF28AB0), "dusk", "clear", "pink", mapOf("petals" to 55, "clouds" to 60, "rays" to 35),
        "Petals, cloud drift and a few light rays, at dusk. Your wallpaper, awake."),
    LiveBgPreset("night", "Night & Gold", Color(0xFFF3D79C), "night", "clear", "gold", mapOf("motes" to 60, "stars" to 65, "rays" to 45, "clouds" to 25),
        "Gold motes, stars and pale moonbeams, at night."),
    LiveBgPreset("inazuma", "Inazuma Violet", Color(0xFFB47FDC), "night", "storm", "violet", mapOf("storm" to 70, "rain" to 45, "clouds" to 75, "mist" to 20),
        "A storm with violet lightning behind heavy clouds, at night."),
    LiveBgPreset("sumeru", "Sumeru Green", Color(0xFFA5C83B), "night", "clear", "green", mapOf("spores" to 65, "mist" to 55, "motes" to 50, "rays" to 25, "clouds" to 20),
        "Glow spores, mist and green fireflies, at night."),
    LiveBgPreset("cozy", "Cozy", Color(0xFFF8A53A), "dusk", "clear", "pink", mapOf("fallen" to 55, "butterflies" to 80, "rays" to 40),
        "Fallen blossoms settling, a couple of butterflies and soft light rays. For a still wallpaper."),
    LiveBgPreset("calm", "Calm", Color(0xFF9FD6E3), "dusk", "clear", "gold", mapOf("clouds" to 18, "motes" to 14, "petals" to 10),
        "Almost still: a hint of cloud, a few motes. Calm mode on.", calm = true),
    LiveBgPreset("off", "Off", Color(0xFF7A7F90), "dusk", "clear", "gold", emptyMap(),
        "Every effect off. Just your wallpaper."),
)

/** Remembers his living background. [style] is what the home draws; change it with `change { it.copy(...) }`. */
class LiveBgStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var style by mutableStateOf(LiveBgStyle.fromJson(prefs.getString(KEY_STYLE, null), LiveBgStyle()))
        private set

    /**
     * When the Glints "Height" slider last moved (uptime ms). The preview draws a dashed guide around the band for
     * two seconds after, so he can see where it is. Not saved.
     */
    @Volatile var glintGuideAt = 0L

    /** Replaces the whole style (and saves it). */
    fun useStyle(new: LiveBgStyle) {
        style = new
        prefs.edit { putString(KEY_STYLE, new.toJson()) }
    }

    /**
     * Changes one or more choices, e.g. `change { it.copy(mood = "gold") }`.
     * Changing anything other than the main switch makes it his own mix (no preset shows as picked).
     */
    fun change(edit: (LiveBgStyle) -> LiveBgStyle) {
        val old = style
        val next = edit(old)
        val onlySwitch = next.copy(enabled = old.enabled, preset = old.preset) == old
        useStyle(if (onlySwitch) next else next.copy(preset = ""))
    }

    /** Changes one effect, e.g. `useFx("petals") { it.copy(density = 70) }`. */
    fun useFx(id: String, edit: (FxSetting) -> FxSetting) = change { s -> s.copy(fx = s.fx + (id to edit(s.fx(id)))) }

    /**
     * Applies a preset: its effects on at its densities, every other effect off (touch sparkle and tilt keep his
     * choice), and its time, weather and mood. Calm mode only changes for the Calm preset.
     */
    fun usePreset(p: LiveBgPreset) {
        val s = style
        val fx = s.fx.toMutableMap()
        LiveFx.ALL.forEach { info ->
            if (info.index == LiveFx.TOUCH || info.index == LiveFx.TILT) return@forEach
            val amount = p.mix[info.id]
            val cur = s.fx(info.id)
            fx[info.id] = if (amount != null) cur.copy(on = true, density = amount) else cur.copy(on = false)
        }
        useStyle(s.copy(enabled = true, fx = fx, time = p.time, weather = p.weather, mood = p.mood, calm = p.calm ?: s.calm, preset = p.id))
    }

    /** Back to his starting mix. */
    fun reset() = useStyle(LiveBgStyle())

    /** Reads the style again from the phone (after a saved look was applied). */
    fun reload() {
        style = LiveBgStyle.fromJson(prefs.getString(KEY_STYLE, null), LiveBgStyle())
    }

    companion object {
        /** The settings file's name (saved themes copy this file). */
        const val PREFS = "living_bg"
        private const val KEY_STYLE = "style"
        @Volatile private var instance: LiveBgStore? = null

        fun get(context: Context): LiveBgStore =
            instance ?: synchronized(this) { instance ?: LiveBgStore(context.applicationContext).also { instance = it } }
    }
}
