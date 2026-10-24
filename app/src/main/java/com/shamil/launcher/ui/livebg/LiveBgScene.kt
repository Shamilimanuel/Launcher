package com.shamil.launcher.ui.livebg

import androidx.compose.ui.graphics.Color
import java.util.Calendar

/*
 * Living backgrounds, direction A (24 Oct): a living layer drawn over his own wallpaper, behind the home.
 * This file is the "scene": every effect, which group it sits in, and when it is allowed to show
 * (time of day and weather), copied from the web mockup's FX table (prototype/living-backgrounds.html).
 * The ids are the saved names in the "living_bg" settings file, so never rename one.
 */

/** Time of day, as an index into the gate tables below. */
internal const val T_DAWN = 0
internal const val T_DAY = 1
internal const val T_DUSK = 2
internal const val T_NIGHT = 3
internal val TIME_IDS = listOf("dawn", "day", "dusk", "night")

/** Weather, as an index into the gate tables below. */
internal const val W_CLEAR = 0
internal const val W_RAIN = 1
internal const val W_STORM = 2
internal const val W_SNOW = 3
internal const val W_MIST = 4
internal val WEATHER_IDS = listOf("clear", "rain", "storm", "snow", "mist")

/**
 * One effect. [gt] = how much it shows at dawn / day / dusk / night, [gw] = in clear / rain / storm / snow / mist
 * (the mockup's `gt` and `gw` "gate" tables; null = always). [max] = the most particles it ever draws.
 */
internal class FxInfo(
    val id: String,
    val index: Int,
    val group: String,
    val name: String,
    val text: String,
    /** When it shows, in words ("Dusk and night"); null = always. */
    val needs: String?,
    /** The density it starts at when he first switches it on. */
    val def: Int,
    val max: Int = 0,
    val gt: FloatArray? = null,
    val gw: FloatArray? = null,
    /** Drawn by the GPU "shader" (AGSL), which needs Android 13. */
    val shader: Boolean = false,
    val amountLabel: String = "Density",
    val hasAmount: Boolean = true,
    /** false = one of the four he hasn't decided on yet (built, but off by default). */
    val decided: Boolean = true,
)

/** The effects, in the order the settings show them. The index of each is fixed (the engine uses it). */
internal object LiveFx {
    const val FALLEN = 0
    const val LEAVES = 1
    const val BUTTERFLIES = 2
    const val BIRDS = 3
    const val LANTERNS = 4
    const val DANDELIONS = 5
    const val GLINTS = 6
    const val CLOUDS = 7
    const val RAYS = 8
    const val MIST = 9
    const val STARS = 10
    const val AURORA = 11
    const val RAIN = 12
    const val SNOW = 13
    const val STORM = 14
    const val PETALS = 15
    const val MOTES = 16
    const val SPORES = 17
    const val TOUCH = 18
    const val TILT = 19
    const val COUNT = 20

    private fun g(vararg v: Float) = v

    val ALL: List<FxInfo> = listOf(
        FxInfo("fallen", FALLEN, "dec", "Fallen cherry blossoms",
            "Blossoms drift down and come to rest along the bottom edge. They fade away slowly, so it never piles up, and now and then a light breeze lifts a few.",
            "Dry weather", def = 55, max = 70, gt = g(1f, 1f, 1f, .8f), gw = g(1f, .5f, .25f, 0f, .7f)),
        FxInfo("leaves", LEAVES, "dec", "Falling autumn leaves",
            "Maple and ginkgo leaves swing down in warm colours, settle along the bottom, then slowly fade.",
            "Dry weather", def = 40, max = 40, gt = g(1f, 1f, 1f, .8f), gw = g(1f, .5f, .25f, 0f, .7f)),
        FxInfo("butterflies", BUTTERFLIES, "dec", "Butterflies",
            "One to three butterflies flutter slowly, land and rest for a few seconds, then fly on.",
            "Daylight, dry weather", def = 60, max = 3, gt = g(.7f, 1f, .85f, 0f), gw = g(1f, 0f, 0f, 0f, .4f), amountLabel = "How many"),
        FxInfo("birds", BIRDS, "dec", "Birds in the sky",
            "Now and then a small flock crosses the sky. Daytime only.",
            "Daytime, dry weather", def = 50, max = 2, gt = g(1f, 1f, .8f, 0f), gw = g(1f, 0f, 0f, 0f, .4f), decided = false),
        FxInfo("lanterns", LANTERNS, "dec", "Floating lanterns",
            "Liyue-style lanterns rise slowly with a warm, flickering glow.",
            "Dusk and night", def = 45, max = 14, gt = g(.3f, 0f, 1f, 1f), gw = g(1f, .1f, 0f, .6f, .8f)),
        FxInfo("dandelions", DANDELIONS, "dec", "Dandelion seeds",
            "Mondstadt-style fluffy seeds drifting on the breeze, turning as they go.",
            "Daytime, dry weather", def = 45, max = 40, gt = g(1f, 1f, .8f, .15f), gw = g(1f, 0f, 0f, 0f, .5f)),
        FxInfo("glints", GLINTS, "dec", "Glints on water",
            "For a wallpaper with a lake or sea: soft glints and shimmer in a band. Height puts the band where your water is.",
            null, def = 55, max = 150, gt = g(.8f, 1f, 1f, .55f), gw = g(1f, .5f, .2f, .2f, .6f)),

        FxInfo("clouds", CLOUDS, "atmo", "Cloud drift",
            "Two soft layers of cloud slide past at different speeds, lit from the glow at the horizon.",
            null, def = 55, shader = true),
        FxInfo("rays", RAYS, "atmo", "Light rays",
            "Soft god rays fanning up from low in the sky, slowly breathing. At night they turn into pale moonbeams.",
            "Not in heavy weather", def = 40, shader = true, gt = g(1f, .8f, 1f, .45f), gw = g(1f, .25f, 0f, 0f, .5f), decided = false),
        FxInfo("mist", MIST, "atmo", "Mist",
            "A low band of fog that slowly slides sideways. In misty weather it rises to cover more.",
            null, def = 45, shader = true, gw = g(1f, .9f, .6f, .7f, 1.6f)),
        FxInfo("stars", STARS, "atmo", "Stars",
            "Gentle twinkling, and now and then a shooting star. Night only, and hidden by rain.",
            "Night, clear sky", def = 60, max = 140, gt = g(0f, 0f, .15f, 1f), gw = g(1f, 0f, 0f, .05f, .25f)),
        FxInfo("aurora", AURORA, "atmo", "Aurora",
            "Slow green and violet curtains high in the night sky. A showpiece for clear nights.",
            "Clear nights", def = 50, shader = true, gt = g(0f, 0f, 0f, 1f), gw = g(1f, 0f, 0f, 0f, .2f)),

        FxInfo("rain", RAIN, "wx", "Rain",
            "Thin streaks, a few drops sliding down the \"glass\", and a slightly darker look.",
            "Rainy weather", def = 55, max = 200),
        FxInfo("snow", SNOW, "wx", "Snow",
            "Soft flakes falling and swaying.",
            "Snowy weather", def = 55, max = 150),
        FxInfo("storm", STORM, "wx", "Storm",
            "Rain with the wind pushing, and lightning behind the clouds. In calm mode it is a soft glow, no flash.",
            "Stormy weather", def = 55),

        FxInfo("petals", PETALS, "magic", "Sakura petals",
            "Petals drift across, tumbling as they go, and a few catch the light when they turn. These keep flying (the Decorations ones settle).",
            "Dry weather", def = 50, max = 64, gt = g(1f, 1f, 1f, .7f), gw = g(1f, .35f, .5f, 0f, .6f)),
        FxInfo("motes", MOTES, "magic", "Fireflies & gold motes",
            "Soft lights that wander and blink gently. Their colour follows the colour mood.",
            "Dusk and night", def = 45, max = 56, gt = g(.5f, .1f, .8f, 1f), gw = g(1f, .05f, 0f, 0f, .7f)),
        FxInfo("spores", SPORES, "magic", "Glow spores",
            "Sumeru-style glowing orbs that rise slowly and pulse.",
            "Dusk and night", def = 55, max = 30, gt = g(.4f, .1f, .7f, 1f), gw = g(1f, .4f, 0f, 0f, 1f), decided = false),

        FxInfo("touch", TOUCH, "tm", "Touch sparkle",
            "Tap an empty spot on your home: a small ripple and a burst of sparkles, and resting petals get blown away.",
            null, def = 50, hasAmount = false, decided = false),
        FxInfo("tilt", TILT, "tm", "Tilt parallax",
            "The effects shift a little as you tilt your phone. Near things move more than far things.",
            null, def = 55, amountLabel = "Strength"),
    )

    private val byIdMap = ALL.associateBy { it.id }
    fun byId(id: String): FxInfo? = byIdMap[id]
}

/** The setting groups, like the mockup's GROUPS. */
internal class FxGroup(val id: String, val name: String, val sub: String)

internal val FX_GROUPS = listOf(
    FxGroup("dec", "Decorations", "Small living things on top of a still wallpaper. They don't need any weather."),
    FxGroup("atmo", "Atmosphere", "Sky, light and air."),
    FxGroup("wx", "Weather", "These show when it really rains, snows or storms (or when you pick that weather above)."),
    FxGroup("magic", "Magic in the air", "Drifting light and petals."),
    FxGroup("tm", "Touch & motion", "How the effects react to you."),
)

/** The colour moods: gold, pink, violet, green (the mockup's TONES). */
internal val MOOD_IDS = listOf("gold", "pink", "violet", "green")
internal val MOOD_TONES = listOf(Color(255, 214, 130), Color(255, 170, 205), Color(196, 150, 255), Color(170, 255, 140))
internal fun moodIndex(mood: String): Int = MOOD_IDS.indexOf(mood).let { if (it < 0) 1 else it }

/** The clock's time of day: dawn 5–8, day 8–17, dusk 17–21, night the rest (the mockup's curTime). */
internal fun clockTimeOfDay(): Int {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..7 -> T_DAWN
        in 8..16 -> T_DAY
        in 17..20 -> T_DUSK
        else -> T_NIGHT
    }
}

/**
 * The real weather's WMO code (from Open-Meteo, see glance/Weather.kt) as one of our five kinds.
 * Clear and cloudy both count as "clear" (Cloud drift handles the clouds); no weather yet = clear.
 */
internal fun weatherKind(code: Int?): Int = when (code) {
    null -> W_CLEAR
    45, 48 -> W_MIST
    in 51..67, in 80..82 -> W_RAIN
    in 71..77, 85, 86 -> W_SNOW
    in 95..99 -> W_STORM
    else -> W_CLEAR
}

/** What the scene is right now, in words ("Dusk · Clear"), for the settings preview. */
internal fun sceneLabel(style: LiveBgStyle, realCode: Int?): String {
    val time = TIME_IDS.indexOf(style.time).takeIf { it >= 0 } ?: clockTimeOfDay()
    val weather = WEATHER_IDS.indexOf(style.weather).takeIf { it >= 0 } ?: weatherKind(realCode)
    val t = listOf("Dawn", "Day", "Dusk", "Night")[time]
    val w = listOf("Clear", "Rain", "Storm", "Snow", "Mist")[weather]
    return "$t · $w"
}
